package com.zlip.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.Log
import com.zlip.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Single source of truth for the cover library and settings, shared by the inner-screen UI, the
 * cover-screen presentation, the notification and the quick-settings tile (all in one process).
 * Persists to `files/items.json`; media lives in `files/media/`.
 */
class ContentRepository(context: Context, private val scope: CoroutineScope) {

    private data class State(
        val items: List<CoverItem> = emptyList(),
        val selectedId: String? = null,
        val settings: CoverSettings = CoverSettings(),
        /** Ids the user picked for rotation, in play order; empty means "the whole library". */
        val playlist: List<String> = emptyList(),
        /** Library entries this version could not read (damaged, or from a newer version): written back untouched. */
        val unreadable: List<Any> = emptyList(),
    ) {
        /** What next/previous and auto-advance move through. */
        val sequence: List<CoverItem>
            get() {
                if (playlist.isEmpty()) return items
                val byId = items.associateBy { it.id }
                return playlist.mapNotNull { byId[it] }.ifEmpty { items }
            }

        /** New items join a custom playlist so they rotate too; without one the whole library rotates anyway. */
        fun withInPlaylist(ids: List<String>): State =
            if (playlist.isEmpty()) this else copy(playlist = playlist + ids.filter { it !in playlist })
    }

    /**
     * What [load] found: [isNew] for a first launch, [clean] when every entry could be read, [keepFile] when the
     * library file itself could not be read and must be left alone.
     */
    private class Loaded(val state: State, val isNew: Boolean = false, val clean: Boolean = true, val keepFile: Boolean = false)

    private val appContext = context.applicationContext
    private val store = AtomicFile(File(appContext.filesDir, STORE_FILE))
    private val writeMutex = Mutex()
    private val state = MutableStateFlow(State())

    /**
     * Set when the library file could not be read: it is then left exactly as it is (a copy is kept too)
     * until the user changes something, so an app update can still read it. Also set until the file has
     * been read at all, so nothing ever writes the empty starting state over it.
     */
    @Volatile
    private var keepStoreFile = true

    private val loadJob = scope.launch {
        val loaded = withContext(Dispatchers.IO) {
            val result = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // load() handles a bad file itself; this is for anything else, which must not cost the library.
                startOverKeepingFile(e)
            }
            // Only after a clean load: files of entries that could not be read must survive, and so must those of
            // a library file that could not be read at all (its kept copy is useless without them).
            if (result.clean && !hasBrokenCopy()) {
                try {
                    deleteOrphanMedia(result.state)
                } catch (e: Throwable) {
                    // Only housekeeping: the library that was read must still load.
                    Log.w(TAG, "Could not remove unused media", e)
                }
            }
            result
        }
        state.value = loaded.state
        keepStoreFile = loaded.keepFile
        // Write the first-launch sample items right away so their ids are stable from now on.
        if (loaded.isNew) persist()
        if (!loaded.keepFile) scope.launch { fillVideoDurations() }
    }

    val items: StateFlow<List<CoverItem>> = state.map { it.items }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<CoverSettings> = state.map { it.settings }
        .stateIn(scope, SharingStarted.Eagerly, CoverSettings())

    /** The item on the cover: the explicit selection, or the first item if nothing valid is selected. */
    val selectedItem: StateFlow<CoverItem?> = state.map { it.selected() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // The StateFlows here follow the state a moment later (they are collected on the main thread), so right
    // after [loaded] turns true they can still hold their defaults. Code that acts on the first loaded
    // values (service start, widget redraw) reads these instead.

    /** Settings exactly as they are now. */
    val currentSettings: CoverSettings get() = state.value.settings

    /** The playlist sequence exactly as it is now. */
    val currentSequence: List<CoverItem> get() = state.value.sequence

    /** The item on the cover exactly as it is now. */
    val currentSelected: CoverItem? get() = state.value.selected()

    private fun State.selected(): CoverItem? = items.firstOrNull { it.id == selectedId } ?: sequence.firstOrNull()

    /** The user's playlist (ordered ids of existing items); empty = whole library. */
    val playlist: StateFlow<List<String>> = state.map { s -> s.playlist.filter { id -> s.items.any { it.id == id } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Items the cover rotates through, in order. */
    val sequence: StateFlow<List<CoverItem>> = state.map { it.sequence }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)

    /** True after the last write to disk failed (e.g. storage full); false again after a good write. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _draft = MutableStateFlow<CoverItem?>(null)

    /** Unsaved item from an open editor; the cover shows it live instead of [selectedItem]. In memory only. */
    val draft: StateFlow<CoverItem?> = _draft.asStateFlow()

    private val persistRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        loadJob.invokeOnCompletion { _loaded.value = true }
        scope.launch {
            @OptIn(FlowPreview::class)
            persistRequests.debounce(PERSIST_DEBOUNCE_MS).collect { persist() }
        }
    }

    fun setDraft(item: CoverItem?) {
        _draft.value = item
    }

    fun upsert(item: CoverItem) = mutate { s ->
        val index = s.items.indexOfFirst { it.id == item.id }
        val items = if (index >= 0) s.items.toMutableList().also { it[index] = item } else s.items + item
        // Collages and groups hold copies: an edit reaches them too, while deleting the original leaves them be.
        val updated = s.copy(items = items.map { it.replacingCopies(item) })
        if (index >= 0) updated else updated.withInPlaylist(listOf(item.id))
    }

    /**
     * Frames media item [id] as [framing] (fit, zoom, focus) in the library and in every copy of it inside collages
     * and groups, as an edit in the editor would. An item that was deleted from the library is not brought back:
     * then only the copies that still hold it change.
     */
    fun setMediaFraming(id: String, framing: MediaFraming) = mutate { s ->
        val items = s.items.map { it.withMediaFraming(id, framing) }
        if (items == s.items) s else s.copy(items = items)
    }

    fun delete(id: String) = deleteAll(setOf(id))

    /** Removes items (and their files) from the library and the playlist. */
    fun deleteAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutate { s ->
            // If the item on the cover goes, show the next surviving one rather than jumping to the start.
            val shown = s.shownId()
            val selected = if (shown in ids) {
                val sequence = s.sequence
                val at = sequence.indexOfFirst { it.id == shown }
                (1..sequence.size).map { sequence[(at + it).mod(sequence.size)] }.firstOrNull { it.id !in ids }?.id
            } else {
                s.selectedId
            }
            val remaining = s.items.filterNot { it.id in ids }
            // A collage or group may hold a copy of a deleted item: only files nothing else uses go.
            val stillUsed = remaining.flatMapTo(HashSet()) { it.mediaFiles() }
            val unused = s.items.filter { it.id in ids }.flatMap { it.mediaFiles() }.filterNot { it in stillUsed }
            if (unused.isNotEmpty()) {
                scope.launch(Dispatchers.IO) { unused.forEach { MediaStorage.file(appContext, it).delete() } }
            }
            s.copy(
                items = remaining,
                selectedId = selected,
                playlist = s.playlist.filterNot { it in ids },
            )
        }
    }

    /** Appends [ids] (in library order) to the playlist, skipping ones already in it. */
    fun addToPlaylist(ids: Set<String>) = mutate { s ->
        val toAdd = s.items.map { it.id }.filter { it in ids && it !in s.playlist }
        s.copy(playlist = s.playlist + toAdd)
    }

    fun removeFromPlaylist(ids: Set<String>) = mutate { s -> s.copy(playlist = s.playlist.filterNot { it in ids }) }

    /** Lets items just added on their own (a gallery import) rotate too, if the user has a playlist. */
    fun joinPlaylist(ids: List<String>) = mutate { it.withInPlaylist(ids) }

    fun clearPlaylist() = mutate { it.copy(playlist = emptyList()) }

    /** Moves a playlist entry by [delta] places (e.g. -1 = earlier). */
    fun movePlaylistItem(id: String, delta: Int) = mutate { s ->
        val from = s.playlist.indexOf(id)
        if (from < 0) return@mutate s
        val to = (from + delta).coerceIn(0, s.playlist.lastIndex)
        if (to == from) return@mutate s
        s.copy(playlist = s.playlist.toMutableList().apply { add(to, removeAt(from)) })
    }

    // Selection changes are frequent (auto-advance, swipes, notification) and cheap to lose, so they are
    // not written on their own: they ride along with the next real save or the flush in onStop.
    fun select(id: String) = mutate(persist = false) { it.copy(selectedId = id) }

    /** Manual next/previous always wrap around; with [shuffle], next walks the current random pass. */
    fun selectNext(shuffle: Boolean = false) {
        nextId(state.value, +1, shuffle, wrap = true)?.let(::select)
    }

    fun selectPrevious() {
        nextId(state.value, -1, shuffle = false, wrap = true)?.let(::select)
    }

    /**
     * Auto-advance step. Returns false (and changes nothing) when there is nothing to move to: a
     * one-item sequence that is already showing, or the end of a pass with [wrap] (Loop) off.
     */
    fun advance(shuffle: Boolean, wrap: Boolean): Boolean {
        val s = state.value
        val target = nextId(s, +1, shuffle, wrap) ?: return false
        if (target == s.shownId()) return false
        select(target)
        return true
    }

    /** The item actually on the cover: the selection, or the sequence's first item when there is none. */
    private fun State.shownId(): String? = items.firstOrNull { it.id == selectedId }?.id ?: sequence.firstOrNull()?.id

    private fun nextId(s: State, delta: Int, shuffle: Boolean, wrap: Boolean): String? {
        val sequence = s.sequence
        if (sequence.isEmpty()) return null
        val shown = s.shownId()
        val current = sequence.indexOfFirst { it.id == shown }
        // Showing something outside the playlist (picked by hand, just imported): enter the playlist.
        if (current < 0) return sequence.first().id
        if (sequence.size < 2) return null
        if (shuffle) return nextShuffled(sequence.map { it.id }, sequence[current].id, wrap)
        val next = current + delta
        return when {
            next in sequence.indices -> sequence[next].id
            wrap -> sequence[next.mod(sequence.size)].id
            else -> null
        }
    }

    /** Ids still to show in the current random pass (in memory only); rebuilt when the sequence changes. */
    private var shuffleBag = ArrayDeque<String>()
    private var shuffleBagIds = emptySet<String>()

    /** Where a pass with Loop off ended; it stays there until the user picks something else. */
    private var shuffleEndedOn: String? = null

    /** Shuffle without repeats inside a pass; with [wrap] (Loop) off it stops after every item showed once. */
    private fun nextShuffled(ids: List<String>, shown: String, wrap: Boolean): String? {
        val idSet = ids.toSet()
        if (idSet != shuffleBagIds) {
            shuffleBagIds = idSet
            shuffleEndedOn = null
            shuffleBag = ArrayDeque(ids.filter { it != shown }.shuffled())
        }
        shuffleBag.remove(shown)
        if (shuffleBag.isEmpty()) {
            // Stop at the end of a pass, but only once: after a manual pick the next advance starts a new one.
            if (!wrap && (shuffleEndedOn == null || shuffleEndedOn == shown)) {
                shuffleEndedOn = shown
                return null
            }
            shuffleEndedOn = null
            shuffleBag = ArrayDeque(ids.filter { it != shown }.shuffled())
        }
        return shuffleBag.removeFirst()
    }

    fun updateSettings(transform: (CoverSettings) -> CoverSettings) = mutate { it.copy(settings = transform(it.settings)) }

    /**
     * Imports a picked photo, GIF/animated WebP or video and adds it to the library. With [select] it also
     * goes on the cover (and into a custom playlist); several files picked at once, or files added to a
     * group, only join the library. Videos are converted for the cover first; [onProgress] reports 0..1
     * during that step, and [onClipped] hears when only the first [MediaImport.MAX_VIDEO_MS] of a longer
     * video were kept. Fails with [MediaTooLargeException], [MediaUnreadableException] or
     * [VideoConversionException]; never leaves partial files behind.
     */
    suspend fun importMedia(
        uri: Uri,
        select: Boolean = true,
        onClipped: () -> Unit = {},
        onProgress: (Float) -> Unit = {},
    ): Result<CoverItem.Media> {
        loadJob.join()
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val baseName = UUID.randomUUID().toString()
        val created = mutableListOf<File>()

        fun newFile(name: String) = MediaStorage.file(appContext, name).also { created += it }

        var clipped = false

        val item = try {
            withContext(Dispatchers.IO) {
                val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                val name = displayName?.substringBeforeLast('.')?.takeSafe(MAX_NAME)?.ifBlank { null }
                    ?: appContext.getString(R.string.default_media_name)

                when {
                    mime.startsWith("video/") -> {
                        val video = newFile("$baseName.mp4")
                        val partial = newFile("$baseName.mp4$PARTIAL_SUFFIX")
                        val transcoded = MediaImport.transcodeVideo(appContext, uri, partial, onProgress)
                        val hasAudio = transcoded.hasAudio
                        if (!partial.renameTo(video)) throw IOException("Could not move ${partial.name}")
                        val poster = newFile("$baseName.jpg")
                        val hasPoster = MediaImport.writePoster(video, poster)
                        if (transcoded.clipped) clipped = true
                        CoverItem.Media(
                            id = newId(),
                            name = name,
                            fileName = video.name,
                            kind = MediaKind.VIDEO,
                            fit = MediaFit.FILL,
                            crisp = false,
                            posterFileName = if (hasPoster) poster.name else null,
                            durationMs = MediaImport.durationMs(video),
                            hasAudio = hasAudio,
                        )
                    }
                    else -> {
                        val graphic = MediaImport.isGraphic(mime)
                        val photo = newFile("$baseName.webp")

                        fun stillItem() = CoverItem.Media(
                            id = newId(),
                            name = name,
                            fileName = photo.name,
                            kind = MediaKind.IMAGE,
                            // Graphics (pixel art, logos) stay sharp and whole; camera photos fill the cover.
                            fit = if (graphic) MediaFit.FIT else MediaFit.FILL,
                            crisp = graphic,
                        )

                        if (MediaImport.mayBeAnimated(mime)) {
                            // Copy first: animations are kept as-is, stills are re-encoded smaller from the copy.
                            val partial = newFile("$baseName$PARTIAL_SUFFIX")
                            val copied = try {
                                MediaImport.copyLimited(appContext, uri, partial, MediaImport.MAX_ANIMATION_BYTES)
                                true
                            } catch (e: MediaTooLargeException) {
                                partial.delete()
                                false
                            }
                            if (!copied) {
                                // Too big to keep as an animation; a still of any size is fine, it gets downscaled.
                                if (MediaImport.isAnimated(appContext, uri)) throw MediaTooLargeException((MediaImport.MAX_ANIMATION_BYTES / (1024 * 1024)).toInt())
                                MediaImport.writeOptimizedPhoto(ImageDecoder.createSource(resolver, uri), photo, lossless = graphic)
                                stillItem()
                            } else if (MediaImport.isAnimated(partial)) {
                                val animation = newFile("$baseName.anim.${if (mime.endsWith("gif")) "gif" else "webp"}")
                                if (!partial.renameTo(animation)) throw IOException("Could not move ${partial.name}")
                                CoverItem.Media(id = newId(), name = name, fileName = animation.name, kind = MediaKind.ANIMATION)
                            } else {
                                MediaImport.writeOptimizedPhoto(ImageDecoder.createSource(partial), photo, lossless = graphic)
                                partial.delete()
                                stillItem()
                            }
                        } else {
                            // JPEG/PNG are never animated: decode straight from the picker (no size cap, one read).
                            MediaImport.writeOptimizedPhoto(ImageDecoder.createSource(resolver, uri), photo, lossless = graphic)
                            stillItem()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(Dispatchers.IO + NonCancellable) { created.forEach(File::delete) }
            throw e
        } catch (e: IOException) {
            withContext(Dispatchers.IO) { created.forEach(File::delete) }
            return Result.failure(e)
        } catch (e: RuntimeException) {
            // SecurityException from a revoked URI grant, or a decoder rejecting the file.
            Log.w(TAG, "Import failed", e)
            withContext(Dispatchers.IO) { created.forEach(File::delete) }
            return Result.failure(MediaUnreadableException(e))
        }

        state.update { s ->
            val added = s.copy(items = s.items + item)
            if (select) added.copy(selectedId = item.id).withInPlaylist(listOf(item.id)) else added
        }
        keepStoreFile = false
        persist()
        if (clipped) onClipped()
        return Result.success(item)
    }

    /**
     * Measures videos imported before their length was kept, once, in the background, and saves the lengths
     * into every copy (collage cells and group steps share the file). Auto-advance and groups then wait for
     * a clip's real end instead of a generous guess.
     */
    private suspend fun fillVideoDurations() {
        val missing = state.value.items.flatMapTo(LinkedHashSet()) { it.videosWithoutDuration() }
        if (missing.isEmpty()) return
        val durations = withContext(Dispatchers.IO) {
            missing.associateWith { MediaImport.durationMs(MediaStorage.file(appContext, it)) }.filterValues { it > 0 }
        }
        if (durations.isEmpty()) return
        mutate { s -> s.copy(items = s.items.map { it.withVideoDurations(durations) }) }
    }

    /**
     * Applies [transform] after the initial load (so early edits are never overwritten) and schedules a
     * save. Saves are debounced so dragging a slider writes the file once, not sixty times a second.
     */
    private fun mutate(persist: Boolean = true, transform: (State) -> State) {
        scope.launch {
            loadJob.join()
            state.update(transform)
            if (persist) {
                // A real change: from now on the library file holds what the user sees.
                keepStoreFile = false
                persistRequests.tryEmit(Unit)
            }
        }
    }

    /** Writes pending changes now (the activity calls this when it stops). */
    fun flush() {
        scope.launch {
            loadJob.join()
            persist()
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (keepStoreFile) return@withLock
            // Read the state under the lock so the last writer always saves the newest state.
            val snapshot = state.value
            val json = JSONObject().apply {
                put("version", 2)
                put("selectedId", snapshot.selectedId ?: JSONObject.NULL)
                put("settings", snapshot.settings.toJson())
                put("playlist", JSONArray(snapshot.playlist))
                put(
                    "items",
                    JSONArray().apply {
                        snapshot.items.forEach { put(CoverItemJson.encode(it)) }
                        snapshot.unreadable.forEach { put(it) }
                    },
                )
            }
            var out: FileOutputStream? = null
            try {
                out = store.startWrite()
                out.write(json.toString().toByteArray(Charsets.UTF_8))
                store.finishWrite(out)
                _saveFailed.value = false
            } catch (e: IOException) {
                out?.let(store::failWrite)
                Log.w(TAG, "Saving the library failed", e)
                _saveFailed.value = true
            }
        }
    }

    /** The stored library, or the first-launch samples when there is none. */
    private fun load(): Loaded {
        if (!store.baseFile.exists()) return Loaded(seed(), isNew = true)
        return try {
            val json = JSONObject(String(store.readFully(), Charsets.UTF_8))
            val array = json.optJSONArray("items") ?: JSONArray()
            val decoded = mutableListOf<CoverItem>()
            val unreadable = mutableListOf<Any>()
            for (index in 0 until array.length()) {
                val entry = array.opt(index) ?: continue
                val item = (entry as? JSONObject)?.let(CoverItemJson::decode)
                if (item != null) decoded += item else unreadable += entry
            }
            // Media files are not in the cloud backup, so a restore can bring back items without files.
            val items = decoded.mapNotNull { it.withoutMissingMedia() }
            val state = State(
                items = items,
                selectedId = json.optString("selectedId").takeIf { id -> items.any { it.id == id } },
                settings = CoverSettings.fromJson(json.optJSONObject("settings")),
                playlist = json.optJSONArray("playlist")?.let { array -> List(array.length()) { array.optString(it) } }
                    ?.filter { id -> items.any { it.id == id } }
                    .orEmpty(),
                unreadable = unreadable,
            )
            if (unreadable.isNotEmpty()) Log.w(TAG, "${unreadable.size} library entries could not be read; keeping them as they are")
            Loaded(state, clean = unreadable.isEmpty())
        } catch (e: Throwable) {
            // Damaged (JSONException), not readable (IOException), or anything else, such as a file too big for
            // memory (OutOfMemoryError): never treat it as an empty library that the next save writes out.
            startOverKeepingFile(e)
        }
    }

    /**
     * The library file cannot be read at all. Shows the samples, but keeps a copy of the file, leaves the
     * file itself alone until the user changes something, and deletes no media.
     */
    private fun startOverKeepingFile(error: Throwable): Loaded {
        // First, so the file stays untouched even if keeping the copy fails as well.
        keepStoreFile = true
        Log.w(TAG, "Library file is unreadable; keeping a copy and every media file", error)
        val file = store.baseFile
        // Named after the broken file's time, so relaunching with the same file does not pile up copies.
        val copy = File(file.parentFile, "$BROKEN_PREFIX${file.lastModified()}")
        try {
            if (!copy.exists()) file.copyTo(copy)
        } catch (e: IOException) {
            Log.w(TAG, "Could not keep a copy of the library file", e)
        }
        return Loaded(seed(), clean = false, keepFile = true)
    }

    /** A copy of an unreadable library file is kept (see [startOverKeepingFile]), so its media may still be needed. */
    private fun hasBrokenCopy(): Boolean = appContext.filesDir.list()?.any { it.startsWith(BROKEN_PREFIX) } == true

    /** The item without media whose file is gone, or null if nothing of it is left to show. */
    private fun CoverItem.withoutMissingMedia(): CoverItem? = when (this) {
        is CoverItem.Media -> takeIf { MediaStorage.file(appContext, fileName).exists() }
        is CoverItem.Collage -> copy(cells = cells.map { cell -> cell.mapNotNull { it.withoutMissingMedia() } })
        // A group left with nothing to show goes too.
        is CoverItem.Group -> copy(items = items.mapNotNull { it.withoutMissingMedia() }).takeIf { it.items.isNotEmpty() }
        else -> this
    }

    /** Removes media files no item points to (interrupted imports, deletes cut short by process death). */
    private fun deleteOrphanMedia(state: State) {
        val used = state.items.flatMapTo(HashSet()) { it.mediaFiles() }
        MediaStorage.dir(appContext).listFiles()?.forEach { file ->
            if (file.name !in used) file.delete()
        }
    }

    /** First launch: a few samples so the cover shows something right away. Fixed ids keep re-seeds stable. */
    private fun seed(): State {
        val heart = CoverItem.PixelArt(
            id = "sample-heart",
            name = appContext.getString(R.string.sample_heart_name),
            width = 16,
            height = 16,
            pixels = pixels(HEART, 0xFFFF2D55.toInt()),
        )
        val smile = CoverItem.PixelArt(
            id = "sample-smile",
            name = appContext.getString(R.string.sample_smile_name),
            width = 16,
            height = 16,
            pixels = pixels(SMILE, 0xFF7DF0C3.toInt()),
        )
        val text = CoverItem.Text(
            id = "sample-text",
            name = appContext.getString(R.string.sample_text_name),
            text = appContext.getString(R.string.sample_text),
            textColor = 0xFF9DB4FF.toInt(),
        )
        return State(items = listOf(smile, heart, text), selectedId = smile.id)
    }

    companion object {
        private const val TAG = "ZlipRepository"
        private const val STORE_FILE = "items.json"
        private const val BROKEN_PREFIX = "$STORE_FILE.broken-"
        private const val PARTIAL_SUFFIX = ".part"
        private const val PERSIST_DEBOUNCE_MS = 400L
        const val MAX_NAME = 40

        fun newId(): String = UUID.randomUUID().toString()

        private fun pixels(rows: List<String>, color: Int): List<Int> =
            rows.flatMap { row -> row.map { if (it == 'X') color else 0 } }

        private val HEART = listOf(
            "................",
            "................",
            "...XXX....XXX...",
            "..XXXXX..XXXXX..",
            ".XXXXXXXXXXXXXX.",
            ".XXXXXXXXXXXXXX.",
            ".XXXXXXXXXXXXXX.",
            "..XXXXXXXXXXXX..",
            "...XXXXXXXXXX...",
            "....XXXXXXXX....",
            ".....XXXXXX.....",
            "......XXXX......",
            ".......XX.......",
            "................",
            "................",
            "................",
        )

        private val SMILE = listOf(
            "................",
            "................",
            "................",
            "....XX....XX....",
            "....XX....XX....",
            "....XX....XX....",
            "................",
            "................",
            "..XX........XX..",
            "..XX........XX..",
            "...XX......XX...",
            "....XXXXXXXX....",
            ".....XXXXXX.....",
            "................",
            "................",
            "................",
        )
    }
}

object MediaStorage {
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    /**
     * Width / height of [media]'s picture as stored (a video's from its poster frame), or 0 when it cannot be read.
     * Reads only the file's header, so it is cheap, but still file access: call it off the main thread.
     */
    fun aspect(context: Context, media: CoverItem.Media): Float {
        val name = (if (media.kind == MediaKind.VIDEO) media.posterFileName else media.fileName) ?: return 0f
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file(context, name).path, bounds) }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 0f
    }
}

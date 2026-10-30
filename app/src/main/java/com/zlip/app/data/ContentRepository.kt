package com.zlip.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
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
        /** Ids the user picked for rotation, in play order; empty means "the whole library" (see [Rotation]). */
        val playlist: List<String> = emptyList(),
        /** Library entries this version could not read (damaged, or from a newer version): written back untouched. */
        val unreadable: List<Any> = emptyList(),
    ) {
        /** What next/previous and auto-advance move through. */
        val sequence: List<CoverItem> get() = Rotation.of(items, playlist)

        /** New items join a custom playlist so they rotate too; without one the whole library rotates anyway. */
        fun withInPlaylist(ids: List<String>): State =
            if (playlist.isEmpty()) this else copy(playlist = playlist + ids.filter { it !in playlist })
    }

    /**
     * What [load] found: [isNew] for a first launch, [clean] when every entry could be read, [keepFile] when the
     * library file itself could not be read and must be left alone.
     */
    private class Loaded(
        val state: State,
        val isNew: Boolean = false,
        val clean: Boolean = true,
        val keepFile: Boolean = false,
        val modified: Boolean = false,
    )

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
        // Write the first-launch sample items or cleaned library right away so disk matches memory.
        if (loaded.isNew || loaded.modified) persist()
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
        val fileSizeOf: (String) -> Long = { fileName -> MediaStorage.file(appContext, fileName).length() }
        val idIndex = s.items.indexOfFirst { it.id == item.id }
        val isContainer = item is CoverItem.Group || item is CoverItem.Collage
        val sig = LibraryCleaner.contentSignature(item, fileSizeOf)
        val sigIndex = if (idIndex >= 0 || isContainer) {
            -1
        } else {
            s.items.indexOfFirst { existing ->
                existing !is CoverItem.Group &&
                    existing !is CoverItem.Collage &&
                    LibraryCleaner.contentSignature(existing, fileSizeOf) == sig
            }
        }
        val targetIndex = if (idIndex >= 0) idIndex else sigIndex
        val raw = if (targetIndex >= 0) {
            s.items.toMutableList().also { it[targetIndex] = item }
        } else {
            s.items + item
        }
        // Collages and groups hold copies: an edit reaches them too, while deleting the original leaves them be.
        val withReplaced = raw.map { it.replacingCopies(item) }
        val cleaned = LibraryCleaner.clean(withReplaced, fileSizeOf)
        val stillUsed = cleaned.flatMapTo(HashSet()) { it.mediaFiles() }
        val unused = withReplaced.flatMapTo(HashSet()) { it.mediaFiles() }.filterNot { it in stillUsed }
        if (unused.isNotEmpty()) {
            scope.launch(Dispatchers.IO) { unused.forEach { MediaStorage.file(appContext, it).delete() } }
        }
        val validIds = cleaned.mapTo(HashSet()) { it.id }
        val updated = s.copy(
            items = cleaned,
            selectedId = item.id.takeIf { it in validIds }
                ?: s.selectedId?.takeIf { it in validIds }
                ?: cleaned.firstOrNull { top -> top.leaves().any { it.id == item.id } }?.id
                ?: cleaned.firstOrNull()?.id,
            playlist = s.playlist.filter { it in validIds }.distinct(),
        )
        if (targetIndex >= 0 || item.id !in validIds) updated else updated.withInPlaylist(listOf(item.id))
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

    /** Makes the playlist exactly [ids] (those still in the library, none twice): how a cleared or changed list is undone. */
    fun setPlaylist(ids: List<String>) = mutate { s ->
        val existing = s.items.mapTo(HashSet()) { it.id }
        s.copy(playlist = ids.distinct().filter { it in existing })
    }

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
     * during that step; a video is kept whole, however long. Fails with [MediaTooLargeException],
     * [MediaUnreadableException], [VideoConversionException] or [NotEnoughSpaceException]; never leaves
     * partial files behind.
     */
    suspend fun importMedia(
        uri: Uri,
        select: Boolean = true,
        onProgress: (Float) -> Unit = {},
    ): Result<CoverItem.Media> {
        loadJob.join()
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val baseName = UUID.randomUUID().toString()
        val created = mutableListOf<File>()

        fun newFile(name: String) = MediaStorage.file(appContext, name).also { created += it }

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
                        val hasAudio = MediaImport.transcodeVideo(appContext, uri, partial, onProgress)
                        if (!partial.renameTo(video)) throw IOException("Could not move ${partial.name}")
                        val poster = newFile("$baseName.jpg")
                        val hasPoster = MediaImport.writePoster(video, poster)
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

        val fileSizeOf: (String) -> Long = { fileName -> MediaStorage.file(appContext, fileName).length() }
        val newSig = LibraryCleaner.contentSignature(item, fileSizeOf)
        val currentState = state.value
        val existingLeaf = currentState.items
            .flatMap { it.leaves() }
            .filterIsInstance<CoverItem.Media>()
            .firstOrNull { LibraryCleaner.contentSignature(it, fileSizeOf) == newSig }
        if (existingLeaf != null) {
            withContext(Dispatchers.IO) { created.forEach(File::delete) }
            val containerId = currentState.items.firstOrNull { top ->
                top.id == existingLeaf.id || top.leaves().any { it.id == existingLeaf.id }
            }?.id
            if (select && containerId != null) {
                select(containerId)
            }
            return Result.success(existingLeaf)
        }

        state.update { s ->
            val added = s.copy(items = s.items + item)
            if (select) added.copy(selectedId = item.id).withInPlaylist(listOf(item.id)) else added
        }
        keepStoreFile = false
        persist()
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
            var droppedLegacy = false
            for (index in 0 until array.length()) {
                val entry = array.opt(index) ?: continue
                val obj = entry as? JSONObject
                if (obj != null && CoverItemJson.isRemovedToy(obj)) {
                    droppedLegacy = true
                    continue
                }
                val item = obj?.let(CoverItemJson::decode)
                if (item != null) decoded += item else unreadable += entry
            }
            // Media files are not in the cloud backup, so a restore can bring back items without files.
            val existingMedia = decoded.mapNotNull { it.withoutMissingMedia() }
            val items = LibraryCleaner.clean(existingMedia) { fileName -> MediaStorage.file(appContext, fileName).length() }
            val rawSelectedId = json.optString("selectedId")
            val resolvedSelectedId = rawSelectedId.takeIf { id -> items.any { it.id == id } }
                ?: items.firstOrNull { top -> top.leaves().any { it.id == rawSelectedId } }?.id
                ?: items.firstOrNull()?.id
            val state = State(
                items = items,
                selectedId = resolvedSelectedId,
                settings = CoverSettings.fromJson(json.optJSONObject("settings")),
                playlist = json.optJSONArray("playlist")?.let { array -> List(array.length()) { array.optString(it) } }
                    ?.filter { id -> items.any { it.id == id } }
                    ?.distinct()
                    .orEmpty(),
                unreadable = unreadable,
            )
            if (unreadable.isNotEmpty()) Log.w(TAG, "${unreadable.size} library entries could not be read; keeping them as they are")
            val modified = droppedLegacy || items != decoded
            Loaded(state, clean = unreadable.isEmpty(), modified = modified)
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

/** Deduplicates the library and ensures items inside a group or collage do not also appear in the main library. */
internal object LibraryCleaner {
    fun contentSignature(item: CoverItem, fileSizeOf: (String) -> Long = { -1L }): String = when (item) {
        is CoverItem.Media -> {
            val size = fileSizeOf(item.fileName)
            when {
                item.kind == MediaKind.VIDEO && item.durationMs > 0L ->
                    "video:${item.name.trim().lowercase()}:${item.durationMs / 250L}:${item.hasAudio}"
                size > 0L ->
                    "media:${item.kind.name}:${item.name.trim().lowercase()}:$size"
                else ->
                    "media-file:${item.fileName}"
            }
        }
        is CoverItem.PixelArt ->
            "pixel:${item.width}x${item.height}:${item.background}:${item.pixels.hashCode()}:${item.extraFrames.hashCode()}"
        is CoverItem.Text ->
            "text:${item.text.trim().lowercase()}:${item.textColor}:${item.background}:${item.sizeSp}:${item.scroll}:${item.bold}"
        is CoverItem.Countdown ->
            "countdown:${item.title.trim().lowercase()}:${item.targetMillis}:${item.countUp}"
        is CoverItem.Qr ->
            "qr:${item.kind.name}:${item.payload.trim()}"
        is CoverItem.Toy ->
            "toy:${item.kind.name}:${item.options.map { it.trim().lowercase() }}"
        is CoverItem.Collage ->
            "collage:${item.layout.name}:${item.cells.map { cell -> cell.map { contentSignature(it, fileSizeOf) } }}"
        is CoverItem.Group ->
            "group:${item.items.map { contentSignature(it, fileSizeOf) }}"
    }

    fun clean(raw: List<CoverItem>, fileSizeOf: (String) -> Long = { -1L }): List<CoverItem> {
        val normalized = raw.mapNotNull { item ->
            when (item) {
                is CoverItem.Group -> {
                    val seenStepIds = HashSet<String>()
                    val seenStepSigs = HashSet<String>()
                    val uniqueSteps = item.items.filter { step ->
                        seenStepIds.add(step.id) && seenStepSigs.add(contentSignature(step, fileSizeOf))
                    }
                    if (uniqueSteps.isEmpty()) null else item.copy(items = uniqueSteps)
                }
                is CoverItem.Collage -> {
                    val cleanedCells = item.cells.map { cell ->
                        val seenIds = HashSet<String>()
                        val seenSigs = HashSet<String>()
                        cell.filter { leaf ->
                            seenIds.add(leaf.id) && seenSigs.add(contentSignature(leaf, fileSizeOf))
                        }
                    }
                    item.copy(cells = cleanedCells)
                }
                else -> item
            }
        }

        val memberIds = HashSet<String>()
        val memberFiles = HashSet<String>()
        val memberSignatures = HashSet<String>()
        for (item in normalized) {
            if (item is CoverItem.Group || item is CoverItem.Collage) {
                for (leaf in item.leaves()) {
                    memberIds += leaf.id
                    if (leaf is CoverItem.Media) memberFiles += leaf.fileName
                    memberSignatures += contentSignature(leaf, fileSizeOf)
                }
            }
        }

        val seenTopIds = HashSet<String>()
        val seenTopSignatures = HashSet<String>()
        val result = mutableListOf<CoverItem>()
        for (item in normalized) {
            val sig = contentSignature(item, fileSizeOf)
            val isContainer = item is CoverItem.Group || item is CoverItem.Collage
            if (!isContainer) {
                val inContainer = item.id in memberIds ||
                    (item is CoverItem.Media && item.fileName in memberFiles) ||
                    sig in memberSignatures
                if (inContainer) continue
            }
            if (!seenTopIds.add(item.id)) continue
            if (!seenTopSignatures.add(sig)) continue
            result += item
        }
        return result
    }
}

/** What the cover rotates through. Pure, so it is unit-tested. */
internal object Rotation {
    /**
     * The user's [playlist] (ids, in play order) when there is one. Without one, the whole library except the items
     * that are steps of a group: those play inside their group, and rotating them on their own too would bring the
     * group's photos back right after it, so it would feel impossible to get past. Never empty while [items] is not:
     * a library of nothing but such steps rotates whole.
     */
    fun of(items: List<CoverItem>, playlist: List<String>): List<CoverItem> {
        if (playlist.isEmpty()) {
            val steps = items.flatMapTo(HashSet()) { item -> if (item is CoverItem.Group) item.items.map { it.id } else emptyList() }
            return items.filter { it.id !in steps }.ifEmpty { items }
        }
        val byId = items.associateBy { it.id }
        return playlist.mapNotNull { byId[it] }.ifEmpty { items }
    }
}

object MediaStorage {
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    /**
     * Width / height of [media]'s picture as stored (a video's from its poster frame, falling back to the video
     * file itself), or 0 when it cannot be read. Reads only the header/metadata, so it is cheap, but still file
     * access: call it off the main thread.
     */
    fun aspect(context: Context, media: CoverItem.Media): Float {
        val name = if (media.kind == MediaKind.VIDEO) media.posterFileName else media.fileName
        if (name != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(file(context, name).path, bounds) }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) return bounds.outWidth.toFloat() / bounds.outHeight
        }
        if (media.kind == MediaKind.VIDEO) {
            runCatching {
                val f = file(context, media.fileName)
                if (f.exists()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(f.path)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (width > 0f && height > 0f) {
                            return if (rotation == 90 || rotation == 270) height / width else width / height
                        }
                    } finally {
                        retriever.release()
                    }
                }
            }
        }
        return 0f
    }

    /** Returns (mediaCount, totalBytes) of Zlip's internal media directory (`files/media/`). */
    fun stats(context: Context): Pair<Int, Long> {
        val files = dir(context).listFiles().orEmpty()
        var count = 0
        var bytes = 0L
        for (f in files) {
            if (!f.isFile) continue
            bytes += f.length()
            if (!f.name.endsWith(".jpg")) count++
        }
        return count to bytes
    }

    /** Exports [media] from Zlip's internal storage back to the phone's Gallery (`Pictures/Zlip` or `Movies/Zlip`). */
    suspend fun exportToGallery(context: Context, media: CoverItem.Media): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = file(context, media.fileName)
            if (!source.exists()) return@withContext false
            val isVideo = media.kind == MediaKind.VIDEO
            val ext = source.extension.lowercase().ifEmpty { if (isVideo) "mp4" else "webp" }
            val mime = when {
                isVideo -> "video/mp4"
                ext == "gif" -> "image/gif"
                else -> "image/webp"
            }
            val safeName = media.name.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().ifEmpty { "Zlip_${System.currentTimeMillis()}" }
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativeDir = if (isVideo) "${Environment.DIRECTORY_MOVIES}/Zlip" else "${Environment.DIRECTORY_PICTURES}/Zlip"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeName.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return@withContext false
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open MediaStore stream")
                out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
                val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, publish, null, null)
                true
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
        }.getOrDefault(false)
    }
}

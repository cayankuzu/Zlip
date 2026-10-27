package com.zlip.app.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import com.zlip.app.data.MediaTooLargeException
import com.zlip.app.data.NotEnoughSpaceException
import com.zlip.app.data.VideoConversionException
import com.zlip.app.render.Playback
import com.zlip.app.system.CoverTileService
import com.zlip.app.widget.CoverWidgets
import com.zlip.app.ui.components.AutoOffChip
import com.zlip.app.ui.components.CoverPipCard
import com.zlip.app.ui.components.LocalHovering
import com.zlip.app.ui.components.LocalStandbyHooks
import com.zlip.app.ui.components.StandbyHooks
import com.zlip.app.ui.components.StandbyOverlay
import com.zlip.app.ui.components.wakesStandby
import com.zlip.app.ui.collage.CollageEditorScreen
import com.zlip.app.ui.countdown.CountdownEditorScreen
import com.zlip.app.ui.group.GroupEditorScreen
import com.zlip.app.ui.qr.QrEditorScreen
import com.zlip.app.ui.toy.ToyEditorScreen
import com.zlip.app.ui.home.AddKind
import com.zlip.app.ui.home.HomeScreen
import com.zlip.app.ui.home.HomeSnack
import com.zlip.app.ui.home.LibraryActions
import com.zlip.app.ui.home.LibraryFilter
import com.zlip.app.ui.media.MediaEditorScreen
import com.zlip.app.ui.onboarding.OnboardingScreen
import com.zlip.app.ui.pixel.PixelEditorScreen
import com.zlip.app.ui.settings.SettingsScreen
import com.zlip.app.ui.text.TextEditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class PixelEditor(val initial: CoverItem.PixelArt?) : Screen

    /** [prefill]: the text a new item starts with (shared from another app). */
    data class TextEditor(val initial: CoverItem.Text?, val prefill: String = "") : Screen

    /** [fresh]: just imported, so leaving it unchanged keeps it and Home offers to undo the import. */
    data class MediaEditor(val initial: CoverItem.Media, val fresh: Boolean = false) : Screen
    data class CollageEditor(val initial: CoverItem.Collage?) : Screen

    /** [prefill]: library items a new group starts with (several files just picked from the gallery). */
    data class GroupEditor(val initial: CoverItem.Group?, val prefill: List<String> = emptyList()) : Screen
    data class CountdownEditor(val initial: CoverItem.Countdown?) : Screen

    /** [prefillUrl]: the link a new card starts with (shared from another app). */
    data class QrEditor(val initial: CoverItem.Qr?, val prefillUrl: String = "") : Screen
    data class ToyEditor(val initial: CoverItem.Toy?) : Screen

    /** Brought back after process death: becomes the editor for item [id] (null = new) once the library loads. */
    data class Restoring(val kind: String, val id: String?) : Screen
}

private val Screen.isEditor get() = this != Screen.Home && this != Screen.Settings

/** The group editor, or the one about to come back after process death. */
private val Screen.isGroupEditor get() = this is Screen.GroupEditor || (this is Screen.Restoring && kind == "group")

/** Item ids, in order, for saved state; a missing list stays missing. Ids never contain a comma. */
private val IdListSaver = Saver<List<String>?, String>(
    save = { ids -> ids?.joinToString(",") },
    restore = { saved -> if (saved.isEmpty()) emptyList() else saved.split(",") },
)

/** Keeps the open screen across process death as {kind, id}: never whole items, which can be large. */
private val ScreenSaver = Saver<Screen, String>(
    save = { screen ->
        JSONObject().apply {
            val (kind, item) = when (screen) {
                Screen.Home -> "home" to null
                Screen.Settings -> "settings" to null
                is Screen.PixelEditor -> "pixel" to screen.initial
                // A prefilled new item comes back through the editor's own saved state; a fresh import as a plain edit.
                is Screen.TextEditor -> "text" to screen.initial
                is Screen.MediaEditor -> "media" to screen.initial
                is Screen.CollageEditor -> "collage" to screen.initial
                // A prefilled new group comes back through the editor's own saved state.
                is Screen.GroupEditor -> "group" to screen.initial
                is Screen.CountdownEditor -> "countdown" to screen.initial
                is Screen.QrEditor -> "qr" to screen.initial
                is Screen.ToyEditor -> "toy" to screen.initial
                is Screen.Restoring -> {
                    put("kind", screen.kind)
                    if (screen.id != null) put("id", screen.id)
                    return@apply
                }
            }
            put("kind", kind)
            if (item != null) put("id", item.id)
        }.toString()
    },
    restore = { saved ->
        val json = JSONObject(saved)
        when (val kind = json.optString("kind")) {
            "settings" -> Screen.Settings
            "home", "" -> Screen.Home
            else -> Screen.Restoring(kind, json.optString("id").ifEmpty { null })
        }
    },
)

/** The editor a restored screen stands for, or Home when its item is gone. */
private fun Screen.Restoring.resolve(items: List<CoverItem>): Screen {
    val item = id?.let { wanted -> items.firstOrNull { it.id == wanted } }
    if (id != null && item == null) return Screen.Home
    return when (kind) {
        "pixel" -> Screen.PixelEditor(item as? CoverItem.PixelArt)
        "text" -> Screen.TextEditor(item as? CoverItem.Text)
        "media" -> (item as? CoverItem.Media)?.let { Screen.MediaEditor(it) } ?: Screen.Home
        "collage" -> Screen.CollageEditor(item as? CoverItem.Collage)
        "group" -> Screen.GroupEditor(item as? CoverItem.Group)
        "countdown" -> Screen.CountdownEditor(item as? CoverItem.Countdown)
        "qr" -> Screen.QrEditor(item as? CoverItem.Qr)
        "toy" -> Screen.ToyEditor(item as? CoverItem.Toy)
        else -> Screen.Home
    }
}

/**
 * Inner-screen navigation and app-wide UI state. Editors stream their unsaved state to the cover via
 * the repository's draft, and Back offers to save changes. [incoming] is content shared to Zlip or asked
 * for by a launcher shortcut, opened (and cleared) here. Every screen may go to standby; [onModalOpenChange]
 * says when one of our sheets, dialogs or menus holds the input focus, so standby can run under it.
 */
@Composable
fun ZlipRoot(
    app: ZlipApp,
    standby: StateFlow<Boolean>,
    settingsRequested: MutableStateFlow<Boolean>,
    incoming: MutableStateFlow<IncomingContent?>,
    onStartCover: () -> Unit,
    onStopCover: () -> Unit,
    onOpeningPicker: () -> Unit,
    onWake: () -> Unit,
    onStandbyAllowedChange: (Boolean) -> Unit,
    onImportingChange: (Boolean) -> Unit,
    onModalOpenChange: (Boolean) -> Unit,
    inPip: StateFlow<Boolean>,
    autoOffCountdown: StateFlow<Int?>,
    onEnterHover: () -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onEditorDirtyChange: (Boolean) -> Unit = {},
) {
    val repository = app.repository
    val items by repository.items.collectAsState()
    val playlist by repository.playlist.collectAsState()
    val selected by repository.selectedItem.collectAsState()
    // What the cover shows: for a group the step it is on, as the small window's Fit / Fill acts on that.
    val shownOnCover by app.playback.shownItem.collectAsState()
    // Where a group on the cover is ("Adım 2/5"), and whether there is another item to leave it for.
    val groupProgress by app.playback.groupProgress.collectAsState()
    val itemSkip by app.playback.itemSkipAvailable.collectAsState()
    val rotation by repository.sequence.collectAsState()
    val settings by repository.settings.collectAsState()
    val loaded by repository.loaded.collectAsState()
    val saveFailed by repository.saveFailed.collectAsState()
    val status by app.cover.status.collectAsState()
    val lastError by app.cover.lastError.collectAsState()
    val paused by app.playback.paused.collectAsState()
    val isStandby by standby.collectAsState()
    val openSettings by settingsRequested.collectAsState()
    val hovering by inPip.collectAsState()
    val autoOffSeconds by autoOffCountdown.collectAsState()
    val mirrorState by app.mirror.state.collectAsState()
    val shared by incoming.collectAsState()

    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    var editorDirty by rememberSaveable { mutableStateOf(false) }
    // The activity keeps Zlip from shrinking into its small window while there are unsaved edits.
    LaunchedEffect(editorDirty) { onEditorDirtyChange(editorDirty) }
    // Whether the open editor's state could be saved as it is (Save is offered when leaving only then).
    var editorCanSave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // A message for Home to show once it is there (Home owns the snackbar), e.g. "Added to your library · Undo".
    var homeSnack by remember { mutableStateOf<HomeSnack?>(null) }
    var importProgress by remember { mutableStateOf<Float?>(null) }
    // Which file of how many, while several picked files come in one after another.
    var importCount by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    // Several files just came in from the gallery: offer to make a group of them.
    var batchResult by remember { mutableStateOf<BatchResult?>(null) }
    // The gallery picking for the group editor: how many more steps fit (null: not picking for a group). Then the
    // files it brought in, until the editor takes them. Saved like the editor itself, so a pick that outlives the
    // activity (font size change, process death) still lands in the group.
    var pickingForGroup by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingGroupAdds by rememberSaveable(stateSaver = IdListSaver) { mutableStateOf<List<String>?>(null) }
    // The small window hides the editor, so the cover plays the playlist meanwhile (and its buttons can skip);
    // the newest unsaved draft waits here and goes back on the cover when Zlip is full size again.
    var parkedDraft by remember { mutableStateOf<CoverItem?>(null) }
    val currentHovering by rememberUpdatedState(hovering)
    var notificationsAllowed by remember { mutableStateOf(app.notifier.canPost()) }
    var permissionAskedAt by remember { mutableLongStateOf(0L) }
    val gridState = rememberLazyGridState()
    // Hoisted with the grid position, so returning from an editor keeps the library as the user left it.
    var libraryFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var widgetPlaced by remember { mutableStateOf(CoverWidgets.hasWidgets(context)) }

    // Battery: inner-screen previews only play while the app is visible, full size and awake. In the
    // background, as the small window or in standby they drop to a still frame (no decoders), and videos
    // never decode twice while the cover is already playing them.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val previewAllowed = lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !isStandby && !hovering
    val coverActive = status == CoverStatus.ACTIVE
    val heroPlayback = if (previewAllowed && !coverActive) Playback.LIVE else Playback.STILL
    val editorPlayback = if (previewAllowed) Playback.LIVE else Playback.STILL

    // Standby on every screen: editors count each draft change as a touch (typing never reaches the activity),
    // and our sheets, dialogs and menus count theirs. Only an import, which the user waits for, keeps it off.
    val standbyAllowed = importProgress == null && batchResult == null && (!loaded || settings.onboardingDone)
    LaunchedEffect(standbyAllowed) { onStandbyAllowedChange(standbyAllowed) }
    // An import runs here: the idle cutoff waits for it, and closing the small window must not cancel it.
    val importing = importJob != null
    LaunchedEffect(importing) { onImportingChange(importing) }
    val currentOnWake by rememberUpdatedState(onWake)
    val currentOnModalOpenChange by rememberUpdatedState(onModalOpenChange)
    val standbyHooks = remember { StandbyHooks(wake = { currentOnWake() }, onOpenChange = { currentOnModalOpenChange(it) }) }
    val onDraftChange: (CoverItem) -> Unit = remember(repository) {
        { item ->
            // A library change can still reach a hidden editor (Fit / Fill switched from the small window); as the
            // small window its draft must not take the cover over from the playlist.
            if (currentHovering) parkedDraft = item else repository.setDraft(item)
            currentOnWake()
        }
    }
    val onDirtyChange: (Boolean, Boolean) -> Unit = remember {
        { dirty, canSave ->
            editorDirty = dirty
            editorCanSave = canSave
        }
    }
    // The keyboard is a window of its own; it goes with the rest of the screen.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isStandby) { if (isStandby) focusManager.clearFocus() }
    var backWakes by remember { mutableStateOf(false) }
    LaunchedEffect(isStandby) {
        if (isStandby) {
            backWakes = true
        } else {
            delay(WAKE_BACK_GRACE_MS)
            backWakes = false
        }
    }

    LifecycleResumeEffect(Unit) {
        notificationsAllowed = app.notifier.canPost()
        widgetPlaced = CoverWidgets.hasWidgets(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(openSettings) {
        if (openSettings) {
            settingsRequested.value = false
            if (!screen.isEditor) screen = Screen.Settings
        }
    }

    LaunchedEffect(saveFailed) {
        if (saveFailed) Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show()
    }

    // The system page alone does not say what to turn on there; a line first does.
    fun openNotificationSettings() {
        Toast.makeText(context, R.string.notif_settings_toast, Toast.LENGTH_LONG).show()
        context.startActivity(app.notifier.settingsIntent())
    }

    var permissionInFlight by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionInFlight = false
        notificationsAllowed = app.notifier.canPost()
        // An instant "denied" means the system did not even show its dialog (denied for good), and a grant
        // that still cannot post means the channel is switched off: both are fixed in system settings.
        val answeredInstantly = SystemClock.uptimeMillis() - permissionAskedAt < INSTANT_ANSWER_MS
        if ((!granted && answeredInstantly) || (granted && !notificationsAllowed)) openNotificationSettings()
    }
    val requestNotifications: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            openNotificationSettings()
        } else if (!permissionInFlight) {
            // A second launch while the dialog is pending is cancelled instantly; never read that as "denied".
            permissionInFlight = true
            permissionAskedAt = SystemClock.uptimeMillis()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Runs an import as THE import: the dialog, its Cancel and the standby follow [importJob]. Started only once
     * it is set, and its end clears the shared state only while it is still the current one.
     */
    fun startImport(block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        importJob = job
        job.start()
    }

    fun isCurrentImport(running: CoroutineScope) = importJob === running.coroutineContext[Job]

    fun importUri(uri: Uri) {
        startImport {
            // The user waits without touching anything: an import is activity, so the idle cutoff waits too.
            currentOnWake()
            importProgress = -1f
            try {
                repository.importMedia(uri) { importProgress = it }
                    .onSuccess { item ->
                        // Never replace an editor the user opened meanwhile; the item is in the library either way.
                        if (screen == Screen.Home) {
                            editorDirty = false
                            screen = Screen.MediaEditor(item, fresh = true)
                        } else {
                            Toast.makeText(context, R.string.media_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        val message = when (error) {
                            is MediaTooLargeException -> context.getString(R.string.import_too_large, error.limitMb)
                            is NotEnoughSpaceException -> context.getString(R.string.import_no_space, error.neededMb)
                            is VideoConversionException -> context.getString(R.string.import_video_failed)
                            else -> context.getString(R.string.import_failed)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
            } finally {
                if (isCurrentImport(this)) {
                    importProgress = null
                    importJob = null
                }
            }
        }
    }

    /** Imports several picked files one after another, in picked order, without an editor for each. */
    fun importBatch(uris: List<Uri>, onDone: (added: List<String>, failed: Int) -> Unit) {
        startImport {
            val added = mutableListOf<String>()
            var failed = 0
            // A full phone fails every file after it the same way: said once, with what it needs.
            var noSpace: NotEnoughSpaceException? = null
            try {
                uris.forEachIndexed { index, uri ->
                    // Each file counts as activity (a batch of videos outlasts the idle cutoff); one file is short.
                    currentOnWake()
                    importCount = index + 1 to uris.size
                    importProgress = -1f
                    repository.importMedia(uri, select = false) { importProgress = it }
                        .onSuccess { added += it.id }
                        .onFailure { error ->
                            failed++
                            if (error is NotEnoughSpaceException) noSpace = error
                        }
                }
            } finally {
                if (isCurrentImport(this)) {
                    importProgress = null
                    importCount = null
                    importJob = null
                }
                noSpace?.let { Toast.makeText(context, context.getString(R.string.import_no_space, it.neededMb), Toast.LENGTH_LONG).show() }
                // After Cancel too: whatever came in so far is in the library.
                onDone(added, failed)
            }
        }
    }

    fun showFailures(failed: Int) {
        if (failed > 0) Toast.makeText(context, resources.getQuantityString(R.plurals.import_batch_failed, failed, failed), Toast.LENGTH_LONG).show()
    }

    /** Several files came in together: on Home the user can make a group of them. */
    fun offerBatch(added: List<String>, failed: Int) {
        when {
            added.isEmpty() -> showFailures(failed)
            screen == Screen.Home -> batchResult = BatchResult(added, failed)
            else -> {
                repository.joinPlaylist(added)
                Toast.makeText(context, R.string.media_added, Toast.LENGTH_SHORT).show()
                showFailures(failed)
            }
        }
    }

    // One file: today's flow (it opens in the media editor). Several: they come in together, then the
    // user can make a group of them.
    val pickGallery = rememberLauncherForActivityResult(remember { ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_PICK) }) { uris ->
        when {
            uris.size == 1 -> importUri(uris.single())
            uris.size > 1 -> importBatch(uris, ::offerBatch)
        }
    }
    val pickForGroup = rememberLauncherForActivityResult(remember { ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_PICK) }) { uris ->
        val room = pickingForGroup
        pickingForGroup = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // The picker cannot be told how much room is left, so the rest is left out before converting anything.
        val (accepted, skipped) = uris.takeAtMost(room ?: uris.size)
        if (skipped > 0) {
            val message = resources.getQuantityString(R.plurals.group_gallery_skipped, skipped, skipped, CoverItem.Group.MAX_ITEMS)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (accepted.isEmpty()) return@rememberLauncherForActivityResult
        importBatch(accepted) { added, failed ->
            // The group editor (back after a recreation, too) takes them; anywhere else they are in the library anyway.
            when {
                added.isEmpty() -> Unit
                room != null && screen.isGroupEditor -> pendingGroupAdds = pendingGroupAdds.orEmpty() + added
                else -> Toast.makeText(context, R.string.media_added, Toast.LENGTH_SHORT).show()
            }
            showFailures(failed)
        }
    }

    val goHome = {
        editorDirty = false
        confirmDiscard = false
        // Every way out of the group editor comes through here: files it did not take never reach another group.
        pendingGroupAdds = null
        screen = Screen.Home
    }
    val requestBack: () -> Unit = {
        val freshImport = (screen as? Screen.MediaEditor)?.takeIf { it.fresh }?.initial
        when {
            isStandby -> onWake()
            screen.isEditor && editorDirty -> confirmDiscard = true
            // Back on a just-imported file means "done": it is already in the library and on the cover. No question;
            // Home says so and offers Undo in case it was the wrong pick.
            freshImport != null -> {
                goHome()
                homeSnack = HomeSnack(
                    message = context.getString(R.string.home_added),
                    actionLabel = context.getString(R.string.action_undo),
                    onAction = { repository.delete(freshImport.id) },
                )
            }
            else -> goHome()
        }
    }
    val save: (CoverItem) -> Unit = { item ->
        repository.upsert(item)
        repository.select(item.id)
        goHome()
    }

    // Shared to Zlip or asked for by a shortcut. An editor with unsaved changes is never replaced: this waits
    // until the user has left it, and so does an import, the batch dialog, and what they just opened (a fresh
    // import's editor, a group being filled from the gallery or from "Grup yap").
    // Anything else gives way, so the shared content opens right away.
    LaunchedEffect(shared, loaded, screen, editorDirty, importJob, batchResult, pendingGroupAdds) {
        // The flow itself, not [shared]: that may still hold what was just taken when going Home restarts this.
        val content = incoming.value ?: return@LaunchedEffect
        val busy = importJob != null || batchResult != null || pendingGroupAdds != null ||
            (screen as? Screen.MediaEditor)?.fresh == true || (screen as? Screen.GroupEditor)?.prefill?.isNotEmpty() == true
        // A restored editor must compose (and take back its saved state) before it may be left: otherwise that
        // unclaimed state would come back in the next new editor of its kind.
        if (!loaded || busy || screen is Screen.Restoring || (screen.isEditor && editorDirty)) return@LaunchedEffect
        // Home first, for one frame: the old editor and all its saved state must be gone, or an editor of the
        // same kind would carry on with the old item (its id, so saving would overwrite it). [screen] is a key,
        // so this runs again once Home is composed, and only then takes the content.
        if (screen != Screen.Home) {
            goHome()
            return@LaunchedEffect
        }
        incoming.value = null
        when (content) {
            is IncomingContent.Media -> {
                // Like a gallery pick: each file takes a moment (videos are converted), so a big share is cut.
                val (uris, skipped) = content.uris.takeAtMost(MAX_GALLERY_PICK)
                if (skipped > 0) {
                    val message = resources.getQuantityString(R.plurals.share_skipped, skipped, skipped, MAX_GALLERY_PICK)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                when (uris.size) {
                    1 -> importUri(uris.single())
                    else -> importBatch(uris, ::offerBatch)
                }
            }
            is IncomingContent.Link -> screen = Screen.QrEditor(null, prefillUrl = content.url)
            is IncomingContent.Text -> screen = Screen.TextEditor(null, prefill = content.text)
        }
    }

    val showingOnboarding = loaded && !settings.onboardingDone
    BackHandler(enabled = !showingOnboarding && screen != Screen.Home, onBack = requestBack)
    // Introduction re-opened from Settings: Back on its first page closes it and returns there.
    BackHandler(enabled = showingOnboarding && screen != Screen.Home) {
        repository.updateSettings { it.copy(onboardingDone = true) }
    }
    // Back on Home would leave Zlip and so end the cover; with the small window on, Zlip shrinks into it instead.
    BackHandler(
        enabled = !showingOnboarding && screen == Screen.Home && coverActive && settings.keepCoverInOtherApps && coverHoverSupported,
        onBack = { if (isStandby) onWake() else onEnterHover() },
    )
    DisposableEffect(screen) {
        onDispose {
            repository.setDraft(null)
            // A draft parked by the editor that just closed must never come back on the next screen.
            parkedDraft = null
        }
    }
    LaunchedEffect(hovering) {
        if (hovering) {
            // Unless the editor already parked a newer one since the small window began.
            parkedDraft = parkedDraft ?: repository.draft.value
            repository.setDraft(null)
        } else {
            if (screen.isEditor) parkedDraft?.let(repository::setDraft)
            parkedDraft = null
        }
    }
    LaunchedEffect(loaded, screen) {
        val restoring = screen as? Screen.Restoring ?: return@LaunchedEffect
        if (loaded) screen = restoring.resolve(items)
    }

    // Our sheets, dialogs and menus (in every screen below) report their touches for the standby through this,
    // and stay away while Zlip is the small window.
    CompositionLocalProvider(LocalStandbyHooks provides standbyHooks, LocalHovering provides hovering) {
        // As the small window only its card is drawn. The app stays composed underneath (hidden, previews still),
        // so an open editor keeps its unsaved work for when Zlip is full size again.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (hovering) 0f else 1f }) {
            // The introduction replaces the app (not drawn over it) so touches and TalkBack cannot reach what is behind.
            if (showingOnboarding) {
                OnboardingScreen(
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotificationPermission = requestNotifications,
                    onDone = { repository.updateSettings { it.copy(onboardingDone = true) } },
                )
            } else when (val current = screen) {
                Screen.Home -> HomeScreen(
                    items = items,
                    playlist = playlist,
                    rotation = rotation,
                    selectedId = selected?.id,
                    shownOnCover = shownOnCover,
                    groupProgress = groupProgress,
                    itemSkip = itemSkip,
                    status = status,
                    settings = settings,
                    paused = paused,
                    mirror = mirrorState,
                    lastError = lastError,
                    heroPlayback = heroPlayback,
                    widgetPlaced = widgetPlaced,
                    gridState = gridState,
                    loaded = loaded,
                    filter = libraryFilter,
                    onFilterChange = { libraryFilter = it },
                    library = remember(repository) {
                        LibraryActions(
                            onSelect = repository::select,
                            onEdit = { item ->
                                editorDirty = false
                                screen = when (item) {
                                    is CoverItem.PixelArt -> Screen.PixelEditor(item)
                                    is CoverItem.Text -> Screen.TextEditor(item)
                                    is CoverItem.Media -> Screen.MediaEditor(item)
                                    is CoverItem.Collage -> Screen.CollageEditor(item)
                                    is CoverItem.Countdown -> Screen.CountdownEditor(item)
                                    is CoverItem.Qr -> Screen.QrEditor(item)
                                    is CoverItem.Toy -> Screen.ToyEditor(item)
                                    is CoverItem.Group -> Screen.GroupEditor(item)
                                }
                            },
                            onDelete = repository::deleteAll,
                            onAddToPlaylist = repository::addToPlaylist,
                            onRemoveFromPlaylist = repository::removeFromPlaylist,
                            onMoveInPlaylist = repository::movePlaylistItem,
                            onClearPlaylist = repository::clearPlaylist,
                            onSetPlaylist = repository::setPlaylist,
                        )
                    },
                    snack = homeSnack,
                    onSnackTaken = { homeSnack = null },
                    onStartCover = onStartCover,
                    onStopCover = onStopCover,
                    onShrinkToMini = onEnterHover,
                    onMirrorChange = onMirrorChange,
                    onTogglePause = app.playback::togglePause,
                    onNext = app.playback::next,
                    onPrevious = app.playback::previous,
                    onNextItem = app.playback::nextItem,
                    onPreviousItem = app.playback::previousItem,
                    onSettingsChange = { updated -> repository.updateSettings { updated } },
                    onAdd = { kind ->
                        editorDirty = false
                        when (kind) {
                            AddKind.PIXEL -> screen = Screen.PixelEditor(null)
                            AddKind.TEXT -> screen = Screen.TextEditor(null)
                            AddKind.COLLAGE -> screen = Screen.CollageEditor(null)
                            AddKind.GROUP -> screen = Screen.GroupEditor(null)
                            AddKind.COUNTDOWN -> screen = Screen.CountdownEditor(null)
                            AddKind.QR -> screen = Screen.QrEditor(null)
                            AddKind.TOY -> screen = Screen.ToyEditor(null)
                            AddKind.GALLERY -> {
                                onOpeningPicker()
                                pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                        }
                    },
                    onOpenSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    notificationsAllowed = notificationsAllowed,
                    widgetPlaced = widgetPlaced,
                    onSettingsChange = { updated -> repository.updateSettings { updated } },
                    onRequestNotificationPermission = requestNotifications,
                    onAddTile = {
                        val statusBar = context.getSystemService(StatusBarManager::class.java)
                        statusBar.requestAddTileService(
                            ComponentName(context, CoverTileService::class.java),
                            context.getString(R.string.tile_label),
                            Icon.createWithResource(context, R.drawable.ic_stat_zlip),
                            context.mainExecutor,
                        ) { result ->
                            val message = when (result) {
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_not_added
                                else -> R.string.tile_add_failed
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShowOnboarding = { repository.updateSettings { it.copy(onboardingDone = false) } },
                    onBack = goHome,
                )
                is Screen.PixelEditor -> PixelEditorScreen(
                    initial = current.initial,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
                is Screen.TextEditor -> TextEditorScreen(
                    initial = current.initial,
                    previewPlayback = editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                    prefill = current.prefill,
                )
                // A moment after process death, until the library is back; then the editor opens as it was.
                is Screen.Restoring -> Unit
                is Screen.CollageEditor -> CollageEditorScreen(
                    initial = current.initial,
                    library = items,
                    // Plays even while the cover does: the cover faces away while the phone is open, and changing the
                    // interval must show here. Mostly photos, so it costs little.
                    previewPlayback = editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
                is Screen.GroupEditor -> GroupEditorScreen(
                    initial = current.initial,
                    prefill = current.prefill,
                    library = items,
                    hasPlaylist = playlist.isNotEmpty(),
                    // Plays even while the cover does: the cover faces away while the phone is open, and its ‹ › and
                    // the step rows step this preview. Mostly photos, so it costs little.
                    previewPlayback = editorPlayback,
                    onAddFromGallery = { room ->
                        pickingForGroup = room
                        onOpeningPicker()
                        pickForGroup.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    pendingAdds = pendingGroupAdds,
                    onPendingAddsConsumed = { pendingGroupAdds = null },
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
                is Screen.CountdownEditor -> CountdownEditorScreen(
                    initial = current.initial,
                    previewPlayback = editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
                is Screen.QrEditor -> QrEditorScreen(
                    initial = current.initial,
                    previewPlayback = editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                    prefillUrl = current.prefillUrl,
                )
                is Screen.ToyEditor -> ToyEditorScreen(
                    initial = current.initial,
                    previewPlayback = editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
                is Screen.MediaEditor -> MediaEditorScreen(
                    initial = current.initial,
                    // The cover is already decoding the video draft; a second decoder here would only cost battery.
                    previewPlayback = if (coverActive && current.initial.kind == MediaKind.VIDEO) Playback.STILL else editorPlayback,
                    onDraftChange = onDraftChange,
                    onDirtyChange = onDirtyChange,
                    onSave = save,
                    onBack = requestBack,
                )
            }

            // Composed afresh each time standby starts, so it comes before every other Back handler: Back
            // wakes the screen instead of leaving Zlip (which would end the cover). It stays a moment after standby
            // ends, because Android counts the start of a Back as interaction (so standby is already over by the
            // time the Back itself arrives, seen on Android 17 with the navigation buttons).
            if (backWakes) BackHandler(onBack = onWake)
            AnimatedVisibility(visible = isStandby, enter = fadeIn(), exit = fadeOut()) {
                StandbyOverlay(onWake = onWake)
            }

            AutoOffChip(
                seconds = autoOffSeconds,
                onKeepOn = onWake,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp),
            )
        }

        if (hovering) {
            CoverPipCard(
                item = shownOnCover ?: selected,
                settings = settings,
                status = status,
                paused = paused,
                mirroring = mirrorState == MirrorState.ON,
            )
        }

        // Dialogs are windows of their own; they wait until Zlip is full size again.
        if (confirmDiscard && !hovering) {
            // Saves exactly what the cover shows: the editor's draft.
            val draft = repository.draft.collectAsState().value?.takeIf { editorCanSave }
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                modifier = Modifier.wakesStandby(),
                // "Save changes?" only where Save is offered.
                title = { Text(stringResource(if (draft != null) R.string.discard_title else R.string.discard_title_unsavable)) },
                text = if (draft == null) {
                    { Text(stringResource(R.string.discard_body)) }
                } else {
                    null
                },
                // Every button says what it does, and the one that throws work away never reads like "Kaydet" next to it
                // ("Kaydetme" did): [Düzenlemeye dön] [Kaydetmeden çık] [Kaydet], or [Düzenlemeye dön] [Sil ve çık].
                confirmButton = {
                    Row {
                        TextButton(onClick = goHome) {
                            Text(
                                text = stringResource(if (draft != null) R.string.discard_leave else R.string.discard_delete_leave),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (draft != null) {
                            TextButton(onClick = { save(draft) }) { Text(stringResource(R.string.action_save)) }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.discard_keep_editing)) }
                },
            )
        }

        // A long video takes minutes to convert: the screen must not time out (and stop the cover) meanwhile.
        if (importProgress != null) KeepScreenOn()
        importProgress?.takeUnless { hovering }?.let { progress ->
            val count = importCount?.takeIf { (_, total) -> total > 1 }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (count != null) stringResource(R.string.import_batch_title, count.first, count.second) else stringResource(R.string.import_title)) },
                text = {
                    Column {
                        Text(stringResource(if (progress >= 0f) R.string.import_video_body else R.string.import_body))
                        Spacer(Modifier.height(16.dp))
                        if (progress >= 0f) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { importJob?.cancel() }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        batchResult?.takeUnless { hovering }?.let { result ->
            val added = result.ids.size
            // Kept as separate items: like anything new, they join a custom playlist.
            val keepSeparate = {
                repository.joinPlaylist(result.ids)
                batchResult = null
            }
            AlertDialog(
                onDismissRequest = keepSeparate,
                title = { Text(pluralStringResource(R.plurals.import_batch_added, added, added)) },
                text = if (added >= 2 || result.failed > 0) {
                    {
                        Column {
                            if (added >= 2) Text(stringResource(R.string.import_batch_body))
                            if (added >= 2 && result.failed > 0) Spacer(Modifier.height(8.dp))
                            if (result.failed > 0) {
                                Text(
                                    text = pluralStringResource(R.plurals.import_batch_failed, result.failed, result.failed),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                confirmButton = {
                    if (added >= 2) {
                        TextButton(onClick = {
                            batchResult = null
                            editorDirty = false
                            screen = Screen.GroupEditor(null, prefill = result.ids)
                        }) { Text(stringResource(R.string.import_batch_make_group)) }
                    } else {
                        TextButton(onClick = keepSeparate) { Text(stringResource(R.string.action_done)) }
                    }
                },
                // "Tamam" did not say that it means "keep them as separate items".
                dismissButton = if (added >= 2) {
                    { TextButton(onClick = keepSeparate) { Text(stringResource(R.string.batch_keep_separate)) } }
                } else {
                    null
                },
            )
        }
    }
}

/** Files that came in together from the gallery ([ids] in picked order), and how many could not. */
private class BatchResult(val ids: List<String>, val failed: Int)

/** A permission result faster than this came without a dialog: the system had already refused. */
private const val INSTANT_ANSWER_MS = 400L

/** Most files one gallery pick brings in; each still takes a moment (videos are converted). */
private const val MAX_GALLERY_PICK = 20

/** How long after standby ends a Back still only wakes the screen (its start already ended standby). */
private const val WAKE_BACK_GRACE_MS = 600L

/** Keeps the screen on while this is shown, so a long video conversion is never cut short by the screen timeout. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

package com.zlip.app.render

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaFraming
import com.zlip.app.data.MediaImport
import com.zlip.app.data.MediaKind
import com.zlip.app.data.MediaPlacement
import com.zlip.app.data.MediaStorage
import com.zlip.app.data.framed
import com.zlip.app.data.framing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How much work a [CoverContent] may do. */
enum class Playback {
    /** Animate and play. */
    LIVE,

    /** Hold the current frame (user paused). */
    PAUSED,

    /** Thumbnail: a still frame and no decoders at all, the cheapest option. */
    STILL,
}

/** Frame rate for text scrolling; the cover screen provides the power mode's value. */
val LocalAnimationFps = staticCompositionLocalOf { 30 }

/** True only inside the cover presentation, where video can use a cheaper hardware overlay. */
val LocalVideoOverlay = staticCompositionLocalOf { false }

/**
 * True where clips with audio ([CoverItem.Media.playsSound]) may be heard: only on the cover itself (open-phone
 * presentation and folded live view), while its sound is on and the screen saver is not resting. Inner-screen
 * previews, thumbnails and collage cells stay silent. Not static: the cover's sound button flips it, and only
 * the players need to hear about that.
 */
val LocalAllowSound = compositionLocalOf { false }

/**
 * Told when a clip that was heard lost its sound for good (another app took the audio, headphones were
 * unplugged), so the cover's sound goes off and its button says so. Null where nobody keeps that state.
 */
val LocalOnSoundLost = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * How the cover shows a photo or video right now: while fingers frame it there, ahead of saving it, the framing they
 * left; otherwise its own. Null everywhere else, where every item shows as stored. Read while composing, so only the
 * picture being framed recomposes as the fingers move.
 */
val LocalMediaFraming = staticCompositionLocalOf<((CoverItem.Media) -> MediaFraming)?> { null }

/**
 * Where a clip reports that it is heard: the events of the outermost item, so a group's steps (whose own
 * events stop at the group) still tell the cover that someone is listening.
 */
private val LocalHeardBy = staticCompositionLocalOf<PlaybackEvents?> { null }

/** What a rendered item reports back; drives smart auto-advance. */
interface PlaybackEvents {
    /** A video, GIF, scrolling text, animation, collage or group completed a full pass. */
    fun onCycle()

    /** A play-once video or GIF reached its end and holds its last frame ([ended] true), or plays again. */
    fun onEnded(ended: Boolean)

    /** The media could not be played ([failed] true), or plays again after an earlier failure. */
    fun onFailed(failed: Boolean)

    /**
     * A clip is really heard ([audible] true: sound on, playing, audio focus held), or went quiet (ended,
     * failed, paused, lost the audio, left the screen). Every true is followed by one false.
     */
    fun onAudible(audible: Boolean) {}

    /** A group now shows [child], so the cover can treat the step like that item (a QR code stays bright). */
    fun onStep(child: CoverItem) {}

    /**
     * A group now shows [child] at [position] (1-based) of the [count] steps in this pass's play order, so a shuffled
     * group still counts 1, 2, 3. Defaults to [onStep] for listeners that only need the step.
     */
    fun onStepAt(child: CoverItem, position: Int, count: Int) {
        onStep(child)
    }

    /** A group on screen lets next/previous move through its steps, until [detachStepper]. */
    fun attachStepper(stepper: GroupStepper) {}

    fun detachStepper(stepper: GroupStepper) {}
}

/** Stories-style stepping through the group on screen. */
interface GroupStepper {
    /** Shows the next step; at the last one only with [wrap] (back to the first), otherwise returns false. */
    fun next(wrap: Boolean): Boolean

    /**
     * Shows the previous step. At step one of a later pass that is the last step of the pass before (the step just
     * seen); at the very first one only with [wrap] (on to the last), otherwise returns false.
     */
    fun previous(wrap: Boolean): Boolean

    /**
     * Jumps to the group's item at [itemIndex] (in the group's list), at its place in this pass's play order.
     * False when there is no such item, or the group cannot jump.
     */
    fun showItem(itemIndex: Int): Boolean = false
}

/** Renders any [CoverItem]. Used on the cover screen and for previews/thumbnails on the inner screen. */
@Composable
fun CoverContent(
    item: CoverItem?,
    modifier: Modifier = Modifier,
    playback: Playback = Playback.LIVE,
    events: PlaybackEvents? = null,
    /** Toys answer taps (the cover, unlocked; editor previews). Thumbnails and collage cells never do. */
    interactive: Boolean = false,
) {
    when (item) {
        null -> EmptyCover(modifier)
        is CoverItem.PixelArt -> key(item.id) { PixelArtCover(item, modifier, playback, events) }
        // Keyed so the next text item starts its marquee from the beginning, while live edits of the same
        // item (same id) keep scrolling smoothly.
        is CoverItem.Text -> key(item.id) { TextCover(item, modifier, playback, events) }
        is CoverItem.Media -> key(item.fileName) {
            // Framed live on the cover: only the framing changes, so the same player or decoder carries on.
            val shown = LocalMediaFraming.current?.invoke(item)?.takeIf { it != item.framing }?.let(item::framed) ?: item
            if (shown.kind == MediaKind.VIDEO) VideoCover(shown, modifier, playback, events) else ImageCover(shown, modifier, playback, events)
        }
        is CoverItem.Collage -> key(item.id) { CollageCover(item, modifier, playback, events) }
        is CoverItem.Countdown -> CountdownCover(item, modifier, playback)
        is CoverItem.Qr -> QrCover(item, modifier)
        is CoverItem.Toy -> key(item.id) { ToyCover(item, modifier, playback, interactive) }
        is CoverItem.Group -> key(item.id) {
            CompositionLocalProvider(LocalHeardBy provides (LocalHeardBy.current ?: events)) {
                GroupCover(item, modifier, playback, events)
            }
        }
    }
}

@Composable
private fun EmptyCover(modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.cover_empty),
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Draws a pixel grid as large as fits, centered. Cells snap to whole device pixels when they are at
 * least 1 px, so pixel art stays sharp with no seams. [gridColor] draws cell borders (editor use).
 */
@Composable
fun PixelArtView(
    art: CoverItem.PixelArt,
    modifier: Modifier = Modifier,
    gridColor: Color? = null,
) {
    Canvas(modifier.background(Color(art.background))) {
        val raw = min(size.width / art.width, size.height / art.height)
        val cell = if (raw >= 1f) floor(raw) else raw
        val left = floor((size.width - cell * art.width) / 2f)
        val top = floor((size.height - cell * art.height) / 2f)
        val cellSize = Size(cell, cell)
        for (y in 0 until art.height) {
            for (x in 0 until art.width) {
                val argb = art[x, y]
                if (argb != 0) {
                    drawRect(Color(argb), topLeft = Offset(left + x * cell, top + y * cell), size = cellSize)
                }
            }
        }
        if (gridColor != null && cell >= 4f) {
            val right = left + cell * art.width
            val bottom = top + cell * art.height
            for (x in 0..art.width) {
                val gx = left + x * cell
                drawLine(gridColor, Offset(gx, top), Offset(gx, bottom), strokeWidth = 1f)
            }
            for (y in 0..art.height) {
                val gy = top + y * cell
                drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 1f)
            }
        }
    }
}

private sealed interface Decoded {
    data object Loading : Decoded
    data object Failed : Decoded
    data class Ready(val drawable: Drawable) : Decoded
}

/** Longest edge we decode to unzoomed; the cover is 748 px wide, so anything larger only costs memory. */
private const val MAX_DECODE_EDGE = 1024

/** How the cover's video sound identifies itself to the system (media volume, film content). */
private val VIDEO_AUDIO = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

private val PLATFORM_VIDEO_AUDIO = android.media.AudioAttributes.Builder()
    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
    .build()

/** One 60 Hz frame; the marquee sleeps the rest of its frame budget and then waits for vsync. */
private const val VSYNC_MS = 16L

/** Still text never shrinks below this to fit. */
private const val MIN_TEXT_SP = 12

/** How quickly a video's poster gives way to the playing video. */
private const val POSTER_FADE_MS = 120

/** Photo or GIF/animated WebP. Callers key this by file name so a new file starts from Loading. */
@Composable
private fun ImageCover(item: CoverItem.Media, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    if (playback == Playback.STILL) StillImage(item, modifier) else LiveImage(item, modifier, playback, events)
}

/**
 * Thumbnail: only the first frame, decoded at the size it is drawn, as a plain bitmap. No animation
 * decoder or frame buffers stay alive, so a grid full of GIFs costs no more than a grid of photos.
 */
@Composable
private fun StillImage(item: CoverItem.Media, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Clipped: a cropped or zoomed picture is laid out larger than the box (see placedAt).
    BoxWithConstraints(modifier.background(Color(item.background)).clipToBounds(), contentAlignment = Alignment.Center) {
        val boxWidth = with(density) { maxWidth.roundToPx() }
        val boxHeight = with(density) { maxHeight.roundToPx() }
        val decodeZoom = decodeZoom(item.zoom)
        val still by produceState<Any?>(null, boxWidth, boxHeight, item.fit, decodeZoom) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(MediaStorage.file(context, item.fileName))) { decoder, info, _ ->
                        decodeForBox(decoder, info.size.width, info.size.height, boxWidth, boxHeight, item.fit, decodeZoom)
                    }.asImageBitmap()
                }.getOrElse { Decoded.Failed }
            }
        }
        when (val state = still) {
            is ImageBitmap -> {
                val placement = item.placementIn(state.width.toFloat() / state.height.coerceAtLeast(1), boxWidth, boxHeight)
                Image(
                    bitmap = state,
                    contentDescription = null,
                    contentScale = when {
                        placement != null -> ContentScale.FillBounds
                        item.fit == MediaFit.FILL -> ContentScale.Crop
                        else -> ContentScale.Fit
                    },
                    filterQuality = if (item.crisp) FilterQuality.None else FilterQuality.Low,
                    modifier = Modifier.fillMaxSize().placedAt(placement),
                )
            }
            Decoded.Failed -> Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.Gray)
            else -> Unit
        }
    }
}

/**
 * Where a picture of [aspect] (width / height) lands in a [boxWidth] x [boxHeight] px box, framed like [this] item
 * ([MediaFocus]); null if unknown or too large to lay out (a layout size is limited; such a picture is simply
 * scaled into the box, centred).
 */
private fun CoverItem.Media.placementIn(aspect: Float, boxWidth: Int, boxHeight: Int): MediaPlacement? =
    MediaFocus.place(aspect, boxWidth.toFloat(), boxHeight.toFloat(), framing)
        ?.takeIf { max(it.width, it.height) <= MAX_PLACED_EDGE }

/**
 * Lays the picture out at [placement] (px, relative to the box), however much larger than the box it is: the
 * same for photos, GIFs, videos and their posters, so all of them show the part the user framed. The box clips
 * it (a hardware video overlay cannot be clipped; the cover window crops it instead). Rounded outwards, so the
 * picture never leaves a hairline of the box uncovered. Null leaves the content to fill the box.
 */
private fun Modifier.placedAt(placement: MediaPlacement?): Modifier = if (placement == null) {
    this
} else {
    layout { measurable, constraints ->
        val left = floor(placement.left).toInt()
        val top = floor(placement.top).toInt()
        val width = (ceil(placement.left + placement.width).toInt() - left).coerceAtLeast(1)
        val height = (ceil(placement.top + placement.height).toInt() - top).coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(
            if (constraints.hasBoundedWidth) constraints.maxWidth else width,
            if (constraints.hasBoundedHeight) constraints.maxHeight else height,
        ) { placeable.place(left, top) }
    }
}

/** Longest side a placed picture is laid out at: both sides then fit in layout constraints, which cannot hold two very long ones. */
private const val MAX_PLACED_EDGE = 32_000

/**
 * The zoom a picture is decoded for: in a few steps, so a pinch decodes again at most twice on its way in, and
 * a zoomed-in photo is sharp rather than a stretched cover-sized bitmap.
 */
private fun decodeZoom(zoom: Float): Float = when {
    zoom <= MediaFocus.MIN_ZOOM -> MediaFocus.MIN_ZOOM
    zoom <= 2f -> 2f
    else -> MediaFocus.MAX_ZOOM
}

/**
 * Decodes exactly as many pixels as the box shows: FIT must cover the box's limiting side, FILL (crop)
 * the other one, both [zoom] times closer. Only ever scales down, so nothing is decoded small and stretched up
 * afterwards. Zoomed in, the cap is the stored photo's own size, so the detail the file has shows.
 */
private fun decodeForBox(decoder: ImageDecoder, width: Int, height: Int, boxWidth: Int, boxHeight: Int, fit: MediaFit, zoom: Float) {
    if (boxWidth <= 0 || boxHeight <= 0) return
    val sx = boxWidth.toFloat() / width
    val sy = boxHeight.toFloat() / height
    val shown = (if (fit == MediaFit.FILL) max(sx, sy) else min(sx, sy)) * MediaFocus.clampZoom(zoom)
    val cap = if (zoom > MediaFocus.MIN_ZOOM) MediaImport.MAX_PHOTO_EDGE else MAX_DECODE_EDGE
    val scale = minOf(shown, cap.toFloat() / max(width, height).coerceAtLeast(1))
    if (scale < 1f) {
        decoder.setTargetSize(ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1))
    }
}

@Composable
private fun LiveImage(item: CoverItem.Media, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Clipped: a cropped or zoomed picture is laid out larger than the box (see placedAt).
    BoxWithConstraints(modifier.background(Color(item.background)).clipToBounds(), contentAlignment = Alignment.Center) {
        val boxWidth = with(density) { maxWidth.roundToPx() }
        val boxHeight = with(density) { maxHeight.roundToPx() }
        LiveImageContent(item, playback, events, context, boxWidth, boxHeight)
    }
}

@Composable
private fun LiveImageContent(
    item: CoverItem.Media,
    playback: Playback,
    events: PlaybackEvents?,
    context: android.content.Context,
    boxWidth: Int,
    boxHeight: Int,
) {
    // A photo is decoded for the way it is framed (a new decode simply replaces the old picture). An animation is
    // decoded once for any framing (as large as Fill needs), since decoding it again would start it over: changing
    // its framing, live with two fingers too, never restarts a GIF.
    val animation = item.kind == MediaKind.ANIMATION
    val decodeFit = if (animation) MediaFit.FILL else item.fit
    val decodeZoom = if (animation) MediaFocus.MIN_ZOOM else decodeZoom(item.zoom)
    val decoded by produceState<Decoded>(Decoded.Loading, decodeFit, decodeZoom, boxWidth, boxHeight) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(MediaStorage.file(context, item.fileName))
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    decodeForBox(decoder, info.size.width, info.size.height, boxWidth, boxHeight, decodeFit, decodeZoom)
                }.also { drawable ->
                    // One play per start, so the end of every loop is seen (see the callback below); the file's
                    // own loop count does not matter, the item's setting decides.
                    (drawable as? AnimatedImageDrawable)?.repeatCount = 0
                }
            }.fold(onSuccess = { Decoded.Ready(it) }, onFailure = { Decoded.Failed })
        }
    }
    val currentEvents by rememberUpdatedState(events)
    val currentLoop by rememberUpdatedState(item.loop)
    // Smart auto-advance and groups must not wait for a pass of a file that cannot even be decoded.
    LaunchedEffect(decoded) {
        when (decoded) {
            Decoded.Failed -> currentEvents?.onFailed(true)
            is Decoded.Ready -> currentEvents?.onFailed(false)
            Decoded.Loading -> Unit
        }
    }
    // Looping again: an earlier end no longer holds, or turning looping off later would count it at once.
    LaunchedEffect(item.loop) {
        if (item.loop) currentEvents?.onEnded(false)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = decoded) {
            Decoded.Loading -> Unit
            Decoded.Failed -> Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.Gray)
            is Decoded.Ready -> {
                val drawable = state.drawable
                DisposableEffect(drawable) {
                    val animated = drawable as? AnimatedImageDrawable
                    // Each loop's end is one pass. A looping item starts again from frame 0; a play-once one
                    // holds its last frame.
                    val callback = object : Animatable2.AnimationCallback() {
                        override fun onAnimationEnd(finished: Drawable) {
                            currentEvents?.onCycle()
                            if (currentLoop) animated?.start() else currentEvents?.onEnded(true)
                        }
                    }
                    animated?.registerAnimationCallback(callback)
                    onDispose {
                        // Unregistered first: stop() reports an end of its own, which must neither count nor restart.
                        animated?.unregisterAnimationCallback(callback)
                        animated?.stop()
                        // Like a video: an end belongs to this showing, so the GIF's next turn gets its full pass.
                        currentEvents?.onEnded(false)
                    }
                }
                // The view is laid out at the picture's framed size and place (fit, zoom, focus); an ImageView's
                // CENTER_CROP could only ever show the middle, and nothing of it zooms.
                val aspect = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
                val placement = if (drawable.intrinsicWidth > 0) item.placementIn(aspect, boxWidth, boxHeight) else null
                AndroidView(
                    factory = { ImageView(it) },
                    update = { view -> showDrawable(view, drawable, item, playback, placed = placement != null) },
                    modifier = Modifier.fillMaxSize().placedAt(placement),
                )
            }
        }
    }
}

/** [placed]: the view already has the picture's framed size and position, so the picture just fills it. */
private fun showDrawable(view: ImageView, drawable: Drawable, item: CoverItem.Media, playback: Playback, placed: Boolean) {
    view.scaleType = when {
        placed -> ImageView.ScaleType.FIT_XY
        item.fit == MediaFit.FILL -> ImageView.ScaleType.CENTER_CROP
        else -> ImageView.ScaleType.FIT_CENTER
    }
    drawable.isFilterBitmap = !item.crisp
    val animated = drawable as? AnimatedImageDrawable
    when {
        animated == null -> {
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
        }
        playback != Playback.LIVE -> {
            // stop() would rewind to frame 0; show a still of the current frame instead. An animation that
            // is not drawn does not advance, so going LIVE again continues from here.
            if (view.getTag(R.id.tag_frozen) !== animated || view.drawable === animated) {
                view.setImageDrawable(snapshotFrame(view, animated))
                view.setTag(R.id.tag_frozen, animated)
            }
            view.drawable?.isFilterBitmap = !item.crisp
        }
        else -> {
            view.setTag(R.id.tag_frozen, null)
            if (view.drawable !== animated) view.setImageDrawable(animated)
            // Start once per drawable: a play-once animation that finished must stay on its last frame. Only
            // when looping is turned back on after such an end does it start over.
            if (view.getTag(R.id.tag_started) !== animated || (item.loop && !animated.isRunning)) {
                animated.start()
                view.setTag(R.id.tag_started, animated)
            }
        }
    }
}

private fun snapshotFrame(view: ImageView, animated: AnimatedImageDrawable): Drawable {
    val width = animated.intrinsicWidth.coerceAtLeast(1)
    val height = animated.intrinsicHeight.coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val savedBounds = animated.copyBounds()
    animated.setBounds(0, 0, width, height)
    animated.draw(android.graphics.Canvas(bitmap))
    animated.bounds = savedBounds
    return bitmap.toDrawable(view.resources)
}

/**
 * Video: LIVE/PAUSED use ExoPlayer (hardware decode, muted, frame rate matched to the video so the
 * panel can run at 30 Hz); STILL shows the poster frame and creates no player at all.
 */
@Composable
private fun VideoCover(item: CoverItem.Media, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    if (playback == Playback.STILL) {
        val poster = item.posterFileName
        if (poster != null) {
            StillImage(item.copy(fileName = poster, kind = MediaKind.IMAGE), modifier)
        } else {
            Box(modifier.background(Color(item.background)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.Gray)
            }
        }
        return
    }
    VideoPlayer(item, modifier, playing = playback == Playback.LIVE, events = events)
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(item: CoverItem.Media, modifier: Modifier, playing: Boolean, events: PlaybackEvents?) {
    val context = LocalContext.current
    val overlay = LocalVideoOverlay.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(MediaStorage.file(context, item.fileName))))
            volume = 0f
            // Silent until sound is wanted: the audio track is not even decoded.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            // Zlip asks for audio focus itself (transient, see below); ExoPlayer's own handling would take
            // permanent focus and stop the user's music for good.
            setAudioAttributes(VIDEO_AUDIO, false)
            prepare()
        }
    }
    var aspect by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    // A play-once clip holding its last frame: it plays nothing, so it must not keep the user's music paused.
    var atEnd by remember { mutableStateOf(false) }
    // The poster covers the codec's start-up (black) until the first real frame is on screen.
    var firstFrame by remember { mutableStateOf(false) }
    // Another app took the audio (music, a video) or headphones were unplugged: keep looping, silently,
    // until the user turns sound on again. On the cover that turns its sound off ([LocalOnSoundLost]), so its
    // button offers to turn it back on, and the next clip does not take the audio back either.
    // One state for the player's lifetime (the listener below holds on to it); sound allowed again, or the
    // clip's own switch turned back on, resets it.
    var audioLost by remember { mutableStateOf(false) }
    val allowSound = LocalAllowSound.current
    LaunchedEffect(allowSound, item.muted) { if (allowSound && !item.muted) audioLost = false }
    val currentOnSoundLost by rememberUpdatedState(LocalOnSoundLost.current)
    LaunchedEffect(audioLost) { if (audioLost) currentOnSoundLost?.invoke() }
    // Only while on screen: a stopped screen saver (the cover went dark) gives the audio back, so the
    // user's music resumes.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Plays only while it is on a screen that is on: a move, a resume or sound coming back while the screen is
    // off (or for content of a session already replaced) must never start it there, out of sight and out loud.
    val shown = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val wantsSound = item.playsSound && allowSound && playing && !audioLost && !atEnd && !failed && shown
    var focusGranted by remember { mutableStateOf(false) }
    val withSound = wantsSound && focusGranted
    // What the player really does (not ended, failed, buffering or paused), for what the cover reports as heard.
    var playerPlaying by remember { mutableStateOf(false) }
    val currentPlaying by rememberUpdatedState(playing)
    val currentShouldPlay by rememberUpdatedState(playing && shown)
    val currentEvents by rememberUpdatedState(events)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerPlaying = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // A looping clip wrapping around to its start = one full pass.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) currentEvents?.onCycle()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val ended = playbackState == Player.STATE_ENDED
                // Only READY clears it (looping turned back on seeks to the start), so dropping the audio track
                // at the end cannot flip it back and forth.
                if (ended) atEnd = true else if (playbackState == Player.STATE_READY) atEnd = false
                if (ended) currentEvents?.onCycle()
                if (playbackState == Player.STATE_READY) currentEvents?.onFailed(false)
                // IDLE also follows stop() when the app goes to the background; only READY/ENDED say anything.
                if (ended || playbackState == Player.STATE_READY) currentEvents?.onEnded(ended)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                failed = true
                currentEvents?.onFailed(true)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val lostAudio = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                if (!playWhenReady && lostAudio) {
                    audioLost = true
                    player.playWhenReady = currentShouldPlay
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            currentEvents?.onEnded(false)
        }
    }
    LaunchedEffect(item.loop) {
        player.repeatMode = if (item.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // A play-once clip that already ended would otherwise stay frozen when looping is turned back on.
        if (item.loop && player.playbackState == Player.STATE_ENDED) player.seekTo(0)
    }
    LaunchedEffect(playing, shown) { player.playWhenReady = playing && shown }
    // Audio focus only while sound is wanted, so a silent loop never touches the user's music. Transient
    // focus: whatever was playing resumes once the clip goes quiet. Losing it (a call, another app) mutes
    // the clip, which keeps looping.
    DisposableEffect(wantsSound) {
        if (!wantsSound) {
            focusGranted = false
            return@DisposableEffect onDispose { }
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(PLATFORM_VIDEO_AUDIO)
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    // Another app took the audio for good: stay muted until the user turns sound on again.
                    AudioManager.AUDIOFOCUS_LOSS -> audioLost = true
                    // A call or an assistant: quiet for now, sound comes back when they are done.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> focusGranted = false
                    AudioManager.AUDIOFOCUS_GAIN -> focusGranted = true
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        when (audio.requestAudioFocus(request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> focusGranted = true
            // During a call: the system grants it later through the listener.
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> focusGranted = false
            else -> audioLost = true
        }
        onDispose {
            focusGranted = false
            audio.abandonAudioFocusRequest(request)
        }
    }
    LaunchedEffect(withSound) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !withSound)
            .build()
        player.volume = if (withSound) 1f else 0f
        // Unplugged headphones mute the clip instead of blasting the speaker (see onPlayWhenReadyChanged).
        player.setHandleAudioBecomingNoisy(withSound)
        if (withSound) player.playWhenReady = currentShouldPlay
    }
    // Someone may be listening: the folded cover then stays lit a while longer. The listener that heard
    // "audible" always hears the matching "quiet", also when the player leaves the screen.
    val audible = withSound && playerPlaying
    val heardBy = LocalHeardBy.current ?: events
    DisposableEffect(audible, heardBy) {
        val listener = heardBy.takeIf { audible }
        listener?.onAudible(true)
        onDispose { listener?.onAudible(false) }
    }
    // When the app goes to the background (inner previews), release the hardware decoder entirely.
    // The cover's own lifecycle stays RESUMED for the whole session, so this never pauses the cover.
    LifecycleStartEffect(player) {
        // After an error the view is gone; re-preparing would only decode into nothing.
        if (!failed) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.playWhenReady = currentPlaying
        }
        onStopOrDispose {
            player.playWhenReady = false
            player.stop()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(item.background))
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.Gray)
            return@BoxWithConstraints
        }
        // Laid out at the video's framed size and place (fit, zoom, focus), exactly like a photo and the poster
        // over it; only the view moves, so a new framing never touches the player.
        val placement = if (aspect > 0f) item.placementIn(aspect, constraints.maxWidth, constraints.maxHeight) else null
        val videoModifier = Modifier.fillMaxSize().placedAt(placement)
        // Keyed so that switching between overlay and texture (e.g. the user rotates the cover) builds the
        // right kind of view and detaches the old one from the player.
        key(overlay) {
            AndroidView(
                factory = { ctx ->
                    // SurfaceView is composited by the display hardware (cheapest), but cannot be clipped or
                    // rotated, so previews and rotated covers use TextureView.
                    if (overlay) {
                        SurfaceView(ctx).also { player.setVideoSurfaceView(it) }
                    } else {
                        TextureView(ctx).also { player.setVideoTextureView(it) }
                    }
                },
                onRelease = { view ->
                    when (view) {
                        is SurfaceView -> player.clearVideoSurfaceView(view)
                        is TextureView -> player.clearVideoTextureView(view)
                    }
                },
                modifier = videoModifier,
            )
        }
        // Frame 0 as a still, fading out once the video draws: no black flash on a switch or a wake. Drawn by
        // the window, so it also covers a hardware overlay (which sits below the window).
        val poster = item.posterFileName
        val posterAlpha by animateFloatAsState(if (firstFrame) 0f else 1f, tween(POSTER_FADE_MS), label = "poster")
        if (poster != null && posterAlpha > 0f) {
            StillImage(item.copy(fileName = poster, kind = MediaKind.IMAGE), Modifier.fillMaxSize().graphicsLayer { alpha = posterAlpha })
        }
    }
}

@Composable
private fun TextCover(item: CoverItem.Text, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    val style = TextStyle(
        color = Color(item.textColor),
        fontSize = item.sizeSp.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal,
    )
    Box(
        modifier = modifier
            .background(Color(item.background))
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (item.scroll) {
            ScrollingText(
                text = item.text.replace('\n', ' '),
                style = style,
                speedDps = item.speedDps,
                running = playback == Playback.LIVE,
                onCycle = events?.let { it::onCycle },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // The size is a maximum: a long note shrinks until it fits instead of being cut off, like the widget's still.
            BasicText(
                text = item.text,
                style = style.copy(textAlign = TextAlign.Center),
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_TEXT_SP.sp, maxFontSize = item.sizeSp.coerceAtLeast(MIN_TEXT_SP).sp),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/**
 * LED-sign marquee: always scrolls (even short text) at [speedDps] dp/s, in reading direction (RTL text
 * enters from the left). Moves in discrete steps at [LocalAnimationFps] instead of every vsync, and
 * only redraws the text layer, which keeps GPU work and power low.
 */
@Composable
private fun ScrollingText(
    text: String,
    style: TextStyle,
    speedDps: Int,
    running: Boolean,
    onCycle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnCycle by rememberUpdatedState(onCycle)
    val fps = LocalAnimationFps.current
    var containerWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    var rtl by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(text, style, speedDps, containerWidth, textWidth, rtl, running, fps) {
        if (containerWidth == 0 || textWidth == 0) return@LaunchedEffect
        // First placement shows the beginning of the text: its left edge for LTR, its right edge
        // (where RTL sentences start) for RTL. Done before the running check so stills get it too.
        if (!placed) {
            offset = if (rtl) (containerWidth - textWidth).toFloat() else 0f
            placed = true
        }
        if (!running) return@LaunchedEffect
        val frameMs = 1_000L / fps.coerceIn(1, 120)
        val pxPerSecond = with(density) { speedDps.coerceAtLeast(1).dp.toPx() }
        // LTR runs containerWidth -> -textWidth; RTL mirrors it.
        val start = if (rtl) -textWidth.toFloat() else containerWidth.toFloat()
        val end = if (rtl) containerWidth.toFloat() else -textWidth.toFloat()
        if (offset < -textWidth || offset > containerWidth) offset = start
        var last = withFrameNanos { it }
        while (true) {
            // Sleep most of the frame budget, then wait for a real frame. withFrameNanos suspends while
            // Compose's frame clock is paused (app in the background), so the loop then costs nothing.
            delay((frameMs - VSYNC_MS).coerceAtLeast(0))
            withFrameNanos { now ->
                val seconds = ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val next = if (rtl) offset + pxPerSecond * seconds else offset - pxPerSecond * seconds
                val wrapped = (rtl && next > end) || (!rtl && next < end)
                offset = if (wrapped) start else next
                if (wrapped) currentOnCycle?.invoke()
            }
        }
    }

    // The geometry assumes x grows to the right; pin LTR so RTL locales don't mirror the box itself.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.onSizeChanged { containerWidth = it.width }, contentAlignment = Alignment.CenterStart) {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { layout ->
                    rtl = layout.layoutInput.text.isNotEmpty() && layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl
                },
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .onSizeChanged { textWidth = it.width }
                    .graphicsLayer { translationX = offset },
            )
        }
    }
}

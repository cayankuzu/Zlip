package com.zlip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** The picked file is over the size limit. */
class MediaTooLargeException(val limitMb: Int) : IOException("File is larger than $limitMb MB")

/** The picked file could not be opened or decoded. */
class MediaUnreadableException(cause: Throwable? = null) : IOException(cause)

/** The video could not be converted for the cover screen. */
class VideoConversionException(cause: Throwable? = null) : IOException(cause)

/**
 * Prepares picked media so the cover can show it at the lowest possible cost: photos are downscaled
 * once to [MAX_PHOTO_EDGE], videos are re-encoded to SDR H.264 at most [VIDEO_SHORT_SIDE]p / 30 fps
 * (audio kept as AAC when there is any), so playback is a light hardware decode instead of a
 * full-size 4K/HDR stream.
 */
internal object MediaImport {
    const val MAX_ANIMATION_BYTES: Long = 30L * 1024 * 1024
    const val MAX_PHOTO_EDGE = 1440
    const val VIDEO_SHORT_SIDE = 720
    const val VIDEO_FPS = 30f
    const val MAX_VIDEO_MS = 60_000L
    private const val POSTER_EDGE = 480
    private const val AUDIO_BITRATE = 128_000

    /** Formats that can hold an animation; anything else (JPEG, PNG, ...) is always a still image. */
    fun mayBeAnimated(mime: String): Boolean {
        val type = mime.lowercase()
        // Prefix match for HEIF so image/heic-sequence and image/heif-sequence count too.
        return type == "image/gif" || type == "image/webp" || type == "image/avif" ||
            type.startsWith("image/heic") || type.startsWith("image/heif")
    }

    /** Formats whose pixels should survive exactly (pixel art, graphics): saved as lossless WebP. */
    fun isGraphic(mime: String): Boolean = mime.endsWith("png") || mime.endsWith("gif")

    /** Copies [uri] to [destination], failing once more than [limit] bytes arrive. */
    fun copyLimited(context: Context, uri: Uri, destination: File, limit: Long) {
        val input = context.contentResolver.openInputStream(uri) ?: throw MediaUnreadableException()
        input.use { source ->
            destination.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) throw MediaTooLargeException((limit / (1024 * 1024)).toInt())
                    sink.write(buffer, 0, read)
                }
            }
        }
    }

    /** True if [file] is an animated GIF/WebP/HEIF; throws [MediaUnreadableException] if it is not an image. */
    fun isAnimated(file: File): Boolean = try {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val edge = max(info.size.width, info.size.height)
            if (edge > 256) decoder.setTargetSampleSize(edge / 256)
        } is AnimatedImageDrawable
    } catch (e: IOException) {
        throw MediaUnreadableException(e)
    }

    /**
     * Decodes [source] (EXIF rotation applied), shrinks it to [MAX_PHOTO_EDGE] and writes a WebP:
     * lossless for graphics so pixel art keeps hard edges, lossy for camera photos.
     */
    fun writeOptimizedPhoto(source: ImageDecoder.Source, destination: File, lossless: Boolean) {
        val bitmap = try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = max(info.size.width, info.size.height)
                if (edge > MAX_PHOTO_EDGE) {
                    val scale = MAX_PHOTO_EDGE.toFloat() / edge
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: IOException) {
            throw MediaUnreadableException(e)
        }
        try {
            val written = destination.outputStream().use { out ->
                if (lossless) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)
                }
            }
            if (!written) throw IOException("Could not encode ${destination.name}")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Re-encodes the first [MAX_VIDEO_MS] of [source] into [destination]; reports 0..1 progress. Keeps the
     * audio track when there is one; if that audio cannot be converted, the clip is imported silent
     * rather than not at all. Returns whether [destination] has audio.
     */
    suspend fun transcodeVideo(context: Context, source: Uri, destination: File, onProgress: (Float) -> Unit): Boolean {
        val (shortSide, sourceHasAudio) = withContext(Dispatchers.IO) { videoShortSide(context, source) to hasAudio { setDataSource(context, source) } }
        if (sourceHasAudio) {
            try {
                export(context, source, destination, shortSide, keepAudio = true, onProgress)
                return withContext(Dispatchers.IO) { hasAudio { setDataSource(destination.absolutePath) } }
            } catch (e: VideoConversionException) {
                withContext(Dispatchers.IO) { destination.delete() }
            }
        }
        export(context, source, destination, shortSide, keepAudio = false, onProgress)
        return false
    }

    @OptIn(UnstableApi::class)
    private suspend fun export(context: Context, source: Uri, destination: File, shortSide: Int, keepAudio: Boolean, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.Main) {
            coroutineScope {
                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    // Any source audio (Opus, AC-3, high-bitrate AAC) becomes small, uniform AAC-LC.
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context.applicationContext)
                            .setRequestedAudioEncoderSettings(
                                AudioEncoderSettings.Builder()
                                    .setProfile(MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                                    .setBitrate(AUDIO_BITRATE)
                                    .build(),
                            )
                            .build(),
                    )
                    .build()
                val poller = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        delay(250)
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    }
                }
                try {
                    suspendCancellableCoroutine { continuation ->
                        transformer.addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continuation.isActive) continuation.resumeWithException(VideoConversionException(exportException))
                            }
                        })
                        val clipped = MediaItem.Builder()
                            .setUri(source)
                            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(MAX_VIDEO_MS).build())
                            .build()
                        // Never upscale: small clips stay at their own size.
                        val effects = buildList<Effect> {
                            if (shortSide == 0 || shortSide > VIDEO_SHORT_SIDE) add(Presentation.createForShortSide(VIDEO_SHORT_SIDE))
                            add(FrameDropEffect.createDefaultFrameDropEffect(VIDEO_FPS))
                        }
                        val edited = EditedMediaItem.Builder(clipped)
                            .setRemoveAudio(!keepAudio)
                            .setEffects(Effects(emptyList(), effects))
                            .build()
                        val tracks = if (keepAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
                        // HDR phone videos would otherwise stay 10-bit HDR (HEVC fallback): tone-map to SDR H.264.
                        val composition = Composition.Builder(EditedMediaItemSequence.Builder(tracks).addItem(edited).build())
                            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                            .build()
                        transformer.start(composition, destination.absolutePath)
                        // Transformer must be touched on its own (main) thread.
                        continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
                    }
                    onProgress(1f)
                } finally {
                    poller.cancel()
                }
            }
        }
    }

    /** Whether the video set up by [source] has an audio track (false if unknown). */
    private fun hasAudio(source: MediaMetadataRetriever.() -> Unit): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.source()
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: RuntimeException) {
            false
        } finally {
            retriever.release()
        }
    }

    /** Shorter edge of the video in pixels, or 0 if unknown. */
    private fun videoShortSide(context: Context, uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (width > 0 && height > 0) min(width, height) else 0
        } catch (e: RuntimeException) {
            0
        } finally {
            retriever.release()
        }
    }

    /**
     * Saves a small JPEG of the first frame of [video] for thumbnails. Best effort: returns false (and
     * leaves no file behind) if no frame could be read or written.
     */
    fun writePoster(video: File, destination: File): Boolean {
        val retriever = MediaMetadataRetriever()
        var written = false
        try {
            retriever.setDataSource(video.absolutePath)
            val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, POSTER_EDGE, POSTER_EDGE)
            if (frame != null) {
                try {
                    written = destination.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } finally {
                    frame.recycle()
                }
            }
        } catch (e: IOException) {
            written = false
        } catch (e: RuntimeException) {
            written = false
        } finally {
            if (!written) destination.delete()
            retriever.release()
        }
        return written
    }

    /** Like [isAnimated] but reads straight from the picker URI (used when a file is too big to copy). */
    fun isAnimated(context: Context, uri: Uri): Boolean = try {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val edge = max(info.size.width, info.size.height)
            if (edge > 256) decoder.setTargetSampleSize(edge / 256)
        } is AnimatedImageDrawable
    } catch (e: IOException) {
        throw MediaUnreadableException(e)
    }
}

package com.zlip.app.cover

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaFraming
import com.zlip.app.data.framing
import com.zlip.app.data.leaves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * The photo or video on the cover that fingers can frame: [item] itself, or the step a group [item] shows ([step], as
 * the group last reported it). Looked up again in [item], so a step whose framing was just saved is seen as saved.
 * Null for anything else. Pure, so it is unit-tested.
 */
internal fun mediaOnScreen(item: CoverItem?, step: CoverItem?): CoverItem.Media? = when (item) {
    is CoverItem.Media -> item
    is CoverItem.Group -> step?.let { shown -> item.items.firstOrNull { it.id == shown.id } } as? CoverItem.Media
    else -> null
}

/**
 * A framing made on the cover and not saved yet: item [id], the framing it started from ([base]) and the one it shows
 * ([shown]).
 */
internal data class FramingEdit(val id: String, val base: MediaFraming, val shown: MediaFraming) {
    /**
     * Whether it still stands over the item's [stored] framing: yes while that is still the one it started from (or
     * already the one it shows); no once something else changed it (the small window's Fit / Fill), which then wins.
     */
    fun standsOver(stored: MediaFraming): Boolean = stored == base || stored == shown
}

/** How [media] shows with [this] edit (or none) over it. Pure, so it is unit-tested. */
internal fun FramingEdit?.framingOf(media: CoverItem.Media): MediaFraming {
    val stored = media.framing
    return this?.takeIf { it.id == media.id && it.standsOver(stored) }?.shown ?: stored
}

/**
 * Moving a picture on the cover: the content can be turned ([CoverSettings.rotation]) inside a cover that is not, so
 * fingers are taken back into the content's own directions first. Pure, so it is unit-tested.
 */
internal object CoverFramingMath {
    /** [vector] as the content sees it when it is shown turned [rotation] degrees clockwise. */
    fun unrotated(vector: Offset, rotation: Int): Offset = when (rotation.mod(360)) {
        90 -> Offset(vector.y, -vector.x)
        180 -> Offset(-vector.x, -vector.y)
        270 -> Offset(-vector.y, vector.x)
        else -> vector
    }

    /**
     * [framing] of a picture of [aspect] in a [width] x [height] cover after the fingers pinched it [zoomChange] times
     * around [pivot] and moved it by [pan] (cover pixels, as the fingers saw them), with the content turned [rotation]
     * degrees. See [MediaFocus.zoomedAround].
     */
    fun moved(
        framing: MediaFraming,
        aspect: Float,
        width: Float,
        height: Float,
        rotation: Int,
        zoomChange: Float,
        pivot: Offset,
        pan: Offset,
    ): MediaFraming {
        // The content turns around the cover's centre.
        val centre = Offset(width / 2f, height / 2f)
        val at = unrotated(pivot - centre, rotation) + centre
        val by = unrotated(pan, rotation)
        return MediaFocus.zoomedAround(framing, aspect, width, height, zoomChange, at.x, at.y, by.x, by.y)
    }
}

/**
 * The cover's live framing of the photo or video it shows: the fingers' framing shows at once ([show]), and is saved
 * for the item, its copies in groups and collages included, once they let go ([save], called a moment later so a few
 * quick gestures make one write). Saving never restarts anything: renderers are keyed by file, not by framing.
 */
@Stable
internal class CoverFraming(private val repository: ContentRepository) {
    /** The framing not saved yet, if any. */
    var edit by mutableStateOf<FramingEdit?>(null)
        private set

    /** Fingers, or the glide after them, are moving the picture right now: nothing moves on under them. */
    var moving by mutableStateOf(false)
        private set

    private var glideJob: Job? = null

    /** How [media] shows now: as the fingers left it, or as stored. */
    fun framingOf(media: CoverItem.Media): MediaFraming = edit.framingOf(media)

    /** Shows [media] framed as [framing] until it is saved. */
    fun show(media: CoverItem.Media, framing: MediaFraming) {
        val base = edit?.takeIf { it.id == media.id && it.standsOver(media.framing) }?.base ?: media.framing
        edit = FramingEdit(media.id, base, framing.clamped())
    }

    /** The fingers started moving the picture. */
    fun startMoving() {
        moving = true
    }

    /** The fingers let go without a fling, or the gesture was cancelled. */
    fun stopMoving() {
        moving = false
    }

    /**
     * A finger came down: a gliding picture stops where it is, as in any gallery. Not moving any more until the new
     * gesture moves it.
     */
    fun touched() {
        glideJob?.cancel()
        glideJob = null
        moving = false
    }

    /**
     * Lets the picture glide on after a fling at [velocity] (cover pixels a second), slowing as [decay] says, in [scope].
     * [move] moves it by one frame's distance and says whether it still moved; at its edges the glide ends.
     */
    fun glide(scope: CoroutineScope, velocity: Velocity, decay: DecayAnimationSpec<Float>, move: (Offset) -> Boolean) {
        glideJob?.cancel()
        moving = true
        var job: Job? = null
        job = scope.launch {
            try {
                glideAlong(velocity, decay, move)
            } finally {
                if (glideJob === job) {
                    glideJob = null
                    moving = false
                }
            }
        }
        glideJob = job
    }

    /** Switches [media] to the other fit, from scratch (not zoomed, centred), and saves it now. */
    fun switchFit(media: CoverItem.Media) {
        val switched = framingOf(media).fitSwitched()
        edit = null
        repository.setMediaFraming(media.id, switched)
    }

    /** Saves the framing the fingers left, unless something else changed the item meanwhile (that change wins). */
    fun save() {
        val pending = edit ?: return
        edit = null
        val stored = storedFraming(pending.id) ?: return
        if (pending.standsOver(stored) && stored != pending.shown) repository.setMediaFraming(pending.id, pending.shown)
    }

    private fun storedFraming(id: String): MediaFraming? =
        repository.items.value.firstNotNullOfOrNull { item -> item.leaves().firstOrNull { it.id == id } as? CoverItem.Media }?.framing
}

/** Moves the picture along [velocity]'s direction by what a fling of that speed travels, frame by frame, until [move] says it stopped. */
private suspend fun glideAlong(velocity: Velocity, decay: DecayAnimationSpec<Float>, move: (Offset) -> Boolean) {
    val speed = hypot(velocity.x, velocity.y)
    if (!(speed > 0f)) return
    val unitX = velocity.x / speed
    val unitY = velocity.y / speed
    var travelled = 0f
    AnimationState(0f, speed).animateDecay(decay) {
        val step = value - travelled
        travelled = value
        if (!move(Offset(unitX * step, unitY * step))) cancelAnimation()
    }
}

package com.zlip.app.cover.mirror

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The mirrored inner screen in the cover window: a SurfaceView the virtual display draws into directly, so the
 * display hardware composes it and nothing is decoded here. Its frame is the whole screen at the current zoom, placed
 * by the layout's offset (the window crops the rest); a drag or a pinch only moves and scales that frame, which the
 * compositor does for free, while the buffer keeps its settled size. Scroll bars show what is out of view while it
 * moves, and TalkBack gets the gestures as actions.
 */
@Composable
fun MirrorSurface(controller: MirrorController, modifier: Modifier = Modifier) {
    val placement = controller.layout.collectAsState()
    val buffer by controller.buffer.collectAsState()
    val callback = remember(controller) {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = controller.attachSurface(holder.surface)

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) = controller.detachSurface(holder.surface)
        }
    }
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onSizeChanged(controller::setCoverSize),
    ) {
        val cover = IntSize(constraints.maxWidth, constraints.maxHeight)
        // The surface is there from the first frame, at the cover's own size until the layout (which needs that
        // size) is known: added only later, on the very first mirror its surface was seen never to be created.
        val fixed = buffer ?: cover
        AndroidView(
            factory = { context -> SurfaceView(context).apply { holder.addCallback(callback) } },
            update = { view -> view.holder.setFixedSize(fixed.width, fixed.height) },
            modifier = Modifier.layout { measurable, constraints ->
                // Read here rather than in composition: a drag changes it every frame, and only the layout follows.
                val current = placement.value
                val frame = current?.frame ?: IntSize(constraints.maxWidth, constraints.maxHeight)
                val placeable = measurable.measure(Constraints.fixed(frame.width, frame.height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(current?.offset ?: IntOffset.Zero) }
            },
        )
        MirrorScrollbars(placement, Modifier.matchParentSize())
        MirrorAccessibility(controller, placement, cover, Modifier.matchParentSize())
    }
}

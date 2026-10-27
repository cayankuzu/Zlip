package com.zlip.app.cover.mirror

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.animateIntOffsetAsState
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
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The mirrored inner screen in the cover window: a SurfaceView the virtual display draws into directly, so the
 * display hardware composes it and nothing is decoded here. Filled, it is larger than the cover and placed by the
 * layout's offset (the window crops the rest); a pan glides rather than jumps.
 */
@Composable
fun MirrorSurface(controller: MirrorController, modifier: Modifier = Modifier) {
    val placement by controller.layout.collectAsState()
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
        // The surface is there from the first frame, at the cover's own size until the layout (which needs that
        // size) is known: added only later, on the very first mirror its surface was seen never to be created.
        val current = placement
        val width = current?.display?.width ?: constraints.maxWidth
        val height = current?.display?.height ?: constraints.maxHeight
        val offset by animateIntOffsetAsState(current?.offset ?: IntOffset.Zero, label = "mirrorPan")
        AndroidView(
            factory = { context -> SurfaceView(context).apply { holder.addCallback(callback) } },
            update = { view -> view.holder.setFixedSize(width, height) },
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offset) }
            },
        )
    }
}

package com.zlip.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import kotlinx.coroutines.launch

/**
 * One gesture or tip: an icon and a single line saying what it does. Onboarding and Settings' "Kapak
 * hareketleri" sheet both use these rows, so they teach the cover in the same words.
 */
@Composable
fun GestureRow(icon: ImageVector, @StringRes text: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The cover while the phone is open: unlock, close, move, leave a group, pause, and done. */
@Composable
fun OpenPhoneGestures(modifier: Modifier = Modifier) {
    Column(modifier) {
        GestureRow(Icons.Filled.TouchApp, R.string.gesture_open_double_tap)
        GestureRow(Icons.Filled.Close, R.string.gesture_close)
        GestureRow(Icons.Filled.SwipeLeft, R.string.gesture_swipe)
        GestureRow(Icons.Filled.SkipNext, R.string.gesture_leave_group)
        GestureRow(Icons.Filled.Pause, R.string.gesture_pause)
        GestureRow(Icons.Filled.TouchApp, R.string.gesture_open_tap_done)
    }
}

/** The screen saver on the folded cover: tap for the controls, swipe to move, up for the menu, down to leave. */
@Composable
fun FoldedGestures(modifier: Modifier = Modifier) {
    Column(modifier) {
        GestureRow(Icons.Filled.TouchApp, R.string.gesture_folded_tap)
        GestureRow(Icons.Filled.SwipeLeft, R.string.gesture_swipe)
        GestureRow(Icons.Filled.SwipeUp, R.string.gesture_folded_up)
        GestureRow(Icons.Filled.SwipeDown, R.string.gesture_folded_down)
    }
}

/**
 * Every cover gesture to look up again after onboarding: "Telefon açıkken" and "Katlıyken", then Tamam. Not
 * drawn while Zlip is the Mini kapak (a sheet is a window of its own); the caller keeps it open meanwhile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverGesturesSheet(onDismiss: () -> Unit) {
    if (LocalHovering.current) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.wakesStandby()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_gestures),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            GuideTitle(R.string.gestures_open_title)
            OpenPhoneGestures()
            Spacer(Modifier.height(12.dp))
            GuideTitle(R.string.gestures_folded_title)
            FoldedGestures()
            Spacer(Modifier.height(16.dp))
            // Slides away like a swipe would, then closes.
            Button(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun GuideTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

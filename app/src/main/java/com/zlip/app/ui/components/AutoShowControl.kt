package com.zlip.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zlip.app.R
import com.zlip.app.data.CoverSettings
import com.zlip.app.system.CoverGuard

/**
 * "Katlayınca kendiliğinden aç": the screen saver on the folded cover at every wake. Turning it on without "Appear
 * on top" first explains that permission, then opens its system page. While it is off, or the permission is
 * still missing, one line says what the folded cover shows instead (only the widget).
 */
@Composable
fun AutoShowControl(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    val context = LocalContext.current
    var canAppearOnTop by remember { mutableStateOf(CoverGuard.canAppearOnTop(context)) }
    var explainAppearOnTop by rememberSaveable { mutableStateOf(false) }
    // The permission is granted on a system page; look again whenever we come back.
    LifecycleResumeEffect(Unit) {
        canAppearOnTop = CoverGuard.canAppearOnTop(context)
        onPauseOrDispose { }
    }

    // Kept open, but not drawn as the small window (a dialog is a window of its own).
    if (explainAppearOnTop && !LocalHovering.current) {
        AlertDialog(
            onDismissRequest = { explainAppearOnTop = false },
            modifier = Modifier.wakesStandby(),
            icon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
            title = { Text(stringResource(R.string.auto_show_dialog_title)) },
            text = { Text(stringResource(R.string.auto_show_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explainAppearOnTop = false
                        // Turned on now, so it starts by itself once the permission is granted.
                        onSettingsChange(settings.copy(autoShowOnCover = true))
                        context.startActivity(CoverGuard.appearOnTopSettings(context))
                    },
                ) { Text(stringResource(R.string.settings_auto_show_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { explainAppearOnTop = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    SwitchRow(
        title = stringResource(R.string.settings_auto_show),
        description = stringResource(R.string.settings_auto_show_desc),
        // The stored choice, so it can always be turned off; a missing permission is shown below.
        checked = settings.autoShowOnCover,
        onCheckedChange = { enabled ->
            if (enabled && !canAppearOnTop) {
                explainAppearOnTop = true
            } else {
                onSettingsChange(settings.copy(autoShowOnCover = enabled))
            }
        },
        icon = Icons.Filled.Visibility,
    )
    when {
        !settings.autoShowOnCover -> HintText(stringResource(R.string.auto_show_off_hint))
        !canAppearOnTop -> {
            HintText(stringResource(R.string.settings_auto_show_missing))
            Button(onClick = { context.startActivity(CoverGuard.appearOnTopSettings(context)) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_auto_show_allow))
            }
        }
    }
}

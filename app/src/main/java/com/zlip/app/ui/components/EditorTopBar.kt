package com.zlip.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R

/**
 * The top bar every editor shares: ← (Back), the [title], then [extraActions] (the pixel editor's undo and redo) and a
 * labelled "Save" button, the editor's main action. An unlabelled ✓ was easy to miss and looked like undo or redo; a
 * button with words also shows plainly when it is off ([saveEnabled]). The title gives way (one line, ellipsized) so
 * the buttons always fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            extraActions()
            FilledTonalButton(onClick = onSave, enabled = saveEnabled, modifier = Modifier.padding(start = 4.dp, end = 12.dp)) {
                Text(stringResource(R.string.action_save), maxLines = 1)
            }
        },
    )
}

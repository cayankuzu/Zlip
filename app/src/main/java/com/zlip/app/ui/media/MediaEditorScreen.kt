package com.zlip.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LocalAllowSound
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow

/** Display options for an imported photo, GIF or video. The file itself is already in the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaEditorScreen(
    initial: CoverItem.Media,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Media) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var fit by rememberSaveable { mutableStateOf(initial.fit) }
    var crisp by rememberSaveable { mutableStateOf(initial.crisp) }
    var loop by rememberSaveable { mutableStateOf(initial.loop) }
    var sound by rememberSaveable { mutableStateOf(initial.sound) }
    var background by rememberSaveable { mutableIntStateOf(initial.background) }

    val fallbackName = stringResource(R.string.default_media_name)
    val item = initial.copy(
        name = name.ifBlank { fallbackName },
        fit = fit,
        crisp = crisp,
        loop = loop,
        background = background,
        sound = sound,
    )
    val baseline = remember { initial.copy(name = initial.name.ifBlank { fallbackName }) }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, true)
    }

    val title = when (initial.kind) {
        MediaKind.IMAGE -> R.string.media_title_photo
        MediaKind.ANIMATION -> R.string.media_title_gif
        MediaKind.VIDEO -> R.string.media_title_video
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoverPreviewFrame(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally),
            ) {
                // The preview may play the sound the user just switched on, so they hear what the cover will do.
                CompositionLocalProvider(LocalAllowSound provides true) {
                    CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback)
                }
            }

            if (initial.kind == MediaKind.VIDEO) {
                SectionCard {
                    if (initial.hasAudio) {
                        SwitchRow(
                            title = stringResource(R.string.media_sound),
                            description = stringResource(R.string.media_sound_desc),
                            checked = sound,
                            onCheckedChange = { sound = it },
                            icon = if (sound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            HintText(stringResource(R.string.media_sound_none))
                        }
                    }
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.media_fit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MediaFit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = fit == option,
                            onClick = { fit = option },
                            shape = SegmentedButtonDefaults.itemShape(index, MediaFit.entries.size),
                        ) {
                            Text(stringResource(if (option == MediaFit.FIT) R.string.media_fit_fit else R.string.media_fit_fill))
                        }
                    }
                }
                if (initial.kind != MediaKind.IMAGE) {
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = stringResource(R.string.media_loop),
                        description = stringResource(R.string.media_loop_desc),
                        checked = loop,
                        onCheckedChange = { loop = it },
                        icon = Icons.Filled.Repeat,
                    )
                }
                if (initial.kind != MediaKind.VIDEO) {
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        title = stringResource(R.string.media_crisp),
                        description = stringResource(R.string.media_crisp_desc),
                        checked = crisp,
                        onCheckedChange = { crisp = it },
                        icon = Icons.Filled.GridOn,
                    )
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.pixel_background),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
                HintText(stringResource(R.string.media_background_hint))
            }

            if (initial.kind == MediaKind.VIDEO) {
                HintText(stringResource(R.string.media_video_note))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

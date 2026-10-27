package com.zlip.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zlip.app.R
import com.zlip.app.ZlipApp
import com.zlip.app.cover.mirror.MirrorPreset
import com.zlip.app.data.MirrorFit

/** Whole screen or filled crop, for the inner screen mirrored on the cover: the view mirroring starts with. */
@Composable
fun MirrorFitChips(selected: MirrorFit, onSelect: (MirrorFit) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips(
        options = MirrorFit.entries,
        selected = selected,
        label = { fit ->
            stringResource(
                when (fit) {
                    MirrorFit.FIT -> R.string.mirror_fit_fit
                    MirrorFit.FILL -> R.string.mirror_fit_fill
                },
            )
        },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * The running mirror's view, steered from the inner screen: the chips glide the cover to a view (Whole screen and
 * Fill are kept as the starting view too), "back to top" returns to where the page starts.
 */
@Composable
fun MirrorViewControls(modifier: Modifier = Modifier) {
    val mirror = (LocalContext.current.applicationContext as ZlipApp).mirror
    val preset by mirror.preset.collectAsState()
    val placement = mirror.layout.collectAsState()
    // Derived: the layout changes on every frame of a drag on the cover, the button only at the top.
    val atStart by remember(placement) { derivedStateOf { placement.value?.atStart != false } }
    Column(modifier) {
        MirrorViewChips(selected = preset, onSelect = { mirror.choose(it) })
        TextButton(
            onClick = mirror::toStart,
            enabled = !atStart,
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Icon(Icons.Filled.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.mirror_to_top))
        }
    }
}

/** The view of a running mirror, live: whole screen, filled or close-up; none selected after a pinch in between. */
@Composable
fun MirrorViewChips(selected: MirrorPreset?, onSelect: (MirrorPreset) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips<MirrorPreset?>(
        options = MirrorPreset.entries,
        selected = selected,
        label = { preset -> preset?.let { stringResource(it.label) }.orEmpty() },
        onSelect = { preset -> preset?.let(onSelect) },
        modifier = modifier,
    )
}

@get:StringRes
val MirrorPreset.label: Int
    get() = when (this) {
        MirrorPreset.FIT -> R.string.mirror_fit_fit
        MirrorPreset.FILL -> R.string.mirror_fit_fill
        MirrorPreset.CLOSE -> R.string.mirror_view_close
    }

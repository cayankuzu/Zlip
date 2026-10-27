package com.zlip.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zlip.app.R
import com.zlip.app.data.MirrorFit

/** Whole screen or filled crop, for the inner screen mirrored on the cover. */
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

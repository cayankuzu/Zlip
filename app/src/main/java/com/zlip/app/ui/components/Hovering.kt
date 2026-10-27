package com.zlip.app.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.compositionLocalOf
import com.zlip.app.cover.coverHoverSupported

/**
 * True while Zlip is its small picture-in-picture window, the Mini kapak. Sheets, dialogs and menus are
 * windows of their own that would be drawn squeezed into it, over the card, so each one stays away meanwhile
 * (its open state kept) and comes back when Zlip is full size again. Provided by ZlipRoot; false elsewhere.
 */
val LocalHovering = compositionLocalOf { false }

/** Whether this phone can show the Mini kapak at all: Android 15 or later, with picture-in-picture. */
fun miniCoverSupported(context: Context): Boolean =
    coverHoverSupported && context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

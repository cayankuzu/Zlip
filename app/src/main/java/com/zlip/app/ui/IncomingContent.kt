package com.zlip.app.ui

import android.content.Intent
import android.net.Uri
import com.zlip.app.data.QrKind
import com.zlip.app.data.QrPayload

/** Something handed to Zlip from outside (shared from another app, or a launcher shortcut) for [ZlipRoot] to open. */
sealed interface IncomingContent {
    /** Photos, GIFs or videos in shared order: one opens in the media editor, several come in together. */
    data class Media(val uris: List<Uri>) : IncomingContent

    /** A web link, for a new QR card. */
    data class Link(val url: String) : IncomingContent

    /** Text for a new text item; empty for a blank one. */
    data class Text(val text: String) : IncomingContent

    companion object {
        private val WEB_LINK = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

        /** What a share ([Intent.ACTION_SEND] or [Intent.ACTION_SEND_MULTIPLE]) carries; null when nothing usable. */
        fun fromShare(intent: Intent): IncomingContent? = when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type?.startsWith("text/") == true) {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(::fromText)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { Media(listOf(it)) }
            }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::Media)
            else -> null
        }

        /** A lone web link that makes a scannable code becomes a QR card; any other text a text item. */
        fun fromText(text: String): IncomingContent? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val scannable = WEB_LINK.matches(trimmed) && QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to trimmed)) == null
            return if (scannable) Link(trimmed) else Text(trimmed)
        }
    }
}

/** The first [max] entries (none for a negative [max]) and how many were left out, for a "skipped" message. */
internal fun <T> List<T>.takeAtMost(max: Int): Pair<List<T>, Int> {
    val kept = take(max.coerceAtLeast(0))
    return kept to size - kept.size
}

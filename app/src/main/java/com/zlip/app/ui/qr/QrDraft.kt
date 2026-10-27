package com.zlip.app.ui.qr

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.res.stringResource
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.QrKind
import com.zlip.app.data.QrPayload
import com.zlip.app.data.takeSafe
import org.json.JSONObject

/**
 * Colour pairs offered by the editor. Only dark modules on a light card: every scanner reads these,
 * even off a dimmed screen in sunlight (inverted or low-contrast codes fail on many phones).
 */
internal enum class QrTheme(val code: Int, val background: Int, @StringRes val label: Int) {
    CLASSIC(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), R.string.qr_theme_classic),
    NAVY(0xFF0B1F4B.toInt(), 0xFFFFFFFF.toInt(), R.string.qr_theme_navy),
    PAPER(0xFF1C1B1A.toInt(), 0xFFF4EFE6.toInt(), R.string.qr_theme_paper),
    FOREST(0xFF0B3D2E.toInt(), 0xFFE6F7EF.toInt(), R.string.qr_theme_forest),
    PLUM(0xFF3D1255.toInt(), 0xFFF3EAFB.toInt(), R.string.qr_theme_plum),
    LAVENDER(0xFF0A1035.toInt(), 0xFFDDE2FF.toInt(), R.string.qr_theme_lavender),
}

/** Caption suggestions per kind ("Scan to join Wi-Fi: Home"), resolved once from resources. */
internal class QrCaptions(
    private val link: String,
    private val linkGeneric: String,
    private val wifi: String,
    private val wifiGeneric: String,
    private val contact: String,
    private val contactGeneric: String,
    private val text: String,
) {
    fun suggest(kind: QrKind, fields: Map<String, String>): String = when (kind) {
        QrKind.LINK -> QrPayload.displayLink(QrPayload.link(fields[QrPayload.URL].orEmpty()))?.let { link.format(it) } ?: linkGeneric
        QrKind.WIFI -> fields[QrPayload.SSID]?.trim()?.ifEmpty { null }?.let { wifi.format(it) } ?: wifiGeneric
        QrKind.CONTACT -> fields[QrPayload.NAME]?.trim()?.ifEmpty { null }?.let { contact.format(it) } ?: contactGeneric
        QrKind.TEXT -> text
    }
}

/** The caption patterns of the current locale (format arguments are filled in by [QrCaptions.suggest]). */
@Composable
internal fun rememberQrCaptions(): QrCaptions {
    val link = stringResource(R.string.qr_caption_link)
    val linkGeneric = stringResource(R.string.qr_caption_link_generic)
    val wifi = stringResource(R.string.qr_caption_wifi)
    val wifiGeneric = stringResource(R.string.qr_caption_wifi_generic)
    val contact = stringResource(R.string.qr_caption_contact)
    val contactGeneric = stringResource(R.string.qr_caption_contact_generic)
    val text = stringResource(R.string.qr_caption_text)
    return remember(link, linkGeneric, wifi, wifiGeneric, contact, contactGeneric, text) {
        QrCaptions(link, linkGeneric, wifi, wifiGeneric, contact, contactGeneric, text)
    }
}

/**
 * Everything the QR editor edits. Fields of every kind are kept while editing, so switching kinds and
 * back loses nothing typed; only the chosen kind's fields are saved.
 */
internal data class QrDraft(
    val kind: QrKind = QrKind.LINK,
    val url: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: QrPayload.Security = QrPayload.Security.WPA,
    val hidden: Boolean = false,
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val org: String = "",
    val text: String = "",
    /** The user's own caption; ignored while [autoCaption] is on. */
    val caption: String = "",
    /** The caption follows the suggestion for the current fields until the user edits it. */
    val autoCaption: Boolean = true,
    val codeColor: Int = QrTheme.CLASSIC.code,
    val background: Int = QrTheme.CLASSIC.background,
    val name: String = "",
) {
    /** The current kind's fields, as stored in [CoverItem.Qr.fields]. An open network keeps no password. */
    val fields: Map<String, String>
        get() = when (kind) {
            QrKind.LINK -> mapOf(QrPayload.URL to url)
            QrKind.WIFI -> buildMap {
                put(QrPayload.SSID, ssid)
                put(QrPayload.SECURITY, security.name)
                if (security != QrPayload.Security.NONE) put(QrPayload.PASSWORD, password)
                put(QrPayload.HIDDEN, hidden.toString())
            }
            QrKind.CONTACT -> mapOf(QrPayload.NAME to contactName, QrPayload.PHONE to phone, QrPayload.EMAIL to email, QrPayload.ORG to org)
            QrKind.TEXT -> mapOf(QrPayload.TEXT to text)
        }

    val problem: QrPayload.Problem? get() = QrPayload.problem(kind, fields)

    /** The caption the card shows: the suggestion while [autoCaption] is on, else the user's own. */
    fun shownCaption(captions: QrCaptions): String = if (autoCaption) captions.suggest(kind, fields) else caption.trim()

    /**
     * The item shown on the cover and saved. While the fields cannot make a valid code the payload is
     * empty, so the cover shows a placeholder instead of a code that would not work.
     */
    fun toItem(id: String, captions: QrCaptions, fallbackName: String): CoverItem.Qr {
        val fields = fields
        return CoverItem.Qr(
            id = id,
            name = name.ifBlank { smartName() }.ifBlank { fallbackName }.takeSafe(ContentRepository.MAX_NAME),
            kind = kind,
            payload = if (QrPayload.problem(kind, fields) == null) QrPayload.build(kind, fields) else "",
            fields = fields,
            caption = shownCaption(captions),
            codeColor = codeColor,
            background = background,
        )
    }

    /** A name taken from the details: the network, the site, the person or the text's first words. */
    internal fun smartName(): String = when (kind) {
        QrKind.LINK -> QrPayload.displayLink(QrPayload.link(url)).orEmpty()
        QrKind.WIFI -> ssid.trim()
        QrKind.CONTACT -> contactName.trim()
        QrKind.TEXT -> text.trim().replace('\n', ' ').takeSafe(24)
    }

    companion object {
        /** The editor's starting state for [item] (null: a new card). */
        fun from(item: CoverItem.Qr?, captions: QrCaptions, fallbackName: String): QrDraft {
            if (item == null) return QrDraft()
            // Items saved without fields (or by an older version) are read back from their payload.
            val fields = item.fields.ifEmpty { QrPayload.fieldsFrom(item.kind, item.payload) }
            val draft = QrDraft(
                kind = item.kind,
                url = fields[QrPayload.URL].orEmpty(),
                ssid = fields[QrPayload.SSID].orEmpty(),
                password = fields[QrPayload.PASSWORD].orEmpty(),
                security = QrPayload.security(fields[QrPayload.SECURITY]),
                hidden = fields[QrPayload.HIDDEN].toBoolean(),
                contactName = fields[QrPayload.NAME].orEmpty(),
                phone = fields[QrPayload.PHONE].orEmpty(),
                email = fields[QrPayload.EMAIL].orEmpty(),
                org = fields[QrPayload.ORG].orEmpty(),
                text = fields[QrPayload.TEXT].orEmpty(),
                codeColor = item.codeColor,
                background = item.background,
                name = item.name,
            )
            // A caption that still equals the suggestion keeps following the fields.
            val auto = item.caption.isNotEmpty() && item.caption == captions.suggest(draft.kind, draft.fields)
            // Likewise a name that was made from the details keeps following them instead of freezing.
            val derived = draft.smartName().ifBlank { fallbackName }.takeSafe(ContentRepository.MAX_NAME)
            return draft.copy(
                caption = if (auto) "" else item.caption,
                autoCaption = auto,
                name = item.name.takeUnless { it == derived }.orEmpty(),
            )
        }

        /** Survives rotation and process death as JSON. */
        val Saver = Saver<QrDraft, String>(
            save = { draft ->
                JSONObject().apply {
                    put("kind", draft.kind.name)
                    put("url", draft.url)
                    put("ssid", draft.ssid)
                    put("password", draft.password)
                    put("security", draft.security.name)
                    put("hidden", draft.hidden)
                    put("contactName", draft.contactName)
                    put("phone", draft.phone)
                    put("email", draft.email)
                    put("org", draft.org)
                    put("text", draft.text)
                    put("caption", draft.caption)
                    put("autoCaption", draft.autoCaption)
                    put("codeColor", draft.codeColor)
                    put("background", draft.background)
                    put("name", draft.name)
                }.toString()
            },
            restore = { json ->
                val o = JSONObject(json)
                QrDraft(
                    kind = QrKind.entries.firstOrNull { it.name == o.optString("kind") } ?: QrKind.LINK,
                    url = o.optString("url"),
                    ssid = o.optString("ssid"),
                    password = o.optString("password"),
                    security = QrPayload.Security.entries.firstOrNull { it.name == o.optString("security") } ?: QrPayload.Security.WPA,
                    hidden = o.optBoolean("hidden"),
                    contactName = o.optString("contactName"),
                    phone = o.optString("phone"),
                    email = o.optString("email"),
                    org = o.optString("org"),
                    text = o.optString("text"),
                    caption = o.optString("caption"),
                    autoCaption = o.optBoolean("autoCaption", true),
                    codeColor = o.optInt("codeColor", QrTheme.CLASSIC.code),
                    background = o.optInt("background", QrTheme.CLASSIC.background),
                    name = o.optString("name"),
                )
            },
        )
    }
}

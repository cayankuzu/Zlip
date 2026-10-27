package com.zlip.app.data

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * What a QR card encodes, built from the editor's fields, and read back from a payload when an item
 * has no fields. Pure Kotlin (no Android types), so every rule here is unit tested.
 */
object QrPayload {
    /** Longest payload we encode: anything denser gets hard to scan off a 3.4" cover screen. */
    const val MAX_CHARS = 800

    /** The same limit in UTF-8 bytes, which is what the code stores (Turkish letters and emoji take 2-4). */
    const val MAX_BYTES = 1_000

    /** Wi-Fi network names are at most 32 bytes. */
    const val MAX_SSID_BYTES = 32

    /** WPA/WPA2 passphrases have at least 8 characters. */
    const val MIN_WPA_PASSWORD = 8

    /** Longest "site/path" shown in a suggested caption before falling back to the bare site. */
    private const val MAX_DISPLAY_LINK = 28

    // Keys of CoverItem.Qr.fields, so the editor can show exactly what the user typed.
    const val URL = "url"
    const val SSID = "ssid"
    const val PASSWORD = "password"
    const val SECURITY = "security"
    const val HIDDEN = "hidden"
    const val NAME = "name"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val ORG = "org"
    const val TEXT = "text"

    /** Wi-Fi security as the WIFI: payload spells it. WPA also covers WPA2 and WPA3. */
    enum class Security(val token: String) { WPA("WPA"), WEP("WEP"), NONE("nopass") }

    /** Why the fields cannot make a code yet; [field] is the editor field it belongs to (null: the whole code). */
    enum class Problem(val field: String?, val missing: Boolean = false) {
        LINK_MISSING(URL, missing = true),
        LINK_INVALID(URL),
        SSID_MISSING(SSID, missing = true),
        SSID_TOO_LONG(SSID),
        PASSWORD_MISSING(PASSWORD, missing = true),
        PASSWORD_TOO_SHORT(PASSWORD),
        NAME_MISSING(NAME, missing = true),
        CONTACT_DETAILS_MISSING(PHONE, missing = true),
        PHONE_INVALID(PHONE),
        EMAIL_INVALID(EMAIL),
        TEXT_MISSING(TEXT, missing = true),
        TOO_LONG(null),
    }

    /** Schemes that are complete without "//" (mailto:me@x.com, tel:+90...). Anything else gets https://. */
    private val OPAQUE_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "geo", "market")
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HOST = Regex("^[^.\\s]+(\\.[^.\\s]+)+$")
    private val EMAIL_SHAPE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val PHONE_CHARS = Regex("^[0-9+()\\-. ]+$")
    private val WHITESPACE = Regex("\\s+")

    /** The payload for [kind] built from the editor [fields] (missing keys count as empty). */
    fun build(kind: QrKind, fields: Map<String, String>): String = when (kind) {
        QrKind.LINK -> link(fields[URL].orEmpty())
        QrKind.WIFI -> wifi(
            ssid = fields[SSID].orEmpty().trim(),
            password = fields[PASSWORD].orEmpty(),
            security = security(fields[SECURITY]),
            hidden = fields[HIDDEN].toBoolean(),
        )
        QrKind.CONTACT -> contact(fields[NAME].orEmpty(), fields[PHONE].orEmpty(), fields[EMAIL].orEmpty(), fields[ORG].orEmpty())
        QrKind.TEXT -> fields[TEXT].orEmpty()
    }

    /** The first reason [fields] cannot make a scannable code, or null when they can. */
    fun problem(kind: QrKind, fields: Map<String, String>): Problem? {
        val fieldProblem = when (kind) {
            QrKind.LINK -> {
                val url = link(fields[URL].orEmpty())
                when {
                    url.isEmpty() -> Problem.LINK_MISSING
                    !isValidLink(url) -> Problem.LINK_INVALID
                    else -> null
                }
            }
            QrKind.WIFI -> {
                val ssid = fields[SSID].orEmpty().trim()
                val password = fields[PASSWORD].orEmpty()
                val security = security(fields[SECURITY])
                when {
                    ssid.isEmpty() -> Problem.SSID_MISSING
                    ssid.toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES -> Problem.SSID_TOO_LONG
                    security != Security.NONE && password.isEmpty() -> Problem.PASSWORD_MISSING
                    security == Security.WPA && password.length < MIN_WPA_PASSWORD -> Problem.PASSWORD_TOO_SHORT
                    else -> null
                }
            }
            QrKind.CONTACT -> {
                val phone = fields[PHONE].orEmpty().trim()
                val email = fields[EMAIL].orEmpty().trim()
                when {
                    fields[NAME].isNullOrBlank() -> Problem.NAME_MISSING
                    phone.isEmpty() && email.isEmpty() -> Problem.CONTACT_DETAILS_MISSING
                    phone.isNotEmpty() && !isValidPhone(phone) -> Problem.PHONE_INVALID
                    email.isNotEmpty() && !EMAIL_SHAPE.matches(email) -> Problem.EMAIL_INVALID
                    else -> null
                }
            }
            QrKind.TEXT -> if (fields[TEXT].isNullOrBlank()) Problem.TEXT_MISSING else null
        }
        return fieldProblem ?: Problem.TOO_LONG.takeIf { isTooLong(build(kind, fields)) }
    }

    fun isTooLong(payload: String): Boolean =
        payload.length > MAX_CHARS || payload.toByteArray(Charsets.UTF_8).size > MAX_BYTES

    /** Trimmed, with https:// added when no scheme was typed ("instagram.com/me"). Empty stays empty. */
    fun link(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return ""
        val scheme = SCHEME.find(url)?.groupValues?.get(1)
        if (scheme != null && (url.regionMatches(scheme.length, "://", 0, 3) || scheme.lowercase() in OPAQUE_SCHEMES)) {
            // Lower-case schemes: a few older scanners only recognize "https", not "HTTPS".
            return scheme.lowercase() + url.substring(scheme.length)
        }
        return "https://$url"
    }

    /** True for a link scanners will offer to open: no spaces, and web links need a real-looking host. */
    fun isValidLink(url: String): Boolean {
        if (url.isEmpty() || url.any { it.isWhitespace() }) return false
        val host = hostOf(url) ?: return url.substringAfter(':').isNotEmpty()
        return host == "localhost" || host.startsWith("[") || (HOST.matches(host) && !host.endsWith("."))
    }

    /**
     * A short form of a web link for captions and names: "instagram.com/ada", or only the host when the
     * path is long. Null for links without a host (mailto: and co.).
     */
    fun displayLink(url: String): String? {
        val host = hostOf(url)?.removePrefix("www.")?.ifEmpty { null } ?: return null
        val rest = url.substringAfter("://").substringBefore('?').substringBefore('#')
        val path = rest.substring(rest.indexOf('/').takeIf { it >= 0 } ?: rest.length).trimEnd('/')
        val full = host + path
        return if (path.isNotEmpty() && full.length <= MAX_DISPLAY_LINK) full else host
    }

    private fun hostOf(url: String): String? {
        val start = url.indexOf("://").takeIf { it > 0 } ?: return null
        val authority = url.substring(start + 3).substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@')
        return if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBefore(':').lowercase()
    }

    /** WIFI:T:<WPA|WEP|nopass>;S:<ssid>;P:<password>;H:<true|false>;; (no P for open networks). */
    fun wifi(ssid: String, password: String, security: Security, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security.token)
        append(";S:").append(escapeWifi(ssid))
        if (security != Security.NONE) append(";P:").append(escapeWifi(password))
        append(";H:").append(hidden).append(";;")
    }

    /** [Security] named by a stored field (an enum name), or by a payload's T: value. */
    fun security(value: String?): Security = when (value?.trim()?.uppercase()) {
        null, "", "WPA", "WPA2", "WPA3", "SAE" -> Security.WPA
        "WEP" -> Security.WEP
        else -> Security.NONE
    }

    /**
     * A vCard 3.0 contact card. The name is split at its last word into given and family name
     * ("Ayşe Nur Yılmaz" -> Yılmaz; Ayşe Nur), which is how contact apps show it.
     */
    fun contact(name: String, phone: String, email: String, org: String): String {
        val full = name.trim().replace(WHITESPACE, " ")
        val words = full.split(' ').filter { it.isNotEmpty() }
        val family = if (words.size > 1) words.last() else ""
        val given = if (words.size > 1) words.dropLast(1).joinToString(" ") else full
        val lines = buildList {
            add("BEGIN:VCARD")
            add("VERSION:3.0")
            add("N:${escapeVCard(family)};${escapeVCard(given)};;;")
            add("FN:${escapeVCard(full)}")
            // CELL so contact apps file it as a mobile number rather than "other".
            if (phone.isNotBlank()) add("TEL;TYPE=CELL:${escapeVCard(phone.trim())}")
            if (email.isNotBlank()) add("EMAIL:${escapeVCard(email.trim())}")
            if (org.isNotBlank()) add("ORG:${escapeVCard(org.trim())}")
            add("END:VCARD")
        }
        return lines.joinToString("\r\n")
    }

    /** Editor fields read back from a payload, for items saved without fields. Unknown formats become text. */
    fun fieldsFrom(kind: QrKind, payload: String): Map<String, String> = when (kind) {
        QrKind.LINK -> mapOf(URL to payload)
        QrKind.WIFI -> parseWifi(payload) ?: emptyMap()
        QrKind.CONTACT -> parseContact(payload) ?: emptyMap()
        QrKind.TEXT -> mapOf(TEXT to payload)
    }

    /** The fields of a WIFI: payload (any field order, backslash escapes), or null if it is not one. */
    fun parseWifi(payload: String): Map<String, String>? {
        if (!payload.startsWith("WIFI:", ignoreCase = true)) return null
        val values = HashMap<String, String>()
        val token = StringBuilder()
        var escaped = false
        fun finish() {
            // The key is plain letters, so the first ':' always ends it, even if the value had escaped ones.
            val text = token.toString()
            val key = text.substringBefore(':', "").trim().uppercase()
            if (key.isNotEmpty() && key !in values) values[key] = text.substringAfter(':')
            token.clear()
        }
        for (c in payload.substring(5)) {
            when {
                escaped -> {
                    token.append(c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == ';' -> finish()
                else -> token.append(c)
            }
        }
        if (token.isNotEmpty()) finish()
        val security = if (values["T"].isNullOrBlank() && values["P"].isNullOrEmpty()) Security.NONE else security(values["T"])
        return buildMap {
            put(SSID, values["S"].orEmpty())
            put(SECURITY, security.name)
            if (security != Security.NONE) put(PASSWORD, values["P"].orEmpty())
            put(HIDDEN, values["H"].equals("true", ignoreCase = true).toString())
        }
    }

    /** Name, phone, email and company of a vCard, or null if [payload] is not one. */
    fun parseContact(payload: String): Map<String, String>? {
        if (!payload.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) return null
        // Unfold continuation lines (a line break followed by a space or tab) before reading properties.
        val lines = payload.replace(Regex("\r?\n[ \t]"), "").split(Regex("\r\n|\n|\r"))
        val values = HashMap<String, String>()
        for (line in lines) {
            val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
            val property = line.substring(0, colon).substringBefore(';').trim().uppercase()
            if (property !in values) values[property] = line.substring(colon + 1)
        }
        val name = values["FN"]?.let(::unescapeVCard)?.trim()?.ifEmpty { null }
            ?: values["N"]?.let { n ->
                val parts = splitVCard(n)
                listOfNotNull(parts.getOrNull(1), parts.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ")
            }.orEmpty()
        return buildMap {
            put(NAME, name)
            put(PHONE, values["TEL"]?.let(::unescapeVCard).orEmpty())
            put(EMAIL, values["EMAIL"]?.let(::unescapeVCard).orEmpty())
            // ORG may list departments after ';'; the company comes first.
            put(ORG, values["ORG"]?.let { splitVCard(it).firstOrNull() }.orEmpty())
        }
    }

    private fun isValidPhone(phone: String): Boolean = PHONE_CHARS.matches(phone) && phone.count { it.isDigit() } >= 3

    /** Backslash before \ ; , : and " (the WIFI: format's special characters). */
    private fun escapeWifi(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    /** vCard text escaping: \ , ; and line breaks. */
    private fun escapeVCard(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            when (val c = value[i]) {
                '\\', ',', ';' -> append('\\').append(c)
                '\r' -> {
                    append("\\n")
                    if (value.getOrNull(i + 1) == '\n') i++
                }
                '\n' -> append("\\n")
                else -> append(c)
            }
            i++
        }
    }

    private fun unescapeVCard(value: String): String = buildString {
        var escaped = false
        for (c in value) {
            when {
                escaped -> {
                    append(if (c == 'n' || c == 'N') '\n' else c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                else -> append(c)
            }
        }
    }

    /** Splits a structured vCard value at unescaped ';' and unescapes each part. */
    private fun splitVCard(value: String): List<String> {
        val parts = mutableListOf<String>()
        val part = StringBuilder()
        var escaped = false
        for (c in value) {
            when {
                escaped -> {
                    part.append('\\').append(c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == ';' -> {
                    parts += unescapeVCard(part.toString())
                    part.clear()
                }
                else -> part.append(c)
            }
        }
        parts += unescapeVCard(part.toString())
        return parts
    }
}

/** A QR symbol: [size] modules per side, without the quiet zone. Immutable, so it is safely cached and shared. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) { "Expected ${size * size} modules, got ${dark.size}" }
    }

    /** True for a dark module; x grows right, y grows down. */
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * Turns payloads into [QrMatrix]es with ZXing (error correction M). Results are cached by payload, so
 * the cover, previews, thumbnails and collage cells encode each code once, never per frame.
 */
object QrEncoder {
    /** Light modules scanners need around the code, in modules. Drawn by the renderer, not stored. */
    const val QUIET_ZONE = 4

    private const val CACHE_SIZE = 32

    private val cache = object : LinkedHashMap<String, QrMatrix>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QrMatrix>?): Boolean = size > CACHE_SIZE
    }

    /** The code for [payload], or null when it is empty or too long to be a scannable code. */
    fun encode(payload: String): QrMatrix? {
        if (payload.isEmpty() || QrPayload.isTooLong(payload)) return null
        synchronized(cache) { cache[payload]?.let { return it } }
        // Plain ASCII needs no charset marker (smaller code, readable by every scanner); anything else is
        // UTF-8 with an ECI header so Turkish letters and emoji arrive intact.
        val hints = if (payload.all { it.code < 0x80 }) null else mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name())
        val code = try {
            Encoder.encode(payload, ErrorCorrectionLevel.M, hints)
        } catch (e: WriterException) {
            return null
        }
        val modules = code.matrix
        val size = modules.width
        val matrix = QrMatrix(size, BooleanArray(size * size) { modules.get(it % size, it / size).toInt() == 1 })
        synchronized(cache) { cache[payload] = matrix }
        return matrix
    }
}

/** Colour rules that keep a code scannable: dark modules on a light background, with enough contrast. */
object QrColors {
    /** WCAG AA contrast; below it phone cameras start missing modules, especially on a dimmed screen. */
    const val MIN_CONTRAST = 4.5

    /** [code] and [background] when scanners can read them, otherwise black on white. */
    fun scannable(code: Int, background: Int): Pair<Int, Int> =
        if (isScannable(code, background)) code to background else CoverItem.BLACK to CoverItem.WHITE

    /** Both opaque, code darker than background (many scanners cannot read inverted codes), contrast >= 4.5:1. */
    fun isScannable(code: Int, background: Int): Boolean =
        code ushr 24 == 0xFF && background ushr 24 == 0xFF &&
            luminance(code) < luminance(background) && contrast(code, background) >= MIN_CONTRAST

    /** WCAG contrast ratio of two colours, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Relative luminance (sRGB, WCAG 2), 0 = black .. 1 = white; alpha is ignored. */
    fun luminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }
}

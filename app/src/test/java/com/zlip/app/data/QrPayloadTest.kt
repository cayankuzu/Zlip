package com.zlip.app.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.zlip.app.data.QrPayload.Problem
import com.zlip.app.data.QrPayload.Security
import com.zlip.app.ui.qr.QrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    // Links

    @Test
    fun linkIsTrimmedAndGetsHttpsWhenNoSchemeWasTyped() {
        assertEquals("https://instagram.com/ada", QrPayload.link("  instagram.com/ada \n"))
        assertEquals("https://localhost:8080/x", QrPayload.link("localhost:8080/x"))
        assertEquals("", QrPayload.link("   "))
    }

    @Test
    fun linkKeepsTypedSchemesAndLowerCasesThem() {
        assertEquals("http://example.com", QrPayload.link("http://example.com"))
        assertEquals("https://Example.com/Path", QrPayload.link("HTTPS://Example.com/Path"))
        assertEquals("mailto:me@example.com", QrPayload.link("mailto:me@example.com"))
        assertEquals("tel:+905551112233", QrPayload.link("TEL:+905551112233"))
    }

    @Test
    fun linkValidation() {
        assertNull(QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "instagram.com/ada")))
        assertNull(QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "mailto:me@example.com")))
        assertEquals(Problem.LINK_MISSING, QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to " ")))
        assertEquals(Problem.LINK_MISSING, QrPayload.problem(QrKind.LINK, emptyMap()))
        assertEquals(Problem.LINK_INVALID, QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "@ada")))
        assertEquals(Problem.LINK_INVALID, QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "my site.com")))
        assertEquals(Problem.LINK_INVALID, QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "https://a..com")))
        assertEquals(Problem.LINK_INVALID, QrPayload.problem(QrKind.LINK, mapOf(QrPayload.URL to "mailto:")))
    }

    @Test
    fun displayLinkIsShortAndDropsWww() {
        assertEquals("instagram.com/ada", QrPayload.displayLink("https://www.instagram.com/ada/?igsh=xyz"))
        assertEquals("example.com", QrPayload.displayLink("https://example.com/"))
        assertEquals("example.com", QrPayload.displayLink("https://example.com/a/very/long/path/that/does/not/fit"))
        assertNull(QrPayload.displayLink("mailto:me@example.com"))
        assertNull(QrPayload.displayLink(""))
    }

    // Wi-Fi

    @Test
    fun wifiPayloadFollowsTheWifiFormat() {
        assertEquals("WIFI:T:WPA;S:Home;P:secret123;H:false;;", QrPayload.wifi("Home", "secret123", Security.WPA, hidden = false))
        assertEquals("WIFI:T:WEP;S:Old;P:abcde;H:true;;", QrPayload.wifi("Old", "abcde", Security.WEP, hidden = true))
    }

    @Test
    fun openNetworkHasNoPassword() {
        assertEquals("WIFI:T:nopass;S:Cafe;H:false;;", QrPayload.wifi("Cafe", "ignored", Security.NONE, hidden = false))
    }

    @Test
    fun wifiEscapesSpecialCharactersInSsidAndPassword() {
        val payload = QrPayload.wifi("My;Net,\"5G\":\\", "p;a,s:s\"\\", Security.WPA, hidden = false)
        assertEquals("WIFI:T:WPA;S:My\\;Net\\,\\\"5G\\\"\\:\\\\;P:p\\;a\\,s\\:s\\\"\\\\;H:false;;", payload)
    }

    @Test
    fun wifiBuildTrimsTheSsidButNotThePassword() {
        val fields = mapOf(QrPayload.SSID to " Home ", QrPayload.PASSWORD to " spaced pass ", QrPayload.SECURITY to "WPA")
        assertEquals("WIFI:T:WPA;S:Home;P: spaced pass ;H:false;;", QrPayload.build(QrKind.WIFI, fields))
    }

    @Test
    fun wifiValidation() {
        fun problem(ssid: String, password: String, security: Security) = QrPayload.problem(
            QrKind.WIFI,
            mapOf(QrPayload.SSID to ssid, QrPayload.PASSWORD to password, QrPayload.SECURITY to security.name),
        )
        assertNull(problem("Home", "12345678", Security.WPA))
        assertNull(problem("Cafe", "", Security.NONE))
        assertNull(problem("Old", "abc", Security.WEP))
        assertEquals(Problem.SSID_MISSING, problem("  ", "12345678", Security.WPA))
        assertEquals(Problem.PASSWORD_MISSING, problem("Home", "", Security.WPA))
        assertEquals(Problem.PASSWORD_TOO_SHORT, problem("Home", "1234567", Security.WPA))
        // 32 bytes is the limit; Turkish letters take two bytes each.
        assertNull(problem("a".repeat(32), "12345678", Security.WPA))
        assertEquals(Problem.SSID_TOO_LONG, problem("ş".repeat(17), "12345678", Security.WPA))
    }

    @Test
    fun wifiPayloadReadsBackIntoFields() {
        val payload = QrPayload.wifi("My;Net:5G", "p\\a;ss,word", Security.WPA, hidden = true)
        val fields = QrPayload.parseWifi(payload)
        assertEquals(
            mapOf(QrPayload.SSID to "My;Net:5G", QrPayload.SECURITY to "WPA", QrPayload.PASSWORD to "p\\a;ss,word", QrPayload.HIDDEN to "true"),
            fields,
        )
        assertEquals(payload, QrPayload.build(QrKind.WIFI, fields!!))
    }

    @Test
    fun wifiParserAcceptsAnyFieldOrderAndOpenNetworks() {
        assertEquals(
            mapOf(QrPayload.SSID to "Cafe", QrPayload.SECURITY to "NONE", QrPayload.HIDDEN to "false"),
            QrPayload.parseWifi("WIFI:S:Cafe;T:nopass;;"),
        )
        assertEquals("WPA", QrPayload.parseWifi("WIFI:P:pw123456;S:X;T:WPA2;;")!![QrPayload.SECURITY])
        assertNull(QrPayload.parseWifi("https://example.com"))
    }

    // Contacts

    @Test
    fun contactIsAVCard3WithCrlfLineEndings() {
        val card = QrPayload.contact("Ayşe Nur Yılmaz", "+90 555 111 22 33", "ayse@example.com", "Zlip")
        assertEquals(
            listOf(
                "BEGIN:VCARD",
                "VERSION:3.0",
                "N:Yılmaz;Ayşe Nur;;;",
                "FN:Ayşe Nur Yılmaz",
                "TEL;TYPE=CELL:+90 555 111 22 33",
                "EMAIL:ayse@example.com",
                "ORG:Zlip",
                "END:VCARD",
            ).joinToString("\r\n"),
            card,
        )
    }

    @Test
    fun contactLeavesOutEmptyFieldsAndEscapesText() {
        val card = QrPayload.contact(" Ada ", "", "ada@example.com", "Smith, Jones; Co\\\nLab")
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:;Ada;;;\r\nFN:Ada\r\nEMAIL:ada@example.com\r\nORG:Smith\\, Jones\\; Co\\\\\\nLab\r\nEND:VCARD",
            card,
        )
    }

    @Test
    fun contactValidation() {
        fun problem(name: String, phone: String, email: String) =
            QrPayload.problem(QrKind.CONTACT, mapOf(QrPayload.NAME to name, QrPayload.PHONE to phone, QrPayload.EMAIL to email))
        assertNull(problem("Ada", "+90 (555) 111-22-33", ""))
        assertNull(problem("Ada", "", "ada@example.com"))
        assertEquals(Problem.NAME_MISSING, problem(" ", "5551112233", ""))
        assertEquals(Problem.CONTACT_DETAILS_MISSING, problem("Ada", " ", ""))
        assertEquals(Problem.PHONE_INVALID, problem("Ada", "call me", ""))
        assertEquals(Problem.PHONE_INVALID, problem("Ada", "12", ""))
        assertEquals(Problem.EMAIL_INVALID, problem("Ada", "", "ada@example"))
    }

    @Test
    fun contactReadsBackIntoFields() {
        val card = QrPayload.contact("Ayşe Nur Yılmaz", "+90 555", "ayse@example.com", "Smith, Jones; Co")
        assertEquals(
            mapOf(QrPayload.NAME to "Ayşe Nur Yılmaz", QrPayload.PHONE to "+90 555", QrPayload.EMAIL to "ayse@example.com", QrPayload.ORG to "Smith, Jones; Co"),
            QrPayload.parseContact(card),
        )
        // Cards from elsewhere: no FN, folded lines, LF endings.
        val foreign = "BEGIN:VCARD\nVERSION:3.0\nN:Lovelace;Ada;;;\nTEL;TYPE=WORK:+44 20\n 7946 0000\nEND:VCARD"
        val fields = QrPayload.parseContact(foreign)!!
        assertEquals("Ada Lovelace", fields[QrPayload.NAME])
        assertEquals("+44 207946 0000", fields[QrPayload.PHONE])
        assertNull(QrPayload.parseContact("WIFI:S:x;;"))
    }

    // Text and limits

    @Test
    fun textIsEncodedAsIs() {
        assertEquals("  Merhaba!\nİyi günler  ", QrPayload.build(QrKind.TEXT, mapOf(QrPayload.TEXT to "  Merhaba!\nİyi günler  ")))
        assertEquals(Problem.TEXT_MISSING, QrPayload.problem(QrKind.TEXT, mapOf(QrPayload.TEXT to " \n ")))
    }

    @Test
    fun tooLongPayloadsAreRejected() {
        assertNull(QrPayload.problem(QrKind.TEXT, mapOf(QrPayload.TEXT to "a".repeat(QrPayload.MAX_CHARS))))
        assertEquals(Problem.TOO_LONG, QrPayload.problem(QrKind.TEXT, mapOf(QrPayload.TEXT to "a".repeat(QrPayload.MAX_CHARS + 1))))
        // Few characters, many bytes (three-byte letters).
        assertEquals(Problem.TOO_LONG, QrPayload.problem(QrKind.TEXT, mapOf(QrPayload.TEXT to "語".repeat(400))))
    }

    @Test
    fun fieldsFromPayloadCoverEveryKind() {
        assertEquals(mapOf(QrPayload.URL to "https://x.com"), QrPayload.fieldsFrom(QrKind.LINK, "https://x.com"))
        assertEquals(mapOf(QrPayload.TEXT to "hi"), QrPayload.fieldsFrom(QrKind.TEXT, "hi"))
        assertEquals("Home", QrPayload.fieldsFrom(QrKind.WIFI, "WIFI:T:WPA;S:Home;P:12345678;;")[QrPayload.SSID])
        assertTrue(QrPayload.fieldsFrom(QrKind.CONTACT, "not a card").isEmpty())
    }

    // Encoding

    @Test
    fun encodedCodesScanBackToTheirPayload() {
        val payloads = listOf(
            QrPayload.wifi("Ev Ağı", "şifre;123", Security.WPA, hidden = false),
            "https://instagram.com/ada",
            QrPayload.contact("Ayşe Nur Yılmaz", "+90 555 111 22 33", "ayse@example.com", ""),
            "Merhaba! Kapıyı çal 🙂",
        )
        for (payload in payloads) assertEquals(payload, scan(QrEncoder.encode(payload)!!))
    }

    @Test
    fun encoderCachesAndRejectsEmptyOrTooLongPayloads() {
        val first = QrEncoder.encode("cache me")
        assertNotNull(first)
        assertSame(first, QrEncoder.encode("cache me"))
        assertNull(QrEncoder.encode(""))
        assertNull(QrEncoder.encode("a".repeat(QrPayload.MAX_CHARS + 1)))
    }

    // Colours

    @Test
    fun onlyDarkOnLightWithEnoughContrastIsScannable() {
        assertTrue(QrColors.isScannable(CoverItem.BLACK, CoverItem.WHITE))
        assertTrue(QrColors.isScannable(0xFF0B1F4B.toInt(), CoverItem.WHITE))
        // Mint on black: plenty of contrast, but inverted.
        assertFalse(QrColors.isScannable(CoverItem.MINT, CoverItem.BLACK))
        // Mid grey on white: about 3.5:1.
        assertFalse(QrColors.isScannable(0xFF888888.toInt(), CoverItem.WHITE))
        // See-through colours depend on what is behind them.
        assertFalse(QrColors.isScannable(0x80000000.toInt(), CoverItem.WHITE))
        assertEquals(CoverItem.BLACK to CoverItem.WHITE, QrColors.scannable(CoverItem.MINT, CoverItem.BLACK))
        assertEquals(0xFF0B1F4B.toInt() to CoverItem.WHITE, QrColors.scannable(0xFF0B1F4B.toInt(), CoverItem.WHITE))
        assertEquals(21.0, QrColors.contrast(CoverItem.BLACK, CoverItem.WHITE), 0.01)
    }

    @Test
    fun everyEditorThemeIsScannable() {
        for (theme in QrTheme.entries) assertTrue(theme.name, QrColors.isScannable(theme.code, theme.background))
    }

    /** Draws [matrix] the way the cover does (4 px modules, 4-module quiet zone) and reads it back with ZXing. */
    private fun scan(matrix: QrMatrix): String {
        val scale = 4
        val side = (matrix.size + 2 * QrEncoder.QUIET_ZONE) * scale
        val pixels = IntArray(side * side) { index ->
            val x = index % side / scale - QrEncoder.QUIET_ZONE
            val y = index / side / scale - QrEncoder.QUIET_ZONE
            val dark = x in 0 until matrix.size && y in 0 until matrix.size && matrix.isDark(x, y)
            if (dark) CoverItem.BLACK else CoverItem.WHITE
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }
}

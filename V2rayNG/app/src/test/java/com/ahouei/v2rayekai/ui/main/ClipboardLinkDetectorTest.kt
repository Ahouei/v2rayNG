package com.ahouei.v2rayekai.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClipboardLinkDetectorTest {
    @Test
    fun everySupportedShareSchemeIsDetected() {
        listOf(
            "vmess://eyJ2IjoiMiJ9", "vless://id@example.com:443", "trojan://pw@example.com:443",
            "ss://YWVzOnB3@example.com:8388", "hysteria2://pw@example.com:443", "hy2://pw@example.com",
            "wireguard://key@example.com:51820", "socks://dXNlcg==@example.com:1080",
        ).forEach { assertTrue(ClipboardLinkDetector.looksImportable(it), it) }
    }

    @Test
    fun schemeIsCaseInsensitiveAndWhitespaceIsTrimmed() {
        assertTrue(ClipboardLinkDetector.looksImportable("  VLESS://id@example.com:443  \n"))
    }

    @Test
    fun subscriptionUrlsAreDetected() {
        assertTrue(ClipboardLinkDetector.looksImportable("https://sub.example.com/api/v1/client?token=abc"))
        assertTrue(ClipboardLinkDetector.looksImportable("http://example.org"))
    }

    @Test
    fun linkOnALaterLineOfABatchIsDetected() {
        assertTrue(ClipboardLinkDetector.looksImportable("My keys:\n\nvmess://abc\ntrojan://def"))
    }

    @Test
    fun emptyAndBlankInputIsRejected() {
        assertFalse(ClipboardLinkDetector.looksImportable(null))
        assertFalse(ClipboardLinkDetector.looksImportable(""))
        assertFalse(ClipboardLinkDetector.looksImportable("   \n "))
        // Utils.getClipboard returns the literal "null" for a clip without text.
        assertFalse(ClipboardLinkDetector.looksImportable("null"))
    }

    @Test
    fun unrelatedTextIsRejected() {
        assertFalse(ClipboardLinkDetector.looksImportable("hello world"))
        assertFalse(ClipboardLinkDetector.looksImportable("ftp://example.com/file"))
        assertFalse(ClipboardLinkDetector.looksImportable("see vmess://abc in the text"))
        assertFalse(ClipboardLinkDetector.looksImportable("vmess://"))
        assertFalse(ClipboardLinkDetector.looksImportable("https://localhost"))
        assertFalse(ClipboardLinkDetector.looksImportable("https://exa mple.com"))
        assertFalse(ClipboardLinkDetector.looksImportable("ssh://example.com"))
    }

    @Test
    fun oversizedTextIsRejected() {
        val huge = "vmess://abc\n" + "x".repeat(ClipboardLinkDetector.MAX_LENGTH)
        assertFalse(ClipboardLinkDetector.looksImportable(huge))
    }
}

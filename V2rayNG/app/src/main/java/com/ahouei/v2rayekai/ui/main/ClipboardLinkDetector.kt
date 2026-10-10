package com.ahouei.v2rayekai.ui.main

/**
 * Decides whether clipboard text looks like something the importer accepts: a supported share
 * link or a subscription URL. Pure and side-effect free; callers must never log the text.
 */
internal object ClipboardLinkDetector {
    /** Share-link schemes offered by the clipboard suggestion (lower case, without "://"). */
    val SHARE_SCHEMES = listOf(
        "vmess", "vless", "trojan", "ss", "hysteria2", "hy2", "wireguard", "socks",
    )

    /** Text longer than this is not inspected (avoids scanning huge clipboard payloads). */
    const val MAX_LENGTH = 256 * 1024

    /** Only the first lines are inspected; a batch of links starts with one on its first lines. */
    private const val MAX_LINES = 20

    private val httpUrl = Regex("^https?://[^\\s/?#@]+\\.[^\\s/?#]+(?:[/?#]\\S*)?$", RegexOption.IGNORE_CASE)

    fun looksImportable(text: String?): Boolean {
        if (text.isNullOrBlank() || text.length > MAX_LENGTH) return false
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_LINES)
            .any(::isImportableLine)
    }

    private fun isImportableLine(line: String): Boolean {
        val sep = line.indexOf("://")
        if (sep <= 0) return false
        val scheme = line.substring(0, sep).lowercase()
        val rest = line.substring(sep + 3)
        if (rest.isBlank()) return false
        return when (scheme) {
            in SHARE_SCHEMES -> true
            "http", "https" -> httpUrl.matches(line)
            else -> false
        }
    }
}

package com.v2ray.ang

import com.v2ray.ang.util.HttpUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HttpUtilTest {

    @Test
    fun testIdnToASCII() {
        // Regular URL remains unchanged
        val regularUrl = "https://example.com/path"
        assertEquals(regularUrl, HttpUtil.toIdnUrl(regularUrl))

        // Non-ASCII URL converts to ASCII (Punycode)
        val nonAsciiUrl = "https://例子.测试/path"
        val expectedNonAscii = "https://xn--fsqu00a.xn--0zwm56d/path"
        assertEquals(expectedNonAscii, HttpUtil.toIdnUrl(nonAsciiUrl))

        // Mixed URL only converts the host part
        val mixedUrl = "https://例子.com/测试"
        val expectedMixed = "https://xn--fsqu00a.com/测试"
        assertEquals(expectedMixed, HttpUtil.toIdnUrl(mixedUrl))

        // URL with Basic Authentication using regular domain
        val basicAuthUrl = "https://user:password@example.com/path"
        assertEquals(basicAuthUrl, HttpUtil.toIdnUrl(basicAuthUrl))

        // URL with Basic Authentication using non-ASCII domain
        val basicAuthNonAscii = "https://user:password@例子.测试/path"
        val expectedBasicAuthNonAscii = "https://user:password@xn--fsqu00a.xn--0zwm56d/path"
        assertEquals(expectedBasicAuthNonAscii, HttpUtil.toIdnUrl(basicAuthNonAscii))

        // URL with non-ASCII username and password
        val nonAsciiAuth = "https://用户:密码@example.com/path"
        // Basic auth credentials should remain unchanged as they're percent-encoded separately
        assertEquals(nonAsciiAuth, HttpUtil.toIdnUrl(nonAsciiAuth))
    }



    @Test
    fun subscriptionCandidatesHttpUpgradesFirst() {
        assertEquals(
            listOf("https://example.com/sub?x=1", "http://example.com/sub?x=1"),
            HttpUtil.subscriptionCandidateUrls("http://example.com/sub?x=1")
        )
    }

    @Test
    fun subscriptionCandidatesHttpsFallsBackToHttp() {
        assertEquals(
            listOf("https://example.com:8443/s", "http://example.com:8443/s"),
            HttpUtil.subscriptionCandidateUrls("https://example.com:8443/s")
        )
        assertEquals(
            listOf("HTTPS://example.com/s", "http://example.com/s"),
            HttpUtil.subscriptionCandidateUrls("HTTPS://example.com/s")
        )
    }

    @Test
    fun subscriptionCandidatesOtherSchemesEmpty() {
        assertEquals(emptyList<String>(), HttpUtil.subscriptionCandidateUrls("ftp://example.com/s"))
        assertEquals(emptyList<String>(), HttpUtil.subscriptionCandidateUrls("example.com/s"))
        assertEquals(emptyList<String>(), HttpUtil.subscriptionCandidateUrls(""))
    }

    @Test
    fun subscriptionCandidatesAfterIdn() {
        val idn = HttpUtil.toIdnUrl("http://例子.测试/sub")
        assertEquals(
            listOf("https://xn--fsqu00a.xn--0zwm56d/sub", "http://xn--fsqu00a.xn--0zwm56d/sub"),
            HttpUtil.subscriptionCandidateUrls(idn)
        )
    }

    @Test
    fun schemeAndHostDropsCredentialsAndPath() {
        assertEquals("https://example.com", HttpUtil.schemeAndHost("https://user:pw@example.com/p?token=1"))
        assertEquals("invalid-url", HttpUtil.schemeAndHost("not a url"))
    }

    @Test
    fun schemeAndHostHttpDropsPortAndQuery() {
        assertEquals("http://example.com", HttpUtil.schemeAndHost("http://example.com:8080/p?token=x"))
    }

    @Test
    fun schemeAndHostKeepsHostThatUriCannotParse() {
        // java.net.URI leaves host null for "_" hosts; the authority is reduced to its host part.
        assertEquals("http://my_host.example.com", HttpUtil.schemeAndHost("http://my_host.example.com/s"))
        assertEquals("http://my_host.example.com", HttpUtil.schemeAndHost("http://u:p@my_host.example.com:81/s?t=1"))
    }

    @Test
    fun firstCandidateHttpsSucceedsHttpNeverFetched() {
        val fetched = mutableListOf<String>()
        val result = HttpUtil.firstSuccessfulCandidate(
            listOf("https://h/s", "http://h/s"),
            fetch = { fetched += it; "content" },
            parse = { 3 },
        )
        assertEquals("https://h/s" to 3, result)
        assertEquals(listOf("https://h/s"), fetched)
    }

    @Test
    fun firstCandidateFallsBackWhenHttpsEmpty() {
        val result = HttpUtil.firstSuccessfulCandidate(
            listOf("https://h/s", "http://h/s"),
            fetch = { if (it.startsWith("https")) "" else "content" },
            parse = { 2 },
        )
        assertEquals("http://h/s" to 2, result)
    }

    @Test
    fun firstCandidateFallsBackWhenHttpsParsesToZero() {
        val result = HttpUtil.firstSuccessfulCandidate(
            listOf("https://h/s", "http://h/s"),
            fetch = { if (it.startsWith("https")) "captive" else "content" },
            parse = { if (it == "content") 5 else 0 },
        )
        assertEquals("http://h/s" to 5, result)
    }

    @Test
    fun firstCandidateNullWhenAllFail() {
        assertNull(HttpUtil.firstSuccessfulCandidate(listOf("https://h/s", "http://h/s"), fetch = { "" }, parse = { 1 }))
        assertNull(HttpUtil.firstSuccessfulCandidate(listOf("https://h/s", "http://h/s"), fetch = { "x" }, parse = { 0 }))
    }

    @Test
    fun firstCandidateNullForEmptyList() {
        var fetched = false
        assertNull(HttpUtil.firstSuccessfulCandidate(emptyList(), fetch = { fetched = true; "x" }, parse = { 1 }))
        assertFalse(fetched)
    }
}

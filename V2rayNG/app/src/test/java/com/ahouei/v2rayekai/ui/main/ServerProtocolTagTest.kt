package com.ahouei.v2rayekai.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerProtocolTagTest {
    @Test
    fun takesFirstSegmentUpperCased() {
        assertEquals("VLESS", serverProtocolTag("VLESS / ws / tls"))
        assertEquals("SHADOWSOCKS", serverProtocolTag("shadowsocks"))
        assertEquals("CUSTOM", serverProtocolTag("CUSTOM"))
    }

    @Test
    fun emptyAndBlankGiveEmpty() {
        assertEquals("", serverProtocolTag(""))
        assertEquals("", serverProtocolTag("   "))
    }
}

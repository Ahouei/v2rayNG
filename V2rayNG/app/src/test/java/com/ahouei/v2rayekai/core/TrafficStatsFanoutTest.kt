package com.ahouei.v2rayekai.core

import com.ahouei.v2rayekai.AppConfig
import com.ahouei.v2rayekai.dto.OutboundTrafficStat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrafficStatsFanoutTest {
    private fun stat(tag: String, dir: String, v: Long) = OutboundTrafficStat(tag, dir, v)

    @Test
    fun eachConsumerSeesAllBytesOnce() {
        val fanout = TrafficStatsFanout()
        val first = listOf(stat("proxy", AppConfig.DOWNLINK, 100), stat("proxy", AppConfig.UPLINK, 10))
        val second = listOf(stat("proxy", AppConfig.DOWNLINK, 50))

        val ui = fanout.drain(TrafficStatsFanout.Consumer.UI) { first }
        assertEquals(TrafficStatsFanout.totals(first), TrafficStatsFanout.totals(ui))

        // Notification reads later: gets both queries' bytes, not stolen by the UI.
        val notification = fanout.drain(TrafficStatsFanout.Consumer.NOTIFICATION) { second }
        assertEquals(150L to 10L, TrafficStatsFanout.totals(notification))

        // UI now only gets the second query's bytes.
        val ui2 = fanout.drain(TrafficStatsFanout.Consumer.UI) { emptyList() }
        assertEquals(50L to 0L, TrafficStatsFanout.totals(ui2))
    }

    @Test
    fun resetDropsPendingCounters() {
        val fanout = TrafficStatsFanout()
        fanout.drain(TrafficStatsFanout.Consumer.UI) { listOf(stat("proxy", AppConfig.DOWNLINK, 100)) }
        fanout.reset()
        assertTrue(fanout.drain(TrafficStatsFanout.Consumer.NOTIFICATION) { emptyList() }.isEmpty())
    }

    @Test
    fun totalsExcludeBlockedAndIncludeDirect() {
        val stats = listOf(
            stat(AppConfig.TAG_BLOCKED, AppConfig.DOWNLINK, 999),
            stat(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK, 5),
            stat("proxy", AppConfig.UPLINK, 7),
        )
        assertEquals(5L to 7L, TrafficStatsFanout.totals(stats))
    }

    @Test
    fun perSecondHandlesZeroElapsed() {
        assertEquals(0L, TrafficStatsFanout.perSecond(100, 0))
        assertEquals(2000L, TrafficStatsFanout.perSecond(1000, 500))
    }
}

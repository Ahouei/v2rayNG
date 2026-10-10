package com.ahouei.v2rayekai.core

import com.ahouei.v2rayekai.AppConfig
import com.ahouei.v2rayekai.dto.OutboundTrafficStat

/**
 * The core resets its outbound counters on every query, so two readers (the speed notification
 * and the live UI sampler) would steal bytes from each other. This fan-out keeps one pending
 * counter set per consumer: every core query is added to all consumers, and a consumer drains
 * only its own set. Each consumer therefore sees exactly what it would see as the only reader.
 */
internal class TrafficStatsFanout {
    enum class Consumer { NOTIFICATION, UI }

    private val pending = Consumer.entries.associateWith { linkedMapOf<Pair<String, String>, Long>() }

    /** Adds one core query result to every consumer, then returns and clears [consumer]'s share. */
    @Synchronized
    fun drain(consumer: Consumer, query: () -> List<OutboundTrafficStat>): List<OutboundTrafficStat> {
        val fresh = query()
        for (counters in pending.values) {
            for (stat in fresh) {
                val key = stat.tag to stat.direction
                counters[key] = (counters[key] ?: 0L) + stat.value
            }
        }
        val own = pending.getValue(consumer)
        val result = own.map { (key, value) -> OutboundTrafficStat(key.first, key.second, value) }
        own.clear()
        return result
    }

    /** Drops every pending counter, used when the core stops and its counters disappear. */
    @Synchronized
    fun reset() {
        pending.values.forEach { it.clear() }
    }

    companion object {
        /** Total (down, up) bytes of every outbound except the blocking one. */
        fun totals(stats: List<OutboundTrafficStat>): Pair<Long, Long> {
            var down = 0L
            var up = 0L
            for (stat in stats) {
                if (stat.tag == AppConfig.TAG_BLOCKED) continue
                when (stat.direction) {
                    AppConfig.DOWNLINK -> down += stat.value
                    AppConfig.UPLINK -> up += stat.value
                }
            }
            return down to up
        }

        /** Converts a byte count over [elapsedMillis] into bytes per second; 0 for no elapsed time. */
        fun perSecond(bytes: Long, elapsedMillis: Long): Long =
            if (elapsedMillis <= 0L || bytes <= 0L) 0L else bytes * 1000L / elapsedMillis
    }
}

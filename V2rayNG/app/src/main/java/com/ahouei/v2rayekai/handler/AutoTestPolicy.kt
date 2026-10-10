package com.ahouei.v2rayekai.handler

import com.ahouei.v2rayekai.dto.AutoTestNetwork

/**
 * Pure decisions of the scheduled automatic server test; no Android types so every branch is
 * covered by JVM tests. All times must come from one
 * monotonic clock (SystemClock.elapsedRealtime milliseconds); never mix in wall-clock time.
 */
object AutoTestPolicy {
    /** A scheduled or network-change test never runs sooner than this after any bulk test. */
    const val MIN_GAP_MILLIS: Long = 10 * 60_000L

    enum class Trigger { SCHEDULE, NETWORK_CHANGE }

    enum class Decision {
        RUN,
        SKIP_DISABLED,
        SKIP_NOT_CONNECTED,
        SKIP_BUSY,
        SKIP_NOT_DUE,
        SKIP_TOO_SOON,
        SKIP_BATTERY_SAVER,
        SKIP_NO_NETWORK,
        SKIP_NOT_WIFI,
        /** Wi-Fi-only is on but the network type is not known yet; retry soon without re-anchoring. */
        DEFER_NETWORK_UNKNOWN,
    }

    data class Inputs(
        val nowMillis: Long,
        /** Start of the last bulk test (manual or automatic), or null when none ran yet. */
        val lastRunMillis: Long?,
        val intervalMinutes: Int,
        val connected: Boolean,
        val isTesting: Boolean,
        val batterySaver: Boolean,
        val wifiOnly: Boolean,
        /** Current network type, or null before the first network callback. */
        val network: AutoTestNetwork?,
        val trigger: Trigger,
        /** Network-change trigger only: whether the "test when network changes" option is on. */
        val testOnNetworkChange: Boolean = true,
    )

    fun decide(i: Inputs): Decision {
        if (i.intervalMinutes <= 0) return Decision.SKIP_DISABLED
        if (i.trigger == Trigger.NETWORK_CHANGE && !i.testOnNetworkChange) return Decision.SKIP_DISABLED
        if (!i.connected) return Decision.SKIP_NOT_CONNECTED
        if (i.isTesting) return Decision.SKIP_BUSY
        val sinceLast = i.lastRunMillis?.let { i.nowMillis - it }
        if (i.trigger == Trigger.SCHEDULE && sinceLast != null && sinceLast < intervalMillis(i.intervalMinutes)) {
            return Decision.SKIP_NOT_DUE
        }
        if (sinceLast != null && sinceLast < MIN_GAP_MILLIS) return Decision.SKIP_TOO_SOON
        if (i.batterySaver) return Decision.SKIP_BATTERY_SAVER
        if (i.network == AutoTestNetwork.NONE) return Decision.SKIP_NO_NETWORK
        if (i.wifiOnly && i.network == null) return Decision.DEFER_NETWORK_UNKNOWN
        if (i.wifiOnly && i.network != AutoTestNetwork.WIFI) return Decision.SKIP_NOT_WIFI
        return Decision.RUN
    }

    fun intervalMillis(intervalMinutes: Int): Long = intervalMinutes.coerceAtLeast(0) * 60_000L

    /**
     * Next scheduled check: one interval after [anchorMillis] (the last run or the last skipped
     * check, whichever is later), or null when the schedule is off.
     */
    fun nextRunAtMillis(anchorMillis: Long, intervalMinutes: Int): Long? =
        if (intervalMinutes <= 0) null else anchorMillis + intervalMillis(intervalMinutes)

    /** Whole minutes until [nextRunAtMillis], rounded up, never below 0. */
    fun minutesUntil(nextRunAtMillis: Long, nowMillis: Long): Int {
        val remaining = nextRunAtMillis - nowMillis
        if (remaining <= 0) return 0
        return ((remaining + 59_999L) / 60_000L).toInt()
    }

    /** True for a real Wi-Fi <-> cellular handover; losing or regaining all networks is not one. */
    fun isWifiCellularSwitch(previous: AutoTestNetwork?, current: AutoTestNetwork): Boolean {
        val a = previous ?: return false
        return (a == AutoTestNetwork.WIFI && current == AutoTestNetwork.CELLULAR) ||
            (a == AutoTestNetwork.CELLULAR && current == AutoTestNetwork.WIFI)
    }
}

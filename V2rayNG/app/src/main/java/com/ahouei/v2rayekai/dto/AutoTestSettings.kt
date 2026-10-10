package com.ahouei.v2rayekai.dto

/** Persisted options of the scheduled automatic server test and auto switch (Easy mode). */
data class AutoTestSettings(
    /** Minutes between scheduled tests; 0 turns the schedule off. */
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val wifiOnly: Boolean = false,
    val testOnNetworkChange: Boolean = true,
    val notifyOnSwitch: Boolean = true,
    val autoSwitch: Boolean = true,
) {
    val scheduleEnabled: Boolean get() = intervalMinutes > 0

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30

        /** Selectable intervals in minutes; 0 means Off. */
        val INTERVAL_OPTIONS: List<Int> = listOf(0, 15, 30, 60, 180)

        /** Maps an unknown stored value back to the default so a bad write cannot disable or spam tests. */
        fun normalizeInterval(minutes: Int): Int =
            if (minutes in INTERVAL_OPTIONS) minutes else DEFAULT_INTERVAL_MINUTES
    }
}

/** Physical (non-VPN) network the device currently uses, as far as the auto test cares. */
enum class AutoTestNetwork { WIFI, CELLULAR, OTHER, NONE }

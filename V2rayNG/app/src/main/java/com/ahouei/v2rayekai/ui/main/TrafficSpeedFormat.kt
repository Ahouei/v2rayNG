package com.ahouei.v2rayekai.ui.main

import java.util.Locale

/** Unit of a formatted speed; each maps to a string resource with a number placeholder. */
internal enum class SpeedUnit { BYTES, KILOBYTES, MEGABYTES, GIGABYTES }

/** A speed split into a locale-neutral number and its unit, rendered via string resources. */
internal data class SpeedValue(val number: String, val unit: SpeedUnit)

/** Pure, deterministic bytes-per-second formatting for the Easy home traffic line. */
internal object TrafficSpeedFormat {
    private const val STEP = 1024.0

    fun format(bytesPerSec: Long): SpeedValue {
        val bytes = bytesPerSec.coerceAtLeast(0L)
        if (bytes < STEP) return SpeedValue(bytes.toString(), SpeedUnit.BYTES)
        var value = bytes / STEP
        var unit = SpeedUnit.KILOBYTES
        // Round before choosing the unit so 1023.99 KB becomes 1.0 MB, not "1024 KB".
        while (rounded(value) >= STEP && unit != SpeedUnit.GIGABYTES) {
            value /= STEP
            unit = SpeedUnit.entries[unit.ordinal + 1]
        }
        val oneDecimal = roundTo(value, 10.0)
        val number = if (oneDecimal < 100.0) String.format(Locale.ROOT, "%.1f", oneDecimal)
        else String.format(Locale.ROOT, "%.0f", value)
        return SpeedValue(number, unit)
    }

    /** Value as it will be displayed: one decimal below 100, otherwise whole. */
    private fun rounded(value: Double): Double {
        val oneDecimal = roundTo(value, 10.0)
        return if (oneDecimal < 100.0) oneDecimal else roundTo(value, 1.0)
    }

    private fun roundTo(value: Double, scale: Double): Double = Math.round(value * scale) / scale
}

package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficSpeedFormatTest {
    @Test
    fun formatsEachUnit() {
        assertEquals(SpeedValue("0", SpeedUnit.BYTES), TrafficSpeedFormat.format(0))
        assertEquals(SpeedValue("0", SpeedUnit.BYTES), TrafficSpeedFormat.format(-5))
        assertEquals(SpeedValue("1023", SpeedUnit.BYTES), TrafficSpeedFormat.format(1023))
        assertEquals(SpeedValue("1.0", SpeedUnit.KILOBYTES), TrafficSpeedFormat.format(1024))
        assertEquals(SpeedValue("380", SpeedUnit.KILOBYTES), TrafficSpeedFormat.format(380L * 1024))
        assertEquals(SpeedValue("4.2", SpeedUnit.MEGABYTES), TrafficSpeedFormat.format((4.2 * 1024 * 1024).toLong()))
        assertEquals(SpeedValue("2.0", SpeedUnit.GIGABYTES), TrafficSpeedFormat.format(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun roundsBeforeChoosingUnit() {
        assertEquals(SpeedValue("1.0", SpeedUnit.MEGABYTES), TrafficSpeedFormat.format(1048575))
        assertEquals(SpeedValue("1.0", SpeedUnit.GIGABYTES), TrafficSpeedFormat.format(1024L * 1024 * 1024 - 1))
        assertEquals(SpeedValue("100", SpeedUnit.KILOBYTES), TrafficSpeedFormat.format((99.96 * 1024).toLong()))
        assertEquals(SpeedValue("1024", SpeedUnit.GIGABYTES), TrafficSpeedFormat.format(1024L * 1024 * 1024 * 1024))
    }
}

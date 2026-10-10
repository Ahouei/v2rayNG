package com.v2ray.ang.handler

import com.v2ray.ang.dto.AutoTestNetwork
import com.v2ray.ang.dto.AutoTestSettings
import com.v2ray.ang.handler.AutoTestPolicy.Decision
import com.v2ray.ang.handler.AutoTestPolicy.Inputs
import com.v2ray.ang.handler.AutoTestPolicy.Trigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoTestPolicyTest {
    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun inputs(
        lastRun: Long? = now - 31 * minute,
        interval: Int = 30,
        connected: Boolean = true,
        isTesting: Boolean = false,
        batterySaver: Boolean = false,
        wifiOnly: Boolean = false,
        network: AutoTestNetwork? = AutoTestNetwork.WIFI,
        trigger: Trigger = Trigger.SCHEDULE,
        onNetworkChange: Boolean = true,
    ) = Inputs(now, lastRun, interval, connected, isTesting, batterySaver, wifiOnly, network, trigger, onNetworkChange)

    @Test
    fun runsWhenDueAndAllowed() {
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs()))
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(lastRun = null)))
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(lastRun = now - 30 * minute)))
    }

    @Test
    fun offIntervalDisablesEveryTrigger() {
        assertEquals(Decision.SKIP_DISABLED, AutoTestPolicy.decide(inputs(interval = 0)))
        assertEquals(Decision.SKIP_DISABLED, AutoTestPolicy.decide(inputs(interval = 0, trigger = Trigger.NETWORK_CHANGE)))
    }

    @Test
    fun networkChangeOptionOffSkipsOnlyNetworkTrigger() {
        assertEquals(
            Decision.SKIP_DISABLED,
            AutoTestPolicy.decide(inputs(trigger = Trigger.NETWORK_CHANGE, onNetworkChange = false))
        )
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(onNetworkChange = false)))
    }

    @Test
    fun skipsWhenNotConnectedOrBusy() {
        assertEquals(Decision.SKIP_NOT_CONNECTED, AutoTestPolicy.decide(inputs(connected = false)))
        assertEquals(Decision.SKIP_BUSY, AutoTestPolicy.decide(inputs(isTesting = true)))
    }

    @Test
    fun scheduleWaitsForTheInterval() {
        assertEquals(Decision.SKIP_NOT_DUE, AutoTestPolicy.decide(inputs(lastRun = now - 29 * minute)))
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(interval = 15, lastRun = now - 15 * minute)))
    }

    @Test
    fun networkChangeRespectsTenMinuteGapButNotInterval() {
        assertEquals(
            Decision.SKIP_TOO_SOON,
            AutoTestPolicy.decide(inputs(lastRun = now - 9 * minute, trigger = Trigger.NETWORK_CHANGE))
        )
        assertEquals(
            Decision.RUN,
            AutoTestPolicy.decide(inputs(lastRun = now - 10 * minute, trigger = Trigger.NETWORK_CHANGE))
        )
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(lastRun = null, trigger = Trigger.NETWORK_CHANGE)))
    }

    @Test
    fun batterySaverSkips() {
        assertEquals(Decision.SKIP_BATTERY_SAVER, AutoTestPolicy.decide(inputs(batterySaver = true)))
        assertEquals(
            Decision.SKIP_BATTERY_SAVER,
            AutoTestPolicy.decide(inputs(batterySaver = true, trigger = Trigger.NETWORK_CHANGE))
        )
    }

    @Test
    fun wifiOnlyRequiresWifi() {
        assertEquals(
            Decision.SKIP_NOT_WIFI,
            AutoTestPolicy.decide(inputs(wifiOnly = true, network = AutoTestNetwork.CELLULAR))
        )
        assertEquals(
            Decision.SKIP_NOT_WIFI,
            AutoTestPolicy.decide(inputs(wifiOnly = true, network = AutoTestNetwork.OTHER))
        )
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(wifiOnly = true, network = AutoTestNetwork.WIFI)))
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(network = AutoTestNetwork.CELLULAR)))
    }

    @Test
    fun unknownNetworkDefersOnlyWhenWifiOnly() {
        assertEquals(Decision.DEFER_NETWORK_UNKNOWN, AutoTestPolicy.decide(inputs(wifiOnly = true, network = null)))
        assertEquals(Decision.RUN, AutoTestPolicy.decide(inputs(network = null)))
    }

    @Test
    fun noNetworkSkips() {
        assertEquals(Decision.SKIP_NO_NETWORK, AutoTestPolicy.decide(inputs(network = AutoTestNetwork.NONE)))
    }

    @Test
    fun nextRunAndMinutes() {
        assertNull(AutoTestPolicy.nextRunAtMillis(now, 0))
        assertEquals(now + 30 * minute, AutoTestPolicy.nextRunAtMillis(now, 30))
        assertEquals(0, AutoTestPolicy.minutesUntil(now, now))
        assertEquals(0, AutoTestPolicy.minutesUntil(now - 1, now))
        assertEquals(1, AutoTestPolicy.minutesUntil(now + 1, now))
        assertEquals(30, AutoTestPolicy.minutesUntil(now + 30 * minute, now))
        assertEquals(31, AutoTestPolicy.minutesUntil(now + 30 * minute + 1, now))
    }

    @Test
    fun onlyWifiCellularHandoverCounts() {
        assertTrue(AutoTestPolicy.isWifiCellularSwitch(AutoTestNetwork.WIFI, AutoTestNetwork.CELLULAR))
        assertTrue(AutoTestPolicy.isWifiCellularSwitch(AutoTestNetwork.CELLULAR, AutoTestNetwork.WIFI))
        assertFalse(AutoTestPolicy.isWifiCellularSwitch(null, AutoTestNetwork.WIFI))
        assertFalse(AutoTestPolicy.isWifiCellularSwitch(AutoTestNetwork.NONE, AutoTestNetwork.WIFI))
        assertFalse(AutoTestPolicy.isWifiCellularSwitch(AutoTestNetwork.WIFI, AutoTestNetwork.NONE))
        assertFalse(AutoTestPolicy.isWifiCellularSwitch(AutoTestNetwork.WIFI, AutoTestNetwork.WIFI))
    }

    @Test
    fun settingsDefaultsAndIntervalNormalization() {
        val defaults = AutoTestSettings()
        assertEquals(30, defaults.intervalMinutes)
        assertFalse(defaults.wifiOnly)
        assertTrue(defaults.testOnNetworkChange)
        assertTrue(defaults.notifyOnSwitch)
        assertTrue(defaults.autoSwitch)
        assertTrue(defaults.scheduleEnabled)
        assertFalse(AutoTestSettings(intervalMinutes = 0).scheduleEnabled)
        AutoTestSettings.INTERVAL_OPTIONS.forEach { assertEquals(it, AutoTestSettings.normalizeInterval(it)) }
        assertEquals(30, AutoTestSettings.normalizeInterval(7))
        assertEquals(30, AutoTestSettings.normalizeInterval(-1))
    }
}

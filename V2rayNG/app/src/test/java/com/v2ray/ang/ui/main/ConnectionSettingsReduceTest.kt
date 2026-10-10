package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.AutoTestSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionSettingsReduceTest {
    private val initial = AutoTestSettings()

    @Test
    fun eachActionChangesOnlyItsField() {
        val reduce = ConnectionSettingsViewModel.Companion::reduce
        assertEquals(initial.copy(intervalMinutes = 60), reduce(initial, ConnectionSettingsAction.SetInterval(60)))
        assertEquals(initial.copy(intervalMinutes = 0), reduce(initial, ConnectionSettingsAction.SetInterval(0)))
        assertEquals(initial.copy(wifiOnly = true), reduce(initial, ConnectionSettingsAction.SetWifiOnly(true)))
        assertEquals(
            initial.copy(testOnNetworkChange = false),
            reduce(initial, ConnectionSettingsAction.SetTestOnNetworkChange(false))
        )
        assertEquals(initial.copy(autoSwitch = false), reduce(initial, ConnectionSettingsAction.SetAutoSwitch(false)))
        assertEquals(
            initial.copy(notifyOnSwitch = false),
            reduce(initial, ConnectionSettingsAction.SetNotifyOnSwitch(false))
        )
    }

    @Test
    fun invalidIntervalFallsBackToDefault() {
        val state = initial.copy(intervalMinutes = 60)
        assertEquals(initial.copy(intervalMinutes = 30), ConnectionSettingsViewModel.reduce(state, ConnectionSettingsAction.SetInterval(45)))
    }
}

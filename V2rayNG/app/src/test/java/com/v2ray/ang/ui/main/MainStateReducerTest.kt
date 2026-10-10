package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.TrafficSpeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainStateReducerTest {
    private val sample = TrafficSpeed(4096, 512)

    @Test
    fun trafficClearedOnStopAndKeptWhileRunning() {
        assertNull(MainStateReducer.trafficAfterRunning(sample, running = false))
        assertEquals(sample, MainStateReducer.trafficAfterRunning(sample, running = true))
    }

    @Test
    fun trafficSampleIgnoredWhenNotRunning() {
        assertNull(MainStateReducer.trafficSample(null, isRunning = false, sample = sample))
        assertEquals(sample, MainStateReducer.trafficSample(null, isRunning = true, sample = sample))
    }

    @Test
    fun measuredDelayFiledUnderGuidSelectedAtTestStart() {
        val state = MainUiState(selectedGuid = "b", isRunning = true)
        val result = MainStateReducer.measured(state, "a", ConnectionTestResult(48), isTesting = false)
        assertEquals(ServerDelay("a", 48), result.currentServerDelay)
        assertEquals(MainStatus.ConnectionTest(ConnectionTestResult(48)), result.status)
    }

    @Test
    fun measuredWithoutGuidKeepsPreviousDelay() {
        val state = MainUiState(currentServerDelay = ServerDelay("a", 48))
        assertEquals(ServerDelay("a", 48), MainStateReducer.measured(state, null, ConnectionTestResult(-1), false).currentServerDelay)
    }

    @Test
    fun stopClearsPostConnectDelay() {
        val state = MainUiState(isRunning = true, currentServerDelay = ServerDelay("a", 48))
        val stopped = MainStateReducer.running(state, running = false, isTesting = false, clearTestingText = true)
        assertNull(stopped.currentServerDelay)
        assertEquals(MainStatus.Disconnected, stopped.status)
        val started = MainStateReducer.running(state, running = true, isTesting = false, clearTestingText = true)
        assertEquals(ServerDelay("a", 48), started.currentServerDelay)
    }

    @Test
    fun bulkTestStartClearsPostConnectDelay() {
        val state = MainUiState(currentServerDelay = ServerDelay("a", 48))
        val testing = MainStateReducer.bulkTestStarted(state)
        assertNull(testing.currentServerDelay)
        assertEquals(MainStatus.Testing, testing.status)
    }
}

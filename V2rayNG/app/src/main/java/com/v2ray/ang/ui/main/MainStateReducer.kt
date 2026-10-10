package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.TrafficSpeed

/** Pure state transitions of [MainViewModel] for connection state, live traffic and the current-server RTT. */
internal object MainStateReducer {

    /** Running flag change; a disconnect drops the post-connect RTT so a stale value is never shown. */
    fun running(state: MainUiState, running: Boolean, isTesting: Boolean, clearTestingText: Boolean): MainUiState =
        state.copy(
            isRunning = running,
            isTesting = isTesting,
            currentServerDelay = if (running) state.currentServerDelay else null,
            status = if (!clearTestingText && state.isRunning == running) state.status
            else if (running) MainStatus.Connected else MainStatus.Disconnected
        )

    /** Live traffic after a running change: cleared on disconnect. */
    fun trafficAfterRunning(current: TrafficSpeed?, running: Boolean): TrafficSpeed? = if (running) current else null

    /** A traffic sample is accepted only while connected. */
    fun trafficSample(current: TrafficSpeed?, isRunning: Boolean, sample: TrafficSpeed): TrafficSpeed? =
        if (isRunning) sample else current

    /** Files the measured delay under [testGuid], the GUID selected when the measurement began. */
    fun measured(state: MainUiState, testGuid: String?, result: ConnectionTestResult, isTesting: Boolean): MainUiState =
        state.copy(
            isTesting = isTesting,
            status = MainStatus.ConnectionTest(result),
            currentServerDelay = testGuid?.let { ServerDelay(it, result.delayMillis) } ?: state.currentServerDelay,
        )

    /** A bulk test replaces stored results, so the earlier post-connect RTT no longer is the latest. */
    fun bulkTestStarted(state: MainUiState): MainUiState =
        state.copy(isTesting = true, status = MainStatus.Testing, currentServerDelay = null)
}

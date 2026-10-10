package com.ahouei.v2rayekai.ui.main

import com.ahouei.v2rayekai.dto.AutoSwitchRules
import com.ahouei.v2rayekai.dto.ConnectionTestResult
import com.ahouei.v2rayekai.dto.TrafficSpeed
import com.ahouei.v2rayekai.handler.AutoSwitchEngine

/** Pure state transitions of [MainViewModel] for connection state, live traffic and the current-server RTT, and the automatic-switch glue. */
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

    // ---------- Automatic switching ----------

    /** Auto switch applies only in Easy mode, connected, with "Switch automatically" on. */
    fun autoSwitchActive(state: MainUiState, autoSwitch: Boolean): Boolean =
        state.isRunning && state.easyMode && autoSwitch

    fun autoSwitchInputs(
        state: MainUiState,
        autoSwitch: Boolean,
        history: AutoSwitchEngine.History,
        nowMillis: Long,
        rules: AutoSwitchRules,
        cachedResults: Map<String, Long>,
    ): AutoSwitchEngine.Inputs = AutoSwitchEngine.Inputs(
        history = history,
        currentGuid = state.selectedGuid,
        nowMillis = nowMillis,
        rules = rules,
        enabled = autoSwitchActive(state, autoSwitch),
        fastestMode = state.fastestMode,
        currentRttMillis = state.currentServerDelay
            ?.takeIf { it.guid == state.selectedGuid && it.delayMillis > 0L }?.delayMillis,
        cachedResults = cachedResults,
    )

    /**
     * True when a queued [event] still holds: the same server is selected and a fresh decision on
     * current state returns the same target and reason (so a stale or pinned speed switch is dropped).
     */
    fun shouldApplyAutoSwitch(
        event: MainViewModelEvent.AutoSwitchServer,
        state: MainUiState,
        fresh: AutoSwitchEngine.Decision,
    ): Boolean =
        state.selectedGuid == event.fromGuid && event.toGuid != event.fromGuid &&
            fresh is AutoSwitchEngine.Decision.Switch &&
            fresh.targetGuid == event.toGuid && fresh.reason == event.reason

    /** Message for an applied switch, or null when "notify when switching" is off. */
    fun autoSwitchNotice(
        event: MainViewModelEvent.AutoSwitchServer,
        notify: Boolean,
        id: Long,
        fromName: String,
        toName: String,
    ): AutoSwitchNotice? = if (!notify) null else AutoSwitchNotice(
        id = id,
        fromGuid = event.fromGuid,
        fromName = fromName,
        toName = toName,
        reason = event.reason,
        gainMillis = event.gainMillis,
    )
}

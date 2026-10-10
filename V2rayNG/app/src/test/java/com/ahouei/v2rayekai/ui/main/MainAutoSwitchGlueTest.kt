package com.ahouei.v2rayekai.ui.main

import com.ahouei.v2rayekai.dto.AutoSwitchRules
import com.ahouei.v2rayekai.handler.AutoSwitchEngine
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.Decision
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.Reason
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.StayReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainAutoSwitchGlueTest {
    private val rules = AutoSwitchRules()
    private val connectedEasy = MainUiState(selectedGuid = "cur", isRunning = true, easyMode = true, fastestMode = true)
    private val run = mapOf("cur" to 300L, "fast" to 100L)
    private val threeRuns = (1..3).fold(AutoSwitchEngine.History()) { h, _ -> AutoSwitchEngine.recordRun(h, run) }
    private val fasterEvent = MainViewModelEvent.AutoSwitchServer("cur", "fast", Reason.FASTER, 200L)

    private fun decide(state: MainUiState, autoSwitch: Boolean = true, history: AutoSwitchEngine.History = threeRuns) =
        AutoSwitchEngine.decide(MainStateReducer.autoSwitchInputs(state, autoSwitch, history, 1_000_000L, rules, emptyMap()))

    @Test
    fun activeOnlyInEasyModeConnectedWithSwitchOn() {
        assertTrue(MainStateReducer.autoSwitchActive(connectedEasy, autoSwitch = true))
        assertFalse(MainStateReducer.autoSwitchActive(connectedEasy.copy(easyMode = false), autoSwitch = true))
        assertFalse(MainStateReducer.autoSwitchActive(connectedEasy.copy(isRunning = false), autoSwitch = true))
        assertFalse(MainStateReducer.autoSwitchActive(connectedEasy, autoSwitch = false))
    }

    @Test
    fun proModeNeverSwitches() {
        assertEquals(Decision.Stay(StayReason.DISABLED), decide(connectedEasy.copy(easyMode = false)))
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(connectedEasy))
    }

    @Test
    fun liveRttOfSelectedServerFeedsDecision() {
        val state = connectedEasy.copy(currentServerDelay = ServerDelay("cur", 120L))
        assertEquals(Decision.Stay(StayReason.CURRENT_FAST_ENOUGH), decide(state))
        // RTT measured for another server is ignored.
        val other = connectedEasy.copy(currentServerDelay = ServerDelay("old", 120L))
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(other))
    }

    @Test
    fun queuedEventAppliedOnlyWhenFreshDecisionAgrees() {
        assertTrue(MainStateReducer.shouldApplyAutoSwitch(fasterEvent, connectedEasy, decide(connectedEasy)))
        // Selection changed meanwhile.
        val moved = connectedEasy.copy(selectedGuid = "other")
        assertFalse(MainStateReducer.shouldApplyAutoSwitch(fasterEvent, moved, decide(moved)))
        // Fastest turned off (pinned) after the event was queued: no speed switch.
        val pinned = connectedEasy.copy(fastestMode = false)
        assertFalse(MainStateReducer.shouldApplyAutoSwitch(fasterEvent, pinned, decide(pinned)))
        // Fresh decision picks another target or reason.
        assertFalse(
            MainStateReducer.shouldApplyAutoSwitch(fasterEvent, connectedEasy, Decision.Switch("x", Reason.FASTER, 200L))
        )
        assertFalse(
            MainStateReducer.shouldApplyAutoSwitch(fasterEvent, connectedEasy, Decision.Switch("fast", Reason.DEAD, null))
        )
        // Event to the same server is never applied.
        val self = fasterEvent.copy(toGuid = "cur")
        assertFalse(MainStateReducer.shouldApplyAutoSwitch(self, connectedEasy, Decision.Switch("cur", Reason.FASTER, 0L)))
    }

    @Test
    fun noticeRespectsNotifySetting() {
        assertNull(MainStateReducer.autoSwitchNotice(fasterEvent, notify = false, id = 1L, fromName = "A", toName = "B"))
        assertEquals(
            AutoSwitchNotice(1L, "cur", "A", "B", Reason.FASTER, 200L),
            MainStateReducer.autoSwitchNotice(fasterEvent, notify = true, id = 1L, fromName = "A", toName = "B"),
        )
    }
}

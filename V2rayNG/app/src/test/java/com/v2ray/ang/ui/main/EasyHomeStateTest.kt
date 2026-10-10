package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyHomeStateTest {
    @Test
    fun defaultStateStartsInEasyModeWithNoServer() {
        val state = MainUiState()

        assertTrue(state.easyMode)
        assertEquals(EasyHomeState.NoServer, state.toEasyHomeState())
    }

    @Test
    fun emptySelectedGuidMeansNoServerEvenWhileRunning() {
        val state = MainUiState(selectedGuid = "", isRunning = true, selectedServerName = "Germany")

        assertEquals(EasyHomeState.NoServer, state.toEasyHomeState())
    }

    @Test
    fun selectedServerIsDisconnectedUntilServiceRuns() {
        val state = MainUiState(selectedGuid = "guid-1", selectedServerName = "Germany")

        assertEquals(EasyHomeState.Disconnected("Germany"), state.toEasyHomeState())
    }

    @Test
    fun runningServiceIsConnectedToTheSelectedServer() {
        val state = MainUiState(selectedGuid = "guid-1", selectedServerName = "Germany", isRunning = true)

        assertEquals(EasyHomeState.Connected("Germany"), state.toEasyHomeState())
    }

    @Test
    fun serverNameNotYetResolvedShowsEmptyName() {
        val state = MainUiState(selectedGuid = "guid-1", selectedServerName = null)

        assertEquals(EasyHomeState.Disconnected(""), state.toEasyHomeState())
    }
}

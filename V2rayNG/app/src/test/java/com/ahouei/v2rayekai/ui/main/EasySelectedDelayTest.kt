package com.ahouei.v2rayekai.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EasySelectedDelayTest {
    private fun row(guid: String, delay: Long) =
        EasyLocationRow(guid, guid, delay, EasyLocationRanking.quality(delay), 0)

    @Test
    fun unknownWhenNothingMeasured() {
        assertNull(EasyLocationRanking.selectedDelay("a", listOf(row("a", 0)), null))
        assertNull(EasyLocationRanking.selectedDelay(null, listOf(row("a", 50)), null))
    }

    @Test
    fun usesStoredResultByGuid() {
        assertEquals(48L, EasyLocationRanking.selectedDelay("b", listOf(row("a", 10), row("b", 48)), null))
        assertEquals(-1L, EasyLocationRanking.selectedDelay("a", listOf(row("a", -1)), null))
    }

    @Test
    fun measurementWinsOnlyForSameGuid() {
        val rows = listOf(row("a", 300))
        assertEquals(48L, EasyLocationRanking.selectedDelay("a", rows, ServerDelay("a", 48)))
        assertEquals(300L, EasyLocationRanking.selectedDelay("a", rows, ServerDelay("other", 48)))
        assertEquals(-1L, EasyLocationRanking.selectedDelay("a", rows, ServerDelay("a", -1)))
    }

    @Test
    fun staleMeasurementDroppedAfterDisconnectShowsLatestStoredResult() {
        val connected = MainUiState(isRunning = true, currentServerDelay = ServerDelay("a", 48))
        val stopped = MainStateReducer.running(connected, running = false, isTesting = false, clearTestingText = true)
        val rows = listOf(row("a", -1))
        assertEquals(-1L, EasyLocationRanking.selectedDelay("a", rows, stopped.currentServerDelay))
        val retested = MainStateReducer.bulkTestStarted(connected)
        assertEquals(-1L, EasyLocationRanking.selectedDelay("a", rows, retested.currentServerDelay))
    }
}

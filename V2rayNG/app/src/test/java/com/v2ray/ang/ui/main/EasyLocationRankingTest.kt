package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyLocationRankingTest {
    private fun server(guid: String, delay: Long, name: String = "name-$guid") =
        ServersCache(guid, ProfileItem(configType = EConfigType.VMESS, remarks = name), delay)

    @Test
    fun qualityBoundaries() {
        assertEquals(SignalQuality.EXCELLENT, EasyLocationRanking.quality(1))
        assertEquals(SignalQuality.EXCELLENT, EasyLocationRanking.quality(100))
        assertEquals(SignalQuality.GOOD, EasyLocationRanking.quality(101))
        assertEquals(SignalQuality.GOOD, EasyLocationRanking.quality(200))
        assertEquals(SignalQuality.FAIR, EasyLocationRanking.quality(201))
        assertEquals(SignalQuality.FAIR, EasyLocationRanking.quality(400))
        assertEquals(SignalQuality.POOR, EasyLocationRanking.quality(401))
        assertEquals(SignalQuality.UNTESTED, EasyLocationRanking.quality(0))
        assertEquals(SignalQuality.UNREACHABLE, EasyLocationRanking.quality(-1))
    }

    @Test
    fun barsMapping() {
        assertEquals(4, EasyLocationRanking.bars(SignalQuality.EXCELLENT))
        assertEquals(3, EasyLocationRanking.bars(SignalQuality.GOOD))
        assertEquals(2, EasyLocationRanking.bars(SignalQuality.FAIR))
        assertEquals(1, EasyLocationRanking.bars(SignalQuality.POOR))
        assertEquals(0, EasyLocationRanking.bars(SignalQuality.UNREACHABLE))
        assertEquals(0, EasyLocationRanking.bars(SignalQuality.UNTESTED))
    }

    @Test
    fun rankOrdersSuccessThenUntestedThenUnreachable() {
        val rows = EasyLocationRanking.rank(
            listOf(
                server("u2", 0), server("f1", -1), server("b", 300), server("u1", 0),
                server("a", 48), server("f0", -1), server("d", 48), server("c", 48),
            )
        )
        assertEquals(listOf("a", "c", "d", "b", "u2", "u1", "f1", "f0"), rows.map { it.guid })
        assertEquals("name-a", rows.first().name)
        assertEquals(SignalQuality.EXCELLENT, rows.first().quality)
        assertEquals(4, rows.first().bars)
        assertEquals(SignalQuality.UNREACHABLE, rows.last().quality)
    }

    @Test
    fun rankCapsAtThirty() {
        val rows = EasyLocationRanking.rank((1..45).map { server("g$it", it.toLong()) })
        assertEquals(EasyLocationRanking.MAX_RANKED, rows.size)
        assertEquals("g1", rows.first().guid)
        assertEquals("g30", rows.last().guid)
    }

    @Test
    fun rankEmpty() {
        assertTrue(EasyLocationRanking.rank(emptyList()).isEmpty())
    }

    @Test
    fun fastestGuid() {
        assertEquals("b", EasyLocationRanking.fastestGuid(listOf(server("a", 90), server("c", 40), server("b", 40), server("x", -1))))
        assertNull(EasyLocationRanking.fastestGuid(listOf(server("a", 0), server("b", -1))))
        assertNull(EasyLocationRanking.fastestGuid(emptyList()))
    }

    @Test
    fun revealPaging() {
        var revealed = EasyLocationRanking.PAGE_SIZE
        assertEquals(10, revealed)
        revealed = EasyLocationRanking.nextRevealed(revealed, 30)
        assertEquals(20, revealed)
        revealed = EasyLocationRanking.nextRevealed(revealed, 30)
        assertEquals(30, revealed)
        assertEquals(30, EasyLocationRanking.nextRevealed(revealed, 30))
        assertEquals(7, EasyLocationRanking.visibleCount(10, 7))
        assertEquals(31, EasyLocationRanking.visibleCount(40, 31))
    }

    @Test
    fun nextPageSizeShrinksOnLastPage() {
        assertEquals(10, EasyLocationRanking.nextPageSize(10, 31))
        assertEquals(1, EasyLocationRanking.nextPageSize(30, 31))
        assertEquals(5, EasyLocationRanking.nextPageSize(10, 15))
        assertEquals(0, EasyLocationRanking.nextPageSize(10, 7))
    }

    @Test
    fun rankPinsSelectedServerOutsideTop() {
        val servers = (1..40).map { server("g$it", it.toLong()) }
        val rows = EasyLocationRanking.rank(servers, "g40")
        assertEquals(EasyLocationRanking.MAX_RANKED + 1, rows.size)
        assertEquals("g40", rows.last().guid)
        assertEquals(EasyLocationRanking.MAX_RANKED, EasyLocationRanking.rank(servers, "g5").size)
        assertEquals(EasyLocationRanking.MAX_RANKED, EasyLocationRanking.rank(servers, "missing").size)
        assertEquals(EasyLocationRanking.MAX_RANKED, EasyLocationRanking.rank(servers, null).size)
    }

    @Test
    fun initialRevealedCoversSelectedRow() {
        val rows = EasyLocationRanking.rank((1..40).map { server("g$it", it.toLong()) }, "g40")
        assertEquals(10, EasyLocationRanking.initialRevealed(rows, "g3"))
        assertEquals(10, EasyLocationRanking.initialRevealed(rows, null))
        assertEquals(20, EasyLocationRanking.initialRevealed(rows, "g11"))
        assertEquals(40, EasyLocationRanking.initialRevealed(rows, "g40"))
    }

    @Test
    fun fastestSelectedIsDerivedFromSelection() {
        assertTrue(EasyLocationState(fastestGuid = "a").isFastestSelected("a"))
        assertFalse(EasyLocationState(fastestGuid = "a").isFastestSelected("b"))
        assertFalse(EasyLocationState(fastestGuid = null).isFastestSelected(null))
        assertFalse(EasyLocationState(fastestGuid = null).isFastestSelected("a"))
    }

    @Test
    fun autoSelectTargetSwitchesToFastestSuccess() {
        val servers = listOf(server("a", 300), server("b", 80), server("c", -1))
        assertEquals("b", EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = false, easyMode = true))
        assertEquals("b", EasyLocationRanking.autoSelectTarget(servers, null, fastestMode = true, isTesting = false, easyMode = true))
    }

    @Test
    fun autoSelectTargetKeepsSelectionWithoutResults() {
        val servers = listOf(server("a", 0), server("b", 0))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = false, easyMode = true))
        assertNull(EasyLocationRanking.autoSelectTarget(emptyList(), "a", fastestMode = true, isTesting = false, easyMode = true))
    }

    @Test
    fun autoSelectTargetKeepsSelectionWhenAllFailed() {
        val servers = listOf(server("a", -1), server("b", -1))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = false, easyMode = true))
    }

    @Test
    fun autoSelectTargetNoopWhenAlreadySelected() {
        val servers = listOf(server("a", 50), server("b", 80))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = false, easyMode = true))
    }

    @Test
    fun autoSelectTargetWaitsWhileTesting() {
        val servers = listOf(server("a", 300), server("b", 80))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = true, easyMode = true))
    }

    @Test
    fun autoSelectTargetOffWhenFastestModeDisabled() {
        val servers = listOf(server("a", 300), server("b", 80))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = false, isTesting = false, easyMode = true))
    }

    @Test
    fun autoSelectTargetOffOutsideEasyMode() {
        val servers = listOf(server("a", 300), server("b", 80))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = true, isTesting = false, easyMode = false))
    }

    @Test
    fun autoSelectTargetKeepsSelectionFromAnotherGroup() {
        val servers = listOf(server("a", 300), server("b", 80))
        assertNull(EasyLocationRanking.autoSelectTarget(servers, "other", fastestMode = true, isTesting = false, easyMode = true))
    }

    @Test
    fun staleAutoSelectEventRejectedAfterManualPick() {
        val servers = listOf(server("a", 300), server("b", 80))
        // Event for "b" was emitted, then the user picked "a" manually, which turns fastest mode off.
        assertFalse(EasyLocationRanking.autoSelectTarget(servers, "a", fastestMode = false, isTesting = false, easyMode = true) == "b")
    }

    @Test
    fun shouldStartTestOnFastestWhenIdleWithoutResults() {
        val untested = listOf(server("a", 0), server("b", -1))
        assertTrue(EasyLocationRanking.shouldStartTestOnFastest(untested, enabled = true, isBulkTesting = false))
        assertTrue(EasyLocationRanking.shouldStartTestOnFastest(emptyList(), enabled = true, isBulkTesting = false))
    }

    @Test
    fun shouldNotStartTestOnFastestWhileTesting() {
        val untested = listOf(server("a", 0))
        assertFalse(EasyLocationRanking.shouldStartTestOnFastest(untested, enabled = true, isBulkTesting = true))
    }

    @Test
    fun shouldNotStartTestOnFastestWithResults() {
        val tested = listOf(server("a", 0), server("b", 80))
        assertFalse(EasyLocationRanking.shouldStartTestOnFastest(tested, enabled = true, isBulkTesting = false))
    }

    @Test
    fun shouldNotStartTestOnFastestWhenDisabled() {
        val untested = listOf(server("a", 0))
        assertFalse(EasyLocationRanking.shouldStartTestOnFastest(untested, enabled = false, isBulkTesting = false))
    }

    @Test
    fun fastestModeDefaultsOnInUiState() {
        assertTrue(MainUiState().fastestMode)
    }
}

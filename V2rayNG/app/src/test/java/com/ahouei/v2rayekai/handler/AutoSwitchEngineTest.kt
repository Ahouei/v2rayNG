package com.ahouei.v2rayekai.handler

import com.ahouei.v2rayekai.dto.AutoSwitchRules
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.Decision
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.History
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.Inputs
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.Reason
import com.ahouei.v2rayekai.handler.AutoSwitchEngine.StayReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoSwitchEngineTest {
    private val rules = AutoSwitchRules()
    private val now = 100_000_000L
    private val minute = 60_000L

    private fun runs(vararg r: Map<String, Long>): History =
        r.fold(History()) { h, run -> AutoSwitchEngine.recordRun(h, run) }

    private fun dead(history: History, guid: String = "cur", times: Int = 3): History =
        (1..times).fold(history) { h, _ -> AutoSwitchEngine.recordHealth(h, guid, -1L) }

    private fun decide(
        history: History,
        current: String? = "cur",
        enabled: Boolean = true,
        fastest: Boolean = true,
        rtt: Long? = null,
        at: Long = now,
        r: AutoSwitchRules = rules,
    ): Decision = AutoSwitchEngine.decide(Inputs(history, current, at, r, enabled, fastest, rtt))

    // A run where "fast" is clearly better than a slow current server (300 vs 100 ms).
    private val goodRun = mapOf("cur" to 300L, "fast" to 100L, "mid" to 250L)
    private val threeGood = runs(goodRun, goodRun, goodRun)

    // ---------- Defaults ----------

    @Test
    fun defaultsMatchAgreedRules() {
        assertEquals(3, rules.deadAfterFailures)
        assertEquals(10, rules.failingCheckSeconds)
        assertEquals(120, rules.healthCheckSeconds)
        assertEquals(150, rules.minCurrentRttMillis)
        assertEquals(60, rules.maxCandidatePercent)
        assertEquals(80, rules.minGainMillis)
        assertEquals(3, rules.confirmRuns)
        assertEquals(10, rules.minMinutesBetweenSwitches)
        assertEquals(30, rules.blockLeftMinutes)
    }

    // ---------- Gating ----------

    @Test
    fun disabledNeverSwitches() {
        assertEquals(Decision.Stay(StayReason.DISABLED), decide(dead(threeGood), enabled = false))
    }

    @Test
    fun noCurrentServer() {
        assertEquals(Decision.Stay(StayReason.NO_CURRENT), decide(threeGood, current = null))
        assertEquals(Decision.Stay(StayReason.NO_CURRENT), decide(threeGood, current = ""))
    }

    // ---------- Dead server ----------

    @Test
    fun twoFailuresAreNotDead() {
        val d = decide(dead(History(), times = 2).copy(runs = threeGood.runs), fastest = false)
        assertEquals(Decision.Stay(StayReason.PINNED), d)
    }

    @Test
    fun threeFailuresSwitchToBestReachable() {
        val history = dead(runs(mapOf("cur" to -1L, "a" to 200L, "b" to 90L, "c" to -1L)))
        assertEquals(Decision.Switch("b", Reason.DEAD, null), decide(history))
    }

    @Test
    fun successResetsFailureStreak() {
        var h = dead(History(), times = 2)
        h = AutoSwitchEngine.recordHealth(h, "cur", 120L)
        assertNull(h.failures)
        h = AutoSwitchEngine.recordHealth(h, "cur", -1L)
        assertEquals(1, h.failures?.count)
    }

    @Test
    fun untestedHealthResultKeepsStreak() {
        val h = dead(History(), times = 2)
        assertEquals(h.failures, AutoSwitchEngine.recordHealth(h, "cur", 0L).failures)
    }

    @Test
    fun failuresOfAnotherServerDoNotCount() {
        var h = dead(runs(goodRun), guid = "old", times = 3)
        assertEquals(Decision.Stay(StayReason.PINNED), decide(h, fastest = false))
        // A new server's failure restarts the count.
        h = AutoSwitchEngine.recordHealth(h, "cur", -1L)
        assertEquals(1, h.failures?.count)
    }

    @Test
    fun deadSwitchIgnoresPinAndGap() {
        val history = dead(runs(goodRun)).copy(lastSpeedSwitchAtMillis = now - minute)
        assertEquals(Decision.Switch("fast", Reason.DEAD, null), decide(history, fastest = false))
    }

    @Test
    fun deadWithNothingReachableStays() {
        assertEquals(Decision.Stay(StayReason.NO_REACHABLE), decide(dead(History())))
        val allFailed = dead(runs(mapOf("cur" to 100L, "a" to -1L)))
        assertEquals(Decision.Stay(StayReason.NO_REACHABLE), decide(allFailed))
    }

    @Test
    fun deadPrefersUnblockedButFallsBackToBlocked() {
        val base = dead(runs(mapOf("a" to 50L, "b" to 120L)))
        val aBlocked = base.copy(blockedUntilMillis = mapOf("a" to now + minute))
        assertEquals(Decision.Switch("b", Reason.DEAD, null), decide(aBlocked))
        val allBlocked = base.copy(blockedUntilMillis = mapOf("a" to now + minute, "b" to now + minute))
        assertEquals(Decision.Switch("a", Reason.DEAD, null), decide(allBlocked))
    }

    @Test
    fun deadTieBreaksByGuid() {
        val h = dead(runs(mapOf("z" to 80L, "y" to 80L)))
        assertEquals(Decision.Switch("y", Reason.DEAD, null), decide(h))
    }

    @Test
    fun deadAfterCustomThreshold() {
        val h = dead(runs(goodRun), times = 1)
        assertEquals(Decision.Switch("fast", Reason.DEAD, null), decide(h, r = rules.copy(deadAfterFailures = 1)))
    }

    // ---------- Significantly better ----------

    @Test
    fun switchesWhenAllConditionsHold() {
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(threeGood))
    }

    @Test
    fun pinnedNeverSwitchesForSpeed() {
        assertEquals(Decision.Stay(StayReason.PINNED), decide(threeGood, fastest = false))
    }

    @Test
    fun needsConfiguredNumberOfRuns() {
        assertEquals(Decision.Stay(StayReason.NOT_ENOUGH_RUNS), decide(runs(goodRun, goodRun)))
        assertEquals(Decision.Stay(StayReason.NOT_ENOUGH_RUNS), decide(History()))
    }

    @Test
    fun currentAtOrBelowThresholdStays() {
        val run = mapOf("cur" to 150L, "fast" to 20L)
        assertEquals(Decision.Stay(StayReason.CURRENT_FAST_ENOUGH), decide(runs(run, run, run)))
    }

    @Test
    fun currentJustAboveThresholdCanSwitch() {
        val run = mapOf("cur" to 151L, "fast" to 60L)
        assertEquals(Decision.Switch("fast", Reason.FASTER, 91L), decide(runs(run, run, run)))
    }

    @Test
    fun healthRttOverridesRunValue() {
        // Runs say 300 ms but the live measurement says 140 ms: fast enough.
        assertEquals(Decision.Stay(StayReason.CURRENT_FAST_ENOUGH), decide(threeGood, rtt = 140L))
        // A non-positive live value falls back to the run.
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(threeGood, rtt = -1L))
    }

    @Test
    fun unknownCurrentRttStays() {
        val run = mapOf("fast" to 50L)
        assertEquals(Decision.Stay(StayReason.CURRENT_FAST_ENOUGH), decide(runs(run, run, run)))
    }

    @Test
    fun ratioBoundaryIsInclusive() {
        // 0.6 x 250 = 150: allowed; 151 is not.
        val ok = mapOf("cur" to 250L, "c" to 150L)
        assertEquals(Decision.Switch("c", Reason.FASTER, 100L), decide(runs(ok, ok, ok)))
        val over = mapOf("cur" to 250L, "c" to 151L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(over, over, over)))
    }

    @Test
    fun gainBoundaryIsInclusive() {
        // 200 -> 120: ratio 0.6 and gain exactly 80.
        val ok = mapOf("cur" to 200L, "c" to 120L)
        assertEquals(Decision.Switch("c", Reason.FASTER, 80L), decide(runs(ok, ok, ok)))
        // Ratio met but gain 79 with a looser ratio rule.
        val small = mapOf("cur" to 160L, "c" to 81L)
        assertEquals(
            Decision.Stay(StayReason.NO_BETTER),
            decide(runs(small, small, small), r = rules.copy(maxCandidatePercent = 95)),
        )
    }

    @Test
    fun candidateMustBeBetterInEveryConfirmRun() {
        val worse = mapOf("cur" to 300L, "fast" to 310L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(worse, goodRun, goodRun)))
        // Older runs beyond the window do not matter.
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(runs(worse, goodRun, goodRun, goodRun)))
    }

    @Test
    fun tieInOneConfirmRunIsNotBetter() {
        val tie = mapOf("cur" to 300L, "fast" to 300L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(tie, goodRun, goodRun)))
        // Current absent from a run: the candidate must beat the live RTT, a tie is not enough.
        val absentTie = mapOf("fast" to 300L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(absentTie, goodRun, goodRun), rtt = 300L))
    }

    @Test
    fun deadWithEmptyHistoryUsesCachedResults() {
        val history = dead(History())
        assertEquals(Decision.Stay(StayReason.NO_REACHABLE), decide(history))
        val cached = mapOf("cur" to 200L, "a" to 150L, "b" to -1L, "c" to 0L)
        assertEquals(
            Decision.Switch("a", Reason.DEAD, null),
            AutoSwitchEngine.decide(Inputs(history, "cur", now, rules, true, true, cachedResults = cached)),
        )
    }

    @Test
    fun cachedResultsIgnoredOnceARunExists() {
        val history = dead(runs(mapOf("cur" to -1L, "b" to 90L)))
        val cached = mapOf("a" to 10L)
        assertEquals(
            Decision.Switch("b", Reason.DEAD, null),
            AutoSwitchEngine.decide(Inputs(history, "cur", now, rules, true, true, cachedResults = cached)),
        )
    }

    @Test
    fun cachedResultsNeverDriveASpeedSwitch() {
        val cached = mapOf("cur" to 300L, "fast" to 100L)
        assertEquals(
            Decision.Stay(StayReason.NOT_ENOUGH_RUNS),
            AutoSwitchEngine.decide(Inputs(History(), "cur", now, rules, true, true, cachedResults = cached)),
        )
    }

    @Test
    fun candidateFailureInWindowBlocksSwitch() {
        val failed = mapOf("cur" to 300L, "fast" to -1L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(goodRun, failed, goodRun)))
        val missing = mapOf("cur" to 300L, "mid" to 250L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(goodRun, missing, goodRun)))
    }

    @Test
    fun currentFailedInRunCountsAsBeaten() {
        val curFailed = mapOf("cur" to -1L, "fast" to 100L)
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(runs(curFailed, goodRun, goodRun)))
    }

    @Test
    fun currentAbsentFromRunsUsesLiveRtt() {
        val run = mapOf("fast" to 100L)
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(runs(run, run, run), rtt = 300L))
        val slowRun = mapOf("fast" to 320L)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(runs(slowRun, run, run), rtt = 300L))
    }

    @Test
    fun picksLargestGainThenGuid() {
        val run = mapOf("cur" to 400L, "a" to 100L, "b" to 90L, "c" to 90L)
        assertEquals(Decision.Switch("b", Reason.FASTER, 310L), decide(runs(run, run, run)))
    }

    @Test
    fun confirmRunsIsConfigurable() {
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(runs(goodRun), r = rules.copy(confirmRuns = 1)))
    }

    // ---------- Gap and block ----------

    @Test
    fun speedSwitchGapIsTenMinutes() {
        val recent = threeGood.copy(lastSpeedSwitchAtMillis = now - 10 * minute + 1)
        assertEquals(Decision.Stay(StayReason.TOO_SOON), decide(recent))
        val elapsed = threeGood.copy(lastSpeedSwitchAtMillis = now - 10 * minute)
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(elapsed))
    }

    @Test
    fun blockedCandidateIsSkippedUntilExpiry() {
        val blocked = threeGood.copy(blockedUntilMillis = mapOf("fast" to now + 1))
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(blocked))
        assertEquals(Decision.Switch("fast", Reason.FASTER, 200L), decide(blocked, at = now + 1))
    }

    @Test
    fun recordSwitchBlocksLeftServerAndStartsGapOnlyForSpeed() {
        val h = AutoSwitchEngine.recordSwitch(dead(History()), "cur", Reason.FASTER, now, rules)
        assertEquals(now + 30 * minute, h.blockedUntilMillis["cur"])
        assertEquals(now, h.lastSpeedSwitchAtMillis)
        assertNull(h.failures)

        val d = AutoSwitchEngine.recordSwitch(History(lastSpeedSwitchAtMillis = 5L), "cur", Reason.DEAD, now, rules)
        assertEquals(5L, d.lastSpeedSwitchAtMillis)
        assertEquals(now + 30 * minute, d.blockedUntilMillis["cur"])
    }

    @Test
    fun recordSwitchDropsExpiredBlocksAndHonoursZeroBlock() {
        val h = History(blockedUntilMillis = mapOf("old" to now - 1, "live" to now + 1))
        val r = AutoSwitchEngine.recordSwitch(h, "cur", Reason.FASTER, now, rules.copy(blockLeftMinutes = 0))
        assertEquals(mapOf("live" to now + 1), r.blockedUntilMillis)
    }

    @Test
    fun switchedAwayServerIsNotChosenBackForSpeed() {
        // After leaving "fast" for "cur", "fast" looks better again but is blocked.
        val h = AutoSwitchEngine.recordSwitch(threeGood, "fast", Reason.DEAD, now, rules)
        assertEquals(Decision.Stay(StayReason.NO_BETTER), decide(h, at = now + 11 * minute))
    }

    // ---------- History ----------

    @Test
    fun recordRunDropsUntestedAndEmptyRuns() {
        val h = AutoSwitchEngine.recordRun(History(), mapOf("a" to 0L, "b" to 50L))
        assertEquals(listOf(mapOf("b" to 50L)), h.runs)
        assertEquals(h, AutoSwitchEngine.recordRun(h, mapOf("a" to 0L)))
        assertEquals(h, AutoSwitchEngine.recordRun(h, emptyMap()))
    }

    @Test
    fun recordRunKeepsOnlyNewest() {
        val h = (1..15).fold(History()) { acc, i -> AutoSwitchEngine.recordRun(acc, mapOf("a" to i.toLong())) }
        assertEquals(AutoSwitchEngine.MAX_RUNS_KEPT, h.runs.size)
        assertEquals(mapOf("a" to 15L), h.runs.last())
    }

    @Test
    fun healthIntervalShortWhileFailing() {
        assertEquals(120_000L, AutoSwitchEngine.nextHealthCheckDelayMillis(History(), "cur", rules))
        val failing = AutoSwitchEngine.recordHealth(History(), "cur", -1L)
        assertEquals(10_000L, AutoSwitchEngine.nextHealthCheckDelayMillis(failing, "cur", rules))
        assertEquals(120_000L, AutoSwitchEngine.nextHealthCheckDelayMillis(failing, "other", rules))
        assertEquals(120_000L, AutoSwitchEngine.nextHealthCheckDelayMillis(failing, null, rules))
    }
}

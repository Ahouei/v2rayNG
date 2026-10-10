package com.v2ray.ang.handler

import com.v2ray.ang.dto.AutoSwitchRules

/**
 * Pure decisions of Easy mode automatic server switching; no Android types so every rule is
 * covered by JVM tests. All times are one monotonic clock (SystemClock.elapsedRealtime ms).
 *
 * Delay convention (as stored by real-ping tests): `> 0` round-trip ms, `< 0` failed, `0` or
 * absent untested.
 */
object AutoSwitchEngine {
    /** Test runs kept in [History.runs]; the confirm-runs rule never needs more. */
    val MAX_RUNS_KEPT: Int get() = AutoSwitchRules.Field.CONFIRM_RUNS.max

    /** Consecutive failed health checks of one server. */
    data class FailureStreak(val guid: String, val count: Int)

    /** In-memory switching history owned by the main ViewModel. */
    data class History(
        /** Bulk test results, oldest first, each GUID -> delay; capped at [MAX_RUNS_KEPT]. */
        val runs: List<Map<String, Long>> = emptyList(),
        val failures: FailureStreak? = null,
        /** Time of the last speed switch, or null when none happened. */
        val lastSpeedSwitchAtMillis: Long? = null,
        /** GUID -> time until which it is excluded from speed switches. */
        val blockedUntilMillis: Map<String, Long> = emptyMap(),
    )

    enum class Reason { DEAD, FASTER }

    enum class StayReason {
        DISABLED,
        NO_CURRENT,
        /** Current server is dead but no other server is reachable in the latest results. */
        NO_REACHABLE,
        /** The user picked a specific server (Fastest off); only a dead server is switched. */
        PINNED,
        TOO_SOON,
        NOT_ENOUGH_RUNS,
        CURRENT_FAST_ENOUGH,
        NO_BETTER,
    }

    sealed interface Decision {
        data class Stay(val reason: StayReason) : Decision
        data class Switch(val targetGuid: String, val reason: Reason, val gainMillis: Long?) : Decision
    }

    data class Inputs(
        val history: History,
        val currentGuid: String?,
        val nowMillis: Long,
        val rules: AutoSwitchRules,
        /** Easy mode, connected, and "Switch automatically" on. */
        val enabled: Boolean,
        /** Easy "Fastest" on; off means the user pinned [currentGuid]. */
        val fastestMode: Boolean,
        /** Latest health-check RTT of [currentGuid] (`> 0`), or null to use the latest run. */
        val currentRttMillis: Long? = null,
        /**
         * Last stored results of the current group (GUID -> delay); used only for a dead-server
         * switch while [History.runs] is still empty (after connect or ViewModel recreation).
         */
        val cachedResults: Map<String, Long> = emptyMap(),
    )

    // ---------- History reducers ----------

    /** Appends one finished bulk test run, dropping untested entries and the oldest runs. */
    fun recordRun(history: History, results: Map<String, Long>): History {
        val tested = results.filterValues { it != 0L }
        if (tested.isEmpty()) return history
        return history.copy(runs = (history.runs + listOf(tested)).takeLast(MAX_RUNS_KEPT))
    }

    /** Counts a failed health check of [guid] or resets the streak on success. */
    fun recordHealth(history: History, guid: String, delayMillis: Long): History {
        val failures = when {
            delayMillis > 0L -> null
            delayMillis < 0L -> {
                val previous = history.failures?.takeIf { it.guid == guid }?.count ?: 0
                FailureStreak(guid, previous + 1)
            }
            else -> history.failures
        }
        return history.copy(failures = failures)
    }

    /**
     * Records a switch away from [fromGuid]: it is blocked for the configured time, the failure
     * streak is cleared and a speed switch starts the minimum gap. Expired blocks are dropped.
     */
    fun recordSwitch(history: History, fromGuid: String?, reason: Reason, nowMillis: Long, rules: AutoSwitchRules): History {
        val blocked = history.blockedUntilMillis.filterValues { it > nowMillis }.toMutableMap()
        if (fromGuid != null && rules.blockLeftMinutes > 0) {
            blocked[fromGuid] = nowMillis + rules.blockLeftMinutes * 60_000L
        }
        return history.copy(
            failures = null,
            blockedUntilMillis = blocked,
            lastSpeedSwitchAtMillis = if (reason == Reason.FASTER) nowMillis else history.lastSpeedSwitchAtMillis,
        )
    }

    /** Milliseconds until the next health check: short while the current server is failing. */
    fun nextHealthCheckDelayMillis(history: History, currentGuid: String?, rules: AutoSwitchRules): Long {
        val failing = currentGuid != null && history.failures?.let { it.guid == currentGuid && it.count > 0 } == true
        return (if (failing) rules.failingCheckSeconds else rules.healthCheckSeconds) * 1_000L
    }

    // ---------- Decision ----------

    fun decide(i: Inputs): Decision {
        if (!i.enabled) return Decision.Stay(StayReason.DISABLED)
        val current = i.currentGuid?.takeIf { it.isNotEmpty() } ?: return Decision.Stay(StayReason.NO_CURRENT)
        val history = i.history
        val failures = history.failures?.takeIf { it.guid == current }?.count ?: 0
        if (failures >= i.rules.deadAfterFailures) return decideDead(i, current)

        if (!i.fastestMode) return Decision.Stay(StayReason.PINNED)
        val last = history.lastSpeedSwitchAtMillis
        if (last != null && i.nowMillis - last < i.rules.minMinutesBetweenSwitches * 60_000L) {
            return Decision.Stay(StayReason.TOO_SOON)
        }
        return decideFaster(i, current)
    }

    private fun isBlocked(history: History, guid: String, nowMillis: Long): Boolean =
        (history.blockedUntilMillis[guid] ?: Long.MIN_VALUE) > nowMillis

    /**
     * Best reachable server in the latest run, or in [Inputs.cachedResults] before any run. Blocked servers are used only when no unblocked one
     * is reachable, because staying on a dead server is always worse.
     */
    private fun decideDead(i: Inputs, current: String): Decision {
        val latest = i.history.runs.lastOrNull() ?: i.cachedResults
        val reachable = latest.filter { (guid, delay) -> guid != current && delay > 0L }
        if (reachable.isEmpty()) return Decision.Stay(StayReason.NO_REACHABLE)
        val unblocked = reachable.filterKeys { !isBlocked(i.history, it, i.nowMillis) }
        val pool = unblocked.ifEmpty { reachable }
        val target = pool.entries.minWith(compareBy({ it.value }, { it.key })).key
        return Decision.Switch(target, Reason.DEAD, gainMillis = null)
    }

    private fun decideFaster(i: Inputs, current: String): Decision {
        val rules = i.rules
        val runs = i.history.runs.takeLast(rules.confirmRuns)
        if (runs.size < rules.confirmRuns) return Decision.Stay(StayReason.NOT_ENOUGH_RUNS)
        val latest = runs.last()
        val currentRtt = i.currentRttMillis?.takeIf { it > 0L }
            ?: latest[current]?.takeIf { it > 0L }
            ?: return Decision.Stay(StayReason.CURRENT_FAST_ENOUGH)
        if (currentRtt <= rules.minCurrentRttMillis) return Decision.Stay(StayReason.CURRENT_FAST_ENOUGH)

        var best: Pair<String, Long>? = null
        for ((guid, candidateRtt) in latest) {
            if (guid == current || candidateRtt <= 0L) continue
            if (isBlocked(i.history, guid, i.nowMillis)) continue
            if (candidateRtt * 100 > currentRtt * rules.maxCandidatePercent) continue
            val gain = currentRtt - candidateRtt
            if (gain < rules.minGainMillis) continue
            if (!runs.all { run -> betterInRun(run, guid, current, currentRtt) }) continue
            val b = best
            if (b == null || gain > b.second || (gain == b.second && guid < b.first)) best = guid to gain
        }
        val chosen = best ?: return Decision.Stay(StayReason.NO_BETTER)
        return Decision.Switch(chosen.first, Reason.FASTER, chosen.second)
    }

    /**
     * Candidate succeeded in [run] and beat the current server there: a failed current counts as
     * beaten; a current absent from the run is compared with [currentRtt].
     */
    private fun betterInRun(run: Map<String, Long>, candidate: String, current: String, currentRtt: Long): Boolean {
        val c = run[candidate] ?: return false
        if (c <= 0L) return false
        val cur = run[current] ?: 0L
        return when {
            cur < 0L -> true
            cur > 0L -> c < cur
            else -> c < currentRtt
        }
    }
}

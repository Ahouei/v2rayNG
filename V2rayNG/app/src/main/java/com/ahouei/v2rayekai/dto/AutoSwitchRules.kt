package com.ahouei.v2rayekai.dto

/**
 * Persisted thresholds of Easy mode automatic server switching. Every default and allowed range
 * lives here; [com.ahouei.v2rayekai.handler.SettingsManager] stores each field and
 * [com.ahouei.v2rayekai.handler.AutoSwitchEngine] reads only this value.
 */
data class AutoSwitchRules(
    /** Consecutive failed health checks of the current server that mark it dead. */
    val deadAfterFailures: Int = Field.DEAD_AFTER_FAILURES.default,
    /** Seconds between health checks while the current server is failing. */
    val failingCheckSeconds: Int = Field.FAILING_CHECK_SECONDS.default,
    /** Seconds between health checks while the current server is healthy. */
    val healthCheckSeconds: Int = Field.HEALTH_CHECK_SECONDS.default,
    /** A speed switch is considered only when the current RTT is above this. */
    val minCurrentRttMillis: Int = Field.MIN_CURRENT_RTT_MILLIS.default,
    /** Candidate RTT must be at most this percentage of the current RTT. */
    val maxCandidatePercent: Int = Field.MAX_CANDIDATE_PERCENT.default,
    /** Candidate must be at least this many ms faster. */
    val minGainMillis: Int = Field.MIN_GAIN_MILLIS.default,
    /** Candidate must have been better in this many consecutive test runs without failing. */
    val confirmRuns: Int = Field.CONFIRM_RUNS.default,
    /** Minimum minutes between two speed switches (dead-server switches are exempt). */
    val minMinutesBetweenSwitches: Int = Field.MIN_MINUTES_BETWEEN_SWITCHES.default,
    /** Minutes the server just left is excluded from speed switches. */
    val blockLeftMinutes: Int = Field.BLOCK_LEFT_MINUTES.default,
) {
    /** One editable rule with its default and inclusive range; [key] is the persisted MMKV key. */
    enum class Field(val key: String, val default: Int, val min: Int, val max: Int, val step: Int) {
        DEAD_AFTER_FAILURES("pref_auto_switch_dead_failures", 3, 1, 10, 1),
        FAILING_CHECK_SECONDS("pref_auto_switch_failing_check_seconds", 10, 5, 60, 5),
        HEALTH_CHECK_SECONDS("pref_auto_switch_health_check_seconds", 120, 30, 600, 30),
        MIN_CURRENT_RTT_MILLIS("pref_auto_switch_min_current_rtt", 150, 50, 1000, 10),
        MAX_CANDIDATE_PERCENT("pref_auto_switch_max_candidate_percent", 60, 10, 95, 5),
        MIN_GAIN_MILLIS("pref_auto_switch_min_gain", 80, 10, 1000, 10),
        CONFIRM_RUNS("pref_auto_switch_confirm_runs", 3, 1, 10, 1),
        MIN_MINUTES_BETWEEN_SWITCHES("pref_auto_switch_min_gap_minutes", 10, 1, 120, 1),
        BLOCK_LEFT_MINUTES("pref_auto_switch_block_minutes", 30, 0, 240, 5);

        /** Maps a stored or edited value into range so a bad write cannot disable a rule. */
        fun normalize(value: Int): Int = value.coerceIn(min, max)
    }

    operator fun get(field: Field): Int = when (field) {
        Field.DEAD_AFTER_FAILURES -> deadAfterFailures
        Field.FAILING_CHECK_SECONDS -> failingCheckSeconds
        Field.HEALTH_CHECK_SECONDS -> healthCheckSeconds
        Field.MIN_CURRENT_RTT_MILLIS -> minCurrentRttMillis
        Field.MAX_CANDIDATE_PERCENT -> maxCandidatePercent
        Field.MIN_GAIN_MILLIS -> minGainMillis
        Field.CONFIRM_RUNS -> confirmRuns
        Field.MIN_MINUTES_BETWEEN_SWITCHES -> minMinutesBetweenSwitches
        Field.BLOCK_LEFT_MINUTES -> blockLeftMinutes
    }

    /** Returns a copy with [field] set to [value] clamped into its range. */
    fun with(field: Field, value: Int): AutoSwitchRules {
        val v = field.normalize(value)
        return when (field) {
            Field.DEAD_AFTER_FAILURES -> copy(deadAfterFailures = v)
            Field.FAILING_CHECK_SECONDS -> copy(failingCheckSeconds = v)
            Field.HEALTH_CHECK_SECONDS -> copy(healthCheckSeconds = v)
            Field.MIN_CURRENT_RTT_MILLIS -> copy(minCurrentRttMillis = v)
            Field.MAX_CANDIDATE_PERCENT -> copy(maxCandidatePercent = v)
            Field.MIN_GAIN_MILLIS -> copy(minGainMillis = v)
            Field.CONFIRM_RUNS -> copy(confirmRuns = v)
            Field.MIN_MINUTES_BETWEEN_SWITCHES -> copy(minMinutesBetweenSwitches = v)
            Field.BLOCK_LEFT_MINUTES -> copy(blockLeftMinutes = v)
        }
    }

    companion object {
        /** Builds rules from raw stored values (missing fields use defaults), clamping each. */
        fun fromValues(read: (Field) -> Int): AutoSwitchRules =
            Field.entries.fold(AutoSwitchRules()) { rules, field -> rules.with(field, read(field)) }
    }
}

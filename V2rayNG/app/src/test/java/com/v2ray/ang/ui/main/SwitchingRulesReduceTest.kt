package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.AutoSwitchRules
import com.v2ray.ang.dto.AutoSwitchRules.Field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SwitchingRulesReduceTest {
    private fun reduce(rules: AutoSwitchRules, action: SwitchingRulesAction) =
        SwitchingRulesViewModel.reduce(rules, action)

    @Test
    fun increaseAndDecreaseStepEachField() {
        Field.entries.forEach { field ->
            val base = AutoSwitchRules()
            val up = reduce(base, SwitchingRulesAction.Increase(field))
            assertEquals(base[field] + field.step, up[field], field.name)
            val down = reduce(up, SwitchingRulesAction.Decrease(field))
            assertEquals(base, down, field.name)
        }
    }

    @Test
    fun valuesStayInRange() {
        Field.entries.forEach { field ->
            val atMax = AutoSwitchRules().with(field, field.max)
            assertEquals(atMax, reduce(atMax, SwitchingRulesAction.Increase(field)))
            val atMin = AutoSwitchRules().with(field, field.min)
            assertEquals(atMin, reduce(atMin, SwitchingRulesAction.Decrease(field)))
        }
    }

    @Test
    fun resetRestoresDefaults() {
        val edited = AutoSwitchRules(deadAfterFailures = 7, minGainMillis = 300)
        assertEquals(AutoSwitchRules(), reduce(edited, SwitchingRulesAction.ResetDefaults))
    }

    @Test
    fun fromValuesClampsAndDefaults() {
        val stored = mapOf(Field.CONFIRM_RUNS to 99, Field.MAX_CANDIDATE_PERCENT to -5)
        val rules = AutoSwitchRules.fromValues { stored[it] ?: it.default }
        assertEquals(Field.CONFIRM_RUNS.max, rules.confirmRuns)
        assertEquals(Field.MAX_CANDIDATE_PERCENT.min, rules.maxCandidatePercent)
        assertEquals(AutoSwitchRules().minGainMillis, rules.minGainMillis)
        assertEquals(AutoSwitchRules(), AutoSwitchRules.fromValues { it.default })
    }

    @Test
    fun defaultsAreInsideTheirRanges() {
        Field.entries.forEach { assertEquals(it.default, it.normalize(it.default), it.name) }
    }
}

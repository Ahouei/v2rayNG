package com.ahouei.v2rayekai.ui.main

import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahouei.v2rayekai.R
import com.ahouei.v2rayekai.dto.AutoSwitchRules
import com.ahouei.v2rayekai.dto.AutoSwitchRules.Field
import com.ahouei.v2rayekai.ui.base.BaseComponentActivity
import com.ahouei.v2rayekai.ui.compose.GlassBackground
import com.ahouei.v2rayekai.ui.compose.GlassSurface

/**
 * Advanced "Switching rules": edits the thresholds of Easy mode automatic switching. Opened from
 * Connection settings; MainActivity re-reads the rules when the settings flow returns.
 */
class SwitchingRulesActivity : BaseComponentActivity() {

    private val viewModel: SwitchingRulesViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        SwitchingRulesScreen(
            rules = state,
            onAction = viewModel::onAction,
            onBack = { finish() },
        )
    }
}

@Composable
internal fun SwitchingRulesScreen(
    rules: AutoSwitchRules,
    onAction: (SwitchingRulesAction) -> Unit,
    onBack: () -> Unit,
) {
    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.acc_back),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.switching_rules_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.switching_rules_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                RulesSection(
                    header = stringResource(R.string.switching_rules_section_dead),
                    fields = listOf(Field.DEAD_AFTER_FAILURES, Field.FAILING_CHECK_SECONDS, Field.HEALTH_CHECK_SECONDS),
                    rules = rules,
                    onAction = onAction,
                )
                RulesSection(
                    header = stringResource(R.string.switching_rules_section_faster),
                    fields = listOf(
                        Field.MIN_CURRENT_RTT_MILLIS,
                        Field.MAX_CANDIDATE_PERCENT,
                        Field.MIN_GAIN_MILLIS,
                        Field.CONFIRM_RUNS,
                        Field.MIN_MINUTES_BETWEEN_SWITCHES,
                        Field.BLOCK_LEFT_MINUTES,
                    ),
                    rules = rules,
                    onAction = onAction,
                )
                GlassSurface(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Text(
                        text = stringResource(R.string.switching_rules_reset),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onAction(SwitchingRulesAction.ResetDefaults) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RulesSection(
    header: String,
    fields: List<Field>,
    rules: AutoSwitchRules,
    onAction: (SwitchingRulesAction) -> Unit,
) {
    Text(
        text = header,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp)
            .semantics { heading() },
    )
    GlassSurface(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
        Column {
            fields.forEach { field ->
                RuleStepperRow(field = field, value = rules[field], onAction = onAction)
            }
        }
    }
}

@Composable
private fun ruleTitle(field: Field): String = stringResource(
    when (field) {
        Field.DEAD_AFTER_FAILURES -> R.string.switching_rules_dead_failures
        Field.FAILING_CHECK_SECONDS -> R.string.switching_rules_failing_check
        Field.HEALTH_CHECK_SECONDS -> R.string.switching_rules_health_check
        Field.MIN_CURRENT_RTT_MILLIS -> R.string.switching_rules_min_current_rtt
        Field.MAX_CANDIDATE_PERCENT -> R.string.switching_rules_max_percent
        Field.MIN_GAIN_MILLIS -> R.string.switching_rules_min_gain
        Field.CONFIRM_RUNS -> R.string.switching_rules_confirm_runs
        Field.MIN_MINUTES_BETWEEN_SWITCHES -> R.string.switching_rules_min_gap
        Field.BLOCK_LEFT_MINUTES -> R.string.switching_rules_block
    }
)

@Composable
private fun ruleValue(field: Field, value: Int): String = when (field) {
    Field.DEAD_AFTER_FAILURES, Field.CONFIRM_RUNS -> stringResource(R.string.switching_rules_value_count, value)
    Field.FAILING_CHECK_SECONDS, Field.HEALTH_CHECK_SECONDS -> stringResource(R.string.switching_rules_value_seconds, value)
    Field.MIN_CURRENT_RTT_MILLIS, Field.MIN_GAIN_MILLIS -> stringResource(R.string.switching_rules_value_millis, value)
    Field.MAX_CANDIDATE_PERCENT -> stringResource(R.string.switching_rules_value_percent, value)
    Field.MIN_MINUTES_BETWEEN_SWITCHES, Field.BLOCK_LEFT_MINUTES ->
        stringResource(R.string.switching_rules_value_minutes, value)
}

/** Label and value read as one node; the two step buttons are separate, named actions. */
@Composable
private fun RuleStepperRow(field: Field, value: Int, onAction: (SwitchingRulesAction) -> Unit) {
    val title = ruleTitle(field)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = ruleValue(field, value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onAction(SwitchingRulesAction.Decrease(field)) },
            enabled = value > field.min,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove_24dp),
                contentDescription = stringResource(R.string.switching_rules_decrease, title),
            )
        }
        IconButton(
            onClick = { onAction(SwitchingRulesAction.Increase(field)) },
            enabled = value < field.max,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.switching_rules_increase, title),
            )
        }
    }
}

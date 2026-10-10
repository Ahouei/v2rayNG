package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.AutoTestPolicy
import com.v2ray.ang.ui.compose.GlassShapePill
import com.v2ray.ang.ui.compose.GlassSurface
import com.v2ray.ang.ui.compose.GlassTokens
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.colorConnectedDark
import com.v2ray.ang.ui.compose.colorConnectedLight
import kotlinx.coroutines.launch

/**
 * Easy mode "Choose location" sheet. Rows, delays, ranking and the fastest choice come from
 * [state] (owned by MainViewModel); only the number of revealed rows is local UI state.
 */
// ModalBottomSheet and its state are @ExperimentalMaterial3Api in the pinned material3; opt-in is
// scoped to this function like ui/compose/Components.kt. Re-evaluate when the API becomes stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EasyLocationSheet(
    state: EasyLocationState,
    selectedGuid: String?,
    fastestMode: Boolean,
    isTesting: Boolean,
    testingText: String?,
    onAction: (MainAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var revealed by rememberSaveable {
        mutableIntStateOf(EasyLocationRanking.initialRevealed(state.rows, selectedGuid))
    }
    val fastestSelected = fastestMode
    val visible = EasyLocationRanking.visibleCount(revealed, state.rows.size)
    val choose: (MainAction) -> Unit = { action ->
        onAction(action)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val dark = LocalDarkTheme.current
    val glass = GlassTokens.palette(dark)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Translucent glass sheet; opaque enough (92%) that rows stay readable over any content.
        containerColor = glass.base.copy(alpha = 0.92f),
        // Not a colorScheme role, so contentColorFor() would be Unspecified (black text).
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.easy_location_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (isTesting) {
                GlassPill(
                    text = stringResource(R.string.easy_location_stop),
                    onClick = { onAction(MainAction.CancelTesting) },
                )
            } else {
                GlassPill(
                    text = stringResource(R.string.easy_location_test_again),
                    onClick = { onAction(MainAction.TestRealAllServers) },
                )
            }
        }
        val nextAt = state.nextAutoTestAtMillis
        if (!isTesting && nextAt != null) {
            NextCheckLabel(nextAtMillis = nextAt)
        }
        if (isTesting) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = testingText ?: stringResource(R.string.easy_location_testing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            item(key = "fastest") {
                EasyFastestRow(
                    selected = fastestSelected,
                    hasResults = state.fastestGuid != null,
                    isTesting = isTesting,
                    onClick = { choose(MainAction.SelectFastest) },
                )
            }
            items(state.rows.take(visible), key = { it.guid }) { row ->
                EasyLocationRowItem(
                    row = row,
                    selected = !fastestSelected && row.guid == selectedGuid,
                    onClick = { choose(MainAction.SelectEasyServer(row.guid)) },
                )
            }
            if (visible < state.rows.size) {
                item(key = "more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val more = EasyLocationRanking.nextPageSize(revealed, state.rows.size)
                        GlassPill(
                            text = pluralStringResource(R.plurals.easy_location_show_more, more, more),
                            onClick = { revealed = EasyLocationRanking.nextRevealed(revealed, state.rows.size) },
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EasyFastestRow(
    selected: Boolean,
    hasResults: Boolean,
    isTesting: Boolean,
    onClick: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val green = if (dark) colorConnectedDark else colorConnectedLight
    GlassSurface(
        tint = GlassTokens.greenTint(dark),
        edgeColor = green.copy(alpha = 0.6f),
        elevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        interaction = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.easy_location_fastest),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        when {
                            isTesting -> R.string.easy_location_testing
                            hasResults -> R.string.easy_location_recommended
                            else -> R.string.easy_location_no_results
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EasyLocationRowItem(
    row: EasyLocationRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val qualityText = stringResource(qualityLabel(row.quality))
    GlassSurface(
        // Lightweight list row: no shadow layer.
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        interaction = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = row.name.ifBlank { stringResource(R.string.easy_location_unnamed) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = qualityText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.quality == SignalQuality.UNREACHABLE) colors.error else colors.onSurfaceVariant,
                )
                if (row.delayMillis > 0L) {
                    Text(
                        text = stringResource(R.string.server_test_delay_value, row.delayMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            SignalBars(level = row.bars)
        }
    }
}

/** "Next check in N min"; the clock ticks only while the sheet is shown. */
@Composable
private fun NextCheckLabel(nextAtMillis: Long) {
    val now by produceState(SystemClock.elapsedRealtime(), nextAtMillis) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(30_000L)
        }
    }
    val minutes = AutoTestPolicy.minutesUntil(nextAtMillis, now).coerceAtLeast(1)
    Text(
        text = pluralStringResource(R.plurals.easy_location_next_check, minutes, minutes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
    )
}

/** Small glass action pill; at least 48dp tall. */
@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    GlassSurface(
        shape = GlassShapePill,
        elevation = 2.dp,
        modifier = Modifier
            .heightIn(min = 48.dp),
        interaction = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** Four bars of increasing height; decorative, the row text already states the quality. */
@Composable
internal fun SignalBars(level: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(18.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (index in 1..4) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((index * 4 + 2).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index <= level) colors.primary else colors.outlineVariant),
            )
        }
    }
}

internal fun qualityLabel(quality: SignalQuality): Int = when (quality) {
    SignalQuality.EXCELLENT -> R.string.easy_quality_excellent
    SignalQuality.GOOD -> R.string.easy_quality_good
    SignalQuality.FAIR -> R.string.easy_quality_fair
    SignalQuality.POOR -> R.string.easy_quality_poor
    SignalQuality.UNREACHABLE -> R.string.easy_quality_unreachable
    SignalQuality.UNTESTED -> R.string.easy_quality_untested
}

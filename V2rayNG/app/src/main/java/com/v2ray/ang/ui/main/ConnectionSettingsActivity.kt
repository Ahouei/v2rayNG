package com.v2ray.ang.ui.main

import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AutoTestSettings
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.GlassBackground
import com.v2ray.ang.ui.compose.GlassSurface
import com.v2ray.ang.ui.compose.GlassTokens
import com.v2ray.ang.ui.compose.LocalDarkTheme
import kotlinx.coroutines.launch

/**
 * Easy mode "Connection" settings: automatic test schedule and auto-switch options. Opened from
 * the Easy home gear; MainActivity re-reads the settings when this activity returns.
 */
class ConnectionSettingsActivity : BaseComponentActivity() {

    private val viewModel: ConnectionSettingsViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        ConnectionSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onBack = { finish() },
        )
    }
}

@Composable
internal fun ConnectionSettingsScreen(
    state: AutoTestSettings,
    onAction: (ConnectionSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    var showIntervalSheet by rememberSaveable { mutableStateOf(false) }
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
                        text = stringResource(R.string.connection_settings_title),
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
                SectionHeader(stringResource(R.string.connection_auto_test_header))
                GlassSurface(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Column {
                        GlassValueRow(
                            title = stringResource(R.string.connection_auto_test_interval),
                            value = intervalLabel(state.intervalMinutes),
                            onClick = { showIntervalSheet = true },
                        )
                        GlassSwitchRow(
                            title = stringResource(R.string.connection_wifi_only),
                            summary = stringResource(R.string.connection_wifi_only_summary),
                            checked = state.wifiOnly,
                            enabled = state.scheduleEnabled,
                            onCheckedChange = { onAction(ConnectionSettingsAction.SetWifiOnly(it)) },
                        )
                        GlassSwitchRow(
                            title = stringResource(R.string.connection_network_change),
                            summary = stringResource(R.string.connection_network_change_summary),
                            checked = state.testOnNetworkChange,
                            enabled = state.scheduleEnabled,
                            onCheckedChange = { onAction(ConnectionSettingsAction.SetTestOnNetworkChange(it)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.connection_auto_test_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                SectionHeader(stringResource(R.string.connection_auto_switch_header))
                GlassSurface(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Column {
                        GlassSwitchRow(
                            title = stringResource(R.string.connection_auto_switch),
                            summary = stringResource(R.string.connection_auto_switch_summary),
                            checked = state.autoSwitch,
                            onCheckedChange = { onAction(ConnectionSettingsAction.SetAutoSwitch(it)) },
                        )
                        GlassSwitchRow(
                            title = stringResource(R.string.connection_notify_switch),
                            summary = stringResource(R.string.connection_notify_switch_summary),
                            checked = state.notifyOnSwitch,
                            enabled = state.autoSwitch,
                            onCheckedChange = { onAction(ConnectionSettingsAction.SetNotifyOnSwitch(it)) },
                        )
                    }
                }
            }
        }
        if (showIntervalSheet) {
            IntervalSheet(
                selected = state.intervalMinutes,
                onSelect = { onAction(ConnectionSettingsAction.SetInterval(it)) },
                onDismiss = { showIntervalSheet = false },
            )
        }
    }
}

@Composable
internal fun intervalLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.connection_interval_off)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.connection_interval_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.connection_interval_minutes, minutes, minutes)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun GlassValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One focusable node: the row is the toggle; the Switch only draws state. */
@Composable
private fun GlassSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else 0.5f
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

// ModalBottomSheet and its state are @ExperimentalMaterial3Api in the pinned material3; opt-in is
// scoped to this function like EasyLocationSheet. Re-evaluate when the API becomes stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val glass = GlassTokens.palette(LocalDarkTheme.current)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glass.base.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.connection_interval_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.selectableGroup()) {
            AutoTestSettings.INTERVAL_OPTIONS.forEach { minutes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = minutes == selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(minutes)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            },
                        )
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = minutes == selected, onClick = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = intervalLabel(minutes), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

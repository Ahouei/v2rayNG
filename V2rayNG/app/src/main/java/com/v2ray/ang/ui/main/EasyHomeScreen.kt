package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.v2ray.ang.R
import com.v2ray.ang.dto.TrafficSpeed
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.colorConnectedDark
import com.v2ray.ang.ui.compose.colorConnectedLight
import com.v2ray.ang.ui.compose.onColorConnectedDark
import com.v2ray.ang.ui.compose.onColorConnectedLight

/**
 * Easy mode home: one connect button, the current server, and a small way into Pro mode.
 * Every operation is dispatched as a [MainAction]; this composable holds no durable state.
 */
@Composable
internal fun EasyHomeScreen(
    state: EasyHomeState,
    selectedGuid: String?,
    fastestMode: Boolean,
    locationState: EasyLocationState,
    isTesting: Boolean,
    testingText: String?,
    traffic: () -> TrafficSpeed?,
    selectedDelay: Long?,
    onAction: (MainAction) -> Unit,
) {
    var showLocations by rememberSaveable { mutableStateOf(false) }
    // Live traffic is sampled only while this screen is started; the ViewModel also requires a connection.
    LifecycleStartEffect(Unit) {
        onAction(MainAction.SetTrafficVisible(true))
        onStopOrDispose { onAction(MainAction.SetTrafficVisible(false)) }
    }
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(MainAction.SetEasyMode(false)) }) {
                    Text(
                        text = stringResource(R.string.easy_mode_switch_to_pro),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                EasyHomeState.NoServer -> EasyNoServer(onAction = onAction)
                is EasyHomeState.Disconnected,
                is EasyHomeState.Connected -> EasyConnect(
                    state = state,
                    autoFastest = fastestMode,
                    traffic = traffic,
                    selectedDelay = selectedDelay,
                    onOpenLocations = { showLocations = true },
                    onAction = onAction,
                )
            }
        }
    }
    if (showLocations && state != EasyHomeState.NoServer) {
        EasyLocationSheet(
            state = locationState,
            selectedGuid = selectedGuid,
            fastestMode = fastestMode,
            isTesting = isTesting,
            testingText = testingText,
            onAction = onAction,
            onDismiss = { showLocations = false },
        )
    }
}

@Composable
private fun ColumnScope.EasyConnect(
    state: EasyHomeState,
    autoFastest: Boolean,
    traffic: () -> TrafficSpeed?,
    selectedDelay: Long?,
    onOpenLocations: () -> Unit,
    onAction: (MainAction) -> Unit,
) {
    val connected = state is EasyHomeState.Connected
    val serverName = when (state) {
        is EasyHomeState.Connected -> state.serverName
        is EasyHomeState.Disconnected -> state.serverName
        EasyHomeState.NoServer -> ""
    }
    val colors = MaterialTheme.colorScheme
    val darkTheme = LocalDarkTheme.current
    val connectedColor = if (darkTheme) colorConnectedDark else colorConnectedLight
    val onConnectedColor = if (darkTheme) onColorConnectedDark else onColorConnectedLight
    val buttonColor by animateColorAsState(
        targetValue = if (connected) connectedColor else colors.surfaceVariant,
        label = "easyConnectColor",
    )
    val contentColor = if (connected) onConnectedColor else colors.onSurfaceVariant
    val buttonLabel = stringResource(
        if (connected) R.string.easy_mode_connected else R.string.easy_mode_tap_to_connect
    )
    val statusLabel = stringResource(
        if (connected) R.string.easy_mode_status_protected else R.string.easy_mode_status_not_protected
    )
    val openLabel = stringResource(R.string.easy_location_open)
    val actionLabel = stringResource(
        if (connected) R.string.easy_mode_action_disconnect else R.string.easy_mode_action_connect
    )

    Spacer(modifier = Modifier.weight(1f))
    Box(
        modifier = Modifier
            .size(220.dp)
            .border(BorderStroke(16.dp, buttonColor.copy(alpha = 0.25f)), CircleShape)
            .padding(16.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = { onAction(MainAction.ToggleService) },
            )
            .semantics {
                // One node: the action as its name, the protection status as its state; the
                // visible label below is hidden so TalkBack does not read it twice.
                contentDescription = actionLabel
                stateDescription = statusLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(if (connected) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buttonLabel,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
    Spacer(modifier = Modifier.height(40.dp))
    Text(
        text = statusLabel,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    if (connected) TrafficLine(traffic)
    Spacer(modifier = Modifier.weight(1f))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenLocations),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.easy_mode_current_server),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = serverName.ifBlank { stringResource(R.string.easy_location_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (autoFastest) {
                    Text(
                        text = stringResource(R.string.easy_location_fastest),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
            }
            if (selectedDelay != null) ServerLatency(selectedDelay)
            Text(
                text = openLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun speedText(bytesPerSec: Long): String {
    val value = TrafficSpeedFormat.format(bytesPerSec)
    val res = when (value.unit) {
        SpeedUnit.BYTES -> R.string.easy_speed_bytes
        SpeedUnit.KILOBYTES -> R.string.easy_speed_kilobytes
        SpeedUnit.MEGABYTES -> R.string.easy_speed_megabytes
        SpeedUnit.GIGABYTES -> R.string.easy_speed_gigabytes
    }
    return stringResource(res, value.number)
}

/** "↓ 4.2 MB/s  ↑ 380 KB/s"; read by TalkBack as words instead of arrows. */
@Composable
private fun TrafficLine(trafficProvider: () -> TrafficSpeed?) {
    val traffic = trafficProvider() ?: return
    Spacer(modifier = Modifier.height(8.dp))
    val down = speedText(traffic.downBytesPerSec)
    val up = speedText(traffic.upBytesPerSec)
    val description = stringResource(R.string.easy_traffic_description, down, up)
    Text(
        text = stringResource(R.string.easy_traffic_line, down, up),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** RTT plus signal bars of the selected server; failed last result shows "Not reachable". */
@Composable
private fun ServerLatency(delayMillis: Long) {
    val quality = EasyLocationRanking.quality(delayMillis)
    val text = if (delayMillis > 0L) {
        stringResource(R.string.server_test_delay_value, delayMillis)
    } else {
        stringResource(R.string.easy_quality_unreachable)
    }
    val description = if (delayMillis > 0L) {
        stringResource(R.string.easy_latency_quality_description, text, stringResource(qualityLabel(quality)))
    } else {
        stringResource(R.string.easy_latency_description, text)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (quality == SignalQuality.UNREACHABLE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignalBars(level = EasyLocationRanking.bars(quality))
    }
}

@Composable
private fun ColumnScope.EasyNoServer(
    onAction: (MainAction) -> Unit,
) {
    Spacer(modifier = Modifier.weight(1f))
    Text(
        text = stringResource(R.string.easy_mode_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.easy_mode_welcome_message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = { onAction(MainAction.ImportQRcode) },
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .height(56.dp),
    ) {
        Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.menu_item_import_config_qrcode))
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = { onAction(MainAction.ImportClipboard) },
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .height(56.dp),
    ) {
        Icon(painterResource(R.drawable.ic_copy), contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.menu_item_import_config_clipboard))
    }
    Spacer(modifier = Modifier.weight(1f))
}

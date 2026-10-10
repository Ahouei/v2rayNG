package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.handler.AutoSwitchEngine
import com.v2ray.ang.ui.base.ViewModelEvent

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    val easyMode: Boolean = true,
    val selectedServerName: String? = null,
    /** Persisted "Fastest" choice: when true the fastest tested server is selected automatically. */
    val fastestMode: Boolean = true,
    /** Live throughput of the running tunnel; null while disconnected or before the first sample. */
    /** Latest connected-server measurement, tied to the GUID it was taken for. */
    val currentServerDelay: ServerDelay? = null,
)

/** One delay measurement identified by server GUID: `< 0` failed, `> 0` round-trip ms. */
data class ServerDelay(val guid: String, val delayMillis: Long)

/** What the Easy mode home screen shows, derived from [MainUiState]. */
sealed interface EasyHomeState {
    /** No server is selected, so the user must add one before connecting. */
    data object NoServer : EasyHomeState
    data class Disconnected(val serverName: String) : EasyHomeState
    data class Connected(val serverName: String) : EasyHomeState
}

fun MainUiState.toEasyHomeState(): EasyHomeState {
    if (selectedGuid.isNullOrEmpty()) return EasyHomeState.NoServer
    val name = selectedServerName.orEmpty()
    return if (isRunning) EasyHomeState.Connected(name) else EasyHomeState.Disconnected(name)
}

/** Ranked rows and choice for the Easy mode "Choose location" sheet; identity is by GUID. */
internal data class EasyLocationState(
    val rows: List<EasyLocationRow> = emptyList(),
    val fastestGuid: String? = null,
    /** SystemClock.elapsedRealtime of the next scheduled automatic test, or null when none is scheduled. */
    val nextAutoTestAtMillis: Long? = null,
) {
    /** True only when the selected server is the current fastest one; derived, never persisted. */
    fun isFastestSelected(selectedGuid: String?): Boolean =
        fastestGuid != null && fastestGuid == selectedGuid
}

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction

    data class SetEasyMode(val enabled: Boolean) : MainAction

    /** Easy mode: turn on persisted auto-fastest mode; handled by the activity (may restart the service). */
    data object SelectFastest : MainAction

    /** Easy mode: pick a specific server and leave auto-fastest mode; handled by the activity. */
    data class SelectEasyServer(val guid: String) : MainAction

    /** Easy home became visible (started) or hidden; drives live traffic sampling in the daemon. */
    data class SetTrafficVisible(val visible: Boolean) : MainAction

    /** Easy mode: open the Connection settings screen; handled by the activity. */
    data object OpenConnectionSettings : MainAction

    /** Undo of an automatic switch: pins [previousGuid] (Fastest off); handled by the activity. */
    data class UndoAutoSwitch(val previousGuid: String) : MainAction

    /** The automatic-switch message was shown and dismissed without Undo. */
    data object DismissAutoSwitchNotice : MainAction
}

/** Message shown on Easy home after an automatic switch; [id] makes each switch a new message. */
data class AutoSwitchNotice(
    val id: Long,
    val fromGuid: String,
    val fromName: String,
    val toName: String,
    val reason: AutoSwitchEngine.Reason,
    /** Round-trip gain in ms for a speed switch; null for a dead-server switch. */
    val gainMillis: Long?,
)

/** One-shot MainViewModel -> MainActivity events. */
sealed interface MainViewModelEvent : ViewModelEvent {
    /** Auto-fastest mode picked [guid]; the activity re-checks fresh state before applying it. */
    data class AutoSelectServer(val guid: String) : MainViewModelEvent

    /**
     * Auto switch decided to leave [fromGuid] for [toGuid]; the activity re-checks fresh state
     * through [MainViewModel.consumeAutoSwitch] and applies it with the normal restart path.
     */
    data class AutoSwitchServer(
        val fromGuid: String,
        val toGuid: String,
        val reason: AutoSwitchEngine.Reason,
        val gainMillis: Long?,
    ) : MainViewModelEvent
}

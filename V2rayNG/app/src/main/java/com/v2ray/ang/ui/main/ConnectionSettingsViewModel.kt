package com.v2ray.ang.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AutoTestSettings
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** User intents of the Easy mode Connection settings screen. */
sealed interface ConnectionSettingsAction {
    data class SetInterval(val minutes: Int) : ConnectionSettingsAction
    data class SetWifiOnly(val enabled: Boolean) : ConnectionSettingsAction
    data class SetTestOnNetworkChange(val enabled: Boolean) : ConnectionSettingsAction
    data class SetAutoSwitch(val enabled: Boolean) : ConnectionSettingsAction
    data class SetNotifyOnSwitch(val enabled: Boolean) : ConnectionSettingsAction
}

/** Persistence port of [ConnectionSettingsViewModel]; the app binds it to [SettingsManager]. */
interface ConnectionSettingsStore {
    fun load(): AutoTestSettings
    fun save(settings: AutoTestSettings)
}

object SettingsManagerConnectionStore : ConnectionSettingsStore {
    override fun load(): AutoTestSettings = SettingsManager.getAutoTestSettings()

    override fun save(settings: AutoTestSettings) {
        SettingsManager.setAutoTestInterval(settings.intervalMinutes)
        SettingsManager.setAutoTestWifiOnly(settings.wifiOnly)
        SettingsManager.setAutoTestOnNetworkChange(settings.testOnNetworkChange)
        SettingsManager.setAutoSwitchEnabled(settings.autoSwitch)
        SettingsManager.setAutoSwitchNotify(settings.notifyOnSwitch)
    }
}

/**
 * Owns the Connection settings state. The state updates immediately; writes run on [ioDispatcher]
 * one after another so the last choice always wins.
 */
class ConnectionSettingsViewModel(
    private val store: ConnectionSettingsStore = SettingsManagerConnectionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : ViewModel() {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AutoTestSettings> = _state.asStateFlow()

    fun onAction(action: ConnectionSettingsAction) {
        val updated = reduce(_state.value, action)
        if (updated == _state.value) return
        _state.update { updated }
        viewModelScope.launch(ioDispatcher) {
            try {
                store.save(updated)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Connection settings: failed to save $action", e)
            }
        }
    }

    companion object {
        internal fun reduce(state: AutoTestSettings, action: ConnectionSettingsAction): AutoTestSettings =
            when (action) {
                is ConnectionSettingsAction.SetInterval ->
                    state.copy(intervalMinutes = AutoTestSettings.normalizeInterval(action.minutes))
                is ConnectionSettingsAction.SetWifiOnly -> state.copy(wifiOnly = action.enabled)
                is ConnectionSettingsAction.SetTestOnNetworkChange -> state.copy(testOnNetworkChange = action.enabled)
                is ConnectionSettingsAction.SetAutoSwitch -> state.copy(autoSwitch = action.enabled)
                is ConnectionSettingsAction.SetNotifyOnSwitch -> state.copy(notifyOnSwitch = action.enabled)
            }
    }
}

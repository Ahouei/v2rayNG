package com.ahouei.v2rayekai.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahouei.v2rayekai.AppConfig
import com.ahouei.v2rayekai.dto.AutoSwitchRules
import com.ahouei.v2rayekai.handler.SettingsManager
import com.ahouei.v2rayekai.util.LogUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** User intents of the Advanced "Switching rules" screen. */
sealed interface SwitchingRulesAction {
    data class Increase(val field: AutoSwitchRules.Field) : SwitchingRulesAction
    data class Decrease(val field: AutoSwitchRules.Field) : SwitchingRulesAction
    data object ResetDefaults : SwitchingRulesAction
}

/** Persistence port of [SwitchingRulesViewModel]; the app binds it to [SettingsManager]. */
interface SwitchingRulesStore {
    fun load(): AutoSwitchRules
    fun save(rules: AutoSwitchRules)
}

object SettingsManagerSwitchingRulesStore : SwitchingRulesStore {
    override fun load(): AutoSwitchRules = SettingsManager.getAutoSwitchRules()
    override fun save(rules: AutoSwitchRules) = SettingsManager.setAutoSwitchRules(rules)
}

/**
 * Owns the switching-rules state. The state updates immediately; writes run on [ioDispatcher]
 * one after another so the last edit always wins. MainActivity re-reads the rules on return.
 */
class SwitchingRulesViewModel(
    private val store: SwitchingRulesStore = SettingsManagerSwitchingRulesStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : ViewModel() {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AutoSwitchRules> = _state.asStateFlow()

    fun onAction(action: SwitchingRulesAction) {
        val updated = reduce(_state.value, action)
        if (updated == _state.value) return
        _state.value = updated
        viewModelScope.launch(ioDispatcher) {
            try {
                store.save(updated)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Switching rules: failed to save $action", e)
            }
        }
    }

    companion object {
        internal fun reduce(rules: AutoSwitchRules, action: SwitchingRulesAction): AutoSwitchRules =
            when (action) {
                is SwitchingRulesAction.Increase -> rules.with(action.field, rules[action.field] + action.field.step)
                is SwitchingRulesAction.Decrease -> rules.with(action.field, rules[action.field] - action.field.step)
                SwitchingRulesAction.ResetDefaults -> AutoSwitchRules()
            }
    }
}

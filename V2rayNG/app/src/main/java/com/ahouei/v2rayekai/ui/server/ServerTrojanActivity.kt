package com.ahouei.v2rayekai.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.ahouei.v2rayekai.R
import com.ahouei.v2rayekai.dto.entities.ProfileItem
import com.ahouei.v2rayekai.enums.EConfigType
import com.ahouei.v2rayekai.ui.compose.FormTextField

class ServerTrojanActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.TROJAN

    @Composable
    override fun ScreenContent() {
        val options = rememberFieldOptions()
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.TROJAN
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            TrojanProtocolFields(uiState)
            CommonNetworkFields(uiState, options)
            CommonStreamSecurityFields(
                state = uiState,
                options = options,
                scope = scope,
                buildProfileItem = { uiState.toProfileItem(initialConfig) }
            )
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            return false
        }
        if (config.security.isNullOrBlank()) {
            return false
        }
        return true
    }

    @Composable
    private fun TrojanProtocolFields(state: ServerUiState) {
        FormTextField(
            label = stringResource(R.string.server_lab_id3),
            value = state.password,
            onValueChange = { state.password = it },
            isError = state.isPasswordError
        )
    }
}


package com.ahouei.v2rayekai.ui.main

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahouei.v2rayekai.AngApplication
import com.ahouei.v2rayekai.AppConfig
import com.ahouei.v2rayekai.R
import com.ahouei.v2rayekai.core.LauncherManager
import com.ahouei.v2rayekai.dto.entities.ProfileItem
import com.ahouei.v2rayekai.enums.EConfigType
import com.ahouei.v2rayekai.enums.PermissionType
import com.ahouei.v2rayekai.extension.toast
import com.ahouei.v2rayekai.extension.toastError
import com.ahouei.v2rayekai.extension.toastSuccess
import com.ahouei.v2rayekai.handler.AngConfigManager
import com.ahouei.v2rayekai.handler.MmkvManager
import com.ahouei.v2rayekai.handler.SettingsChangeManager
import com.ahouei.v2rayekai.handler.SettingsManager
import com.ahouei.v2rayekai.ui.AboutActivity
import com.ahouei.v2rayekai.ui.backup.BackupActivity
import com.ahouei.v2rayekai.ui.base.HelperBaseComponentActivity
import com.ahouei.v2rayekai.ui.checkupdate.CheckUpdateActivity
import com.ahouei.v2rayekai.ui.logcat.LogcatActivity
import com.ahouei.v2rayekai.ui.perappproxy.PerAppProxyActivity
import com.ahouei.v2rayekai.ui.routing.RoutingSettingActivity
import com.ahouei.v2rayekai.ui.server.ProfileEditorResult
import com.ahouei.v2rayekai.ui.server.ServerCustomConfigActivity
import com.ahouei.v2rayekai.ui.server.ServerGroupActivity
import com.ahouei.v2rayekai.ui.server.ServerHttpActivity
import com.ahouei.v2rayekai.ui.server.ServerHysteria2Activity
import com.ahouei.v2rayekai.ui.server.ServerProxyChainActivity
import com.ahouei.v2rayekai.ui.server.ServerShadowsocksActivity
import com.ahouei.v2rayekai.ui.server.ServerSocksActivity
import com.ahouei.v2rayekai.ui.server.ServerTrojanActivity
import com.ahouei.v2rayekai.ui.server.ServerVlessActivity
import com.ahouei.v2rayekai.ui.server.ServerVmessActivity
import com.ahouei.v2rayekai.ui.server.ServerWireguardActivity
import com.ahouei.v2rayekai.ui.settings.SettingsActivity
import com.ahouei.v2rayekai.ui.subscription.SubSettingActivity
import com.ahouei.v2rayekai.ui.userasset.UserAssetActivity
import com.ahouei.v2rayekai.util.LogUtil
import com.ahouei.v2rayekai.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    data.getStringExtra(ProfileEditorResult.EXTRA_GUID) == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        observeViewModelEvents()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        MainScreen(
            mainViewModel = mainViewModel,
            onAction = { action ->
                when (action) {
                    MainAction.ToggleService -> handleFabAction()
                    MainAction.TestCurrentServer -> handleLayoutTestClick()
                    MainAction.ImportQRcode -> importQRcode()
                    MainAction.ImportClipboard -> importClipboard()
                    MainAction.ImportClipboardSuggestion -> {
                        mainViewModel.setClipboardLinkFound(false)
                        importClipboard()
                    }
                    MainAction.ImportConfigLocal -> importConfigLocal()
                    is MainAction.ImportManually -> importManually(action.type)
                    MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
                    MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
                    is MainAction.SelectServer -> selectServerManually(action.guid)
                    MainAction.SelectFastest -> mainViewModel.setFastestMode(true)
                    is MainAction.SelectEasyServer -> selectServerManually(action.guid)
                    is MainAction.UndoAutoSwitch -> {
                        mainViewModel.dismissAutoSwitchNotice()
                        selectServerManually(action.previousGuid)
                    }
                    MainAction.OpenConnectionSettings ->
                        settingsActivityLauncher.launch(Intent(this, ConnectionSettingsActivity::class.java))
                    is MainAction.EditServer -> editServer(action.guid, action.profile)
                    is MainAction.ShareClipboard -> shareToClipboard(action.guid)
                    is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
                    else -> mainViewModel.onAction(action)
                }
            },
            onNavigate = { route -> navigateTo(route) },
        )
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.Subscriptions -> Intent(this, SubSettingActivity::class.java)
            MainDestination.PerAppProxy -> Intent(this, PerAppProxyActivity::class.java)
            MainDestination.Routing -> Intent(this, RoutingSettingActivity::class.java)
            MainDestination.UserAssets -> Intent(this, UserAssetActivity::class.java)
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.Logcat -> Intent(this, LogcatActivity::class.java)
            MainDestination.CheckUpdate -> Intent(this, CheckUpdateActivity::class.java)
            MainDestination.BackupRestore -> Intent(this, BackupActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
            MainDestination.Promotion -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            requestServiceStart()
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private var clipboardCheckJob: Job? = null

    /** Identity of the clip last checked, so an unchanged clip is not re-read on every focus gain. */
    private var lastCheckedClipKey: Long? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ returns clipboard data only to the focused app, so check on focus gain only.
        if (hasFocus) checkClipboardForLink()
    }

    /**
     * Easy first launch: when no server exists, offers to import a link found on the clipboard.
     * Only the importable/not verdict reaches the ViewModel; the text is never stored or logged.
     * The clip content is read only when its description identity changed (reading the description
     * does not trigger the Android 12+ paste toast).
     */
    private fun checkClipboardForLink() {
        val state = mainViewModel.uiState.value
        if (!state.easyMode || state.toEasyHomeState() != EasyHomeState.NoServer) {
            lastCheckedClipKey = null
            mainViewModel.setClipboardLinkFound(false)
            return
        }
        val clip = try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val description = clipboard?.primaryClipDescription
            if (description == null || !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
            ) {
                lastCheckedClipKey = null
                null
            } else {
                val key = clipIdentity(description)
                if (key != null && key == lastCheckedClipKey) return
                lastCheckedClipKey = key
                clipboard.primaryClip
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Easy first launch: failed to read clipboard for link check", e)
            null
        }
        clipboardCheckJob?.cancel()
        clipboardCheckJob = lifecycleScope.launch {
            val found = withContext(Dispatchers.Default) {
                val text = try {
                    clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Easy first launch: failed to read clipboard item for link check", e)
                    null
                }
                ClipboardLinkDetector.looksImportable(text)
            }
            mainViewModel.setClipboardLinkFound(found)
        }
    }

    /**
     * Stable identity of the current clip from its description. ClipDescription.getTimestamp exists on
     * API 26+; below that there is no paste toast (added in Android 12), so returning null re-reads.
     */
    private fun clipIdentity(description: ClipDescription): Long? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) description.timestamp else null

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    mainViewModel.onAction(MainAction.ImportBatchConfig(reader.readText()))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.viewModelEvent.collect { event ->
                    when (event) {
                        // Re-resolved from fresh state so a manual pick made after emission wins.
                        is MainViewModelEvent.AutoSelectServer ->
                            if (mainViewModel.isAutoSelectCurrent(event.guid)) setSelectServer(event.guid)
                        // Same restart path as a manual pick; dropped when the selection changed meanwhile.
                        is MainViewModelEvent.AutoSwitchServer ->
                            if (mainViewModel.consumeAutoSwitch(event)) {
                                setSelectServer(event.toGuid)
                                mainViewModel.onAutoSwitchApplied(event)
                            }
                        else -> Unit
                    }
                }
            }
        }
    }

    /** A user-chosen server leaves the persisted auto-fastest mode. */
    private fun selectServerManually(guid: String) {
        if (mainViewModel.uiState.value.fastestMode) mainViewModel.setFastestMode(false)
        setSelectServer(guid)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

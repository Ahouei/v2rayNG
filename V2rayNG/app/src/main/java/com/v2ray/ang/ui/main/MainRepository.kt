package com.v2ray.ang.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AutoSwitchRules
import com.v2ray.ang.dto.AutoTestNetwork
import com.v2ray.ang.dto.AutoTestSettings
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.TrafficSpeed
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean

class MainRepository(
    private val app: AngApplication
) : MainDataSource {

    private val localizedContext: Context
        get() = AppLocaleManager.localizedContext(app)

    private val closed = AtomicBoolean(false)

    // Probe results are finite and must remain lossless until the ViewModel coalesces them.
    private val mainServiceEventChannel = Channel<MainServiceEvent>(Channel.UNLIMITED)

    override val mainServiceEvent: Flow<MainServiceEvent> = mainServiceEventChannel.receiveAsFlow()

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            val event = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess
                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure(
                    safeIntent.getStringExtra("content")
                )

                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
                AppConfig.MSG_MEASURE_DELAY_RESULT -> safeIntent
                    .serializable<ConnectionTestResult>("content")
                    ?.let { MainServiceEvent.MeasureDelayResult(it, requestId) }
                AppConfig.MSG_MEASURE_DELAY_CANCEL -> MainServiceEvent.MeasureDelayCancelled(requestId)

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> safeIntent
                    .serializable<RealPingResult>("content")
                    ?.let { MainServiceEvent.MeasureConfigSuccess(it, requestId) }
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(
                    safeIntent.getStringExtra("content").orEmpty(), requestId
                )

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    requestId
                )
                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> MainServiceEvent.MeasureConfigCancelled(requestId)
                AppConfig.MSG_TRAFFIC_STATS -> safeIntent
                    .serializable<TrafficSpeed>("content")
                    ?.let { MainServiceEvent.TrafficStats(it.downBytesPerSec, it.upBytesPerSec) }

                else -> null
            }
            event?.let { mainServiceEventChannel.trySend(it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            MessageHelper.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "")
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to unregister service client", it)
        }
        runCatching {
            app.unregisterReceiver(serviceReceiver)
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it)
        }
        mainServiceEventChannel.close()
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun setSelectedSubscriptionId(id: String) {
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, id)
    }

    override fun getSelectServer(): String? = MmkvManager.getSelectServer()

    override fun setSelectServer(guid: String) = MmkvManager.setSelectServer(guid)

    override fun getConfirmRemove(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    override fun getDoubleColumnDisplay(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)

    override fun getEasyMode(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_EASY_MODE, true)

    override fun setEasyMode(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_EASY_MODE, enabled)
    }

    /** Easy mode "Fastest" choice; defaults to on so new users get the fastest tested server. */
    override fun getFastestMode(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_EASY_FASTEST_MODE, true)

    override fun setFastestMode(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_EASY_FASTEST_MODE, enabled)
    }

    override fun isGroupAllDisplayEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)

    override fun getAutoTestSettings(): AutoTestSettings = SettingsManager.getAutoTestSettings()

    override fun getAutoSwitchRules(): AutoSwitchRules = SettingsManager.getAutoSwitchRules()

    override fun isPowerSaveMode(): Boolean =
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

    /**
     * Tracks networks through a request that keeps the default NOT_VPN capability, so the app's
     * own tunnel is never reported as the device network. Wi-Fi wins when both are up.
     */
    override fun physicalNetwork(): Flow<AutoTestNetwork> = callbackFlow {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(AutoTestNetwork.OTHER)
            awaitClose { }
            return@callbackFlow
        }
        val networks = HashMap<Network, AutoTestNetwork>()
        fun publish() {
            val types = synchronized(networks) { networks.values.toSet() }
            trySend(
                when {
                    AutoTestNetwork.WIFI in types -> AutoTestNetwork.WIFI
                    AutoTestNetwork.CELLULAR in types -> AutoTestNetwork.CELLULAR
                    types.isNotEmpty() -> AutoTestNetwork.OTHER
                    else -> AutoTestNetwork.NONE
                }
            )
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> AutoTestNetwork.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> AutoTestNetwork.CELLULAR
                    else -> AutoTestNetwork.OTHER
                }
                synchronized(networks) { networks[network] = type }
                publish()
            }

            override fun onLost(network: Network) {
                synchronized(networks) { networks.remove(network) }
                publish()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            // Existing networks are delivered through the callback right after registration.
            cm.registerNetworkCallback(request, callback)
        } catch (e: RuntimeException) {
            // SecurityException or TooManyRequestsException: treat the network as unknown.
            LogUtil.e(AppConfig.TAG, "Auto test: failed to register network callback", e)
            trySend(AutoTestNetwork.OTHER)
            awaitClose { }
            return@callbackFlow
        }
        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: IllegalArgumentException) {
                LogUtil.e(AppConfig.TAG, "Auto test: network callback already unregistered", e)
            }
        }
    }.conflate().distinctUntilChanged()

    override fun getString(resId: Int): String = localizedContext.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        localizedContext.getString(resId, *formatArgs)

    override fun getSubscriptions(): List<SubscriptionCache> {
        val result = mutableListOf<SubscriptionCache>()
        if (isGroupAllDisplayEnabled()) {
            result += SubscriptionCache(
                guid = "",
                subscription = SubscriptionItem().apply {
                    remarks = localizedContext.getString(R.string.filter_config_all)
                }
            )
        }
        result += MmkvManager.decodeSubscriptions()
        return result
    }

    override fun getSubscriptionItem(id: String): SubscriptionItem? =
        MmkvManager.decodeSubscription(id)

    override fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }

    override fun decodeServerConfig(guid: String): ProfileItem? =
        MmkvManager.decodeServerConfig(guid)

    override fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo? =
        MmkvManager.decodeServerAffiliationInfo(guid)

    override fun encodeServerList(guids: List<String>, groupId: String) =
        MmkvManager.encodeServerList(ArrayList(guids), groupId)

    override fun removeServer(guid: String) = MmkvManager.removeServer(guid)

    override fun removeAllServer(): Int = MmkvManager.removeAllServer()

    override fun removeInvalidServerByGuid(guid: String): Int =
        MmkvManager.removeInvalidServer(guid)

    override fun removeInvalidServersInGroup(groupId: String): Int =
        if (groupId.isEmpty()) {
            MmkvManager.removeInvalidServer("")
        } else {
            getServerGuidList(groupId).sumOf(::removeInvalidServerByGuid)
        }

    override fun clearAllTestDelayResults(guids: List<String>) =
        MmkvManager.clearAllTestDelayResults(guids)

    override fun sortByTestResultsForSub(subId: String) {
        AngConfigManager.sortByTestResultsForSub(subId)
    }

    override fun getSubsList(): List<String> = MmkvManager.decodeSubsList()

    override suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int> = AngConfigManager.importBatchConfig(server, subscriptionId, updateUI)

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun share2QRCode(guid: String): android.graphics.Bitmap? =
        AngConfigManager.share2QRCode(guid)

    override fun share2Clipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(app, guid) == 0

    override fun sendMsg2Service(msgId: Int, content: String) =
        MessageHelper.sendMsg2Service(app, msgId, content)

    override fun sendMsg2TestService(msg: TestServiceMessage, requestId: String?) =
        MessageHelper.sendMsg2TestService(app, msg, requestId)

    override fun cancelAllPing() {
        sendMsg2TestService(
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
    }

    override fun testCurrentServerRealPing(requestId: String) {
        MessageHelper.sendMsg2ServiceForResult(app, AppConfig.MSG_MEASURE_DELAY, requestId) { handled ->
            if (!handled) mainServiceEventChannel.trySend(MainServiceEvent.MeasureDelayCancelled(requestId))
        }
    }

    override fun setTrafficStatsEnabled(enabled: Boolean) {
        MessageHelper.sendMsg2Service(
            app,
            if (enabled) AppConfig.MSG_TRAFFIC_STATS_START else AppConfig.MSG_TRAFFIC_STATS_STOP,
            "",
        )
    }

    override fun syncSubscriptions() {
        SubscriptionUpdater.sync(app)
    }

    override fun initAssets() {
        SettingsManager.initAssets(app, app.assets)
    }
}

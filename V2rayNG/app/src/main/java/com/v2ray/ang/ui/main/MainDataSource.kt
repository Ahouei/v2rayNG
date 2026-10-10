package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.AutoSwitchRules
import com.v2ray.ang.dto.AutoTestNetwork
import com.v2ray.ang.dto.AutoTestSettings
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    fun setSelectServer(guid: String)

    fun getConfirmRemove(): Boolean
    fun getDoubleColumnDisplay(): Boolean
    fun getEasyMode(): Boolean
    fun setEasyMode(enabled: Boolean)
    fun getFastestMode(): Boolean
    fun setFastestMode(enabled: Boolean)
    fun isGroupAllDisplayEnabled(): Boolean
    fun getAutoTestSettings(): AutoTestSettings
    fun getAutoSwitchRules(): AutoSwitchRules

    /** True while Android battery saver is on; scheduled tests are skipped then. */
    fun isPowerSaveMode(): Boolean

    /** Physical (non-VPN) network type; emits the current value on collection and on each change. */
    fun physicalNetwork(): Flow<AutoTestNetwork>

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?

    fun encodeServerList(guids: List<String>, groupId: String)

    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun removeInvalidServersInGroup(groupId: String): Int

    fun clearAllTestDelayResults(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)
    fun getSubsList(): List<String>

    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int>

    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult

    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun share2QRCode(guid: String): android.graphics.Bitmap?
    fun share2Clipboard(guid: String): Boolean

    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage, requestId: String? = null)
    fun cancelAllPing()
    fun testCurrentServerRealPing(requestId: String)

    /** Asks the running daemon to start or stop sending live traffic samples. */
    fun setTrafficStatsEnabled(enabled: Boolean)

    fun syncSubscriptions()
    fun initAssets()
}

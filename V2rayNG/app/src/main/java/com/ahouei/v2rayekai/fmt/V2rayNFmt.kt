package com.ahouei.v2rayekai.fmt

import com.ahouei.v2rayekai.AppConfig
import com.ahouei.v2rayekai.dto.V2rayNShareItem
import com.ahouei.v2rayekai.dto.entities.ProfileItem
import com.ahouei.v2rayekai.extension.isNotNullEmpty
import com.ahouei.v2rayekai.util.JsonUtil
import com.ahouei.v2rayekai.util.LogUtil
import com.ahouei.v2rayekai.util.Utils

object V2rayNFmt : FmtBase() {
    fun parse(list: List<String>, subId: String): List<ProfileItem> {
        val idMap = mutableMapOf<String, V2rayNShareItem>()
        list.mapNotNull(::parseShareItem).forEach { item ->
            idMap.putIfAbsent(item.IndexId.orEmpty(), item)
        }

        return idMap.values.map { item ->
            item.toProfileItem().apply {
                val proto = item.ProtoExtraObj
                policyGroupSubscriptionId = if (proto?.SubChildItems == "self") {
                    subId
                } else {
                    null
                }
                proto?.ChildItems?.takeIf { it.isNotNullEmpty() }?.let { ids ->
                    val remarks = ids.split(",")
                        .mapNotNull { idMap[it]?.Remarks }
                        .filter { it.isNotNullEmpty() }

                    if (remarks.isNotEmpty()) {
                        when (item.ConfigType) {
                            101 -> policyGroupFilter = remarks.joinToString("|", "^(", ")$") { Regex.escape(it) }
                            102 -> proxyChainProfiles = remarks.joinToString(",")
                        }
                    }
                }
            }
        }
    }

    private fun parseShareItem(str: String): V2rayNShareItem? = try {
        JsonUtil.fromJson(Utils.decode(str.substringAfterLast('/')), V2rayNShareItem::class.java)
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN share item", e)
        null
    }
}
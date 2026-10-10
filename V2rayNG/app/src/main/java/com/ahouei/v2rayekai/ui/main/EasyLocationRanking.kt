package com.ahouei.v2rayekai.ui.main

import com.ahouei.v2rayekai.dto.entities.ServersCache

/** Connection quality of one server, derived from its last real-ping delay. */
internal enum class SignalQuality { EXCELLENT, GOOD, FAIR, POOR, UNREACHABLE, UNTESTED }

/** One ranked row of the Easy mode "Choose location" sheet, identified by [guid]. */
internal data class EasyLocationRow(
    val guid: String,
    val name: String,
    /** Raw delay: `0` untested, `< 0` failed, `> 0` round-trip time in ms. */
    val delayMillis: Long,
    val quality: SignalQuality,
    val bars: Int,
)

/** Pure ranking and quality rules for the Easy mode location picker. */
internal object EasyLocationRanking {
    const val MAX_RANKED = 30

    /**
     * Delay to show for the selected server: the measurement taken after connecting wins when it
     * belongs to [selectedGuid], otherwise the latest stored test result. Null when unknown.
     */
    fun selectedDelay(selectedGuid: String?, rows: List<EasyLocationRow>, measured: ServerDelay?): Long? {
        if (selectedGuid.isNullOrEmpty()) return null
        val delay = measured?.takeIf { it.guid == selectedGuid }?.delayMillis
            ?: rows.firstOrNull { it.guid == selectedGuid }?.delayMillis
        return delay?.takeIf { it != 0L }
    }
    const val PAGE_SIZE = 10

    fun quality(delayMillis: Long): SignalQuality = when {
        delayMillis == 0L -> SignalQuality.UNTESTED
        delayMillis < 0L -> SignalQuality.UNREACHABLE
        delayMillis <= 100L -> SignalQuality.EXCELLENT
        delayMillis <= 200L -> SignalQuality.GOOD
        delayMillis <= 400L -> SignalQuality.FAIR
        else -> SignalQuality.POOR
    }

    fun bars(quality: SignalQuality): Int = when (quality) {
        SignalQuality.EXCELLENT -> 4
        SignalQuality.GOOD -> 3
        SignalQuality.FAIR -> 2
        SignalQuality.POOR -> 1
        SignalQuality.UNREACHABLE, SignalQuality.UNTESTED -> 0
    }

    private fun bucket(delayMillis: Long): Int = when {
        delayMillis > 0L -> 0
        delayMillis == 0L -> 1
        else -> 2
    }

    /**
     * Successful servers by ascending delay (ties by GUID), then untested, then unreachable,
     * each of the latter two keeping input order; capped at [MAX_RANKED]. When [selectedGuid]
     * belongs to the group but falls outside the cap, it is appended so it stays visible.
     */
    fun rank(servers: List<ServersCache>, selectedGuid: String? = null): List<EasyLocationRow> {
        val sorted = servers
            .sortedWith(
                compareBy<ServersCache> { bucket(it.testDelayMillis) }
                    .thenComparator { a, b ->
                        if (a.testDelayMillis > 0L && b.testDelayMillis > 0L) {
                            compareValuesBy(a, b, { it.testDelayMillis }, { it.guid })
                        } else {
                            0
                        }
                    }
            )
        val top = sorted.take(MAX_RANKED)
        val pinned = if (selectedGuid != null && top.none { it.guid == selectedGuid }) {
            sorted.firstOrNull { it.guid == selectedGuid }?.let { top + it } ?: top
        } else {
            top
        }
        return pinned.map { server ->
                val quality = quality(server.testDelayMillis)
                EasyLocationRow(
                    guid = server.guid,
                    name = server.profile.remarks,
                    delayMillis = server.testDelayMillis,
                    quality = quality,
                    bars = bars(quality),
                )
            }
    }

    /** GUID of the server with the lowest successful delay (ties by GUID), or null. */
    fun fastestGuid(servers: List<ServersCache>): String? =
        servers.asSequence()
            .filter { it.testDelayMillis > 0L }
            .minWithOrNull(compareBy<ServersCache>({ it.testDelayMillis }, { it.guid }))
            ?.guid

    /**
     * GUID the auto-fastest mode should switch to, or null to keep the current selection: null when
     * Easy mode or the fastest mode is off, a test is still running, the current selection belongs to
     * another group (browsing a group never overrides it), no server succeeded, or the fastest is
     * already selected. While [connectedLocked] (connected, and the user did not just choose
     * "Fastest") the selection is changed only by the auto-switch rules in
     * [com.ahouei.v2rayekai.handler.AutoSwitchEngine], so this returns null.
     */
    fun autoSelectTarget(
        servers: List<ServersCache>,
        selectedGuid: String?,
        fastestMode: Boolean,
        isTesting: Boolean,
        easyMode: Boolean,
        connectedLocked: Boolean = false,
    ): String? {
        if (!easyMode || !fastestMode || isTesting || connectedLocked) return null
        if (selectedGuid != null && servers.none { it.guid == selectedGuid }) return null
        val fastest = fastestGuid(servers) ?: return null
        return fastest.takeIf { it != selectedGuid }
    }

    /**
     * True when choosing "Fastest" must start a bulk real-ping test: the mode is being enabled, no
     * bulk test is running and no server in [servers] has a successful result yet.
     */
    fun shouldStartTestOnFastest(servers: List<ServersCache>, enabled: Boolean, isBulkTesting: Boolean): Boolean =
        enabled && !isBulkTesting && fastestGuid(servers) == null

    fun visibleCount(revealed: Int, total: Int): Int = minOf(revealed, total)

    fun nextRevealed(revealed: Int, total: Int): Int = minOf(revealed + PAGE_SIZE, total)

    /** Rows the "Show more" button will add: a full page, or fewer on the last page. */
    fun nextPageSize(revealed: Int, total: Int): Int = nextRevealed(revealed, total) - visibleCount(revealed, total)

    /** Initial revealed count: one page, extended in whole pages until the selected row is visible. */
    fun initialRevealed(rows: List<EasyLocationRow>, selectedGuid: String?): Int {
        val index = rows.indexOfFirst { it.guid == selectedGuid }
        return if (index < PAGE_SIZE) PAGE_SIZE else (index / PAGE_SIZE + 1) * PAGE_SIZE
    }
}

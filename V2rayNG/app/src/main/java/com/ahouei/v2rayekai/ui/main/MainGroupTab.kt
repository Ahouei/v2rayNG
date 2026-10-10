package com.ahouei.v2rayekai.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahouei.v2rayekai.dto.GroupMapItem
import com.ahouei.v2rayekai.dto.entities.ServersCache
import com.ahouei.v2rayekai.ui.compose.GlassShapePill
import com.ahouei.v2rayekai.ui.compose.GlassSurface
import com.ahouei.v2rayekai.ui.compose.GlassTokens
import com.ahouei.v2rayekai.ui.compose.LocalDarkTheme
import com.ahouei.v2rayekai.ui.compose.colorConnectedDark
import com.ahouei.v2rayekai.ui.compose.colorConnectedLight
import kotlinx.coroutines.flow.StateFlow

/** Group tabs as a scrollable row of glass chips; the selected chip is tinted and outlined green. */
@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, groups.size) {
        // Keep the selected chip visible, as the former ScrollableTabRow did.
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any {
            it.index == selectedIndex && it.offset >= 0 &&
                it.offset + it.size <= listState.layoutInfo.viewportEndOffset
        }
        if (!fullyVisible) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
            val serverFlow = remember(group.id, mainViewModel) {
                mainViewModel.serversForGroup(group.id)
            }
            GroupTabItem(
                group = group,
                selected = index == selectedIndex,
                serverFlow = serverFlow,
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }
    val dark = LocalDarkTheme.current
    val green = if (dark) colorConnectedDark else colorConnectedLight

    GlassSurface(
        shape = GlassShapePill,
        elevation = 0.dp,
        tint = if (selected) GlassTokens.greenTint(dark) else Color.Transparent,
        edgeColor = if (selected) green else null,
        modifier = Modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 48.dp),
        interaction = Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

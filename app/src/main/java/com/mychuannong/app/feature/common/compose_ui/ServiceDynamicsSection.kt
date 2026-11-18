package com.mychuannong.app.feature.common.compose_ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private const val AUTO_ADVANCE_MILLIS = 1_000L
private const val INTERACTION_PAUSE_MILLIS = 4_000L
private const val LOOP_CENTER_PAGES = 10_000

/**
 * Keep only service dynamics relevant to the current client version.
 *
 * The backend returns the latest PUBLISHED version regardless of the caller,
 * so the client filters an UPDATE card when the server version is not newer.
 * Maintenance, closed and announcement entries are kept as-is.
 */
fun filterServiceUpdatesForClient(
    updates: List<RemoteServiceUpdate>,
    currentVersionCode: Int,
): List<RemoteServiceUpdate> = updates.filter { update ->
    if (update.type == "UPDATE") {
        val serverCode = update.versionCode ?: return@filter false
        serverCode > currentVersionCode
    } else {
        true
    }
}

/**
 * Map an infinite pager virtual page back to the real updates index.
 *
 * The pager uses a large virtual page count so swiping never reaches a dead
 * end; the current virtual page maps back with modulo so cards and
 * click targets always refer to a real update.
 */
fun virtualPageToRealIndex(
    virtualPage: Int,
    itemCount: Int,
): Int = when {
    itemCount <= 0 -> 0
    else -> Math.floorMod(virtualPage, itemCount)
}

/**
 * Unified "服务动态" area on the service page.
 *
 * A single update is a static card; two or more updates are presented as a
 * horizontal pager. Existing automatic rotation pauses after interaction.
 */
@Composable
fun ServiceDynamicsSection(
    updates: List<RemoteServiceUpdate>,
    onUpdateClick: (RemoteServiceUpdate) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    isActive: Boolean = true,
) {
    val colors = AppTheme.colors
    val totalVirtualPages = if (updates.size > 1) LOOP_CENTER_PAGES * 2 * updates.size else 1
    val initialVirtualPage = if (updates.size > 1) LOOP_CENTER_PAGES * updates.size else 0
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage,
        pageCount = { totalVirtualPages },
    )
    var lastInteractionAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) lastInteractionAt = System.currentTimeMillis()
            }
    }
    LaunchedEffect(isActive, updates.size, pagerState) {
        if (!isActive || updates.size < 2) return@LaunchedEffect
        while (true) {
            delay(AUTO_ADVANCE_MILLIS)
            val now = System.currentTimeMillis()
            if (now - lastInteractionAt < INTERACTION_PAUSE_MILLIS) continue
            val next = (pagerState.currentPage + 1) % pagerState.pageCount
            pagerState.animateScrollToPage(next)
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "服务动态",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTextColor,
            )
            if (error != null && updates.isNotEmpty()) Text("刷新失败，已保留上次动态",
                modifier = Modifier.weight(1f).padding(start = 4.dp), fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.secondaryTextColor)
        }
        if(updates.isEmpty()) {
            androidx.compose.material3.Surface(shape=RoundedCornerShape(20.dp),color=colors.bgCardColor) {
                Text(if(loading) "正在加载服务动态…" else error ?: "暂无服务动态",
                    Modifier.fillMaxWidth().padding(20.dp),color=colors.secondaryTextColor)
            }
            return@Column
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().testTag("service-dynamics-pager"),
        ) { page ->
            val realIndex = virtualPageToRealIndex(page, updates.size)
            val update = updates[realIndex]
            ServiceDynamicsCard(
                update = update,
                onClick = {
                    lastInteractionAt = System.currentTimeMillis()
                    onUpdateClick(update)
                },
            )
        }

        if (updates.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(updates.size) { index ->
                    val isSelected = virtualPageToRealIndex(pagerState.currentPage, updates.size) == index
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .background(
                                if (isSelected) colors.commonColor else colors.outlineLowContrastColor,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                }
            }
        }

    }
}

@Composable
private fun ServiceDynamicsCard(
    update: RemoteServiceUpdate,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val (label, accent, icon) = when (update.type) {
        "UPDATE" -> Triple("更新", colors.commonColor, Icons.Rounded.SystemUpdate)
        "MAINTENANCE" -> Triple("维护中", Color(0xFFB8860B), Icons.Rounded.Build)
        "CLOSED" -> Triple("暂不开放", colors.greyTextColor, Icons.Rounded.EventBusy)
        else -> Triple("公告", colors.commonColor, Icons.Rounded.Campaign)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgCardColor, RoundedCornerShape(20.dp))
            .border(
                BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = update.category?.takeIf { it.isNotBlank() } ?: label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = update.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildSummary(update),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.secondaryTextColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        update.imageUrl?.let { image ->
            coil.compose.AsyncImage(model = com.mychuannong.app.core.network.campustools.CampusToolsApiClient.absoluteUrl(image), contentDescription = "公告图片", modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        update.recoverAt?.let { recoverAt ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "预计恢复：${formatMillis(recoverAt)}",
                fontSize = 12.sp,
                color = colors.greyTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildSummary(update: RemoteServiceUpdate): String {
    val versionSuffix = update.versionName?.let { "（$it）" }.orEmpty()
    return when (update.type) {
        "UPDATE" -> "发现新版本$versionSuffix，点击查看更新说明"
        "MAINTENANCE", "CLOSED" -> update.summary
        else -> update.summary
    }
}

private fun formatMillis(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))

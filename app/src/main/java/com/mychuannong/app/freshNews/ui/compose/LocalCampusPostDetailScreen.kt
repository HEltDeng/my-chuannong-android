package com.mychuannong.app.freshNews.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LostFoundStatus
import android.graphics.BitmapFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalCampusPostDetailScreen(
    post: LocalCampusPost?,
    onBackClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit,
    onLostFoundStatus: (LostFoundStatus) -> Unit,
    onLocalClaim: () -> Unit,
) {
    val colors = AppTheme.colors
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.overviewPageBackgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "帖子详情",
                        color = colors.primaryTextColor,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.overviewPageBackgroundColor
                )
            )
        }
    ) { paddingValues ->
        if (post == null) {
            LocalCampusMissingPost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
            )
        } else {
            var showDeleteConfirm by remember(post.id) { mutableStateOf(false) }
            var showReportConfirm by remember(post.id) { mutableStateOf(false) }
            var reportMessage by remember(post.id) { mutableStateOf<String?>(null) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.overviewPageBackgroundColor)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.bgCardColor
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = colors.bgButtonLowlightColor
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = colors.commonColor,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "川农同学",
                                        color = colors.primaryTextColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = "本地发布",
                                        color = colors.commonColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.bgButtonLowlightColor)
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatLocalCampusTime(post.createdAtMillis),
                                    color = colors.greyTextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (post.title.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = post.title,
                                color = colors.primaryTextColor,
                                fontSize = 21.sp,
                                lineHeight = 29.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(if (post.title.isEmpty()) 20.dp else 12.dp))
                        Text(
                            text = post.content,
                            color = colors.primaryTextColor,
                            fontSize = 16.sp,
                            lineHeight = 25.sp
                        )
                        post.lostFound?.let { info ->
                            Spacer(modifier = Modifier.height(14.dp))
                            val image = remember(info.imagePath) { info.imagePath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                            if (image != null) {
                                Image(image, "本地物品图片", modifier = Modifier.fillMaxWidth().height(190.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Text("拾到物品 · ${info.status.label}", color = colors.commonColor, fontWeight = FontWeight.SemiBold)
                            Text("${info.category.label}\n拾取地点：${info.location}\n拾取日期：${info.foundDate}${info.approximateTime?.let { " · $it" } ?: ""}", color = colors.secondaryTextColor, fontSize = 14.sp, lineHeight = 21.sp)
                            info.claimNote.takeIf(String::isNotBlank)?.let { Text("认领说明：$it", color = colors.secondaryTextColor, fontSize = 14.sp) }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "#${post.tag}",
                            color = colors.commonColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bgButtonLowlightColor)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "此内容仅保存在当前设备，尚未公开发布。",
                            color = colors.secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f)) {
                        Text(if (post.isFavorite) "取消收藏" else "收藏")
                    }
                    OutlinedButton(onClick = { showReportConfirm = true }, modifier = Modifier.weight(1f)) { Text("举报") }
                }
                reportMessage?.let { Text(it, color = colors.commonColor, fontSize = 12.sp) }
                post.lostFound?.let { info ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (info.isLocalPublisher) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onLostFoundStatus(LostFoundStatus.CLAIMED) }, modifier = Modifier.weight(1f), enabled = info.status != LostFoundStatus.RETURNED) { Text("标记已认领") }
                            Button(onClick = { onLostFoundStatus(LostFoundStatus.RETURNED) }, modifier = Modifier.weight(1f), enabled = info.status != LostFoundStatus.RETURNED) { Text("标记已归还") }
                        }
                    } else {
                        Button(onClick = onLocalClaim, enabled = info.status != LostFoundStatus.RETURNED, modifier = Modifier.fillMaxWidth()) { Text("申请认领") }
                        if (info.hasLocalClaimRequest) Text("已提交本地模拟认领申请，未向任何真实联系人发送消息。", color = colors.secondaryTextColor, fontSize = 12.sp)
                    }
                    Text("当前为本地功能原型，信息仅保存在本机。", color = colors.secondaryTextColor, fontSize = 12.sp)
                }
                if (post.linkedActivityId == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("删除本机帖子") }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.bgCardColor
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = colors.commonColor,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = "社区互动功能筹备中",
                                color = colors.primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "当前版本不提供真实评论、点赞或公开传播。",
                                color = colors.secondaryTextColor,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("删除本机帖子？") },
                        text = { Text("删除后列表与收藏状态会同步更新。") },
                        confirmButton = { Button(onClick = { onDelete(); showDeleteConfirm = false }) { Text("删除") } },
                        dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
                    )
                }
                if (showReportConfirm) {
                    AlertDialog(
                        onDismissRequest = { showReportConfirm = false },
                        title = { Text("本地测试举报") },
                        text = { Text("正式上线后仍需服务端审核、管理员后台与封禁机制。") },
                        confirmButton = { Button(onClick = { onReport("不当内容"); reportMessage = "已收到本地测试举报，未进行线上处理。"; showReportConfirm = false }) { Text("记录举报") } },
                        dismissButton = { OutlinedButton(onClick = { showReportConfirm = false }) { Text("取消") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalCampusMissingPost(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.bgCardColor
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "帖子不存在",
                color = AppTheme.colors.primaryTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本地数据可能已被清除",
                color = AppTheme.colors.secondaryTextColor
            )
        }
    }
}

package com.mychuannong.app.feature.feedback.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.network.campustools.FeedbackRequest
import com.mychuannong.app.core.network.campustools.RemoteFeedback
import com.mychuannong.app.core.network.campustools.authenticatedCall
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class FeedbackActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.mychuannong.app.core.account.AccountAccess.isSignedIn) {
            com.mychuannong.app.core.account.AccountAccess.openOrRequestLogin(
                this,
                android.content.Intent(this, FeedbackActivity::class.java),
            )
            finish()
            return
        }
        setComposeContent {
            FeedbackScreen(onBack = ::finish)
        }
    }
}

private enum class FeedbackPage { Submit, Mine }
private data class FeedbackCategory(val value: String, val label: String)

private val feedbackCategories = listOf(
    FeedbackCategory("FEATURE_SUGGESTION", "功能建议"),
    FeedbackCategory("RESERVATION", "预约问题"),
    FeedbackCategory("LOST_FOUND", "失物招领"),
    FeedbackCategory("RESOURCE_LIBRARY", "资料库"),
    FeedbackCategory("ACCOUNT_SECURITY", "账号与安全"),
    FeedbackCategory("CONTENT_REPORT", "内容投诉"),
    FeedbackCategory("OTHER", "其他"),
)

@Composable
private fun FeedbackScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf(FeedbackPage.Submit) }
    var category by remember { mutableStateOf(feedbackCategories.first()) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var successVisible by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<List<RemoteFeedback>>(emptyList()) }
    var loadingMine by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun refreshMine() {
        if (loadingMine) return
        loadingMine = true
        scope.launch {
            runCatching { authenticatedCall { it.feedback() } }
                .onSuccess { feedback = it }
                .onFailure { message = it.message ?: "反馈记录加载失败，请稍后重试" }
            loadingMine = false
        }
    }

    LaunchedEffect(page) {
        if (page == FeedbackPage.Mine) refreshMine()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp),
    ) {
        com.mychuannong.app.core.theme.SecondaryPageHeader("意见反馈", onBack, actions = {
            TextButton(onClick = {
                page = if (page == FeedbackPage.Submit) FeedbackPage.Mine else FeedbackPage.Submit
                message = ""
            }) { Text(if (page == FeedbackPage.Submit) "我的反馈" else "提交反馈") }
        })

        if (page == FeedbackPage.Submit) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "你的反馈会关联当前我的川农账号，方便管理员跟进。请勿填写密码、验证码或其他敏感信息。",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                )
                Text(
                    "反馈类型",
                    color = AppTheme.colors.primaryTextColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 10.dp),
                ) {
                    feedbackCategories.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { item ->
                                FilterChip(
                                    selected = category == item,
                                    onClick = { category = item },
                                    label = { Text(item.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = AppTheme.colors.bgCardColor,
                                        labelColor = AppTheme.colors.primaryTextColor,
                                        selectedContainerColor = AppTheme.colors.bgSecondaryColor,
                                        selectedLabelColor = AppTheme.colors.primaryTextColor,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                ImeCompatibleTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(80)
                        message = ""
                    },
                    label = "反馈标题",
                    placeholder = "请简要概括问题或建议",
                    singleLine = true,
                )
                Text(
                    "${title.length}/80",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 5.dp),
                )
                ImeCompatibleTextField(
                    value = content,
                    onValueChange = {
                        content = it.take(2000)
                        message = ""
                    },
                    label = "反馈内容",
                    placeholder = "请描述问题现象、操作步骤或建议",
                    singleLine = false,
                    minLines = 7,
                    maxLines = 12,
                )
                Text(
                    "${content.length}/2000",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
                )
                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = AppTheme.colors.errorRedColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Button(
                    enabled = title.isNotBlank() && content.isNotBlank() && !submitting,
                    onClick = {
                        if (submitting) return@Button
                        submitting = true
                        message = ""
                        scope.launch {
                            runCatching {
                                authenticatedCall {
                                    it.submitFeedback(
                                        FeedbackRequest(
                                            category = category.value,
                                            title = title.trim(),
                                            content = content.trim(),
                                            appVersion = BuildConfig.VERSION_NAME,
                                        )
                                    )
                                }
                            }.onSuccess {
                                title = ""
                                content = ""
                                successVisible = true
                                feedback = emptyList()
                            }.onFailure {
                                message = it.message ?: "提交失败，已保留填写内容，请检查网络后重试"
                            }
                            submitting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.functionalTextColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 28.dp),
                ) {
                    Text(if (submitting) "提交中…" else "提交反馈")
                }
            }
        } else {
            FeedbackList(
                feedback = feedback,
                loading = loadingMine,
                message = message,
                onRefresh = ::refreshMine,
            )
        }
    }

    if (successVisible) {
        AlertDialog(
            onDismissRequest = { successVisible = false },
            title = { Text("反馈已提交", color = AppTheme.colors.primaryTextColor) },
            text = {
                Text(
                    "我们会尽快处理。你可在‘我的反馈’中查看处理进度。",
                    color = AppTheme.colors.secondaryTextColor,
                )
            },
            confirmButton = {
                Button(onClick = {
                    successVisible = false
                    page = FeedbackPage.Mine
                }) { Text("查看我的反馈") }
            },
            dismissButton = {
                TextButton(onClick = { successVisible = false }) { Text("继续") }
            },
        )
    }
}

@Composable
private fun FeedbackList(
    feedback: List<RemoteFeedback>,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) {
                Text(
                    "仅显示当前账号提交的反馈",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "刷新中…" else "刷新")
                }
            }
        }
        if (message.isNotBlank()) {
            item { Text(message, color = AppTheme.colors.errorRedColor, fontSize = 13.sp) }
        }
        if (!loading && feedback.isEmpty()) {
            item {
                Text(
                    "还没有反馈记录",
                    color = AppTheme.colors.secondaryTextColor,
                    modifier = Modifier.padding(vertical = 34.dp),
                )
            }
        }
        items(feedback, key = RemoteFeedback::id) { item ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            feedbackCategories.firstOrNull { it.value == item.category }?.label
                                ?: "其他",
                            color = AppTheme.colors.commonColor,
                            fontSize = 12.sp,
                        )
                        Text(
                            feedbackStatusLabel(item.status),
                            color = feedbackStatusColor(item.status),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        item.title,
                        color = AppTheme.colors.primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        item.content,
                        color = AppTheme.colors.secondaryTextColor,
                        maxLines = 4,
                    )
                    if (item.publicReply.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = AppTheme.colors.bgSecondaryColor
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "处理说明",
                                    color = AppTheme.colors.primaryTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    item.publicReply,
                                    color = AppTheme.colors.primaryTextColor,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    } else if (item.status in setOf("RESOLVED", "CLOSED")) {
                        Text(
                            "处理说明正在同步，请稍后刷新",
                            color = AppTheme.colors.secondaryTextColor,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "提交 ${feedbackTime(item.createdAt)} · 更新 ${feedbackTime(item.updatedAt)}",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun feedbackStatusLabel(status: String): String = when (status) {
    "OPEN" -> "待处理"
    "PROCESSING" -> "处理中"
    "RESOLVED" -> "已解决"
    "CLOSED" -> "已关闭"
    else -> status
}

@Composable
private fun feedbackStatusColor(status: String) = when (status) {
    "RESOLVED" -> AppTheme.colors.successGreenColor
    "CLOSED" -> AppTheme.colors.secondaryTextColor
    else -> AppTheme.colors.commonColor
}

private fun feedbackTime(value: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(value))

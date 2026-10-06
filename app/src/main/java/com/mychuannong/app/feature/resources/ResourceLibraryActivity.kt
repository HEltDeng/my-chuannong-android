@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mychuannong.app.feature.resources

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.network.campustools.RemoteResource
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ResourceLibraryActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccountAccess.isSignedIn) {
            startActivity(com.mychuannong.app.core.account.ui.AccountRequiredActivity.intent(this, intent))
            finish()
            return
        }
        setComposeContent {
            FeatureAvailabilityGate(
                featureKey = "resource_library",
                title = "资料库",
                onBack = ::finish,
            ) {
                ResourceLibraryRoute(
                    onBack = ::finish,
                    onOpenUrl = ::openUrl,
                )
            }
        }
    }

    private fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            Toast.makeText(this, "链接未通过安全校验", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            if (uri?.scheme == "https" && uri.host in setOf("www.sicau.edu.cn", "lib.sicau.edu.cn")) {
                startActivity(Intent(this, com.mychuannong.app.common.ui.WebViewActivity::class.java)
                    .putExtra("url_tag", url).putExtra("title_tag", "学校服务"))
                return
            }
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        }.onFailure {
            Toast.makeText(this, "未找到可打开网页的应用", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ResourceLibraryRoute(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items by ResourceLibraryRepository.items.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(ResourceListMode.ALL) }
    var selected by remember { mutableStateOf<RemoteResource?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(query, selectedCategory, mode) {
        delay(220)
        loading = true
        ResourceLibraryRepository.refresh(query, selectedCategory, mode)
            .onSuccess { rows ->
                if (query.isBlank() && selectedCategory == null && mode == ResourceListMode.ALL) {
                    categories = rows.map(RemoteResource::category).distinct()
                }
                message = ""
            }
            .onFailure { message = it.message ?: "资料库暂时不可用" }
        loading = false
    }

    selected?.let { resource ->
        ResourceDetailScreen(
            resource = resource,
            onBack = { selected = null },
            onOpen = {
                scope.launch {
                    onOpenUrl(resource.externalUrl)
                }
            },
            onFavorite = {
                if (!AccountAccess.requireLogin(context, Intent(context, ResourceLibraryActivity::class.java))) return@ResourceDetailScreen
                scope.launch {
                    ResourceLibraryRepository.toggleFavorite(resource.id)
                        .onSuccess { selected = resource.copy(isFavorite = it) }
                        .onFailure { message = it.message ?: "收藏操作失败" }
                }
            },
            onComplaint = { reason, detail ->
                if (!AccountAccess.requireLogin(context, Intent(context, ResourceLibraryActivity::class.java))) return@ResourceDetailScreen
                scope.launch {
                    ResourceLibraryRepository.submitComplaint(
                        resource.id,
                        reason,
                        detail,
                    ).onSuccess {
                        message = "投诉已提交，管理员将在后台处理"
                    }.onFailure {
                        message = it.message ?: "投诉提交失败"
                    }
                }
            },
            message = message,
        )
        return
    }

    Scaffold(
        containerColor = AppTheme.colors.bgPrimaryColor,
        topBar = {
            com.mychuannong.app.core.theme.SecondaryScaffoldHeader("资料库", onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            Surface(
                color = AppTheme.colors.bgSecondaryColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    "请确认资料来源合法并遵守版权与使用规则。资料页仅提供经后台配置的外部链接，不支持普通用户上传。",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
            ImeCompatibleTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                placeholder = "搜索标题、简介和标签",
                showSearchIcon = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = mode == ResourceListMode.ALL,
                        onClick = { mode = ResourceListMode.ALL },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = mode == ResourceListMode.FAVORITES,
                        onClick = {
                            if (AccountAccess.requireLogin(context, Intent(context, ResourceLibraryActivity::class.java))) mode = ResourceListMode.FAVORITES
                        },
                        label = { Text("我的收藏") },
                    )
                }
                item {
                    FilterChip(
                        selected = mode == ResourceListMode.RECENT,
                        onClick = {
                            if (AccountAccess.requireLogin(context, Intent(context, ResourceLibraryActivity::class.java))) mode = ResourceListMode.RECENT
                        },
                        label = { Text("最近浏览") },
                    )
                }
            }
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("全部分类") },
                        )
                    }
                    items(categories, key = { it }) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                        )
                    }
                }
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            when {
                loading && items.isEmpty() -> EmptyLibrary("正在读取资料…")
                items.isEmpty() -> EmptyLibrary(
                    when (mode) {
                        ResourceListMode.FAVORITES -> "还没有收藏资料"
                        ResourceListMode.RECENT -> "还没有浏览记录"
                        ResourceListMode.ALL -> "没有找到匹配的资料"
                    }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = RemoteResource::id) { resource ->
                        ResourceCard(
                            resource = resource,
                            onClick = {
                                selected = resource
                                if (CampusToolsSession.currentUser != null) scope.launch { ResourceLibraryRepository.recordView(resource.id) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    resource: RemoteResource,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    resource.category,
                    color = AppTheme.colors.functionalTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            AppTheme.colors.bgButtonLowlightColor,
                            RoundedCornerShape(99.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                if (resource.isFavorite) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        "已收藏",
                        tint = AppTheme.colors.functionalTextColor,
                    )
                }
            }
            Text(
                resource.title,
                color = AppTheme.colors.primaryTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                resource.summary,
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${resource.sourceLabel.ifBlank { resource.publisherLabel }} · ${resource.audienceScope.ifBlank { "适用范围见详情" }}",
                color = AppTheme.colors.greyTextColor,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ResourceDetailScreen(
    resource: RemoteResource,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onComplaint: (String, String) -> Unit,
    message: String,
) {
    var complaintVisible by remember { mutableStateOf(false) }
    var complaintReason by rememberSaveable { mutableStateOf("版权或授权问题") }
    var complaintDetail by rememberSaveable { mutableStateOf("") }
    Scaffold(
        containerColor = AppTheme.colors.bgPrimaryColor,
        topBar = {
            com.mychuannong.app.core.theme.SecondaryScaffoldHeader("资料详情", onBack, actions = {
                IconButton(onClick = onFavorite) {
                    Icon(if (resource.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        if (resource.isFavorite) "取消收藏" else "收藏")
                }
            })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            if (!resource.coverUrl.isNullOrBlank()) {
                item {
                    AsyncImage(
                        model = resource.coverUrl,
                        contentDescription = "${resource.title}封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }
            }
            item {
                Text(
                    resource.category,
                    color = AppTheme.colors.functionalTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    resource.title,
                    color = AppTheme.colors.primaryTextColor,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            item {
                Surface(
                    color = AppTheme.colors.bgCardColor,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(18.dp),
                    ) {
                        DetailLine("简介", resource.summary.ifBlank { "暂无简介" })
                        DetailLine("标签", resource.tags.joinToString(" · ").ifBlank { "暂无标签" })
                        DetailLine("适用范围", resource.audienceScope.ifBlank { "未特别限定" })
                        DetailLine("发布者", resource.publisherLabel)
                        DetailLine("资料来源", resource.sourceLabel.ifBlank { "请查看外部页面" })
                        DetailLine(
                            "发布时间",
                            resource.publishedAt?.let(::formatPublishedAt) ?: "未记录",
                        )
                        DetailLine(
                            "资源类型",
                            if (resource.resourceType == "EXTERNAL_LINK") "外部链接"
                            else resource.resourceType,
                        )
                        DetailLine("费用", if (resource.isFree) "免费" else "后续开放")
                    }
                }
            }
            item {
                Text(
                    resource.usageNote.ifBlank {
                        "请确认资料来源合法并遵守版权与使用规则。"
                    },
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            if (message.isNotBlank()) {
                item {
                    Text(message, color = AppTheme.colors.commonColor, fontSize = 12.sp)
                }
            }
            item {
                Button(
                    enabled = resource.isFree,
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Text(if (resource.isFree) "打开资料" else "付费能力后续开放")
                }
            }
            item {
                OutlinedButton(
                    onClick = { complaintVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("侵权投诉 / 要求下架")
                }
            }
        }
    }
    if (complaintVisible) {
        AlertDialog(
            onDismissRequest = { complaintVisible = false },
            title = { Text("提交资料投诉") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeCompatibleTextField(
                        value = complaintReason,
                        onValueChange = { complaintReason = it.take(80) },
                        label = "投诉原因",
                    )
                    ImeCompatibleTextField(
                        value = complaintDetail,
                        onValueChange = { complaintDetail = it.take(1000) },
                        label = "补充说明（可选）",
                        singleLine = false,
                    )
                    Text(
                        "投诉会关联当前我的川农账号，后台不会公开你的手机号或预约识别学号。",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = complaintReason.isNotBlank(),
                    onClick = {
                        onComplaint(complaintReason, complaintDetail)
                        complaintVisible = false
                    },
                ) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { complaintVisible = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, color = AppTheme.colors.greyTextColor, fontSize = 11.sp)
        Text(
            value,
            color = AppTheme.colors.primaryTextColor,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EmptyLibrary(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = AppTheme.colors.secondaryTextColor)
    }
}

private fun formatPublishedAt(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))

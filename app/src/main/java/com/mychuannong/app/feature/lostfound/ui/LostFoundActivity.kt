package com.mychuannong.app.feature.lostfound.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import com.mychuannong.app.feature.lostfound.data.LostFoundListFilter
import com.mychuannong.app.feature.lostfound.data.LostFoundConnectionState
import com.mychuannong.app.feature.lostfound.data.LostFoundRepository
import com.mychuannong.app.feature.lostfound.data.LostFoundRules
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalLostFoundInfo
import com.mychuannong.app.freshNews.data.local.model.LostFoundCategory
import com.mychuannong.app.freshNews.data.local.model.LostFoundStatus
import com.mychuannong.app.freshNews.data.local.model.LostFoundType
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay

class LostFoundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccountAccess.isSignedIn) {
            startActivity(com.mychuannong.app.core.account.ui.AccountRequiredActivity.intent(this, intent))
            finish()
            return
        }
        enableEdgeToEdge()
        val startPage = when (intent.getStringExtra(EXTRA_START)) {
            START_DETAIL -> intent.getStringExtra(EXTRA_POST_ID)
                ?.takeIf(String::isNotBlank)
                ?.let(LostFoundPage::Detail)
                ?: LostFoundPage.List
            START_PUBLISH_LOST -> LostFoundPage.Publish(LostFoundType.LOST)
            START_PUBLISH_FOUND -> LostFoundPage.Publish(LostFoundType.FOUND)
            else -> LostFoundPage.List
        }
        setContent {
            AppSkinTheme {
                FeatureAvailabilityGate(
                    featureKey = "lost_found",
                    title = "失物招领",
                    onBack = ::finish,
                ) {
                    LostFoundRoute(
                        initialPage = startPage,
                        finishActivity = ::finish,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_POST_ID = "local_campus_post_id"
        private const val EXTRA_START = "lost_found_start"
        private const val START_DETAIL = "detail"
        private const val START_PUBLISH_LOST = "publish_lost"
        private const val START_PUBLISH_FOUND = "publish_found"

        fun listIntent(context: Context): Intent = Intent(context, LostFoundActivity::class.java)

        fun detailIntent(context: Context, postId: String): Intent =
            listIntent(context)
                .putExtra(EXTRA_START, START_DETAIL)
                .putExtra(EXTRA_POST_ID, postId)

        fun publishIntent(context: Context, type: LostFoundType): Intent =
            listIntent(context).putExtra(
                EXTRA_START,
                if (type == LostFoundType.LOST) START_PUBLISH_LOST else START_PUBLISH_FOUND,
            )
    }
}

private data class LostFoundDraft(
    val type: LostFoundType? = LostFoundType.FOUND,
    val imagePath: String? = null,
    val itemName: String = "",
    val category: LostFoundCategory? = null,
    val location: String = "",
    val date: String = LocalDate.now().toString(),
    val approximateTime: String? = null,
    val description: String = "",
    val claimNote: String = "",
    val status: LostFoundStatus? = null,
) {
    val effectiveType: LostFoundType
        get() = type ?: LostFoundType.FOUND

    val hasContent: Boolean
        get() = imagePath != null || itemName.isNotBlank() || location.isNotBlank() ||
            description.isNotBlank() || claimNote.isNotBlank()
}

private object LostFoundDraftStore {
    private const val CACHE_ID = "lost_found_local_v1"
    private const val KEY_DRAFT = "publish_draft_v1"
    private val gson = Gson()
    private val storage by lazy { MMKV.mmkvWithID(CACHE_ID) }

    fun load(): LostFoundDraft = runCatching {
        gson.fromJson(storage.decodeString(KEY_DRAFT), LostFoundDraft::class.java)
            ?.let { it.copy(type = it.type ?: LostFoundType.FOUND) }
            ?: LostFoundDraft()
    }.getOrDefault(LostFoundDraft())

    fun save(draft: LostFoundDraft) {
        check(storage.encode(KEY_DRAFT, gson.toJson(draft)))
    }

    fun clear() {
        storage.removeValueForKey(KEY_DRAFT)
    }
}

private sealed interface LostFoundPage {
    data object List : LostFoundPage
    data class Publish(
        val type: LostFoundType,
        val editingId: String? = null,
    ) : LostFoundPage
    data class Detail(val postId: String) : LostFoundPage
}

@Composable
private fun LostFoundRoute(
    initialPage: LostFoundPage,
    finishActivity: () -> Unit,
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(initialPage) }
    var draft by remember { mutableStateOf(LostFoundDraftStore.load()) }
    val operationNotice by LostFoundRepository.operationNotice.collectAsState()
    LaunchedEffect(Unit) { LostFoundRepository.refresh() }
    LaunchedEffect(operationNotice) {
        if (operationNotice != null) page = LostFoundPage.List
    }

    fun updateDraft(next: LostFoundDraft, persist: Boolean) {
        draft = next
        if (persist) LostFoundDraftStore.save(next)
    }

    fun openNewPublish(type: LostFoundType) {
        if (!AccountAccess.requireLogin(context, LostFoundActivity.publishIntent(context, type))) return
        val stored = LostFoundDraftStore.load()
        draft = if (stored.hasContent) stored.copy(type = type) else LostFoundDraft(type = type)
        LostFoundDraftStore.save(draft)
        page = LostFoundPage.Publish(type)
    }

    fun openEdit(post: LocalCampusPost) {
        if (!AccountAccess.requireLogin(context, LostFoundActivity.detailIntent(context, post.id))) return
        val info = post.lostFound ?: return
        draft = LostFoundDraft(
            type = info.effectiveType,
            imagePath = info.imagePath,
            itemName = info.itemName,
            category = info.category,
            location = info.location,
            date = info.foundDate,
            approximateTime = info.approximateTime,
            description = info.description,
            claimNote = info.claimNote,
            status = info.status,
        )
        page = LostFoundPage.Publish(info.effectiveType, editingId = post.id)
    }

    when (val current = page) {
        LostFoundPage.List -> LostFoundListScreen(
            onBack = finishActivity,
            onPublish = ::openNewPublish,
            onContinueDraft = {
                val stored = LostFoundDraftStore.load()
                draft = stored
                page = LostFoundPage.Publish(stored.effectiveType)
            },
            onOpen = { page = LostFoundPage.Detail(it) },
        )

        is LostFoundPage.Publish -> {
            BackHandler { page = LostFoundPage.List }
            LostFoundPublishScreen(
                type = current.type,
                draft = draft,
                isEditing = current.editingId != null,
                update = { updateDraft(it, current.editingId == null) },
                onBack = { page = LostFoundPage.List },
                onPublished = { info ->
                    val post = current.editingId?.let { existingId ->
                        LostFoundRepository.update(existingId, info)
                    } ?: if (current.editingId == null) {
                        LostFoundRepository.publish(info)
                    } else {
                        null
                    }
                    if (post != null) {
                        LostFoundDraftStore.clear()
                        draft = LostFoundDraft()
                        page = LostFoundPage.Detail(post.id)
                    }
                },
            )
        }

        is LostFoundPage.Detail -> {
            BackHandler { page = LostFoundPage.List }
            LostFoundDetailScreen(
                postId = current.postId,
                onBack = { page = LostFoundPage.List },
                onEdit = ::openEdit,
                onDeleted = { page = LostFoundPage.List },
            )
        }
    }

    operationNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = LostFoundRepository::clearOperationNotice,
            title = { Text("服务提示") },
            text = { Text(notice) },
            confirmButton = {
                Button(onClick = LostFoundRepository::clearOperationNotice) {
                    Text("知道了")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LostFoundScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.overviewPageBackgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = colors.primaryTextColor)
                        Text(
                            "服务端同步 · 断网可查看缓存",
                            color = colors.secondaryTextColor,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "返回", tint = colors.primaryTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.overviewPageBackgroundColor,
                ),
            )
        },
    ) { padding ->
        content(
            Modifier
                .padding(padding)
                .windowInsetsPadding(WindowInsets.ime),
        )
    }
}

@Composable
private fun LostFoundListScreen(
    onBack: () -> Unit,
    onPublish: (LostFoundType) -> Unit,
    onContinueDraft: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val allPosts by LostFoundRepository.sourcePosts.collectAsState()
    val serverSearchResults by LostFoundRepository.searchResults.collectAsState()
    val connectionState by LostFoundRepository.connectionState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LostFoundListFilter.ALL) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) {
        delay(300)
        LostFoundRepository.search(query)
    }
    val draft = remember(allPosts.size) { LostFoundDraftStore.load() }
    val searchBase = if (query.isBlank()) allPosts else serverSearchResults ?: allPosts
    val posts = remember(searchBase, query, filter, favoritesOnly, mineOnly) {
        LostFoundRules.filter(searchBase, filter, query, favoritesOnly, mineOnly)
    }

    LostFoundScaffold("失物招领", onBack) { modifier ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "独立失物工具，信息由校园工具服务统一保存，不会发布到校园频道。",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            if (connectionState == LostFoundConnectionState.LOADING) {
                item {
                    Text("正在同步失物信息…", color = AppTheme.colors.secondaryTextColor)
                }
            } else if (connectionState == LostFoundConnectionState.OFFLINE) {
                item {
                    Text(
                        "服务暂时不可用，正在显示本机缓存；恢复连接后会自动兼容旧数据。",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 12.sp,
                    )
                }
            } else if (connectionState == LostFoundConnectionState.RESTRICTED) {
                item {
                    Text(
                        "账号当前受限，失物招领服务暂不可访问。",
                        color = AppTheme.colors.errorRedColor,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onPublish(LostFoundType.LOST) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("发布寻物")
                    }
                    OutlinedButton(
                        onClick = { onPublish(LostFoundType.FOUND) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("发布招领")
                    }
                }
            }
            if (draft.hasContent) {
                item {
                    OutlinedButton(
                        onClick = {
                            if (AccountAccess.requireLogin(context, LostFoundActivity.publishIntent(context, draft.effectiveType))) onContinueDraft()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("继续未完成的${draft.effectiveType.label}草稿")
                    }
                }
            }
            item {
                ImeCompatibleTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = "搜索失物信息",
                    placeholder = "物品、地点、类别或描述",
                    modifier = Modifier.fillMaxWidth(),
                    showSearchIcon = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(LostFoundListFilter.entries.toList()) { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = {
                                if (AccountAccess.requireLogin(context, LostFoundActivity.listIntent(context))) favoritesOnly = !favoritesOnly
                            },
                            label = { Text("我的收藏") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = mineOnly,
                            onClick = {
                                if (AccountAccess.requireLogin(context, LostFoundActivity.listIntent(context))) mineOnly = !mineOnly
                            },
                            label = { Text("我的发布") },
                        )
                    }
                }
            }
            if (posts.isEmpty()) {
                item {
                    LostFoundEmptyState(
                        if (query.isNotBlank()) "没有找到匹配的失物信息" else "暂时没有失物信息",
                    )
                }
            } else {
                items(posts, key = LocalCampusPost::id) { post ->
                    LostFoundPostCard(post = post, onClick = { onOpen(post.id) })
                }
            }
        }
    }
}

@Composable
private fun LostFoundEmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = AppTheme.colors.commonColor)
            Text(message, color = AppTheme.colors.primaryTextColor, fontWeight = FontWeight.SemiBold)
            Text(
                "你可以调整筛选条件，或发布一条失物信息。",
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun LostFoundPostCard(
    post: LocalCampusPost,
    onClick: () -> Unit,
) {
    val info = post.lostFound
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LostFoundThumbnail(info?.imagePath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info?.effectiveType?.label ?: "历史信息",
                        color = AppTheme.colors.commonColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " · ${info?.let(LostFoundRules::statusLabel) ?: "历史记录"}",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (post.isFavorite) {
                        Icon(
                            Icons.Rounded.Favorite,
                            "已收藏",
                            tint = AppTheme.colors.commonColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    post.title,
                    color = AppTheme.colors.primaryTextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info?.let {
                        "${it.category.label} · ${it.location} · ${it.foundDate}"
                    } ?: post.content,
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LostFoundThumbnail(path: String?) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppTheme.colors.bgButtonLowlightColor,
    ) {
        if (path.isNullOrBlank()) {
            Icon(
                Icons.Rounded.Image,
                null,
                tint = AppTheme.colors.commonColor,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            AsyncImage(
                model = imageModel(path),
                contentDescription = "物品图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LostFoundPublishScreen(
    type: LostFoundType,
    draft: LostFoundDraft,
    isEditing: Boolean,
    update: (LostFoundDraft) -> Unit,
    onBack: () -> Unit,
    onPublished: (LocalLostFoundInfo) -> Unit,
) {
    var step by rememberSaveable(type, isEditing) { mutableStateOf(1) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val effectiveDraft = if (draft.effectiveType == type) draft else draft.copy(type = type)
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { copyImageToPrivateStorage(context, uri) }.fold(
                onSuccess = {
                    update(effectiveDraft.copy(imagePath = it, type = type))
                    imageError = null
                },
                onFailure = {
                    imageError = "图片读取失败，请更换图片或不添加图片继续"
                },
            )
        }
    }

    BackHandler {
        if (step > 1) step-- else onBack()
    }
    LostFoundScaffold(
        title = if (isEditing) "编辑${type.label}信息" else "发布${type.label}信息",
        onBack = { if (step > 1) step-- else onBack() },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("第 $step / 5 步", color = AppTheme.colors.commonColor, fontSize = 13.sp)
            when (step) {
                1 -> {
                    DraftImage(effectiveDraft.imagePath)
                    Button(
                        onClick = { imageLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (effectiveDraft.imagePath == null) "添加图片（可选）" else "替换图片")
                    }
                    if (effectiveDraft.imagePath != null) {
                        OutlinedButton(
                            onClick = { update(effectiveDraft.copy(imagePath = null)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除图片")
                        }
                    }
                    imageError?.let {
                        Text(it, color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp)
                    }
                }

                2 -> {
                    ImeCompatibleTextField(
                        effectiveDraft.itemName,
                        { update(effectiveDraft.copy(itemName = it.take(40), type = type)) },
                        label = "物品名称",
                        placeholder = if (type == LostFoundType.LOST) "例如：黑色雨伞" else "例如：校园卡",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("物品类别", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(LostFoundCategory.entries.toList()) { category ->
                            FilterChip(
                                effectiveDraft.category == category,
                                { update(effectiveDraft.copy(category = category, type = type)) },
                                { Text(category.label) },
                            )
                        }
                    }
                }

                3 -> {
                    ImeCompatibleTextField(
                        effectiveDraft.location,
                        { update(effectiveDraft.copy(location = it.take(60), type = type)) },
                        label = if (type == LostFoundType.LOST) "遗失地点" else "拾取地点",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(LOST_FOUND_PLACE_SUGGESTIONS) { place ->
                            FilterChip(
                                selected = effectiveDraft.location == place,
                                onClick = { update(effectiveDraft.copy(location = place, type = type)) },
                                label = { Text(place) },
                            )
                        }
                    }
                    Text(
                        if (type == LostFoundType.LOST) "遗失日期" else "拾取日期",
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((0L..6L).map { LocalDate.now().minusDays(it).toString() }) { date ->
                            FilterChip(
                                effectiveDraft.date == date,
                                { update(effectiveDraft.copy(date = date, type = type)) },
                                { Text(date.removeRange(0, 5)) },
                            )
                        }
                    }
                    Text("大致时间（可选）")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("上午", "下午", "晚上")) { time ->
                            FilterChip(
                                effectiveDraft.approximateTime == time,
                                {
                                    update(
                                        effectiveDraft.copy(
                                            approximateTime = time,
                                            type = type,
                                        ),
                                    )
                                },
                                { Text(time) },
                            )
                        }
                    }
                }

                4 -> {
                    ImeCompatibleTextField(
                        effectiveDraft.description,
                        { update(effectiveDraft.copy(description = it.take(300), type = type)) },
                        label = "物品特征描述（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                    )
                    ImeCompatibleTextField(
                        effectiveDraft.claimNote,
                        { update(effectiveDraft.copy(claimNote = it.take(160), type = type)) },
                        label = if (type == LostFoundType.LOST) {
                            "找回说明（可选）"
                        } else {
                            "认领说明（可选）"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                    Text(
                        "当前不收集手机号或社交账号，也不提供跨设备联系。",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 13.sp,
                    )
                }

                5 -> LostFoundPreviewBody(effectiveDraft)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("上一步")
                    }
                }
                Button(
                    onClick = {
                        if (step < 5) {
                            step++
                        } else if (!submitting) {
                            submitting = true
                            onPublished(effectiveDraft.toInfo())
                        }
                    },
                    enabled = !submitting && when (step) {
                        2 -> effectiveDraft.itemName.isNotBlank() &&
                            effectiveDraft.category != null
                        3 -> effectiveDraft.location.isNotBlank() &&
                            effectiveDraft.date.isNotBlank()
                        else -> true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (step == 5) {
                            if (isEditing) "保存修改" else "发布信息"
                        } else {
                            "下一步"
                        },
                    )
                }
            }
        }
    }
}

private fun LostFoundDraft.toInfo(): LocalLostFoundInfo = LocalLostFoundInfo(
    itemName = itemName.trim(),
    category = checkNotNull(category),
    location = location.trim(),
    foundDate = date,
    approximateTime = approximateTime,
    description = description.trim(),
    claimNote = claimNote.trim(),
    imagePath = imagePath,
    type = effectiveType,
    status = status ?: LostFoundRules.initialStatus(effectiveType),
)

@Composable
private fun LostFoundPreviewBody(draft: LostFoundDraft) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DraftImage(draft.imagePath)
            Text(
                "${draft.effectiveType.label} · ${
                    LostFoundRules.statusLabel(draft.toInfo())
                }",
                color = AppTheme.colors.commonColor,
                fontSize = 13.sp,
            )
            Text(
                draft.itemName.ifBlank { "未填写物品名称" },
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AppTheme.colors.primaryTextColor,
            )
            Text(
                draft.category?.label ?: "未选择类别",
                color = AppTheme.colors.secondaryTextColor,
            )
            Text(
                "${draft.location.ifBlank { "未填写地点" }} · ${draft.date}${
                    draft.approximateTime?.let { " · $it" } ?: ""
                }",
                color = AppTheme.colors.secondaryTextColor,
            )
            draft.description.takeIf(String::isNotBlank)?.let { Text(it) }
            draft.claimNote.takeIf(String::isNotBlank)?.let {
                Text("补充说明：$it", color = AppTheme.colors.secondaryTextColor)
            }
            Text(
                "通过失物招领服务发布，不会进入校园频道。",
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LostFoundDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onEdit: (LocalCampusPost) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    fun requireAccount() = AccountAccess.requireLogin(context, LostFoundActivity.detailIntent(context, postId))
    val sourcePosts by LostFoundRepository.sourcePosts.collectAsState()
    val post = sourcePosts.firstOrNull { it.id == postId && LostFoundRules.isLostFound(it) }
    var showDeleteConfirm by remember(postId) { mutableStateOf(false) }
    var showReportConfirm by remember(postId) { mutableStateOf(false) }
    var feedback by remember(postId) { mutableStateOf<String?>(null) }

    LostFoundScaffold("失物详情", onBack) { modifier ->
        if (post == null) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "这条失物信息不存在或已被删除",
                    color = AppTheme.colors.primaryTextColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack) { Text("返回列表") }
            }
            return@LostFoundScaffold
        }
        val info = post.lostFound
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DraftImage(info?.imagePath)
                        Text(
                            info?.let {
                                "${it.effectiveType.label} · ${LostFoundRules.statusLabel(it)}"
                            } ?: "历史失物信息",
                            color = AppTheme.colors.commonColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            post.title,
                            color = AppTheme.colors.primaryTextColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (info == null) {
                            Text(post.content, color = AppTheme.colors.primaryTextColor)
                        } else {
                            Text(
                                "类别：${info.category.label}\n" +
                                    "${if (info.effectiveType == LostFoundType.LOST) "遗失" else "拾取"}地点：${info.location}\n" +
                                    "${if (info.effectiveType == LostFoundType.LOST) "遗失" else "拾取"}日期：${info.foundDate}${
                                        info.approximateTime?.let { " · $it" } ?: ""
                                    }",
                                color = AppTheme.colors.secondaryTextColor,
                                lineHeight = 22.sp,
                            )
                            info.description.takeIf(String::isNotBlank)?.let {
                                Text("物品特征\n$it", color = AppTheme.colors.primaryTextColor)
                            }
                            info.claimNote.takeIf(String::isNotBlank)?.let {
                                Text("补充说明\n$it", color = AppTheme.colors.primaryTextColor)
                            }
                        }
                        Text(
                            "此信息由失物招领服务保存，未发布到校园频道。",
                            color = AppTheme.colors.secondaryTextColor,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (requireAccount()) LostFoundRepository.toggleFavorite(post.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (post.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(if (post.isFavorite) "取消收藏" else "收藏")
                    }
                    OutlinedButton(
                        onClick = { if (requireAccount()) showReportConfirm = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("举报")
                    }
                }
            }
            if (info != null) {
                if (info.isLocalPublisher) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { if (requireAccount()) onEdit(post) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Edit, null)
                                Spacer(Modifier.size(6.dp))
                                Text("编辑")
                            }
                            Button(
                                onClick = {
                                    if (requireAccount()) LostFoundRepository.updateStatus(
                                        post.id,
                                        LostFoundRules.resolvedStatus(info.effectiveType),
                                    )
                                },
                                enabled = LostFoundRules.statusLabel(info) !in
                                    setOf("已找回", "已归还"),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    if (info.effectiveType == LostFoundType.LOST) {
                                        "标记已找回"
                                    } else {
                                        "标记已归还"
                                    },
                                )
                            }
                        }
                    }
                    if (info.effectiveType == LostFoundType.FOUND &&
                        info.status == LostFoundStatus.WAITING
                    ) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    if (requireAccount()) LostFoundRepository.updateStatus(
                                        post.id,
                                        LostFoundStatus.CLAIMED,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("标记已认领")
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { if (requireAccount()) showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                            Spacer(Modifier.size(6.dp))
                            Text("删除我的发布")
                        }
                    }
                } else if (LostFoundRules.statusLabel(info) !in setOf("已找回", "已归还")) {
                    item {
                        Button(
                            onClick = {
                                if (requireAccount()) {
                                    LostFoundRepository.submitLocalClaim(post.id)
                                    feedback = "认领申请已记录；当前阶段不提供站内联系。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (info.effectiveType == LostFoundType.LOST) "提供线索" else "申请认领")
                        }
                    }
                }
            }
            feedback?.let { message ->
                item {
                    Text(
                        message,
                        color = AppTheme.colors.commonColor,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条失物信息？") },
            text = { Text("删除后列表、收藏和我的发布会同步更新。") },
            confirmButton = {
                Button(
                    onClick = {
                        if (LostFoundRepository.delete(context, postId)) onDeleted()
                        showDeleteConfirm = false
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
    if (showReportConfirm) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text("记录问题") },
            text = { Text("反馈将提交校园工具服务；当前不代表学校已受理。") },
            confirmButton = {
                Button(
                    onClick = {
                        LostFoundRepository.report(postId, "不当或无效信息")
                        feedback = "反馈已记录；管理员处理能力将在管理后台阶段完善。"
                        showReportConfirm = false
                    },
                ) {
                    Text("确认记录")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DraftImage(path: String?) {
    if (path.isNullOrBlank()) {
        Card(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(
                containerColor = AppTheme.colors.bgButtonLowlightColor,
            ),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Image, null, tint = AppTheme.colors.commonColor)
                Text("未添加图片", color = AppTheme.colors.secondaryTextColor)
            }
        }
    } else {
        AsyncImage(
            model = imageModel(path),
            contentDescription = "物品图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

private fun imageModel(path: String): Any =
    if (path.startsWith("http://") || path.startsWith("https://")) path else File(path)

private fun copyImageToPrivateStorage(context: Context, uri: Uri): String {
    val directory = File(context.filesDir, "lost_found_images").apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.jpg")
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unreadable image" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) {
            "Image dimensions are too large"
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        val decoded = resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unreadable image" }
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }
        requireNotNull(decoded) { "Unsupported image" }
        val rotation = runCatching {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
        val oriented = if (rotation == 0f) decoded else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation) },
                true,
            ).also { decoded.recycle() }
        }
        val scale = minOf(
            1f,
            MAX_IMAGE_DIMENSION.toFloat() / maxOf(oriented.width, oriented.height),
        )
        val scaled = if (scale == 1f) oriented else {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { oriented.recycle() }
        }
        val jpeg = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(jpeg).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (jpeg !== scaled) scaled.recycle()
        var encoded = ByteArray(0)
        for (quality in listOf(90, 82, 74, 66, 58, 50)) {
            encoded = ByteArrayOutputStream().use { output ->
                check(jpeg.compress(Bitmap.CompressFormat.JPEG, quality, output))
                output.toByteArray()
            }
            if (encoded.size <= MAX_IMAGE_BYTES) break
        }
        jpeg.recycle()
        require(encoded.isNotEmpty() && encoded.size <= MAX_IMAGE_BYTES) {
            "Image cannot be compressed to the upload limit"
        }
        target.outputStream().use { it.write(encoded) }
    } catch (error: Throwable) {
        target.delete()
        throw error
    }
    return target.absolutePath
}

private val LOST_FOUND_PLACE_SUGGESTIONS = listOf(
    "教学楼",
    "图书馆",
    "食堂",
    "宿舍区",
    "操场",
    "体育馆",
    "校门附近",
    "其他",
)

private const val MAX_IMAGE_BYTES = 3L * 1024L * 1024L
private const val MAX_IMAGE_DIMENSION = 1920
private const val MAX_IMAGE_DECODE_DIMENSION = 4096
private const val MAX_IMAGE_PIXELS = 50_000_000L

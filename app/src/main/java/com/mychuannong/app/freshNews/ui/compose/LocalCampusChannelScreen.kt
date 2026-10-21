package com.mychuannong.app.freshNews.ui.compose

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.viewModel.LocalCampusUiState
import com.mychuannong.app.freshNews.viewModel.LocalCampusViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocalCampusChannelScreen(
    viewModel: LocalCampusViewModel,
    onPublishClick: () -> Unit,
    onPostClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LocalCampusChannelContent(
        state = state,
        onTabSelected = viewModel::selectTab,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onCategorySelected = viewModel::selectCategory,
        onToggleFavorites = viewModel::toggleFavoritesOnly,
        onRefresh = viewModel::refresh,
        onPublishClick = onPublishClick,
        onPostClick = onPostClick
    )
}

@Composable
private fun LocalCampusChannelContent(
    state: LocalCampusUiState,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onRefresh: () -> Unit,
    onPublishClick: () -> Unit,
    onPostClick: (String) -> Unit
) {
    val colors = AppTheme.colors
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.scrollToTopRequest) {
        if (state.scrollToTopRequest > 0L) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = colors.overviewPageBackgroundColor,
        topBar = {
            LocalCampusTopBar(
                currentTab = state.currentTab,
                searchQuery = state.searchQuery,
                selectedCategory = state.selectedCategory,
                showFavoritesOnly = state.showFavoritesOnly,
                onTabSelected = onTabSelected,
                onSearchQueryChange = onSearchQueryChange,
                onCategorySelected = onCategorySelected,
                onToggleFavorites = onToggleFavorites
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPublishClick,
                modifier = Modifier.padding(bottom = 80.dp),
                containerColor = colors.commonColor,
                contentColor = colors.textButtonColor,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "发布本地帖子")
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overviewPageBackgroundColor),
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    containerColor = colors.bgCardColor,
                    color = colors.commonColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = 112.dp + paddingValues.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.refreshError?.let { message ->
                    item(key = "local_refresh_error") {
                        LocalCampusMessageCard(
                            title = "本地内容刷新失败",
                            message = message
                        )
                    }
                }
                if (state.currentTab == 1) {
                    item(key = "local_hot_empty") {
                        LocalCampusMessageCard(
                            title = "热门内容暂未开放",
                            message = "本地版本没有可靠的互动数据，因此暂不生成热度排行"
                        )
                    }
                } else if (state.visiblePosts.isEmpty()) {
                    item(key = "local_latest_empty") {
                        LocalCampusMessageCard(
                            title = when {
                                state.searchQuery.isNotBlank() -> "没有找到相关帖子"
                                state.selectedCategory == "失物招领" -> "暂时没有本地失物信息"
                                else -> "还没有本地帖子"
                            },
                            message = if (state.searchQuery.isBlank()) {
                                if (state.selectedCategory == "失物招领") "你可以稍后查看，或发布拾取信息帮助失主"
                                else "发布的内容只保存在这台设备，可随时从这里查看"
                            } else {
                                "换个关键词试试"
                            }
                        )
                    }
                } else {
                    items(
                        items = state.visiblePosts,
                        key = LocalCampusPost::id
                    ) { post ->
                        LocalCampusPostCard(
                            post = post,
                            onClick = { onPostClick(post.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalCampusTopBar(
    currentTab: Int,
    searchQuery: String,
    selectedCategory: String,
    showFavoritesOnly: Boolean,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onToggleFavorites: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.overviewPageBackgroundColor)
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园频道",
                    color = colors.primaryTextColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "简洁交流，仅保存在本机",
                    color = colors.secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.material3.IconButton(onClick = onToggleFavorites) {
                Icon(if (showFavoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "我的收藏", tint = colors.commonColor)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.bgButtonLowlightColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colors.commonColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        ImeCompatibleTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            placeholder = "搜索本地帖子或话题",
            showSearchIcon = true
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("全部") + com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules.tags) { category ->
                androidx.compose.material3.FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category) }
                )
            }
        }

        val tabs = listOf("最新", "热门")
        PrimaryTabRow(
            modifier = Modifier.fillMaxWidth(),
            selectedTabIndex = currentTab.coerceIn(tabs.indices),
            containerColor = colors.overviewPageBackgroundColor,
            contentColor = colors.primaryTextColor,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(currentTab.coerceIn(tabs.indices)),
                    color = colors.commonColor
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == index) colors.commonColor else colors.secondaryTextColor
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalCampusPostCard(
    post: LocalCampusPost,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgCardColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.bgButtonLowlightColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colors.commonColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "川农同学",
                        color = colors.primaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = post.title,
                color = colors.primaryTextColor,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(if (post.title.isEmpty()) 14.dp else 8.dp))
        Text(
            text = post.content,
            color = colors.primaryTextColor,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${post.tag}",
                color = colors.commonColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgButtonLowlightColor)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
            post.lostFound?.let { info ->
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "拾到物品 · ${info.status.label}",
                    color = colors.secondaryTextColor,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "查看详情",
                color = colors.secondaryTextColor,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.secondaryTextColor,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun LocalCampusMessageCard(
    title: String,
    message: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.bgCardColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = AppTheme.colors.primaryTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = AppTheme.colors.secondaryTextColor,
                lineHeight = 21.sp
            )
        }
    }
}

internal fun formatLocalCampusTime(timestamp: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(timestamp))

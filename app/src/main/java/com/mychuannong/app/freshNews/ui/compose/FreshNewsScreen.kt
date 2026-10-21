package com.mychuannong.app.freshNews.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.network.ApiResponse
import com.mychuannong.app.core.ads.AdPlacement
import com.mychuannong.app.core.ads.AdSlot
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.freshNews.contract.FreshNewsContract.Intent.FavoriteNews
import com.mychuannong.app.freshNews.contract.FreshNewsContract.Intent.LikeNews
import com.mychuannong.app.freshNews.contract.FreshNewsContract.Intent.OpenComments
import com.mychuannong.app.freshNews.contract.FreshNewsContract.Intent.RefreshNewsByTime
import com.mychuannong.app.freshNews.contract.FreshNewsContract.Intent.UpdateTabIndex
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItem
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItemResult
import com.mychuannong.app.freshNews.viewModel.FreshNewsViewModel
import kotlinx.coroutines.launch

@Composable
fun FreshNewsScreen(
    viewModel: FreshNewsViewModel,
    onPublishClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onUserClick: (Int) -> Unit,
    canInteract: Boolean,
    onAccountRequired: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val isLoading = state.freshNewsListResults is ApiResponse.Loading
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 5 && lastVisibleItemIndex >= totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoading && searchQuery.isBlank()) {
            viewModel.processIntent(RefreshNewsByTime(state.page + 1, 10))
        }
    }

    FreshNewsContent(
        currentTab = state.currentTab,
        searchQuery = searchQuery,
        freshNewsListResult = state.freshNewsListResults,
        listState = listState,
        showBackToTop = showBackToTop,
        onTabSelected = { index -> viewModel.processIntent(UpdateTabIndex(index)) },
        onSearchQueryChange = { searchQuery = it },
        onRefresh = { viewModel.processIntent(RefreshNewsByTime(1, 10)) },
        onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
        onPublishClick = onPublishClick,
        onImageClick = onImageClick,
        onUserClick = onUserClick,
        onLikeClick = { id ->
            if (canInteract) viewModel.processIntent(LikeNews(id)) else onAccountRequired()
        },
        onCommentClick = { item -> viewModel.processIntent(OpenComments(item)) },
        onFavoriteClick = { id ->
            if (canInteract) viewModel.processIntent(FavoriteNews(id)) else onAccountRequired()
        }
    )
}

@Composable
fun FreshNewsContent(
    modifier: Modifier = Modifier,
    currentTab: Int,
    searchQuery: String,
    freshNewsListResult: ApiResponse<List<FreshNewsItemResult>>,
    listState: LazyListState,
    showBackToTop: Boolean,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onScrollToTop: () -> Unit,
    onPublishClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onUserClick: (Int) -> Unit,
    onLikeClick: (Int) -> Unit,
    onCommentClick: (FreshNewsItem) -> Unit,
    onFavoriteClick: (Int) -> Unit
) {
    val colors = AppTheme.colors
    val isRefreshing = freshNewsListResult is ApiResponse.Loading
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = colors.overviewPageBackgroundColor,
        topBar = {
            NewsTopBar(
                currentTab = currentTab,
                searchQuery = searchQuery,
                onTabSelected = onTabSelected,
                onSearchQueryChange = onSearchQueryChange
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showBackToTop) {
                    SmallFloatingActionButton(
                        onClick = onScrollToTop,
                        containerColor = colors.bgCardColor,
                        contentColor = colors.commonColor,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "回到顶部")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                FloatingActionButton(
                    onClick = onPublishClick,
                    containerColor = colors.commonColor,
                    contentColor = colors.textButtonColor,
                    modifier = Modifier.padding(bottom = 80.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "发布帖子")
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overviewPageBackgroundColor),
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    containerColor = colors.bgCardColor,
                    color = colors.commonColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = 112.dp + paddingValues.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                when (freshNewsListResult) {
                    is ApiResponse.Loading -> items(3) { NewsSkeletonItem() }

                    is ApiResponse.Success -> {
                        val displayItems = freshNewsListResult.data.forCommunityDisplay(
                            query = searchQuery,
                            hot = currentTab == 1
                        )
                        if (displayItems.none { it is FreshNewsItemResult.Success }) {
                            item {
                                CommunityMessageCard(
                                    title = if (searchQuery.isBlank()) "还没有帖子" else "没有找到相关帖子",
                                    message = if (searchQuery.isBlank()) {
                                        "来发布校园频道的第一条内容吧"
                                    } else {
                                        "换个关键词试试"
                                    }
                                )
                            }
                        }
                        if (displayItems.any { it is FreshNewsItemResult.Success }) {
                            item(key = "community_ad_slot") {
                                AdSlot(
                                    placement = AdPlacement.COMMUNITY_FEED_INLINE,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(
                            items = displayItems,
                            key = { item ->
                                when (item) {
                                    is FreshNewsItemResult.Success -> item.freshNewsItem.freshNewsId
                                    FreshNewsItemResult.NoMore -> "no_more_footer_key"
                                    else -> item.hashCode()
                                }
                            }
                        ) { itemResult ->
                            when (itemResult) {
                                is FreshNewsItemResult.Success -> NewsItem(
                                    item = itemResult.freshNewsItem,
                                    onImageClick = onImageClick,
                                    onUserClick = onUserClick,
                                    onMenuClick = {},
                                    onLikeClick = onLikeClick,
                                    onCommentClick = onCommentClick,
                                    onShareClick = onFavoriteClick
                                )

                                FreshNewsItemResult.NoMore -> Text(
                                    text = "已经到底了",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    color = colors.secondaryTextColor,
                                    textAlign = TextAlign.Center
                                )

                                else -> Unit
                            }
                        }
                    }

                    is ApiResponse.Error -> item {
                        CommunityErrorCard(
                            message = freshNewsListResult.msg.ifBlank { "校园频道暂时无法连接" },
                            onRetry = onRefresh
                        )
                    }
                }
            }
        }
    }
}

private fun List<FreshNewsItemResult>.forCommunityDisplay(
    query: String,
    hot: Boolean
): List<FreshNewsItemResult> {
    val normalizedQuery = query.trim()
    val posts = filterIsInstance<FreshNewsItemResult.Success>()
        .filter { result ->
            normalizedQuery.isBlank() || with(result.freshNewsItem) {
                title.contains(normalizedQuery, ignoreCase = true) ||
                    content.contains(normalizedQuery, ignoreCase = true) ||
                    authorName.contains(normalizedQuery, ignoreCase = true) ||
                    tags.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }
        .let { filtered ->
            if (hot) {
                filtered.sortedByDescending { result ->
                    with(result.freshNewsItem) { liked * 2 + comments * 3 + favoritesCount }
                }
            } else {
                filtered
            }
        }

    return if (normalizedQuery.isBlank() && any { it is FreshNewsItemResult.NoMore }) {
        posts + FreshNewsItemResult.NoMore
    } else {
        posts
    }
}

@Composable
private fun CommunityMessageCard(title: String, message: String) {
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
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CommunityErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.bgCardColor
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "校园频道暂时不可用",
                color = AppTheme.colors.primaryTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = AppTheme.colors.secondaryTextColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onRetry) { Text("重新加载") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FreshNewsContentPreview() {
    AppSkinTheme {
        MaterialTheme {
            FreshNewsContent(
                currentTab = 0,
                searchQuery = "",
                freshNewsListResult = ApiResponse.Success(emptyList()),
                listState = rememberLazyListState(),
                showBackToTop = false,
                onTabSelected = {},
                onSearchQueryChange = {},
                onRefresh = {},
                onScrollToTop = {},
                onPublishClick = {},
                onImageClick = { _, _ -> },
                onUserClick = {},
                onLikeClick = {},
                onCommentClick = {},
                onFavoriteClick = {}
            )
        }
    }
}

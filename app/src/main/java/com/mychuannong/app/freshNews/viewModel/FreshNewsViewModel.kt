package com.mychuannong.app.freshNews.viewModel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.common.data.local.mmkv.UserInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.coroutineContext.ErrorCoroutineContext
import com.mychuannong.app.core.mvi.MviViewModel
import com.mychuannong.app.core.network.ApiResponse
import com.mychuannong.app.freshNews.contract.FreshNewsContract
import com.mychuannong.app.freshNews.data.local.mmkv.RefreshNewsCache
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItem
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItemResult
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsPublish
import com.mychuannong.app.freshNews.data.remote.repository.FreshNewsRepository
import com.mychuannong.app.freshNews.data.remote.repository.UserProfileRepository
import com.mychuannong.app.widget.view.CustomToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mychuannong.app.utils.event.AppEventBus
import java.io.File

class FreshNewsViewModel : MviViewModel<FreshNewsContract.Intent, FreshNewsContract.State>() {
    private val TAG = "FreshNewsViewModel"
    private val handler = Handler(Looper.getMainLooper())

    override fun processIntent(intent: FreshNewsContract.Intent) {
        when (intent) {
            is FreshNewsContract.Intent.InputMessage -> inputMessage(intent.value, intent.type)
            is FreshNewsContract.Intent.AddImage -> addImage(intent.file)
            is FreshNewsContract.Intent.RemoveImage -> removeImage(intent.index)
            is FreshNewsContract.Intent.Publish -> publish()
            is FreshNewsContract.Intent.LoadIp -> loadIp()
            is FreshNewsContract.Intent.ClearAll -> clearAll()
            is FreshNewsContract.Intent.RefreshNewsByTime -> refreshNewsByTime(
                intent.page,
                intent.pageSize
            )

            is FreshNewsContract.Intent.UpdateUserProfile -> refreshUserProfileByNetwork(intent.userId)
            is FreshNewsContract.Intent.UpdateTabIndex -> changeCurrentTab(intent.currentIndex)
            is FreshNewsContract.Intent.Initialization -> {}

            is FreshNewsContract.Intent.LikeNews -> likeNewsItem(intent.freshNewsId)
            is FreshNewsContract.Intent.FavoriteNews -> favoriteNewsItem(intent.freshNewsId)
            is FreshNewsContract.Intent.OpenComments -> openComments(intent.freshNewsItem)
            is FreshNewsContract.Intent.UpdateLocalCommentCount -> updateLocalCommentCount(
                intent.newsId,
                intent.count
            )

            is FreshNewsContract.Intent.UpdateLocalUserInfo -> updateLocalUserInfo(
                intent.userId,
                intent.name,
                intent.avatar
            )
        }

    }

    // 给State初始值
    override fun initialState() = FreshNewsContract.State(
        0,
        ApiResponse.Loading(),
        FreshNewsPublish(),
        mutableListOf(),
        false,
        0
    )

    private fun changeCurrentTab(currentTab: Int) {
        viewModelScope.launch(ErrorCoroutineContext()) {

        }
        updateState {
            copy(currentTab = currentTab)
        }
    }

    private fun inputMessage(value: Any, type: String) {
        updateState {
            when (type) {
                "title" -> state.value.publishNews.title = value as String
                "content" -> state.value.publishNews.content = value as String
                "tags" -> state.value.publishNews.tags = (value as String)
                    .split("，", ",", "#")
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .joinToString(",")
            }
            copy(
                isEnable = checkEnable()
            )
        }
    }

    private fun addImage(file: File) {
        updateState {
            state.value.images.add(file)
            copy()
        }
    }

    private fun removeImage(index: Int) {
        updateState {
            state.value.images.removeAt(index)
            copy()
        }
    }

    private fun checkEnable(): Boolean = with(state.value.publishNews) {
        val boolean = title.isNotEmpty() && content.isNotEmpty()
        boolean
    }

    private fun publish() {
        if (!requireCommunityAccount()) return
        viewModelScope.launch {
            state.value.publishNews.user_id = UserInfoManager.userId
            val result = FreshNewsRepository.Companion.instance.postFreshNews(
                state.value.images,
                state.value.publishNews
            )
            result.onEach { response ->
                when (response.code) {
                    "200" -> {
                        Log.d(TAG,"发送成功")
                        val tempFiles = state.value.images.toList()
                        clearAll()
                        cleanupTempFiles(tempFiles)
                        AppEventBus.freshNewsEvent.tryEmit(FreshNewsContract.Event.closePublish)    //关闭发布界面
                        AppEventBus.freshNewsEvent.tryEmit(FreshNewsContract.Event.RefreshNewsList) //刷新新鲜事列表
                        handler.post {
                            CustomToast.Companion.showMessage(
                                PlanetApplication.Companion.appContext,
                                "发布成功"
                            )
                        }
                    }

                    else -> {
                        Log.d(TAG,"发送失败")
                        handler.post {
                            CustomToast.Companion.showMessage(
                                PlanetApplication.Companion.appContext,
                                "发布失败"
                            )
                        }
                    }
                }
            }.launchIn(viewModelScope)
        }
    }

    private fun clearAll() {
        updateState {
            copy(
                images = mutableListOf(),
                isEnable = false,
                publishNews = FreshNewsPublish()
            )
        }
    }

    private fun refreshNewsByTime(page: Int, pageSize: Int) {
        viewModelScope.launch {
            val oldList = (state.value.freshNewsListResults as? ApiResponse.Success)?.data ?: emptyList()
            Log.d("wxy","${oldList}")

            if (page == 1) updateState { copy(freshNewsListResults = ApiResponse.Loading()) }

            val newsResult = FreshNewsRepository.Companion.instance.getNewsListByTime(page, pageSize)
            newsResult.collect { resource ->
                when (resource) {
                    is ApiResponse.Success -> {
                        val freshNewsItems = mutableListOf<FreshNewsItemResult>()
                        if (resource.data.isEmpty() && page > 1) {
                            // 如果是加载更多且没有数据，添加一个 no more 项
                            freshNewsItems.add(FreshNewsItemResult.NoMore)
                        }
                        else{
                            resource.data.forEach { freshNews ->
                                try {
                                    // 获取用户信息
                                    val userApiResponse = withContext(Dispatchers.IO) {
                                        UserProfileRepository.Companion.instance
                                            .getUserInformationById(freshNews.userId)
                                            .first { it is ApiResponse.Success || it is ApiResponse.Error }
                                    }

                                    val profile = when (userApiResponse) {
                                        is ApiResponse.Success -> userApiResponse.data
                                        else -> null
                                    }

                                    val localIsLiked = RefreshNewsCache.getLikeState(freshNews.freshNewsId)
                                    val localIsFavorited = RefreshNewsCache.getFavoriteState(freshNews.freshNewsId)
                                    val localLikeNum = RefreshNewsCache.getLikeNum(freshNews.freshNewsId)
                                    val localFavoriteNum = RefreshNewsCache.getFavoriteNum(freshNews.freshNewsId)

                                    var finalLikeNum = freshNews.liked ?: 0
                                    if (localLikeNum > finalLikeNum) {
                                        finalLikeNum = localLikeNum
                                    } else if (finalLikeNum > localLikeNum) {
                                        RefreshNewsCache.saveLikeNum(freshNews.freshNewsId, finalLikeNum)
                                    }

                                    var finalFavoriteNum = freshNews.favoritesCount ?: 0
                                    if (localFavoriteNum > finalFavoriteNum) {
                                        finalFavoriteNum = localFavoriteNum
                                    } else if (finalFavoriteNum > localFavoriteNum) {
                                        RefreshNewsCache.saveFavoriteNum(freshNews.freshNewsId, finalFavoriteNum)
                                    }

                                    // 构建最终的 FreshNewsItem
                                    val freshNewsItem = FreshNewsItem(
                                        freshNewsId = freshNews.freshNewsId,
                                        userId = profile?.userId ?: -1,
                                        authorName = profile?.account ?: "未知用户",
                                        authorAvatar = profile?.avatarUrl ?: "",
                                        title = freshNews.title,
                                        content = freshNews.content,
                                        images = when(freshNews.images){
                                            "图片正在审核中"-> listOf()
                                            ""-> listOf()
                                            else-> freshNews.images.split(",")
                                        },
                                        tags = when(freshNews.tags){
                                            ""-> listOf()
                                            else-> freshNews.tags.split(",")
                                        },
                                        liked = finalLikeNum,
                                        createTime = freshNews.createTime,
                                        comments = freshNews.comments,
                                        allowComments = freshNews.allowComments,
                                        favoritesCount = finalFavoriteNum,
                                        location = freshNews.address
                                    ).apply {
                                        isLiked = localIsLiked
                                        isFavorited = localIsFavorited
                                    }

                                    freshNewsItems.add(FreshNewsItemResult.Success(freshNewsItem))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    freshNewsItems.add(FreshNewsItemResult.Error(e.message?:"未知错误"))
                                    Log.e("FreshNews", "Error processing news item: ${e.message}")
                                    return@forEach
                                }
                            }
                        }

//                        Log.d("wxy","${freshNewsItems}")
                        updateState {
                            val mergedList = if (page == 1) {
                                // 下拉刷新：直接替换
                                freshNewsItems
                            } else {
                                // 加载更多：拼接旧列表
                                oldList + freshNewsItems
                            }

                            copy(
                                freshNewsListResults = ApiResponse.Success(mergedList),
                                page = page
                            )
                        }


                    }
                    is ApiResponse.Error -> {
                        //修改
                        Log.d("Trainer", "${PlanetApplication.Companion.isExpired}")

                        if (page == 1) updateState {
                            copy(
                                freshNewsListResults = ApiResponse.Error(
                                    resource.msg
                                )
                            )
                        }

                        if (!PlanetApplication.Companion.isExpired){
                            handler.post {
                                CustomToast.Companion.showMessage(
                                    PlanetApplication.Companion.appContext,
                                    "刷新失败: ${resource.msg}"
                                )
                            }
                        }

                    }
                    else -> {}
                }
            }
        }
    }

    private fun refreshUserProfileByNetwork(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            UserProfileRepository.Companion.instance.getUserInFormationNoCache(userId)
        }
    }

    private fun cleanupTempFiles(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) file.delete()
        }
    }


    // 点赞
    private fun likeNewsItem(freshNewsId: Int) {
        if (!requireCommunityAccount()) return
        viewModelScope.launch {
            // 在新的数据结构中需要先找到包含该 news 的 Success 项
            val currentItemResult = (state.value.freshNewsListResults as? ApiResponse.Success)?.data?.find { itemResult ->
                when (itemResult) {
                    is FreshNewsItemResult.Success -> itemResult.freshNewsItem.freshNewsId == freshNewsId
                    else -> false
                }
            } as? FreshNewsItemResult.Success

            val currentItem = currentItemResult?.freshNewsItem ?: return@launch
            val currentLikeCount = currentItem.liked
            val isCurrentlyLiked = currentItem.isLiked
            val newLikeState = !isCurrentlyLiked

            try {
                // 乐观更新状态
                updateState {
                    val updatedList = (state.value.freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                        when (itemResult) {
                            is FreshNewsItemResult.Success -> {
                                if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                    val newCount = if (newLikeState) currentLikeCount + 1 else currentLikeCount - 1
                                    val updatedItem = itemResult.freshNewsItem.copy(
                                        liked = newCount,
                                        isLiked = newLikeState
                                    ).apply {
                                        // 保存最新的数值到本地缓存
                                        RefreshNewsCache.saveLikeNum(freshNewsId, newCount)
                                    }
                                    FreshNewsItemResult.Success(updatedItem)
                                } else {
                                    itemResult
                                }
                            }
                            else -> itemResult
                        }
                    }

                    if (updatedList != null) {
                        copy(freshNewsListResults = ApiResponse.Success(updatedList))
                    } else {
                        this
                    }
                }

                // 保存点赞状态到 MMKV
                RefreshNewsCache.saveLikeState(freshNewsId, newLikeState)

                // 发送网络请求
                withContext(Dispatchers.IO) {

                    val result = FreshNewsRepository.Companion.instance.likeNews(freshNewsId)

                    result.collect { resource ->
                        withContext(Dispatchers.Main) {
                            when (resource) {
                                is ApiResponse.Success -> {
                                    val actionText =
                                        if (newLikeState) "已点赞" else "已取消点赞"
                                    handler.post {
                                        CustomToast.Companion.showMessage(
                                            PlanetApplication.Companion.appContext,
                                            actionText
                                        )
                                    }
                                }

                                is ApiResponse.Error -> {
                                    Log.d("FreshNewsVM", "点赞请求失败，回滚状态: id=$freshNewsId")
                                    updateState {
                                        val updatedList =
                                            (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                                                when (itemResult) {
                                                    is FreshNewsItemResult.Success -> {
                                                        if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                                            val updatedItem = itemResult.freshNewsItem.copy(
                                                                liked = currentLikeCount
                                                            ).apply {
                                                                isLiked = isCurrentlyLiked  // 回滚到原始状态
                                                            }
                                                            FreshNewsItemResult.Success(updatedItem)
                                                        } else {
                                                            itemResult
                                                        }
                                                    }
                                                    else -> itemResult
                                                }
                                            }

                                        if (updatedList != null) {
                                            copy(freshNewsListResults = ApiResponse.Success(updatedList))
                                        } else {
                                            this
                                        }
                                    }

                                    handler.post {
                                        CustomToast.Companion.showMessage(
                                            PlanetApplication.Companion.appContext,
                                            "操作失败: ${resource.msg}"
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FreshNewsVM", "点赞操作异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateState {
                        val updatedList = (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                            when (itemResult) {
                                is FreshNewsItemResult.Success -> {
                                    if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                        val updatedItem = itemResult.freshNewsItem.copy(
                                            liked = currentLikeCount
                                        ).apply {
                                            isLiked = isCurrentlyLiked
                                        }
                                        FreshNewsItemResult.Success(updatedItem)
                                    } else {
                                        itemResult
                                    }
                                }
                                else -> itemResult
                            }
                        }

                        if (updatedList != null) {
                            copy(freshNewsListResults = ApiResponse.Success(updatedList))
                        } else {
                            this
                        }
                    }

                    handler.post {
                        CustomToast.Companion.showMessage(
                            PlanetApplication.Companion.appContext,
                            "操作出错: ${e.message}"
                        )
                    }
                }
            }
        }
    }


    private fun favoriteNewsItem(freshNewsId: Int) {
        if (!requireCommunityAccount()) return
        viewModelScope.launch {
            // 保存初始状态
            val currentItemResult = (state.value.freshNewsListResults as? ApiResponse.Success)?.data?.find { itemResult ->
                when (itemResult) {
                    is FreshNewsItemResult.Success -> itemResult.freshNewsItem.freshNewsId == freshNewsId
                    else -> false
                }
            } as? FreshNewsItemResult.Success

            val freshNewsItem = currentItemResult?.freshNewsItem ?: return@launch
            val currentFavoriteCount = freshNewsItem.favoritesCount ?: 0
            val isCurrentlyFavorited = freshNewsItem.isFavorited
            // 计算新的收藏状态
            val newFavoriteState = !isCurrentlyFavorited

            try {
                // 乐观更新状态
                updateState {
                    val updatedList = (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                        when (itemResult) {
                            is FreshNewsItemResult.Success -> {
                                if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                    val newCount = if (newFavoriteState) currentFavoriteCount + 1 else currentFavoriteCount - 1
                                    val updatedItem = itemResult.freshNewsItem.copy(
                                        favoritesCount = newCount,
                                        isFavorited = newFavoriteState
                                    ).apply {
                                        // 使用计算好的新状态
                                        RefreshNewsCache.saveFavoriteNum(freshNewsId, favoritesCount)
                                    }
                                    FreshNewsItemResult.Success(updatedItem)
                                } else {
                                    itemResult
                                }
                            }
                            else -> itemResult
                        }
                    }

                    if (updatedList != null) {
                        copy(freshNewsListResults = ApiResponse.Success(updatedList))
                    } else {
                        this
                    }
                }


                // 保存收藏状态到 MMKV
                RefreshNewsCache.saveFavoriteState(freshNewsId, newFavoriteState)

                // 发送网络请求
                withContext(Dispatchers.IO) {
                    Log.d("FreshNewsVM", "发送收藏请求: id=$freshNewsId")
                    val result = FreshNewsRepository.Companion.instance.favoriteNews(freshNewsId, newFavoriteState)

                    result.collect { resource ->
                        withContext(Dispatchers.Main) {
                            when (resource) {
                                is ApiResponse.Success -> {
                                    // 请求成功，只显示 Toast，不需要更新状态（因为乐观更新已经处理了）
                                    val actionText =
                                        if (newFavoriteState) "已收藏" else "已取消收藏"
                                    handler.post {
                                        CustomToast.Companion.showMessage(
                                            PlanetApplication.Companion.appContext,
                                            actionText
                                        )
                                    }
                                }

                                is ApiResponse.Error -> {
                                    // 请求失败，回滚状态
                                    Log.d("FreshNewsVM", "收藏请求失败，回滚状态: id=$freshNewsId")
                                    updateState {
                                        val updatedList =
                                            (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                                                when (itemResult) {
                                                    is FreshNewsItemResult.Success -> {
                                                        if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                                            val updatedItem = itemResult.freshNewsItem.copy(
                                                                favoritesCount = currentFavoriteCount
                                                            ).apply {
                                                                isFavorited =
                                                                    isCurrentlyFavorited  // 回滚到原始状态
                                                            }
                                                            FreshNewsItemResult.Success(updatedItem)
                                                        } else {
                                                            itemResult
                                                        }
                                                    }
                                                    else -> itemResult
                                                }
                                            }
                                        if (updatedList != null) {
                                            copy(freshNewsListResults = ApiResponse.Success(updatedList))
                                        } else {
                                            this
                                        }
                                    }

                                    handler.post {
                                        CustomToast.Companion.showMessage(
                                            PlanetApplication.Companion.appContext,
                                            "操作失败: ${resource.msg}"
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FreshNewsVM", "收藏操作异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // 发生异常时回滚状态
                    updateState {
                        val updatedList = (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                            when (itemResult) {
                                is FreshNewsItemResult.Success -> {
                                    if (itemResult.freshNewsItem.freshNewsId == freshNewsId) {
                                        val updatedItem = itemResult.freshNewsItem.copy(
                                            favoritesCount = currentFavoriteCount
                                        ).apply {
                                            isFavorited = isCurrentlyFavorited  // 回滚到原始状态
                                        }
                                        FreshNewsItemResult.Success(updatedItem)
                                    } else {
                                        itemResult
                                    }
                                }
                                else -> itemResult
                            }
                        }

                        if (updatedList != null) {
                            copy(freshNewsListResults = ApiResponse.Success(updatedList))
                        } else {
                            this
                        }
                    }

                    handler.post {
                        CustomToast.Companion.showMessage(
                            PlanetApplication.Companion.appContext,
                            "操作出错: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun requireCommunityAccount(): Boolean {
        if (UserInfoManager.hasCommunitySession) return true
        handler.post {
            CustomToast.showMessage(
                PlanetApplication.appContext,
                "校园频道账号服务尚未配置，互动功能暂不可用"
            )
        }
        return false
    }
    private fun openComments(freshNewsItem: FreshNewsItem) {
        AppEventBus.currentFreshNews.resetReplayCache()
        AppEventBus.currentFreshNews.tryEmit(freshNewsItem)

        viewModelScope.launch {
            AppEventBus.freshNewsEvent.tryEmit(FreshNewsContract.Event.openComments)
        }

    }
    private fun loadIp() {
        // 地区/IP 应由自有后端按需推导，客户端不再调用第三方 IP 定位服务。
    }

    private fun updateLocalCommentCount(newsId: Int, count: Int) {
        updateState {
            val updatedList =
                (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                    when (itemResult) {
                        is FreshNewsItemResult.Success -> {
                            if (itemResult.freshNewsItem.freshNewsId == newsId) {
                                val updatedItem = itemResult.freshNewsItem.copy(
                                    comments = itemResult.freshNewsItem.comments + count
                                )
                                FreshNewsItemResult.Success(updatedItem)
                            } else {
                                itemResult
                            }
                        }

                        else -> itemResult
                    }
                }
            if (updatedList != null) {
                copy(freshNewsListResults = ApiResponse.Success(updatedList))
            } else {
                this
            }
        }
    }

    private fun updateLocalUserInfo(userId: Int, name: String, avatar: String) {
        updateState {
            val updatedList =
                (freshNewsListResults as? ApiResponse.Success)?.data?.map { itemResult ->
                    when (itemResult) {
                        is FreshNewsItemResult.Success -> {
                            if (itemResult.freshNewsItem.userId == userId) {
                                val updatedItem = itemResult.freshNewsItem.copy(
                                    authorName = name,
                                    authorAvatar = avatar
                                )
                                FreshNewsItemResult.Success(updatedItem)
                            } else {
                                itemResult
                            }
                        }

                        else -> itemResult
                    }
                }
            if (updatedList != null) {
                copy(freshNewsListResults = ApiResponse.Success(updatedList))
            } else {
                this
            }
        }
    }
}

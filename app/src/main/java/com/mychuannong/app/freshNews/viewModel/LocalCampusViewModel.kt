package com.mychuannong.app.freshNews.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.freshNews.data.local.mmkv.CampusCommunityLocalStore
import com.mychuannong.app.freshNews.data.local.model.LocalCampusDraft
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules
import com.mychuannong.app.freshNews.data.local.model.LocalCampusValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalCampusUiState(
    val posts: List<LocalCampusPost> = emptyList(),
    val currentTab: Int = 0,
    val selectedCategory: String = "全部",
    val showFavoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val scrollToTopRequest: Long = 0L
) {
    val visiblePosts: List<LocalCampusPost>
        get() {
            if (currentTab != 0) return emptyList()
            val query = searchQuery.trim()
            val categoryFiltered = posts.filter { post ->
                (selectedCategory == "全部" || post.tag == selectedCategory) &&
                    (!showFavoritesOnly || post.isFavorite)
            }
            if (query.isEmpty()) return categoryFiltered
            return categoryFiltered.filter { post ->
                post.title.contains(query, ignoreCase = true) ||
                    post.content.contains(query, ignoreCase = true) ||
                    post.tag.contains(query, ignoreCase = true)
            }
        }
}

class LocalCampusViewModel : ViewModel() {
    private val store = CampusCommunityLocalStore
    private val _uiState = MutableStateFlow(
        LocalCampusUiState(posts = store.posts.value)
    )
    val uiState: StateFlow<LocalCampusUiState> = _uiState.asStateFlow()

    init {
        store.posts
            .onEach { posts -> _uiState.update { it.copy(posts = posts) } }
            .launchIn(viewModelScope)
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(currentTab = index.coerceIn(0, 1)) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, refreshError = null) }
    }

    fun selectCategory(category: String) { _uiState.update { it.copy(selectedCategory = category) } }

    fun toggleFavoritesOnly() { _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) } }

    fun showLatestPostsAfterPublish() {
        _uiState.update {
            it.copy(
                currentTab = 0,
                searchQuery = "",
                refreshError = null,
                scrollToTopRequest = it.scrollToTopRequest + 1L
            )
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshError = null) }
            val refreshed = runCatching {
                withContext(Dispatchers.IO) { store.reload() }
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    refreshError = if (refreshed) null else "本地内容暂时无法读取，请稍后重试"
                )
            }
        }
    }
}

data class LocalCampusPublishUiState(
    val title: String = "",
    val content: String = "",
    val tag: String = LocalCampusPostRules.DEFAULT_TAG,
    val storageError: String? = null
) {
    val hasContent: Boolean
        get() = title.isNotBlank() || content.isNotBlank()

    val validationError: LocalCampusValidationError?
        get() = LocalCampusPostRules.validate(title, content, tag)

    val canPublish: Boolean
        get() = validationError == null
}

class LocalCampusPublishViewModel : ViewModel() {
    private val store = CampusCommunityLocalStore
    private val _uiState = MutableStateFlow(store.draft.value.toPublishState())
    val uiState: StateFlow<LocalCampusPublishUiState> = _uiState.asStateFlow()

    fun updateTitle(value: String) {
        updateDraft(title = value.take(LocalCampusPostRules.MAX_TITLE_LENGTH))
    }

    fun updateContent(value: String) {
        updateDraft(content = value.take(LocalCampusPostRules.MAX_CONTENT_LENGTH))
    }

    fun updateTag(value: String) {
        updateDraft(tag = LocalCampusPostRules.normalizedTag(value))
    }

    fun publish(): Boolean {
        val state = _uiState.value
        if (!state.canPublish) return false
        return runCatching {
            store.publish(
                title = state.title,
                content = state.content,
                tag = state.tag
            )
        }.fold(
            onSuccess = {
                _uiState.value = LocalCampusPublishUiState()
                true
            },
            onFailure = {
                _uiState.update { current ->
                    current.copy(storageError = "本地保存失败，请稍后重试")
                }
                false
            }
        )
    }

    fun discardDraft() {
        runCatching { store.clearDraft() }
        _uiState.value = LocalCampusPublishUiState()
    }

    fun keepDraft(): Boolean {
        val state = _uiState.value
        if (!state.hasContent) return true
        return runCatching {
            store.saveDraft(state.title, state.content, state.tag)
        }.fold(
            onSuccess = { true },
            onFailure = {
                _uiState.update { current ->
                    current.copy(storageError = "草稿暂时无法保存")
                }
                false
            }
        )
    }

    private fun updateDraft(
        title: String = _uiState.value.title,
        content: String = _uiState.value.content,
        tag: String = _uiState.value.tag
    ) {
        val next = LocalCampusPublishUiState(
            title = title,
            content = content,
            tag = tag
        )
        _uiState.value = next
        runCatching { store.saveDraft(title, content, tag) }
            .onFailure {
                _uiState.update { current ->
                    current.copy(storageError = "草稿暂时无法保存")
                }
            }
    }

    private fun LocalCampusDraft.toPublishState() = LocalCampusPublishUiState(
        title = title,
        content = content,
        tag = LocalCampusPostRules.normalizedTag(tag)
    )
}

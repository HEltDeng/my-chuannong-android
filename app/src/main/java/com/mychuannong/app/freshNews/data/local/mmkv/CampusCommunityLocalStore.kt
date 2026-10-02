package com.mychuannong.app.freshNews.data.local.mmkv

import com.mychuannong.app.freshNews.data.local.model.LocalCampusDraft
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules
import com.mychuannong.app.freshNews.data.local.model.LocalLostFoundInfo
import com.mychuannong.app.freshNews.data.local.model.LostFoundStatus
import com.tencent.mmkv.MMKV
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-only persistence for the campus channel MVP.
 *
 * This store deliberately has no dependency on a network client, account state or the old community
 * repositories. Clearing app data or uninstalling the app removes both posts and drafts.
 */
object CampusCommunityLocalStore {
    private const val CACHE_ID = "campus_channel_local_v1"
    private const val KEY_POSTS = "posts_v1"
    private const val KEY_DRAFT = "draft_v1"

    private val lock = Any()
    private val codec = CampusCommunityCodec()
    private val mmkv: MMKV? by lazy {
        runCatching { MMKV.mmkvWithID(CACHE_ID) }.getOrNull()
    }

    private val _posts = MutableStateFlow<List<LocalCampusPost>>(emptyList())
    private val _draft = MutableStateFlow(LocalCampusDraft())
    private val exposedPosts = _posts.asStateFlow()
    private val exposedDraft = _draft.asStateFlow()

    @Volatile
    private var loaded = false

    val posts: StateFlow<List<LocalCampusPost>>
        get() {
            ensureLoaded()
            return exposedPosts
        }

    val draft: StateFlow<LocalCampusDraft>
        get() {
            ensureLoaded()
            return exposedDraft
        }

    fun reload(): Boolean {
        synchronized(lock) {
            return loadSnapshotLocked()
        }
    }

    fun publish(
        title: String,
        content: String,
        tag: String,
        id: String = UUID.randomUUID().toString(),
        createdAtMillis: Long = System.currentTimeMillis(),
        linkedActivityId: String? = null,
        lostFound: LocalLostFoundInfo? = null,
    ): LocalCampusPost {
        check(ensureLoaded()) { "Local campus storage could not be loaded" }
        val post = LocalCampusPostRules.createPost(
            id = id,
            title = title,
            content = content,
            tag = tag,
            createdAtMillis = createdAtMillis,
            linkedActivityId = linkedActivityId
        ).copy(lostFound = lostFound)

        synchronized(lock) {
            val storage = requireStorage()
            val updated = (listOf(post) + _posts.value)
                .distinctBy(LocalCampusPost::id)
                .sortedByDescending(LocalCampusPost::createdAtMillis)
            check(storage.encode(KEY_POSTS, codec.encodePosts(updated))) {
                "Unable to save local campus post"
            }
            _posts.value = updated
            clearDraftLocked()
        }
        return post
    }

    fun saveDraft(
        title: String,
        content: String,
        tag: String,
        updatedAtMillis: Long = System.currentTimeMillis()
    ) {
        check(ensureLoaded()) { "Local campus storage could not be loaded" }
        val draft = LocalCampusDraft(
            title = title.take(LocalCampusPostRules.MAX_TITLE_LENGTH),
            content = content.take(LocalCampusPostRules.MAX_CONTENT_LENGTH),
            tag = LocalCampusPostRules.normalizedTag(tag),
            updatedAtMillis = updatedAtMillis.coerceAtLeast(0L)
        )

        synchronized(lock) {
            val storage = requireStorage()
            if (draft.hasContent) {
                check(storage.encode(KEY_DRAFT, codec.encodeDraft(draft))) {
                    "Unable to save local campus draft"
                }
                _draft.value = draft
            } else {
                clearDraftLocked()
            }
        }
    }

    fun clearDraft() {
        check(ensureLoaded()) { "Local campus storage could not be loaded" }
        synchronized(lock) { clearDraftLocked() }
    }

    fun findPost(id: String): LocalCampusPost? {
        if (!ensureLoaded()) return null
        return _posts.value.firstOrNull { it.id == id }
    }

    fun toggleFavorite(id: String): LocalCampusPost? = updatePost(id) { it.copy(isFavorite = !it.isFavorite) }

    fun publishLostFound(
        info: LocalLostFoundInfo,
        id: String = UUID.randomUUID().toString(),
        createdAtMillis: Long = System.currentTimeMillis(),
    ): LocalCampusPost = publish(
        title = info.itemName,
        content = lostFoundContent(info),
        tag = "失物招领",
        id = id,
        createdAtMillis = createdAtMillis,
        lostFound = info.copy(updatedAtMillis = createdAtMillis, pendingSync = true),
    )

    fun updateLostFoundStatus(id: String, status: LostFoundStatus): LocalCampusPost? =
        updatePost(id) { post ->
            val info = post.lostFound ?: return@updatePost post
            if (!info.isLocalPublisher) post else post.copy(
                lostFound = info.copy(
                    status = status,
                    updatedAtMillis = System.currentTimeMillis(),
                    pendingSync = true,
                )
            )
        }

    fun updateLostFound(id: String, info: LocalLostFoundInfo): LocalCampusPost? =
        updatePost(id) { post ->
            val current = post.lostFound ?: return@updatePost post
            if (!current.isLocalPublisher) {
                post
            } else {
                post.copy(
                    title = info.itemName.trim(),
                    content = lostFoundContent(info),
                    lostFound = info.copy(
                        isLocalPublisher = true,
                        updatedAtMillis = System.currentTimeMillis(),
                        pendingSync = true,
                    ),
                )
            }
        }

    fun submitLocalClaim(id: String): LocalCampusPost? = updatePost(id) { post ->
        val info = post.lostFound ?: return@updatePost post
        if (info.status == LostFoundStatus.RETURNED) post else post.copy(
            lostFound = info.copy(hasLocalClaimRequest = true, updatedAtMillis = System.currentTimeMillis())
        )
    }

    fun deleteLocalPost(id: String): Boolean {
        if (!ensureLoaded()) return false
        synchronized(lock) {
            val current = _posts.value
            val updated = current.filterNot { it.id == id && it.linkedActivityId == null }
            if (updated.size == current.size) return false
            val storage = requireStorage()
            if (!storage.encode(KEY_POSTS, codec.encodePosts(updated))) return false
            _posts.value = updated
            return true
        }
    }

    fun recordReport(id: String, reason: String): Boolean {
        if (!ensureLoaded() || findPost(id) == null || reason.isBlank()) return false
        return runCatching { requireStorage().encode("report_${id}", reason.take(80)); true }.getOrDefault(false)
    }

    fun replaceLostFoundPosts(lostFoundPosts: List<LocalCampusPost>): Boolean {
        if (!ensureLoaded()) return false
        synchronized(lock) {
            val nonLostFound = _posts.value.filter {
                it.lostFound == null && it.tag != "失物招领"
            }
            val updated = (lostFoundPosts + nonLostFound)
                .distinctBy(LocalCampusPost::id)
                .sortedByDescending(LocalCampusPost::createdAtMillis)
            if (!requireStorage().encode(KEY_POSTS, codec.encodePosts(updated))) return false
            _posts.value = updated
            return true
        }
    }

    private fun updatePost(id: String, transform: (LocalCampusPost) -> LocalCampusPost): LocalCampusPost? {
        if (!ensureLoaded()) return null
        synchronized(lock) {
            val current = _posts.value
            val target = current.firstOrNull { it.id == id } ?: return null
            val updatedTarget = transform(target)
            val updated = current.map { if (it.id == id) updatedTarget else it }
            if (!requireStorage().encode(KEY_POSTS, codec.encodePosts(updated))) return null
            _posts.value = updated
            return updatedTarget
        }
    }

    private fun lostFoundContent(info: LocalLostFoundInfo): String = buildString {
        val action = if (info.effectiveType ==
            com.mychuannong.app.freshNews.data.local.model.LostFoundType.LOST
        ) {
            "遗失"
        } else {
            "拾取"
        }
        append("${action}地点：${info.location}\n${action}日期：${info.foundDate}")
        info.approximateTime?.takeIf(String::isNotBlank)?.let { append(" · $it") }
        info.description.takeIf(String::isNotBlank)?.let { append("\n物品特征：$it") }
        info.claimNote.takeIf(String::isNotBlank)?.let { append("\n补充说明：$it") }
    }

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        synchronized(lock) {
            if (loaded) return true
            return loadSnapshotLocked()
        }
    }

    private fun clearDraftLocked() {
        val storage = requireStorage()
        val removed = runCatching {
            storage.removeValueForKey(KEY_DRAFT)
            !storage.containsKey(KEY_DRAFT)
        }.getOrDefault(false)
        check(removed) {
            "Unable to remove local campus draft"
        }
        _draft.value = LocalCampusDraft()
    }

    private fun loadSnapshotLocked(): Boolean {
        val storage = mmkv ?: return false
        val snapshot = runCatching {
                codec.decodePosts(storage.decodeString(KEY_POSTS)) to
                    codec.decodeDraft(storage.decodeString(KEY_DRAFT))
            }.getOrNull() ?: return false
        _posts.value = snapshot.first
        _draft.value = snapshot.second
        loaded = true
        return true
    }

    private fun requireStorage(): MMKV = checkNotNull(mmkv) {
        "Local campus storage is unavailable"
    }
}

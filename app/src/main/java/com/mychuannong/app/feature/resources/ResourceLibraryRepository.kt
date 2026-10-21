package com.mychuannong.app.feature.resources

import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.network.campustools.CampusToolsApiException
import com.mychuannong.app.core.network.campustools.CampusToolsApiClient
import com.mychuannong.app.core.network.campustools.RemoteResource
import com.mychuannong.app.core.network.campustools.ResourceComplaintRequest
import com.mychuannong.app.core.network.campustools.authenticatedCall
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResourceListMode { ALL, FAVORITES, RECENT }

internal fun shouldUseResourceOfflineCache(error: Throwable): Boolean =
    error !is CampusToolsApiException || error.errorCode != "ACCOUNT_OPERATION_RESTRICTED"

internal object ResourceLibraryFilters {
    fun apply(
        resources: List<RemoteResource>,
        query: String,
        category: String?,
        mode: ResourceListMode,
    ): List<RemoteResource> = resources.filter { resource ->
        val modeMatch = when (mode) {
            ResourceListMode.ALL -> true
            ResourceListMode.FAVORITES -> resource.isFavorite
            ResourceListMode.RECENT -> resource.lastViewedAt != null
        }
        val categoryMatch = category == null || resource.category == category
        val queryMatch = query.isBlank() || buildString {
            append(resource.title)
            append(resource.summary)
            append(resource.tags.joinToString())
        }.contains(query, ignoreCase = true)
        modeMatch && categoryMatch && queryMatch
    }.let { rows ->
        if (mode == ResourceListMode.RECENT) {
            rows.sortedByDescending(RemoteResource::lastViewedAt)
        } else {
            rows
        }
    }
}

object ResourceLibraryRepository {
    private const val STORE_ID = "resource_library_cache_v1"
    private val store by lazy { MMKV.mmkvWithID(STORE_ID) }
    private val gson = Gson()
    private val mutableItems = MutableStateFlow<List<RemoteResource>>(emptyList())
    val items: StateFlow<List<RemoteResource>> = mutableItems.asStateFlow()

    suspend fun refresh(
        query: String = "",
        category: String? = null,
        mode: ResourceListMode = ResourceListMode.ALL,
    ): Result<List<RemoteResource>> = runCatching {
        val result = if (CampusToolsSession.currentUser == null) {
            CampusToolsApiClient.unauthenticated.resources(
                category = category,
                query = query.takeIf(String::isNotBlank),
            ).data ?: emptyList()
        } else authenticatedCall {
            it.resources(
                category = category,
                query = query.takeIf(String::isNotBlank),
                favorites = (mode == ResourceListMode.FAVORITES).takeIf { it },
                recent = (mode == ResourceListMode.RECENT).takeIf { it },
            )
        }
        mutableItems.value = result
        if (mode == ResourceListMode.ALL && query.isBlank() && category == null) {
            saveCache(result)
        }
        result
    }.recoverCatching { cause ->
        if (!shouldUseResourceOfflineCache(cause)) {
            mutableItems.value = emptyList()
            throw cause
        }
        val cached = ResourceLibraryFilters.apply(loadCache(), query, category, mode)
        if (cached.isEmpty()) throw cause
        mutableItems.value = cached
        cached
    }

    suspend fun toggleFavorite(resourceId: String): Result<Boolean> = runCatching {
        val result = authenticatedCall { it.toggleResourceFavorite(resourceId) }
        mutableItems.value = mutableItems.value.map {
            if (it.id == resourceId) it.copy(isFavorite = result.isFavorite) else it
        }
        mergeCache(resourceId) { it.copy(isFavorite = result.isFavorite) }
        result.isFavorite
    }

    suspend fun recordView(resourceId: String): Result<Unit> = runCatching {
        val result = authenticatedCall { it.recordResourceView(resourceId) }
        val viewedAt = result.viewedAt ?: System.currentTimeMillis()
        mutableItems.value = mutableItems.value.map {
            if (it.id == resourceId) {
                it.copy(viewCount = it.viewCount + 1, lastViewedAt = viewedAt)
            } else {
                it
            }
        }
        mergeCache(resourceId) {
            it.copy(viewCount = it.viewCount + 1, lastViewedAt = viewedAt)
        }
    }

    suspend fun submitComplaint(
        resourceId: String,
        reason: String,
        detail: String,
    ): Result<Unit> = runCatching {
        authenticatedCall {
            it.complainResource(
                resourceId,
                ResourceComplaintRequest(reason.trim(), detail.trim()),
            )
        }
        Unit
    }

    private fun cacheKey(): String =
        "resources_${CampusToolsSession.currentUser?.id ?: "signed_out"}"

    private fun loadCache(): List<RemoteResource> = runCatching {
        gson.fromJson(store.decodeString(cacheKey()), Array<RemoteResource>::class.java)
            ?.toList()
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun saveCache(value: List<RemoteResource>) {
        check(store.encode(cacheKey(), gson.toJson(value.distinctBy(RemoteResource::id))))
    }

    private fun mergeCache(
        resourceId: String,
        transform: (RemoteResource) -> RemoteResource,
    ) {
        saveCache(loadCache().map {
            if (it.id == resourceId) transform(it) else it
        })
    }
}

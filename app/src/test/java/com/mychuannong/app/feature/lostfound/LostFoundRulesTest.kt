package com.mychuannong.app.feature.lostfound

import com.mychuannong.app.feature.lostfound.data.LostFoundListFilter
import com.mychuannong.app.feature.lostfound.data.LostFoundRules
import com.mychuannong.app.feature.lostfound.data.shouldUseLostFoundOfflineCache
import com.mychuannong.app.core.network.campustools.CampusToolsApiException
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalLostFoundInfo
import com.mychuannong.app.freshNews.data.local.model.LostFoundCategory
import com.mychuannong.app.freshNews.data.local.model.LostFoundStatus
import com.mychuannong.app.freshNews.data.local.model.LostFoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFoundRulesTest {
    @Test
    fun `legacy lost-found payload defaults to found type`() {
        val post = post("legacy", type = null, favorite = true)

        val result = LostFoundRules.filter(
            posts = listOf(post),
            type = LostFoundListFilter.FOUND,
            query = "",
            favoritesOnly = true,
        )

        assertEquals(listOf("legacy"), result.map(LocalCampusPost::id))
        assertEquals("等待认领", LostFoundRules.statusLabel(post.lostFound!!))
    }

    @Test
    fun `search covers structured item location category and description`() {
        val lost = post("lost", LostFoundType.LOST)
        val ordinary = LocalCampusPost(
            id = "ordinary",
            title = "图书馆",
            content = "蓝色耳机",
            tag = "校园动态",
            createdAtMillis = 1L,
        )

        listOf("蓝色耳机", "图书馆", "电子设备", "保护壳").forEach { query ->
            assertEquals(
                query,
                listOf("lost"),
                LostFoundRules.filter(
                    listOf(ordinary, lost),
                    LostFoundListFilter.ALL,
                    query,
                ).map(LocalCampusPost::id),
            )
        }
    }

    @Test
    fun `lost and found status labels remain type specific`() {
        val lost = post("lost", LostFoundType.LOST).lostFound!!
        val found = post("found", LostFoundType.FOUND).lostFound!!

        assertEquals("寻找中", LostFoundRules.statusLabel(lost))
        assertEquals("已找回", LostFoundRules.statusLabel(lost.copy(status = LostFoundStatus.FOUND)))
        assertEquals("等待认领", LostFoundRules.statusLabel(found))
        assertEquals("已归还", LostFoundRules.statusLabel(found.copy(status = LostFoundStatus.RETURNED)))
    }

    @Test
    fun `resolved posts leave ordinary lists and enter claimed list`() {
        val waiting = post("waiting", LostFoundType.FOUND)
        val claimed = post("claimed", LostFoundType.FOUND, status = LostFoundStatus.CLAIMED)
        val returned = post("returned", LostFoundType.FOUND, status = LostFoundStatus.RETURNED)
        val recovered = post("recovered", LostFoundType.LOST, status = LostFoundStatus.FOUND)
        val posts = listOf(waiting, claimed, returned, recovered)

        assertEquals(
            listOf("waiting"),
            LostFoundRules.filter(posts, LostFoundListFilter.ALL, "").map(LocalCampusPost::id),
        )
        assertEquals(
            setOf("claimed", "returned", "recovered"),
            LostFoundRules.filter(posts, LostFoundListFilter.CLAIMED, "")
                .map(LocalCampusPost::id)
                .toSet(),
        )
    }

    @Test
    fun `successful server snapshot removes records that only remain in cache`() {
        val active = post("server-active", LostFoundType.FOUND)
        val deletedCache = post("admin-deleted", LostFoundType.FOUND)

        assertEquals(
            listOf("server-active"),
            LostFoundRules.synchronizedSnapshot(
                serverPosts = listOf(active),
                cachedPosts = listOf(active, deletedCache),
            ).map(LocalCampusPost::id),
        )
        assertEquals(
            setOf("server-active", "admin-deleted"),
            LostFoundRules.synchronizedSnapshot(
                serverPosts = null,
                cachedPosts = listOf(active, deletedCache),
            ).map(LocalCampusPost::id).toSet(),
        )
    }

    @Test
    fun `restricted account rejection discards only unsynced local creation`() {
        assertTrue(
            LostFoundRules.shouldDiscardRejectedPending(
                errorCode = "ACCOUNT_OPERATION_RESTRICTED",
                pendingSync = true,
                hasRemoteId = false,
            ),
        )
        assertFalse(
            LostFoundRules.shouldDiscardRejectedPending(
                errorCode = "ACCOUNT_OPERATION_RESTRICTED",
                pendingSync = true,
                hasRemoteId = true,
            ),
        )
        assertFalse(
            LostFoundRules.shouldDiscardRejectedPending(
                errorCode = "NETWORK_ERROR",
                pendingSync = true,
                hasRemoteId = false,
            ),
        )
    }

    @Test
    fun `restricted account never receives offline lost-found cache`() {
        assertFalse(shouldUseLostFoundOfflineCache(
            CampusToolsApiException(403, "ACCOUNT_OPERATION_RESTRICTED", "账号受限"),
        ))
        assertTrue(shouldUseLostFoundOfflineCache(IllegalStateException("网络断开")))
    }

    private fun post(
        id: String,
        type: LostFoundType?,
        favorite: Boolean = false,
        status: LostFoundStatus? = null,
    ) = LocalCampusPost(
        id = id,
        title = "蓝色耳机",
        content = "物品信息",
        tag = "失物招领",
        createdAtMillis = 2L,
        isFavorite = favorite,
        lostFound = LocalLostFoundInfo(
            itemName = "蓝色耳机",
            category = LostFoundCategory.ELECTRONIC,
            location = "图书馆",
            foundDate = "2026-07-28",
            description = "带透明保护壳",
            type = type,
            status = status ?: if (type == LostFoundType.LOST) {
                LostFoundStatus.SEARCHING
            } else {
                LostFoundStatus.WAITING
            },
        ),
    )
}

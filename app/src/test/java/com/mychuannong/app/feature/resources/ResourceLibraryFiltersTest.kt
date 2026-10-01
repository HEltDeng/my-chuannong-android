package com.mychuannong.app.feature.resources

import com.mychuannong.app.core.network.campustools.RemoteResource
import com.mychuannong.app.core.network.campustools.CampusToolsApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceLibraryFiltersTest {
    private val rows = listOf(
        RemoteResource(
            id = "course",
            title = "高等数学复习",
            summary = "极限与导数",
            category = "课程资料",
            tags = listOf("数学", "大一"),
            externalUrl = "https://example.org/course",
            isFavorite = true,
            lastViewedAt = 100,
        ),
        RemoteResource(
            id = "exam",
            title = "考试安排说明",
            summary = "诚信考试",
            category = "考试资料",
            tags = listOf("通知"),
            externalUrl = "https://example.org/exam",
            lastViewedAt = 200,
        ),
    )

    @Test
    fun `offline keyword filter checks title summary and tags while keeping category`() {
        assertEquals(
            listOf("course"),
            ResourceLibraryFilters.apply(
                rows,
                query = "大一",
                category = "课程资料",
                mode = ResourceListMode.ALL,
            ).map(RemoteResource::id),
        )
        assertEquals(
            emptyList<String>(),
            ResourceLibraryFilters.apply(
                rows,
                query = "考试",
                category = "课程资料",
                mode = ResourceListMode.ALL,
            ).map(RemoteResource::id),
        )
    }

    @Test
    fun `favorite and recent filters stay account scoped and recent is newest first`() {
        assertEquals(
            listOf("course"),
            ResourceLibraryFilters.apply(
                rows,
                query = "",
                category = null,
                mode = ResourceListMode.FAVORITES,
            ).map(RemoteResource::id),
        )
        assertEquals(
            listOf("exam", "course"),
            ResourceLibraryFilters.apply(
                rows,
                query = "",
                category = null,
                mode = ResourceListMode.RECENT,
            ).map(RemoteResource::id),
        )
    }

    @Test
    fun `restricted account never receives offline resource cache`() {
        assertFalse(shouldUseResourceOfflineCache(
            CampusToolsApiException(403, "ACCOUNT_OPERATION_RESTRICTED", "账号受限"),
        ))
        assertTrue(shouldUseResourceOfflineCache(IllegalStateException("网络断开")))
    }
}

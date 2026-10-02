package com.mychuannong.app.freshNews.data.local.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFoundPostModelTest {
    @Test fun `lost-found tag is retained by channel rules`() {
        assertEquals("失物招领", LocalCampusPostRules.normalizedTag("失物招领"))
        assertEquals("校园动态", LocalCampusPostRules.normalizedTag("missing-tag"))
    }

    @Test fun `ordinary legacy post has no lost-found payload`() {
        val post = LocalCampusPostRules.createPost("post-1", "普通内容", "正文", "校园动态", 1L)
        assertNull(post.lostFound)
    }

    @Test fun `lost-found payload carries a single local state`() {
        val info = LocalLostFoundInfo(
            itemName = "校园卡", category = LostFoundCategory.CARD,
            location = "图书馆", foundDate = "2026-07-22"
        )
        assertEquals(LostFoundStatus.WAITING, info.status)
        assertEquals("证件 / 校园卡", info.category.label)
        assertEquals("等待认领", info.status.label)
        assertEquals(LostFoundType.FOUND, info.effectiveType)
    }

    @Test fun `new lost item carries searching state`() {
        val info = LocalLostFoundInfo(
            itemName = "雨伞",
            category = LostFoundCategory.UMBRELLA,
            location = "教学楼",
            foundDate = "2026-07-28",
            type = LostFoundType.LOST,
            status = LostFoundStatus.SEARCHING,
        )

        assertEquals(LostFoundType.LOST, info.effectiveType)
        assertEquals("寻找中", info.status.label)
    }
}

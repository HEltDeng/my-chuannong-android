package com.mychuannong.app.freshNews.data.local.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCampusPostRulesTest {
    @Test
    fun `title is optional while body is required`() {
        assertNull(
            LocalCampusPostRules.validate(
                title = "",
                content = "本地测试内容",
                tag = LocalCampusPostRules.DEFAULT_TAG
            )
        )
        assertEquals(
            LocalCampusValidationError.CONTENT_REQUIRED,
            LocalCampusPostRules.validate(
                title = "可选标题",
                content = "   ",
                tag = LocalCampusPostRules.DEFAULT_TAG
            )
        )
    }

    @Test
    fun `length limits are enforced`() {
        assertNull(
            LocalCampusPostRules.validate(
                title = "题".repeat(LocalCampusPostRules.MAX_TITLE_LENGTH),
                content = "文".repeat(LocalCampusPostRules.MAX_CONTENT_LENGTH),
                tag = LocalCampusPostRules.DEFAULT_TAG
            )
        )
        assertEquals(
            LocalCampusValidationError.TITLE_TOO_LONG,
            LocalCampusPostRules.validate(
                title = "题".repeat(LocalCampusPostRules.MAX_TITLE_LENGTH + 1),
                content = "正文",
                tag = LocalCampusPostRules.DEFAULT_TAG
            )
        )
        assertEquals(
            LocalCampusValidationError.CONTENT_TOO_LONG,
            LocalCampusPostRules.validate(
                title = "",
                content = "文".repeat(LocalCampusPostRules.MAX_CONTENT_LENGTH + 1),
                tag = LocalCampusPostRules.DEFAULT_TAG
            )
        )
    }

    @Test
    fun `only fixed local tags are accepted`() {
        LocalCampusPostRules.tags.forEach { tag ->
            assertNull(LocalCampusPostRules.validate("", "正文", tag))
        }
        assertEquals(
            LocalCampusValidationError.INVALID_TAG,
            LocalCampusPostRules.validate("", "正文", "远端分类")
        )
        assertEquals(
            LocalCampusPostRules.DEFAULT_TAG,
            LocalCampusPostRules.normalizedTag("未知分类")
        )
    }

    @Test
    fun `created post is trimmed without changing its identity`() {
        val post = LocalCampusPostRules.createPost(
            id = "local-test-id",
            title = "  本地标题  ",
            content = "  本地正文  ",
            tag = "学习交流",
            createdAtMillis = 100L
        )

        assertEquals("local-test-id", post.id)
        assertEquals("本地标题", post.title)
        assertEquals("本地正文", post.content)
        assertEquals("学习交流", post.tag)
        assertEquals(100L, post.createdAtMillis)
    }
}

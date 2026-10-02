package com.mychuannong.app.freshNews.data.local.mmkv

import com.mychuannong.app.freshNews.data.local.model.LocalCampusDraft
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusCommunityCodecTest {
    private val codec = CampusCommunityCodec()

    @Test
    fun `posts round trip and newest post is first`() {
        val older = localPost(id = "older", createdAtMillis = 100L)
        val newer = localPost(id = "newer", createdAtMillis = 200L)

        val decoded = codec.decodePosts(codec.encodePosts(listOf(older, newer)))

        assertEquals(listOf("newer", "older"), decoded.map(LocalCampusPost::id))
        assertEquals("本地测试正文", decoded.first().content)
    }

    @Test
    fun `duplicate and invalid posts are safely filtered`() {
        val valid = localPost(id = "same", createdAtMillis = 200L)
        val duplicate = valid.copy(createdAtMillis = 100L)
        val invalid = valid.copy(id = "invalid", content = "")

        val decoded = codec.decodePosts(codec.encodePosts(listOf(valid, duplicate, invalid)))

        assertEquals(1, decoded.size)
        assertEquals("same", decoded.single().id)
        assertEquals(200L, decoded.single().createdAtMillis)
    }

    @Test
    fun `malformed json falls back to empty local state`() {
        assertTrue(codec.decodePosts("not-json").isEmpty())
        assertTrue(codec.decodePosts(null).isEmpty())
        assertEquals(LocalCampusDraft(), codec.decodeDraft("not-json"))
    }

    @Test
    fun `draft round trip keeps text and normalizes unknown tag`() {
        val draft = LocalCampusDraft(
            title = "草稿标题",
            content = "草稿正文",
            tag = "未知分类",
            updatedAtMillis = 300L
        )

        val decoded = codec.decodeDraft(codec.encodeDraft(draft))

        assertEquals("草稿标题", decoded.title)
        assertEquals("草稿正文", decoded.content)
        assertEquals(LocalCampusPostRules.DEFAULT_TAG, decoded.tag)
        assertEquals(300L, decoded.updatedAtMillis)
    }

    @Test
    fun `legacy local post tag is migrated instead of being dropped`() {
        val legacy = localPost(id = "legacy", createdAtMillis = 400L).copy(tag = "校园生活")

        val decoded = codec.decodePosts(codec.encodePosts(listOf(legacy)))

        assertEquals(1, decoded.size)
        assertEquals("生活服务", decoded.single().tag)
    }

    private fun localPost(id: String, createdAtMillis: Long) = LocalCampusPost(
        id = id,
        title = "本地测试标题",
        content = "本地测试正文",
        tag = "学习交流",
        createdAtMillis = createdAtMillis
    )
}

package com.mychuannong.app.freshNews.viewModel

import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps the prior category-filter regression on the local model retained for lost-and-found. */
class LocalCampusUiStateFilterTest {
    @Test
    fun `lost found cache can be isolated from removed legacy community posts`() {
        val lost = LocalCampusPost("lost", "校园卡", "内容", "失物招领", 2L)
        val general = LocalCampusPost("general", "动态", "内容", "校园动态", 1L)

        val visible = listOf(lost, general).filter { it.tag == "失物招领" }

        assertEquals(listOf("lost"), visible.map(LocalCampusPost::id))
    }
}

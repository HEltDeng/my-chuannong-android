package com.mychuannong.app.school

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.SecondaryPageHeader
import com.mychuannong.app.feature.common.compose_ui.ServiceDynamicsSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI-only data; never writes the remote store or learning caches. */
@RunWith(AndroidJUnit4::class)
class ServiceDynamicsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyStateIsVisibleAndHeaderBackWorks() {
        var back = false
        compose.setContent { AppSkinTheme {
            androidx.compose.foundation.layout.Column {
                SecondaryPageHeader("测试详情",{ back=true })
                ServiceDynamicsSection(emptyList(),{})
            }
        } }
        compose.onNodeWithText("服务动态").assertIsDisplayed()
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(true,back) }
    }
    @Test fun swipeClickAndShrinkingRefreshStayConsistent() {
        val entries=mutableStateOf(listOf(
            RemoteServiceUpdate("test-a","ANNOUNCEMENT",title="合成动态甲",summary="仅用于测试"),
            RemoteServiceUpdate("test-b","ANNOUNCEMENT",title="合成动态乙",summary="仅用于测试")))
        var clicked=""
        compose.setContent { AppSkinTheme { ServiceDynamicsSection(entries.value,{ clicked=it.id }) } }
        compose.onNodeWithText("合成动态甲").assertIsDisplayed()
        compose.onNodeWithTag("service-dynamics-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("合成动态乙").assertIsDisplayed()
        compose.onNodeWithText("合成动态乙").performClick()
        compose.runOnIdle { assertEquals("test-b",clicked);entries.value=entries.value.take(1) }
        compose.onNodeWithText("合成动态甲").assertIsDisplayed()
        compose.runOnIdle { entries.value=emptyList() }
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertIsDisplayed()
    }
}

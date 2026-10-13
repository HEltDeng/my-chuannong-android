package com.mychuannong.app.school

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.R
import com.mychuannong.app.core.main.navigation.MainTabDestination
import com.mychuannong.app.core.main.ui.MainBottomBar
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.feature.common.ui.FeatureScreen
import com.mychuannong.app.widget.view.rememberFloatingTabBarScrollConnection
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against installed DEX/resources: JVM parser tests cannot detect a stale packaged R class. */
@RunWith(AndroidJUnit4::class)
class ServiceNavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun installedResourceIdsAndBottomTabsMatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(listOf("首页", "服务", "我的"), MainTabDestination.ordered.map { context.getString(it.labelResId) })
        assertEquals("计算机考试", context.getString(R.string.feature_function_computer_exam))
        var selected: MainTabDestination = MainTabDestination.Overview
        compose.setContent {
            AppSkinTheme {
                MainBottomBar(MainTabDestination.Overview, 0f, { selected = it },
                    rememberFloatingTabBarScrollConnection(), rememberHazeState())
            }
        }
        listOf("首页", "服务", "我的").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("服务").performClick()
        compose.runOnIdle { assertEquals(MainTabDestination.Feature, selected) }
    }

    @Test fun servicePageRendersInstalledLearningResourcesWithoutRefresh() {
        compose.setContent { AppSkinTheme { FeatureScreen(isActive = false) } }
        compose.onNodeWithText("学习").assertIsDisplayed()
        compose.onNodeWithText("计算机考试").assertIsDisplayed()
    }
}

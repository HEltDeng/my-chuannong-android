package com.mychuannong.app.school

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.theme.AppSkinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic status only, so no real account, credentials or school connectivity is needed. */
class CampusNetworkScreenDeviceTest {
    // The binding activity is an allowed first-use screen. A generic test activity is
    // correctly rejected by the production school gate before an identity is bound.
    @get:Rule val compose = createAndroidComposeRule<com.mychuannong.app.settings.ui.BindingUserActivity>()

    @Test fun nativeStatusPageShowsVerifiedVpnCampusAndDisconnectedStates() {
        val state = mutableStateOf(CampusNetworkStatus.CHECKING)
        var bindClicks = 0
        var loginClicks = 0
        var refreshClicks = 0
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { AppSkinTheme {
            CampusNetworkScreen(state.value, {}, { bindClicks++ }, { refreshClicks++ }, { loginClicks++ })
        } } }
        compose.onNodeWithText("校园网络环境绑定").assertIsDisplayed()
        compose.onNodeWithText("正在检查校园网络环境…").assertIsDisplayed()
        compose.runOnIdle { state.value = CampusNetworkStatus.NOT_CONNECTED }
        compose.onNodeWithText("校园 VPN 尚未连接").assertIsDisplayed()
        compose.onNodeWithText("川农 e 认证登录").performClick()
        screenshot("network-unbound")
        compose.runOnIdle { state.value = CampusNetworkStatus.CAMPUS_DIRECT }
        compose.onNodeWithText("当前校园网可用").assertIsDisplayed()
        compose.onNodeWithText("川农 e 认证登录").assertIsDisplayed()
        compose.onNodeWithText("校园网环境").assertDoesNotExist()
        compose.onNodeWithText("继续登录教务系统").assertDoesNotExist()
        screenshot("network-campus")
        compose.runOnIdle { state.value = CampusNetworkStatus.VPN_CONNECTED }
        compose.onNodeWithText("你现在已经连接校园 VPN").assertIsDisplayed()
        compose.onNodeWithText("川农 e 认证登录").assertDoesNotExist()
        compose.onNodeWithText("继续登录教务系统").performClick()
        compose.onNodeWithText("重新检测").performClick()
        screenshot("network-vpn-connected")
        compose.runOnIdle {
            assertEquals(1, bindClicks)
            assertEquals(1, loginClicks)
            assertEquals(1, refreshClicks)
        }
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("bindingScreenshots") != "true") return
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

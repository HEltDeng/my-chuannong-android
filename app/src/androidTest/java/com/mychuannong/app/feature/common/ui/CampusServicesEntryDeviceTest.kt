package com.mychuannong.app.feature.common.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.config.BuiltInAppConfig
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.feature.common.compose_ui.FunctionSection
import com.mychuannong.app.feature.common.compose_ui.offCampusFunctionShortcuts
import com.mychuannong.app.feature.common.compose_ui.toFunctionItemData
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CampusServicesEntryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theThreeExistingServiceTilesLaunchTheirMatchingNativeModuleAndReturn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.setContent {
            AppSkinTheme {
                val context = LocalContext.current
                Box(Modifier.statusBarsPadding().padding(16.dp)) {
                    FunctionSection("校外园地", "校外服务与便民资讯",
                        offCampusFunctionShortcuts(BuiltInAppConfig.envelope().config).map { it.toFunctionItemData(context) })
                }
            }
        }
        compose.onNodeWithText("驾校").assertIsDisplayed()
        for ((name, route) in listOf("雅思托福" to "language", "考公考研" to "exam", "健身房" to "gym")) {
            val monitor = instrumentation.addMonitor(CampusServicesActivity::class.java.name, null, false)
            try {
                compose.onNodeWithText(name).assertIsDisplayed().performClick()
                val activity = requireNotNull(monitor.waitForActivityWithTimeout(5000))
                assertEquals(route, activity.intent.getStringExtra("campus_service"))
                compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
                compose.waitForIdle()
                compose.waitUntil(5000) { activity.isFinishing }
                compose.onNodeWithText("校外园地").assertIsDisplayed()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "campus-services-entry.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}

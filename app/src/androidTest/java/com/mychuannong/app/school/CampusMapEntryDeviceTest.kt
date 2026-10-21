package com.mychuannong.app.school

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.feature.common.compose_ui.FunctionSection
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CampusMapEntryDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun schoolServicesEighthEntryOpensCampusMap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(CampusMapActivity::class.java.name, null, false)
        try {
            compose.setContent {
                AppSkinTheme {
                    Box(Modifier.padding(16.dp)) {
                        FunctionSection("学校服务", "学校官方网站与公共服务", SchoolServiceCatalog.items(LocalContext.current))
                    }
                }
            }
            compose.onNodeWithText("学校官网").assertIsDisplayed()
            compose.onNodeWithText("校园地图").assertIsDisplayed()
            val items = SchoolServiceCatalog.items(instrumentation.targetContext)
            assertEquals(8, items.size)
            assertEquals("campus-map", items.last().id)
            compose.waitForIdle()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(instrumentation.targetContext.getExternalFilesDir(null), "campus-map-entry.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            compose.onNodeWithText("校园地图").performClick()
            val activity = requireNotNull(monitor.waitForActivityWithTimeout(5000))
            if(compose.onAllNodesWithTag("campus-amap-decline").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("campus-amap-decline").performClick()
            compose.onNodeWithText("校园地图").assertIsDisplayed()
            compose.onNodeWithTag("campus-map-mode").assertDoesNotExist()
            compose.onNodeWithTag("campus-map-scene").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitForIdle()
            assertTrue(activity.isFinishing)
            compose.onNodeWithText("学校官网").assertIsDisplayed()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}

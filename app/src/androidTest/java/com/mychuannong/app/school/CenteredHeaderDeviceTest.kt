package com.mychuannong.app.school

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.SecondaryPageHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CenteredHeaderDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun asymmetricActionsKeepTitleCenteredAndButtonsWorking() {
        var back = false
        var refreshed = false
        compose.setContent { AppSkinTheme {
            Box(Modifier.width(360.dp).testTag("header")) {
                SecondaryPageHeader("课程表", { back = true }) {
                    TextButton(onClick = { refreshed = true }, modifier = Modifier.width(88.dp)) { Text("刷新") }
                }
            }
        } }
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("课程表").fetchSemanticsNode().boundsInRoot
        val backBounds = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithText("刷新").fetchSemanticsNode().boundsInRoot
        assertEquals(header.center.x, title.center.x, 1f)
        assertTrue(title.left >= backBounds.right && title.right <= refresh.left)
        compose.onNodeWithText("刷新").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertTrue(back && refreshed) }
    }
}

package com.mychuannong.app.school

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.overview.ui.compose.HomeScoreSummaryCard
import com.mychuannong.app.overview.ui.model.OverviewMetricUiModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScoreSummaryCollapseDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapseRemovesEveryDetailAndRestoresWithoutOpeningGrades() {
        val expanded = mutableStateOf(true)
        var opens = 0
        val fields = SchoolTranscript(emptyList(), listOf(TranscriptField("平均成绩绩点", "3.50"),
            TranscriptField("有效学分", "40.0"), TranscriptField("加权平均成绩", "85.0")),
            ranking = listOf(TranscriptField("专业排名", "12"))).scoreSummaryFields()
        val metric = OverviewMetricUiModel("score", "成绩", "3.50", subtitle = "合成课程成绩",
            summaryFields = fields, iconRes = R.drawable.ic_grade, accentColor = Color(0xFF237B62))
        compose.setContent { AppSkinTheme { Column {
            Text("明日课程")
            HomeScoreSummaryCard(metric, expanded.value, { expanded.value = !expanded.value },
                { opens++ }, Modifier.testTag("summary"))
            Text("考试安排")
        } } }
        val full = compose.onNodeWithTag("summary").fetchSemanticsNode().boundsInRoot
        val eye = compose.onNodeWithContentDescription("折叠成绩汇总").fetchSemanticsNode().boundsInRoot
        assertTrue(eye.center.x > full.center.x && eye.center.y > full.center.y)
        compose.onNodeWithContentDescription("折叠成绩汇总").performClick()
        fields.forEach { (label, value) ->
            compose.onNodeWithText(label).assertDoesNotExist()
            compose.onNodeWithText(value).assertDoesNotExist()
        }
        compose.onNodeWithText("合成课程成绩").assertDoesNotExist()
        compose.onNodeWithText("****").assertDoesNotExist()
        compose.onNodeWithText("成绩汇总").assertIsDisplayed()
        compose.onNodeWithText("明日课程").assertIsDisplayed()
        compose.onNodeWithText("考试安排").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("summary").fetchSemanticsNode().boundsInRoot.height < full.height / 2)
        compose.onNodeWithContentDescription("展开成绩汇总").performClick()
        fields.forEach { (label, value) ->
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText(value).assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(0, opens) }
        compose.onNodeWithText("合成课程成绩").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }
}

package com.mychuannong.app.overview.ui.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewHomeVisualTest {
    @Test
    fun overviewStateDoesNotCarryElectricityMetricField() {
        val state = OverviewUiState()
        assertTrue(state.metrics.isEmpty())
        assertTrue(OverviewUiState::class.java.declaredFields.none { it.name.contains("Electricity", ignoreCase = true) })
    }

    @Test
    fun gpaMetricModelSupportsValueAndEmptyFallback() {
        val gpa = OverviewMetricUiModel(
            id = "ScoreInquiry",
            title = "平均绩点",
            value = "3.02",
            subtitle = "平均分: 82.0",
            iconRes = 0,
            accentColor = Color(0xFF3A6FF0),
        )
        assertEquals("平均绩点", gpa.title)
        assertEquals("3.02", gpa.value)

        val empty = gpa.copy(value = "--", subtitle = "去成绩查询加载数据")
        assertEquals("--", empty.value)
        assertEquals("去成绩查询加载数据", empty.subtitle)
    }

    @Test
    fun settingsOptionKeepsSingleLineTitleWithoutSubtitle() {
        val option = com.mychuannong.app.profileSettings.ui.model.SettingItemUiModel.Option(
            id = "12",
            title = "上课提醒 & 桌面小组件",
        )
        assertEquals("上课提醒 & 桌面小组件", option.title)
        assertNull(option.subtitle)
    }
}

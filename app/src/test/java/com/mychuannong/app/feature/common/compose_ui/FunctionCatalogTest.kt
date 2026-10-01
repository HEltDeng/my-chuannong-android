package com.mychuannong.app.feature.common.compose_ui

import com.mychuannong.app.core.config.BuiltInAppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionCatalogTest {

    @Test
    fun `campus study and life tools are present while exclusions stay absent`() {
        val primaryIds = primaryFunctionShortcuts().map(FunctionShortcut::id)
        val learningIds = learningFunctionShortcuts().map(FunctionShortcut::id)
        val lifeIds = lifeFunctionShortcuts().map(FunctionShortcut::id)

        assertFalse("map" in primaryIds)
        assertTrue(listOf("schedule", "grade", "classroom", "homework", "exam").all(primaryIds::contains))
        assertTrue(listOf("schedule", "grade", "classroom", "homework", "exam", "document", "cet", "mandarin").all(learningIds::contains))
        assertTrue(listOf("electric", "lost_found").all(lifeIds::contains))
        assertFalse("calendar" in primaryIds)
        assertFalse("calendar" in learningIds)
        assertFalse("calendar" in lifeIds)
        assertTrue(sportsFunctionShortcuts().isEmpty())
    }

    @Test
    fun `lost found remains reachable`() {
        val lifeIds = lifeFunctionShortcuts().map(FunctionShortcut::id)

        assertTrue("lost_found" in lifeIds)
    }

    @Test
    fun `remote navigation changes title order visibility and url without changing action`() {
        val config = BuiltInAppConfig.envelope().config.copy(
            navigation = BuiltInAppConfig.envelope().config.navigation.map { item ->
                when (item.actionId) {
                    "grade" -> item.copy(
                        title = "综合成绩",
                        order = 1,
                        url = "https://mychangliapp.cn/features.html",
                    )
                    "classroom" -> item.copy(visible = false)
                    else -> item
                }
            },
        )

        val primary = primaryFunctionShortcuts(config)

        assertEquals("grade", primary.first().id)
        assertEquals("综合成绩", primary.first().titleOverride)
        assertEquals("https://mychangliapp.cn/features.html", primary.first().url)
        assertFalse(primary.any { it.id == "classroom" })
    }
}

package com.mychuannong.app.feature.common.compose_ui

import com.mychuannong.app.core.config.BuiltInAppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionCatalogTest {
    @Test
    fun `computer exam is available in both learning catalogs`() {
        val config = BuiltInAppConfig.envelope().config
        listOf(learningFunctionShortcuts(), learningFunctionShortcuts(config)).forEach { items ->
            assertEquals(1, items.count { it.id == "ncre" })
            assertEquals(FunctionDestination.ComputerExam, items.single { it.id == "ncre" }.destination)
        }
        assertEquals("计算机考试", learningFunctionShortcuts(config).single { it.id == "ncre" }.titleOverride)
        assertTrue(com.mychuannong.app.core.config.FeatureRegistry.isRegisteredAction("ncre"))
        assertTrue("computer" in com.mychuannong.app.core.config.FeatureRegistry.iconKeys)
        assertFalse(lifeFunctionShortcuts(config).any { it.id == "ncre" })
    }

    @Test
    fun `older navigation gains computer exam without changing other learning entries`() {
        val base = BuiltInAppConfig.envelope().config
        val older = base.copy(navigation = base.navigation.filterNot { it.actionId == "ncre" })
        val actual = learningFunctionShortcuts(older)
        assertEquals(1, actual.count { it.id == "ncre" })
        assertEquals(learningFunctionShortcuts(base).filterNot { it.id == "ncre" }, actual.filterNot { it.id == "ncre" })
        val hidden = base.copy(navigation = base.navigation.map { if(it.actionId == "ncre") it.copy(visible = false) else it })
        assertFalse(learningFunctionShortcuts(hidden).any { it.id == "ncre" })
    }

    @Test
    fun `campus study and life tools are present while exclusions stay absent`() {
        val primaryIds = primaryFunctionShortcuts().map(FunctionShortcut::id)
        val learningIds = learningFunctionShortcuts().map(FunctionShortcut::id)
        val lifeIds = lifeFunctionShortcuts().map(FunctionShortcut::id)

        assertFalse("map" in primaryIds)
        assertFalse(primaryIds.any { it in setOf("classroom", "homework") })
        assertFalse(learningIds.any { it in setOf("classroom", "homework") })
        assertTrue(listOf("schedule", "grade", "exam").all(primaryIds::contains))
        assertTrue(listOf("schedule", "grade", "exam", "document", "cet", "mandarin").all(learningIds::contains))
        assertTrue(listOf("electric", "lost_found").all(lifeIds::contains))
        assertFalse("calendar" in primaryIds)
        assertFalse("calendar" in learningIds)
        assertFalse("calendar" in lifeIds)
        assertTrue(sportsFunctionShortcuts().isEmpty())
    }

    @Test
    fun `old cached configuration cannot restore removed entries`() {
        val base = BuiltInAppConfig.envelope().config
        val template = base.navigation.first()
        val config = base.copy(navigation = base.navigation + listOf(
            template.copy(id = "classroom", actionId = "classroom", section = "learning", visible = true),
            template.copy(id = "homework", actionId = "homework", section = "learning", visible = true),
        ))
        assertFalse(primaryFunctionShortcuts(config).any { it.id in setOf("classroom", "homework") })
        assertFalse(learningFunctionShortcuts(config).any { it.id in setOf("classroom", "homework") })
        assertTrue(learningFunctionShortcuts(config).any { it.id == "schedule" })
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

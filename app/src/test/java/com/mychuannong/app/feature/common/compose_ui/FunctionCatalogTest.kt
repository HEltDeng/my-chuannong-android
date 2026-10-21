package com.mychuannong.app.feature.common.compose_ui

import com.mychuannong.app.core.config.BuiltInAppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionCatalogTest {
    @Test fun repairIsInLifeAndOlderConfigurationsReceiveIt() {
        val base = com.mychuannong.app.core.config.BuiltInAppConfig.envelope().config
        val old = base.copy(navigation = base.navigation.filterNot { it.actionId == "logistics_repair" })
        for (config in listOf(null, base, old)) {
            val entry = lifeFunctionShortcuts(config).single { it.id == "logistics_repair" }
            assertEquals(FunctionDestination.LogisticsRepair, entry.destination)
            assertFalse(learningFunctionShortcuts(config).any { it.id == "logistics_repair" })
        }
        val hidden = base.copy(navigation = base.navigation.map {
            if (it.actionId == "logistics_repair") it.copy(visible = false) else it
        })
        assertFalse(lifeFunctionShortcuts(hidden).any { it.id == "logistics_repair" })
    }

    @Test fun offCampusSurvivesOldConfigAndRespectsVisibilityAndOrder() {
        val base = BuiltInAppConfig.envelope().config
        val expected = listOf("driving_school", "ielts_toefl", "civil_postgrad", "gym")
        val older = base.copy(navigation = base.navigation.filterNot { it.section == "off_campus" })
        assertEquals(expected, offCampusFunctionShortcuts(older).map { it.id })
        assertEquals(FunctionDestination.DrivingSchool, offCampusFunctionShortcuts().single { it.id == "driving_school" }.destination)
        val destinations = mapOf("ielts_toefl" to FunctionDestination.IeltsToefl,
            "civil_postgrad" to FunctionDestination.CivilPostgrad, "gym" to FunctionDestination.Gym)
        for (config in listOf(null, base, older)) {
            for ((id, destination) in destinations) {
                val entry = offCampusFunctionShortcuts(config).single { it.id == id }
                assertEquals(destination, entry.destination)
                assertTrue(entry.url == null)
            }
        }
        val customized = base.copy(navigation = base.navigation.map {
            when (it.id) { "gym" -> it.copy(order = -1); "driving_school" -> it.copy(visible = false); else -> it }
        })
        assertEquals(listOf("gym", "ielts_toefl", "civil_postgrad"), offCampusFunctionShortcuts(customized).map { it.id })
        assertFalse(learningFunctionShortcuts(base).any { it.id in expected })
        assertFalse(lifeFunctionShortcuts(base).any { it.id in expected })
    }

    @Test fun drivingSchoolIsLocalAndAvailableWithOlderMenusWithoutMovingOtherEntries() {
        val base = BuiltInAppConfig.envelope().config
        val older = base.copy(navigation = base.navigation.filterNot { it.actionId == "driving_school" })
        for (config in listOf(null, base, older)) {
            val items = offCampusFunctionShortcuts(config)
            assertEquals(1, items.count { it.id == "driving_school" })
            assertEquals(FunctionDestination.DrivingSchool, items.single { it.id == "driving_school" }.destination)
            assertFalse(learningFunctionShortcuts(config).any { it.id == "driving_school" })
            assertFalse(lifeFunctionShortcuts(config).any { it.id == "driving_school" })
        }
        assertEquals(offCampusFunctionShortcuts(base), offCampusFunctionShortcuts(older))
    }

    @Test fun errandIsAvailableInLifeIncludingOlderMenusAndRespectsHide() {
        val base = BuiltInAppConfig.envelope().config
        val old = base.copy(navigation=base.navigation.filterNot { it.actionId == "errand" })
        listOf(lifeFunctionShortcuts(),lifeFunctionShortcuts(base),lifeFunctionShortcuts(old)).forEach { items ->
            assertEquals(1,items.count { it.id == "errand" })
            assertEquals(FunctionDestination.Errand,items.single { it.id == "errand" }.destination)
        }
        val hidden=base.copy(navigation=base.navigation.map { if(it.actionId=="errand") it.copy(visible=false) else it })
        assertFalse(lifeFunctionShortcuts(hidden).any { it.id=="errand" })
        assertEquals(lifeFunctionShortcuts(old).filterNot { it.id=="errand" },lifeFunctionShortcuts(base).filterNot { it.id=="errand" })
        assertFalse(learningFunctionShortcuts(base).any { it.id=="errand" })
    }

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

    @Test fun studentServicesSurviveOldMenusWithoutDuplicatingOrRestoringHiddenEntries() {
        val ids = setOf("comprehensive_score", "weekly_attendance", "holiday_destination")
        val base = BuiltInAppConfig.envelope().config
        val old = base.copy(navigation = base.navigation.filterNot { it.actionId in ids })
        for (config in listOf(null, base, old)) {
            val learning = learningFunctionShortcuts(config)
            val life = lifeFunctionShortcuts(config)
            assertEquals(1, learning.count { it.id == "comprehensive_score" })
            assertEquals(1, life.count { it.id == "weekly_attendance" })
            assertEquals(1, life.count { it.id == "holiday_destination" })
            assertFalse(life.any { it.id == "comprehensive_score" })
            assertFalse(learning.any { it.id in setOf("weekly_attendance", "holiday_destination") })
        }
        val hidden = base.copy(navigation = base.navigation.map { if(it.actionId in ids) it.copy(visible=false) else it })
        assertFalse((learningFunctionShortcuts(hidden) + lifeFunctionShortcuts(hidden)).any { it.id in ids })
        assertEquals(learningFunctionShortcuts(base), learningFunctionShortcuts(old))
        assertEquals(lifeFunctionShortcuts(base), lifeFunctionShortcuts(old))
    }

    @Test fun cachedGlobeIconCannotOverrideCetEnglishIconOrChangeDestination() {
        val base = BuiltInAppConfig.envelope().config
        val old = base.copy(navigation = base.navigation.map { if(it.actionId == "cet") it.copy(iconKey="language") else it })
        val cet = learningFunctionShortcuts(old).single { it.id == "cet" }
        assertEquals(FunctionDestination.Cet, cet.destination)
        assertEquals(learningFunctionShortcuts().single { it.id == "cet" }.icon, cet.icon)
        assertEquals("四六级", cet.titleOverride)
    }
}

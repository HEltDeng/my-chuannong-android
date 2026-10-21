package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class CampusPlacesTest {
    private val campus = CampusMap.CHENGDU
    private val root = CampusGeoPoi("root", "四川农业大学成都校区", 30.707, 103.86)
    private val catalogue = CampusAmapCatalogue.places(campus, CampusGeoData(root, listOf(
        root.copy(id="teaching", name="第二教学实验楼"),
        root.copy(id="dorm", name="四川农业大学学生公寓1号"),
        root.copy(id="library", name="四川农业大学图书馆"),
    )))

    @Test fun shortNamesFullwidthAndMultipleTokensFindVendorBuilding() {
        listOf("二教", "２号教学楼", "  2号  教学  ", "二号教学楼").forEach { query ->
            assertEquals(query, "teaching", CampusPlaces.visible(campus, PlaceCategory.DORM, query, catalogue).single().geo!!.id)
        }
    }
    @Test fun clearingQueryRestoresCampusAndCategoryFilter() {
        assertEquals("dorm", CampusPlaces.visible(campus, PlaceCategory.DORM, "  ", catalogue).single().geo!!.id)
        assertTrue(CampusPlaces.visible(CampusMap.YAAN, PlaceCategory.ALL, "", catalogue).isEmpty())
        assertEquals("library", CampusPlaces.visible(CampusMap.YAAN, PlaceCategory.SPORTS, "图书馆", catalogue).single().geo!!.id)
    }
    @Test fun missingQueryAndMissingCatalogueNeverSubstitutePlaces() {
        assertTrue(CampusPlaces.visible(campus, PlaceCategory.ALL, "不存在的楼987654", catalogue).isEmpty())
        assertTrue(CampusPlaces.visible(campus, PlaceCategory.ALL, "", emptyList()).isEmpty())
        assertTrue(CampusPlaces.visible(campus, PlaceCategory.ALL, "食堂", emptyList()).isEmpty())
    }
}

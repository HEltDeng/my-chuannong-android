package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class CampusAmapCatalogueTest {
    private val center=CampusGeoPoi("root","四川农业大学雅安校区",29.978648,102.995025)
    private fun poi(id: String,name: String)=CampusGeoPoi(id,name,29.978922,103.002933)
    private fun places(vararg poi: CampusGeoPoi)=CampusAmapCatalogue.places(CampusMap.YAAN,CampusGeoData(center,poi.toList()))

    @Test fun vendorNamesAndCoordinatesArePreservedWithoutIllustrationMatching() {
        val vendor=poi("B0HRCPJFLY","四川农业大学雅安校区第一教学区操场")
        val place=places(vendor).single()
        assertEquals(vendor.name,place.name)
        assertEquals(vendor.name,place.shortName)
        assertEquals(vendor,place.geo)
        assertEquals(PlaceCategory.SPORTS,place.category)
        assertFalse(place.aliases.contains("老区操场"))
        assertEquals("amap-yaan-B0HRCPJFLY",place.id)
    }
    @Test fun allMainBuildingTypesAreIncludedButShopsAndParkingAreNot() {
        val names=listOf("四川农业大学第二教学楼A区","四川农业大学学生二十五舍","四川农业大学杏苑餐厅",
            "四川农业大学图书馆","四川农业大学体育馆","四川农业大学医务室","四川农业大学东门")
        assertEquals(7,places(*names.mapIndexed { i,n -> poi("$i",n) }.toTypedArray()).size)
        assertTrue(places(poi("p","四川农业大学图书馆停车场"),poi("s","四川农业大学老袁香肠"),poi("b","四川农业大学公交站")).isEmpty())
    }
    @Test fun searchIsGlobalAndAliasesNeverRenameThePoiOrConfuseBuildingNumbers() {
        val first=poi("1","第一综合教学楼")
        val eleven=poi("11","四川农业大学11号教学楼")
        val list=places(first,eleven,poi("food","四川农业大学杏苑餐厅"),poi("dorm","四川农业大学学生公寓1号"))
        val matches=CampusPlaces.visible(CampusMap.CHENGDU,PlaceCategory.SPORTS,"1号",list)
        assertEquals(setOf("1","dorm"),matches.map { it.geo!!.id }.toSet())
        assertEquals(first.name,matches.first { it.geo!!.id=="1" }.name)
        assertEquals("food",CampusPlaces.visible(CampusMap.CHENGDU,PlaceCategory.SPORTS,"食堂",list).single().geo!!.id)
    }
    @Test fun nearbyBuildingsMustHaveSchoolOwnershipAndCorrectCampus() {
        assertTrue(CampusAmapCatalogue.belongs(poi("1","第一教学楼").copy(address="四川农业大学内"),CampusMap.YAAN,center))
        assertFalse(CampusAmapCatalogue.belongs(poi("2","雅安中学教学楼"),CampusMap.YAAN,center))
        assertFalse(CampusAmapCatalogue.belongs(poi("3","四川农业大学成都校区图书馆"),CampusMap.YAAN,center))
        assertFalse(CampusAmapCatalogue.belongs(poi("4","四川农业大学教学楼").copy(latitude=30.7),CampusMap.YAAN,center))
    }
    @Test fun idDeduplicationDoesNotMergeDistinctVendorBuildingsWithSameName() {
        val a=poi("a","四川农业大学学生公寓")
        val b=a.copy(id="b",longitude=103.001)
        assertEquals(2,places(a,a,b).size)
    }
    @Test fun invalidCoordinatesAndCampusRootAreNotSelectablePlaces() {
        assertTrue(places(center,poi("zero","四川农业大学图书馆").copy(latitude=0.0),
            poi("nan","四川农业大学教学楼").copy(longitude=Double.NaN)).isEmpty())
    }
}

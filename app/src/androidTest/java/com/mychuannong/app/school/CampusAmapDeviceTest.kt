package com.mychuannong.app.school

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Live vendor integration smoke test. Requires a registered Android debug Key and network.
 * Run on a disposable emulator with the exact registered package/certificate. */
class CampusAmapDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext

    @Test fun consentThenLiveCampusMapAndPoiLookup() {
        assumeTrue(BuildConfig.AMAP_API_KEY.isNotBlank())
        context.getSharedPreferences("campus_amap_consent",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("campus_amap_catalogue_v2",Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(CampusMapActivity::class.java).use { scenario ->
            compose.onNodeWithTag("campus-amap-accept").assertIsDisplayed()
            compose.onNodeWithTag("campus-amap-scene").assertDoesNotExist()
            compose.onNodeWithTag("campus-amap-decline").performClick()
            compose.onNodeWithTag("campus-map-unavailable").assertIsDisplayed()
            compose.onNodeWithTag("campus-map-scene").assertDoesNotExist()
            compose.onNodeWithTag("campus-amap-scene").assertDoesNotExist()
            assertFalse(CampusAmapConsent.agreed(context))
            compose.onNodeWithTag("campus-amap-enable").performClick()
            compose.onNodeWithTag("campus-amap-accept").performClick()
            compose.onNodeWithTag("campus-amap-scene").assertExists()
            compose.onNodeWithText("示意图").assertDoesNotExist()
            compose.onNodeWithText("原图").assertDoesNotExist()
            var view: CampusAmapView?=null
            scenario.onActivity { activity -> view=findMap(activity.window.decorView) }
            val map=requireNotNull(view)
            compose.waitUntil(180000) { !map.status.startsWith("正在") }
            screenshot("amap-first-load.png")
            assertTrue("Live campus POIs: ${map.status}",map.status.contains("个地点"))
            compose.waitUntil(20000) { map.loaded }
            compose.waitForIdle()
            Thread.sleep(15000)
            screenshot("amap-settled.png")
            compose.waitForIdle()
            val repository=CampusAmapData(context)
            val catalogues=mutableMapOf<CampusMap,List<CampusPlace>>()
            for(campus in CampusMap.entries) {
                val data=runBlocking { repository.load(campus) }
                val places=CampusAmapCatalogue.places(campus,data)
                catalogues[campus]=places
                File(context.getExternalFilesDir(null),"amap-"+campus.id+"-pois.txt").writeText(
                    data.center.toString()+"\n"+data.pois.joinToString("\n")+"\nCATALOGUE\n"+
                    places.joinToString("\n") { it.category.name+" | "+it.name+" | "+it.geo })
                assertTrue("Include the main school buildings",places.size>=15)
                assertTrue(places.all { it.name==it.geo!!.name && CampusAmapCatalogue.valid(it.geo!!) })
                assertEquals(places.size,places.map { it.id }.distinct().size)
                compose.onNodeWithTag("campus-tab-${campus.id}").performClick()
                compose.waitForIdle()
                compose.waitUntil(60000) { map.status.contains("个地点") }
                compose.waitForIdle();Thread.sleep(1500)
                screenshot("amap-${campus.id}.png")
            }
            compose.onNodeWithTag("campus-tab-chengdu").performClick()
            compose.waitForIdle()
            compose.waitUntil(60000) { map.status.contains("个地点") }
            compose.onNodeWithTag("campus-category-SPORTS").performScrollTo().performClick()
            compose.onNodeWithTag("campus-search").performClick().performTextInput("图书馆")
            compose.onNodeWithTag("campus-place-amap-chengdu-B001C8O02T").performClick()
            compose.onNodeWithTag("campus-category-LIBRARY").assertIsSelected()
            compose.onNodeWithTag("campus-place-amap-chengdu-B001C8O02T").assertIsSelected()
            compose.waitUntil(60000) { !map.status.startsWith("正在") }
            compose.waitForIdle();Thread.sleep(5000)
            scenario.onActivity { File(context.getExternalFilesDir(null),"amap-camera.txt").writeText(map.map.cameraPosition.toString()) }
            assertTrue("Selected POI must resolve",map.status.contains("图书馆 · 高德地点"))
            scenario.onActivity {
                val camera=map.map.cameraPosition
                assertEquals(30.705401,camera.target.latitude,.0001)
                assertEquals(103.861227,camera.target.longitude,.0001)
                assertTrue(camera.zoom>=18f)
                val point=map.map.projection.toScreenLocation(camera.target)
                assertTrue("Target must remain above sheet",point.y in (map.height*.17f).toInt()..(map.height*.62f).toInt())
                assertFalse(androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true)
            }
            screenshot("amap-selected.png")
            File(context.getExternalFilesDir(null),"amap-selection.txt").writeText(map.status)
            val yaanSports=catalogues.getValue(CampusMap.YAAN).filter { it.category==PlaceCategory.SPORTS }
            val oldGround=yaanSports.first { it.geo!!.id=="B0HRCPJFLY" }
            assertEquals("四川农业大学雅安校区第一教学区操场",oldGround.name)
            compose.onNodeWithTag("campus-search").performClick().performTextInput("第一教学区操场")
            compose.onNodeWithTag("campus-place-"+oldGround.id).performClick()
            compose.onNodeWithTag("campus-tab-yaan").assertIsSelected()
            compose.onNodeWithTag("campus-category-SPORTS").assertIsSelected()
            compose.onNodeWithTag("campus-place-"+oldGround.id).assertIsSelected()
            compose.waitUntil(30000) { map.status==oldGround.name+" · 高德地点" }
            compose.waitForIdle();Thread.sleep(2000)
            scenario.onActivity {
                assertEquals(oldGround.geo!!.latitude,map.map.cameraPosition.target.latitude,.0001)
                assertEquals(oldGround.geo!!.longitude,map.map.cameraPosition.target.longitude,.0001)
            }
            screenshot("amap-yaan-selected.png")
            // Native map-only labels are bundled from verified SDK callbacks, not fabricated matches.
            val nativeBuilding=catalogues.getValue(CampusMap.CHENGDU).first { it.geo!!.id=="B001C8O5CF" }
            assertEquals("第二教学实验楼",nativeBuilding.name)
            compose.onNodeWithTag("campus-search").performClick().performTextInput(nativeBuilding.name)
            compose.onNodeWithTag("campus-place-"+nativeBuilding.id).performClick()
            compose.waitUntil(15000) { map.status==nativeBuilding.name+" · 高德地点" }
            compose.waitForIdle();Thread.sleep(1500)
            compose.onNodeWithTag("campus-place-"+nativeBuilding.id).assertIsDisplayed().assertIsSelected()
            screenshot("amap-native-building.png")
            val firstBuilding=catalogues.getValue(CampusMap.CHENGDU).first { it.geo!!.id=="B001C8O10H" }
            compose.onNodeWithTag("campus-category-ALL").performScrollTo().performClick()
            scenario.onActivity { map.map.moveCamera(com.amap.api.maps.CameraUpdateFactory.newCameraPosition(
                com.amap.api.maps.model.CameraPosition(firstBuilding.geo!!.position,18f,35f,0f))) }
            compose.waitForIdle();Thread.sleep(1500)
            var x=0f;var y=0f
            scenario.onActivity {
                val screen=map.map.projection.toScreenLocation(firstBuilding.geo!!.position)
                val origin=IntArray(2);map.getLocationOnScreen(origin)
                x=(origin[0]+screen.x).toFloat();y=origin[1]+screen.y-8*map.resources.displayMetrics.density
            }
            val time=android.os.SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),action,x,y,0)
                instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle()
                Thread.sleep(100)
            }
            compose.waitUntil(15000) { map.status==firstBuilding.name+" · 高德地点" }
            compose.onNodeWithTag("campus-place-"+firstBuilding.id).assertIsDisplayed().assertIsSelected()
            // Exercise real SDK search separately from the dated first-install snapshot.
            val refreshed=runBlocking { repository.load(CampusMap.CHENGDU,refresh=true) }
            assertTrue(CampusAmapCatalogue.places(CampusMap.CHENGDU,refreshed).any { it.geo!!.id=="B001C8O5CF" })
            scenario.recreate()
            compose.onNodeWithTag("campus-amap-scene").assertExists()
            compose.onNodeWithTag("campus-amap-accept").assertDoesNotExist()
        }
    }
    private fun findMap(view: View): CampusAmapView? {
        if(view is CampusAmapView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun screenshot(name: String) {
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}

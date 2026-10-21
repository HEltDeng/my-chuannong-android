package com.mychuannong.app.feature.common.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only against the isolated test package, with the bundled synthetic merchant data. */
@RunWith(AndroidJUnit4::class)
class DrivingSchoolIntegrationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun schoolListSharedDetailsBookingsAndHierarchicalBackWorkOffline() {
        ActivityScenario.launch(DrivingSchoolActivity::class.java).use { scenario ->
            waitFor(scenario, "Boolean(window.CampusDriving && document.querySelectorAll('.school-list-card').length === 3)")
            waitFor(scenario, "innerWidth > 200 && innerHeight > 200 && document.querySelector('#screen-scroll').getBoundingClientRect().height > 200")
            scenario.onActivity { activity ->
                val web = requireNotNull(findWeb(activity.window.decorView))
                assertTrue(web.settings.javaScriptEnabled)
                assertTrue(web.settings.blockNetworkLoads)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertFalse(web.settings.domStorageEnabled)
            }
            assertJs(scenario, "document.documentElement.classList.contains('native-host')")
            assertJs(scenario, "getComputedStyle(document.querySelector('.screen-header')).display === 'none'")
            assertJs(scenario, "getComputedStyle(document.documentElement).getPropertyValue('--paper').trim() === '#f5f9fd'")
            assertJs(scenario, "Array.from(document.querySelectorAll('.school-list-card')).map(b=>b.dataset.school).join(',') === 'a,b,c'")
            assertJs(scenario, "Array.from(document.querySelectorAll('.school-list-card')).every(b=>b.getBoundingClientRect().width > b.getBoundingClientRect().height)")
            assertJs(scenario, "!document.querySelector('.bottom-dock') && !document.querySelector('#school-name')")
            assertNoOverflow(scenario)
            screenshot(scenario, "driving-school-list.png")

            click(scenario, "[data-action='enter'][data-school='a']")
            assertJs(scenario, "document.querySelector('#school-name').textContent === '青禾驾校'")
            assertCleanDetail(scenario)
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 3")
            assertNoOverflow(scenario)
            screenshot(scenario, "driving-school-detail-a.png")
            click(scenario, "[data-group='gear'][data-value='auto']")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 2")
            click(scenario, "[data-group='day'][data-value='weekend']")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 1 && document.querySelector('.class-card').dataset.class === 'a-c2-weekend'")
            click(scenario, "[data-group='gear'][data-value='auto']")
            click(scenario, "[data-group='day'][data-value='weekend']")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 3")
            click(scenario, "[data-group='gear'][data-value='manual']")
            click(scenario, "[data-group='day'][data-value='weekend']")
            assertJs(scenario, "Boolean(document.querySelector('.empty-state'))")
            click(scenario, ".empty-state [data-action='clear']")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 3")
            click(scenario, "#expand-a-c2-day")
            assertJs(scenario, "!document.querySelector('#details-a-c2-day').hidden")
            click(scenario, "#expand-a-c2-day")
            assertJs(scenario, "document.querySelector('#details-a-c2-day').hidden")
            click(scenario, "#expand-a-c2-day")
            click(scenario, "#details-a-c2-day [data-action='book']")
            assertJs(scenario, "document.querySelectorAll('.slot').length === 2")
            assertJs(scenario, "document.querySelector('[data-action=submit]').disabled")
            click(scenario, "[data-slot='a-4']")
            assertJs(scenario, "!document.querySelector('[data-action=submit]').disabled")
            click(scenario, "[data-action='submit']")
            waitFor(scenario, "document.querySelector('.success-message')?.textContent === '模拟预约已提交，等待商家确认。'")
            assertCleanDetail(scenario)
            screenshot(scenario, "driving-school-booking-success.png")
            nativeBack(scenario)
            waitFor(scenario, "!document.querySelector('.sheet')")
            assertJs(scenario, "!document.querySelector('#details-a-c2-day').hidden")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.school-list-card').length === 3 && !document.querySelector('.bottom-dock')")

            click(scenario, "[data-action='enter'][data-school='b']")
            assertJs(scenario, "document.querySelector('#school-name').textContent === '晴川驾校'")
            assertJs(scenario, "document.querySelector('.hero-photo img').getAttribute('src').endsWith('school-b.png')")
            assertCleanDetail(scenario)
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 3 && !document.querySelector('.filter-chip.selected')")
            click(scenario, "[data-group='gear'][data-value='manual']")
            click(scenario, "[data-group='day'][data-value='weekend']")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 1 && document.querySelector('.class-card').dataset.class === 'b-c1-weekend'")
            click(scenario, ".clear-filters")
            click(scenario, ".location-button")
            assertJs(scenario, "document.querySelector('.location-large-title').textContent === '学府西路 · 晴川训练基地'")
            assertJs(scenario, "document.querySelector('.sheet .route-map').textContent.includes('晴川训练场')")
            screenshot(scenario, "driving-school-pickup-b.png")
            nativeBack(scenario)
            waitFor(scenario, "!document.querySelector('.sheet')")
            click(scenario, ".bottom-dock [data-action='book']")
            assertJs(scenario, "document.querySelector('[data-slot=b-4]').disabled")
            click(scenario, ".booking-cancel")
            waitFor(scenario, "!document.querySelector('.sheet')")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.school-list-card').length === 3")

            click(scenario, "[data-action='enter'][data-school='c']")
            assertJs(scenario, "document.querySelector('#school-name').textContent === '知行驾校'")
            assertJs(scenario, "document.querySelector('.hero-photo img').getAttribute('src').endsWith('school-c.png')")
            assertJs(scenario, "document.querySelector('.location-button').textContent.includes('校园南路') && document.querySelector('.route-map').textContent.includes('知行训练场')")
            assertJs(scenario, "document.querySelectorAll('.class-card').length === 3 && Array.from(document.querySelectorAll('.class-card')).every(c=>c.dataset.class.startsWith('c-'))")
            assertCleanDetail(scenario)
            assertNoOverflow(scenario)
            screenshot(scenario, "driving-school-detail-c.png")
            click(scenario, ".hero-photo")
            assertJs(scenario, "document.querySelector('.large-photo').getAttribute('src').endsWith('school-c.png')")
            click(scenario, ".sheet-header [data-action='close']")
            waitFor(scenario, "!document.querySelector('.sheet')")
            click(scenario, ".bottom-dock [data-action='book']")
            assertJs(scenario, "document.querySelector('.booking-school strong').textContent === '知行驾校' && document.querySelectorAll('.slot').length === 4")
            assertJs(scenario, "document.querySelector('[data-slot=c-2]').disabled && document.querySelectorAll('input').length === 0")
            click(scenario, "[data-slot='c-3']")
            assertNoOverflow(scenario)
            screenshot(scenario, "driving-school-booking-c.png")
            click(scenario, "[data-action='submit']")
            waitFor(scenario, "Boolean(document.querySelector('.success-message'))")
            assertJs(scenario, "document.querySelector('.success-ticket').textContent.includes('知行驾校') && document.querySelector('.success-ticket').textContent.includes('14:30–15:00')")
            click(scenario, ".sheet-footer [data-action='close']")
            waitFor(scenario, "!document.querySelector('.sheet')")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.school-list-card').length === 3")
            nativeBack(scenario)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (scenario.state != Lifecycle.State.DESTROYED && System.nanoTime() < deadline) Thread.sleep(50)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private fun assertCleanDetail(scenario: ActivityScenario<DrivingSchoolActivity>) {
        assertJs(scenario, "document.querySelectorAll('.school-switch,.school-stamp,.feature-row,.benefit-entry,.hero-cta,.hero-bottom-note,.section-description,.price-note,.pickup-excerpt,.closing-copy,.eyebrow').length === 0")
        assertJs(scenario, "!document.querySelector('#screen-content').textContent.includes('CLASSES') && !document.querySelector('#screen-content').textContent.includes('把方向，握在自己手里。')")
    }

    private fun assertNoOverflow(scenario: ActivityScenario<DrivingSchoolActivity>) {
        assertJs(scenario, "document.querySelector('#screen-scroll').scrollWidth === document.querySelector('#screen-scroll').clientWidth")
        waitFor(scenario, "Array.from(document.getAnimations()).every(animation => animation.playState === 'finished')")
        assertJs(scenario, "Array.from(document.querySelectorAll('.sheet')).every(s=>{const r=s.getBoundingClientRect();return r.left>=0 && r.right<=innerWidth+1 && r.top>=0 && r.bottom<=innerHeight+1})")
    }

    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun js(scenario: ActivityScenario<DrivingSchoolActivity>, expression: String): String {
        val completed = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            val web = findWeb(activity.window.decorView)
            if (web == null) {
                completed.countDown()
            } else web.evaluateJavascript(expression) {
                result = it
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", completed.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun assertJs(scenario: ActivityScenario<DrivingSchoolActivity>, expression: String) {
        assertEquals(expression, "true", js(scenario, "Boolean($expression)"))
    }

    private fun waitFor(scenario: ActivityScenario<DrivingSchoolActivity>, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < deadline) {
            if (js(scenario, "Boolean($expression)") == "true") return
            Thread.sleep(50)
        }
        assertJs(scenario, expression)
    }

    private fun click(scenario: ActivityScenario<DrivingSchoolActivity>, selector: String) {
        val quoted = org.json.JSONObject.quote(selector)
        assertEquals("true", js(scenario, "(function(){const button=document.querySelector($quoted);if(!button||button.disabled)return false;button.click();return true;})()"))
    }

    private fun nativeBack(scenario: ActivityScenario<DrivingSchoolActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun screenshot(scenario: ActivityScenario<DrivingSchoolActivity>, name: String) {
        waitFor(scenario, "Array.from(document.getAnimations()).every(animation => animation.playState === 'finished')")
        js(scenario, "(function(){window.__drivingCaptureReady=false;Promise.all(Array.from(document.images).map(image=>image.decode())).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>{window.__drivingCaptureReady=true})));return true;})()")
        waitFor(scenario, "window.__drivingCaptureReady === true")
        val painted = CountDownLatch(1)
        scenario.onActivity { activity ->
            requireNotNull(findWeb(activity.window.decorView)).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { painted.countDown() }
            })
        }
        assertTrue(painted.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(200, 3000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = instrumentation.targetContext.getExternalFilesDir(null)
        File(requireNotNull(directory), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

package com.mychuannong.app.feature.common.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses the isolated .campusservicestest package and fictional merchants only. */
@RunWith(AndroidJUnit4::class)
class CampusServicesIntegrationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun allNineProvidersOpenOfflineAndNativeBackReturnsThroughTheHierarchy() {
        for (service in CampusService.entries) {
            launch(service).use { scenario ->
                waitFor(scenario, "Boolean(window.CampusServices) && document.querySelectorAll('.provider-list-card').length===3")
                waitFor(scenario, "innerHeight>200 && document.querySelector('#screen').clientHeight>200")
                assertJs(scenario, "document.documentElement.classList.contains('native-host') && !document.querySelector('.header')")
                assertJs(scenario, "document.querySelectorAll('#dock button').length===0")
                assertJs(scenario, "Array.from(document.querySelectorAll('.provider-list-card')).every(e=>e.clientWidth>e.clientHeight)")
                scenario.onActivity { activity ->
                    val web = requireNotNull(findWeb(activity.window.decorView))
                    assertTrue(web.settings.blockNetworkLoads)
                    assertFalse(web.settings.allowFileAccess)
                    assertFalse(web.settings.allowContentAccess)
                    assertFalse(web.settings.domStorageEnabled)
                }
                assertNoOverflow(scenario)
                screenshot(scenario, "campus-${service.route}-list.png")
                for (id in listOf("a", "b", "c")) {
                    click(scenario, "a[href='#/${service.route}/$id']")
                    waitFor(scenario, "document.querySelector('.venue-title h2')?.textContent===window.CAMPUS_DATA.${service.route}.providers.find(p=>p.id==='$id').name")
                    assertJs(scenario, "document.querySelectorAll('#dock button').length===1")
                    assertJs(scenario, "!document.querySelector('.metrics,.benefit-banner,.bottom-notice,.header')")
                    assertNoOverflow(scenario)
                    if (id == "a") screenshot(scenario, "campus-${service.route}-detail.png")
                    click(scenario, "[data-action=location]")
                    assertJs(scenario, "document.querySelector('.sheet').textContent.includes(window.CAMPUS_DATA.${service.route}.providers.find(p=>p.id==='$id').address)")
                    backAndWait(scenario, "!document.querySelector('.sheet') && Boolean(document.querySelector('.venue-title'))")
                    backAndWait(scenario, "document.querySelectorAll('.provider-list-card').length===3")
                }
                // Attempts to leave a native module cannot create a mismatched native title or home page.
                js(scenario, "location.hash='#/home'")
                waitFor(scenario, "location.hash==='#/${service.route}' && document.querySelectorAll('.provider-list-card').length===3")
                nativeBack(scenario)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (scenario.state != Lifecycle.State.DESTROYED && System.nanoTime() < deadline) Thread.sleep(50)
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        }
    }

    @Test fun languageCourseComparisonBookingRescheduleAndCancelWorkInTheNativeViewport() {
        launch(CampusService.Language).use { scenario ->
            waitFor(scenario, "document.querySelectorAll('.provider-list-card').length===3")
            click(scenario, "a[href='#/language/c']")
            click(scenario, "[data-action=track][data-id='托福']")
            assertJs(scenario, "document.querySelectorAll('.course-card').length===3")
            click(scenario, "[data-action=toggle-compare][data-id=toefl-start]")
            click(scenario, "[data-action=toggle-compare][data-id=toefl-weekend]")
            click(scenario, "[data-action=compare]")
            assertJs(scenario, "document.querySelector('.sheet').textContent.includes('5,180')")
            backAndWait(scenario, "!document.querySelector('.sheet')")
            click(scenario, "[data-action=filter][data-id='周末小班']")
            assertJs(scenario, "document.querySelectorAll('.course-card').length===1")
            click(scenario, "[data-action=detail][data-id=toefl-weekend]")
            assertJs(scenario, "document.querySelector('.sheet').textContent.includes('费用包含')")
            click(scenario, ".sheet [data-action=book]")
            assertJs(scenario, "document.querySelectorAll('[data-action=booking-date]').length===2")
            assertJs(scenario, "document.querySelector('[data-action=review]').disabled")
            click(scenario, "[data-action=booking-mode][data-id='线上交流']")
            finishBooking(scenario)
            assertJs(scenario, "document.querySelector('.success').textContent.includes('知行语学') && document.querySelector('.success').textContent.includes('线上交流')")
            screenshot(scenario, "campus-language-booking.png")
            click(scenario, "[data-action=records]")
            click(scenario, "[data-action=reschedule]")
            click(scenario, "[data-action=booking-date][data-id='2026-10-18']")
            click(scenario, "[data-action=booking-slot]:not(:disabled)")
            click(scenario, "[data-action=review]")
            click(scenario, "#booking-agree")
            click(scenario, "#booking-submit")
            click(scenario, "[data-action=records]")
            assertJs(scenario, "document.querySelectorAll('.record').length===1 && document.querySelector('.record').textContent.includes('2026-10-18')")
            click(scenario, "[data-action=cancel]")
            click(scenario, "[data-action=confirm-cancel]")
            assertJs(scenario, "document.querySelectorAll('.record.cancelled').length===1")
            backAndWait(scenario, "!document.querySelector('.sheet')")
            click(scenario, "[data-action=tab][data-id=teachers]")
            assertJs(scenario, "document.querySelectorAll('.teacher-card').length===2")
        }
    }

    @Test fun examDirectionsAndThirtyMinutePlanningKeepTheSelectedInstitution() {
        launch(CampusService.Exam).use { scenario ->
            waitFor(scenario, "document.querySelectorAll('.provider-list-card').length===3")
            click(scenario, "a[href='#/exam/c']")
            click(scenario, "[data-action=track][data-id='考公']")
            click(scenario, "[data-action=filter][data-id='一对一']")
            assertJs(scenario, "document.querySelectorAll('.course-card').length===1 && document.querySelector('.course-card').textContent.includes('2,280')")
            click(scenario, ".course-card [data-action=book]")
            assertJs(scenario, "document.querySelector('.sheet').textContent.includes('10:00–10:30')")
            finishBooking(scenario)
            assertJs(scenario, "document.querySelector('.success').textContent.includes('启程研习中心')")
            screenshot(scenario, "campus-exam-booking.png")
        }
    }

    @Test fun gymFacilitiesMembershipPoolAndClassesStayInteractive() {
        launch(CampusService.Gym).use { scenario ->
            waitFor(scenario, "document.querySelectorAll('.provider-list-card').length===3")
            click(scenario, "a[href='#/gym/b']")
            assertJs(scenario, "document.querySelectorAll('.facility-card').length===3")
            click(scenario, "[data-action=tab][data-id=cards]")
            assertJs(scenario, "document.querySelector('[data-action=swim][data-id=yes]').disabled")
            backAndWait(scenario, "document.querySelectorAll('.provider-list-card').length===3")
            click(scenario, "a[href='#/gym/c']")
            click(scenario, "[data-action=facility][data-id=pool]")
            click(scenario, ".sheet [data-action=book]")
            finishBooking(scenario)
            assertJs(scenario, "document.querySelector('.success').textContent.includes('星澜健身中心')")
            backAndWait(scenario, "!document.querySelector('.sheet')")
            click(scenario, "[data-action=tab][data-id=cards]")
            click(scenario, "[data-action=swim][data-id=yes]")
            click(scenario, "[data-action=card][data-id=year]")
            click(scenario, "[data-action=membership]")
            assertJs(scenario, "document.querySelector('#member-submit').disabled")
            click(scenario, "#member-agree")
            click(scenario, "#member-submit")
            assertJs(scenario, "document.querySelector('.success').textContent.includes('办卡意向已保存')")
            screenshot(scenario, "campus-gym-membership.png")
            backAndWait(scenario, "!document.querySelector('.sheet')")
            click(scenario, "[data-action=tab][data-id=classes]")
            assertJs(scenario, "document.querySelectorAll('.class-card').length>0")
            click(scenario, "[data-action=class-book]:not(:disabled)")
            assertNoOverflow(scenario)
            assertJs(scenario, "document.querySelectorAll('[data-action=booking-slot]').length===1")
        }
    }

    private fun launch(service: CampusService): ActivityScenario<CampusServicesActivity> =
        ActivityScenario.launch(CampusServicesActivity.createIntent(instrumentation.targetContext, service))

    private fun finishBooking(scenario: ActivityScenario<CampusServicesActivity>) {
        click(scenario, "[data-action=booking-date]")
        click(scenario, "[data-action=booking-slot]:not(:disabled)")
        assertNoOverflow(scenario)
        click(scenario, "[data-action=review]")
        assertJs(scenario, "document.querySelector('#booking-submit').disabled")
        click(scenario, "#booking-agree")
        click(scenario, "#booking-submit")
        waitFor(scenario, "Boolean(document.querySelector('.success'))")
    }

    private fun backAndWait(scenario: ActivityScenario<CampusServicesActivity>, expression: String) {
        nativeBack(scenario)
        waitFor(scenario, expression)
    }

    private fun assertNoOverflow(scenario: ActivityScenario<CampusServicesActivity>) {
        assertJs(scenario, "document.querySelector('#screen').scrollWidth === document.querySelector('#screen').clientWidth")
        waitFor(scenario, "Array.from(document.getAnimations()).every(a=>a.playState==='finished')")
        assertJs(scenario, "Array.from(document.querySelectorAll('.sheet,.sheet-footer,#dock')).every(e=>{const r=e.getBoundingClientRect();return r.left>=0 && r.right<=innerWidth+1 && r.top>=0 && r.bottom<=innerHeight+1})")
    }
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun js(scenario: ActivityScenario<CampusServicesActivity>, expression: String): String {
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

    private fun assertJs(scenario: ActivityScenario<CampusServicesActivity>, expression: String) {
        assertEquals(expression, "true", js(scenario, "Boolean($expression)"))
    }

    private fun waitFor(scenario: ActivityScenario<CampusServicesActivity>, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < deadline) {
            if (js(scenario, "Boolean($expression)") == "true") return
            Thread.sleep(50)
        }
        assertJs(scenario, expression)
    }

    private fun click(scenario: ActivityScenario<CampusServicesActivity>, selector: String) {
        val quoted = org.json.JSONObject.quote(selector)
        assertEquals(selector, "true", js(scenario, "(function(){const button=document.querySelector($quoted);if(!button||button.disabled)return false;window.__tapReady=false;button.click();requestAnimationFrame(()=>requestAnimationFrame(()=>window.__tapReady=true));return true;})()"))
        waitFor(scenario, "window.__tapReady===true")
    }

    private fun nativeBack(scenario: ActivityScenario<CampusServicesActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun screenshot(scenario: ActivityScenario<CampusServicesActivity>, name: String) {
        waitFor(scenario, "Array.from(document.getAnimations()).every(animation => animation.playState === 'finished')")
        js(scenario, "(function(){window.__servicesCaptureReady=false;Promise.all(Array.from(document.images).map(image=>image.decode())).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>{window.__servicesCaptureReady=true})));return true;})()")
        waitFor(scenario, "window.__servicesCaptureReady === true")
        val painted = CountDownLatch(1)
        scenario.onActivity { activity ->
            requireNotNull(findWeb(activity.window.decorView)).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { painted.countDown() }
            })
        }
        assertTrue(painted.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(200, 3000)
        // The compositor can present the previous frame just after the WebView callback.
        Thread.sleep(300)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = instrumentation.targetContext.getExternalFilesDir(null)
        File(requireNotNull(directory), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

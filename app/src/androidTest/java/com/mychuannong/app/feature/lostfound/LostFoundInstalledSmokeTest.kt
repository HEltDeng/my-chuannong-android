package com.mychuannong.app.feature.lostfound

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
import com.mychuannong.app.freshNews.data.local.model.LostFoundType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only UI smoke against the actual installed Debug APK; never seeds or changes records. */
@RunWith(AndroidJUnit4::class)
class LostFoundInstalledSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun installedPageAndSoftwareKeyboardRemainUsable() {
        val context = instrumentation.targetContext
        check(context.packageName == "com.mychuannong.app.debug")
        ActivityScenario.launch<LostFoundActivity>(LostFoundActivity.listIntent(context)).use { scenario ->
            waitFor(scenario,"window.CampusLostFound?.ready === true && document.fonts.status==='loaded'")
            assertJs(scenario,"document.querySelector('h1')?.textContent === '失物招领' && Boolean(document.querySelector('.lf-fab'))")
            screenshot(scenario,"lost-found-installed-home.png")
        }
        ActivityScenario.launch<LostFoundActivity>(LostFoundActivity.publishIntent(context,LostFoundType.LOST)).use { scenario ->
            waitFor(scenario,"window.CampusLostFound?.ready === true")
            tap(scenario,"[aria-label=联系方式]")
            scenario.onActivity { activity ->
                val web=requireNotNull(findWeb(activity.window.decorView))
                WindowInsetsControllerCompat(activity.window,web).show(WindowInsetsCompat.Type.ime())
            }
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            var shown=false
            while(!shown && System.nanoTime()<deadline) {
                scenario.onActivity { shown=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true }
                if(!shown) Thread.sleep(100)
            }
            assertTrue("A real Android IME must be visible",shown)
            // Validate the active Android IME mode. This emulator uses Gboard's
            // floating hardware-keyboard toolbar, whose bottom inset is legitimately zero.
            js(scenario,"document.querySelector('[aria-label=联系方式]').scrollIntoView({block:'center'});true")
            Thread.sleep(300)
            assertJs(scenario,"(()=>{const e=document.querySelector('[aria-label=联系方式]').getBoundingClientRect();const d=document.querySelector('.lf-dock').getBoundingClientRect();return document.activeElement===document.querySelector('[aria-label=联系方式]') && e.top>=document.querySelector('.lf-header').getBoundingClientRect().bottom && e.bottom<=d.top && d.bottom<=innerHeight+1})()")
            screenshot(scenario,"lost-found-installed-keyboard.png")
        }
    }

    private fun tap(scenario: ActivityScenario<LostFoundActivity>, selector: String) {
        js(scenario, "document.querySelector(" + org.json.JSONObject.quote(selector) + ").scrollIntoView({block:'center'});true")
        instrumentation.waitForIdleSync()
        val expression = "JSON.stringify((()=>{const r=document.querySelector(" + org.json.JSONObject.quote(selector) + ").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth}})())"
        val result = org.json.JSONObject(org.json.JSONArray("[" + js(scenario, expression) + "]").getString(0))
        val point = FloatArray(2)
        scenario.onActivity { activity ->
            val web = requireNotNull(findWeb(activity.window.decorView)); val origin = IntArray(2); web.getLocationOnScreen(origin)
            val scale = web.width / result.getDouble("width")
            point[0] = (origin[0] + result.getDouble("x") * scale).toFloat(); point[1] = (origin[1] + result.getDouble("y") * scale).toFloat()
        }
        val now = android.os.SystemClock.uptimeMillis()
        val pointer = android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER }
        fun event(action: Int, time: Long): android.view.MotionEvent {
            val coordinates = android.view.MotionEvent.PointerCoords().apply { x = point[0]; y = point[1]; pressure = if (action == android.view.MotionEvent.ACTION_UP) 0f else 1f; size = 1f }
            return android.view.MotionEvent.obtain(now, time, action, 1, arrayOf(pointer), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        }
        val down = event(android.view.MotionEvent.ACTION_DOWN, now)
        val up = event(android.view.MotionEvent.ACTION_UP, now + 50)
        instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up)
        down.recycle(); up.recycle()
    }

    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun js(scenario: ActivityScenario<LostFoundActivity>, expression: String): String {
        val completed = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            requireNotNull(findWeb(activity.window.decorView)).evaluateJavascript(expression) {
                result = it
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", completed.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun assertJs(scenario: ActivityScenario<LostFoundActivity>, expression: String) {
        assertEquals(expression, "true", js(scenario, "Boolean($expression)"))
    }

    private fun waitFor(scenario: ActivityScenario<LostFoundActivity>, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < deadline) {
            if (js(scenario, "Boolean($expression)") == "true") return
            Thread.sleep(50)
        }
        assertJs(scenario, expression)
    }

    private fun click(scenario: ActivityScenario<LostFoundActivity>, selector: String) {
        android.util.Log.i("LostFoundTest", "click $selector")
        val quoted = org.json.JSONObject.quote(selector)
        assertEquals("true", js(scenario, "(()=>{const el=document.querySelector($quoted);if(!el||el.disabled)return false;el.click();return true})()"))
    }

    private fun fill(scenario: ActivityScenario<LostFoundActivity>, name: String, value: String) {
        val selector = org.json.JSONObject.quote("[name=$name]")
        val quoted = org.json.JSONObject.quote(value)
        js(scenario, "(()=>{const el=document.querySelector($selector);el.value=$quoted;el.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
    }

    private fun nativeBack(scenario: ActivityScenario<LostFoundActivity>) {
        android.util.Log.i("LostFoundTest", "back route=" + js(scenario, "location.hash"))
        scenario.onActivity {
            val web = requireNotNull(findWeb(it.window.decorView))
            val history = web.copyBackForwardList()
            android.util.Log.i("LostFoundTest", "native back=${web.canGoBack()}; entries=${history.size}; index=${history.currentIndex}")
            it.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun screenshot(scenario: ActivityScenario<LostFoundActivity>, name: String) {
        js(scenario, "(()=>{window.captureReady=false;requestAnimationFrame(()=>requestAnimationFrame(()=>{window.captureReady=true}));return true})()")
        waitFor(scenario, "window.captureReady === true")
        val painted = CountDownLatch(1)
        scenario.onActivity { activity ->
            requireNotNull(findWeb(activity.window.decorView)).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { painted.countDown() }
            })
        }
        assertTrue(painted.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        Thread.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

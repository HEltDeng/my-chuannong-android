package com.mychuannong.app.feature.lostfound

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
import com.mychuannong.app.freshNews.data.local.model.LostFoundType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic data only; the suffix guard prevents any access to the user's installed data. */
@RunWith(AndroidJUnit4::class)
class LostFoundGuestAccessDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun requireIsolatedGuestInstall() {
        check(context.packageName.endsWith(".lostfoundtest"))
        assertFalse(AccountAccess.isSignedIn)
    }

    @Test fun bothServiceRoutesOpenApprovedPageWithoutAccountOrBackend() {
        listOf<(Context) -> Unit>(Route::goLostFound, Route::goLostFoundChannel).forEach { open ->
            var target: Intent? = null
            open(object : ContextWrapper(context) { override fun startActivity(intent: Intent) { target = intent } })
            val intent = requireNotNull(target)
            assertEquals(LostFoundActivity::class.java.name, intent.component?.className)
            ActivityScenario.launch<LostFoundActivity>(intent).use { scenario ->
                reset(scenario)
                assertJs(scenario, "document.querySelector('h1').textContent === '失物招领' && document.querySelectorAll('.lf-item').length === 4")
                assertJs(scenario, "!document.body.innerText.match(/收藏|评论|分类筛选/) && document.querySelectorAll('input').length === 2")
                waitFor(scenario, "Array.from(document.querySelectorAll('.lf-thumb')).every(i=>i.complete && i.naturalWidth>0) && document.fonts.status==='loaded'")
                screenshot(scenario,"lost-found-home.png")
            }
        }
    }

    @Test fun campusSearchTabsEmptyStateAndContactWork() {
        launch().use { scenario ->
            reset(scenario)
            choose(scenario,".lf-campus","雅安校区")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
            assertJs(scenario,"document.querySelector('.lf-item').textContent.includes('雨伞')")
            click(scenario,".lf-tabs button:first-child")
            waitFor(scenario,"document.querySelector('.lf-item').textContent.includes('宿舍钥匙')")
            fillSelector(scenario,"[aria-label='搜索物品或地点']","不存在")
            waitFor(scenario,"Boolean(document.querySelector('.lf-empty'))")
            click(scenario,".lf-empty button")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 3")
            fillSelector(scenario,"[aria-label='搜索物品或地点']","成都 校园卡")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
            click(scenario,".lf-item")
            waitFor(scenario,"Boolean(document.querySelector('.lf-detail'))")
            screenshot(scenario,"lost-found-detail.png")
            click(scenario,".lf-dock .lf-primary")
            waitFor(scenario,"Boolean(document.querySelector('.lf-contact-card'))")
            screenshot(scenario,"lost-found-contact.png")
            tap(scenario,".native-sheet .lf-primary")
            waitFor(scenario,"document.querySelector('.lf-toast')?.textContent.includes('已复制')")
            scenario.onActivity {
                val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                assertEquals("campus_demo",clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
            nativeBack(scenario)
            waitFor(scenario,"!document.querySelector('[role=dialog]')")
            nativeBack(scenario)
            waitFor(scenario,"Boolean(document.querySelector('.lf-search'))")
        }
    }

    @Test fun publicationPhotoEditCompletionDeleteAndUndoSurviveRecreation() {
        launch().use { scenario ->
            reset(scenario)
            click(scenario,".lf-fab")
            waitFor(scenario,"Boolean(document.querySelector('#lf-publish-form'))")
            click(scenario,".lf-dock .lf-primary")
            waitFor(scenario,"document.querySelector('[role=alert]')?.textContent === '请填写物品名称'")
            fillSelector(scenario,"[aria-label='物品名称']","<img src=x>测试卡")
            fillSelector(scenario,"[aria-label='具体地点']","图书馆一楼")
            fillSelector(scenario,"[aria-label='联系方式']","lost_found_test")
            fillSelector(scenario,"[aria-label='补充描述']","合成测试数据，确认照片和文字能够保留。")
            choose(scenario,".lf-select","都江堰校区")
            click(scenario,".lf-photo-picker")
            waitFor(scenario,"Boolean(document.querySelector('.lf-photo-options'))")
            click(scenario,".lf-photo-options button:first-child")
            waitFor(scenario,"Boolean(document.querySelector('.lf-photo-picker img'))")
            screenshot(scenario,"lost-found-publish.png")
            click(scenario,".lf-dock .lf-primary")
            waitFor(scenario,"Boolean(document.querySelector('.lf-detail'))")
            assertJs(scenario,"document.querySelector('.lf-detail h2').textContent === '<img src=x>测试卡' && !document.querySelector('.lf-detail h2 img')")
            scenario.recreate()
            ready(scenario)
            click(scenario,".lf-text-button")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
            click(scenario,".lf-item")
            waitFor(scenario,"document.querySelector('.lf-detail-image')?.naturalWidth > 0")
            click(scenario,".lf-management button:first-child")
            waitFor(scenario,"document.querySelector('[aria-label=联系方式]')?.value === 'lost_found_test'")
            fillSelector(scenario,"[aria-label='物品名称']","修改后的校园卡")
            click(scenario,".lf-dock .lf-primary")
            waitFor(scenario,"document.querySelector('.lf-detail h2')?.textContent === '修改后的校园卡'")
            click(scenario,".lf-dock > .lf-primary")
            waitFor(scenario,"document.querySelector('.lf-label')?.textContent.includes('已完成')")
            screenshot(scenario,"lost-found-managed.png")
            scenario.recreate(); ready(scenario)
            click(scenario,".lf-text-button")
            waitFor(scenario,"Boolean(document.querySelector('.lf-mine-tabs'))")
            click(scenario,".lf-mine-tabs button:last-child")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
            click(scenario,".lf-item")
            waitFor(scenario,"Boolean(document.querySelector('.lf-delete'))")
            click(scenario,".lf-delete")
            waitFor(scenario,"Boolean(document.querySelector('.lf-danger'))")
            click(scenario,".lf-danger")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 0 && Boolean(document.querySelector('.lf-toast button'))")
            click(scenario,".lf-toast button")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
            scenario.recreate(); ready(scenario)
            click(scenario,".lf-text-button")
            waitFor(scenario,"document.querySelector('.lf-item-title')?.textContent === '修改后的校园卡'")
            click(scenario,".lf-item"); waitFor(scenario,"Boolean(document.querySelector('.lf-delete'))")
            click(scenario,".lf-delete"); waitFor(scenario,"Boolean(document.querySelector('.lf-danger'))"); click(scenario,".lf-danger")
            waitFor(scenario,"document.querySelectorAll('.lf-item').length === 0")
            scenario.recreate(); ready(scenario); click(scenario,".lf-text-button")
            waitFor(scenario,"Boolean(document.querySelector('.lf-empty')) && document.querySelectorAll('.lf-item').length === 0")
        }
    }

    @Test fun nativeBackClosesSheetsAndProtectsUnsavedDraft() {
        launch().use { scenario ->
            reset(scenario); click(scenario,".lf-campus")
            waitFor(scenario,"Boolean(document.querySelector('[role=dialog]'))")
            screenshot(scenario,"lost-found-campus.png")
            nativeBack(scenario); waitFor(scenario,"!document.querySelector('[role=dialog]')")
            click(scenario,".lf-fab"); waitFor(scenario,"Boolean(document.querySelector('#lf-publish-form'))")
            fillSelector(scenario,"[aria-label=物品名称]","保留输入")
            nativeBack(scenario)
            waitFor(scenario,"document.querySelector('[role=dialog]')?.textContent.includes('放弃这次编辑')")
            click(scenario,".lf-sheet-actions .lf-primary")
            waitFor(scenario,"!document.querySelector('[role=dialog]')")
            assertJs(scenario,"document.querySelector('[aria-label=物品名称]').value === '保留输入'")
            nativeBack(scenario); waitFor(scenario,"Boolean(document.querySelector('.lf-sheet-actions'))")
            click(scenario,".lf-sheet-actions .lf-secondary")
            waitFor(scenario,"Boolean(document.querySelector('.lf-search'))")
            nativeBack(scenario)
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && System.nanoTime()<deadline) Thread.sleep(50)
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.state)
        }
    }

    @Test fun directPublishAndMissingDetailIntentsStayUsable() {
        LostFoundType.entries.forEach { type ->
            ActivityScenario.launch<LostFoundActivity>(LostFoundActivity.publishIntent(context,type)).use { scenario ->
                ready(scenario)
                assertJs(scenario,"document.querySelector('.lf-segment [aria-selected=true]').textContent === '${if(type==LostFoundType.LOST) "我丢了东西" else "我捡到东西"}'")
            }
        }
        ActivityScenario.launch<LostFoundActivity>(LostFoundActivity.detailIntent(context,"missing/entry?test")).use { scenario ->
            ready(scenario)
            assertJs(scenario,"document.querySelector('.lf-empty').textContent.includes('不存在或已被删除')")
            click(scenario,".lf-empty button")
            waitFor(scenario,"Boolean(document.querySelector('.lf-search'))")
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun imageChooserReturnsASelectedImageToThePublishForm() {
        val resolver = instrumentation.targetContext.contentResolver
        val name = "lost-found-test-" + System.currentTimeMillis() + ".png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LostFoundTests")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(90, 145, 105))
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            ActivityScenario.launch(LostFoundActivity::class.java).use { scenario ->
                reset(scenario)
                click(scenario, ".lf-fab")
                waitFor(scenario, "Boolean(document.querySelector('#lf-publish-form'))")
                click(scenario, ".lf-photo-picker")
                waitFor(scenario,"Boolean(document.querySelector('.lf-photo-options'))")
                tap(scenario, ".native-sheet .lf-primary")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var selected = false
                while (!selected && System.nanoTime() < deadline) {
                    fun listControl(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                        if (node == null) return null
                        if (node.isClickable && node.contentDescription?.toString()?.equals("List view", ignoreCase = true) == true) return node
                        for (index in 0 until node.childCount) listControl(node.getChild(index))?.let { return it }
                        return null
                    }
                    listControl(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    val nodes = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("lost-found-test-").orEmpty()
                    if (nodes.isNotEmpty()) {
                        var node: android.view.accessibility.AccessibilityNodeInfo? = nodes.first()
                        while (node != null && !node.isClickable) node = node.parent
                        selected = node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
                    if (!selected) Thread.sleep(100)
                }
                if (!selected) {
                    val capture = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "lost-found-picker-debug.png").outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    capture.recycle()
                    android.util.Log.i("LostFoundPhoto", "active picker package=" + instrumentation.uiAutomation.rootInActiveWindow?.packageName)
                }
                assertTrue("The selected test image must be visible in the document picker", selected)
                waitFor(scenario,"document.querySelector('.lf-photo-picker img')?.src.startsWith('data:image/png')")
                fillSelector(scenario,"[aria-label=物品名称]","系统照片选择检查")
                fillSelector(scenario,"[aria-label=具体地点]","图书馆")
                fillSelector(scenario,"[aria-label=联系方式]","image_test")
                tap(scenario,"[aria-label=物品名称]")
                Thread.sleep(700)
                screenshot(scenario,"lost-found-keyboard.png")
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                Thread.sleep(300)
                click(scenario,".lf-dock .lf-primary")
                waitFor(scenario,"document.querySelector('.lf-detail-image')?.naturalWidth === 80")
                scenario.recreate(); ready(scenario)
                click(scenario,".lf-text-button")
                waitFor(scenario,"document.querySelectorAll('.lf-item').length === 1")
                click(scenario,".lf-item")
                waitFor(scenario,"document.querySelector('.lf-detail-image')?.naturalWidth === 80")
                screenshot(scenario,"lost-found-uploaded.png")
            }
        } finally { resolver.delete(uri, null, null) }
    }


    private fun launch() = ActivityScenario.launch<LostFoundActivity>(LostFoundActivity.listIntent(context))
    private fun ready(scenario: ActivityScenario<LostFoundActivity>) = waitFor(scenario,"window.CampusLostFound?.ready === true")
    private fun reset(scenario: ActivityScenario<LostFoundActivity>) {
        ready(scenario)
        js(scenario,"(()=>{window.fixtureReloading=true;const r=indexedDB.open('mychuannong-lost-found-v2',1);r.onsuccess=()=>{const t=r.result.transaction('content','readwrite');t.objectStore('content').delete('state');t.oncomplete=()=>{r.result.close();location.reload()}};return true})()")
        waitFor(scenario,"typeof window.fixtureReloading === 'undefined' && window.CampusLostFound?.ready === true")
    }
    private fun choose(scenario: ActivityScenario<LostFoundActivity>,selector:String,value:String) {
        click(scenario,selector); waitFor(scenario,"Boolean(document.querySelector('.lf-sheet-options'))")
        js(scenario,"Array.from(document.querySelectorAll('.lf-sheet-options button')).find(b=>b.textContent==="+org.json.JSONObject.quote(value)+").click()")
        waitFor(scenario,"!document.querySelector('[role=dialog]')")
    }
    private fun fillSelector(scenario: ActivityScenario<LostFoundActivity>,selector:String,value:String) {
        js(scenario,"(()=>{const e=document.querySelector("+org.json.JSONObject.quote(selector)+");const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,"+org.json.JSONObject.quote(value)+");e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
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

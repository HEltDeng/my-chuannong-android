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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses the isolated test package and synthetic listings only. */
@RunWith(AndroidJUnit4::class)
class SecondhandIntegrationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun campusAndFullTextSearchCombineAcrossSellAndWant() {
        ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
            seed(scenario, populated = true)
            assertJs(scenario, "document.querySelectorAll('.item').length === 3 && !document.querySelector('.search-form')")
            click(scenario, ".campus-filter")
            waitFor(scenario, "document.querySelectorAll('[role=radio]').length === 4")
            screenshot(scenario, "secondhand-campus.png")
            nativeBack(scenario)
            waitFor(scenario, "!document.querySelector('.sheet') && Boolean(document.querySelector('.hero'))")
            choose(scenario, ".campus-filter", "雅安校区")
            assertJs(scenario, "document.querySelectorAll('.item').length === 1 && document.querySelector('.item').textContent.includes('雅安校区')")
            click(scenario, ".tabs [data-value=want]")
            assertJs(scenario, "document.querySelector('.campus-filter').textContent === '雅安校区' && document.querySelectorAll('.wanted').length === 1")
            choose(scenario, ".campus-filter", "成都校区")
            assertJs(scenario, "document.querySelectorAll('.wanted').length === 1 && document.querySelector('.wanted').textContent.includes('成都校区')")
            choose(scenario, ".campus-filter", "都江堰校区")
            assertJs(scenario, "document.querySelectorAll('.wanted').length === 1 && document.querySelector('.wanted').textContent.includes('都江堰校区')")
            choose(scenario, ".campus-filter", "全部校区")
            click(scenario, ".tabs [data-value=sell]")
            click(scenario, ".search-trigger")
            fillSelector(scenario, "[name=keyword]", " 高速材料 ")
            click(scenario, ".search-submit")
            assertJs(scenario, "document.querySelectorAll('.item').length === 2")
            choose(scenario, ".campus-filter", "成都校区")
            assertJs(scenario, "document.querySelectorAll('.item').length === 1 && document.querySelector('.item h3').textContent === '课堂资料整理包'")
            click(scenario, ".tabs [data-value=want]")
            assertJs(scenario, "document.querySelectorAll('.wanted').length === 1 && document.querySelector('.campus-filter').textContent === '成都校区'")
            choose(scenario, ".campus-filter", "都江堰校区")
            assertJs(scenario, "document.querySelector('.empty').textContent.trim() === '暂无相关信息'")
            click(scenario, ".search-cancel")
            assertJs(scenario, "document.querySelectorAll('.wanted').length === 1")
            choose(scenario, ".campus-filter", "全部校区")
            click(scenario, ".tabs [data-value=sell]")
            click(scenario, ".search-trigger")
            fillSelector(scenario, "[name=keyword]", "成都校区")
            assertJs(scenario, "document.querySelectorAll('.item').length === 1")
            fillSelector(scenario, "[name=keyword]", "书籍")
            assertJs(scenario, "document.querySelectorAll('.item').length === 1")
            fillSelector(scenario, "[name=keyword]", "高速材料 成都")
            assertJs(scenario, "document.querySelectorAll('.item').length === 1")
            assertJs(scenario, "!document.body.innerText.match(/模拟|演示|预算|面议|¥/) && !document.querySelector('select')")
            screenshot(scenario, "secondhand-search.png")
        }
    }

    @Test fun emptyStartPublishingCustomPickersAndReloadWorkWithoutPrices() {
        ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
            seed(scenario, populated = false)
            assertJs(scenario, "document.querySelector('.empty').textContent.trim() === '暂无相关信息' && document.querySelectorAll('.item,.wanted').length === 0")
            screenshot(scenario, "secondhand-empty.png")
            click(scenario, ".publish-add")
            waitFor(scenario, "Boolean(document.querySelector('#publish-form'))")
            assertJs(scenario, "Boolean(document.querySelector('#publish-images')) && !document.querySelector('select')")
            fill(scenario, "title", "<img src=x onerror=alert(1)>高速材料")
            fill(scenario, "description", "整理了具体章节的课堂记录。")
            click(scenario, "[data-action=publish-category]")
            waitFor(scenario, "document.querySelectorAll('[role=radio]').length === 7")
            screenshot(scenario, "secondhand-categories.png")
            click(scenario, "[role=radio][data-value='其他']")
            waitFor(scenario, "!document.querySelector('.sheet')")
            choose(scenario, "[data-action=publish-campus]", "成都校区")
            assertJs(scenario, "document.querySelector('[name=title]').value.includes('<img src=x') && document.querySelector('#selected-category').textContent === '其他'")
            click(scenario, "#publish-form [type=submit]")
            assertJs(scenario, "Boolean(document.querySelector('#publish-form')) && !document.querySelector('[name=wechat]').validity.valid")
            fill(scenario, "wechat", "secondhand_contact_test")
            screenshot(scenario, "secondhand-publish.png")
            click(scenario, "#publish-form [type=submit]")
            waitFor(scenario, "document.querySelectorAll('.item').length === 1")
            reload(scenario)
            assertJs(scenario, "document.querySelector('.item h3').textContent.includes('<img src=x') && !document.querySelector('.item h3 img')")
            click(scenario, ".item")
            waitFor(scenario, "Boolean(document.querySelector('.body'))")
            assertJs(scenario, "!document.body.innerText.match(/价格|预算|面议|¥|模拟|演示/) && !document.querySelector('.panel h2 img')")
            nativeBack(scenario)
            waitFor(scenario, "Boolean(document.querySelector('.publish-add'))")
            click(scenario, ".publish-add")
            waitFor(scenario, "Boolean(document.querySelector('#publish-want'))")
            click(scenario, "#publish-want")
            assertJs(scenario, "!document.querySelector('input[type=file]') && !document.querySelector('select')")
            fill(scenario, "title", "求购高速材料课堂笔记")
            fill(scenario, "description", "   ")
            click(scenario, "#publish-form [type=submit]")
            assertJs(scenario, "Boolean(document.querySelector('#publish-form')) && !document.querySelector('[name=description]').validity.valid")
            fill(scenario, "description", "想了解具体章节内容，校内交接。")
            fill(scenario, "phone", "13800000000")
            click(scenario, "#publish-sell")
            assertJs(scenario, "document.querySelector('[name=title]').value === ''")
            click(scenario, "#publish-want")
            assertJs(scenario, "document.querySelector('[name=title]').value === '求购高速材料课堂笔记' && document.querySelector('[name=phone]').value === '13800000000'")
            choose(scenario, "[data-action=publish-category]", "其他")
            choose(scenario, "[data-action=publish-campus]", "雅安校区")
            click(scenario, "#publish-form [type=submit]")
            waitFor(scenario, "document.querySelectorAll('.wanted').length === 1")
            reload(scenario)
            assertJs(scenario, "document.querySelector('.wanted h3').textContent === '求购高速材料课堂笔记' && !document.body.innerText.match(/价格|预算|面议|¥|模拟|演示/)")
        }
    }

    @Test fun myPublicationsCanBeEditedDeletedAndReopenedWithoutTouchingOtherPosts() {
        ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
            seed(scenario, populated = true)
            assertJs(scenario, "document.querySelector('[data-action=mine]').previousElementSibling.textContent === '求购' && document.querySelector('.tabs').nextElementSibling.classList.contains('campus-filter')")
            screenshot(scenario, "secondhand-mine-entry.png")
            click(scenario, "[data-action=mine]")
            waitFor(scenario, "location.hash === '#mine' && document.querySelector('.mine-empty').textContent.includes('还没有发布信息')")
            click(scenario, ".mine-empty [data-action=publish]")
            waitFor(scenario, "Boolean(document.querySelector('#publish-form'))")
            fill(scenario, "title", "我发布的教材")
            fill(scenario, "description", "章节完整，校内交接。")
            fill(scenario, "wechat", "my_sale_test")
            choose(scenario, "[data-action=publish-campus]", "雅安校区")
            click(scenario, "#publish-form [type=submit]")
            waitFor(scenario, "location.hash === '#mine' && document.querySelectorAll('.my-post').length === 1")
            click(scenario, ".mine-intro .publish-add")
            waitFor(scenario, "Boolean(document.querySelector('#publish-want'))")
            click(scenario, "#publish-want")
            fill(scenario, "title", "我发布的求购资料")
            fill(scenario, "description", "需要课程笔记和章节重点。")
            fill(scenario, "phone", "13800000000")
            choose(scenario, "[data-action=publish-campus]", "成都校区")
            click(scenario, "#publish-form [type=submit]")
            waitFor(scenario, "location.hash === '#mine' && document.querySelectorAll('.my-post').length === 2")
            assertJs(scenario, "!document.querySelector('#mine-listing').textContent.includes('发布同学') && document.querySelectorAll('.edit-post,.delete-post').length === 4")
            click(scenario, "[data-action=mine-filter][data-value=sell]")
            assertJs(scenario, "document.querySelectorAll('.my-post').length === 1 && document.querySelector('.my-post h3').textContent === '我发布的教材'")
            click(scenario, "[data-action=mine-filter][data-value=want]")
            click(scenario, ".edit-post")
            waitFor(scenario, "location.hash.startsWith('#edit/') && document.querySelector('[name=phone]').value === '13800000000'")
            assertJs(scenario, "!document.querySelector('#publish-want,input[type=file]')")
            fill(scenario, "title", "更新后的求购：高速材料")
            fill(scenario, "description", "希望有完整的课堂笔记。")
            choose(scenario, "[data-action=publish-category]", "其他")
            choose(scenario, "[data-action=publish-campus]", "都江堰校区")
            screenshot(scenario, "secondhand-edit-wanted.png")
            click(scenario, "#publish-form [type=submit]")
            waitFor(scenario, "location.hash === '#mine' && document.querySelector('.my-post h3').textContent === '更新后的求购：高速材料'")
            assertJs(scenario, "document.querySelector('.my-post-meta').textContent.includes('都江堰校区')")
            click(scenario, "[data-action=mine-filter][data-value=all]")
            reload(scenario)
            assertJs(scenario, "document.querySelectorAll('.my-post').length === 2 && document.querySelector('#mine-listing').textContent.includes('更新后的求购：高速材料')")
            screenshot(scenario, "secondhand-mine.png")
            click(scenario, "[data-action=mine-filter][data-value=want]")
            click(scenario, ".my-post-detail")
            waitFor(scenario, "document.querySelector('.contact-value')?.textContent === '13800000000'")
            nativeBack(scenario)
            waitFor(scenario, "Boolean(document.querySelector('.delete-post'))")
            click(scenario, ".delete-post")
            waitFor(scenario, "Boolean(document.querySelector('.delete-sheet'))")
            screenshot(scenario, "secondhand-delete.png")
            click(scenario, ".delete-sheet [data-action=close-sheet]")
            waitFor(scenario, "!document.querySelector('.delete-sheet') && document.querySelectorAll('.my-post').length === 1")
            click(scenario, ".delete-post")
            waitFor(scenario, "Boolean(document.querySelector('.confirm-delete'))")
            click(scenario, ".confirm-delete")
            waitFor(scenario, "!document.querySelector('.delete-sheet') && document.querySelectorAll('.my-post').length === 0")
            click(scenario, "[data-action=mine-filter][data-value=all]")
            reload(scenario)
            assertJs(scenario, "document.querySelectorAll('.my-post').length === 1 && document.querySelector('.my-post h3').textContent === '我发布的教材'")
            nativeBack(scenario)
            waitFor(scenario, "Boolean(document.querySelector('.hero'))")
            choose(scenario, ".campus-filter", "全部校区")
            assertJs(scenario, "document.querySelectorAll('.wanted').length === 3 && !document.querySelector('#listing').textContent.includes('更新后的求购：高速材料')")
        }
    }

    @Test fun contactsAreVisibleAndCopyableOnlyInsideSaleAndWantedDetails() {
        ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
            seed(scenario, populated = true)
            click(scenario, ".tabs [data-value=want]")
            assertJs(scenario, "!document.querySelector('.contact-details,.copy-contact') && !document.body.innerText.match(/secondhand_contact_test|13800000000|聊一聊/) && !document.querySelector('[data-action=chat]')")
            screenshot(scenario, "secondhand-wanted-list.png")
            click(scenario, ".wanted[data-listing-id='w-ya'] .contact-entry")
            waitFor(scenario, "location.hash === '#detail/w-ya' && document.querySelectorAll('.contact-value').length === 2")
            assertJs(scenario, "document.querySelector('.contact-value').textContent === 'secondhand_contact_test'")
            screenshot(scenario, "secondhand-wanted-details.png")
            tap(scenario, "[data-field=wechat]")
            waitFor(scenario, "document.querySelector('#toast').textContent === '微信号已复制，可前往微信添加'")
            assertClipboard(scenario, "secondhand_contact_test")
            tap(scenario, "[data-field=phone]")
            waitFor(scenario, "document.querySelector('#toast').textContent === '联系电话已复制'")
            assertClipboard(scenario, "13800000000")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.wanted').length === 3 && !document.querySelector('.contact-details')")
            click(scenario, ".wanted[data-listing-id='w-ya'] .wanted-detail")
            waitFor(scenario, "location.hash === '#detail/w-ya' && Boolean(document.querySelector('.contact-heading'))")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.wanted').length === 3")
            click(scenario, ".tabs [data-value=sell]")
            assertJs(scenario, "!document.querySelector('.contact-details,.copy-contact') && !document.body.innerText.match(/secondhand_contact_test|13800000000/)")
            click(scenario, ".item[data-id='s-ya']")
            waitFor(scenario, "location.hash === '#detail/s-ya' && document.querySelectorAll('.contact-value').length === 2")
            assertJs(scenario, "!document.querySelector('.chat-composer,#chat-form,[data-action=chat]') && !document.body.innerText.match(/价格|面议|预算|聊一聊/)")
            screenshot(scenario, "secondhand-sale-contacts.png")
            tap(scenario, "[data-field=wechat]")
            waitFor(scenario, "document.querySelector('#toast').textContent === '微信号已复制，可前往微信添加'")
            assertClipboard(scenario, "secondhand_contact_test")
            nativeBack(scenario)
            waitFor(scenario, "document.querySelectorAll('.item').length === 3")
            js(scenario, "location.hash='chat/w-ya';true")
            waitFor(scenario, "Boolean(document.querySelector('.hero')) && !document.querySelector('#chat-form')")
            nativeBack(scenario)
            waitFor(scenario, "location.hash === '' && document.querySelectorAll('.item').length === 3")
        }
    }

    @Test fun oldSavedListingsRemainReadableWhenContactDataIsMissing() {
        ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
            seed(scenario, populated = true)
            js(scenario, "(()=>{window.fixtureReloading=true;const r=indexedDB.open('mychuannong-secondhand',1);r.onsuccess=()=>{const tx=r.result.transaction('content','readwrite');const s=tx.objectStore('content');const get=s.get('state');get.onsuccess=()=>{const v=get.result;v.version=1;v.listings.forEach(x=>{delete x.wechat;delete x.phone});v.threads=[{listingId:'w-ya',messages:[{id:'old',text:'old content'}]}];s.put(v,'state')};tx.oncomplete=()=>{r.result.close();location.reload()}};return true})()")
            waitFor(scenario, "typeof window.fixtureReloading === 'undefined' && window.CampusSecondhand?.ready === true")
            assertJs(scenario, "document.querySelectorAll('.item').length === 3")
            click(scenario, ".item[data-id='s-ya']")
            waitFor(scenario, "document.querySelector('.contact-missing')?.textContent === '发布人暂未留联系方式'")
            assertJs(scenario, "!document.querySelector('.copy-contact')")
            js(scenario, "(()=>{const r=indexedDB.open('mychuannong-secondhand',1);r.onsuccess=()=>{const get=r.result.transaction('content','readonly').objectStore('content').get('state');get.onsuccess=()=>{window.migrationChecked=get.result.version===2&&!('threads' in get.result)&&get.result.listings.length===6;r.result.close()}};return true})()")
            waitFor(scenario, "window.migrationChecked === true")
        }
    }

    private fun assertClipboard(scenario: ActivityScenario<SecondhandActivity>, expected: String) {
        scenario.onActivity { activity ->
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals(expected, clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString())
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun imageChooserReturnsASelectedImageToThePublishForm() {
        val resolver = instrumentation.targetContext.contentResolver
        val name = "secondhand-test-" + System.currentTimeMillis() + ".png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SecondhandTests")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(90, 145, 105))
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            ActivityScenario.launch(SecondhandActivity::class.java).use { scenario ->
                seed(scenario, populated = false)
                click(scenario, ".publish-add")
                waitFor(scenario, "Boolean(document.querySelector('#publish-images'))")
                tap(scenario, "[data-action=add-photo]")
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
                    val nodes = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("secondhand-test-").orEmpty()
                    if (nodes.isNotEmpty()) {
                        var node: android.view.accessibility.AccessibilityNodeInfo? = nodes.first()
                        while (node != null && !node.isClickable) node = node.parent
                        selected = node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
                    if (!selected) Thread.sleep(100)
                }
                if (!selected) {
                    val capture = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "secondhand-picker-debug.png").outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    capture.recycle()
                    android.util.Log.i("SecondhandPhoto", "active picker package=" + instrumentation.uiAutomation.rootInActiveWindow?.packageName)
                }
                assertTrue("The selected test image must be visible in the document picker", selected)
                waitFor(scenario, "document.querySelectorAll('.photo-thumb').length === 1")
                fill(scenario, "title", "图片发布检查")
                fill(scenario, "description", "图片选择后保存。")
                fill(scenario, "wechat", "secondhand_image_test")
                click(scenario, "#publish-form [type=submit]")
                waitFor(scenario, "Boolean(document.querySelector('.item img.product-photo'))")
                reload(scenario)
                waitFor(scenario, "document.querySelector('.item img.product-photo')?.naturalWidth > 0")
                click(scenario, "[data-action=mine]")
                waitFor(scenario, "document.querySelectorAll('.my-post').length === 1")
                click(scenario, ".edit-post")
                waitFor(scenario, "document.querySelectorAll('.photo-thumb').length === 1")
                fill(scenario, "title", "保留图片的编辑检查")
                choose(scenario, "[data-action=publish-campus]", "都江堰校区")
                assertJs(scenario, "document.querySelectorAll('.photo-thumb').length === 1 && document.querySelector('[name=wechat]').value === 'secondhand_image_test'")
                click(scenario, "#publish-form [type=submit]")
                waitFor(scenario, "document.querySelector('.my-post h3')?.textContent === '保留图片的编辑检查'")
                click(scenario, ".my-post-detail")
                waitFor(scenario, "document.querySelector('.detail-gallery img.product-photo')?.naturalWidth > 0")
            }
        } finally { resolver.delete(uri, null, null) }
    }

    private fun choose(scenario: ActivityScenario<SecondhandActivity>, selector: String, value: String) {
        click(scenario, selector)
        waitFor(scenario, "Boolean(document.querySelector('.sheet'))")
        click(scenario, "[role=radio][data-value='" + value + "']")
        waitFor(scenario, "!document.querySelector('.sheet')")
    }

    private fun seed(scenario: ActivityScenario<SecondhandActivity>, populated: Boolean) {
        waitFor(scenario, "window.CampusSecondhand?.ready === true")
        val listings = org.json.JSONArray()
        fun add(id: String, type: String, title: String, description: String, category: String, campus: String) {
            listings.put(org.json.JSONObject(mapOf("id" to id, "type" to type, "title" to title, "description" to description, "category" to category, "campus" to campus, "ownerId" to ("peer-" + id), "ownerName" to "发布同学", "images" to org.json.JSONArray(), "wechat" to "secondhand_contact_test", "phone" to "13800000000", "createdAt" to System.currentTimeMillis(), "price" to "80元以内", "budget" to "价格面议")))
        }
        if (populated) {
            add("s-ya", "sell", "高速材料课程笔记", "整理了具体章节记录。", "书籍", "雅安校区")
            add("s-cheng", "sell", "课堂资料整理包", "内容包含高速材料课堂记录。", "高速材料", "成都校区")
            add("s-du", "sell", "校园通勤自行车", "日常通勤，车况良好。", "自行车", "都江堰校区")
            add("w-ya", "want", "求购高速材料复习资料", "想了解具体章节内容。", "书籍", "雅安校区")
            add("w-cheng", "want", "寻找课堂资料", "需要高速材料课程记录。", "高速材料", "成都校区")
            add("w-du", "want", "求收一辆自行车", "希望适合校园通勤。", "自行车", "都江堰校区")
        }
        val data = org.json.JSONObject(mapOf("version" to 2, "sampleRevision" to 1, "listings" to listings, "preferences" to org.json.JSONObject(mapOf("tab" to "sell", "campus" to "全部校区"))))
        js(scenario, "(()=>{window.fixtureReloading=true;const request=indexedDB.open('mychuannong-secondhand',1);request.onsuccess=()=>{const tx=request.result.transaction('content','readwrite');tx.objectStore('content').put(" + data.toString() + ",'state');tx.oncomplete=()=>{request.result.close();location.reload()}};return true})()")
        waitFor(scenario, "typeof window.fixtureReloading === 'undefined' && window.CampusSecondhand?.ready === true")
    }

    private fun reload(scenario: ActivityScenario<SecondhandActivity>) {
        js(scenario, "window.fixtureReloading=true;location.reload();true")
        waitFor(scenario, "typeof window.fixtureReloading === 'undefined' && window.CampusSecondhand?.ready === true")
    }

    private fun fillSelector(scenario: ActivityScenario<SecondhandActivity>, selector: String, value: String) {
        val quotedSelector = org.json.JSONObject.quote(selector)
        val quoted = org.json.JSONObject.quote(value)
        js(scenario, "(()=>{const el=document.querySelector(" + quotedSelector + ");el.value=" + quoted + ";el.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
    }

    private fun tap(scenario: ActivityScenario<SecondhandActivity>, selector: String) {
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

    private fun js(scenario: ActivityScenario<SecondhandActivity>, expression: String): String {
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

    private fun assertJs(scenario: ActivityScenario<SecondhandActivity>, expression: String) {
        assertEquals(expression, "true", js(scenario, "Boolean($expression)"))
    }

    private fun waitFor(scenario: ActivityScenario<SecondhandActivity>, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < deadline) {
            if (js(scenario, "Boolean($expression)") == "true") return
            Thread.sleep(50)
        }
        assertJs(scenario, expression)
    }

    private fun click(scenario: ActivityScenario<SecondhandActivity>, selector: String) {
        android.util.Log.i("SecondhandTest", "click $selector")
        val quoted = org.json.JSONObject.quote(selector)
        assertEquals("true", js(scenario, "(()=>{const el=document.querySelector($quoted);if(!el||el.disabled)return false;el.click();return true})()"))
    }

    private fun fill(scenario: ActivityScenario<SecondhandActivity>, name: String, value: String) {
        val selector = org.json.JSONObject.quote("[name=$name]")
        val quoted = org.json.JSONObject.quote(value)
        js(scenario, "(()=>{const el=document.querySelector($selector);el.value=$quoted;el.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
    }

    private fun nativeBack(scenario: ActivityScenario<SecondhandActivity>) {
        android.util.Log.i("SecondhandTest", "back route=" + js(scenario, "location.hash"))
        scenario.onActivity {
            val web = requireNotNull(findWeb(it.window.decorView))
            val history = web.copyBackForwardList()
            android.util.Log.i("SecondhandTest", "native back=${web.canGoBack()}; entries=${history.size}; index=${history.currentIndex}")
            it.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun screenshot(scenario: ActivityScenario<SecondhandActivity>, name: String) {
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

package com.mychuannong.app.school

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in public-site diagnostic. Emits only browser error names and content length, no page data. */
class ServiceWebsiteProbeTest {
    @Test fun publicServiceConnectivity() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("probePublicServices") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val academicOnly = InstrumentationRegistry.getArguments().getString("academicOnly") == "true"
        val targets = if(academicOnly) listOf("academic" to SchoolAcademicActivity.PUBLIC_URL) else listOf("electric" to "http://bd.bdfairy.cn/selectservice", "student" to "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex")
        for((name, url) in targets) {
            if(InstrumentationRegistry.getArguments().getString("studentOnly") == "true" && name != "student") continue
            val a = i.startActivitySync(Intent(i.targetContext, if(name == "academic") SchoolAcademicActivity::class.java else if(name == "electric") com.mychuannong.app.feature.common.ui.ElectronicActivity::class.java else com.mychuannong.app.SchoolWebActivity::class.java)
                .putExtra("url_tag", url).putExtra("title_tag", name).putExtra("url", url).putExtra("title", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            android.os.SystemClock.sleep(18000)
            val done = CountDownLatch(1)
            i.runOnMainSync {
                fun find(v: View): WebView? {
                    if(v is WebView) return v
                    if(v is ViewGroup) for(n in 0 until v.childCount) find(v.getChildAt(n))?.let { return it }
                    return null
                }
                val web = find(a.window.decorView)
                if(web == null) { println("PUBLIC_SERVICE $name WEBVIEW_MISSING"); done.countDown() }
                else web.evaluateJavascript("JSON.stringify({error:(document.body.innerText.match(/net::ERR_[A-Z_]+/)||[])[0]||'',length:document.body.innerText.length,providerError:document.body.innerText.includes('操作失败'),electricHall:document.body.innerText.includes('水电管理'),studentHall:document.body.innerText.includes('常用功能'),viewport:!!document.querySelector('meta[name=viewport]')})") {
                    println("PUBLIC_SERVICE $name $it"); done.countDown()
                }
            }
            check(done.await(5, TimeUnit.SECONDS))
            if(academicOnly) {
                i.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(i.targetContext.getExternalFilesDir(null),"academic-preview.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
                    bitmap.recycle()
                }
            }
            i.runOnMainSync { a.finish() }
        }
    }
}

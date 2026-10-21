package com.mychuannong.app.school

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.R

import com.mychuannong.app.core.web.ServiceWebActivity
import com.mychuannong.app.databinding.ScoreItemCourseBinding
import com.mychuannong.app.feature.common.ui.ScoreInquiryActivity
import com.mychuannong.app.feature.common.ui.adapter.model.CourseScore
import com.mychuannong.app.feature.common.ui.adapter.vh.CourseViewHolder
import org.junit.Assert.*
import org.junit.Test

class ScoreAndServiceDeviceTest {
    @Test fun backFromHallExitsEvenWithEarlierWebHistory() {
        val i=InstrumentationRegistry.getInstrumentation()
        val context=RecordingContext(i.targetContext)
        SchoolServiceCatalog.items(context).single { it.id=="student-affairs" }.onClick()
        val a=i.startActivitySync(context.intents.single().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as com.mychuannong.app.SchoolWebActivity
        try {
            i.waitForIdleSync()
            val ready=java.util.concurrent.CountDownLatch(1)
            i.runOnMainSync {
                fun find(v:View):android.webkit.WebView? {
                    if(v is android.webkit.WebView)return v
                    if(v is android.view.ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n))?.let{return it}
                    return null
                }
                val web=requireNotNull(find(a.window.decorView))
                web.stopLoading()
                web.webViewClient=object:android.webkit.WebViewClient(){
                    override fun onPageFinished(v:android.webkit.WebView?,url:String?) {
                        if(url?.contains("synthetic-back-test")==true) web.evaluateJavascript(
                            "history.pushState({},'', '/mobile/stuhall/studentindex?from=login')"
                        ){ready.countDown()}
                    }
                }
                web.loadDataWithBaseURL("https://xsgz.sicau.edu.cn/synthetic-back-test", "<html><body>Local navigation test</body></html>", "text/html", "UTF-8", null)
            }
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS))
            i.runOnMainSync {
                a.onBackPressedDispatcher.onBackPressed()
                assertTrue(a.isFinishing)
            }
        } finally { i.runOnMainSync { if(!a.isFinishing)a.finish() } }
    }
    @Test fun originalScoreLayoutAndRowsPreserveSchoolStrings() {
        val i = InstrumentationRegistry.getInstrumentation()
        val a = i.startActivitySync(Intent(i.targetContext, ScoreInquiryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { i.runOnMainSync {
            assertNotNull(a.findViewById<View>(R.id.ScoreRecyclerView))
            assertNotNull(a.findViewById<View>(R.id.total_gpa))
            assertNotNull(a.findViewById<View>(R.id.refresh))
            val b = ScoreItemCourseBinding.inflate(LayoutInflater.from(a), FrameLayout(a), false)
            CourseViewHolder(b, a) { _, _ -> }.bind(CourseScore("合成测试课程", "缓考", null, null, "必修", null,
                creditText = "02.00", remarks = "合成备注", number = "SYN-1", fromTranscript = true))
            b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            assertEquals("缓考", b.tvScore.text.toString())
            assertEquals("学分: 02.00", b.tvCredit.text.toString())
            assertEquals("备注：合成备注", b.tvRemarks.text.toString())
            assertEquals("课程序号：SYN-1", b.tvNumber.text.toString())
            assertEquals(View.GONE, b.getCredit.visibility)
            assertTrue(b.tvScore.measuredWidth < b.root.measuredWidth)
        } } finally { i.runOnMainSync { a.finish() } }
    }
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) { intents += Intent(intent) }
    }
    @Test fun studentAffairsOpensNativeMobilePage() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        SchoolServiceCatalog.items(context).single { it.id == "student-affairs" }.onClick()
        val intent = context.intents.single()
        assertEquals(com.mychuannong.app.SchoolWebActivity::class.java.name, intent.component?.className)
        assertEquals("https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex", intent.getStringExtra("url"))
        assertNull(intent.action)
    }
    @Test fun electricityHttpExceptionIsLimitedToProvider() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("bd.bdfairy.cn"))
        assertFalse(policy.isCleartextTrafficPermitted("other.bd.bdfairy.cn"))
        assertFalse(policy.isCleartextTrafficPermitted("xsgz.sicau.edu.cn"))
        assertFalse(policy.isCleartextTrafficPermitted("example.org"))
    }

    @Test fun studentHallActivityKeepsAnInteractiveEmbeddedWebView() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = RecordingContext(i.targetContext)
        SchoolServiceCatalog.items(context).single { it.id == "student-affairs" }.onClick()
        val a = i.startActivitySync(context.intents.single().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            i.waitForIdleSync()
            i.runOnMainSync {
                fun find(view: View): android.webkit.WebView? {
                    if(view is android.webkit.WebView) return view
                    if(view is android.view.ViewGroup) for(n in 0 until view.childCount)
                        find(view.getChildAt(n))?.let { return it }
                    return null
                }
                assertFalse(a.isFinishing)
                val web = requireNotNull(find(a.window.decorView))
                assertTrue(web.settings.javaScriptEnabled)
                assertTrue(web.settings.domStorageEnabled)
                assertTrue(web.settings.useWideViewPort)
                assertTrue(web.width > 0 && web.height > 0)
                assertTrue(web.width <= a.window.decorView.width)
                assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
            }
        } finally { i.runOnMainSync { a.finish() } }
    }
}

package com.mychuannong.app.school

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.webkit.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/** Local synthetic HTML + a synthetic verified binding. Never submits school credentials. */
class ServiceFlowDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val owner="20269991"
    private val fakeUrl="https://www.sicau.edu.cn/mobile/module"

    @Before fun setup()=runBlocking {
        Assume.assumeTrue(context.packageName.endsWith(".serviceflowtest") || context.packageName.endsWith(".perftest"))
        finishActivities();SchoolSyncEngine.clear();SicauNativeAuth.awaitCleared()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
        val jar=SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.ORIGIN.toHttpUrl(),listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type","text/html; charset=utf-8")
                .body("<table><tr><td>学号</td><td>$owner</td></tr></table>".toResponseBody()).build()
        }.build()
        val session=SicauJiaowuSession(jar,client,{})
        session.fetchPersonalInfo(owner);SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner);SchoolAccessGate.restoreBinding()
        Unit
    }
    @After fun cleanup()=runBlocking { finishActivities();SchoolSyncEngine.clear();SicauNativeAuth.awaitCleared() }
    private fun finishActivities()=inst.runOnMainSync {
        listOf(Stage.RESUMED,Stage.STARTED,Stage.CREATED,Stage.STOPPED).flatMap {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
        }.distinct().forEach { it.finish() }
    }
    private fun resumed():Activity? {
        var result:Activity?=null
        inst.runOnMainSync { result=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).lastOrNull() }
        return result
    }
    private fun waitFor(condition:()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+12_000
        while(!condition() && android.os.SystemClock.elapsedRealtime()<end) Thread.sleep(50)
        assertTrue("Timed out waiting for service transition",condition())
    }
    private fun web(a:SchoolWebActivity)=SchoolWebActivity::class.java.getDeclaredField("web").apply { isAccessible=true }.get(a) as WebView
    private fun onMain(block:()->Unit)=inst.runOnMainSync(block)
    private fun loadFixture(main:MainActivity,title:String="合成服务",redirect:Boolean=false):Pair<SchoolWebActivity,AtomicInteger> {
        // The public school origin receives no authentication cookies. Fixture requests are intercepted.
        onMain { main.startActivity(SchoolServiceNavigation.intent(main,SchoolService(title,fakeUrl,SchoolServiceAuth.NONE))) }
        waitFor { resumed() is SchoolWebActivity }
        val a=resumed() as SchoolWebActivity
        val count=AtomicInteger()
        onMain {
            (SchoolWebActivity::class.java.getDeclaredField("entryJob").apply { isAccessible=true }.get(a) as? Job)?.cancel()
            val w=web(a);w.stopLoading()
            val delegate=w.webViewClient
            w.webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse {
                    val n=if(r.url.path=="/mobile/module")count.incrementAndGet() else count.get()
                    val body=if(r.url.path=="/login") """
                        <html><body>合成验证页<script>setTimeout(function(){
                        var e=document.createElement('input');e.type='password';document.body.appendChild(e);
                        },1800);</script></body></html>
                    """.trimIndent() else if(redirect && n==1 && r.url.path=="/mobile/module")
                        "<script>location.replace('https://www.sicau.edu.cn/')</script>" else
                        "<html><meta name='viewport' content='width=device-width'><body><h1>合成目标页</h1><a id='back' href='/'>返回</a><button id='close'>关闭页面</button><p>仅本地测试</p></body></html>"
                    return WebResourceResponse("text/html","UTF-8",body.byteInputStream())
                }
                override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=delegate.shouldOverrideUrlLoading(v,r)
                override fun onPageStarted(v:WebView,u:String?,b:Bitmap?)=delegate.onPageStarted(v,u,b)
                override fun onPageFinished(v:WebView,u:String?)=delegate.onPageFinished(v,u)
                override fun doUpdateVisitedHistory(v:WebView,u:String?,reload:Boolean)=delegate.doUpdateVisitedHistory(v,u,reload)
            }
            SchoolWebActivity::class.java.getDeclaredMethod("openEntry").apply { isAccessible=true }.invoke(a)
        }
        waitFor {
            var ready=false
            onMain { ready=!a.isFinishing && web(a).url==fakeUrl && web(a).visibility==android.view.View.VISIBLE }
            ready
        }
        return a to count
    }
    private fun main(block:(MainActivity)->Unit) {
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("main_tab",1)).use { scenario ->
            var main:MainActivity?=null;scenario.onActivity { main=it }
            compose.onNode(
                hasScrollAction() and SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
            ).performScrollToNode(hasText("学校服务"))
            block(requireNotNull(main))
        }
    }
    private fun assertReturned(main:MainActivity) {
        waitFor { resumed()===main }
        assertEquals(1,main.intent.getIntExtra("main_tab",-1))
        compose.onNodeWithText("学校服务").assertIsDisplayed()
    }
    @Test fun systemBackForAllEightServicesKeepsMainAndScroll()=main { main ->
        val scrollMatcher=SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val before=compose.onAllNodes(scrollMatcher).fetchSemanticsNodes().map { it.config[SemanticsProperties.VerticalScrollAxisRange].value() }
        SchoolServices.entries.values.forEach { service ->
            val (a,_)=loadFixture(main,service.title)
            onMain { web(a).evaluateJavascript("history.pushState({},'',location.href+'?detail=1');",null) }
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertReturned(main)
        }
        val after=compose.onAllNodes(scrollMatcher).fetchSemanticsNodes().map { it.config[SemanticsProperties.VerticalScrollAxisRange].value() }
        assertEquals(before,after)
    }
    @Test fun nativeBackClosesService()=main { main ->
        loadFixture(main)
        compose.onNodeWithContentDescription("返回").performClick()
        assertReturned(main)
    }
    @Test fun moreMenuCloseReturnsToServices()=main { main ->
        loadFixture(main)
        clickNativeText("更多");clickNativeText("关闭页面")
        assertReturned(main)
    }
    @Test fun pageBackCloseAndNegativeHistoryReturnToServices()=main { main ->
        listOf("document.getElementById('back').click()", "document.getElementById('close').click()",
            "history.back()", "history.go(-1)", "window.close()").forEach { script ->
            val (a,_)=loadFixture(main)
            onMain { web(a).evaluateJavascript(script,null) }
            assertReturned(main)
        }
    }
    @Test fun intermediateLandingAutomaticallyResumesTarget()=main { main ->
        val (a,requests)=loadFixture(main,redirect=true)
        assertEquals(2,requests.get())
        assertSame(a,resumed())
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);assertReturned(main)
    }
    @Test fun delayedOfficialVerificationControlsBecomeUsable()=main { main ->
        val (a,_)=loadFixture(main)
        onMain { web(a).loadUrl("https://www.sicau.edu.cn/login") }
        waitFor {
            var visible=false
            onMain { visible=web(a).url=="https://www.sicau.edu.cn/login" && web(a).visibility==android.view.View.VISIBLE }
            visible
        }
        assertSame(a,resumed())
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);assertReturned(main)
    }
    private fun clickNativeText(text:String) {
        waitFor { inst.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any { it.text?.toString()==text }==true }
        val node=inst.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(text).first { it.text?.toString()==text }
        assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        inst.waitForIdleSync()
    }
}

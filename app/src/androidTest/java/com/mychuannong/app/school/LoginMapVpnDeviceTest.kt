package com.mychuannong.app.school

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.webkit.*
import android.widget.EditText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.account.ui.AccountRequiredActivity
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Isolated synthetic school identity and local HTML only; never uses the installed user's data. */
class LoginMapVpnDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val vpn = SicauJiaowuRoute.VPN_ORIGIN
    private val owner = "20269983"

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".uitransition"))
        CampusToolsSession.invalidate(); SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared()
        SicauWebVpn.forgetRoute()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
        val jar = SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.ORIGIN.toHttpUrl(), listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type", "text/html; charset=utf-8")
                .body("<table><tr><td>学号</td><td>$owner</td></tr></table>".toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        session.fetchPersonalInfo(owner); SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner); SchoolAccessGate.restoreBinding()
        Unit
    }
    @After fun cleanup() = runBlocking {
        CampusToolsSession.invalidate(); SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared(); SicauWebVpn.forgetRoute()
    }
    private fun main(block: () -> Unit) = inst.runOnMainSync(block)
    private fun fields(view: View): List<EditText> = when(view) {
        is EditText -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { fields(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun screenshot(activity: Activity, name: String) {
        main {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            activity.currentFocus?.let {
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(it.windowToken,0)
                it.clearFocus()
            }
        }
        inst.waitForIdleSync(); Thread.sleep(200)
        val image = requireNotNull(inst.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }

    @Test fun accountAutofillKeepsNeutralFieldsAndNormalPasswordOrderInBothForms() {
        ActivityScenario.launch(AccountRequiredActivity::class.java).use { scenario ->
            var activity: Activity? = null; scenario.onActivity { activity = it }
            val host = requireNotNull(activity)
            listOf(false,true).forEach { registering ->
                if(registering) compose.onNodeWithText("没有账号？创建账号").performScrollTo().performClick()
                compose.waitForIdle()
                main {
                    val inputs = fields(host.window.decorView)
                    val phone = inputs.single { it.hint.toString().startsWith("手机号") }
                    val password = inputs.single { it.hint.toString().startsWith("密码") }
                    phone.autofill(android.view.autofill.AutofillValue.forText("19900000001"))
                    password.autofill(android.view.autofill.AutofillValue.forText("12345678"))
                    inputs.forEach { input ->
                        val style = input.context.obtainStyledAttributes(intArrayOf(android.R.attr.autofilledHighlight))
                        try { assertEquals(android.graphics.Color.TRANSPARENT,style.getColor(0,android.graphics.Color.YELLOW)) }
                        finally { style.recycle() }
                        assertEquals(android.graphics.Color.WHITE,(input.background as android.graphics.drawable.GradientDrawable).color?.defaultColor)
                    }
                }
                compose.waitForIdle()
                main {
                    val password = fields(host.window.decorView).single { it.hint.toString().startsWith("密码") }
                    assertEquals("12345678",password.text.toString())
                    assertTrue(password.transformationMethod is android.text.method.PasswordTransformationMethod)
                }
                screenshot(host,if(registering) "account-register-neutral" else "account-login-neutral")
            }
        }
    }

    private class Fixture(val activity: SicauWebVpnActivity, val web: WebView, val loads: AtomicInteger)
    private fun fixture(block: (Fixture) -> Unit) {
        ActivityScenario.launch(SicauWebVpnActivity::class.java).use { scenario ->
            var fixture: Fixture? = null
            scenario.onActivity { a ->
                (SicauWebVpnActivity::class.java.getDeclaredField("entryJob").apply { isAccessible=true }.get(a) as? Job)?.cancel()
                val w = SicauWebVpnActivity::class.java.getDeclaredField("web").apply { isAccessible=true }.get(a) as WebView
                w.stopLoading()
                val delegate = w.webViewClient
                val loads = AtomicInteger()
                w.webViewClient = object: WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        loads.incrementAndGet()
                        val content = if(request.url.path in listOf("/portal","/m/portal")) portal else login
                        return WebResourceResponse("text/html","UTF-8",content.byteInputStream())
                    }
                    override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=delegate.shouldOverrideUrlLoading(v,r)
                    override fun onPageStarted(v:WebView,u:String?,b:Bitmap?) {
                        delegate.onPageStarted(v,u,b)
                        assertEquals("Every navigation must start concealed",View.INVISIBLE,v.visibility)
                    }
                    override fun onPageCommitVisible(v:WebView,u:String?)=delegate.onPageCommitVisible(v,u)
                    override fun doUpdateVisitedHistory(v:WebView,u:String?,reload:Boolean)=delegate.doUpdateVisitedHistory(v,u,reload)
                    override fun onPageFinished(v:WebView,u:String?)=delegate.onPageFinished(v,u)
                    override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError)=delegate.onReceivedError(v,r,e)
                    override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,e:WebResourceResponse)=delegate.onReceivedHttpError(v,r,e)
                }
                fixture = Fixture(a,w,loads)
                w.loadUrl("$vpn/wui/")
            }
            val f = requireNotNull(fixture)
            awaitShown(f.web,true)
            block(f)
        }
    }
    private fun awaitShown(w:WebView,expected:Boolean) {
        compose.waitUntil(12_000) { var result=false; main { result=w.isShown==expected && w.isEnabled==expected }; result }
    }
    private fun js(w:WebView,script:String):String {
        var result:String?=null
        main { w.evaluateJavascript(script) { result=it } }
        compose.waitUntil(8_000) { result!=null }
        return requireNotNull(result)
    }
    private fun assertBlocked(f:Fixture) {
        awaitShown(f.web,false)
        compose.onNodeWithText("正在验证校园 VPN，请稍候……").assertIsDisplayed()
        main {
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,f.web.importantForAccessibility)
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val t=android.os.SystemClock.uptimeMillis()
                val event=MotionEvent.obtain(t,t,action,100f,150f,0)
                f.web.dispatchTouchEvent(event);event.recycle()
            }
        }
        assertEquals("0",js(f.web,"window.resourceClicks||0"))
        assertNotEquals(WebVpnPhase.AVAILABLE,SicauWebVpn.phase.value)
    }
    @Test fun portalAndHistoryNavigationStayHiddenAndCannotBeTapped()=fixture { f ->
        main { f.web.loadUrl("$vpn/portal") }
        compose.waitUntil(8_000) { js(f.web,"document.getElementById('resource')!==null")=="true" }
        assertBlocked(f)
        screenshot(f.activity,"vpn-native-validation")
        main { f.web.evaluateJavascript("history.pushState({},'', '/m/portal');",null) }
        assertBlocked(f)
    }
    @Test fun sameDocumentLoginReplacementCannotExposeResources()=fixture { f ->
        main { f.web.loadUrl("$vpn/") }
        awaitShown(f.web,true)
        compose.waitUntil(8_000) { js(f.web,"!!window.__campusVpnLoginGuard")=="true" }
        js(f.web,"document.body.innerHTML='<a id=resource href=\"/portal\" onclick=\"window.resourceClicks++\">学校资源</a>'; window.resourceClicks=0;")
        assertEquals("\"0\"",js(f.web,"getComputedStyle(document.documentElement).opacity"))
        assertEquals("\"学校资源\"",js(f.web,"document.getElementById('resource').innerText"))
        assertBlocked(f)
    }
    @Test fun mainDocumentFailureStaysNativeAndReconnectReturnsToOfficialLogin()=fixture { f ->
        main { f.web.loadUrl("$vpn/portal") }
        compose.waitUntil(8_000) { js(f.web,"document.getElementById('resource')!==null")=="true" }
        val request=object:WebResourceRequest {
            override fun getUrl()=android.net.Uri.parse("$vpn/portal")
            override fun isForMainFrame()=true
            override fun isRedirect()=false
            override fun hasGesture()=false
            override fun getMethod()="GET"
            override fun getRequestHeaders()=emptyMap<String,String>()
        }
        main { f.web.webViewClient.onReceivedHttpError(f.web,request,
            WebResourceResponse("text/html","UTF-8",503,"Synthetic unavailable",emptyMap(),"".byteInputStream())) }
        awaitShown(f.web,false)
        compose.onNodeWithText("重新连接").assertIsDisplayed()
        screenshot(f.activity,"vpn-reconnect")
        main { f.web.webViewClient.onPageFinished(f.web,"$vpn/wui/") }
        awaitShown(f.web,false)
        val before=f.loads.get()
        compose.onNodeWithText("重新连接").performClick()
        compose.waitUntil(12_000) { f.loads.get()>before }
        awaitShown(f.web,true)
        compose.onNodeWithText("重新连接").assertDoesNotExist()
        // A delayed failure from the previous portal request must not cover the new login.
        main { f.web.webViewClient.onReceivedHttpError(f.web,request,
            WebResourceResponse("text/html","UTF-8",503,"Synthetic stale error",emptyMap(),"".byteInputStream())) }
        awaitShown(f.web,true)
    }

    companion object {
        private const val login="<html><meta name='viewport' content='width=device-width'><body><h1>合成官方认证页</h1><input type='password' id='password'><input autocomplete='one-time-code' id='otp'></body></html>"
        private const val portal="<html><meta name='viewport' content='width=device-width'><body><script>window.resourceClicks=0;</script><h1>WebVPN 资源</h1><a id='resource' href='#' onclick='window.resourceClicks++'>学校资源</a></body></html>"
    }
}

package com.mychuannong.app.school

import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Invoke seed and restore in separate instrumentation processes with force-stop between.
 * No real account, school request, password, or production package is used. */
class SchoolWebSessionDeviceTest {
    private val auth="https://auth.sicau.edu.cn/"
    private val affairs="https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"
    private val payment="https://op.sicau.edu.cn/CasLogin.aspx"
    private val vpn="https://webvpn.sicau.edu.cn/"
    private val academic="https://jiaowu.sicau.edu.cn/xuesheng/dangan/banji/bjiben.asp"
    private val proxyBase = vpn.removeSuffix("/") + "/https/" + "a".repeat(64)
    private val proxyAcademic = proxyBase + "/xuesheng/dangan/banji/bjiben.asp"
    private val file get()=File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,"sicau-web-sessions.enc")

    @Before fun isolatedOnly() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertTrue(packageName.endsWith(".sessiontest") || packageName.endsWith(".bindingtest"))
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))
        assertNotNull(SchoolWebCookiePolicy.valid(affairs,"shared=synthetic; Domain=sicau.edu.cn; Path=/; Secure"))
    }

    private suspend fun set(url:String,raw:String)=withContext(Dispatchers.Main) {
        SchoolWebSessionStore.setCookie(CookieManager.getInstance(),url,raw)
    }
    private suspend fun clearBrowser()=withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { c -> CookieManager.getInstance().removeAllCookies { if(c.isActive)c.resumeWith(Result.success(Unit)) } }
        CookieManager.getInstance().flush()
    }

    @Test fun seedEncryptedSessions()=runBlocking {
        SicauNativeAuth.clear();SicauNativeAuth.awaitCleared()
        SchoolWebSessionStore.restoreOnce()
        set(auth + "api/v1/cas/login","go-admin-tgt=synthetic-auth; Path=/api/; Secure; HttpOnly; SameSite=Lax; Max-Age=3600")
        set(affairs,"student=synthetic-student; Path=/mobile/; Secure; HttpOnly; SameSite=Strict")
        set(payment,"pay=synthetic-payment; Path=/; Secure; HttpOnly; SameSite=Lax")
        set(vpn,"vpn=synthetic-vpn; Path=/; Secure; HttpOnly; SameSite=Lax")
        set(academic,"ASPSESSIONIDTEST=synthetic-academic; Path=/xuesheng/; Secure; HttpOnly; SameSite=Strict")
        set(proxyBase + "/api/v1/cas/login","go-admin-tgt=synthetic-proxy-auth; Path=/https/${"a".repeat(64)}/api/; Secure; HttpOnly; SameSite=Lax")
        set(proxyAcademic,"ASPSESSIONIDVPN=synthetic-proxy-academic; Path=/https/${"a".repeat(64)}/xuesheng/; Secure; HttpOnly; SameSite=Lax")
        withContext(Dispatchers.Main) { SchoolWebSessionStore.remember(proxyBase + "/wui/") }
        set(payment,"expired=synthetic-expiring; Path=/; Secure; Max-Age=1")
        for(url in listOf(auth,affairs,payment,vpn,academic,proxyAcademic)) SchoolWebSessionStore.capture(url)
        assertTrue(file.exists())
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("synthetic-"))
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("sicau.edu.cn"))
        clearBrowser() // Prove the next process restores the encrypted snapshot.
    }

    @Test fun restoreAfterColdStartAndLogout()=runBlocking {
        assertTrue(file.exists())
        // A fresh browser login must win over an older on-disk snapshot.
        set(affairs,"student=synthetic-newer; Path=/mobile/; Secure; HttpOnly; SameSite=Strict")
        SchoolWebSessionStore.restoreOnce()
        withContext(Dispatchers.Main) {
            val manager=CookieManager.getInstance()
            assertTrue(manager.getCookie(auth + "api/v1/cas/login").orEmpty().contains("synthetic-auth"))
            assertFalse(manager.getCookie(auth).orEmpty().contains("synthetic-auth"))
            assertTrue(manager.getCookie(affairs).orEmpty().contains("synthetic-newer"))
            assertFalse(manager.getCookie(affairs).orEmpty().contains("synthetic-student"))
            assertTrue(manager.getCookie(payment).orEmpty().contains("synthetic-payment"))
            assertFalse(manager.getCookie(payment).orEmpty().contains("synthetic-expiring"))
            assertTrue(manager.getCookie(vpn).orEmpty().contains("synthetic-vpn"))
            assertTrue(manager.getCookie(academic).orEmpty().contains("synthetic-academic"))
            assertTrue(manager.getCookie(proxyBase + "/api/v1/cas/login").orEmpty().contains("synthetic-proxy-auth"))
            assertTrue(manager.getCookie(proxyAcademic).orEmpty().contains("synthetic-proxy-academic"))
            assertFalse(manager.getCookie(vpn).orEmpty().contains("synthetic-proxy"))
            assertFalse(manager.getCookie("https://jiaowu.sicau.edu.cn/").orEmpty().contains("synthetic-academic"))
            assertFalse(manager.getCookie(affairs).orEmpty().contains("synthetic-auth"))
            val raw=CookieManagerCompat.getCookieInfo(manager,academic).single { it.startsWith("ASPSESSIONIDTEST=") }
            assertTrue(raw.contains("httponly",true) && raw.contains("secure",true) && raw.contains("samesite=strict",true))
        }
        val oldRevision=SicauNativeAuth.webRevision.value
        SicauNativeAuth.clear();SicauNativeAuth.awaitCleared()
        SchoolWebSessions.persist(auth,oldRevision)
        SchoolWebSessionStore.restoreOnce()
        assertFalse(file.exists())
        withContext(Dispatchers.Main) {
            for(url in listOf(auth,affairs,payment,vpn,academic)) assertTrue(CookieManager.getInstance().getCookie(url).isNullOrBlank())
        }
        assertTrue(SchoolSessionVault.read().snapshot().isEmpty())
    }

    @Test fun logoutClearsWebsiteTokensAndPreservesOfflineAppStorage(): Unit = runBlocking {
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
        suspend fun page(origin:String):android.webkit.WebView=withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { c ->
                val web=android.webkit.WebView(InstrumentationRegistry.getInstrumentation().targetContext)
                web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true
                web.webViewClient=object:android.webkit.WebViewClient() {
                    override fun onPageFinished(view:android.webkit.WebView,url:String?) {
                        if(c.isActive)c.resumeWith(Result.success(web))
                    }
                }
                web.loadDataWithBaseURL(origin,"<html><body>synthetic local fixture</body></html>","text/html","UTF-8",null)
            }
        }
        suspend fun js(web:android.webkit.WebView,script:String):String=withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { c -> web.evaluateJavascript(script) { if(c.isActive)c.resumeWith(Result.success(it)) } }
        }
        val offline="https://appassets.androidplatform.net/"
        val school=page(auth)
        val local=page(offline)
        try {
            js(school,"localStorage.setItem('synthetic-token','synthetic-session')")
            js(local,"localStorage.setItem('synthetic-draft','keep-me')")
            withContext(Dispatchers.Main) { school.destroy();local.destroy() }
            SicauNativeAuth.clear();SicauNativeAuth.awaitCleared()
            val freshSchool=page(auth);val freshLocal=page(offline)
            try {
                assertEquals("null",js(freshSchool,"localStorage.getItem('synthetic-token')"))
                assertEquals("\"keep-me\"",js(freshLocal,"localStorage.getItem('synthetic-draft')"))
                js(freshLocal,"localStorage.removeItem('synthetic-draft')")
            } finally { withContext(Dispatchers.Main) { freshSchool.destroy();freshLocal.destroy() } }
        } finally { withContext(Dispatchers.Main) { school.destroy();local.destroy() } }
        Unit
    }

    @Test fun officialSsoGetsOneReuseAttemptWithoutRootCookie()=runBlocking {
        SicauNativeAuth.clear();SicauNativeAuth.awaitCleared()
        val entry = auth + "wui/#/?appid=test&service=https%3A%2F%2Fxsgz.sicau.edu.cn%2Fcallback"
        withContext(Dispatchers.Main) {
            assertTrue(CookieManager.getInstance().getCookie(auth).isNullOrBlank())
            assertEquals(entry + "&cascallback=choose", SchoolWebSessions.reuseSso(entry))
            assertNull(SchoolWebSessions.reuseSso(entry))
            assertNull(SchoolWebSessions.reuseSso(entry + "&cascallback=err"))
            val proxy = entry.replace(auth.removeSuffix("/"), vpn.removeSuffix("/") + "/https/" + "a".repeat(64))
            assertEquals(proxy + "&cascallback=choose", SchoolWebSessions.reuseSso(proxy))
            assertNull(SchoolWebSessions.reuseSso(proxy))
        }
    }
}

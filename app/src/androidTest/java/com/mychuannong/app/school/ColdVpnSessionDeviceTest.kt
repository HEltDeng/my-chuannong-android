package com.mychuannong.app.school

import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.*
import kotlin.coroutines.resume

/** Run seed and restore in separate instrumented processes, with a force-stop between. */
class ColdVpnSessionDeviceTest {
    private val origin="https://webvpn.sicau.edu.cn/"
    private val route=requireNotNull(SicauJiaowuRoute.restore("/https/"+"c".repeat(64)))
    @Before fun isolatedOnly() { Assume.assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".bindingtest")) }
    @Test fun seedEncryptedSession()=runBlocking {
        SchoolSyncEngine.clear()
        StudentInfoManager.acceptVerifiedIdentity("synthetic-cold")
        StudentInfoManager.saveVerifiedName("synthetic-cold","欧阳小雨")
        withContext(Dispatchers.Main) {
            val manager=CookieManager.getInstance()
            Assume.assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))
            // Wait for the explicit logout clear before preparing synthetic browser cookies.
            SicauNativeAuth.exportToWebView()
            suspendCancellableCoroutine<Unit> { c -> manager.removeAllCookies { c.resume(Unit) } }
            suspendCancellableCoroutine<Unit> { c -> manager.setCookie(origin,"vpn_fixture=synthetic; Path=/; Secure; HttpOnly; SameSite=Lax") { c.resume(Unit) } }
            suspendCancellableCoroutine<Unit> { c -> manager.setCookie(route.url(SicauJiaowuProtocol.PERSONAL_INFO),"academic_fixture=synthetic; Path=${route.prefix}/; Secure; HttpOnly; SameSite=Strict") { c.resume(Unit) } }
        }
        SicauWebVpn.discovered(route)
        WebVpnSessionStore.capture()
        withContext(Dispatchers.Main) { suspendCancellableCoroutine<Unit> { c -> CookieManager.getInstance().removeAllCookies { c.resume(Unit) } };CookieManager.getInstance().flush() }
    }
    @Test fun restoreAfterProcessRestart()=runBlocking {
        assertTrue(StudentInfoManager.hasCompleteBinding)
        assertEquals("欧阳同学",StudentInfoManager.greeting)
        WebVpnSessionStore.restoreOnce()
        withContext(Dispatchers.Main) {
            val manager=CookieManager.getInstance()
            assertTrue(manager.getCookie(origin).orEmpty().contains("vpn_fixture=synthetic"))
            assertFalse(manager.getCookie(origin).orEmpty().contains("academic_fixture"))
            val cookies=CookieManagerCompat.getCookieInfo(manager,route.url(SicauJiaowuProtocol.PERSONAL_INFO))
            assertTrue(cookies.any { it.startsWith("academic_fixture=") && it.contains("httponly",true) && it.contains("samesite=strict",true) })
        }
        SchoolSyncEngine.clear()
        WebVpnSessionStore.restoreOnce()
        withContext(Dispatchers.Main) { SicauNativeAuth.exportToWebView();assertTrue(CookieManager.getInstance().getCookie(origin).isNullOrBlank()) }
    }
}

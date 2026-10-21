package com.mychuannong.app.school

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.net.URLEncoder

/** Reproduce the real domain separation using Chromium cookies and synthetic school responses. */
class SchoolSsoBrokerDeviceTest {
    private val realm = "https://webvpn.sicau.edu.cn/https/" + "a".repeat(64)
    private val owner = "20260001"
    @Before fun isolated() = runBlocking {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".bindingtest"))
        SicauNativeAuth.clear(); SicauNativeAuth.awaitCleared()
        SchoolWebSessionStore.restoreOnce()
    }
    @After fun cleanup() = runBlocking { StudentInfoManager.clear(); SicauNativeAuth.clear(); SicauNativeAuth.awaitCleared() }
    private val cookies = object : SchoolSsoCookies {
        override suspend fun get(url: String) = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(url).orEmpty()
        }
        override suspend fun accept(url: String, cookies: List<String>) = withContext(Dispatchers.Main) {
            cookies.forEach { SchoolWebSessionStore.setCookie(CookieManager.getInstance(), url, it) }
        }
    }

    @Test fun proxyLoginIsReusedForStudentAndPortalWithoutTransplantingCookies() = runBlocking {
        cookies.accept("$realm/api/v1/cas/login", listOf(
            "go-admin-tgt=synthetic-proxy-login; Path=/https/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/; Secure; HttpOnly; SameSite=Lax"))
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val request = chain.request()
            assertEquals("GET", request.method)
            assertTrue(request.url.toString().startsWith("$realm/api/v1/cas/login?"))
            assertEquals("go-admin-tgt=synthetic-proxy-login", request.header("Cookie"))
            calls.add(request.url.queryParameter("appid")!!)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body("""{"code":200,"data":{"st":"synthetic-ticket","user":{"id":"synthetic-id","number":"$owner"},"users":[{"id":"delegate","number":"20260002"},{"id":"synthetic-id","number":"$owner"}]}}""".toResponseBody()).build()
        }.build()
        val broker = SchoolSsoBroker(cookies, client)
        for((app, service) in listOf("student" to "https://xsgz.sicau.edu.cn/callback",
            "ywtb" to "https://ywtb.sicau.edu.cn/cas/login/user",
            "ywtb" to "https://ywtb.sicau.edu.cn/cas/login/user")) {
            val login = "https://auth.sicau.edu.cn/wui/#/?appid=$app&service=" + URLEncoder.encode(service, "UTF-8")
            assertTrue(cookies.get("https://auth.sicau.edu.cn/api/v1/cas/login").isEmpty())
            val callback = broker.callback(SchoolSsoRequest.parse(login)!!, owner, realm)!!.toHttpUrl()
            assertEquals(service.toHttpUrl().host, callback.host)
            assertEquals("synthetic-ticket:synthetic-id", callback.queryParameter("ticket"))
            assertTrue(cookies.get(service).isEmpty())
        }
        assertEquals(listOf("student", "ywtb", "ywtb"), calls)
        assertTrue(cookies.get("https://auth.sicau.edu.cn/").isEmpty())
        // Persist only the official routing hint, never a ticket or login query.
        SchoolResourceAccess.rememberAuthRedirect("$realm/wui/#/?appid=vpn&service=" +
            URLEncoder.encode("https://webvpn.sicau.edu.cn/login?cas_login=true", "UTF-8"))
        assertEquals(realm, SchoolResourceAccess.authRealm())
        SicauNativeAuth.clear(); SicauNativeAuth.awaitCleared()
        assertNull(SchoolResourceAccess.authRealm())
        assertTrue(cookies.get("$realm/api/v1/cas/login").isEmpty())
    }

    @Test fun openingPortalThenUnifiedAuthenticationDoesNotShareLoopCooldown() {
        val inst = InstrumentationRegistry.getInstrumentation()
        // No HTTP runs here: exercise the real navigation admission with cancelled work.
        val stoppedScope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        inst.runOnMainSync {
            StudentInfoManager.acceptVerifiedIdentity(owner)
            val portal = WebView(inst.targetContext)
            val unified = WebView(inst.targetContext)
            try {
                SchoolWebSessions.track(portal)
                SchoolWebSessions.track(unified)
                assertTrue(SchoolWebSessions.reuseSso(portal, SicauAdapter.AUTH, stoppedScope))
                SchoolWebSessions.cancelSso(portal)
                SchoolWebSessions.track(portal)
                assertFalse(SchoolWebSessions.reuseSso(portal, SicauAdapter.AUTH, stoppedScope))
                // Same appid/service, new screen: it must get its own session-reuse attempt.
                assertTrue(SchoolWebSessions.reuseSso(unified, SicauAdapter.AUTH, stoppedScope))
            } finally {
                SchoolWebSessions.cancelSso(portal)
                SchoolWebSessions.cancelSso(unified)
                portal.destroy(); unified.destroy()
            }
        }
    }
}

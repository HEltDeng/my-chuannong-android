package com.mychuannong.app.school

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport tests, not evidence of a real school WebVPN session. */
class SicauWebVpnSessionTest {
    private val prefix = "/https/" + "a".repeat(64)
    private val route = requireNotNull(SicauJiaowuRoute.fromResourcePage("https://webvpn.sicau.edu.cn$prefix/web/web/web/index.asp"))
    private class Cookies : WebVpnCookieBridge {
        val requested = mutableListOf<String>()
        val received = mutableListOf<String>()
        override fun get(url: String): String { requested += url; return "vpn=synthetic; ASPSESSIONIDTEST=synthetic" }
        override fun accept(url: String, setCookie: String) { received += setCookie }
        override fun flush() {}
    }
    @Test fun proxyRoutesEveryAcademicRequestAndNeverFallsBack() = runBlocking {
        val cookies = Cookies()
        val jar = SicauJiaowuCookieJar(route, cookies)
        val paths = mutableListOf<String>()
        var step = 0
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val req = chain.request(); paths += req.url.toString()
            assertEquals("webvpn.sicau.edu.cn", req.url.host)
            assertTrue(req.url.encodedPath.startsWith("$prefix/"))
            assertTrue(req.header("Referer")!!.startsWith("https://webvpn.sicau.edu.cn$prefix/"))
            val body = when(step++) {
                0 -> "<form action='/jiaoshi/bangong/check.asp'><input name=sign value=x><input name=hour_key value=y></form>"
                1 -> { assertEquals("https://webvpn.sicau.edu.cn", req.header("Origin")); "" }
                2 -> "<table><tr><td>学号</td><td>20260001</td></tr></table>"
                else -> "<html><body><table><tr><td>synthetic</td></tr></table></body></html>"
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(if (step == 2) 302 else 200)
                .header("Content-Type", "text/html; charset=utf-8")
                .header("Location", "$prefix/xuesheng/").message("synthetic")
                .body(body.toByteArray(Charsets.UTF_8).toResponseBody("text/html; charset=utf-8".toMediaType())).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        assertEquals("20260001", session.login("20260001", "synthetic").studentId)
        for (page in JiaowuPage.entries) session.fetchBusiness(page)
        assertEquals(6, paths.size)
        assertTrue(jar.loadForRequest(SicauJiaowuProtocol.PERSONAL_INFO.toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://webvpn.sicau.edu.cn/https/${"b".repeat(64)}/".toHttpUrl()).isEmpty())
        assertFalse(jar.encode().contains("synthetic"))
    }
    @Test fun authenticationRedirectAndHttp200LoginAreNotAcademicSuccess() = runBlocking {
        for (status in listOf(200, 302, 403)) {
            val jar = SicauJiaowuCookieJar(route, Cookies())
            val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("synthetic")
                    .header("Content-Type", "text/html; charset=utf-8")
                    .header("Location", "/login?cas_login=true")
                    .body("<title>川农e认证</title>".toResponseBody("text/html; charset=utf-8".toMediaType())).build()
            }.build()
            try { SicauJiaowuSession(jar,client,{}).checkLoginPage(); fail("Authentication is not data") }
            catch (e: JiaowuException) { assertEquals(JiaowuFailure.WEBVPN_REQUIRED, e.reason) }
        }
    }
    @Test fun rawCookieAttributesAndRouteSurviveBridgeWithoutCookieSerialization() {
        val bridge = Cookies(); val jar = SicauJiaowuCookieJar(route,bridge)
        val url = route.url(SicauJiaowuProtocol.LOGIN_PAGE).toHttpUrl()
        val raw = "ticket=synthetic; Path=$prefix/; Secure; HttpOnly; SameSite=Lax"
        jar.acceptRawCookies(url,listOf(raw))
        assertEquals(listOf(raw), bridge.received)
        val restored = SicauJiaowuCookieJar(bridge=bridge).apply { restore(jar.encode()) }
        assertEquals(route.prefix, restored.route.prefix)
        assertTrue(restored.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        assertTrue(bridge.requested.last().contains(prefix))
    }
    @Test fun routesRejectForeignHostsUnknownActionsAndGuessedPrefixes() {
        for (url in listOf("http://webvpn.sicau.edu.cn$prefix/", "https://webvpn.sicau.edu.cn.evil.test$prefix/",
            "https://user@webvpn.sicau.edu.cn$prefix/", "https://webvpn.sicau.edu.cn:444$prefix/", "https://webvpn.sicau.edu.cn/login"))
            assertNull(SicauJiaowuRoute.fromResourcePage(url))
        try { route.loginAction("https://evil.test/check.asp", route.url(SicauJiaowuProtocol.LOGIN_PAGE)); fail() }
        catch (_: JiaowuException) {}
        assertEquals(route.url(SicauJiaowuProtocol.LOGIN_POST), route.loginAction("../../../jiaoshi/bangong/check.asp", route.url(SicauJiaowuProtocol.LOGIN_PAGE)))
        assertEquals(route.url(SicauJiaowuProtocol.LOGIN_POST), route.loginAction(SicauJiaowuProtocol.LOGIN_POST, route.url(SicauJiaowuProtocol.LOGIN_PAGE)))
    }
}

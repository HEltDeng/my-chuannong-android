package com.mychuannong.app.school

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Chromium cookie behavior with synthetic cookies in the isolated validation package. */
@RunWith(AndroidJUnit4::class)
class SicauWebVpnCookieDeviceTest {
    @Test fun nativeBridgeHonorsHttpOnlyPathExpiryAndHostWithoutExportingCookies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(instrumentation.targetContext.packageName.endsWith(".validation"))
        val prefix = "/https/" + "a".repeat(64)
        val url = "https://webvpn.sicau.edu.cn$prefix/web/web/web/index.asp"
        val route = requireNotNull(SicauJiaowuRoute.fromResourcePage(url))
        val cookies = CookieManager.getInstance()
        fun set(value: String) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync { cookies.setCookie(url, value) { latch.countDown() } }
            assertTrue(latch.await(5,TimeUnit.SECONDS))
        }
        val name = "MYCHUANNONG_SYNTHETIC_WEBVPN"
        try {
            set("$name=synthetic; Path=$prefix/; Secure; HttpOnly; SameSite=Lax")
            val jar = SicauJiaowuCookieJar(route)
            assertTrue(jar.loadForRequest(url.toHttpUrl()).any { it.name == name })
            assertFalse(cookies.getCookie("https://webvpn.sicau.edu.cn/login").orEmpty().contains(name))
            assertTrue(jar.loadForRequest(SicauJiaowuProtocol.LOGIN_PAGE.toHttpUrl()).isEmpty())
            assertTrue(jar.loadForRequest("http://webvpn.sicau.edu.cn$prefix/".toHttpUrl()).isEmpty())
            val encoded = jar.encode()
            assertFalse(encoded.contains("synthetic"))
            val restored = SicauJiaowuCookieJar().apply { restore(encoded) }
            assertTrue(restored.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
            // Emulates an actual raw Set-Cookie rotation received by the OkHttp network interceptor.
            jar.acceptRawCookies(url.toHttpUrl(), listOf("$name=rotated; Path=$prefix/; Secure; HttpOnly; SameSite=Lax"))
            cookies.flush()
            assertTrue(jar.loadForRequest(url.toHttpUrl()).any { it.name == name && it.value == "rotated" })
            set("$name=; Path=$prefix/; Secure; Max-Age=0")
            assertFalse(jar.loadForRequest(url.toHttpUrl()).any { it.name == name })
        } finally { set("$name=; Path=$prefix/; Secure; Max-Age=0"); cookies.flush() }
    }
}

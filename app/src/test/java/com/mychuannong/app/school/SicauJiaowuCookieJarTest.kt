package com.mychuannong.app.school

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SicauJiaowuCookieJarTest {
    private val origin = SicauJiaowuProtocol.ORIGIN.toHttpUrl()
    @Test fun destinationIsolationAndAspCookieAllowlist() {
        val jar = SicauJiaowuCookieJar()
        jar.saveFromResponse(origin, listOf(
            Cookie.Builder().name("ASPSESSIONIDSYNTHETIC").value("synthetic").domain("sicau.edu.cn").path("/").secure().httpOnly().build(),
            Cookie.parse(origin,"tracking=synthetic; Path=/")!!))
        assertEquals(1, jar.loadForRequest(origin).size)
        for (url in listOf("http://jiaowu.sicau.edu.cn/", "https://auth.sicau.edu.cn/", "https://api.mychuannong.example/",
            "https://jiaowu.sicau.edu.cn.evil.example/")) assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        jar.saveFromResponse("https://auth.sicau.edu.cn/".toHttpUrl(), listOf(Cookie.parse(origin,"ASPSESSIONIDOTHER=ignored")!!))
        assertEquals(1, jar.loadForRequest(origin).size)
    }
    @Test fun pathExpiryReplacementAndPersistence() {
        val jar = SicauJiaowuCookieJar()
        jar.saveFromResponse(origin,listOf(Cookie.parse(origin,"ASPSESSIONIDTEST=synthetic; Path=/xuesheng; Secure")!!))
        assertFalse(jar.hasSession(SicauJiaowuProtocol.LOGIN_POST))
        assertTrue(jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        val restored = SicauJiaowuCookieJar().apply { restore(jar.encode()) }
        assertTrue(restored.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        assertFalse(restored.hasSession(SicauJiaowuProtocol.LOGIN_POST))
        restored.saveFromResponse(origin,listOf(Cookie.parse(origin,"ASPSESSIONIDTEST=gone; Path=/xuesheng; Max-Age=0; Secure")!!))
        assertFalse(restored.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        restored.clear(); assertEquals("", restored.encode())
    }
    @Test fun networkAndCookieJarCallbacksRetainSameSiteAndServerDeadline() {
        val jar=SicauJiaowuCookieJar()
        val raw="ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=600"
        jar.acceptRawCookies(origin,listOf(raw))
        jar.saveFromResponse(origin,listOf(requireNotNull(Cookie.parse(origin,raw))))
        val before = jar.rawForRequest(origin).single()
        val delayed = Cookie.Builder().name("ASPSESSIONIDTEST").value("synthetic").hostOnlyDomain(origin.host)
            .path("/").secure().httpOnly().expiresAt(System.currentTimeMillis()+900_000).build()
        jar.saveFromResponse(origin,listOf(delayed))
        assertEquals(before, jar.rawForRequest(origin).single())
        val restored=SicauJiaowuCookieJar().apply { restore(jar.encode()) }
        assertTrue(restored.rawForRequest(origin).single().contains("SameSite=Strict"))
        assertFalse(restored.rawForRequest(origin).single().contains("Max-Age",true))
        jar.acceptRawCookies(origin,listOf("ASPSESSIONIDTEST=deleted; Path=/; Max-Age=0"))
        assertTrue(jar.rawForRequest(origin).isEmpty())
    }
}

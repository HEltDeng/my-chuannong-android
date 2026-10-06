package com.mychuannong.app.school

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SchoolCookieJarTest {
    private val auth = "https://auth.sicau.edu.cn/".toHttpUrl()
    @Test fun schoolCookieCannotReachPlatformOrSiblingService() {
        val jar=SchoolCookieJar()
        jar.saveFromResponse(auth,listOf(Cookie.parse(auth,"go-admin-tgt=synthetic; Path=/; HttpOnly")!!))
        assertEquals(1,jar.loadForRequest(auth).size)
        for(url in listOf("https://op.sicau.edu.cn/","https://auth.sicau.edu.cn.evil.example/","http://auth.sicau.edu.cn/","https://platform.example/")) {
            assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        }
    }
    @Test fun webRefreshReplacesStaleAuthAndMissingCookieInvalidatesIt() {
        val jar=SchoolCookieJar()
        jar.importAuthCookie("unrelated=ignored; go-admin-tgt=synthetic-old")
        jar.importAuthCookie("go-admin-tgt=synthetic-new")
        assertEquals(listOf("synthetic-new"),jar.loadForRequest(auth).map { it.value })
        assertTrue(jar.loadForRequest(auth).single().hostOnly)
        jar.importAuthCookie(null)
        assertTrue(jar.loadForRequest(auth).isEmpty())
    }
    @Test fun encryptedVaultPayloadRoundTripRetainsCookieScope() {
        val jar=SchoolCookieJar();jar.importAuthCookie("go-admin-tgt=synthetic")
        val restored=SchoolCookieJar().apply{restore(jar.encode())}
        assertEquals("synthetic",restored.loadForRequest(auth).single().value)
        assertTrue(restored.loadForRequest("https://op.sicau.edu.cn/".toHttpUrl()).isEmpty())
    }
    @Test fun errorsDoNotEchoExceptions() {
        assertEquals(SchoolFailure.DNS,SchoolFailure.from(java.net.UnknownHostException("private-host")))
        assertEquals(SchoolFailure.TLS,SchoolFailure.from(javax.net.ssl.SSLHandshakeException("private-detail")))
        assertEquals(SchoolFailure.TIMEOUT,SchoolFailure.from(java.net.SocketTimeoutException("private-detail")))
        assertFalse(SchoolFailure.from(java.io.IOException("secret-cookie")).userMessage.contains("secret-cookie"))
    }
}

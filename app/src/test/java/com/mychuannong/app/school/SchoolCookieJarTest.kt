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
        jar.importAuthCookies(listOf("go-admin-tgt=synthetic-old; Secure; HttpOnly; Path=/; SameSite=Lax"))
        jar.importAuthCookies(listOf("go-admin-tgt=synthetic-new; Secure; HttpOnly; Path=/; SameSite=Lax"))
        assertEquals(listOf("synthetic-new"),jar.loadForRequest(auth).map { it.value })
        assertTrue(jar.loadForRequest(auth).single().hostOnly)
        jar.importAuthCookies(emptyList())
        assertTrue(jar.loadForRequest(auth).isEmpty())
    }
    @Test fun encryptedVaultPayloadRoundTripRetainsCookieScope() {
        val jar=SchoolCookieJar();jar.importAuthCookies(listOf("go-admin-tgt=synthetic; Secure; HttpOnly; Path=/; SameSite=Strict"))
        val restored=SchoolCookieJar().apply{restore(jar.encode())}
        assertEquals("synthetic",restored.loadForRequest(auth).single().value)
        assertTrue(restored.rawSnapshot().single().contains("SameSite=Strict"))
        assertTrue(restored.loadForRequest("https://op.sicau.edu.cn/".toHttpUrl()).isEmpty())
    }
    @Test fun logoutFencesLateNativeCookiesAndLegacySnapshotsAreNotRevived() {
        var epoch=0L
        val jar=SchoolCookieJar { epoch }
        jar.importAuthCookies(listOf("go-admin-tgt=synthetic; Path=/; Secure; HttpOnly"))
        assertEquals(1,jar.snapshot().size)
        epoch++
        jar.acceptRawCookies(auth,listOf("go-admin-tgt=late; Path=/; Secure"))
        assertTrue(jar.snapshot().isEmpty())
        assertTrue(jar.rawSnapshot().isEmpty())
        val legacy=SchoolCookieJar().apply { restore("go-admin-tgt=old; Path=/; Secure".toByteArray()) }
        assertTrue(legacy.snapshot().isEmpty())
    }
    @Test fun errorsDoNotEchoExceptions() {
        assertEquals(SchoolFailure.DNS,SchoolFailure.from(java.net.UnknownHostException("private-host")))
        assertEquals(SchoolFailure.TLS,SchoolFailure.from(javax.net.ssl.SSLHandshakeException("private-detail")))
        assertEquals(SchoolFailure.TIMEOUT,SchoolFailure.from(java.net.SocketTimeoutException("private-detail")))
        assertFalse(SchoolFailure.from(java.io.IOException("secret-cookie")).userMessage.contains("secret-cookie"))
    }
}

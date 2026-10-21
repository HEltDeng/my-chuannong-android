package com.mychuannong.app.school

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

/** Synthetic transport only; never sends credentials or SMS requests to the school. */
class SicauLoginSecurityRegressionTest {
    private val prefix = "/https/" + "a".repeat(64)
    private val route = requireNotNull(SicauJiaowuRoute.fromResourcePage("https://webvpn.sicau.edu.cn$prefix/web/web/web/index.asp"))
    private fun failure(expected: JiaowuFailure, action: () -> Unit) {
        try { action(); fail("Expected $expected") }
        catch (e: JiaowuException) { assertEquals(expected, e.reason) }
    }
    private fun profile(id: String) = "<table><tr><td>学号</td><td>$id</td></tr></table>"

    @Test fun nativeFormUsesTheOfficialDocumentEncodingUnlessTheFormOverridesIt() {
        val fields = "<form><input name=sign value=a><input name=hour_key value=b></form>"
        val official = "<meta http-equiv='Content-Type' content='text/html; charset=gb2312'>" + fields
        assertEquals("GBK", SicauJiaowuProtocol.loginFields(official).charset.name())
        assertEquals("UTF-8", SicauJiaowuProtocol.loginFields(official, "text/html; charset=UTF-8").charset.name())
        assertEquals("UTF-8", SicauJiaowuProtocol.loginFields(official.replace("<form>", "<form accept-charset='UTF-8'>")).charset.name())
        val body = FormBody.Builder(SicauJiaowuProtocol.loginFields(official).charset).add("pwd", "测试+&=").build()
        val buffer = Buffer(); body.writeTo(buffer)
        assertEquals("pwd=%B2%E2%CA%D4%2B%26%3D", buffer.readUtf8())
    }
    @Test fun responseMetaEncodingIsUsedWhenTheServerOmitsTheHeaderCharset() {
        val html = "<meta charset='utf-8'>学校要求短信验证"
        assertEquals(html, SicauJiaowuProtocol.decode(html.toByteArray(Charsets.UTF_8), "text/html"))
        assertEquals("密码错误", SicauJiaowuProtocol.decode("密码错误".toByteArray(Charset.forName("GBK")), "text/html; charset=gb2312"))
    }
    @Test fun schoolSecurityRequirementsAreNeitherGenericErrorsNorSuccessfulRedirects() {
        for (status in listOf(200, 302)) {
            failure(JiaowuFailure.SMS_VERIFICATION_REQUIRED) {
                SicauJiaowuProtocol.classifyLogin(status,
                    "<script>alert('请通过验证码重新登录教务系统并及时修改密码');location='verify.asp'</script>")
            }
            failure(JiaowuFailure.PASSWORD_CHANGE_REQUIRED) {
                SicauJiaowuProtocol.classifyLogin(status, "<script>alert('密码已过期，需要修改密码');location='password.asp'</script>")
            }
        }
        assertTrue(JiaowuFailure.SMS_VERIFICATION_REQUIRED.supportsSchoolVerification)
        assertTrue(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN.supportsSchoolVerification)
        assertFalse(JiaowuFailure.CREDENTIAL_REJECTED.supportsSchoolVerification)
    }
    @Test fun pendingVerificationCannotBeOverriddenByAnIdentityTableOrWrongOwner() {
        failure(JiaowuFailure.SMS_VERIFICATION_REQUIRED) {
            SicauJiaowuProtocol.verifyIdentity("<p>请完成短信验证</p>" + profile("20260001"), "20260001")
        }
        assertEquals("20260001", SicauJiaowuProtocol.verifyIdentity("<a>修改密码</a>" + profile("20260001"), "20260001").studentId)
        failure(JiaowuFailure.IDENTITY_MISMATCH) { SicauJiaowuProtocol.verifyIdentity(profile("20260002"), "20260001") }
    }
    @Test fun browserVerificationNavigationOnlyAllowsOfficialAcademicAndAuthenticationOrigins() {
        for (url in listOf(SicauJiaowuProtocol.LOGIN_PAGE, "https://webvpn.sicau.edu.cn/login",
            "https://auth.sicau.edu.cn/wui/")) assertTrue(url, SchoolVerificationPolicy.allowed(url))
        for (url in listOf("http://jiaowu.sicau.edu.cn/", "https://jiaowu.sicau.edu.cn.evil.test/",
            "https://jiaowu.sicau.edu.cn:444/", "https://user@jiaowu.sicau.edu.cn/", "file:///sdcard/a.html",
            "javascript:alert(1)", "https://www.sicau.edu.cn/")) assertFalse(url, SchoolVerificationPolicy.allowed(url))
    }
    private class DelayedCookies : WebVpnCookieBridge {
        var header = "vpn=synthetic; ASPSESSIONIDTEST=old"
        var awaited = 0
        override fun get(url: String) = header
        override fun accept(url: String, setCookie: String) { fail("Unawaited write loses the new session") }
        override fun flush() {}
        override suspend fun acceptAndAwait(url: String, setCookie: String, current: () -> Boolean) {
            assertTrue(current()); delay(5); assertTrue(current())
            header = "vpn=synthetic; " + setCookie.substringBefore(';')
            awaited++
        }
    }
    @Test fun nativeLoginAwaitsBrowserCookieCompletionBeforePostingFreshFields() = runBlocking {
        val cookies = DelayedCookies()
        val jar = SicauJiaowuCookieJar(route, cookies)
        var step = 0
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("synthetic").header("Content-Type", "text/html; charset=gb2312")
            val body = when (step++) {
                0 -> {
                    response.header("Set-Cookie", "ASPSESSIONIDTEST=fresh; Path=$prefix/; Secure; HttpOnly; SameSite=Lax")
                    "<form><input name=sign value=one-use><input name=hour_key value=fresh-hour></form>"
                }
                1 -> {
                    assertEquals(1, cookies.awaited)
                    assertEquals("fresh", jar.loadForRequest(request.url).single { it.name == "ASPSESSIONIDTEST" }.value)
                    response.code(302).header("Location", "$prefix/xuesheng/")
                        .header("Set-Cookie", "ASPSESSIONIDTEST=authenticated; Path=$prefix/; Secure; HttpOnly")
                    ""
                }
                else -> {
                    assertEquals(2, cookies.awaited)
                    assertEquals("authenticated", jar.loadForRequest(request.url).single { it.name == "ASPSESSIONIDTEST" }.value)
                    profile("20260001")
                }
            }
            response.body(body.toByteArray(Charset.forName("GBK")).toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        assertEquals("20260001", session.login("20260001", "synthetic").studentId)
        assertEquals(3, step)
        assertTrue(jar.encode().contains("HttpOnly"))
    }
    @Test fun schoolSmsChallengeDoesNotRetryThePasswordOrFetchTheProtectedProfile() = runBlocking {
        val cookies = object : WebVpnCookieBridge {
            override fun get(url: String) = "vpn=synthetic; ASPSESSIONIDTEST=synthetic"
            override fun accept(url: String, setCookie: String) {}
            override fun flush() {}
        }
        val jar = SicauJiaowuCookieJar(route, cookies)
        val events = mutableListOf<String>()
        var step = 0
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val body = when (step++) {
                0 -> "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                1 -> "<script>alert('请通过验证码重新登录教务系统并及时修改密码');location='verify.asp'</script>"
                else -> error("Must not fetch a profile or retry credentials before school verification")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type", "text/html; charset=utf-8").body(body.toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, events::add)
        try { session.login("20260001", "synthetic-password"); fail("School verification is required") }
        catch (e: JiaowuException) { assertEquals(JiaowuFailure.SMS_VERIFICATION_REQUIRED, e.reason) }
        assertEquals(2, step)
        assertNull(session.verifiedId)
        assertTrue(events.contains("failure=SMS_VERIFICATION_REQUIRED"))
        assertTrue(events.none { it.contains("synthetic-password") || it.contains("20260001") || it.contains("synthetic-sign") })
    }
    @Test fun browserContinuationUsesOnlyAProfileGetAndRequiresTheExactOwner() = runBlocking {
        for (id in listOf("20260001", "20260002")) {
            val cookies = object : WebVpnCookieBridge {
                override fun get(url: String) = "vpn=synthetic; ASPSESSIONIDTEST=synthetic"
                override fun accept(url: String, setCookie: String) {}
                override fun flush() {}
            }
            val jar = SicauJiaowuCookieJar(route, cookies)
            var requests = 0
            val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
                val request = chain.request(); requests++
                assertEquals("GET", request.method)
                assertEquals(route.url(SicauJiaowuProtocol.PERSONAL_INFO), request.url.toString())
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                    .header("Content-Type", "text/html; charset=utf-8").body(profile(id).toResponseBody()).build()
            }.build()
            val session = SicauJiaowuSession(jar, client, {})
            if (id == "20260001") assertEquals(id, session.fetchPersonalInfo("20260001").studentId)
            else {
                try { session.fetchPersonalInfo("20260001"); fail("Wrong owner") }
                catch (e: JiaowuException) { assertEquals(JiaowuFailure.IDENTITY_MISMATCH, e.reason) }
                assertNull(session.verifiedId)
            }
            assertEquals(1, requests)
        }
    }
}

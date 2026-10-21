package com.mychuannong.app.school

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Synthetic school responses; no real account or network. */
class AcademicSessionRecoveryTest {
    private val owner = "20260001"
    private val profile = "<table><tr><td>学号</td><td>$owner</td></tr></table>"
    private fun session(reply: (Request) -> Pair<Int, String>, location: String? = null): SicauJiaowuSession {
        val jar = SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.PERSONAL_INFO.toHttpUrl(),
            listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val (status, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("synthetic")
                .header("Content-Type", "text/html; charset=utf-8")
                .apply { if(location != null) header("Location", location) }
                .body(body.toResponseBody("text/html; charset=utf-8".toMediaType())).build()
        }.build()
        return SicauJiaowuSession(jar, client, {})
    }
    @Test fun sameOriginRedirectCanRestoreProfileWithoutRevokingBinding() = runBlocking {
        var requests = 0
        val session = session({ request ->
            assertEquals("GET", request.method)
            if(requests++ == 0) 302 to "" else 200 to profile
        }, "/xuesheng/dangan/banji/bjiben.asp?restored=1")
        assertEquals(owner, session.fetchPersonalInfo(owner).studentId)
        assertEquals(2, requests)
    }
    @Test fun forbiddenAndUnknownRedirectsAreRecoverable() = runBlocking {
        for ((status, location, expected) in listOf(
            Triple(403, null, JiaowuFailure.PERSONAL_INFO_HTTP_ERROR),
            Triple(302, "https://unrelated.test/login", JiaowuFailure.BUSINESS_REDIRECT),
            Triple(302, null, JiaowuFailure.BUSINESS_REDIRECT),
            Triple(302, SicauJiaowuProtocol.PERSONAL_INFO, JiaowuFailure.BUSINESS_REDIRECT)
        )) {
            var requests = 0
            try { session({ requests++; status to "" }, location).fetchPersonalInfo(owner); fail() }
            catch(e: JiaowuException) { assertEquals(expected, e.reason); assertFalse(e.reason.invalidatesSession) }
            assertEquals("No foreign request or redirect loop", 1, requests)
        }
    }
    @Test fun actualLoginPageAndWrongIdentityStillRequireAuthentication() = runBlocking {
        for(body in listOf("<form><input type=password name=pwd><input name=hour_key></form>",
            profile.replace(owner, "20260002"))) {
            try { session({ 200 to body }).fetchPersonalInfo(owner); fail() }
            catch(e: JiaowuException) { assertTrue(e.reason.invalidatesSession) }
        }
    }
    @Test fun missingCookiesAndUnrestoredMemoryAreNotProofOfExpiry() = runBlocking {
        val session = session({ error("No request without session") })
        session.jar.clear()
        try { session.fetchPersonalInfo(owner); fail() }
        catch(e: JiaowuException) { assertEquals(JiaowuFailure.SESSION_COOKIE_MISSING, e.reason); assertFalse(e.reason.invalidatesSession) }
        try { session.fetchBusiness(JiaowuPage.COURSES); fail() }
        catch(e: JiaowuException) { assertEquals(JiaowuFailure.SESSION_UNAVAILABLE, e.reason); assertFalse(e.reason.invalidatesSession) }
        assertFalse(JiaowuFailure.WEBVPN_REQUIRED.invalidatesSession)
        assertFalse(JiaowuFailure.NETWORK_UNREACHABLE.invalidatesSession)
    }
}

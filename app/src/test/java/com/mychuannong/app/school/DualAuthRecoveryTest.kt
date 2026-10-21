package com.mychuannong.app.school

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Synthetic HTTP only; exercises the real form parser, cookie jar and recovery coordinator. */
class DualAuthRecoveryTest {
    private class Fixture : AcademicRecoveryPort {
        val id = "20260001"
        var epoch = 0L
        var bound: String? = id
        var clock = 100_000L
        var valid = false
        var profileOwner = id
        var networkDown = false
        var postNetworkDown = false
        var challenge = false
        var reject = false
        var posts = 0
        var saves = 0
        var formGets = 0
        var beforePost: () -> Unit = {}
        var afterForm: () -> Unit = {}
        var authorization=true
        var transportFailure: JiaowuFailure? = null
        var stored: AcademicCredential? = AcademicCredential(id, "synthetic-secret", SicauJiaowuRoute.Direct)
        var active: SicauJiaowuSession? = makeSession()
        var web: SicauJiaowuSession? = null
        override fun revision() = epoch
        override fun owner() = bound
        override suspend fun restore() = active
        override suspend fun browser(previous: SicauJiaowuSession?) = web
        override suspend fun credential() = stored
        override suspend fun storeCredential(value: AcademicCredential, epoch: Long) {
            assertEquals(this.epoch, epoch); stored = value
        }
        override suspend fun transport(route: SicauJiaowuRoute, force: Boolean) {
            transportFailure?.let { throw JiaowuException(it) }
        }
        override fun create(route: SicauJiaowuRoute) = makeSession()
        override suspend fun save(session: SicauJiaowuSession, epoch: Long) {
            assertEquals(this.epoch, epoch); assertEquals(bound, session.verifiedId)
            active = session; saves++
        }
        fun recovery() = AcademicRecovery(this) { clock }
        fun makeSession(): SicauJiaowuSession {
            val jar = SicauJiaowuCookieJar()
            fun cookie() { jar.acceptRawCookies(SicauJiaowuProtocol.PERSONAL_INFO.toHttpUrl(),
                listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly")) }
            cookie()
            val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
                val request = chain.request()
                if (networkDown) throw IOException("synthetic")
                val body = when(request.url.toString()) {
                    SicauJiaowuProtocol.LOGIN_PAGE -> {
                        formGets++; cookie(); afterForm()
                        "<form><input name=sign value=synthetic><input name=hour_key value=synthetic>" +
                            (if(challenge) "<input name=captcha placeholder=验证码>" else "") + "</form>"
                    }
                    SicauJiaowuProtocol.LOGIN_POST -> {
                        posts++
                        assertEquals("POST", request.method)
                        assertEquals(posts, stored!!.failures) // Journal is durable before each submission.
                        beforePost()
                        if(postNetworkDown) throw IOException("synthetic")
                        if(reject) "<script>alert('用户名或密码错误')</script>"
                        else { valid=true; "<script>location='/xuesheng/'</script>" }
                    }
                    SicauJiaowuProtocol.PERSONAL_INFO -> if(valid)
                        "<table><tr><td>学号</td><td>$profileOwner</td></tr></table>"
                        else "<form><input type=password name=pwd><input name=hour_key></form>"
                    else -> "<html><table><tr><td>synthetic data</td></tr></table></html>"
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                    .header("Content-Type", "text/html; charset=utf-8").body(body.toResponseBody()).build()
            }.build()
            return SicauJiaowuSession(jar, client, {}, authorized={authorization})
        }
    }
    private suspend fun failure(reason: JiaowuFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $reason") } catch(e: JiaowuException) { assertEquals(reason, e.reason) }
    }
    @Test fun validSessionDoesNotReadOrSubmitSavedPassword() = runBlocking {
        val f=Fixture().apply { valid=true; stored=null }
        assertEquals(f.id, f.recovery().read { _, id -> id })
        assertEquals(0, f.posts); assertEquals(0, f.formGets)
    }
    @Test fun expiredSessionLogsInOnceAndContinuesOriginalRead() = runBlocking {
        val f=Fixture()
        assertEquals("fresh", f.recovery().read { _, _ -> "fresh" })
        assertEquals(1, f.posts); assertTrue(f.valid); assertEquals(0, f.stored!!.failures)
    }
    @Test fun concurrentPullsShareRecoveredSession() = runBlocking {
        val f=Fixture(); val recovery=f.recovery()
        val results=(1..8).map { async { recovery.read { _, id -> id } } }.awaitAll()
        assertEquals(List(8) { f.id }, results); assertEquals(1, f.posts)
    }
    @Test fun expiryBetweenIdentityAndBusinessReadIsRecoveredAndGetReplayed() = runBlocking {
        val f=Fixture().apply { valid=true }
        var reads=0
        assertEquals("fresh",f.recovery().read { _, _ ->
            if(reads++==0) { f.valid=false; throw JiaowuException(JiaowuFailure.SESSION_EXPIRED) }
            "fresh"
        })
        assertEquals(2,reads); assertEquals(1,f.posts)
    }
    @Test fun browserSessionWinsBeforePasswordRecovery() = runBlocking {
        val f=Fixture().apply { active=null; valid=true; web=makeSession() }
        f.recovery().read { _, _ -> Unit }
        assertEquals(0,f.posts)
    }
    @Test fun absentConsentAndWrongOwnerNeverSubmit() = runBlocking {
        for(wrongOwner in listOf(false,true)) {
            val f=Fixture().apply { stored=if(wrongOwner) AcademicCredential("20260002","synthetic",SicauJiaowuRoute.Direct) else null }
            failure(JiaowuFailure.AUTO_LOGIN_NOT_ENABLED) { f.recovery().read { _, _ -> Unit } }
            assertEquals(0,f.posts)
        }
    }
    @Test fun networkFailurePreservesSessionAndCredentialBudget() = runBlocking {
        val f=Fixture().apply { networkDown=true }
        val old=f.active
        failure(JiaowuFailure.NETWORK_UNREACHABLE) { f.recovery().read { _, _ -> Unit } }
        assertSame(old,f.active); assertEquals(0,f.posts); assertEquals(0,f.stored!!.failures)
    }
    @Test fun identityMismatchNeverUsesSavedPassword() = runBlocking {
        val f=Fixture().apply { valid=true; profileOwner="20260002" }
        failure(JiaowuFailure.IDENTITY_MISMATCH) { f.recovery().read { _, _ -> Unit } }
        assertEquals(0,f.posts)
    }
    @Test fun captchaStopsBeforePostAndRemainsBlockedAcrossCoordinatorRestart() = runBlocking {
        val f=Fixture().apply { challenge=true }
        failure(JiaowuFailure.CAPTCHA_REQUIRED) { f.recovery().read { _, _ -> Unit } }
        assertEquals(0,f.posts); assertTrue(f.stored!!.blocked)
        f.clock+=120_000
        failure(JiaowuFailure.AUTO_LOGIN_STOPPED) { f.recovery().read { _, _ -> Unit } }
        assertEquals(1,f.formGets)
    }
    @Test fun rejectedPasswordIsNotRepeatedByFollowingRefresh() = runBlocking {
        val f=Fixture().apply { reject=true }
        failure(JiaowuFailure.CREDENTIAL_REJECTED) { f.recovery().read { _, _ -> Unit } }
        f.clock+=120_000
        failure(JiaowuFailure.AUTO_LOGIN_STOPPED) { f.recovery().read { _, _ -> Unit } }
        assertEquals(1,f.posts)
    }
    @Test fun logoutDuringLoginCannotSaveOrReturnData() = runBlocking {
        val f=Fixture()
        f.beforePost={f.epoch++; f.bound=null; f.stored=null}
        try { f.recovery().read { _, _ -> fail("No data after logout") }; fail("Cancelled") }
        catch(_: CancellationException) {}
        assertEquals(0,f.saves); assertNull(f.stored)
    }
    @Test fun disablingConsentDuringLoginCannotRecreateCredentials() = runBlocking {
        val f=Fixture()
        f.beforePost={f.epoch++; f.stored=null}
        try { f.recovery().read { _, _ -> Unit }; fail("Cancelled") } catch(_: CancellationException) {}
        assertNull(f.stored); assertEquals(0,f.saves)
    }
    @Test fun transportFailureDoesNotConsumeAcademicCredentials() = runBlocking {
        val f=Fixture().apply { transportFailure=JiaowuFailure.WEBVPN_REQUIRED }
        failure(JiaowuFailure.WEBVPN_REQUIRED) { f.recovery().read { _, _ -> Unit } }
        assertEquals(0,f.posts); assertEquals(0,f.stored!!.failures)
    }
    @Test fun cooldownAndAttemptLimitSurviveProcessStyleRestarts() = runBlocking {
        val f=Fixture().apply { postNetworkDown=true }
        failure(JiaowuFailure.NETWORK_UNREACHABLE) { f.recovery().read { _, _ -> Unit } }
        failure(JiaowuFailure.AUTO_LOGIN_COOLDOWN) { f.recovery().read { _, _ -> Unit } }
        assertEquals(1,f.posts)
        f.clock+=120_000
        failure(JiaowuFailure.NETWORK_UNREACHABLE) { f.recovery().read { _, _ -> Unit } }
        assertEquals(2,f.stored!!.failures)
        f.clock+=120_000
        failure(JiaowuFailure.AUTO_LOGIN_STOPPED) { f.recovery().read { _, _ -> Unit } }
        assertEquals(2,f.posts)
    }
    @Test fun repeatedExpiryHasOnlyOneRecoveryPerRead() = runBlocking {
        val f=Fixture()
        failure(JiaowuFailure.SESSION_EXPIRED) { f.recovery().read { _, _ -> throw JiaowuException(JiaowuFailure.SESSION_EXPIRED) } }
        assertEquals(1,f.posts)
    }
    @Test fun immediatelyRevokedSuccessfulLoginCannotCauseRapidResubmission() = runBlocking {
        val f=Fixture()
        f.recovery().read { _, _ -> Unit }
        f.valid=false
        failure(JiaowuFailure.AUTO_LOGIN_COOLDOWN) { f.recovery().read { _, _ -> Unit } }
        assertEquals(1,f.posts)
    }
    @Test fun consentRevokedWhileLoadingFormPreventsCredentialPost() = runBlocking {
        val f=Fixture()
        f.afterForm={f.authorization=false}
        try { f.recovery().read { _, _ -> Unit }; fail("Cancelled") } catch(_: CancellationException) {}
        assertEquals(1,f.formGets); assertEquals(0,f.posts)
    }
}

package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SchoolWebSessionPolicyTest {
    @Test fun snapshotsRetainAttributesAndNeverExtendAnAbsoluteExpiry() {
        val url="https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"
        val raw="session=synthetic; Path=/mobile/; Secure; HttpOnly; SameSite=Strict; Max-Age=120"
        val stored=requireNotNull(SchoolWebCookiePolicy.durable(url,raw))
        val cookie=requireNotNull(SchoolWebCookiePolicy.valid(url,stored))
        assertFalse(stored.contains("Max-Age",true))
        assertTrue(stored.contains("SameSite=Strict"))
        assertTrue(cookie.httpOnly && cookie.secure && cookie.hostOnly)
        assertEquals(stored,SchoolWebCookiePolicy.durable(url,stored))
        assertNull(SchoolWebCookiePolicy.valid(url,stored,cookie.expiresAt))
        assertNull(SchoolWebCookiePolicy.valid(url,"session=deleted; Path=/; Max-Age=0"))
    }

    @Test fun rejectForeignScopesAndPartitionsRatherThanWideningCookies() {
        val raw="s=synthetic; Path=/mobile/; Secure; HttpOnly"
        for(url in listOf("http://xsgz.sicau.edu.cn/mobile/", "https://xsgz.sicau.edu.cn.evil.test/mobile/",
            "https://user@xsgz.sicau.edu.cn/mobile/", "https://xsgz.sicau.edu.cn:444/mobile/",
            "https://xsgz.sicau.edu.cn/other/")) assertNull(SchoolWebCookiePolicy.valid(url,raw))
        val url="https://xsgz.sicau.edu.cn/mobile/"
        assertNull(SchoolWebCookiePolicy.valid(url,"s=synthetic; Domain=auth.sicau.edu.cn; Path=/"))
        assertNull(SchoolWebCookiePolicy.valid(url,"s=synthetic; Domain=edu.cn; Path=/"))
        assertNull(SchoolWebCookiePolicy.valid(url,"s=synthetic; Path=/; Secure; Partitioned"))
        // Parent-domain acceptance also needs Android's bundled public-suffix asset;
        // exercise it in SchoolWebSessionDeviceTest on the actual WebView runtime.
    }

    @Test fun persistedLocationsExcludeTicketsCodesAndFragments() {
        assertEquals("https://op.sicau.edu.cn/CasLogin.aspx",SchoolWebCookiePolicy.location(
            "https://op.sicau.edu.cn/CasLogin.aspx?ticket=synthetic&code=secret#/token=secret"))
        assertNull(SchoolWebCookiePolicy.location("https://external.example/login"))
    }

    @Test fun unknownAndLongDeadlinesDoNotTriggerExpiryWhileNearDeadlinesDo() {
        val now=1_000_000L
        assertFalse(SchoolWebSessionPolicy.needsCheck(Long.MAX_VALUE,now))
        assertFalse(SchoolWebSessionPolicy.needsCheck(now+120_001,now))
        assertTrue(SchoolWebSessionPolicy.needsCheck(now+120_000,now))
        assertTrue(SchoolWebSessionPolicy.needsCheck(now-1,now))
    }

    @Test fun vpnAndCentralLoginAreIndependentAndCallbacksAreNotLoginForms() {
        assertEquals(SchoolLoginKind.SSO,SchoolWebSessionPolicy.loginKind("https://auth.sicau.edu.cn/wui/#/?appid=x"))
        assertEquals(SchoolLoginKind.VPN,SchoolWebSessionPolicy.loginKind("https://webvpn.sicau.edu.cn/login?cas_login=true"))
        assertNull(SchoolWebSessionPolicy.loginKind("https://op.sicau.edu.cn/CasLogin.aspx?ticket=synthetic"))
        assertNull(SchoolWebSessionPolicy.loginKind("https://webvpn.sicau.edu.cn/https/synthetic/login"))
        assertNull(SchoolWebSessionPolicy.loginKind("https://auth.sicau.edu.cn.evil.test/wui/"))
    }

    @Test fun redirectLoopsAreBoundedAcrossContainersAndNewLoginCanTryAgain() {
        val attempts=SchoolSsoAttempts()
        assertTrue(attempts.claim("service-one","cookie-fingerprint-one"))
        assertFalse(attempts.claim("service-one","cookie-fingerprint-one"))
        assertTrue(attempts.claim("service-two","cookie-fingerprint-one"))
        assertTrue(attempts.claim("service-one","cookie-fingerprint-two"))
        attempts.clear()
        assertTrue(attempts.claim("service-one","cookie-fingerprint-two"))
        val laterExpiry=SchoolSsoAttempts()
        assertTrue(laterExpiry.claim("service-one","unchanged-tgt",0))
        assertFalse(laterExpiry.claim("service-one","unchanged-tgt",59_999))
        assertTrue(laterExpiry.claim("service-one","unchanged-tgt",60_000))
    }
    @Test fun expiredServiceGetsOnlyOneOfficialEntryRetryAndNeverReplaysPosts() {
        val recovery=SchoolServiceRecovery()
        val page="https://op.sicau.edu.cn/order.aspx"
        assertNull(recovery.entry(401,"POST",page,page))
        assertNull(recovery.entry(403,"GET",page,page))
        assertNull(recovery.entry(500,"GET",page,page))
        assertEquals("https://op.sicau.edu.cn/CasLogin.aspx",recovery.entry(401,"GET",page,page))
        assertNull(recovery.entry(401,"GET",page,page))
        assertNull(SchoolServiceRecovery().entry(401,"GET","https://auth.sicau.edu.cn/wui/",page))
        assertNull(SchoolServiceRecovery().entry(401,"GET",page,"https://external.example/"))
    }
}

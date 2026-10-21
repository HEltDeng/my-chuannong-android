package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SchoolSsoNavigationTest {
    @Test fun loginFinishesOnlyAtOfficialBusinessLanding() {
        assertTrue(SchoolSsoNavigation.isLoginDestination("https://ywtb.sicau.edu.cn/"))
        assertTrue(SchoolSsoNavigation.isLoginDestination("https://ywtb.sicau.edu.cn/#/index"))
        listOf("https://auth.sicau.edu.cn/wui/", "https://ywtb.sicau.edu.cn/cas/login/user?ticket=test",
            "https://ywtb.sicau.edu.cn/#/login", "http://ywtb.sicau.edu.cn/",
            "https://ywtb.sicau.edu.cn.evil.test/", "https://user@ywtb.sicau.edu.cn/",
            "https://ywtb.sicau.edu.cn:444/", "not a url").forEach {
            assertFalse(it, SchoolSsoNavigation.isLoginDestination(it))
        }
    }
    private val entry = "https://auth.sicau.edu.cn/wui/#/?appid=test&service=https%3A%2F%2Fxsgz.sicau.edu.cn%2Fcallback%3Fnext%3D%252Fmobile"
    @Test fun preservesOfficialCallbackAndOnlyTriesExistingSession() {
        assertNull(SchoolSsoNavigation.reuseSession(entry,false))
        assertEquals(entry+"&cascallback=choose", SchoolSsoNavigation.reuseSession(entry,true))
        assertNull(SchoolSsoNavigation.reuseSession(entry+"&cascallback=choose",true))
        assertNull(SchoolSsoNavigation.reuseSession(entry+"&cascallback=err",true))
    }
    @Test fun rejectsOtherOriginsAndIncompleteOrSpecialLoginFlows() {
        listOf(entry.replace("auth.sicau.edu.cn","auth.sicau.edu.cn.evil.test"),
            entry.replace("https:","http:"),entry.replace("/wui/","/other/"),
            "https://auth.sicau.edu.cn/wui/#/?appid=test",
            entry.replace("#/?","#/h5?"),entry+"&%63ascallback=wxlogined").forEach {
            assertNull(SchoolSsoNavigation.reuseSession(it,true))
        }
    }
    @Test fun vpnAuthenticationKeepsItsOwnOriginAndOfficialCallback() {
        val prefix = "/https/" + "a".repeat(64)
        val proxy = entry.replace("https://auth.sicau.edu.cn", "https://webvpn.sicau.edu.cn$prefix")
        assertEquals(proxy + "&cascallback=choose", SchoolSsoNavigation.reuseSession(proxy, true))
        assertEquals(listOf("https://webvpn.sicau.edu.cn$prefix/", "https://webvpn.sicau.edu.cn$prefix/wui/",
            "https://webvpn.sicau.edu.cn$prefix/api/v1/cas/login"), SchoolSsoNavigation.authLocations(proxy))
        assertNull(SchoolSsoNavigation.reuseSession(proxy + "&cascallback=err", true))
        assertNull(SchoolSsoNavigation.reuseSession(proxy.replace("/wui/", "/payment/"), true))
        assertNull(SchoolSsoNavigation.reuseSession(proxy.replace("webvpn.sicau.edu.cn", "evil.test"), true))
    }
    @Test fun authCookieLookupsIncludeApiScopedSessionAndRejectForeignOrigins() {
        assertTrue(SchoolSsoNavigation.authLocations(entry).contains("https://auth.sicau.edu.cn/api/v1/cas/login"))
        listOf("https://auth.sicau.edu.cn.evil.test/wui/", "http://auth.sicau.edu.cn/wui/",
            "https://user@auth.sicau.edu.cn/wui/", "https://auth.sicau.edu.cn:444/wui/").forEach {
            assertTrue(SchoolSsoNavigation.authLocations(it).isEmpty())
        }
    }
}

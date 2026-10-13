package com.mychuannong.app.school
import org.junit.Assert.*
import org.junit.Test

class WebVpnCookiePolicyTest {
    private val url="https://webvpn.sicau.edu.cn/https/synthetic/path"
    @Test fun cookieAttributesAndScopeArePreserved() {
        val raw="synthetic=fixture; Path=/https/synthetic/; Secure; HttpOnly; SameSite=Lax"
        assertEquals(raw,WebVpnCookiePolicy.durable(url,raw))
        assertNull(WebVpnCookiePolicy.valid("https://jiaowu.sicau.edu.cn/",raw))
        assertNull(WebVpnCookiePolicy.valid(url,"x=fixture; Domain=example.org; Path=/"))
        assertNull(WebVpnCookiePolicy.valid("https://webvpn.sicau.edu.cn/other/",raw))
    }
    @Test fun expiryIsNotExtendedByRestoringRelativeMaxAge() {
        val raw=requireNotNull(WebVpnCookiePolicy.durable(url,"x=fixture; Path=/; Max-Age=60; Secure; SameSite=Strict"))
        assertFalse(raw.contains("Max-Age",true));assertTrue(raw.contains("SameSite=Strict"))
        val expiry=requireNotNull(WebVpnCookiePolicy.valid(url,raw)).expiresAt
        assertNull(WebVpnCookiePolicy.valid(url,raw,expiry+1))
        assertNull(WebVpnCookiePolicy.durable(url,"x=fixture; Max-Age=0; Path=/"))
    }
}

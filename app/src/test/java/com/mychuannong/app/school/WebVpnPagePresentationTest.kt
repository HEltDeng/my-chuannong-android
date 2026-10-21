package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class WebVpnPagePresentationTest {
    private val vpn = SicauJiaowuRoute.VPN_ORIGIN
    private val proxy = "$vpn/https/0123456789abcdef0123456789abcdef"

    @Test fun authenticationAndSecondFactorPagesCanBePresented() {
        listOf("https://auth.sicau.edu.cn/wui/", "https://auth.sicau.edu.cn/security/otp",
            "$vpn/login?cas_login=true", "$vpn/wui/", "$proxy/wui/#/login").forEach {
            assertTrue(it, WebVpnPagePresentation.isAuthentication(it))
        }
    }
    @Test fun resourceLandingAndAcademicPagesAreNeverAuthenticationScreens() {
        listOf("$vpn/", "$vpn/portal", "$vpn/m/portal", "$vpn/portal#/login",
            "$proxy/jiaoshi/bangong/main/index1.asp", "$proxy/", "$proxy/login", "https://www.sicau.edu.cn/").forEach {
            assertFalse(it, WebVpnPagePresentation.isAuthentication(it))
        }
        assertTrue(WebVpnPagePresentation.mayContainRootLogin("$vpn/"))
        assertFalse(WebVpnPagePresentation.mayContainRootLogin("$vpn/portal"))
    }
    @Test fun lookalikesAndNonOfficialOriginsCannotBeExposed() {
        listOf("http://auth.sicau.edu.cn/wui/", "https://auth.sicau.edu.cn.evil.test/wui/",
            "https://auth.sicau.edu.cn:8443/wui/", "https://user@auth.sicau.edu.cn/wui/",
            "javascript:alert(1)", "$vpn/portal?next=/wui/").forEach {
            assertFalse(it, WebVpnPagePresentation.isAuthentication(it))
            assertFalse(it, WebVpnPagePresentation.mayContainRootLogin(it))
        }
    }
}

package com.mychuannong.app.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortalInspectionTest {
    private val prefix = "/https/" + "a".repeat(64)
    private val resource = "https://webvpn.sicau.edu.cn$prefix/web/web/web/index.asp"
    private val academic = listOf("教务系统 jiaowu.sicau.edu.cn" to resource)

    @Test fun connectedRequiresAnOfficialPortalAndFreshAcademicResource() {
        assertEquals(prefix, PortalInspection.connectedRoute("https://webvpn.sicau.edu.cn/portal", false, academic)?.prefix)
        assertNull(PortalInspection.connectedRoute("https://webvpn.sicau.edu.cn/portal", true, academic))
        assertNull(PortalInspection.connectedRoute(resource, false, academic))
        assertNull(PortalInspection.connectedRoute("https://auth.sicau.edu.cn/", false, academic))
        assertNull(PortalInspection.connectedRoute("https://webvpn.sicau.edu.cn/", false, emptyList()))
    }

    @Test fun unrelatedOrDeceptiveLinksDoNotProveVpnAccess() {
        val portal = "https://webvpn.sicau.edu.cn/"
        assertNull(PortalInspection.connectedRoute(portal, false, listOf("jiaowu.sicau.edu.cn.example" to resource)))
        assertNull(PortalInspection.connectedRoute(portal, false, listOf("jiaowu.sicau.edu.cn" to "https://example.invalid$prefix/")))
        assertNull(PortalInspection.connectedRoute(portal, false, listOf("jiaowu.sicau.edu.cn" to "https://webvpn.sicau.edu.cn/portal")))
        assertNull(PortalInspection.connectedRoute("http://webvpn.sicau.edu.cn/", false, academic))
        assertNull(PortalInspection.connectedRoute("https://webvpn.sicau.edu.cn:8443/", false, academic))
    }
}

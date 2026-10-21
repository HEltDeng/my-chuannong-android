package com.mychuannong.app.school

import com.mychuannong.app.SafeUrl
import com.mychuannong.app.core.web.ServiceWebPolicy
import org.junit.Assert.*
import org.junit.Test

class StudentAffairsWebPolicyTest {
    private val hall = "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"

    @Test fun hallIgnoresLoginQueryButDoesNotTreatChildRoutesAsRoot() {
        assertTrue(SchoolWebPolicy.isStudentHall(hall))
        assertTrue(SchoolWebPolicy.isStudentHall("$hall/?from=login"))
        assertFalse(SchoolWebPolicy.isStudentHall("$hall#/details"))
        assertFalse(SchoolWebPolicy.isStudentHall("$hall/details"))
        assertFalse(SchoolWebPolicy.isStudentHall("https://other.example/mobile/stuhall/studentindex"))
    }

    @Test fun hallAndSchoolLoginCanStayInSchoolContainer() {
        assertTrue(SafeUrl.school(hall))
        assertTrue(SafeUrl.school("https://auth.sicau.edu.cn/cas/login"))
        assertTrue(SafeUrl.school("https://xsgz.sicau.edu.cn/mobile/login"))
        assertFalse(SafeUrl.school("http://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"))
        assertFalse(SafeUrl.school("https://xsgz.sicau.edu.cn.evil.example/"))
        assertFalse(SafeUrl.school("https://xsgz.sicau.edu.cn@evil.example/"))
    }

    @Test fun originalMobileLayoutIsPreservedAndBackSkipsLogin() {
        assertEquals(ServiceWebPolicy.mobileViewport, SchoolWebPolicy.adaptation(hall))
        assertEquals(0, SchoolWebPolicy.previousBusinessIndex(listOf(hall,
            "https://auth.sicau.edu.cn/cas/login", "https://xsgz.sicau.edu.cn/mobile/details"), 2))
        assertNull(SchoolWebPolicy.previousBusinessIndex(listOf(hall), 0))
    }

    @Test fun paymentBackSkipsCasEntryButPreservesBusinessHistory() {
        val entry="https://op.sicau.edu.cn/CasLogin.aspx"
        val home="https://op.sicau.edu.cn/"
        assertNull(SchoolWebPolicy.previousBusinessIndex(listOf(entry, home), 1))
        assertEquals(1, SchoolWebPolicy.previousBusinessIndex(listOf(entry, home,
            "https://op.sicau.edu.cn/orders.aspx"), 2))
    }

    @Test fun studentDeepLinksUseOriginalMobilePagesAndReturnOnlyFromOfficialLanding() {
        val targets = listOf(
            "https://xsgz.sicau.edu.cn/mobile/scoreLocus/list",
            "https://xsgz.sicau.edu.cn/mobile/signTask",
            "https://xsgz.sicau.edu.cn/mobile/leaveSchoolRegister",
        )
        assertEquals(targets.toSet(), StudentAffairsServices.entries.values.map { it.url }.toSet())
        targets.forEach { target ->
            assertTrue(SchoolWebPolicy.isStudentService(target))
            assertEquals(ServiceWebPolicy.mobileViewport, SchoolWebPolicy.adaptation(target))
            assertTrue(SchoolWebPolicy.shouldReturnToStudentTarget(target, hall))
            assertTrue(SchoolWebPolicy.shouldReturnToStudentTarget(target, "https://xsgz.sicau.edu.cn/"))
            assertFalse(SchoolWebPolicy.shouldReturnToStudentTarget(target, target))
            assertFalse(SchoolWebPolicy.shouldReturnToStudentTarget(target, "https://auth.sicau.edu.cn/cas/login"))
            assertFalse(SchoolWebPolicy.shouldReturnToStudentTarget(target, "https://xsgz.sicau.edu.cn/mobile/otherForm"))
            assertFalse(SchoolWebPolicy.shouldReturnToStudentTarget(target, "https://xsgz.sicau.edu.cn.evil.example/"))
            assertFalse(SchoolWebPolicy.shouldReturnToStudentTarget(target, "http://xsgz.sicau.edu.cn/"))
        }
    }

    @Test fun failedStudentConnectionsCanRecoverThroughOfficialGatewayOnlyOnce() {
        val targets = listOf(hall) + StudentAffairsServices.entries.values.map { it.url }
        targets.forEach { target ->
            assertTrue(SchoolWebPolicy.shouldTryStudentGateway(target, target, false))
            assertFalse(SchoolWebPolicy.shouldTryStudentGateway(target, target, true))
            assertFalse(SchoolWebPolicy.shouldTryStudentGateway(target, "https://auth.sicau.edu.cn/cas/login", false))
            assertFalse(SchoolWebPolicy.shouldTryStudentGateway(target, "https://xsgz.sicau.edu.cn/mobile/login", false))
            assertFalse(SchoolWebPolicy.shouldTryStudentGateway(target, "https://webvpn.sicau.edu.cn/", false))
            assertFalse(SchoolWebPolicy.shouldTryStudentGateway(target, "https://xsgz.sicau.edu.cn.evil.example/", false))
        }
        assertFalse(SchoolWebPolicy.shouldTryStudentGateway("https://op.sicau.edu.cn/", hall, false))
    }

    @Test fun studentEntryIgnoresOldGatewayMappingAndRecognizesConfirmedModuleFailure() {
        val proxy="https://webvpn.sicau.edu.cn/"
        (listOf(hall)+StudentAffairsServices.entries.values.map { it.url }).forEach { target ->
            assertEquals(target, SchoolWebPolicy.initialEntry(target, proxy))
        }
        assertEquals(proxy, SchoolWebPolicy.initialEntry("https://jiaowu.sicau.edu.cn/", proxy))
        val message="Uncaught SyntaxError: Unexpected token 'export'"
        assertTrue(SchoolWebPolicy.isStudentGatewayModuleFailure(proxy, message))
        assertFalse(SchoolWebPolicy.isStudentGatewayModuleFailure(hall, message))
        assertFalse(SchoolWebPolicy.isStudentGatewayModuleFailure("https://webvpn.sicau.edu.cn.evil.example/", message))
        assertFalse(SchoolWebPolicy.isStudentGatewayModuleFailure(proxy, "network timeout"))
    }
}

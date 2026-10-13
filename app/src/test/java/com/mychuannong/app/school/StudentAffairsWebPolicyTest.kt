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
}

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
}

package com.mychuannong.app.school
import org.junit.Assert.*
import org.junit.Test

class StudentGreetingTest {
    @Test fun greetingUsesVerifiedSurnameAndKeepsUnboundFallback() {
        assertEquals("林同学",StudentGreeting.from("林晓明",true))
        assertEquals("欧阳同学",StudentGreeting.from("欧阳小雨",true))
        assertEquals("川农同学",StudentGreeting.from("林晓明",false))
        assertEquals("川农同学",StudentGreeting.from(null,true))
    }
    @Test fun profileNameComesOnlyFromLabeledAuthenticatedTable() {
        val info=SicauJiaowuProtocol.verifyIdentity("<table><tr><td>学号</td><td>synthetic01</td><td>姓名</td><td>林晓明</td></tr></table>","synthetic01")
        assertEquals("林晓明",info.name)
        assertNull(SicauJiaowuProtocol.personalInfo("<p>姓名：不能用</p><table><tr><td>学号</td><td>synthetic01</td></tr></table>").name)
        assertNull(SicauJiaowuProtocol.personalInfo("<table><tr><td>学号</td><td>synthetic01</td><td>姓名</td><td>林晓明</td></tr><tr><td>姓名</td><td>其他人</td></tr></table>").name)
    }
}

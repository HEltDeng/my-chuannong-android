package com.mychuannong.app.school

import com.google.gson.Gson
import com.mychuannong.app.core.network.campustools.SchoolPresenceIdentity
import com.mychuannong.app.core.network.campustools.StudentPresenceRequest
import org.junit.Assert.*
import org.junit.Test

class SchoolPresenceTest {
    @Test fun gradeUsesOnlyLabeledSchoolFields() {
        val info=SicauJiaowuProtocol.personalInfo("<table><tr><td>学号</td><td>20990001</td><td>姓名</td><td>测试学生</td><td>年级</td><td>大二</td><td>入学年份</td><td>2025</td></tr></table>")
        assertEquals("测试学生",info.name); assertEquals("大二",info.grade);assertEquals(2025,info.admissionYear)
        val missing=SicauJiaowuProtocol.personalInfo("<p>大四 2022级</p><table><tr><td>学号</td><td>20990001</td></tr></table>")
        assertNull(missing.grade);assertNull(missing.admissionYear)
    }
    @Test fun payloadIsAnExplicitProjectionWithoutSchoolCredentialsOrPhone() {
        val request=StudentPresenceRequest("synthetic-installation","a".repeat(64),1L,"1.0.0",14,6,"2026-10-23.3",1792713600000L,
            manufacturer="Synthetic",model="Test",osVersion="16",schoolIdentity=SchoolPresenceIdentity("20990001","测试学生","大二",2025))
        val json=Gson().toJsonTree(request).asJsonObject
        assertEquals(setOf("installationId","reportToken","reportSequence","versionName","versionCode","consentVersion","policyVersion","consentAcceptedAt","platform","manufacturer","model","osVersion","schoolIdentity"),json.keySet())
        assertEquals(setOf("studentId","realName","grade","admissionYear"),json.getAsJsonObject("schoolIdentity").keySet())
    }
}

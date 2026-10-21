package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class TimetableHtmlReaderTest {
    private fun grid(content:String):String = "<table><tr><td colspan=2>时间</td>"+
        listOf("一","二","三","四","五","六","日").joinToString("") { "<td>星期$it</td>" }+"</tr>"+
        (0..4).joinToString("") { row -> "<tr>"+(if(row in listOf(0,2))"<td rowspan=2>上下午</td>" else if(row==4)"<td>晚上</td>" else "")+
            "<td>${row*2+1}${row*2+2}节</td>"+(0..6).joinToString("") { day -> "<td>"+(if(row==0 && day==2)content else "&nbsp;")+"</td>" }+"</tr>" }+"</table>"
    @Test fun rowspanHeadersAndFontParityAreReadWithoutOcr() {
        val result=TimetableHtmlReader.read("2026-2027-1",grid("合成课程：合成教师<br>雅安校区：测试室<br>1-16周<font color=red>(双周)</font>(2学时)<br>----------------<br>&nbsp;"))
        assertEquals(1,result.drafts.size)
        val draft=result.drafts.single()
        assertEquals("合成课程",draft.name)
        assertEquals("合成教师",draft.teacher)
        val course=draft.course(result.term,"synthetic")
        assertEquals(3,course.weekday)
        assertEquals(listOf(2,4,6,8,10,12,14,16),course.weeks)
        assertFalse(draft.reviewed)
    }
    @Test fun emptyTruncatedAndWrongSemesterNeverReplaceCache() {
        assertTrue(runCatching { TimetableHtmlReader.read("2026-2027-1",grid("")) }.isFailure)
        assertTrue(runCatching { TimetableHtmlReader.read("2026-2027-1",grid("合成课程").replace("910节","不明节次")) }.isFailure)
        assertTrue(runCatching { TimetableHtmlReader.read("2026-2028-1",grid("合成课程")) }.isFailure)
    }
    private fun details(time:String="3-1,3-2(双)",week:String="1-16") = "<table><tr>"+
        listOf("课程名称","周次","教室","上课时间","教师","混合式教学").joinToString(""){"<td>$it</td>"}+
        "</tr><tr><td>合成课程</td><td>$week</td><td>测试室</td><td>$time</td><td>合成教师</td><td>线上安排请核对</td></tr></table>"
    @Test fun detailAndGridMustAgreeBeforeImport() {
        val html=grid("合成课程：合成教师<br>雅安校区：测试室<br>1-16周(双周)(2学时)").replace("</tr>","</tr><tr></tr>")+details()
        val result=TimetableHtmlReader.read("2026-2027-1",html)
        assertEquals("测试室",result.drafts.single().room)
        assertTrue(result.drafts.single().source.contains("线上安排"))
        assertEquals(8,result.drafts.single().course(result.term,"synthetic").weeks!!.size)
        assertTrue(runCatching { TimetableHtmlReader.read("2026-2027-1",html.replace("3-1,3-2(双)","3-1,3-2(单)")) }.isFailure)
        assertTrue(runCatching { TimetableHtmlReader.read("2026-2027-1",html.replace("3-1,3-2(双)","3-1,3-3(双)")) }.isFailure)
    }
    @Test fun privateLocalPageOptIn() {
        val path=System.getenv("SICAU_PRIVATE_HTML_SAMPLE") ?: return
        val html=java.io.File(path).readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val term=Regex("20\\d{2}-20\\d{2}-[12]").find(html)!!.value
        val parsed=TimetableHtmlReader.read(term,html)
        assertEquals(25,parsed.drafts.size)
        assertTrue(parsed.drafts.all { runCatching { it.course(term,"local-test") }.isSuccess })
    }
}

package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SicauTranscriptParserTest {
    private val owner = "synthetic-a"
    @Test fun printedSchoolLayoutPreservesBothColumnsAndOriginalGpaLabel() {
        val headings = "<td>序号</td><td>课程</td><td>成绩</td><td>学分</td><td>性质</td><td>备注</td>"
        val first = "<td>1</td><td>合成课程甲</td><td>缓考</td><td>03.00</td><td>必修</td><td>合成备注</td>"
        val second = "<td>2</td><td>合成课程乙</td><td>免修</td><td>01.00</td><td>任选</td><td></td>"
        val html = "<center>四川农业大学学生成绩单</center><table><tr><td>学 号</td><td>$owner</td></tr></table>" +
            "<table><tr>$headings$headings</tr><tr>$first$second</tr><tr>${first.replace(">1<", ">3<")}<td colspan=6></td></tr></table>" +
            "<p>有效学分：04.00 算术平均成绩：81.250 加权平均成绩：82.750 平均成绩绩点：3.250</p>"
        val report = SicauTranscriptParser.transcript(html, owner)
        assertEquals(listOf("1", "2", "3"), report.courses.map { it.number })
        assertEquals(listOf("缓考", "免修", "缓考"), report.courses.map { it.grade })
        assertEquals(TranscriptField("平均成绩绩点", "3.250"), report.summary.last())
        assertEquals(4, report.summary.size)
    }
    @Test fun schoolProfessionalRankingKeepsOnlyOwnAcademicFields() {
        val html = "<h2>有效必修课加权平均成绩及专业排名【实时数据】</h2><table>" +
            "<tr><td>序</td><td>校区</td><td>系列</td><td>专业</td><td>年级</td><td>学号</td><td>姓名</td><td>班级</td><td>有效必修加权成绩</td><td>专业排名</td><td>在读情况</td></tr>" +
            "<tr><td>1</td><td>合成校区</td><td>合成学院</td><td>合成专业</td><td>2099</td><td>$owner</td><td>合成姓名</td><td>合成班级</td><td>81.250</td><td>007</td><td>在读</td></tr></table>"
        val result = SicauTranscriptParser.ranking(html, owner)
        assertEquals(listOf("校区", "系列", "专业", "年级", "有效必修加权成绩", "专业排名"), result.map { it.name })
        assertEquals("007", result.last().value)
        assertFalse(result.any { it.name in setOf("总人数", "排名比例", "姓名", "学号", "班级") })
        reject { SicauTranscriptParser.ranking(html, "synthetic-other") }
    }
    private val header = "<tr><th>课程序号</th><th>课程名称</th><th>课程成绩</th><th>学分</th><th>课程性质</th><th>备注</th></tr>"
    private fun row(number: String = "SYN-1", score: String = "缓考") = "<tr><td>$number</td><td>合成测试课程</td><td>$score</td><td>02.00</td><td>必修</td><td>合成备注</td></tr>"
    private fun page(rows: String = row(), summary: String = "<p>有效学分：02.00 算术平均成绩：76.500 加权平均成绩：77.250 平均绩点：2.750</p>") =
        "<h1>个人成绩单</h1><p>学号：$owner</p><table>$header$rows</table>$summary"
    private fun reject(reason: JiaowuFailure = JiaowuFailure.BUSINESS_STRUCTURE, block: () -> Unit) {
        try { block(); fail("must reject") } catch(e: JiaowuException) { assertEquals(reason, e.reason) }
    }
    @Test fun exactStringsAndSummaryArePreserved() {
        val report = SicauTranscriptParser.transcript(page(), owner)
        val course = report.courses.single()
        assertEquals("缓考", course.grade); assertEquals("02.00", course.credit)
        assertEquals("合成备注", course.remarks); assertEquals("SYN-1", course.number)
        assertEquals(listOf("02.00", "76.500", "77.250", "2.750"), report.summary.map { it.value })
    }
    @Test fun allTextGradesAreRetainedWithoutNumericConversion() {
        val states = listOf("缺考", "缓考", "补考", "免修", "优秀", "85.00")
        val report = SicauTranscriptParser.transcript(page(states.mapIndexed { i, score -> row("SYN-$i", score) }.joinToString("")), owner)
        assertEquals(states, report.courses.map { it.grade })
    }
    @Test fun duplicateRowsAreRemovedButSameNamedDistinctCoursesRemain() {
        val report = SicauTranscriptParser.transcript(page(row() + row() + row("SYN-2")), owner)
        assertEquals(2, report.courses.size)
    }
    @Test fun summaryCanBeHorizontalHeaderAndValueRow() {
        val summary = "<table><tr><th>有效学分</th><th>算术平均成绩</th><th>加权平均成绩</th><th>平均绩点</th></tr><tr><td>02.00</td><td>76.500</td><td>77.250</td><td>2.750</td></tr></table>"
        assertEquals(4, SicauTranscriptParser.transcript(page(summary = summary), owner).summary.size)
    }
    @Test fun unrelatedSummaryIsNotCalculatedFromCourses() {
        assertTrue(SicauTranscriptParser.transcript(page(summary = ""), owner).summary.isEmpty())
    }
    @Test fun emptyRequiresExplicitSchoolStatement() {
        reject { SicauTranscriptParser.transcript(page(rows = ""), owner) }
        assertTrue(SicauTranscriptParser.transcript(page(rows = "<tr><td colspan=6>暂无成绩</td></tr>"), owner).courses.isEmpty())
    }
    @Test fun malformedRowCannotPartiallyReplaceCache() {
        reject { SicauTranscriptParser.transcript(page(rows = row() + "<tr><td>不完整</td></tr>"), owner) }
    }
    @Test fun loginAndForeignOwnerFailClosed() {
        reject(JiaowuFailure.SESSION_EXPIRED) { SicauTranscriptParser.transcript("<input type=password>", owner) }
        reject(JiaowuFailure.IDENTITY_MISMATCH) { SicauTranscriptParser.transcript(page().replace(owner, "synthetic-b"), owner) }
    }
    @Test fun sideBySidePrintedTranscriptKeepsBothGroups() {
        val doubleHeader = header.removeSuffix("</tr>") + header.removePrefix("<tr>")
        val doubleRow = row().removeSuffix("</tr>") + row("SYN-2", "免修").removePrefix("<tr>")
        assertEquals(2, SicauTranscriptParser.transcript("<h1>个人成绩单</h1><table>$doubleHeader$doubleRow</table>", owner).courses.size)
    }
    @Test fun currentUsersRankingOnlyIsSelectedFromCohortTable() {
        val html = "<h1>有效必修加权排名</h1><table><tr><th>学号</th><th>姓名</th><th>排名</th><th>总人数</th></tr><tr><td>synthetic-b</td><td>测试乙</td><td>1</td><td>20</td></tr><tr><td>$owner</td><td>测试甲</td><td>03</td><td>20</td></tr></table>"
        val fields = SicauTranscriptParser.ranking(html, owner)
        assertEquals(listOf(TranscriptField("排名", "03"), TranscriptField("总人数", "20")), fields)
        reject { SicauTranscriptParser.ranking(html, "synthetic-c") }
    }
    @Test fun personalRankingDoesNotInventPopulationOrScope() {
        val rank = SicauTranscriptParser.ranking("<h1>有效必修加权排名</h1><p>本人排名：03</p>", owner)
        assertEquals(listOf(TranscriptField("本人排名", "03")), rank)
    }
    @Test fun ambiguousRankingsAreNotAssumedToBeCurrentUser() {
        reject { SicauTranscriptParser.ranking("<h1>有效必修加权排名</h1><table><tr><th>排名</th></tr><tr><td>3</td></tr><tr><td>4</td></tr></table>", owner) }
    }
    @Test fun rankPreservesProvidedAdditionalFields() {
        val rank = SicauTranscriptParser.ranking("<h1>有效必修加权排名</h1><p>排名：03 排名比例：15.00% 统计范围：本专业 学年学期：2025-2026-2</p>", owner)
        assertEquals(listOf("03", "15.00%", "本专业", "2025-2026-2"), rank.map { it.value })
    }
}

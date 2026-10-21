package com.mychuannong.app.school
import org.junit.Assert.*
import org.junit.Test

class SicauBusinessParserTest {
 private fun course(time:String="3-3,3-4",room:String="A",weeks:String="1-16",term:String="2026-2027-1")="""<h1>四川农业大学${term}学期课程表</h1><table><tr><td>课程名称</td><td>周次</td><td>教室</td><td>上课时间</td><td>教师</td></tr><tr><td>合成课</td><td>$weeks</td><td>$room</td><td>$time</td><td>合成教师</td></tr></table>"""
 private fun failure(expected:JiaowuFailure,block:()->Unit) {try {block();fail("must reject")}catch(e:JiaowuException){assertEquals(expected,e.reason)}}
 @Test fun liveStructureProducesFifteenRecordsAndTwentyFiveSlots() {
  val html=javaClass.getResource("/sicau/courses-structure.html")!!.readText()
  val result=SicauBusinessParser.courses(html,"synthetic-a")
  assertEquals("2026-2027-1",result.term);assertEquals(15,result.records.size)
  assertEquals(25,result.courses.size)
  assertTrue(result.courses.all { it.studentId=="synthetic-a" && it.source=="SCHOOL" && it.start+it.step<=11 })
 }
 @Test fun parityBelongsToIndividualSlotAndRoom() {
  val rows=SicauBusinessParser.courses(course("2-9,2-10(单)<br>4-5,4-6","A<br>B"),"a").courses
  assertEquals(2,rows.size);assertEquals(listOf(1,3,5,7,9,11,13,15),rows[0].weeks);assertEquals("A",rows[0].classroom)
  assertEquals((1..16).toList(),rows[1].weeks);assertEquals("B",rows[1].classroom)
 }
 @Test fun onlineIsPreservedWithoutFakeSlot() {val b=SicauBusinessParser.courses(course("自行在线学习",""),"a");assertTrue(b.courses.isEmpty());assertTrue(b.records.single().unscheduled)}
 @Test fun unknownTimeDoesNotBecomeEmptySuccess() {failure(JiaowuFailure.COURSE_MAPPING){SicauBusinessParser.courses(course("未知语法"),"a")}}
 @Test fun roomMismatchRejectsWholeBatch() {failure(JiaowuFailure.COURSE_MAPPING){SicauBusinessParser.courses(course(room="A<br>B"),"a")}}
 @Test fun missingTermIsNotCurrentDateFallback() {failure(JiaowuFailure.TERM_UNCONFIRMED){SicauBusinessParser.courses(course(term=""),"a")}}
 @Test fun ambiguousTermRejected() {failure(JiaowuFailure.TERM_UNCONFIRMED){SicauBusinessParser.courses(course()+"<h1>2025-2026-2</h1>","a")}}
 @Test fun differentTermRetainsServerProvenance() {assertEquals("2025-2026-2",SicauBusinessParser.courses(course(term="2025-2026-2"),"a").courses.single().term)}
 @Test fun separatedDaysAndSectionsAreSplit() {val r=SicauBusinessParser.courses(course("1-1,1-2,1-5,2-3"),"a").courses;assertEquals(3,r.size);assertEquals(listOf(2,1,1),r.map { it.step })}
 @Test fun explicitEmptyRecognizedTable() {val html=course().replace(Regex("<tr><td>合成课.*?</tr>"),"");assertTrue(SicauBusinessParser.courses(html+"<p>暂无课程</p>","a").courses.isEmpty())}
 @Test fun unexplainedHeaderOnlyTimetableCannotClearCache() { val html=course().replace(Regex("<tr><td>合成课.*?</tr>"),"");failure(JiaowuFailure.BUSINESS_STRUCTURE){SicauBusinessParser.courses(html,"a")} }
 @Test fun missingStructureNeverClearsCache() {failure(JiaowuFailure.BUSINESS_STRUCTURE){SicauBusinessParser.courses("<h1>2026-2027-1</h1>系统维护", "a")}}
 @Test fun loginPageIsExpired() {failure(JiaowuFailure.SESSION_EXPIRED){SicauBusinessParser.courses("<input type=password>","a")}}
 @Test fun scriptRedirectIsNotBusinessData() {failure(JiaowuFailure.BUSINESS_REDIRECT){SicauBusinessParser.grades("<script>top.location='/login'</script>")}}
 @Test fun gradeUnknownsAndTextScoresStayUnknown() {
  val html="<table><tr><th>课程</th><th>成绩</th><th>学期</th><th>成绩来源</th></tr><tr><td>合成课</td><td>优秀</td><td>2026-2027-1</td><td>初修</td></tr></table>"
  val g=SicauBusinessParser.grades(html).single();assertEquals("优秀",g.grade);assertEquals("",g.point);assertEquals("",g.score);assertEquals("初修",g.property)
 }
 @Test fun headerNamesDoNotConfuseScoreWithSource() {
  val html="<table><tr><th>成绩来源</th><th>课程</th><th>绩点</th><th>成绩</th><th>学期</th></tr><tr><td>补考</td><td>合成</td><td>3.5</td><td>85</td><td>2026-2027-1</td></tr></table>"
  val g=SicauBusinessParser.grades(html).single();assertEquals("85",g.grade);assertEquals("3.5",g.point)
 }
 @Test fun truncatedRowFailsEntireParse() {failure(JiaowuFailure.BUSINESS_STRUCTURE){SicauBusinessParser.courses(course().replace("<td>合成教师</td>",""),"a")}}
 @Test fun actualExamHeaderOnlyMeansUnpublished() {assertTrue(SicauBusinessParser.exams("<table><tr><td>序号</td><td>课程</td><td>考试时间</td><td>考试教室</td><td>考核方式</td><td>座位号</td></tr></table>").isEmpty())}
 @Test fun examDateTimeAndUnknownDateAreNotFabricated() {
  fun html(time:String)="<table><tr><th>课程</th><th>考试时间</th><th>考试教室</th><th>座位号</th></tr><tr><td>合成课</td><td>$time</td><td>A</td><td>12</td></tr></table>"
  val e=SicauBusinessParser.exams(html("2026-12-20 09:00-11:00")).single();assertEquals("2026-12-20T09:00",e.examStartTimeval.toString());assertEquals("12",e.seatNumberval)
  val unknown=SicauBusinessParser.exams(html("时间待定")).single();assertNull(unknown.examStartTimeval);assertEquals("时间待定",unknown.examTime)
 }
 @Test fun weekMergeAvoidsUniqueIndexLoss() {
  val one=SicauBusinessParser.courses(course(weeks="1-4"),"a").courses.single()
  assertEquals((1..8).toList(),TimetableImportRules.merge(listOf(one,one.copy(weeks=(5..8).toList()))).single().weeks)
 }
}

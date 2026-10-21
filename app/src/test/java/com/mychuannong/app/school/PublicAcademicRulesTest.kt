package com.mychuannong.app.school
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.mychuannong.app.feature.schedule.*

class PublicAcademicRulesTest {
    private val start=LocalDate.parse("2026-09-07")
    @Test fun weeksAcrossSundayMonthYearAndBeforeTerm() {
        mapOf("2026-09-06" to 0,"2026-09-07" to 1,"2026-09-13" to 1,"2026-09-14" to 2,"2026-09-28" to 4,"2026-10-04" to 4,"2026-10-05" to 5,"2027-01-04" to 18).forEach{(date,week)->assertEquals(week,CourseScheduleEngine.teachingWeek(start,LocalDate.parse(date)))}
    }
    @Test fun singleSectionAndEveningDoubleSectionUseSamePeriodsAndFilterWeeks() {
        val periods=listOf("08:10/08:55","09:05/09:50","10:10/10:55","11:05/11:50","14:20/15:05","15:15/16:00","16:20/17:05","17:15/18:00","19:30/20:15","20:25/21:10").map{val p=it.split('/');CoursePeriod(LocalTime.parse(p[0]),LocalTime.parse(p[1]))}
        val courses=listOf(ScheduleCourse(1,"隔离测试单节","","",1,1,1,setOf(1),false),ScheduleCourse(2,"隔离测试晚课","","",1,9,2,setOf(4),false))
        val one=CourseScheduleEngine.coursesOnDate(courses,start,start,ZoneId.of("Asia/Shanghai"),periods)
        assertEquals(1,one.single().course.id);assertEquals(45L,one.single().durationMinutes)
        val four=CourseScheduleEngine.coursesOnDate(courses,start,start.plusWeeks(3),ZoneId.of("Asia/Shanghai"),periods)
        assertEquals(2,four.single().course.id);assertEquals(LocalTime.parse("21:10"),four.single().endsAt.toLocalTime())
    }
    @Test fun authenticationRedirectsAreNotBusinessBackHistory() {
        val urls=listOf("https://jiaowu.sicau.edu.cn/","https://auth.sicau.edu.cn/wui/","https://jiaowu.sicau.edu.cn/callback","https://jiaowu.sicau.edu.cn/business")
        assertEquals(0,SchoolWebPolicy.previousBusinessIndex(urls,3))
        assertNull(SchoolWebPolicy.previousBusinessIndex(listOf(urls[1],urls[3]),1))
    }
    @Test fun strictNotesRetainOverlappingPracticeAndVacation() {
        val n=SicauImageParser.parseNotes("开学上班：9月4日\n报到注册：9月4日至6日\n正式行课：9月7日\n实践周：1月18日至24日\n寒假：1月18日至2月25日",2026,1)
        assertEquals(start,n.teaching);assertEquals(n.practiceStart,n.vacationStart);assertEquals(LocalDate.parse("2027-02-25"),n.vacationEnd)
    }
    @Test(expected=IllegalArgumentException::class) fun uncertainNotesNeverBecomeVerifiedDates() {
        SicauImageParser.parseNotes("开学上班9月4日 正式行课9月8日",2026,1)
    }
}

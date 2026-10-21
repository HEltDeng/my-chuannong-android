package com.mychuannong.app.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CourseScheduleEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    // Explicit verified SICAU public fixture; the pure engine intentionally has no school default.
    private val periods = com.google.gson.JsonParser.parseString(java.io.File("src/main/assets/sicau-academic.json").readText()).asJsonObject.getAsJsonArray("periods").map { e -> val p=e.asJsonObject; CoursePeriod(java.time.LocalTime.parse(p.get("start").asString),java.time.LocalTime.parse(p.get("end").asString)) }
    private val termStart = LocalDate.of(2026, 3, 2)

    @Test fun `teaching week handles boundaries and term outside`() {
        assertEquals(0, CourseScheduleEngine.teachingWeek(termStart, termStart.minusDays(1)))
        assertEquals(1, CourseScheduleEngine.teachingWeek(termStart, termStart.plusDays(6)))
        assertEquals(2, CourseScheduleEngine.teachingWeek(termStart, termStart.plusDays(7)))
    }

    @Test fun `specific odd and even weeks are selected from persisted week list`() {
        val odd = course(id = 1, weeks = setOf(1, 3, 5))
        val even = course(id = 2, weeks = setOf(2, 4, 6))
        assertEquals(listOf(1), CourseScheduleEngine.coursesOnDate(listOf(odd, even), termStart, termStart, zone, periods).map { it.course.id })
        assertEquals(listOf(2), CourseScheduleEngine.coursesOnDate(listOf(odd, even), termStart, termStart.plusWeeks(1), zone, periods).map { it.course.id })
    }

    @Test fun `sunday uses ISO weekday seven without shifting weeks`() {
        val sunday = course(id = 7, weekday = 7, weeks = setOf(1))
        val result = CourseScheduleEngine.coursesOnDate(listOf(sunday), termStart, termStart.plusDays(6), zone, periods)
        assertEquals(7, result.single().course.id)
        assertEquals(1, result.single().week)
    }

    @Test fun `current next remaining and finished states are consistent`() {
        val courses = listOf(course(1, start = 1), course(2, start = 3))
        val during = CourseScheduleEngine.snapshot(courses, termStart, at(8, 20), periods)
        assertEquals(1, during.currentCourse?.course?.id)
        assertEquals(2, during.nextCourse?.course?.id)
        assertEquals(listOf(1, 2), during.remainingToday.map { it.course.id })
        val finished = CourseScheduleEngine.snapshot(courses, termStart, at(12, 0), periods)
        assertEquals(CourseScheduleStatus.TODAY_FINISHED, finished.status)
        assertTrue(finished.remainingToday.isEmpty())
    }

    @Test fun `overlapping courses are retained in deterministic order`() {
        val result = CourseScheduleEngine.coursesOnDate(
            listOf(course(2, start = 1), course(1, start = 1)), termStart, termStart, zone, periods
        )
        assertEquals(listOf(1, 2), result.map { it.course.id })
    }

    @Test fun `tomorrow first and cross day next course are calculated`() {
        val tomorrow = course(3, weekday = 2, start = 1)
        val snapshot = CourseScheduleEngine.snapshot(listOf(tomorrow), termStart, at(22, 0), periods)
        assertEquals(3, snapshot.tomorrowFirst?.course?.id)
        assertEquals(3, snapshot.nextCourse?.course?.id)
    }

    @Test fun `term not started ended and missing data return explicit states`() {
        assertEquals(CourseScheduleStatus.NO_LOCAL_COURSES, CourseScheduleEngine.snapshot(emptyList(), termStart, at(8, 0)).status)
        assertEquals(CourseScheduleStatus.TERM_DATE_UNKNOWN, CourseScheduleEngine.snapshot(listOf(course(1)), null, at(8, 0)).status)
        assertEquals(CourseScheduleStatus.TERM_NOT_STARTED, CourseScheduleEngine.snapshot(listOf(course(1)), termStart.plusDays(1), at(8, 0)).status)
        val endedNow = ZonedDateTime.of(termStart.plusWeeks(21), java.time.LocalTime.NOON, zone)
        assertEquals(CourseScheduleStatus.TERM_ENDED, CourseScheduleEngine.snapshot(listOf(course(1)), termStart, endedNow).status)
    }

    @Test fun `timezone input controls occurrence instant without changing local lesson time`() {
        val shanghai = CourseScheduleEngine.coursesOnDate(listOf(course(1)), termStart, termStart, zone, periods).single()
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val tokyo = CourseScheduleEngine.coursesOnDate(listOf(course(1)), termStart, termStart, tokyoZone, periods).single()
        assertEquals(8, shanghai.startsAt.hour)
        assertEquals(8, tokyo.startsAt.hour)
        assertTrue(shanghai.startsAt.toInstant() != tokyo.startsAt.toInstant())
    }

    @Test fun `reminder calculation excludes disabled courses and expired reminders`() {
        val from = at(7, 40)
        val until = at(12, 0)
        val reminders = CourseScheduleEngine.reminderOccurrences(
            listOf(course(1, start = 1), course(2, start = 3)), termStart, from, until, 15, setOf(1), periods
        )
        assertEquals(listOf(2), reminders.map { it.first.course.id })
        assertEquals(9, reminders.single().second.hour)
        assertEquals(55, reminders.single().second.minute)
    }

    private fun at(hour: Int, minute: Int) = ZonedDateTime.of(termStart, java.time.LocalTime.of(hour, minute), zone)

    private fun course(
        id: Int,
        weekday: Int = 1,
        start: Int = 1,
        weeks: Set<Int> = setOf(1, 2, 3, 4, 5, 6),
    ) = ScheduleCourse(id, "课程$id", "A$id", "教师$id", weekday, start, 2, weeks, false)
}

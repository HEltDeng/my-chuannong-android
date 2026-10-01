package com.mychuannong.app.feature.schedule

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class ScheduleCourse(
    val id: Int,
    val name: String,
    val room: String,
    val teacher: String,
    val weekday: Int,
    val startSection: Int,
    val sectionSpan: Int,
    val weeks: Set<Int>,
    val isCustom: Boolean,
)

data class CoursePeriod(val start: LocalTime, val end: LocalTime)

data class CourseOccurrence(
    val course: ScheduleCourse,
    val date: LocalDate,
    val week: Int,
    val startsAt: ZonedDateTime,
    val endsAt: ZonedDateTime,
) {
    val durationMinutes: Long get() = Duration.between(startsAt, endsAt).toMinutes()
}

enum class CourseScheduleStatus {
    READY,
    NO_LOCAL_COURSES,
    TERM_DATE_UNKNOWN,
    TERM_NOT_STARTED,
    TERM_ENDED,
    NO_COURSES_TODAY,
    TODAY_FINISHED,
}

data class CourseScheduleSnapshot(
    val status: CourseScheduleStatus,
    val teachingWeek: Int?,
    val weekday: DayOfWeek,
    val currentCourse: CourseOccurrence?,
    val nextCourse: CourseOccurrence?,
    val remainingToday: List<CourseOccurrence>,
    val tomorrowFirst: CourseOccurrence?,
    val nextBoundary: ZonedDateTime?,
)

object CourseScheduleEngine {
    val defaultPeriods: List<CoursePeriod> = emptyList()

    fun snapshot(
        courses: List<ScheduleCourse>,
        termStart: LocalDate?,
        now: ZonedDateTime,
        periods: List<CoursePeriod> = defaultPeriods,
        maxTeachingWeek: Int = 20,
    ): CourseScheduleSnapshot {
        if (courses.isEmpty()) return emptySnapshot(CourseScheduleStatus.NO_LOCAL_COURSES, now)
        if (termStart == null) return emptySnapshot(CourseScheduleStatus.TERM_DATE_UNKNOWN, now)
        val today = now.toLocalDate()
        val week = teachingWeek(termStart, today)
        if (week < 1) return emptySnapshot(CourseScheduleStatus.TERM_NOT_STARTED, now)
        if (week > maxTeachingWeek) return emptySnapshot(CourseScheduleStatus.TERM_ENDED, now, week)

        val todayCourses = coursesOnDate(courses, termStart, today, now.zone, periods, maxTeachingWeek)
        val current = todayCourses.firstOrNull { !now.isBefore(it.startsAt) && now.isBefore(it.endsAt) }
        val remaining = todayCourses.filter { now.isBefore(it.endsAt) }
        val nextToday = todayCourses.firstOrNull { now.isBefore(it.startsAt) }
        val next = nextToday ?: (1L..14L).asSequence()
            .flatMap { offset ->
                coursesOnDate(courses, termStart, today.plusDays(offset), now.zone, periods, maxTeachingWeek)
                    .asSequence()
            }
            .firstOrNull { now.isBefore(it.startsAt) }
        val tomorrowFirst = coursesOnDate(
            courses,
            termStart,
            today.plusDays(1),
            now.zone,
            periods,
            maxTeachingWeek,
        ).firstOrNull()
        val status = when {
            todayCourses.isEmpty() -> CourseScheduleStatus.NO_COURSES_TODAY
            remaining.isEmpty() -> CourseScheduleStatus.TODAY_FINISHED
            else -> CourseScheduleStatus.READY
        }
        val midnight = today.plusDays(1).atStartOfDay(now.zone)
        val nextBoundary = listOfNotNull(current?.endsAt, nextToday?.startsAt, midnight)
            .filter { now.isBefore(it) }
            .minOrNull()
        return CourseScheduleSnapshot(
            status = status,
            teachingWeek = week,
            weekday = now.dayOfWeek,
            currentCourse = current,
            nextCourse = next,
            remainingToday = remaining,
            tomorrowFirst = tomorrowFirst,
            nextBoundary = nextBoundary,
        )
    }

    fun coursesOnDate(
        courses: List<ScheduleCourse>,
        termStart: LocalDate,
        date: LocalDate,
        zoneId: ZoneId,
        periods: List<CoursePeriod> = defaultPeriods,
        maxTeachingWeek: Int = 20,
    ): List<CourseOccurrence> {
        val week = teachingWeek(termStart, date)
        if (week !in 1..maxTeachingWeek) return emptyList()
        val weekday = date.dayOfWeek.value
        return courses.asSequence()
            .filter { it.weekday == weekday }
            .filter { it.weeks.isEmpty() || week in it.weeks }
            .mapNotNull { course -> occurrence(course, date, week, zoneId, periods) }
            .sortedWith(compareBy<CourseOccurrence> { it.startsAt }.thenBy { it.endsAt }.thenBy { it.course.id })
            .toList()
    }

    fun reminderOccurrences(
        courses: List<ScheduleCourse>,
        termStart: LocalDate?,
        from: ZonedDateTime,
        until: ZonedDateTime,
        leadMinutes: Int,
        disabledCourseIds: Set<Int> = emptySet(),
        periods: List<CoursePeriod> = defaultPeriods,
        maxTeachingWeek: Int = 20,
    ): List<Pair<CourseOccurrence, ZonedDateTime>> {
        if (termStart == null || !from.isBefore(until)) return emptyList()
        val result = mutableListOf<Pair<CourseOccurrence, ZonedDateTime>>()
        var date = from.toLocalDate()
        while (!date.isAfter(until.toLocalDate())) {
            coursesOnDate(courses, termStart, date, from.zone, periods, maxTeachingWeek)
                .filterNot { it.course.id in disabledCourseIds }
                .forEach { occurrence ->
                    val reminderAt = occurrence.startsAt.minusMinutes(leadMinutes.toLong())
                    if (!reminderAt.isBefore(from) && reminderAt.isBefore(until)) {
                        result += occurrence to reminderAt
                    }
                }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.second }
    }

    fun teachingWeek(termStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(termStart, date)
        return if (days < 0) 0 else (days / 7L + 1L).toInt()
    }

    private fun occurrence(
        course: ScheduleCourse,
        date: LocalDate,
        week: Int,
        zoneId: ZoneId,
        periods: List<CoursePeriod>,
    ): CourseOccurrence? {
        if (course.startSection < 1 || course.sectionSpan < 1) return null
        val first = periods.getOrNull(course.startSection - 1) ?: return null
        val last = periods.getOrNull(course.startSection + course.sectionSpan - 2) ?: return null
        val startsAt = ZonedDateTime.of(date, first.start, zoneId)
        val endsAt = ZonedDateTime.of(date, last.end, zoneId)
        if (!startsAt.isBefore(endsAt)) return null
        return CourseOccurrence(course, date, week, startsAt, endsAt)
    }

    private fun emptySnapshot(
        status: CourseScheduleStatus,
        now: ZonedDateTime,
        week: Int? = null,
    ) = CourseScheduleSnapshot(
        status = status,
        teachingWeek = week,
        weekday = now.dayOfWeek,
        currentCourse = null,
        nextCourse = null,
        remainingToday = emptyList(),
        tomorrowFirst = null,
        nextBoundary = now.toLocalDate().plusDays(1).atStartOfDay(now.zone),
    )
}

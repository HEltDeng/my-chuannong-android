package com.mychuannong.app.feature.schedule

import android.content.Context
import com.mychuannong.app.common.cache.CommonInfo
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import com.tencent.mmkv.MMKV

object CourseScheduleRepository {
    suspend fun loadCourses(context: Context, term: String = CommonInfo.getCurrentTerm()): List<ScheduleCourse> {
        val dao = CoursesDataBase.getDatabase(context.applicationContext).courseDao()
        val all = dao.getCoursesByTerm(term)
        val studentId = StudentInfoManager.studentId
        val selected = if (studentId.isBlank()) all.filter { it.isCustom } else {
            all.filter { it.studentId == studentId || (it.studentId.isBlank() && it.isCustom) }
        }
        return selected.distinctBy {
            listOf(it.courseName, it.teacher, it.classroom, it.weekday, it.start, it.step, it.weeks).joinToString("|")
        }.map(TimeTableMySubject::toScheduleCourse)
    }

    suspend fun snapshot(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now(currentZone()),
        term: String = CommonInfo.getCurrentTerm(),
    ): CourseScheduleSnapshot = CourseScheduleEngine.snapshot(
        courses = loadCourses(context, term),
        termStart = termStart(term),
        now = now.withZoneSameInstant(currentZone()),
        periods = CourseTimePreferences.periods,
        maxTeachingWeek = CommonInfo.teachingWeeks(term),
    )

    fun termStart(term: String): LocalDate? = CommonInfo.getTermStartDate(term)
        ?.take(10)
        ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }

    fun currentZone(): ZoneId = ZoneId.of("Asia/Shanghai")

    private suspend fun migrateLegacySundayWeeks(
        dao: com.mychuannong.app.feature.common.data.local.room.dao.CourseDao,
    ) {
        val migration = MMKV.mmkvWithID("course_schedule_migrations")
        if (migration.decodeBool("sunday_week_offset_v1", false)) return
        val corrected = dao.getAllCoursesNow().filter { !it.isCustom && it.weekday == 7 && !it.weeks.isNullOrEmpty() }
            .map { course -> course.copy(weeks = course.weeks?.map { it + 1 }) }
        if (corrected.isNotEmpty()) dao.updateCourses(corrected)
        migration.encode("sunday_week_offset_v1", true)
    }
}

fun TimeTableMySubject.toScheduleCourse() = ScheduleCourse(
    id = id,
    name = courseName,
    room = classroom.orEmpty(),
    teacher = teacher,
    weekday = weekday.coerceIn(1, 7),
    startSection = start,
    sectionSpan = step,
    weeks = weeks.orEmpty().filter { it > 0 }.toSet(),
    isCustom = isCustom,
)

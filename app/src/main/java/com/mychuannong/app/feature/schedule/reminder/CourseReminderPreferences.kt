package com.mychuannong.app.feature.schedule.reminder

import com.mychuannong.app.feature.schedule.ScheduleCourse
import com.tencent.mmkv.MMKV

object CourseReminderPreferences {
    private const val STORE_ID = "course_reminder_preferences_v1"
    private val store by lazy { MMKV.mmkvWithID(STORE_ID) }

    var enabled: Boolean
        get() = store.decodeBool("enabled", false)
        set(value) { store.encode("enabled", value) }

    var leadMinutes: Int
        get() = store.decodeInt("lead_minutes", 15).takeIf { it in setOf(5, 10, 15, 20, 30) } ?: 15
        set(value) { store.encode("lead_minutes", value.coerceIn(5, 30)) }

    var showDetails: Boolean
        get() = store.decodeBool("show_details", true)
        set(value) { store.encode("show_details", value) }

    var disabledCourseKeys: Set<Int>
        get() = store.decodeString("disabled_course_keys")
            .orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        set(value) { store.encode("disabled_course_keys", value.sorted().joinToString(",")) }

    fun courseKey(course: ScheduleCourse): Int = listOf(
        course.name.trim(),
        course.weekday,
        course.startSection,
        course.sectionSpan,
        course.room.trim(),
        course.teacher.trim(),
        course.weeks.sorted().joinToString(","),
        course.isCustom,
    ).joinToString("|").hashCode()

    /** Compatibility with reminder switches saved by the first schedule reminder build. */
    fun legacyCourseKey(course: ScheduleCourse): Int =
        listOf(course.name.trim(), course.weekday, course.startSection).joinToString("|").hashCode()

    fun isDisabled(course: ScheduleCourse): Boolean =
        courseKey(course) in disabledCourseKeys || legacyCourseKey(course) in disabledCourseKeys

    fun setCourseEnabled(course: ScheduleCourse, enabled: Boolean) {
        val current = disabledCourseKeys.toMutableSet()
        current -= legacyCourseKey(course)
        if (enabled) current -= courseKey(course) else current += courseKey(course)
        disabledCourseKeys = current
    }

    var scheduledRequestCodes: Set<Int>
        get() = store.decodeString("scheduled_request_codes")
            .orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        set(value) { store.encode("scheduled_request_codes", value.sorted().joinToString(",")) }
}

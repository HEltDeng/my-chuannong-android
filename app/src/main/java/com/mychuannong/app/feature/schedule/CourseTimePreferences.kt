package com.mychuannong.app.feature.schedule

import com.tencent.mmkv.MMKV
import java.time.LocalTime

object CourseTimePreferences {
    private const val STORE_ID = "course_time_preferences_v1"
    private const val KEY_PERIODS = "periods"
    private val store by lazy { MMKV.mmkvWithID(STORE_ID) }

    val periods: List<CoursePeriod>
        get() = store.decodeString(KEY_PERIODS)
            ?.split(';')
            ?.mapNotNull { encoded ->
                val values = encoded.split(',')
                if (values.size != 2) null else runCatching {
                    CoursePeriod(LocalTime.parse(values[0]), LocalTime.parse(values[1]))
                }.getOrNull()
            }
            ?.takeIf { it.isNotEmpty() }
            ?: com.mychuannong.app.school.SicauAcademicData.periods()

    fun save(periods: List<CoursePeriod>): Boolean {
        require(periods.isNotEmpty())
        require(periods.all { it.start < it.end })
        return store.encode(KEY_PERIODS, periods.joinToString(";") { "${it.start},${it.end}" })
    }
}

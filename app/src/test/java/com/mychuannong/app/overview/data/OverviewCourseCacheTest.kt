package com.mychuannong.app.overview.data

import com.google.gson.Gson
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import org.junit.Assert.*
import org.junit.Test

class OverviewCourseCacheTest {
    private val term = "2026-2027-1"
    private val row = TimeTableMySubject(courseName = "测试课程", studentId = "test-owner",
        term = term, weekday = 3, start = 2, step = 2, weeks = listOf(1, 3, 5))
    private fun json(rows: List<TimeTableMySubject>) = Gson().toJson(rows)

    @Test fun retainsFullScheduleForRecomputingTheCurrentDay() {
        val restored = OverviewCourseCache.decode(json(listOf(row)), "test-owner", term)!!.single()
        assertEquals(row, restored)
        assertEquals(listOf(1, 3, 5), restored.weeks)
    }

    @Test fun rejectsOtherAccountsTermsAndUnownedSchoolRows() {
        assertNull(OverviewCourseCache.decode(json(listOf(row)), "other", term))
        assertNull(OverviewCourseCache.decode(json(listOf(row)), "test-owner", "2025-2026-2"))
        assertNull(OverviewCourseCache.decode(json(listOf(row)), "", term))
        assertNull(OverviewCourseCache.decode(json(listOf(row.copy(studentId = ""))), "test-owner", term))
    }

    @Test fun handlesEmptyCustomAndCorruptSnapshots() {
        assertEquals(emptyList<TimeTableMySubject>(), OverviewCourseCache.decode("[]", "test-owner", term))
        assertEquals(1, OverviewCourseCache.decode(json(listOf(row.copy(studentId = "", isCustom = true))), "test-owner", term)!!.size)
        assertNull(OverviewCourseCache.decode("broken", "test-owner", term))
        assertNull(OverviewCourseCache.decode("null", "test-owner", term))
    }
}

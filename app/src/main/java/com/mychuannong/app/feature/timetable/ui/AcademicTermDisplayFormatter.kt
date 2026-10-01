package com.mychuannong.app.feature.timetable.ui

/**
 * Converts the raw academic term used by the education system into a concise grade label.
 *
 * This formatter is intentionally presentation-only: callers must continue to use [rawTerm]
 * for state, cache keys and requests. When either value cannot be parsed safely, the raw value
 * is returned unchanged.
 */
object AcademicTermDisplayFormatter {
    private val termPattern = Regex("^(\\d{4})-(\\d{4})-([12])$")
    private val gradeNames = listOf("大一", "大二", "大三", "大四")

    fun format(rawTerm: String, studentId: String): String {
        val admissionYear = studentId.take(4).toIntOrNull() ?: return rawTerm
        val match = termPattern.matchEntire(rawTerm) ?: return rawTerm
        val termStartYear = match.groupValues[1].toIntOrNull() ?: return rawTerm
        val termEndYear = match.groupValues[2].toIntOrNull() ?: return rawTerm
        if (termEndYear != termStartYear + 1) return rawTerm

        val gradeIndex = termStartYear - admissionYear
        val gradeName = gradeNames.getOrNull(gradeIndex) ?: return rawTerm
        val semesterName = when (match.groupValues[3]) {
            "1" -> "上"
            "2" -> "下"
            else -> return rawTerm
        }
        return gradeName + semesterName
    }
}

package com.mychuannong.app.feature.timetable.ui

/** Inherited V1.0.5 grade mapping. Display only: raw IDs remain in queries, selection and cache. */
object AcademicTermDisplayFormatter {
    private val termPattern = Regex("^(\\d{4})-(\\d{4})-([12])$")
    private val gradeNames = listOf("大一", "大二", "大三", "大四")
    fun format(rawTerm: String, admissionYear: Int?): String {
        val match = termPattern.matchEntire(rawTerm) ?: return rawTerm
        val start = match.groupValues[1].toInt()
        val end = match.groupValues[2].toInt()
        if (end != start + 1) return rawTerm
        val semester = if (match.groupValues[3] == "1") "上" else "下"
        val grade = admissionYear?.let { gradeNames.getOrNull(start - it) }
        return if (grade != null) grade + semester else "$start-${end}学年·${semester}学期"
    }
}

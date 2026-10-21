package com.mychuannong.app.school

import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject

/** Pure, deliberately strict rules: an OCR guess must never become a scheduled reminder. */
object TimetableImportRules {
    fun term(value: String): Boolean {
        val m = Regex("(20\\d{2})-(20\\d{2})-([12])").matchEntire(value) ?: return false
        return m.groupValues[2].toInt() == m.groupValues[1].toInt() + 1
    }

    fun weeks(value: String): List<Int> {
        val text = value.replace(Regex("\\s+"), "").replace('，', ',').replace('、', ',')
            .replace('－', '-').replace('—', '-').replace('～', '-').replace('~', '-')
            .replace('（', '(').replace('）', ')')
        val m = Regex("([0-9,\\-]+)(?:周)?(?:\\((单|双)(?:周)?\\))?").matchEntire(text)
            ?: throw IllegalArgumentException("周次请填写 1-16、1-16(单周) 或 1,3,5")
        val result = m.groupValues[1].split(',').flatMap { part ->
            val bounds = part.split('-').map { it.toIntOrNull() ?: error("周次格式不完整") }
            require(bounds.size in 1..2 && bounds.all { it in 1..30 }) { "周次应在 1 至 30 之间" }
            require(bounds.first() <= bounds.last()) { "起止周次顺序不正确" }
            (bounds.first()..bounds.last()).toList()
        }.filter { when (m.groupValues[2]) { "单" -> it % 2 == 1; "双" -> it % 2 == 0; else -> true } }
        require(result.isNotEmpty()) { "该周次条件没有课程" }
        return result.distinct().sorted()
    }

    data class Draft(val name: String, val teacher: String, val room: String,
        val day: String, val start: String, val end: String, val weeks: String,
        val source: String, val reviewed: Boolean = false) {
        fun course(term: String, account: String): TimeTableMySubject {
            require(TimetableImportRules.term(term)) { "请核对学期，例如 2026-2027-1" }
            require(name.isNotBlank() && name.length <= 160) { "请核对课程名称" }
            val dayNumber = day.toIntOrNull() ?: error("请核对星期")
            val first = start.toIntOrNull() ?: error("请核对开始节次")
            val last = end.toIntOrNull() ?: error("请核对结束节次")
            require(dayNumber in 1..7 && first in 1..10 && last in first..10) { "星期为 1 至 7，节次为 1 至 10" }
            return TimeTableMySubject(courseName = name.trim(), teacher = teacher.trim(), classroom = room.trim(),
                weekday = dayNumber, start = first, step = last - first + 1,
                weeks = TimetableImportRules.weeks(weeks), term = term, studentId = account)
        }
    }

    /** The Room unique key excludes weeks, so union them before its IGNORE insert policy. */
    fun merge(courses: List<TimeTableMySubject>): List<TimeTableMySubject> = courses.groupBy {
        listOf(it.courseName, it.teacher, it.classroom.orEmpty(), it.weekday.toString(),
            it.start.toString(), it.step.toString(), it.term, it.studentId)
    }.values.map { group -> group.first().copy(id = 0, weeks = group.flatMap { it.weeks.orEmpty() }.distinct().sorted()) }

    fun cell(raw: String, day: Int, start: Int): List<Draft> {
        val lines = raw.lines().map { it.trim().trim('|').replace('：', ':') }.filter { it.isNotBlank() }
        val groups = mutableListOf<MutableList<String>>()
        lines.forEach { line ->
            val title = line.contains(':') && !line.contains("校区") && !line.contains("室") && !line.contains("周")
            if (groups.isEmpty() || title && groups.last().any { it.contains("周") }) groups.add(mutableListOf())
            groups.last().add(line)
        }
        return groups.filter { it.any { line -> line.any(Char::isLetterOrDigit) } }.map { group ->
            val title = group.first()
            val weekLine = group.firstOrNull { it.contains("周") }.orEmpty().replace(" ", "")
            // Require a complete token. Never turn 114 into 1-14 or infer an absent parity marker.
            val weekToken = Regex("^([0-9]+(?:[-－~～][0-9]+)?(?:[,，、][0-9]+(?:-[0-9]+)?)*)周(?:[（(]([单双])周[）)])?").find(weekLine.trimStart('|'))
            val weekText = weekToken?.let { it.groupValues[1] + if (it.groupValues[2].isNotEmpty()) "(${it.groupValues[2]}周)" else "" }.orEmpty()
            val unclearParity = weekLine.contains(Regex("[（(][^）)]*周[）)]")) && !weekLine.contains(Regex("[（(][单双]周[）)]"))
            val validWeeks = if (!unclearParity && runCatching { weeks(weekText) }.isSuccess) weekText else ""
            Draft(title.substringBefore(':'), if (title.contains(':')) title.substringAfter(':') else "",
                group.firstOrNull { it.contains("校区") || it.contains("室") }.orEmpty(),
                day.toString(), start.toString(), (start + 1).toString(), validWeeks, group.joinToString("\n"))
        }
    }
}

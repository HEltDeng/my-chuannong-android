package com.mychuannong.app.school

import androidx.annotation.Keep
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Keep data class TranscriptCourse(
    val name: String, val grade: String, val credit: String,
    val nature: String, val remarks: String, val number: String, val term: String = ""
)
@Keep data class TranscriptField(val name: String, val value: String)
@Keep data class SchoolTranscript(
    val courses: List<TranscriptCourse>, val summary: List<TranscriptField>,
    val ranking: List<TranscriptField> = emptyList(),
    val rankingPhase: SyncPhase = SyncPhase.NOT_SYNCED,
    val rankingMessage: String = "尚未读取有效必修加权排名",
    val updatedAt: Long = 0L
)

/** Structured HTML only. No inferred GPA, score conversion, names, photos or identity fields in output. */
internal object SicauTranscriptParser {
    val summaryLabels = listOf("有效学分", "算术平均成绩", "加权平均成绩", "平均绩点", "平均成绩绩点")
    private val rankNames = setOf("排名", "本人排名", "名次", "有效必修加权排名", "专业排名")
    private val rankLabels = rankNames + setOf("排名总人数", "总人数", "参评人数", "排名比例", "排名百分比", "统计范围", "排名范围", "学年学期", "学年", "学期", "有效必修加权成绩", "校区", "系列", "专业", "年级")
    fun isTranscriptPage(doc: Document): Boolean = doc.text().let { it.contains("个人成绩单") || it.contains("四川农业大学学生成绩单") }
    private fun isRankingPage(doc: Document): Boolean = doc.text().let { it.contains("有效必修加权排名") || it.contains("有效必修课加权平均成绩及专业排名") }
    private val names = setOf("课程名称", "课程名", "课程")
    private val grades = setOf("课程成绩", "成绩", "总评成绩", "最终成绩")
    private val credits = setOf("课程学分", "学分")
    private fun fail(): Nothing = throw JiaowuException(JiaowuFailure.BUSINESS_STRUCTURE)
    private fun key(s: String) = s.replace(Regex("[\\s\u00a0：:]"), "")
    private fun value(e: Element): String = e.wholeText().replace('\u00a0', ' ').trim()
    fun document(html: String): Document {
        val doc = Jsoup.parse(html)
        if(doc.select("input[type=password],input[name=hour_key]").isNotEmpty() ||
            Regex("登录超时|账号验证失败|请重新登录|请先登录|登录已失效").containsMatchIn(doc.text()))
            throw JiaowuException(JiaowuFailure.SESSION_EXPIRED)
        return doc
    }
    private fun ownIdentity(doc: Document, account: String) {
        require(account.isNotBlank())
        val ids = mutableSetOf<String>()
        doc.select("tr").forEach { row ->
            val cells = row.children().filter { it.tagName() in setOf("td", "th") }
            cells.forEachIndexed { i, cell ->
                if(key(cell.text()) in setOf("学号", "学生学号") && i + 1 < cells.size &&
                    cells[i + 1].tagName() != "th") {
                    val candidate = cells[i + 1].text().trim()
                    if(candidate.matches(Regex("[0-9A-Za-z_-]{4,32}"))) ids += candidate
                }
            }
        }
        Regex("(?:学生学号|学号)\\s*[：:]\\s*([0-9A-Za-z_-]{4,32})(?![0-9A-Za-z_-])")
            .findAll(doc.text()).forEach { ids += it.groupValues[1] }
        if(ids.any { it != account }) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
    }
    /** Read named key/value cells or explicit labelled text, never arbitrary numerical guesses. */
    private fun fields(doc: Element, labels: Set<String>): List<TranscriptField> {
        val found = linkedMapOf<String, String>()
        fun add(label: String, raw: String) {
            val v = raw.trim()
            if(v.isBlank() || key(v) in labels) return
            if(found[label]?.let { it != v } == true) fail()
            found[label] = v
        }
        doc.select("table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") == table }
            val header = rows.indexOfFirst { row ->
                val h = row.children().map { key(it.text()) }
                h.isNotEmpty() && h.all { it in labels }
            }
            if(header >= 0) {
                val h = rows[header].children().map { key(it.text()) }
                val data = rows.drop(header + 1).filter { it.text().isNotBlank() }
                if(data.size != 1 || data.single().childrenSize() != h.size) fail()
                h.indices.forEach { add(h[it], value(data.single().child(it))) }
            }
        }
        doc.select("tr").forEach { row ->
            val cells = row.children().filter { it.tagName() in setOf("td", "th") }
            cells.forEachIndexed { i, e ->
                val label = key(e.text())
                if(label in labels && i + 1 < cells.size) add(label, value(cells[i + 1]))
            }
        }
        // Restrict text parsing to leaf-like blocks, avoiding navigation and whole-page identity text.
        val alternatives = labels.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val regex = Regex("($alternatives)\\s*[：:]\\s*(.*?)(?=\\s*(?:$alternatives)\\s*[：:]|$)")
        doc.select("td,th,p,div,span,font").filter { it.children().none { child -> child.tagName() in setOf("table", "div", "p") } }.forEach { block ->
            regex.findAll(block.text()).forEach { m -> add(m.groupValues[1], m.groupValues[2].trim().trimEnd('；', ';')) }
        }
        return found.map { TranscriptField(it.key, it.value) }
    }
    fun transcript(html: String, account: String): SchoolTranscript {
        val doc = document(html)
        ownIdentity(doc, account)
        if(!isTranscriptPage(doc)) fail()
        val summary = fields(doc, summaryLabels.toSet())
        val result = mutableListOf<TranscriptCourse>()
        var recognized = false
        doc.select("table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") == table }
            val head = rows.indexOfFirst { row ->
                val h = row.children().map { key(it.text()) }
                h.any { it in names } && h.any { it in grades } && h.any { it in credits }
            }
            if(head < 0) return@forEach
            recognized = true
            val headers = rows[head].children().map { key(it.text()) }
            // Printed transcripts may place two independent course groups side by side.
            val starts = headers.indices.filter { headers[it] in names }
            val boundaries = listOf(0) + starts.drop(1).map { start ->
                if(headers[start - 1] in setOf("课程序号", "课程编号", "课程号", "序号")) start - 1 else start
            } + headers.size
            rows.drop(head + 1).forEach rowLoop@ { row ->
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }.flatMap { cell ->
                    val span = cell.attr("colspan").toIntOrNull() ?: 1
                    if(span > 1 && cell.text().isBlank() && span <= headers.size) List(span) { cell } else listOf(cell)
                }
                val text = row.text()
                if(text.isBlank() || cells.map { key(it.text()) } == headers) return@rowLoop
                if(summaryLabels.any { text.contains(it) } || Regex("暂无成绩|没有成绩|无成绩记录").containsMatchIn(text)) return@rowLoop
                if(cells.size != headers.size || cells.any { it.attr("rowspan").toIntOrNull()?.let { n -> n > 1 } == true }) fail()
                for(group in 0 until boundaries.lastIndex) {
                    val indices = boundaries[group] until boundaries[group + 1]
                    fun read(keys: Set<String>): String = indices.firstOrNull { headers[it] in keys }?.let { value(cells[it]) }.orEmpty()
                    if(read(setOf("有效", "是否有效")) in setOf("否", "无效")) continue
                    val name = read(names); val grade = read(grades)
                    if(indices.all { value(cells[it]).isBlank() }) continue
                    if(name.isBlank() || grade.isBlank()) fail()
                    result += TranscriptCourse(name, grade, read(credits), read(setOf("课程性质", "性质")),
                        read(setOf("备注信息", "备注", "成绩说明")), read(setOf("课程序号", "课程编号", "课程号", "序号")), read(setOf("学年学期", "学期")))
                }
            }
        }
        if(!recognized || result.isEmpty() && !Regex("暂无成绩|没有成绩|无成绩记录").containsMatchIn(doc.text())) fail()
        return SchoolTranscript(result.distinct(), summaryLabels.mapNotNull { label -> summary.find { it.name == label } })
    }
    fun ranking(html: String, account: String): List<TranscriptField> {
        val doc = document(html)
        if(!isRankingPage(doc)) fail()
        // A cohort table is accepted only by its exact current-user ID row; other rows are discarded.
        val matching = mutableListOf<List<TranscriptField>>()
        var hasCohortTable = false
        doc.select("table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") == table }
            val head = rows.indexOfFirst { row ->
                val h = row.children().map { key(it.text()) }
                h.any { it in setOf("学号", "学生学号") } && h.any { it in rankNames }
            }
            if(head < 0) return@forEach
            hasCohortTable = true
            val h = rows[head].children().map { key(it.text()) }
            val idIndex = h.indexOfFirst { it in setOf("学号", "学生学号") }
            rows.drop(head + 1).forEach { row ->
                val cells = row.children()
                if(cells.getOrNull(idIndex)?.text()?.trim() == account) {
                    if(cells.size != h.size) fail()
                    matching += h.indices.filter { h[it] in rankLabels && value(cells[it]).isNotBlank() }.map { TranscriptField(h[it], value(cells[it])) }
                }
            }
        }
        if(hasCohortTable) return matching.singleOrNull()?.takeIf { list -> list.any { it.name in rankNames } } ?: fail()
        ownIdentity(doc, account)
        val result = fields(doc, rankLabels)
        if(result.none { it.name in rankNames }) fail()
        return result
    }
}

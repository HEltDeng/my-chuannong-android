package com.mychuannong.app.school

import androidx.annotation.Keep
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalTime

/** Field contracts verified through the official WebVPN on 2026-10-08.
 * Independent Kotlin implementation; see THIRD_PARTY_NOTICES.md for protocol references. */
@Keep data class SchoolCourseRecord(val fields: Map<String,String>, val unscheduled: Boolean)
internal object SicauBusinessParser {
    private fun fail(reason:JiaowuFailure):Nothing = throw JiaowuException(reason)
    private fun label(s:String)=s.replace(Regex("[\\s\u00a0：:]"),"")
    internal fun document(html:String):org.jsoup.nodes.Document {
        val doc=Jsoup.parse(html)
        if(doc.select("input[type=password],input[name=hour_key]").isNotEmpty() ||
            Regex("登录超时|账号验证失败|重新登录|请先登录").containsMatchIn(doc.text())) fail(JiaowuFailure.SESSION_EXPIRED)
        if(doc.select("meta[http-equiv=refresh]").isNotEmpty() ||
            Regex("(?:top\\.|window\\.)?location(?:\\.href)?\\s*=").containsMatchIn(doc.select("script").html()))
            fail(JiaowuFailure.BUSINESS_REDIRECT)
        return doc
    }
    private fun cell(e:Element):String {
        val copy=e.clone();copy.select("br").forEach { it.after(org.jsoup.nodes.TextNode("\n")) }
        return copy.wholeText().replace('\u00a0',' ').lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
    }
    private class Table(val headers:List<String>,val rows:List<List<String>>) {
        fun has(vararg names:String)=headers.any { it in names }
        fun value(row:List<String>,vararg names:String)=headers.indexOfFirst { it in names }.let { row.getOrNull(it).orEmpty() }
    }
    private fun table(doc:org.jsoup.nodes.Document,required:List<Set<String>>):Table {
        val candidates=doc.select("table").mapNotNull { t ->
            val rows=t.select("tr").filter { it.closest("table")==t }
            val index=rows.indexOfFirst { r -> val h=r.children().map { label(it.text()) }; required.all { keys -> h.any { it in keys } } }
            if(index<0) null else {
                val h=rows[index].children().map { label(it.text()) }
                if(h.size!=h.distinct().size) fail(JiaowuFailure.BUSINESS_STRUCTURE)
                val data=rows.drop(index+1).map { r -> r.children().filter { it.tagName() in setOf("td","th") }.map(::cell) }
                    .filter { it.any(String::isNotBlank) }
                    .filterNot { it.firstOrNull()?.trim() in setOf("合计","总计") || it.map(::label)==h }
                if(data.any { it.size!=h.size }) fail(JiaowuFailure.BUSINESS_STRUCTURE)
                Table(h,data)
            }
        }
        return candidates.singleOrNull() ?: fail(JiaowuFailure.BUSINESS_STRUCTURE)
    }
    fun term(html:String):String {
        val text=document(html).text()
        val terms=Regex("(20\\d{2})\\s*[-—－]\\s*(20\\d{2})\\s*(?:-\\s*([12])|学年\\s*(上|下|第一|第二)学期)")
            .findAll(text).map { m -> "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3].ifEmpty { if(m.groupValues[4] in setOf("上","第一")) "1" else "2" }}" }.toSet()
        return terms.singleOrNull()?.takeIf(TimetableImportRules::term) ?: fail(JiaowuFailure.TERM_UNCONFIRMED)
    }
    fun courses(html:String,account:String):CourseBatch {
        val doc=document(html);val term=term(html)
        val t=table(doc,listOf(setOf("课程名称","课程名","课程"),setOf("周次","上课周次"),setOf("上课时间"),setOf("教室","上课教室")))
        // A header-only timetable has not been observed as a genuine empty result.
        // Require school-provided empty wording before replacing prior school courses with zero.
        if(t.rows.isEmpty() && !Regex("暂无课程|没有课程|无选课记录|未选课程").containsMatchIn(doc.text())) fail(JiaowuFailure.BUSINESS_STRUCTURE)
        val records=mutableListOf<SchoolCourseRecord>()
        val mapped=t.rows.flatMap { row ->
            fun v(vararg keys:String)=t.value(row,*keys)
            val name=v("课程名称","课程名","课程")
            if(name.isBlank()) fail(JiaowuFailure.COURSE_MAPPING)
            val raw=v("上课时间");val online=raw in setOf("自行在线学习","在线学习","网络学习","不定时","时间待定")
            records+=SchoolCourseRecord(t.headers.zip(row).toMap(),online)
            if(online) emptyList() else {
                val times=raw.lines().filter(String::isNotBlank)
                val rooms=v("教室","上课教室").lines().filter(String::isNotBlank)
                val weeks=v("周次","上课周次").lines().filter(String::isNotBlank)
                if(times.isEmpty() || rooms.size !in setOf(0,1,times.size) || weeks.size !in setOf(1,times.size)) fail(JiaowuFailure.COURSE_MAPPING)
                times.flatMapIndexed { i,time ->
                    val normalized=time.replace(Regex("\\s+"),"").replace('（','(').replace('）',')')
                    val parity=Regex("\\((单|双)(?:周)?\\)$").find(normalized)
                    val points=normalized.removeSuffix(parity?.value.orEmpty()).split(',','，').map { token ->
                        val m=Regex("([1-7])-(10|[1-9])").matchEntire(token) ?: fail(JiaowuFailure.COURSE_MAPPING)
                        m.groupValues[1].toInt() to m.groupValues[2].toInt()
                    }
                    val base=try { TimetableImportRules.weeks(if(weeks.size==1) weeks[0] else weeks[i]) } catch(_:IllegalArgumentException){fail(JiaowuFailure.COURSE_MAPPING)}
                    val selected=base.filter { when(parity?.groupValues?.get(1)){"单"->it%2==1;"双"->it%2==0;else->true} }
                    if(selected.isEmpty()) fail(JiaowuFailure.COURSE_MAPPING)
                    points.groupBy({it.first},{it.second}).flatMap { (day,sections) ->
                        val groups=mutableListOf<MutableList<Int>>()
                        sections.distinct().sorted().forEach { n -> if(groups.isEmpty() || groups.last().last()+1!=n) groups.add(mutableListOf(n)) else groups.last().add(n) }
                        groups.map { s -> TimeTableMySubject(courseName=name,teacher=v("教师","任课教师"),classroom=if(rooms.size<=1)rooms.firstOrNull().orEmpty() else rooms[i],
                            weekday=day,start=s.first(),step=s.size,weeks=selected,term=term,studentId=account,source="SCHOOL") }
                    }
                }
            }
        }
        return CourseBatch(term,TimetableImportRules.merge(mapped),records)
    }
    fun grades(html:String):List<Grade> {
        val t=table(document(html),listOf(setOf("课程名称","课程名","课程"),setOf("成绩","总评成绩","最终成绩"),setOf("学期","学年学期")))
        return t.rows.map { row ->
            fun v(vararg keys:String)=t.value(row,*keys)
            val name=v("课程名称","课程名","课程");val score=v("成绩","总评成绩","最终成绩")
            if(name.isBlank() || score.isBlank()) fail(JiaowuFailure.BUSINESS_STRUCTURE)
            Grade(id=v("课程编号","编号"),item=v("学年学期","学期"),name=name,grade=score,flag=v("有效"),score=v("学分"),timeR=v("学时"),point=v("绩点"),
                upperReItem="",method=v("考核方式","考核方法"),property=v("成绩来源"),attribute=v("课程性质"),reItem=v("成绩说明"))
        }
    }
    fun exams(html:String):List<ExamArrange> {
        val t=table(document(html),listOf(setOf("课程名称","课程名","课程"),setOf("考试时间"),setOf("考试教室","考试地点","考场")))
        return t.rows.map { row ->
            fun v(vararg keys:String)=t.value(row,*keys)
            val name=v("课程名称","课程名","课程");val raw=v("考试时间")
            if(name.isBlank() || raw.isBlank()) fail(JiaowuFailure.BUSINESS_STRUCTURE)
            val date=Regex("(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})日?").find(raw)?.let { m ->
                runCatching { LocalDate.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt()) }.getOrNull() }
            val times=Regex("(?<!\\d)(\\d{1,2})[:：](\\d{2})(?!\\d)").findAll(raw).mapNotNull { m ->
                runCatching { LocalTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt()) }.getOrNull() }.toList()
            val start=if(times.size==2 && times[1]>times[0])date?.atTime(times[0]) else null
            val end=if(start!=null)date?.atTime(times[1]) else null
            ExamArrange(v("校区"),"",v("课程编号","编号"),name,v("教师"),raw,start,end,v("考试教室","考试地点","考场"),v("座位号","座号"),"",listOf(v("备注"),v("考核方式")).filter(String::isNotBlank).joinToString("；"))
        }
    }
}

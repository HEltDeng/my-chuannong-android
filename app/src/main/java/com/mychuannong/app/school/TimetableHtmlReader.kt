package com.mychuannong.app.school

import org.jsoup.Jsoup

/** Reads the semester grid observed in the official teaching system, including rowspan headers. */
object TimetableHtmlReader {
    fun read(term:String,html:String):TimetableImageReader.Result {
        require(TimetableImportRules.term(term)) { "未找到课表学期，请先进入选课情况（课表）" }
        require(html.length<=500_000) { "课表内容过大，请重新打开课表页面" }
        val document=Jsoup.parse(html)
        val details=document.select("table").filter { table ->
            val first=table.select("tr").firstOrNull { it.closest("table")==table }
            first!=null && listOf("课程名称","周次","教室","上课时间","教师").all { first.children().any { cell -> cell.text().trim()==it } }
        }
        require(details.size<=1) { "课程明细不唯一，请重新打开当前学期课表" }
        val detailed=details.singleOrNull()?.let { readDetails(it) }
        val tables=document.select("table").filter { table ->
            val first=table.select("tr").firstOrNull { it.closest("table")==table }
            first!=null && listOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日").all { first.children().any { cell -> cell.text().trim()==it } }
        }
        require(tables.size==1) { "未找到唯一的学期课表，请先进入选课情况（课表）" }
        val table=tables.single()
        val rows=table.select("tr").filter { it.closest("table")==table }.drop(1).filter { row -> row.children().any { it.tagName() in setOf("td","th") } }
        require(rows.size==5) { "课表节次结构发生变化，请使用图片导入并核对" }
        val drafts=rows.flatMapIndexed { rowIndex,row ->
            val cells=row.children().filter { it.tagName() in setOf("td","th") }
            require(cells.size in 8..9) { "课表列不完整，请重新打开完整课表" }
            val label=cells.dropLast(7).joinToString("") { it.text() }.replace(Regex("\\s+"),"")
            val first=rowIndex*2+1
            require(label.contains("$first${first+1}节") || label.contains("$first-${first+1}节")) { "节次无法确认，请使用图片导入并核对" }
            cells.takeLast(7).flatMapIndexed { day,cell ->
                val copy=cell.clone()
                copy.select("br").forEach { it.after(org.jsoup.nodes.TextNode("\n")) }
                val raw=copy.wholeText().replace('\u00a0',' ').lines().filter { line -> line.any(Char::isLetterOrDigit) }.joinToString("\n")
                TimetableImportRules.cell(raw,day+1,first)
            }
        }
        require(drafts.isNotEmpty()) { "此学期没有可读取的课程，原有课表已保留" }
        if(detailed!=null) {
            fun slots(items:List<TimetableImportRules.Draft>) = items.flatMap { draft ->
                val course=draft.course(term,"local-validation")
                course.weeks.orEmpty().flatMap { week -> (course.start until course.start+course.step).map { section ->
                    listOf(course.courseName.replace(Regex("\\s+"),""),course.weekday.toString(),section.toString(),week.toString())
                } }
            }.toSet()
            require(slots(detailed)==slots(drafts)) { "课程明细与上方课表不一致，请核对学校课表后重试；原有数据已保留" }
        }
        return TimetableImageReader.Result(term,detailed ?: drafts)
    }

    private fun lines(cell:org.jsoup.nodes.Element):List<String> {
        val copy=cell.clone()
        copy.select("br").forEach { it.after(org.jsoup.nodes.TextNode("\n")) }
        return copy.wholeText().replace('\u00a0',' ').lines().map(String::trim).filter(String::isNotBlank)
    }
    private fun readDetails(table:org.jsoup.nodes.Element):List<TimetableImportRules.Draft> {
        val rows=table.select("tr").filter { it.closest("table")==table }
        val header=rows.first().children().map { it.text().trim() }
        fun index(label:String)=header.indexOf(label).also { require(it>=0) { "课程明细栏目不完整" } }
        val name=index("课程名称"); val weeks=index("周次"); val room=index("教室")
        val time=index("上课时间"); val teacher=index("教师")
        val notes=header.indexOf("混合式教学")
        return rows.drop(1).filter { it.children().isNotEmpty() }.flatMap { row ->
            val cells=row.children()
            require(cells.size==header.size) { "课程明细行不完整，请重新打开页面" }
            val times=lines(cells[time]); val rooms=lines(cells[room])
            require(times.isNotEmpty() && (rooms.size==times.size || rooms.size==1)) { "教室与上课时间无法对应，请核对学校课表" }
            val week=lines(cells[weeks]).joinToString("")
            val courseName=cells[name].text().trim(); val instructor=cells[teacher].text().trim()
            val note=if(notes>=0) cells[notes].text().trim() else ""
            times.mapIndexed { i,raw ->
                val normalized=raw.replace(Regex("\\s+"),"").replace('（','(').replace('）',')')
                val parity=Regex("\\((单|双)(?:周)?\\)$").find(normalized)
                val points=normalized.removeSuffix(parity?.value.orEmpty()).split(',','，').map { token ->
                    val m=Regex("([1-7])-([1-9]|10)").matchEntire(token)
                    require(m!=null) { "上课时间格式发生变化，请核对学校课表" }
                    m.groupValues[1].toInt() to m.groupValues[2].toInt()
                }
                val day=points.first().first; val sections=points.map { it.second }.distinct().sorted()
                require(points.all { it.first==day } && sections==(sections.first()..sections.last()).toList()) { "课程节次不连续，请核对学校课表" }
                val weekText=week+(parity?.let { "(${it.groupValues[1]}周)" } ?: "")
                TimetableImportRules.weeks(weekText)
                val location=if(rooms.size==1)rooms.single() else rooms[i]
                TimetableImportRules.Draft(courseName,instructor,location,day.toString(),sections.first().toString(),sections.last().toString(),weekText,
                    listOf(courseName,instructor,location,"$weekText 周 · $raw",note).filter(String::isNotBlank).joinToString("\n"))
            }
        }.also { require(it.isNotEmpty()) { "此学期没有可读取的课程，原有课表已保留" } }
    }

    // Read-only and exact-origin constrained, including same-origin nested teaching frames.
    // Returns only timetable markup and its semester; never forms, cookies, storage or full HTML.
    val extractionScript = """
        (() => {
          const found=[]; let seen=0;
          function scan(w, depth) {
            if(depth>6 || ++seen>60) return;
            try {
              if(w.location.protocol!=='https:' || w.location.hostname!=='jiaowu.sicau.edu.cn') return;
              const d=w.document;
              for(const t of d.querySelectorAll('table')) {
                const row=t.rows[0];
                if(!row || !['星期一','星期二','星期三','星期四','星期五','星期六','星期日'].every(x=>Array.from(row.cells).some(c=>c.innerText.trim()===x))) continue;
                const term=(d.body.innerText.match(/四川农业大学\s*(20\d{2}-20\d{2}-[12])\s*学期/)||[])[1];
                const detail=Array.from(d.querySelectorAll('table')).filter(x=>x!==t && x.rows[0] && ['课程名称','周次','教室','上课时间','教师'].every(k=>Array.from(x.rows[0].cells).some(c=>c.innerText.trim()===k)));
                if(detail.length>1) continue;
                const html=t.outerHTML+detail.map(x=>x.outerHTML).join('');
                if(term && html.length<200000) found.push({term,html});
              }
              for(let i=0;i<w.frames.length;i++) scan(w.frames[i],depth+1);
            } catch(_) {}
          }
          scan(window,0);
          return JSON.stringify(found.length===1 ? found[0] : {error:true});
        })()
    """.trimIndent()
}

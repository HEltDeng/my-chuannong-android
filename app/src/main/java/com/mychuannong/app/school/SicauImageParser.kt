package com.mychuannong.app.school

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.mychuannong.app.feature.calendar.data.remote.dto.CalendarEvent
import com.mychuannong.app.feature.calendar.data.remote.dto.SemesterCalendarDetail
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Verified two-column official layout adapter. Unknown layout/ambiguous OCR fails closed. */
object SicauImageParser {
    data class Notes(val opening:LocalDate,val registrationStart:LocalDate,val registrationEnd:LocalDate,val teaching:LocalDate,val practiceStart:LocalDate,val practiceEnd:LocalDate,val vacationStart:LocalDate,val vacationEnd:LocalDate?)
    fun parseNotes(raw:String,year:Int,semester:Int):Notes {
        fun dates(label:String):List<LocalDate> {
            val marker=when(label){"报到注册"->"注册";"实践周"->"实践";"正式行课"->"正式行";"寒假"->"寒";"暑假"->"六";else->label}
            val rows=raw.lines().map{it.replace(Regex("\\s+"),"")}.filter{if(label=="暑假")it.startsWith("六") else it.contains(marker)}
            require(rows.size==1){"$label 备注行无法确认"}
            val part=rows.single()
            require(part.isNotEmpty()){"缺少$label"}
            val matches=Regex("(\\d{1,2})月(\\d{1,2})日?(?:至(?:(\\d{1,2})月)?(\\d{1,2})日?)?").findAll(part).toList()
            require(matches.size==1){"$label 日期不唯一"}
            val m=matches.single();val month=m.groupValues[1].toInt()
            fun date(month:Int,day:Int)=LocalDate.of(if(semester==1 && month>=7)year else year+1,month,day)
            val start=date(month,m.groupValues[2].toInt())
            val end=if(m.groupValues[4].isBlank())start else date(m.groupValues[3].toIntOrNull()?:month,m.groupValues[4].toInt())
            require(end>=start);return listOf(start,end)
        }
        val opening=dates("开学上班")
        val registration=dates("报到注册")
        val teaching=dates("正式行课")
        val practice=dates("实践周")
        val vacation=dates(if(semester==1)"寒假" else "暑假")
        val n=Notes(opening[0],registration[0],registration[1],teaching[0],practice[0],practice[1],vacation[0],if(semester==1)vacation[1] else null)
        require(n.teaching.dayOfWeek.value==1 && n.practiceStart.dayOfWeek.value==1 && n.practiceEnd.dayOfWeek.value==7)
        require(n.registrationStart<=n.registrationEnd && n.registrationEnd<n.teaching && n.opening<=n.registrationStart)
        require(ChronoUnit.DAYS.between(n.registrationEnd,n.teaching) in 1..7)
        require(ChronoUnit.DAYS.between(n.teaching,n.practiceStart) in 98..175 && ChronoUnit.DAYS.between(n.practiceStart,n.practiceEnd)==6L)
        require(n.vacationStart>=n.practiceStart && n.vacationStart<=n.practiceEnd.plusDays(1))
        if(n.vacationEnd!=null)require(ChronoUnit.DAYS.between(n.vacationStart,n.vacationEnd) in 7..70)
        return n
    }
    fun parsePeriods(raw:String):List<PublicPeriod> {
        val labels=listOf("一","二","三","四","五","六","七","八","九","十")
        val lines=raw.lines().map{it.replace(Regex("\\s+"),"").replace('：',':').replace("第丸节","第九节")}
        return labels.mapIndexed { i,label ->
            val rows=lines.filter{it.contains("第${label}节")}
            require(rows.size==1){"第${label}节无法确认"}
            val times=Regex("\\d{1,2}:\\d{2}").findAll(rows.single().substringAfter("第${label}节")).map{it.value.padStart(5,'0')}.toList()
            require(times.size==2){"第${label}节起止时间无法确认"}
            PublicPeriod(i+1,times[0],times[1],i/2+1)
        }
    }

    private fun recognize(bitmap:Bitmap,scale:Int):String {
        val enlarged=Bitmap.createScaledBitmap(bitmap,bitmap.width*scale,bitmap.height*scale,false)
        val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val result=Tasks.await(recognizer.process(InputImage.fromBitmap(enlarged,0)),40,TimeUnit.SECONDS)
            val lines=result.textBlocks.flatMap{it.lines}.sortedBy{it.boundingBox?.centerY()?:0}
            val rows=mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
            lines.forEach { line ->
                val y=line.boundingBox?.centerY()?:0
                val last=rows.lastOrNull()
                if(last!=null && kotlin.math.abs((last.first().boundingBox?.centerY()?:0)-y)<(line.boundingBox?.height()?:20)/2)last.add(line) else rows.add(mutableListOf(line))
            }
            rows.joinToString("\n"){row->row.sortedBy{it.boundingBox?.left?:0}.joinToString(" "){it.text}}
        } finally {recognizer.close();if(enlarged!==bitmap)enlarged.recycle()}
    }
    fun parse(found:SicauOfficialSource.Discovery,active:PublicAcademicData, diagnostic:(String)->Unit = {}):PublicAcademicData {
        require(found.resources.size==2){"官方图片布局需重新核对"}
        val year=found.academicYear.take(4).toInt()
        require(year in active.academicYear.take(4).toInt()..active.academicYear.take(4).toInt()+1){"学年变化异常"}
        var notes:List<Notes>?=null
        var periods:List<PublicPeriod>?=null
        var calendarSource:PublicSource?=null
        var periodsSource:PublicSource?=null
        found.resources.forEach { (resource,bytes) ->
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("原图无法解码")
            require(bitmap.width in 600..4000 && bitmap.height in 600..5000)
            try {
                val overview=recognize(bitmap,2)
                diagnostic("overview ${bitmap.width}x${bitmap.height}: $overview")
                if(overview.contains("第一节") && overview.contains("第十节")) {
                    val crop=Bitmap.createBitmap(bitmap,bitmap.width*64/100,bitmap.height*35/100,bitmap.width*30/100,bitmap.height*31/100)
                    val first=recognize(crop,3);val second=recognize(crop,4);crop.recycle();diagnostic("periods3: $first\nperiods4: $second")
                    val a=parsePeriods(first);val b=parsePeriods(second);require(a==b){"作息识别结果不一致"};periods=a;periodsSource=resource
                } else {
                    fun half(right:Boolean,scale:Int):Notes {
                        val x=if(right)bitmap.width*58/100 else bitmap.width*14/100
                        val width=bitmap.width*33/100
                        val crop=Bitmap.createBitmap(bitmap,x,bitmap.height*83/100,width,bitmap.height*15/100)
                        return try{val text=recognize(crop,scale);diagnostic("notes right=$right scale=$scale: $text");parseNotes(text,year,if(right)2 else 1)}finally{crop.recycle()}
                    }
                    fun consensus(right:Boolean):Notes {
                        val readings=listOf(3,4,5).mapNotNull{scale->runCatching{half(right,scale)}.getOrNull()}
                        val groups=readings.groupingBy{it}.eachCount()
                        require(groups.size==1 && groups.values.single()>=2){"校历识别结果不一致"}
                        return groups.keys.single()
                    }
                    notes=listOf(consensus(false),consensus(true));calendarSource=resource
                }
            } finally {bitmap.recycle()}
        }
        val parsed=notes?:error("未识别出校历规则");val times=periods?:error("未识别出学生作息")
        val terms=parsed.mapIndexed { index,n ->
            val number=index+1
            fun ev(name:String,type:String,s:LocalDate,e:LocalDate=s)=CalendarEvent(name,type,s.toString(),e.toString(),SicauAcademicData.SOURCE)
            val events=listOf(ev("开学上班","opening",n.opening),ev("报到注册","registration",n.registrationStart,n.registrationEnd),ev("正式行课","teaching",n.teaching),ev("实践周","practice",n.practiceStart,n.practiceEnd),ev(if(number==1)"寒假" else "暑假开始",if(number==1)"vacation" else "vacation_start",n.vacationStart,n.vacationEnd?:n.vacationStart))
            val old=active.terms.firstOrNull{it.semesterCode=="${found.academicYear}-$number"}
            // Same-year revisions of key dates require review; never silently shift existing schedules.
            if(old!=null)require(old.semesterStart==n.teaching.toString() && old.semesterEnd==n.practiceEnd.toString()){"正式行课日期发生修订，需核对调整通知"}
            SemesterCalendarDetail(semesterCode="${found.academicYear}-$number",title="${found.academicYear}学年",subtitle=if(number==1)"第一学期" else "第二学期",calendarStart=n.opening.withDayOfMonth(1).toString(),calendarEnd=(n.vacationEnd?:n.practiceEnd.withDayOfMonth(n.practiceEnd.lengthOfMonth())).toString(),semesterStart=n.teaching.toString(),semesterEnd=n.practiceEnd.toString(),teachingWeeks=(ChronoUnit.DAYS.between(n.teaching,n.practiceEnd)/7+1).toInt(),events=events+(old?.events?.filter{it.type=="festival"}?:emptyList()),sourceResource=calendarSource!!.url,sourceFingerprint=calendarSource!!.sha256,dataVersion=found.fingerprint.take(12))
        }
        return active.copy(revisions=(active.revisions.orEmpty()+PublicRevision(active.version,active.contentFingerprint,active.sourceResources,java.time.ZonedDateTime.now(SicauAcademicData.zone).toString())).distinctBy{it.fingerprint}.takeLast(8),academicYear=found.academicYear,version="sicau-${found.academicYear}-${found.fingerprint.take(12)}",contentFingerprint=found.fingerprint,terms=(active.terms.filter{old->terms.none{it.semesterCode==old.semesterCode}}+terms),periods=times,periodsVersion=found.fingerprint.take(12),sourceResources=listOf(calendarSource!!,periodsSource!!),checkedAt=java.time.ZonedDateTime.now(SicauAcademicData.zone).toString())
    }
}

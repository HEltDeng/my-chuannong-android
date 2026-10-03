package com.mychuannong.app.school

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.mychuannong.app.feature.calendar.data.remote.dto.SemesterCalendarDetail
import com.mychuannong.app.feature.schedule.CoursePeriod
import com.tencent.mmkv.MMKV
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Keep data class PublicPeriod(val section:Int, val start:String, val end:String, val lecture:Int)
@Keep data class PublicSource(val url:String, val sha256:String)
@Keep data class PublicRevision(val version:String,val fingerprint:String,val sources:List<PublicSource>,val replacedAt:String)
@Keep data class PublicAcademicData(
    val schemaVersion:Int, val school:String, val academicYear:String, val version:String,
    val validationStatus:String, val sourcePage:String, val sourceResources:List<PublicSource>,
    val contentFingerprint:String, val checkedAt:String, val checkIntervalHours:Int,
    val terms:List<SemesterCalendarDetail>, val periods:List<PublicPeriod>, val periodsVersion:String,
    val revisions:List<PublicRevision> = emptyList(), val schoolPublishedAt:String? = null,
)

/** Public data only. This client has no school CookieJar, account headers or school data upload. */
object SicauAcademicData {
    const val SOURCE = "https://www.sicau.edu.cn/ggfw/rlxl.htm"
    val zone:ZoneId = ZoneId.of("Asia/Shanghai")
    private lateinit var context:Context
    private lateinit var active:PublicAcademicData
    private val gson=Gson()
    private val mutex=Mutex()
    private val store get()=MMKV.mmkvWithID("sicau_public_academic_v1")
    private val changes=MutableStateFlow(0)
    val revision=changes.asStateFlow()
    val client=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
    fun initialize(ctx:Context) {
        context=ctx.applicationContext
        val seed=parse(context.assets.open("sicau-academic.json").bufferedReader().use{it.readText()})
        active=VerifiedAcademicCache().load(seed)
    }
    fun current():PublicAcademicData=active
    fun term(id:String)=active.terms.firstOrNull{it.semesterCode==id}
    fun periods()=active.periods.map{CoursePeriod(LocalTime.parse(it.start),LocalTime.parse(it.end))}
    fun isTeachingDate(id:String,date:LocalDate=LocalDate.now(zone)):Boolean {
        val t=term(id)?:return false
        return !date.isBefore(LocalDate.parse(t.semesterStart)) && !date.isAfter(LocalDate.parse(t.semesterEnd)) &&
            t.events.none{it.type=="vacation" && date.toString()>=it.start && date.toString()<=it.end || it.type=="vacation_start" && date.toString()>=it.start}
    }
    fun parse(json:String):PublicAcademicData {
        val d=gson.fromJson(json,PublicAcademicData::class.java)
        require(d.schemaVersion==1 && d.school=="sicau" && d.validationStatus=="verified")
        require(d.sourcePage==SOURCE && d.version.isNotBlank() && d.contentFingerprint.matches(Regex("[a-f0-9]{64}")))
        require(d.terms.isNotEmpty() && d.terms.size<=40 && d.terms.map{it.semesterCode}.distinct().size==d.terms.size)
        d.terms.forEach { t ->
            require(t.semesterCode.matches(Regex("\\d{4}-\\d{4}-[12]")))
            val s=LocalDate.parse(t.semesterStart); val e=LocalDate.parse(t.semesterEnd)
            require(s.dayOfWeek.value==1 && e>=s && java.time.temporal.ChronoUnit.DAYS.between(s,e) in 70..210)
            require(t.teachingWeeks in 10..30 && t.weekRule=="MONDAY_CONTINUOUS")
            val year=t.semesterCode.take(4).toInt(); require(t.semesterCode.substring(5,9).toInt()==year+1)
            require(s.year==if(t.semesterCode.endsWith("-1"))year else year+1)
            require((java.time.temporal.ChronoUnit.DAYS.between(s,e)/7+1).toInt()==t.teachingWeeks)
            require(LocalDate.parse(t.calendarStart)<=s && LocalDate.parse(t.calendarEnd)>=e)
            t.events.forEach { ev -> require(LocalDate.parse(ev.start)<=LocalDate.parse(ev.end) && ev.name.isNotBlank() && ev.source==SOURCE) }
        }
        require(d.periods.size==10)
        d.periods.forEachIndexed{i,p->require(p.section==i+1 && p.lecture==i/2+1 && LocalTime.parse(p.start)<LocalTime.parse(p.end));if(i>0)require(LocalTime.parse(d.periods[i-1].end)<LocalTime.parse(p.start))}
        require(d.sourceResources.isNotEmpty() && d.sourceResources.all{it.url.startsWith("https://www.sicau.edu.cn/") && it.sha256.matches(Regex("[a-f0-9]{64}"))})
        require(d.academicYear.matches(Regex("20\\d{2}-20\\d{2}")) && d.terms.any{it.semesterCode.startsWith(d.academicYear)})
        require(d.contentFingerprint==SicauOfficialSource.sha256(d.sourceResources.map{it.sha256}.sorted().joinToString("").toByteArray()))
        return d.copy(revisions=d.revisions.orEmpty(),terms=d.terms.map{it.copy(sourceResource=it.sourceResource.orEmpty(),sourceFingerprint=it.sourceFingerprint.orEmpty(),dataVersion=it.dataVersion.orEmpty())})
    }
    suspend fun refresh(force:Boolean=false):Result<PublicAcademicData> = withContext(Dispatchers.IO) { mutex.withLock {
        val now=System.currentTimeMillis()
        if(!force && now-store.decodeLong("attempt",0)<active.checkIntervalHours.coerceIn(1,168)*3600000L) return@withLock if(store.decodeString("status", "checked") == "checked") Result.success(active) else Result.failure(IllegalStateException(store.decodeString("status")))
        store.encode("attempt",now)
        runCatching {
            val endpoint=com.mychuannong.app.BuildConfig.SICAU_ACADEMIC_URL
            val candidate=if(endpoint.isBlank()) SicauOfficialSource.check(active) else {
                val payload=client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                    check(response.isSuccessful){"更新暂不可用，已保留本地校历"}
                    response.body?.string()?.also{require(it.length<=1048576)} ?: error("更新内容为空")
                }
                val envelope=com.google.gson.JsonParser.parseString(payload).asJsonObject
                val status=envelope.get("syncStatus")?.asString
                if(status=="pending_review") error("官方资料有变化，核对中；保留已验证校历")
                if(status=="sync_failed") error("官方来源检查失败，保留已验证校历")
                parse(if(envelope.has("data")) envelope.get("data").toString() else payload)
            }
            val raw=gson.toJson(candidate)
            parse(raw)
            val changed=active.version!=candidate.version || active.contentFingerprint!=candidate.contentFingerprint
            active=VerifiedAcademicCache().commit(candidate)
            if(changed) {
                changes.value++
                com.mychuannong.app.feature.calendar.data.repository.SemesterCalendarRepository.clearMemoryCache()
                com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(context)
                val manager=android.appwidget.AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(android.content.ComponentName(context,com.mychuannong.app.TimeTableAppWidget::class.java))
                com.mychuannong.app.WidgetUpdateCoordinator.updateAll(context)
                context.sendBroadcast(android.content.Intent(context,com.mychuannong.app.TimeTableAppWidget::class.java).setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))
            }
            store.encode("status", "checked")
            active
        }.onFailure { store.encode("status",it.message.orEmpty()) }
    } }
}

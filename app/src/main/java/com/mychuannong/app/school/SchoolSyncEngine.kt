package com.mychuannong.app.school

import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.tencent.mmkv.MMKV
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SyncPhase { NOT_SYNCED, LOADING, DATA, EMPTY, CACHED, FAILED, EXPIRED, PARSER_PENDING }
data class SyncItem(val phase: SyncPhase = SyncPhase.NOT_SYNCED, val lastSuccess: Long? = null, val message: String = "尚未同步")
data class CourseBatch(val term:String,val courses:List<TimeTableMySubject>,val records:List<SchoolCourseRecord> = emptyList())
sealed interface SchoolRead<out T> {
    data class Success<T>(val value:T): SchoolRead<T>
    data class Failure(val phase:SyncPhase,val message:String): SchoolRead<Nothing>
}
/** Implementations must confirm identity from authenticated school evidence, never from a URL or user input. */
internal interface SchoolReader {
    suspend fun identity(): SchoolRead<String>
    suspend fun courses(): SchoolRead<CourseBatch>
    suspend fun grades(): SchoolRead<List<Grade>>
    suspend fun exams(): SchoolRead<List<ExamArrange>>
}
internal object SicauReader:SchoolReader {
    override suspend fun identity() = SicauJiaowuAuth.identity()
    override suspend fun courses() = SicauJiaowuAuth.read(JiaowuPage.COURSES,SicauBusinessParser::courses)
    override suspend fun grades() = SicauJiaowuAuth.read(JiaowuPage.GRADES) { html,_ -> SicauBusinessParser.grades(html) }
    override suspend fun exams() = SicauJiaowuAuth.read(JiaowuPage.EXAMS) { html,_ -> SicauBusinessParser.exams(html) }
}

/** Local-only pipeline. A failed item never overwrites a successful cache; account change is serialized. */
object SchoolSyncEngine {
    private val mutex=Mutex()
    private val metadata by lazy { MMKV.mmkvWithID("sicau_sync_metadata") }
    private val mutable=MutableStateFlow(listOf("courses","grades","exams").associateWith { key ->
        val stamp=metadata.decodeLong(key,0L)
        if(stamp>0) SyncItem(SyncPhase.CACHED,stamp,"显示本机缓存") else SyncItem()
    })
    val items=mutable.asStateFlow()
    private val revisionMutable=MutableStateFlow(0L)
    val revision=revisionMutable.asStateFlow()
    private var generation=0L
    private var lastAttempt=0L
    suspend fun sync(force:Boolean=true) = perform(SicauReader,force)
    fun syncedTerm():String = metadata.decodeString("course_term","").orEmpty()
    fun courseNotes(account:String,term:String):List<String> {
        val raw=metadata.decodeString("school_records_"+importKey(account,term)) ?: return emptyList()
        return runCatching {
            com.google.gson.Gson().fromJson(raw,Array<SchoolCourseRecord>::class.java).mapNotNull { record ->
                val f=record.fields;val note=f["混合式教学"].orEmpty()
                if(record.unscheduled || note.isNotBlank()) listOf(f["课程名称"] ?: f["课程"].orEmpty(),if(record.unscheduled) "不定时课程："+f["上课时间"] else "",note).filter(String::isNotBlank).joinToString(" · ") else null
            }
        }.getOrDefault(emptyList())
    }
    internal suspend fun saveSchoolRows(db:CoursesDataBase,account:String,batch:CourseBatch) {
        require(account.isNotBlank() && TimetableImportRules.term(batch.term))
        require(batch.courses.all { it.studentId==account && it.term==batch.term && !it.isCustom && it.courseName.isNotBlank() &&
            it.weekday in 1..7 && it.start in 1..10 && it.step>0 && it.start+it.step<=11 && !it.weeks.isNullOrEmpty() && it.weeks!!.all { w -> w in 1..30 } })
        db.withTransaction {
            val dao=db.courseDao()
            val protected=dao.getCoursesByTerm(batch.term,account).filter { it.source!="SCHOOL" || it.isCustom }
            fun key(c:TimeTableMySubject)=listOf(c.courseName,c.classroom.orEmpty(),c.teacher,c.start,c.step,c.weekday)
            val protectedKeys=protected.map(::key).toSet()
            val rows=TimetableImportRules.merge(batch.courses).filter { key(it) !in protectedKeys }.map { it.copy(source="SCHOOL",id=0) }
            dao.deleteSyncedCourses(batch.term,account)
            val inserted=dao.insertCourses(rows.toMutableList())
            check(inserted.all { it!=-1L })
        }
    }
    /** Serialize activation and identity acceptance with refresh/logout/account cache changes. */
    internal suspend fun bindJiaowu(academic:SicauJiaowuSession, unified:NativeAuthSession) = mutex.withLock {
        val id=requireNotNull(academic.verifiedId)
        require(academic.jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        SicauNativeAuth.activate(unified,id)
        SicauJiaowuAuth.activate(academic)
        performLocked(object:SchoolReader by SicauReader {
            override suspend fun identity():SchoolRead<String> = SchoolRead.Success(id)
        },true)
        jiaowuTrace("bindingReady=${StudentInfoManager.hasCompleteBinding && StudentInfoManager.studentId==id}")
    }
    private fun importKey(account:String,term:String) = "image_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it) } + "_" + term
    fun hasImportedTimetable(account:String,term:String) = account.isNotBlank() && metadata.decodeLong(importKey(account,term),0L)>0L
    fun saveImportNotes(account:String,term:String,notes:String) { metadata.encode("notes_"+importKey(account,term),notes) }
    fun importNotes(account:String,term:String):String = metadata.decodeString("notes_"+importKey(account,term),"").orEmpty()
    fun importedTerms(account:String):List<String> {
        if(account.isBlank()) return emptyList()
        val prefix=importKey(account,"")
        return metadata.allKeys().orEmpty().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.filter(TimetableImportRules::term)
    }
    /** Explicit reviewed image import does not imply a refreshed school login session. */
    suspend fun importTimetable(term:String,account:String,courses:List<TimeTableMySubject>) = mutex.withLock {
        require(account.isNotBlank() && StudentInfoManager.studentId==account) { "学校账号已变化，请重新打开导入页面" }
        withContext(Dispatchers.IO) { saveImportedRows(CoursesDataBase.getDatabase(PlanetApplication.appContext),term,account,courses) }
        val stamp=System.currentTimeMillis()
        metadata.encode(importKey(account,term),stamp);metadata.encode("courses",stamp)
        MMKV.defaultMMKV().encode("lastUpdate_$term",stamp)
        mutable.value=mutable.value+("courses" to SyncItem(SyncPhase.DATA,stamp,"已保存本机课表"))
        revisionMutable.value++
        com.mychuannong.app.WidgetUpdateCoordinator.updateAll(PlanetApplication.appContext)
        com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(PlanetApplication.appContext)
    }
    internal suspend fun saveImportedRows(db:CoursesDataBase,term:String,account:String,courses:List<TimeTableMySubject>) {
        require(account.isNotBlank() && TimetableImportRules.term(term) && courses.isNotEmpty()) { "没有可保存的完整课表" }
        require(courses.all { it.studentId==account && it.term==term && !it.isCustom && it.courseName.isNotBlank() &&
            it.weekday in 1..7 && it.start in 1..10 && it.step>0 && it.start+it.step<=11 &&
            !it.weeks.isNullOrEmpty() && it.weeks!!.all { week -> week in 1..30 } }) { "课程信息不完整，请核对后保存" }
        db.withTransaction {
            db.courseDao().deleteNetworkCoursesByTerm(term,account)
            db.courseDao().insertCourses(TimetableImportRules.merge(courses).toMutableList())
        }
    }
    internal suspend fun perform(reader:SchoolReader,force:Boolean) = mutex.withLock {
        performLocked(reader,force)
    }
    private suspend fun performLocked(reader:SchoolReader,force:Boolean) {
        val now=System.currentTimeMillis()
        if (!force && now-lastAttempt<30*60*1000L) return
        lastAttempt=now
        SchoolAuthState.move(SchoolAuthPhase.FETCHING)
        val epoch=generation
        mutable.value=mutable.value.mapValues { (_,v)->v.copy(phase=SyncPhase.LOADING,message="正在同步") }
        val identity=try { reader.identity() } catch (e:CancellationException) { throw e } catch (_:Exception) { SchoolRead.Failure(SyncPhase.FAILED,"学校网络请求失败，保留本机缓存") }
        if(identity is SchoolRead.Failure) {
            SchoolAuthState.move(when(identity.phase) { SyncPhase.EXPIRED -> SchoolAuthPhase.SESSION_EXPIRED; SyncPhase.NOT_SYNCED -> SchoolAuthPhase.UNBOUND; else -> SchoolAuthPhase.ERROR })
            mutable.value=mutable.value.mapValues { (_,v)->v.copy(phase=identity.phase,message=identity.message) }
            if(identity.phase==SyncPhase.EXPIRED) { StudentInfoManager.expire();SicauAdapter.expired() }
            if(identity.phase==SyncPhase.PARSER_PENDING) SicauAdapter.pending()
            revisionMutable.value++
            return
        }
        val account=(identity as SchoolRead.Success).value
        if(account.isBlank() || epoch!=generation) return
        withContext(Dispatchers.IO) {
            if(StudentInfoManager.studentId!=account) {
                // Room remains account-scoped; keep earlier accounts and manual/imported rows.
                MMKV.mmkvWithID("content_cache").clearAll()
                MMKV.mmkvWithID("mooc_local_cache").clearAll()
                metadata.clearAll()
                mutable.value=mutable.value.mapValues { SyncItem() }
            }
            StudentInfoManager.acceptVerifiedIdentity(account)
            SicauAdapter.confirmed()
            apply("courses",{reader.courses()}, { it.courses.isEmpty() && it.records.isEmpty() }) { batch ->
                saveSchoolRows(CoursesDataBase.getDatabase(PlanetApplication.appContext),account,batch)
                metadata.encode("course_term",batch.term)
                metadata.encode("school_records_"+importKey(account,batch.term),com.google.gson.Gson().toJson(batch.records))
                MMKV.defaultMMKV().encode("lastUpdate_${batch.term}",System.currentTimeMillis())
                jiaowuTrace("COURSES mapped=${batch.courses.size} records=${batch.records.size} saved=true termMatch=true")
            }
            apply("grades",{reader.grades()}, { it.isEmpty() }) { ScoreCache.saveGrades(it) }
            apply("exams",{reader.exams()}, { it.isEmpty() }) { ExamArrangementCache().saveExamArrangement(it) }
        }
        if(mutable.value.values.any { it.phase==SyncPhase.EXPIRED }) {
            StudentInfoManager.expire();SchoolAuthState.move(SchoolAuthPhase.SESSION_EXPIRED)
        } else SchoolAuthState.move(if(mutable.value["courses"]?.phase in setOf(SyncPhase.DATA,SyncPhase.EMPTY)) SchoolAuthPhase.READY else SchoolAuthPhase.SESSION_VALID)
        revisionMutable.value++
        com.mychuannong.app.WidgetUpdateCoordinator.updateAll(PlanetApplication.appContext)
        com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(PlanetApplication.appContext)
    }
    private suspend fun <T> apply(key:String,read:suspend()->SchoolRead<T>,isEmpty:(T)->Boolean,save:suspend(T)->Unit) {
        val old=mutable.value.getValue(key)
        val result=try { read() } catch (e:CancellationException) { throw e } catch (_:Exception) { SchoolRead.Failure(SyncPhase.FAILED,"刷新失败，保留上次数据") }
        val next=when(result) {
            is SchoolRead.Success -> try {
                save(result.value)
                val stamp=System.currentTimeMillis();metadata.encode(key,stamp)
                SyncItem(if(isEmpty(result.value)) SyncPhase.EMPTY else SyncPhase.DATA,stamp,if(isEmpty(result.value)) "查询成功，暂无数据" else "本机数据已更新")
            } catch(e:CancellationException) { throw e } catch(_:Exception) { old.copy(phase=SyncPhase.FAILED,message="本机缓存更新失败") }
            is SchoolRead.Failure -> old.copy(phase=result.phase,message=result.message)
        }
        mutable.value=mutable.value+(key to next)
    }
    suspend fun clear() = mutex.withLock {
        generation++;lastAttempt=0;StudentInfoManager.clear();metadata.clearAll();SicauNativeAuth.clear();SicauJiaowuAuth.clear()
        withContext(Dispatchers.IO) {
            CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao().clearAllCourses()
            MMKV.mmkvWithID("content_cache").clearAll();MMKV.mmkvWithID("mooc_local_cache").clearAll()
        }
        mutable.value=mutable.value.mapValues { SyncItem() };revisionMutable.value++
        SicauAdapter.clear();SchoolAuthState.move(SchoolAuthPhase.UNBOUND)
    }
}

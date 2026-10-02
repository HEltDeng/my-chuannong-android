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
data class CourseBatch(val term:String,val courses:List<TimeTableMySubject>)
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
    private fun pending() = SchoolRead.Failure(SyncPhase.PARSER_PENDING,SicauAdapter.PENDING)
    override suspend fun identity() = SicauNativeAuth.identity()
    override suspend fun courses() = pending()
    override suspend fun grades() = pending()
    override suspend fun exams() = pending()
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
    internal suspend fun perform(reader:SchoolReader,force:Boolean) = mutex.withLock {
        val now=System.currentTimeMillis()
        if (!force && now-lastAttempt<30*60*1000L) return@withLock
        lastAttempt=now
        val epoch=generation
        mutable.value=mutable.value.mapValues { (_,v)->v.copy(phase=SyncPhase.LOADING,message="正在同步") }
        val identity=try { reader.identity() } catch (e:CancellationException) { throw e } catch (_:Exception) { SchoolRead.Failure(SyncPhase.FAILED,"学校网络请求失败，保留本机缓存") }
        if(identity is SchoolRead.Failure) {
            mutable.value=mutable.value.mapValues { (_,v)->v.copy(phase=identity.phase,message=identity.message) }
            if(identity.phase==SyncPhase.EXPIRED) StudentInfoManager.expire()
            if(identity.phase==SyncPhase.PARSER_PENDING) SicauAdapter.pending()
            revisionMutable.value++
            return@withLock
        }
        val account=(identity as SchoolRead.Success).value
        if(account.isBlank() || epoch!=generation) return@withLock
        withContext(Dispatchers.IO) {
            if(StudentInfoManager.studentId!=account) {
                CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao().clearAllCourses()
                MMKV.mmkvWithID("content_cache").clearAll()
                MMKV.mmkvWithID("mooc_local_cache").clearAll()
                metadata.clearAll()
                mutable.value=mutable.value.mapValues { SyncItem() }
            }
            StudentInfoManager.acceptVerifiedIdentity(account)
            SicauAdapter.confirmed()
            apply("courses",{reader.courses()}, { it.courses.isEmpty() }) { batch ->
                require(batch.term.isNotBlank() && batch.courses.all { it.term==batch.term })
                val db=CoursesDataBase.getDatabase(PlanetApplication.appContext)
                val dao=db.courseDao()
                // An authenticated empty result clears only the explicitly queried term.
                db.withTransaction {
                    dao.deleteNetworkCoursesByTerm(batch.term,account)
                    dao.insertCourses(batch.courses.map { it.copy(studentId=account) }.toMutableList())
                }
            }
            apply("grades",{reader.grades()}, { it.isEmpty() }) { ScoreCache.saveGrades(it) }
            apply("exams",{reader.exams()}, { it.isEmpty() }) { ExamArrangementCache().saveExamArrangement(it) }
        }
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
        generation++;lastAttempt=0;StudentInfoManager.clear();metadata.clearAll();SicauNativeAuth.clear()
        withContext(Dispatchers.IO) {
            CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao().clearAllCourses()
            MMKV.mmkvWithID("content_cache").clearAll();MMKV.mmkvWithID("mooc_local_cache").clearAll()
        }
        mutable.value=mutable.value.mapValues { SyncItem() };revisionMutable.value++
    }
}

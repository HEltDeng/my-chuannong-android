package com.mychuannong.app.school

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LearningCacheDeviceTest {
    private val a="synthetic-cache-a"
    private val b="synthetic-cache-b"
    private val term="2026-2027-1"
    private fun batch(owner:String,name:String)=SicauBusinessParser.courses("""<h1>四川农业大学2026-2027-1学期课程表</h1><table><tr><th>教师</th><th>课程名称</th><th>周次</th><th>教室</th><th>上课时间</th></tr><tr><td>合成教师</td><td>$name</td><td>1-16</td><td>A</td><td>2-9,2-10</td></tr></table>""",owner)
    private fun report(name:String)=SchoolTranscript(listOf(TranscriptCourse(name,"缓考","02.00","必修","测试备注","TEST")),listOf(TranscriptField("有效学分","02.00")),updatedAt=123456789L)
    private fun isolated(block:suspend()->Unit)=runBlocking {
        org.junit.Assume.assumeTrue(StudentInfoManager.studentId.isBlank())
        val db=CoursesDataBase.getDatabase(PlanetApplication.appContext)
        org.junit.Assume.assumeTrue(db.courseDao().getAllCoursesNow().isEmpty())
        try { block() } finally { SchoolSyncEngine.clear() }
    }
    @Test fun homeUsesTranscriptAndCurrentOwnerCoursesWithoutNetwork()=isolated {
        StudentInfoManager.acceptVerifiedIdentity(a)
        val summary=listOf(TranscriptField("平均成绩绩点","03.10"),TranscriptField("加权平均成绩","080.50"))
        ScoreCache.saveTranscript(a,report("首页合成成绩").copy(summary=summary))
        val db=CoursesDataBase.getDatabase(PlanetApplication.appContext)
        val today=java.time.LocalDate.now(SicauAcademicData.zone)
        val target=if(java.time.LocalTime.now(SicauAcademicData.zone)>=com.mychuannong.app.feature.schedule.CourseTimePreferences.periods.last().end)today.plusDays(1) else today
        val localBatch=batch(a,"首页合成课程").let { old -> old.copy(courses=old.courses.map {
            it.copy(weekday=target.dayOfWeek.value,weeks=(1..30).toList())
        }) }
        SchoolSyncEngine.saveSchoolRows(db,a,localBatch)
        val repository=com.mychuannong.app.overview.data.OverviewRepository(PlanetApplication.appContext)
        val state=repository.loadLocalState()
        assertEquals("03.10",state.metrics.single().value)
        assertTrue(state.metrics.single().subtitle.contains("080.50"))
        assertTrue(state.metrics.single().secondarySubtitle.contains("本地缓存"))
        assertEquals("首页合成课程",state.todayCourses.single().courseName)
        StudentInfoManager.expire()
        assertEquals(state.metrics,repository.loadLocalState().metrics)
        StudentInfoManager.acceptVerifiedIdentity(b)
        val other=repository.loadLocalState()
        assertEquals("--",other.metrics.single().value)
        assertTrue(other.todayCourses.isEmpty())
        SchoolSyncEngine.clear()
        assertTrue(repository.loadLocalState().todayCourses.isEmpty())
    }
    @Test fun seedForProcessRestart()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("restartPhase")=="seed")
        check(StudentInfoManager.studentId.isBlank())
        StudentInfoManager.acceptVerifiedIdentity(a)
        ScoreCache.saveTranscript(a,report("合成重启成绩"))
        val db=CoursesDataBase.getDatabase(PlanetApplication.appContext)
        SchoolSyncEngine.saveSchoolRows(db,a,batch(a,"合成重启课程"))
    }
    @Test fun verifyAfterProcessRestart()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("restartPhase")=="verify")
        try {
            assertEquals(a,StudentInfoManager.studentId)
            assertEquals(report("合成重启成绩"),ScoreCache.getTranscript(a))
            val rows=CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao().getCoursesByTerm(term,a)
            assertEquals("合成重启课程",rows.single().courseName)
        } finally { SchoolSyncEngine.clear() }
    }    @Test fun transcriptSurvivesAccountSwitchExpiryAndFailedRequestUntilLogout()=isolated {
        StudentInfoManager.acceptVerifiedIdentity(a)
        ScoreCache.saveTranscript(a,report("合成缓存成绩甲"))
        StudentInfoManager.acceptVerifiedIdentity(b)
        assertNull(ScoreCache.getTranscript(a));assertNull(ScoreCache.getTranscript(b))
        ScoreCache.saveTranscript(b,report("合成缓存成绩乙"))
        StudentInfoManager.acceptVerifiedIdentity(a)
        StudentInfoManager.expire()
        assertEquals(report("合成缓存成绩甲"),ScoreCache.getTranscript(a))
        SchoolSyncEngine.perform(object:SchoolReader {
            override suspend fun identity():SchoolRead<String> = SchoolRead.Failure(SyncPhase.EXPIRED,"模拟会话过期")
            override suspend fun courses():SchoolRead<CourseBatch> = error("must not fetch")
            override suspend fun grades():SchoolRead<List<Grade>> = error("must not fetch")
            override suspend fun exams():SchoolRead<List<ExamArrange>> = error("must not fetch")
        },true)
        assertEquals(report("合成缓存成绩甲"),ScoreCache.getTranscript(a))
        val disk=java.io.File(java.io.File(PlanetApplication.appContext.filesDir,"mmkv").path,"learning_scores_v2").readBytes().toString(Charsets.UTF_8)
        assertFalse(disk.contains("合成缓存成绩甲"))
        SchoolSyncEngine.clear()
        StudentInfoManager.acceptVerifiedIdentity(a)
        assertNull(ScoreCache.getTranscript(a))
    }
    @Test fun automaticSyncKeepsFirstDataManualSuccessReplacesAndFailureKeepsIt()=isolated {
        StudentInfoManager.acceptVerifiedIdentity(a)
        var courseName="合成首次课程"
        var count=0
        var fail=false
        val reader=object:SchoolReader {
            override suspend fun identity():SchoolRead<String> = SchoolRead.Success(a)
            override suspend fun courses():SchoolRead<CourseBatch> {
                count++
                return if(fail)SchoolRead.Failure(SyncPhase.FAILED,"模拟离线") else SchoolRead.Success(batch(a,courseName))
            }
            override suspend fun grades():SchoolRead<List<Grade>> = SchoolRead.Success(emptyList())
            override suspend fun exams():SchoolRead<List<ExamArrange>> = SchoolRead.Success(emptyList())
        }
        SchoolSyncEngine.networkChanged();SchoolSyncEngine.perform(reader,false)
        val dao=CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao()
        val first=dao.getCoursesByTerm(term,a)
        assertTrue(first.isNotEmpty());assertEquals(1,count)
        courseName="合成更新课程"
        SchoolSyncEngine.networkChanged();SchoolSyncEngine.perform(reader,false)
        assertEquals(1,count);assertEquals(first,dao.getCoursesByTerm(term,a))
        SchoolSyncEngine.perform(reader,true)
        val updated=dao.getCoursesByTerm(term,a)
        assertEquals("合成更新课程",updated.single().courseName)
        val stamp=SchoolSyncEngine.courseUpdatedAt(term)
        fail=true;SchoolSyncEngine.perform(reader,true)
        assertEquals(updated,dao.getCoursesByTerm(term,a));assertEquals(stamp,SchoolSyncEngine.courseUpdatedAt(term))
        StudentInfoManager.acceptVerifiedIdentity(b)
        assertTrue(dao.getCoursesByTerm(term,b).isEmpty());assertEquals(0L,SchoolSyncEngine.courseUpdatedAt(term))
        SchoolSyncEngine.perform(reader,true) // Old school session may not switch the current owner.
        assertEquals(b,StudentInfoManager.studentId)
        StudentInfoManager.acceptVerifiedIdentity(a)
        assertEquals(updated,dao.getCoursesByTerm(term,a))
    }
    @Test fun plaintextMigrationAndEncryptedColdReopenPreserveAllOwners()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val old="cache-test-${java.util.UUID.randomUUID()}"
        val target="$old-encrypted"
        try {
            Room.databaseBuilder(ctx,CoursesDataBase::class.java,old).build().let { db ->
                try { db.courseDao().insertCourses((batch(a,"合成持久课程甲").courses+batch(b,"合成持久课程乙").courses).toMutableList()) }
                finally { db.close() }
            }
            EncryptedCourseStorage.migrate(ctx,old,target)
            assertFalse(ctx.getDatabasePath(old).exists())
            fun open()=Room.databaseBuilder(ctx,CoursesDataBase::class.java,target).openHelperFactory(EncryptedCourseStorage.factory()).build()
            repeat(2) {
                val db=open()
                try { assertEquals("合成持久课程甲",db.courseDao().getCoursesByTerm(term,a).single().courseName);assertEquals("合成持久课程乙",db.courseDao().getCoursesByTerm(term,b).single().courseName) }
                finally { db.close() }
            }
            val bytes=ctx.getDatabasePath(target).readBytes()
            assertFalse(String(bytes,Charsets.UTF_8).contains("合成持久课程甲"))
            assertFalse(String(bytes.take(16).toByteArray()).startsWith("SQLite format"))
        } finally { ctx.deleteDatabase(old);ctx.deleteDatabase(target);ctx.deleteDatabase("$target.pending") }
    }
}

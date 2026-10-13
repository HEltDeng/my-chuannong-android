package com.mychuannong.app.school
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.toScheduleCourse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SicauPipelineStorageTest {
 private val term="2026-2027-1"
 private fun html(weeks:String="1-16")="""<h1>四川农业大学2026-2027-1学期课程表</h1><table><tr><th>教师</th><th>课程名称</th><th>周次</th><th>教室</th><th>上课时间</th></tr><tr><td>合成教师</td><td>合成课</td><td>$weeks</td><td>A<br>B</td><td>2-9,2-10(单)<br>4-5,4-6</td></tr></table>"""
 private fun runDb(block:suspend(CoursesDataBase)->Unit)=runBlocking {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val db=Room.inMemoryDatabaseBuilder(ctx,CoursesDataBase::class.java).build()
  try {block(db)}finally {db.close()}
 }
 @Test fun htmlToRoomAndSharedNativeReadersPreservesParity()=runDb { db ->
  val batch=SicauBusinessParser.courses(html(),"synthetic-a")
  SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)
  val native=CourseScheduleRepository.readRows(db.courseDao(),term,"synthetic-a")
  assertEquals(2,native.size);assertEquals(listOf(1,3,5,7,9,11,13,15),native.first { it.weekday==2 }.weeks)
  val reminderAndWidget=native.map {it.toScheduleCourse()}
  assertEquals(native.map {it.courseName},reminderAndWidget.map {it.name})
  assertTrue(CourseScheduleRepository.readRows(db.courseDao(),term,"synthetic-b").isEmpty())
 }
 @Test fun replacementPreservesManualImportsOtherAccountsAndTerms()=runDb { db ->
  val batch=SicauBusinessParser.courses(html(),"synthetic-a");val course=batch.courses.first()
  db.courseDao().insertCourses(mutableListOf(course.copy(courseName="手动",isCustom=true,source="LOCAL"),course.copy(courseName="导入",source="LOCAL"),course.copy(studentId="synthetic-b"),course.copy(term="2025-2026-2")))
  SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)
  SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",CourseBatch(term,emptyList()))
  assertEquals(setOf("手动","导入"),db.courseDao().getCoursesByTerm(term,"synthetic-a").map {it.courseName}.toSet())
  assertEquals(1,db.courseDao().getCoursesByTerm(term,"synthetic-b").size)
  assertEquals(1,db.courseDao().getCoursesByTerm("2025-2026-2","synthetic-a").size)
 }
 @Test fun malformedPageAndWrongOwnerDoNotModifyCache()=runDb {db ->
  val batch=SicauBusinessParser.courses(html(),"synthetic-a");SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)
  val before=db.courseDao().getAllCoursesNow()
  assertTrue(runCatching {val bad=SicauBusinessParser.courses(html().replace("2-9,2-10(单)","invalid"),"synthetic-a");SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",bad)}.isFailure)
  assertTrue(runCatching {SchoolSyncEngine.saveSchoolRows(db,"synthetic-b",batch)}.isFailure)
  assertEquals(before,db.courseDao().getAllCoursesNow())
 }
 @Test fun insertFailureRollsBackDeletion()=runDb {db ->
  val batch=SicauBusinessParser.courses(html(),"synthetic-a");SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)
  val before=db.courseDao().getAllCoursesNow()
  db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_failure BEFORE INSERT ON courses BEGIN SELECT RAISE(ABORT,'synthetic'); END")
  assertTrue(runCatching {SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)}.isFailure)
  assertEquals(before,db.courseDao().getAllCoursesNow())
 }
 @Test fun mergedWeeksAndColdReopen()=runBlocking {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext;val name="sicau-synthetic-${java.util.UUID.randomUUID()}.db"
  fun open()=Room.databaseBuilder(ctx,CoursesDataBase::class.java,name).build()
  var db=open()
  try {
   val batch=SicauBusinessParser.courses(html("1-8"),"synthetic-a")
   SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch.copy(courses=batch.courses+batch.courses.map {it.copy(weeks=listOf(9,11))}))
   val before=db.courseDao().getAllCoursesNow();db.close();db=open();assertEquals(before,db.courseDao().getAllCoursesNow())
   assertEquals(listOf(1,3,5,7,9,11),before.first {it.weekday==2}.weeks)
  }finally{db.close();ctx.deleteDatabase(name)}
 }

 @Test fun htmlRoomViewModelAndHomeReadSameCourses()=runDb { db ->
  val identity=com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
  org.junit.Assume.assumeTrue(identity.studentId.isBlank())
  identity.acceptVerifiedIdentity("synthetic-a")
  try {
  val batch=SicauBusinessParser.courses(html(),"synthetic-a")
  SchoolSyncEngine.saveSchoolRows(db,"synthetic-a",batch)
  val model=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
   com.mychuannong.app.feature.timetable.viewmodel.TimeTableViewModel()
  }
  model.reloadFromCache(term,db.courseDao(),"synthetic-a")
  val homeSource=CourseScheduleRepository.readRows(db.courseDao(),term,"synthetic-a")
  val viewState=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.uiState.value }
  assertEquals(homeSource,viewState!!.subjects)
  assertEquals(term,viewState.term)
  } finally { identity.clear() }
 }
 @Test fun migrationPreservesExistingCoursesAsLocal()=runBlocking {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext;val name="sicau-migration-${java.util.UUID.randomUUID()}.db"
  val legacy=ctx.openOrCreateDatabase(name,0,null)
  legacy.execSQL("CREATE TABLE courses (courseName TEXT NOT NULL,classroom TEXT,teacher TEXT NOT NULL,weeks TEXT,start INTEGER NOT NULL,step INTEGER NOT NULL,weekday INTEGER NOT NULL,id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,isCustom INTEGER NOT NULL,term TEXT NOT NULL,studentId TEXT NOT NULL)")
  legacy.execSQL("CREATE UNIQUE INDEX index_courses_courseName_classroom_teacher_start_step_weekday_term_studentId ON courses(courseName,classroom,teacher,start,step,weekday,term,studentId)")
  legacy.execSQL("INSERT INTO courses VALUES ('合成旧课','A','T','[1,2]',1,2,1,1,0,'2026-2027-1','synthetic-a')")
  legacy.version=11;legacy.close()
  val db=Room.databaseBuilder(ctx,CoursesDataBase::class.java,name).addMigrations(CoursesDataBase.MIGRATION_11_12).build()
  try { val row=db.courseDao().getAllCoursesNow().single();assertEquals("LOCAL",row.source);assertEquals(listOf(1,2),row.weeks);assertEquals(1,row.id) }
  finally {db.close();ctx.deleteDatabase(name)}
 }
}

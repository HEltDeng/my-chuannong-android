package com.mychuannong.app.school.education.data.remote
import com.mychuannong.app.school.*
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.model.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.core.PlanetApplication
/** Legacy entry points share the single verified pipeline; never write their own course cache. */
object EducationHelper {
 suspend fun getCourseScheduleByTerm(week:String,academicSemester:String):Resource<CourseScheduleData> {
  SchoolSyncEngine.sync(false)
  val status=SchoolSyncEngine.items.value.getValue("courses")
  if(status.phase !in setOf(SyncPhase.DATA,SyncPhase.EMPTY) || SchoolSyncEngine.syncedTerm()!=academicSemester) return Resource.Error(if(SchoolSyncEngine.syncedTerm()!=academicSemester) "学校返回学期与查询学期不一致，保留本机缓存" else status.message)
  val rows=CoursesDataBase.getDatabase(PlanetApplication.appContext).courseDao().getCoursesByTerm(academicSemester,StudentInfoManager.studentId)
  return Resource.Success(CourseScheduleData(rows.map { Course(it.courseName,it.teacher,it.weeks.orEmpty().joinToString(",")+"周[${it.start}-${it.start+it.step-1}]节",it.classroom.orEmpty(),it.weekday.toString()) },SchoolSyncEngine.courseNotes(StudentInfoManager.studentId,academicSemester)))
 }
 suspend fun getParsedCourseScheduleByTerm(week:String,academicSemester:String)=getCourseScheduleByTerm(week,academicSemester)
 suspend fun getCourseGrades(academicYearSemester:String?=null,courseNature:CourseNature?=null,courseName:String="",displayMode:DisplayMode=DisplayMode.BEST_GRADE,studyMode:StudyMode=StudyMode.MAJOR):CourseGradeResponse {
  SchoolSyncEngine.sync(false)
  val state=SchoolSyncEngine.items.value.getValue("grades")
  if(state.phase !in setOf(SyncPhase.DATA,SyncPhase.EMPTY))return CourseGradeResponse("UNAVAILABLE",state.message)
  val rows=ScoreCache.getGrades().orEmpty().filter { (academicYearSemester==null || it.item==academicYearSemester) && it.name.contains(courseName) && (courseNature==null || it.attribute==courseNature.chineseName) }
  return CourseGradeResponse("200","学校原始成绩",rows.map { CourseGrade(it.item,it.id,it.name,it.reItem,it.grade,"","",it.flag,it.score,it.timeR,it.point,it.upperReItem,it.method,it.property,it.attribute,CourseNature.fromChineseName(it.attribute),"") })
 }
 suspend fun getAvailableSemestersForCourseGrades():List<String> { SchoolSyncEngine.sync(false);return ScoreCache.getGrades().orEmpty().map { it.item }.distinct() }
 suspend fun getGradeDetail(url:String):GradeDetailResponse? = throw IllegalStateException("学校未提供已验证的成绩明细接口")
 suspend fun getSemesterStartDate(academicYearSemester:String?=null):String? = academicYearSemester?.let { SicauAcademicData.term(it)?.semesterStart }
}

package com.mychuannong.app.school.education.data.remote
import com.mychuannong.app.school.SicauAdapter
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.model.*
object EducationHelper {
 suspend fun getCourseScheduleByTerm(week:String, academicSemester:String):Resource<CourseScheduleData> = Resource.Error(SicauAdapter.pending())
 suspend fun getParsedCourseScheduleByTerm(week:String, academicSemester:String) = getCourseScheduleByTerm(week,academicSemester)
 suspend fun getCourseGrades(academicYearSemester:String?=null,courseNature:CourseNature?=null,courseName:String="",displayMode:DisplayMode=DisplayMode.BEST_GRADE,studyMode:StudyMode=StudyMode.MAJOR):CourseGradeResponse = CourseGradeResponse("PARSER_PENDING",SicauAdapter.pending())
 suspend fun getAvailableSemestersForCourseGrades():List<String> = throw IllegalStateException(SicauAdapter.pending())
 suspend fun getGradeDetail(url:String):GradeDetailResponse? = throw IllegalStateException(SicauAdapter.pending())
 suspend fun getSemesterStartDate(academicYearSemester:String?=null):String? = null
}

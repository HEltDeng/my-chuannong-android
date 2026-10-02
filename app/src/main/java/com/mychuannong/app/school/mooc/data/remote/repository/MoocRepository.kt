package com.mychuannong.app.school.mooc.data.remote.repository
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.mooc.data.remote.dto.*
import kotlinx.coroutines.flow.flowOf
class MoocRepository private constructor() {
 companion object { val instance by lazy { MoocRepository() } }
 private fun <T> pending() = flowOf<Resource<T>>(Resource.Error("川农教学作业来源及解析适配未完成"))
 fun login(username:String,password:String) = pending<Boolean>()
 fun getLoginUser() = pending<SSOProfile>()
 fun getProfile() = pending<MoocProfile>()
 fun getCourses() = pending<List<MoocCourse>>()
 fun getCourseHomeworks(courseId:String) = pending<List<MoocHomework>>()
 fun getCourseTests(courseId:String) = pending<List<MoocTest>>()
 fun getCourseNamesWithPendingHomeworks() = pending<List<PendingAssignmentCourse>>()
 suspend fun clearMoocLocalSession() { }
}

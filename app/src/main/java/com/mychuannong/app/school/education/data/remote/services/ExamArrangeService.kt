package com.mychuannong.app.school.education.data.remote.services
import com.mychuannong.app.school.*
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
object ExamArrangeService {
 suspend fun getExamArrange(term:String):Resource<List<ExamArrange>> {
  SchoolSyncEngine.sync(false)
  val state=SchoolSyncEngine.items.value.getValue("exams")
  return if(state.phase in setOf(SyncPhase.DATA,SyncPhase.EMPTY)) Resource.Success(ExamArrangementCache().getExamArrangement().orEmpty()) else Resource.Error(state.message)
 }
}

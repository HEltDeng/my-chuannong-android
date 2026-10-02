package com.mychuannong.app.school.education.data.remote.services
import com.mychuannong.app.school.SicauAdapter
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
object ExamArrangeService {
 suspend fun getExamArrange(term:String):Resource<List<ExamArrange>> = Resource.Error(SicauAdapter.pending())
}

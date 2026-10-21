package com.mychuannong.app.feature.common.viewModel

import androidx.lifecycle.viewModelScope
import com.mychuannong.app.core.mvi.MviViewModel
import com.mychuannong.app.core.security.SchoolSessionCoordinator
import com.mychuannong.app.core.security.SchoolSessionResult
import com.mychuannong.app.core.security.SchoolSessionState
import com.mychuannong.app.feature.common.contract.ExamArrangementContract
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
import com.mychuannong.app.feature.common.ui.adapter.model.Exam
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.mychuannong.app.school.education.data.remote.services.ExamArrangeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExamArrangementViewModel :
    MviViewModel<ExamArrangementContract.Intent, ExamArrangementContract.State>() {
    private val cache by lazy { ExamArrangementCache() }
    private val _effect = Channel<ExamArrangementContract.Effect>(Channel.BUFFERED)
    val effect: Flow<ExamArrangementContract.Effect> = _effect.receiveAsFlow()

    override fun initialState(): ExamArrangementContract.State = ExamArrangementContract.State()

    override fun processIntent(intent: ExamArrangementContract.Intent) {
        when (intent) {
            ExamArrangementContract.Intent.InitialLoad -> loadCached()
            is ExamArrangementContract.Intent.LoadExamArrangement -> {
                fetchExamArrangement(intent.termTime)
            }
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null
    private fun loadCached() {
        if(refreshJob?.isActive == true) return
        val item=com.mychuannong.app.school.SchoolSyncEngine.items.value.getValue("exams")
        updateState { copy(exams=cache.getExamArrangement().orEmpty().toUiExamList(), lastSuccess=item.lastSuccess,
            statusMessage=if(item.lastSuccess != null) "显示上次保存的考试安排" else "暂无本地考试安排，可下拉刷新") }
    }
    private fun fetchExamArrangement(termTime: String) {
        if(refreshJob?.isActive == true) return
        val owner=com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId
        val epoch=com.mychuannong.app.school.SicauNativeAuth.webRevision.value
        updateState { copy(isLoading=true) }
        refreshJob=viewModelScope.launch {
            try {
                com.mychuannong.app.school.SchoolSyncEngine.sync(true,refreshLearning=false)
                if(owner != com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId || epoch != com.mychuannong.app.school.SicauNativeAuth.webRevision.value) return@launch
                val item=com.mychuannong.app.school.SchoolSyncEngine.items.value.getValue("exams")
                updateState { copy(exams=cache.getExamArrangement().orEmpty().toUiExamList(),
                    lastSuccess=item.lastSuccess, statusMessage=item.message) }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { updateState { copy(statusMessage="刷新失败，保留上次考试安排") } }
            finally { updateState { copy(isLoading=false) } }
        }
    }

    private fun List<ExamArrange>.toUiExamList(): List<Exam> {
        return mapNotNull { exam ->
            runCatching {
                val courseName = exam.courseNameval
                val examTime = exam.examTime
                val campus = exam.campus
                val examRoom = exam.examRoomval
                if (courseName.isBlank() || examTime.isBlank()) {
                    null
                } else {
                    Exam(courseName, examTime, campus, examRoom)
                }
            }.getOrNull()
        }.distinctBy { listOf(it.name, it.time, it.place, it.room) }
    }
}

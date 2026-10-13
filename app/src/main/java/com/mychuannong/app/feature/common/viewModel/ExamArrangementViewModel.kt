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
            is ExamArrangementContract.Intent.LoadExamArrangement -> {
                fetchExamArrangement(intent.termTime)
            }
        }
    }

    private fun fetchExamArrangement(termTime: String) {
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.sync(true,refreshLearning=false)
            val item = com.mychuannong.app.school.SchoolSyncEngine.items.value.getValue("exams")
            updateState { copy(
                isLoading = false,
                exams = cache.getExamArrangement().orEmpty().toUiExamList(),
                statusMessage = if (item.phase == com.mychuannong.app.school.SyncPhase.EMPTY)
                    "查询成功，学校当前未返回考试安排" else item.message
            ) }
            if(item.phase !in setOf(com.mychuannong.app.school.SyncPhase.DATA, com.mychuannong.app.school.SyncPhase.EMPTY)) {
                _effect.send(ExamArrangementContract.Effect.ShowErrorDialog(item.message))
            }
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

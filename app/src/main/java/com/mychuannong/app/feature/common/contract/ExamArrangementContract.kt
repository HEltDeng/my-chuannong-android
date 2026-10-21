package com.mychuannong.app.feature.common.contract

import com.mychuannong.app.core.mvi.MviIntent
import com.mychuannong.app.core.mvi.MviSideEffect
import com.mychuannong.app.core.mvi.MviViewState
import com.mychuannong.app.feature.common.ui.adapter.model.Exam

interface ExamArrangementContract {
    sealed class Intent : MviIntent {
        data object InitialLoad : Intent()
        data class LoadExamArrangement(val termTime: String) : Intent()
    }

    data class State(
        val exams: List<Exam> = emptyList(),
        val isLoading: Boolean = false,
        val lastSuccess: Long? = null,
        val statusMessage: String = "尚未查询考试安排"
    ) : MviViewState

    sealed class Effect : MviSideEffect {
        data class ShowToast(val message: String) : Effect()
        data class ShowErrorDialog(val message: String) : Effect()
        data class ShowBindingRequired(val message: String) : Effect()
    }
}

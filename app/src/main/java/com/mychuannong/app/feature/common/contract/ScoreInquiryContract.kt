package com.mychuannong.app.feature.common.contract

import com.mychuannong.app.core.mvi.MviIntent
import com.mychuannong.app.core.mvi.MviSideEffect
import com.mychuannong.app.core.mvi.MviViewState
import com.mychuannong.app.feature.common.data.local.entity.Grade

interface ScoreInquiryContract {
    sealed class Intent : MviIntent {
        object InitialLoad : Intent()
        data class RefreshData(val force: Boolean) : Intent()
        data class FetchScoreDetail(val pscjUrl: String, val courseName: String) : Intent()
    }

    data class State(
        val isLoading: Boolean = false,
        val grades: List<Grade> = emptyList(),
        val transcript: com.mychuannong.app.school.SchoolTranscript? = null,
        val phase: com.mychuannong.app.school.SyncPhase = com.mychuannong.app.school.SyncPhase.NOT_SYNCED,
        val message: String = "尚未读取个人成绩单",
        val isCached: Boolean = false
    ) : MviViewState

    sealed class Effect : MviSideEffect {
        data class ShowToast(val message: String) : Effect()
        data class ShowAuthErrorDialog(val title: String, val msg: String) : Effect()
        data class ShowNetErrorDialog(val title: String, val msg: String) : Effect()
        data class ShowDetailDialog(val detail: String, val title: String) : Effect()
    }
}

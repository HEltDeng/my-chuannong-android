package com.mychuannong.app.feature.common.viewModel

import androidx.lifecycle.viewModelScope
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.mvi.MviViewModel
import com.mychuannong.app.core.security.SchoolSessionCoordinator
import com.mychuannong.app.core.security.SchoolSessionResult
import com.mychuannong.app.core.security.SchoolSessionState
import com.mychuannong.app.feature.common.contract.ScoreInquiryContract
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.remote.dto.ScoreDetail
import com.mychuannong.app.school.education.data.remote.EducationHelper
import com.mychuannong.app.school.education.data.remote.model.CourseGrade
import com.mychuannong.app.school.education.data.remote.model.GradeDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScoreInquiryViewModel :
    MviViewModel<ScoreInquiryContract.Intent, ScoreInquiryContract.State>() {

    private val _effect = Channel<ScoreInquiryContract.Effect>(Channel.BUFFERED)
    val effect: Flow<ScoreInquiryContract.Effect> = _effect.receiveAsFlow()

    override fun initialState(): ScoreInquiryContract.State = ScoreInquiryContract.State()

    override fun processIntent(intent: ScoreInquiryContract.Intent) {
        when (intent) {
            is ScoreInquiryContract.Intent.InitialLoad -> loadCachedData()
            is ScoreInquiryContract.Intent.RefreshData -> refreshData(intent.force)
            is ScoreInquiryContract.Intent.FetchScoreDetail -> fetchScoreDetail(
                intent.pscjUrl,
                intent.courseName
            )
        }
    }

    private fun loadCachedData() {
        val cachedGrades = ScoreCache.getGrades()
        if (cachedGrades != null && cachedGrades.isNotEmpty()) {
            updateState { copy(grades = cachedGrades) }
        } else {
            refreshData(force = true)
        }
    }

    private fun refreshData(force: Boolean) {
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.sync(force)
            val item = com.mychuannong.app.school.SchoolSyncEngine.items.value.getValue("grades")
            updateState { copy(isLoading = false, grades = ScoreCache.getGrades().orEmpty()) }
            if(item.phase !in setOf(com.mychuannong.app.school.SyncPhase.DATA, com.mychuannong.app.school.SyncPhase.EMPTY)) {
                sendEffect(ScoreInquiryContract.Effect.ShowNetErrorDialog("成绩尚未同步", item.message))
            }
        }
    }

    private fun fetchScoreDetail(pscjUrl: String, courseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (SchoolSessionCoordinator.instance.ensureReady()) {
                    SchoolSessionResult.MissingCredentials -> {
                        sendEffect(
                            ScoreInquiryContract.Effect.ShowAuthErrorDialog(
                                "需要绑定学号",
                                "学校身份尚未确认。川农成绩解析适配未完成，可在学校页面查询本人数据。"
                            )
                        )
                        return@launch
                    }

                    SchoolSessionResult.TemporarilyUnavailable -> {
                        sendEffect(sessionUnavailableEffect())
                        return@launch
                    }

                    SchoolSessionResult.Ready -> Unit
                }

                var result = EducationHelper.getGradeDetail(pscjUrl)
                if (result?.code == "403") {
                    SchoolSessionState.invalidate()
                    if (SchoolSessionCoordinator.instance.ensureReady(forceRefresh = true) == SchoolSessionResult.Ready) {
                        result = EducationHelper.getGradeDetail(pscjUrl)
                    }
                }
                withContext(Dispatchers.Main) {
                    when (result?.code) {
                        "200" -> {
                            result.data?.let {
                                val detailString = buildScoreDetailString(it.toScoreDetail())
                                ScoreCache.saveGradesDetailByUrl(pscjUrl, detailString)
                                sendEffect(
                                    ScoreInquiryContract.Effect.ShowDetailDialog(
                                        detailString,
                                        courseName
                                    )
                                )
                            }
                        }

                        "403" -> {
                            sendEffect(sessionUnavailableEffect())
                        }

                        "404", null -> {
                            sendEffect(
                                ScoreInquiryContract.Effect.ShowNetErrorDialog(
                                    "查询失败",
                                    "网络连接不稳定，请刷新后重试。"
                                )
                            )
                        }

                        else -> {
                            sendEffect(ScoreInquiryContract.Effect.ShowToast("获取详情失败"))
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    sendEffect(sessionUnavailableEffect())
                }
            }
        }
    }

    private fun sendEffect(effect: ScoreInquiryContract.Effect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    private fun sessionUnavailableEffect() = ScoreInquiryContract.Effect.ShowNetErrorDialog(
        "查询暂不可用",
        "教务会话暂时无法恢复，请稍后刷新重试。"
    )

    // --- Helpers (moved from Store) ---

    private fun buildScoreDetailString(scoreDetail: ScoreDetail): String {
        val contentBuilder = StringBuilder()
        var flag = false
        with(scoreDetail) {
            sjcj?.let {
                flag = true
                contentBuilder.append("上机成绩：$it\n")
                contentBuilder.append("上机成绩比例：$sjcjBL\n\n")
            }
            pscj?.let {
                flag = true
                contentBuilder.append("平时成绩：$it\n")
                contentBuilder.append("平时成绩比例：$pscjBL\n\n")
            }
            qzcj?.let {
                flag = true
                contentBuilder.append("期中成绩：$it\n")
                contentBuilder.append("期中成绩比例：$qzcjBL\n\n")
            }
            qmcj?.let {
                flag = true
                contentBuilder.append("期末成绩：$it\n")
                contentBuilder.append("期末成绩比例：$qmcjBL\n\n")
            }
            contentBuilder.append("总成绩：$score")
        }
        return if (!flag) "暂无平时成绩" else contentBuilder.toString()
    }

    private fun CourseGrade.toGrade(): Grade {
        return Grade(
            id = courseID,
            item = semester,
            name = courseName,
            grade = grade.toString(),
            flag = gradeIdentifier,
            score = credit.toString(),
            timeR = totalHours.toString(),
            point = gradePoint.toString(),
            upperReItem = retakeSemester,
            method = assessmentMethod,
            property = examNature,
            attribute = courseAttribute,
            reItem = groupName,
            pscjUrl = gradeDetailUrl
        )
    }

    private fun GradeDetail.toScoreDetail(): ScoreDetail {
        val componentMap = components.associateBy { it.type }
        return ScoreDetail(
            pscj = componentMap["平时成绩"]?.grade?.toString(),
            pscjBL = componentMap["平时成绩"]?.ratio?.toString(),
            qzcj = componentMap["期中成绩"]?.grade?.toString(),
            qzcjBL = componentMap["期中成绩"]?.ratio?.toString(),
            qmcj = componentMap["期末成绩"]?.grade?.toString(),
            qmcjBL = componentMap["期末成绩"]?.ratio?.toString(),
            sjcj = componentMap["上机成绩"]?.grade?.toString(),
            sjcjBL = componentMap["上机成绩"]?.ratio?.toString(),
            score = totalGrade.toString()
        )
    }
}

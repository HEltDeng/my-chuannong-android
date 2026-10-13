package com.mychuannong.app.overview.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.security.SchoolSessionCoordinator
import com.mychuannong.app.core.security.SchoolSessionResult
import com.mychuannong.app.feature.mooc.data.MoocDataHelper
import com.mychuannong.app.feature.mooc.data.local.MoocLocalCache
import com.mychuannong.app.overview.data.OverviewRepository
import com.mychuannong.app.overview.ui.model.OverviewMetricUiModel
import com.mychuannong.app.overview.ui.model.OverviewUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel : ViewModel() {
    companion object {
        private const val TAG = "OverviewViewModel"
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 分钟
    }

    private val repository by lazy { OverviewRepository(PlanetApplication.appContext) }
    private var loadJob: Job? = null
    private var syncJob: Job? = null

    // 上次网络刷新成功的时间戳
    private var lastRefreshTimeMs: Long = 0L

    private val _uiState = MutableStateFlow(OverviewUiState(todayCourseMessage = "正在读取本地课表…"))
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.revision.collect { load() }
        }
        viewModelScope.launch {
            StudentInfoManager.academicRevision.collect {
                loadJob?.cancel()
                _uiState.value = OverviewUiState(todayCourseMessage = "正在读取本地课表…")
                lastRefreshTimeMs = 0L
                load()
            }
        }
        viewModelScope.launch {
            com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache.revision.collect { load() }
        }
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.items.collect { load() }
        }
    }

    fun refresh() {
        syncIfNeeded(force = true)
    }

    fun onResume() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val owner = StudentInfoManager.studentId
            val identityRevision = StudentInfoManager.academicRevision.value

            val localState = repository.loadLocalState()
            if(owner != StudentInfoManager.studentId || identityRevision != StudentInfoManager.academicRevision.value) return@launch
            val displayState = localState.copy(
                isSilentSyncing = syncJob?.isActive == true,
                metrics = localState.metrics.map { metric ->
                    if(syncJob?.isActive == true && metric.value == "--" &&
                        com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache.getTranscript(owner) == null)
                        metric.copy(subtitle = "正在获取成绩…") else metric
                }
            )
            _uiState.value = displayState

            loadMoocFromCache()

            val shouldRefresh = localState.isBoundStudent && localState.isOnline &&
                (lastRefreshTimeMs == 0L || System.currentTimeMillis() - lastRefreshTimeMs >= REFRESH_INTERVAL_MS)

            if (shouldRefresh) syncIfNeeded(false)
        }
    }

    private fun syncIfNeeded(force: Boolean) {
        if(syncJob?.isActive == true || StudentInfoManager.studentId.isBlank()) return
        lastRefreshTimeMs = System.currentTimeMillis()
        syncJob = viewModelScope.launch {
            val owner = StudentInfoManager.studentId
            _uiState.value = _uiState.value.copy(isRefreshing = force, isSilentSyncing = true)
            try {
                com.mychuannong.app.school.SchoolSyncEngine.sync(force)
                if(owner == StudentInfoManager.studentId &&
                    (force || com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache.getTranscript(owner) == null))
                    com.mychuannong.app.school.SchoolSyncEngine.readTranscript(owner, replaceExisting = force)
            } catch(e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch(_: Exception) {
                // Local storage remains the display source after a failed online attempt.
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false, isSilentSyncing = false)
                load()
            }
        }
    }

    /**
     * 直接从 MMKV 缓存读取慕课数据并更新 UI
     */
    private fun loadMoocFromCache() {
        val courseItems = MoocLocalCache.getCourseItems()
        val homeworks = MoocDataHelper.extractPendingHomeworks(courseItems)
        val tests = MoocDataHelper.extractPendingTests(courseItems)
        val isBound = StudentInfoManager.hasCompleteBinding
        val current = _uiState.value
        _uiState.value = current.copy(
            pendingHomeworks = homeworks,
            pendingHomeworkMessage = when {
                !isBound -> "先绑定学号"
                homeworks.isNotEmpty() -> ""
                else -> "没有数据"
            },
            pendingTests = tests,
            pendingTestMessage = when {
                !isBound -> "先绑定学号"
                tests.isNotEmpty() -> ""
                else -> "没有数据"
            }
        )
    }

    /**
     * 绑定学号后，用 MoocDataHelper 刷新慕课网络数据
     */
    private fun refreshMoocData() {
        val studentId = StudentInfoManager.studentId
        val studentPassword = StudentInfoManager.studentPassword
        if (studentId.isBlank() || studentPassword.isBlank()) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MoocDataHelper.fetchMoocCourses(studentId, studentPassword, forceRefresh = false)
                }
                // 网络刷新成功后重新从缓存读取
                loadMoocFromCache()
            } catch (e: Exception) {
                Log.w(TAG, "慕课数据刷新失败，使用缓存数据", e)
            }
        }
    }

}

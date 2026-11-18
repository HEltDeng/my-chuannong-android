package com.mychuannong.app.overview.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.feature.mooc.data.MoocDataHelper
import com.mychuannong.app.feature.mooc.data.local.MoocLocalCache
import com.mychuannong.app.overview.data.OverviewRepository
import com.mychuannong.app.overview.ui.model.OverviewUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class OverviewViewModel : ViewModel() {

    private val repository by lazy { OverviewRepository(PlanetApplication.appContext) }
    private var loadJob: Job? = null
    private var initialSyncOwner: String? = null

    private val _uiState = MutableStateFlow(repository.initialState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.revision.drop(1).collect { load() }
        }
        viewModelScope.launch {
            StudentInfoManager.academicRevision.drop(1).collect {
                loadJob?.cancel()
                _uiState.value = repository.initialState()
                load()
            }
        }
        viewModelScope.launch {
            com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache.revision.drop(1).collect { load() }
        }
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.items.drop(1).collect { load() }
        }
        viewModelScope.launch {
            com.mychuannong.app.core.security.SchoolAccessGate.state.collect { syncMissingAfterBinding() }
        }
    }

    fun onResume() {
        load()
        syncMissingAfterBinding()
    }

    /** As on iOS, try the missing initial school data once per bound owner. */
    private fun syncMissingAfterBinding() {
        val owner = StudentInfoManager.studentId
        if (owner.isBlank() || !StudentInfoManager.hasCompleteBinding || initialSyncOwner == owner) return
        initialSyncOwner = owner
        viewModelScope.launch {
            try {
                com.mychuannong.app.school.SchoolSyncEngine.syncInitialMissing(owner)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Keep the local view; a manual refresh can retry. */ }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val owner = StudentInfoManager.studentId
            val identityRevision = StudentInfoManager.academicRevision.value

            val localState = withContext(Dispatchers.IO) {
                withMoocCache(repository.loadLocalState())
            }
            if(owner != StudentInfoManager.studentId || identityRevision != StudentInfoManager.academicRevision.value) return@launch
            _uiState.value = localState

            // Opening/resuming the app only reads local data. Network refresh is explicit.
        }
    }

    /**
     * 直接从 MMKV 缓存读取慕课数据并更新 UI
     */
    private fun withMoocCache(current: OverviewUiState): OverviewUiState {
        val courseItems = MoocLocalCache.getCourseItems()
        val homeworks = MoocDataHelper.extractPendingHomeworks(courseItems)
        val tests = MoocDataHelper.extractPendingTests(courseItems)
        val isBound = StudentInfoManager.hasCompleteBinding
        return current.copy(
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

}

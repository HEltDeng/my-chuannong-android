package com.mychuannong.app.feature.timetable.viewmodel

import com.mychuannong.app.core.network.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class CourseRefreshFailure(val message: String, val requiresAuthentication: Boolean)

/** The current request owns both its progress indicator and its completion. */
internal class CourseRefreshCoordinator<T>(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = 60_000,
    private val needsAuthentication: () -> Boolean = { false },
    private val onTimeout: () -> Unit = {},
) {
    private var generation = 0L
    private var job: Job? = null
    private val refreshingMutable = MutableStateFlow(false)
    val refreshing = refreshingMutable.asStateFlow()
    private val responseMutable = MutableStateFlow<ApiResponse<T>?>(null)
    val response = responseMutable.asStateFlow()
    private val failureChannel = Channel<CourseRefreshFailure>(Channel.BUFFERED)
    val failures = failureChannel.receiveAsFlow()

    inner class Request internal constructor(private val id: Long, private val manual: Boolean) {
        fun isCurrent() = id == generation
        fun loading() {
            if (isCurrent()) {
                refreshingMutable.value = true
                responseMutable.value = ApiResponse.Loading()
            }
        }
        suspend fun complete(result: ApiResponse<T>, requiresAuthentication: Boolean = false) {
            if (!isCurrent()) return
            responseMutable.value = result
            if (manual && result is ApiResponse.Error) {
                failureChannel.send(CourseRefreshFailure(result.msg, requiresAuthentication || needsAuthentication()))
            }
        }
    }

    fun start(manual: Boolean, work: suspend Request.() -> Unit) {
        val id = ++generation
        job?.cancel()
        refreshingMutable.value = manual
        val request = Request(id, manual)
        job = scope.launch {
            try {
                val completed = withTimeoutOrNull(timeoutMillis) { request.work(); true } == true
                if (!completed && request.isCurrent()) {
                    onTimeout()
                    request.complete(ApiResponse.Error("学校连接超时，请重试"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                request.complete(ApiResponse.Error("刷新失败，已保留本地数据"))
            } finally {
                if (request.isCurrent()) refreshingMutable.value = false
            }
        }
    }
}

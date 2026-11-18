package com.mychuannong.app.feature.timetable.viewmodel

import com.mychuannong.app.core.network.ApiResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseRefreshCoordinatorTest {
    @Test fun aNewRefreshCancelsTheOldRequestWithoutStoppingItsIndicator() = runBlocking {
        val coordinator = CourseRefreshCoordinator<List<String>>(this, timeoutMillis = 2_000)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coordinator.start(manual = false) { loading(); started.complete(Unit); awaitCancellation() }
        started.await()
        coordinator.start(manual = true) { finish.await(); complete(ApiResponse.Success(listOf("course"))) }
        yield()
        assertTrue(coordinator.refreshing.value)
        finish.complete(Unit)
        withTimeout(2_000) { coordinator.refreshing.first { !it } }
        assertEquals(listOf("course"), (coordinator.response.value as ApiResponse.Success).data)
    }

    @Test fun aStalledRefreshTimesOutAndReportsFailure() = runBlocking {
        var timeouts = 0
        val coordinator = CourseRefreshCoordinator<List<String>>(this, timeoutMillis = 25, onTimeout = { timeouts++ })
        coordinator.start(manual = true) { awaitCancellation() }
        val failure = withTimeout(2_000) { coordinator.failures.first() }
        withTimeout(2_000) { coordinator.refreshing.first { !it } }
        assertEquals("学校连接超时，请重试", failure.message)
        assertFalse(failure.requiresAuthentication)
        assertEquals(1, timeouts)
    }

    @Test fun repeatedIdenticalManualFailuresAreBothDelivered() = runBlocking {
        val coordinator = CourseRefreshCoordinator<List<String>>(this, needsAuthentication = { true })
        repeat(2) {
            coordinator.start(manual = true) { complete(ApiResponse.Error("学校会话已失效")) }
            val failure = withTimeout(2_000) { coordinator.failures.first() }
            assertEquals("学校会话已失效", failure.message)
            assertTrue(failure.requiresAuthentication)
            withTimeout(2_000) { coordinator.refreshing.first { !it } }
        }
    }
}

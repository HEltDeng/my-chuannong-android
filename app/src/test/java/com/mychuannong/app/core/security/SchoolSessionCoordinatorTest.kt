package com.mychuannong.app.core.security

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSessionCoordinatorTest {

    @Test
    fun missingCredentialsNeverStartsAuthentication() = runBlocking {
        val authenticationCount = AtomicInteger(0)
        val coordinator = createCoordinator(
            credentialsProvider = SchoolCredentialsProvider { null },
            authenticator = SchoolSessionAuthenticator {
                authenticationCount.incrementAndGet()
                true
            }
        )

        assertEquals(
            SchoolSessionResult.MissingCredentials,
            coordinator.ensureReady()
        )
        assertEquals(0, authenticationCount.get())
    }

    @Test
    fun explicitBindingInFlightDoesNotStartAutomaticAuthentication() = runBlocking {
        val authenticationCount = AtomicInteger(0)
        val coordinator = createCoordinator(
            authenticator = SchoolSessionAuthenticator {
                authenticationCount.incrementAndGet()
                true
            },
            isExplicitBindingInFlight = { true }
        )

        assertEquals(
            SchoolSessionResult.TemporarilyUnavailable,
            coordinator.ensureReady()
        )
        assertEquals(0, authenticationCount.get())
    }

    @Test
    fun concurrentCallersShareOneAuthenticationAttempt() = runBlocking {
        val authenticationCount = AtomicInteger(0)
        val releaseAuthentication = CompletableDeferred<Unit>()
        val sessionReady = AtomicBoolean(false)
        val coordinator = createCoordinator(
            sessionReady = sessionReady,
            authenticator = SchoolSessionAuthenticator {
                authenticationCount.incrementAndGet()
                releaseAuthentication.await()
                true
            }
        )

        val results = List(8) {
            async(Dispatchers.Default) { coordinator.ensureReady() }
        }
        withTimeout(2_000) {
            while (authenticationCount.get() == 0) yield()
        }
        assertEquals(1, authenticationCount.get())

        releaseAuthentication.complete(Unit)

        assertTrue(results.awaitAll().all { it == SchoolSessionResult.Ready })
        assertEquals(1, authenticationCount.get())
        assertTrue(sessionReady.get())
    }

    @Test
    fun temporaryFailureDoesNotBecomeMissingBindingAndCanRetry() = runBlocking {
        val authenticationCount = AtomicInteger(0)
        val coordinator = createCoordinator(
            authenticator = SchoolSessionAuthenticator {
                authenticationCount.incrementAndGet() >= 2
            }
        )

        assertEquals(
            SchoolSessionResult.TemporarilyUnavailable,
            coordinator.ensureReady()
        )
        assertEquals(SchoolSessionResult.Ready, coordinator.ensureReady())
        assertEquals(2, authenticationCount.get())
    }

    @Test
    fun invalidationDuringAuthenticationCannotMarkOldAttemptReady() = runBlocking {
        val sessionReady = AtomicBoolean(false)
        val generation = AtomicLong(0L)
        val authenticationStarted = CompletableDeferred<Unit>()
        val releaseAuthentication = CompletableDeferred<Unit>()
        val coordinator = createCoordinator(
            sessionReady = sessionReady,
            generation = generation,
            authenticator = SchoolSessionAuthenticator {
                authenticationStarted.complete(Unit)
                releaseAuthentication.await()
                true
            }
        )

        val result = async(Dispatchers.Default) { coordinator.ensureReady() }
        authenticationStarted.await()
        generation.incrementAndGet()
        releaseAuthentication.complete(Unit)

        assertEquals(SchoolSessionResult.TemporarilyUnavailable, result.await())
        assertFalse(sessionReady.get())
    }

    @Test
    fun authenticationFailureDetectionIsConservative() {
        assertTrue(SchoolSessionState.isAuthenticationFailure("403"))
        assertTrue(SchoolSessionState.isAuthenticationFailure("当前会话已失效"))
        assertTrue(SchoolSessionState.isAuthenticationFailure("Unauthorized"))
        assertFalse(SchoolSessionState.isAuthenticationFailure("网络暂时不可用"))
        assertFalse(SchoolSessionState.isAuthenticationFailure(null))
    }

    private fun createCoordinator(
        credentialsProvider: SchoolCredentialsProvider = SchoolCredentialsProvider {
            SchoolSessionCredentials("test-only-id", "test-only-secret")
        },
        authenticator: SchoolSessionAuthenticator = SchoolSessionAuthenticator { true },
        sessionReady: AtomicBoolean = AtomicBoolean(false),
        generation: AtomicLong = AtomicLong(0L),
        isExplicitBindingInFlight: () -> Boolean = { false }
    ): SchoolSessionCoordinator = SchoolSessionCoordinator(
        credentialsProvider = credentialsProvider,
        authenticator = authenticator,
        isSessionReady = sessionReady::get,
        markSessionReady = { sessionReady.set(true) },
        sessionGeneration = generation::get,
        isExplicitBindingInFlight = isExplicitBindingInFlight
    )
}

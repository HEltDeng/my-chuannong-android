package com.mychuannong.app.core.security

import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.services.AuthService
import com.mychuannong.app.school.mooc.data.remote.repository.MoocRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SchoolSessionResult {
    data object MissingCredentials : SchoolSessionResult
    data object Ready : SchoolSessionResult
    data object TemporarilyUnavailable : SchoolSessionResult
}

internal class SchoolSessionCredentials(
    val studentId: String,
    val studentPassword: String
)

internal fun interface SchoolCredentialsProvider {
    fun readCredentials(): SchoolSessionCredentials?
}

internal fun interface SchoolSessionAuthenticator {
    suspend fun authenticate(credentials: SchoolSessionCredentials): Boolean
}

/**
 * Restores the process-local school session without changing the persisted binding.
 * Concurrent callers share one in-flight authentication attempt.
 */
class SchoolSessionCoordinator internal constructor(
    private val credentialsProvider: SchoolCredentialsProvider,
    private val authenticator: SchoolSessionAuthenticator,
    private val isSessionReady: () -> Boolean,
    private val markSessionReady: () -> Unit,
    private val sessionGeneration: () -> Long,
    private val isExplicitBindingInFlight: () -> Boolean
) {
    private val mutex = Mutex()
    private val sessionMutationMutex = Mutex()
    private var inFlight: CompletableDeferred<SchoolSessionResult>? = null

    suspend fun ensureReady(forceRefresh: Boolean = false): SchoolSessionResult {
        if (isExplicitBindingInFlight()) {
            return SchoolSessionResult.TemporarilyUnavailable
        }
        if (credentialsProvider.readCredentials() == null) {
            return SchoolSessionResult.MissingCredentials
        }
        if (!forceRefresh && isSessionReady()) {
            return SchoolSessionResult.Ready
        }

        var ownsAttempt = false
        val attempt = mutex.withLock {
            if (!forceRefresh && isSessionReady()) {
                null
            } else {
                inFlight ?: CompletableDeferred<SchoolSessionResult>().also {
                    inFlight = it
                    ownsAttempt = true
                }
            }
        } ?: return SchoolSessionResult.Ready

        if (ownsAttempt) {
            try {
                val result = sessionMutationMutex.withLock {
                    when {
                        isExplicitBindingInFlight() ->
                            SchoolSessionResult.TemporarilyUnavailable
                        !forceRefresh && isSessionReady() -> SchoolSessionResult.Ready
                        else -> restoreSession()
                    }
                }
                attempt.complete(result)
            } catch (cancellation: CancellationException) {
                attempt.complete(SchoolSessionResult.TemporarilyUnavailable)
                throw cancellation
            } catch (_: Throwable) {
                attempt.complete(SchoolSessionResult.TemporarilyUnavailable)
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (inFlight === attempt) inFlight = null
                    }
                }
            }
        }

        return attempt.await()
    }

    internal suspend fun <T> withExclusiveSessionChange(block: suspend () -> T): T =
        sessionMutationMutex.withLock { block() }

    private suspend fun restoreSession(): SchoolSessionResult {
        val credentials = credentialsProvider.readCredentials()
            ?: return SchoolSessionResult.MissingCredentials
        val generationAtStart = sessionGeneration()
        val authenticated = authenticator.authenticate(credentials)
        if (!authenticated || generationAtStart != sessionGeneration()) {
            return SchoolSessionResult.TemporarilyUnavailable
        }
        markSessionReady()
        return SchoolSessionResult.Ready
    }

    companion object {
        val instance: SchoolSessionCoordinator by lazy {
            SchoolSessionCoordinator(
                credentialsProvider = SchoolCredentialsProvider {
                    StudentInfoManager.credentialsOrNull()?.let {
                        SchoolSessionCredentials(it.studentId, it.studentPassword)
                    }
                },
                authenticator = SchoolSessionAuthenticator { credentials ->
                    val ssoResult = MoocRepository.instance
                        .login(credentials.studentId, credentials.studentPassword)
                        .filter { it !is Resource.Loading }
                        .first()
                    if (ssoResult !is Resource.Success) {
                        false
                    } else {
                        AuthService.login(credentials.studentId, credentials.studentPassword)
                    }
                },
                isSessionReady = { SchoolSessionState.isReady() },
                markSessionReady = { SchoolSessionState.markReady() },
                sessionGeneration = { SchoolSessionState.generation.get() },
                isExplicitBindingInFlight = { SchoolSessionState.explicitBindingInFlight.get() }
            )
        }
    }
}

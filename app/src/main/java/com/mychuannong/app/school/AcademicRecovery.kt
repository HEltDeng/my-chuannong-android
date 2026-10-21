package com.mychuannong.app.school

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Passwords have no printable representation and are never a substitute for a verified owner. */
internal class AcademicCredential(val owner: String, val password: String, val route: SicauJiaowuRoute,
    val failures: Int = 0, val retryAfter: Long = 0, val blocked: Boolean = false) {
    fun attempted(now: Long) = AcademicCredential(owner, password, route, failures + 1, now + 60_000, blocked)
    fun suspended() = AcademicCredential(owner, password, route, failures, retryAfter, true)
    fun recovered() = AcademicCredential(owner, password, route, retryAfter = retryAfter)
}

internal interface AcademicRecoveryPort {
    fun revision(): Long
    fun owner(): String?
    suspend fun restore(): SicauJiaowuSession?
    suspend fun browser(previous: SicauJiaowuSession?): SicauJiaowuSession?
    suspend fun credential(): AcademicCredential?
    suspend fun storeCredential(value: AcademicCredential, epoch: Long)
    suspend fun transport(route: SicauJiaowuRoute, force: Boolean = false)
    fun create(route: SicauJiaowuRoute): SicauJiaowuSession
    suspend fun save(session: SicauJiaowuSession, epoch: Long)
    fun discardInvalidSession(epoch: Long) {}
}

/** One serialized recovery for native reads and web entries. Only idempotent reads are replayed.
 * Network/parse/permission failures never authorize a credential POST. The attempt budget is
 * persisted BEFORE login, so process death or repeated pulls cannot create a login storm. */
internal class AcademicRecovery(private val port: AcademicRecoveryPort, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    suspend fun <T> read(block: suspend (SicauJiaowuSession, String) -> T): T = mutex.withLock {
        val epoch=port.revision()
        try { readLocked(block) }
        catch(e: JiaowuException) {
            if(epoch == port.revision() && (e.reason.invalidatesSession || e.reason in setOf(
                    JiaowuFailure.AUTO_LOGIN_NOT_ENABLED,JiaowuFailure.AUTO_LOGIN_STOPPED))) port.discardInvalidSession(epoch)
            throw e
        }
    }
    private suspend fun <T> readLocked(block: suspend (SicauJiaowuSession, String) -> T): T {
        val epoch = port.revision()
        val owner = port.owner()?.takeIf(String::isNotBlank) ?: throw JiaowuException(JiaowuFailure.SESSION_UNAVAILABLE)
        fun current() {
            if (epoch != port.revision() || owner != port.owner()) throw CancellationException("School session changed")
        }
        suspend fun <R> readThroughVpn(session: SicauJiaowuSession, read: suspend () -> R): R = try { read() }
        catch(e: JiaowuException) {
            if(e.reason != JiaowuFailure.WEBVPN_REQUIRED || !session.jar.route.isWebVpn) throw e
            current(); port.transport(session.jar.route, force = true); current()
            read()
        }
        var recovered = false
        suspend fun recover(previous: SicauJiaowuSession?): SicauJiaowuSession {
            current()
            if (recovered) throw JiaowuException(JiaowuFailure.AUTO_LOGIN_STOPPED)
            recovered = true
            port.browser(previous)?.let { browser ->
                try {
                    port.transport(browser.jar.route)
                    readThroughVpn(browser) { browser.fetchPersonalInfo(owner) }
                    current(); port.save(browser, epoch); current()
                    return browser
                } catch (e: JiaowuException) { if (!e.reason.canRecoverAcademic) throw e }
            }
            val credential = port.credential()?.takeIf { it.owner == owner }
                ?: throw JiaowuException(JiaowuFailure.AUTO_LOGIN_NOT_ENABLED)
            if (credential.blocked || credential.failures >= 2) throw JiaowuException(JiaowuFailure.AUTO_LOGIN_STOPPED)
            if (now() < credential.retryAfter) throw JiaowuException(JiaowuFailure.AUTO_LOGIN_COOLDOWN)
            port.transport(credential.route)
            current()
            val attempted = credential.attempted(now())
            port.storeCredential(attempted, epoch)
            current()
            val session = port.create(credential.route)
            try {
                current()
                session.login(owner, credential.password)
                current(); port.save(session, epoch); current()
                port.storeCredential(attempted.recovered(), epoch)
                return session
            } catch (e: CancellationException) { session.cancel(); throw e }
            catch (e: JiaowuException) {
                current()
                if (e.reason != JiaowuFailure.NETWORK_UNREACHABLE && e.reason != JiaowuFailure.WEBVPN_REQUIRED)
                    port.storeCredential(attempted.suspended(), epoch)
                throw e
            }
        }
        var session = port.restore()
        current()
        if (session != null) {
            try {
                port.transport(session.jar.route)
                readThroughVpn(session) { session!!.fetchPersonalInfo(owner) }
                current(); port.save(session, epoch)
            } catch (e: JiaowuException) {
                if (!e.reason.canRecoverAcademic) throw e
                session = recover(session)
            }
        } else session = recover(null)
        current()
        val result = try { readThroughVpn(session) { block(session!!, owner) } }
        catch (e: JiaowuException) {
            if (!e.reason.canRecoverAcademic || recovered) throw e
            session = recover(session)
            readThroughVpn(session) { block(session!!, owner) }
        }
        current(); port.save(session, epoch); current()
        return result
    }
}

internal val JiaowuFailure.canRecoverAcademic get() = this in setOf(
    JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.SESSION_COOKIE_MISSING, JiaowuFailure.SESSION_UNAVAILABLE)

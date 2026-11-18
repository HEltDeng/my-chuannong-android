package com.mychuannong.app.school

import android.webkit.CookieManager
import com.mychuannong.app.core.PlanetApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** WebVPN access is independent of academic identity and local cached data. */
enum class WebVpnPhase { UNKNOWN, AUTHENTICATING, RESOURCE_FOUND, AVAILABLE, AUTH_REQUIRED, NETWORK_ERROR }

internal interface WebVpnCookieBridge {
    fun get(url: String): String
    fun accept(url: String, setCookie: String)
    fun flush()
    suspend fun acceptAndAwait(url: String, setCookie: String, current: () -> Boolean) {
        if (!current()) throw kotlinx.coroutines.CancellationException()
        accept(url, setCookie)
    }
}

internal object AndroidWebVpnCookies : WebVpnCookieBridge {
    override fun get(url: String) = CookieManager.getInstance().getCookie(url).orEmpty()
    // Null callback works on the OkHttp thread without a Looper. Keep the raw attributes,
    // including HttpOnly, SameSite, domain, path and expiry; never reconstruct a persisted cookie.
    override fun accept(url: String, setCookie: String) = CookieManager.getInstance().setCookie(url, setCookie)
    override fun flush() = CookieManager.getInstance().flush()
    override suspend fun acceptAndAwait(url: String, setCookie: String, current: () -> Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (!current()) throw kotlinx.coroutines.CancellationException()
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                CookieManager.getInstance().setCookie(url, setCookie) { accepted ->
                    if (continuation.isActive) continuation.resumeWith(
                        if (accepted) Result.success(Unit)
                        else Result.failure(JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)))
                }
            }
        }
}

internal object SicauWebVpn {
    private val recoveryMutex = Mutex()
    private var recoveryAt = 0L
    suspend fun ensureAvailable(): Boolean {
        val epoch = SicauNativeAuth.webRevision.value
        return kotlinx.coroutines.withTimeoutOrNull(35_000) { ensureAvailableWithinDeadline() } ?: run {
            schoolDiagnostic("vpn_total=timeout")
            if (epoch == SicauNativeAuth.webRevision.value) move(WebVpnPhase.NETWORK_ERROR)
            false
        }
    }
    private suspend fun ensureAvailableWithinDeadline(): Boolean {
        if (check()) return true
        if (phase.value !in setOf(WebVpnPhase.AUTH_REQUIRED, WebVpnPhase.NETWORK_ERROR)) return false
        return recoveryMutex.withLock {
            if (phase.value == WebVpnPhase.AVAILABLE) return@withLock true
            val now = android.os.SystemClock.elapsedRealtime()
            if (recoveryAt != 0L && now - recoveryAt < 60_000) return@withLock false
            val owner = SicauNativeAuth.sessionOwner().takeIf(String::isNotBlank) ?: return@withLock false
            val epoch = SicauNativeAuth.webRevision.value
            recoveryAt = now
            restoreUnified(owner, epoch)
        }
    }

    /** After a successful 川农 e login, follow the school's SSO before showing any web login. */
    suspend fun connectUnified(owner: String, epoch: Long): Boolean = kotlinx.coroutines.withTimeoutOrNull(35_000) {
        recoveryMutex.withLock {
            if (owner.isBlank() || owner != SicauNativeAuth.sessionOwner() || epoch != SicauNativeAuth.webRevision.value)
                throw kotlinx.coroutines.CancellationException("School session changed")
            restoreUnified(owner, epoch)
        }
    } ?: run {
        schoolDiagnostic("vpn_total=timeout")
        if (epoch == SicauNativeAuth.webRevision.value) move(WebVpnPhase.NETWORK_ERROR)
        false
    }

    private fun browserBroker(current: () -> Boolean) = SchoolSsoBroker(object : SchoolSsoCookies {
        override suspend fun get(url: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (!current()) throw kotlinx.coroutines.CancellationException()
            CookieManager.getInstance().getCookie(url).orEmpty()
        }
        override suspend fun accept(url: String, cookies: List<String>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            for (raw in cookies) {
                if (!current()) throw kotlinx.coroutines.CancellationException()
                SchoolWebSessionStore.setCookie(CookieManager.getInstance(), url, raw)
            }
        }
    })

    suspend fun confirmBrowserOwner(expected: String?, epoch: Long): Boolean {
        val previousOwner = SicauNativeAuth.sessionOwner()
        fun current() = epoch == SicauNativeAuth.webRevision.value && previousOwner == SicauNativeAuth.sessionOwner()
        if (!current()) throw kotlinx.coroutines.CancellationException()
        val broker = browserBroker(::current)
        val proxy = SchoolResourceAccess.authRealm()
        val requiredOwner = previousOwner.takeIf(String::isNotBlank) ?: expected
        val owner = broker.browserOwner(proxy, requiredOwner)
            ?: if (proxy == null) broker.discoverProxyRealm()?.let { discovered ->
                if (!current()) throw kotlinx.coroutines.CancellationException()
                SchoolResourceAccess.saveAuthRealm(discovered)
                broker.browserOwner(discovered, requiredOwner)
            } else null
        if (owner == null) return false
        if (expected != null && owner != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
        synchronized(SicauNativeAuth) {
            if (!current()) throw kotlinx.coroutines.CancellationException()
            SicauNativeAuth.acceptBrowserOwner(owner, epoch)
        }
        return true
    }

    private suspend fun restoreUnified(owner: String, epoch: Long): Boolean {
        fun current() = epoch == SicauNativeAuth.webRevision.value && owner == SicauNativeAuth.sessionOwner()
        return try {
            UnifiedAuthState.move(UnifiedAuthPhase.RECOVERING)
            val broker = browserBroker(::current)
            val restored = kotlinx.coroutines.withTimeoutOrNull(12_000) {
                val proxy = SchoolResourceAccess.authRealm() ?: broker.discoverProxyRealm()?.also {
                    if (!current()) throw kotlinx.coroutines.CancellationException()
                    SchoolResourceAccess.saveAuthRealm(it)
                }
                broker.restoreVpn(owner, proxy)
            } ?: run {
                schoolDiagnostic("vpn_restore=timeout")
                if (current()) move(WebVpnPhase.NETWORK_ERROR)
                return false
            }
            if (!current()) throw kotlinx.coroutines.CancellationException()
            if (!restored) { schoolDiagnostic("vpn_restore=verification"); move(WebVpnPhase.AUTH_REQUIRED) }
            val available = restored && check(force = true)
            if (available) {
                UnifiedAuthState.move(UnifiedAuthPhase.VALID)
                SchoolWebSessions.persistNow(SicauJiaowuRoute.VPN_ORIGIN, epoch)
            } else UnifiedAuthState.move(UnifiedAuthPhase.INTERACTION_REQUIRED)
            available
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            val kind = when (e) {
                is java.net.UnknownHostException -> "dns"
                is javax.net.ssl.SSLException -> "tls"
                is java.net.SocketTimeoutException -> "timeout"
                else -> "connection"
            }
            schoolDiagnostic("vpn_restore=" + kind)
            if (current()) move(WebVpnPhase.NETWORK_ERROR)
            false
        }
    }
    private val checkMutex = Mutex()
    private var checkedAt = 0L
    private val networkMutable = MutableStateFlow(0L)
    val networkRevision = networkMutable.asStateFlow()
    suspend fun check(force: Boolean = false): Boolean = checkMutex.withLock {
        if (!force && state.value == WebVpnPhase.AVAILABLE && android.os.SystemClock.elapsedRealtime() - checkedAt < 30_000) return@withLock true
        WebVpnSessionStore.restoreOnce()
        val epoch = SicauNativeAuth.webRevision.value
        val network = networkRevision.value
        try {
            SchoolWebSessions.prepare(SicauJiaowuRoute.VPN_ORIGIN + "/")
            val result = SicauVpnPortalProbe.check()
            if(epoch != SicauNativeAuth.webRevision.value || network != networkRevision.value) return@withLock false
            schoolDiagnostic("vpn=" + when (result) {
                is PortalProbeResult.Connected -> "connected"
                PortalProbeResult.AuthenticationRequired -> "verification"
                PortalProbeResult.Unavailable -> "unavailable"
            })
            when (result) {
                is PortalProbeResult.Connected -> {
                    discovered(result.route)
                    SchoolResourceAccess.rememberAcademic(result.route)
                    checkedAt = android.os.SystemClock.elapsedRealtime()
                    move(WebVpnPhase.AVAILABLE)
                    WebVpnSessionStore.capture()
                    true
                }
                PortalProbeResult.AuthenticationRequired -> { move(WebVpnPhase.AUTH_REQUIRED); false }
                PortalProbeResult.Unavailable -> { move(WebVpnPhase.NETWORK_ERROR); false }
            }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) { if(epoch == SicauNativeAuth.webRevision.value && network == networkRevision.value) move(WebVpnPhase.NETWORK_ERROR); false }
    }
    private val state = MutableStateFlow(WebVpnPhase.UNKNOWN)
    val phase = state.asStateFlow()
    private val preferences get() = PlanetApplication.appContext.getSharedPreferences("sicau_webvpn_routes", 0)
    fun route(): SicauJiaowuRoute? = preferences.getString("academic_prefix", null)?.let(SicauJiaowuRoute::restore)?.takeIf { it.isWebVpn }
    fun discovered(route: SicauJiaowuRoute) {
        require(route.isWebVpn)
        preferences.edit().putString("academic_prefix", route.prefix).apply()
        move(WebVpnPhase.RESOURCE_FOUND)
    }
    fun move(next: WebVpnPhase) { state.value = next }
    fun forgetRoute() { checkedAt = 0L; recoveryAt = 0L; preferences.edit().clear().apply(); SchoolResourceAccess.clear(); move(WebVpnPhase.UNKNOWN) }
    fun observeNetwork(context: android.content.Context) {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = changed()
            override fun onLost(network: android.net.Network) = changed()
            private fun changed() {
                // Network type is not authentication evidence. The next sync must recheck the
                // actual resource/profile instead of trusting the periodic-refresh throttle.
                move(WebVpnPhase.UNKNOWN)
                networkMutable.update { it + 1 }
                SchoolSyncEngine.networkChanged()
            }
        })
    }
}

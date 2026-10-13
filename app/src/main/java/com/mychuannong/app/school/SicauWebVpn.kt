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
}

internal object AndroidWebVpnCookies : WebVpnCookieBridge {
    override fun get(url: String) = CookieManager.getInstance().getCookie(url).orEmpty()
    // Null callback works on the OkHttp thread without a Looper. Keep the raw attributes,
    // including HttpOnly, SameSite, domain, path and expiry; never reconstruct a persisted cookie.
    override fun accept(url: String, setCookie: String) = CookieManager.getInstance().setCookie(url, setCookie)
    override fun flush() = CookieManager.getInstance().flush()
}

internal object SicauWebVpn {
    private val checkMutex = Mutex()
    @Volatile private var activeCheck: SicauJiaowuSession? = null
    private var checkedAt = 0L
    private val networkMutable = MutableStateFlow(0L)
    val networkRevision = networkMutable.asStateFlow()
    suspend fun check(force: Boolean = false): Boolean = checkMutex.withLock {
        if (!force && state.value == WebVpnPhase.AVAILABLE && android.os.SystemClock.elapsedRealtime() - checkedAt < 30_000) return@withLock true
        WebVpnSessionStore.restoreOnce()
        val epoch = SicauNativeAuth.webRevision.value
        val network = networkRevision.value
        val route = route() ?: run { move(WebVpnPhase.AUTH_REQUIRED); return@withLock false }
        val session = SicauJiaowuSession(SicauJiaowuCookieJar(route))
        activeCheck = session
        try {
            session.checkLoginPage()
            if(epoch != SicauNativeAuth.webRevision.value || network != networkRevision.value) return@withLock false
            checkedAt = android.os.SystemClock.elapsedRealtime()
            move(WebVpnPhase.AVAILABLE)
            WebVpnSessionStore.capture()
            true
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: JiaowuException) {
            if(epoch == SicauNativeAuth.webRevision.value && network == networkRevision.value) move(if(e.reason == JiaowuFailure.WEBVPN_REQUIRED) WebVpnPhase.AUTH_REQUIRED else WebVpnPhase.NETWORK_ERROR)
            false
        } catch(_: Exception) { if(epoch == SicauNativeAuth.webRevision.value && network == networkRevision.value) move(WebVpnPhase.NETWORK_ERROR); false }
        finally { session.cancel(); activeCheck = null }
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
    fun forgetRoute() { activeCheck?.cancel(); checkedAt = 0L; preferences.edit().clear().apply(); SchoolResourceAccess.clear(); move(WebVpnPhase.UNKNOWN) }
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

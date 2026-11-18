package com.mychuannong.app.school

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import com.mychuannong.app.SafeUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest

/** Authentication center for all official website containers. No passwords or form values.
 * A service's own session and the VPN transport remain independent from the central SSO. */
internal object SchoolWebSessions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkMutex = Mutex()
    private var checkedAt = Long.MIN_VALUE
    private var pending: NativeAuthSession? = null
    private var expiryNotice: String? = null
    private val attempts = SchoolSsoAttempts()
    private val browsers = java.util.WeakHashMap<WebView, SchoolSsoAttempts>()
    private class PendingSso(val source: String, val job: Job)
    private val pendingSso = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebView, PendingSso>())
    val revision get() = SicauNativeAuth.webRevision

    fun track(web: WebView) { browsers.getOrPut(web) { SchoolSsoAttempts() } }
    fun cancelSso(web: WebView) { pendingSso.remove(web)?.job?.cancel() }
    fun pageStarted(web: WebView, url: String?) {
        if(pendingSso[web]?.source != url) cancelSso(web)
        url?.let(SchoolResourceAccess::rememberAuthRedirect)
    }
    fun stopBrowsers() {
        browsers.keys.toList().forEach { web -> runCatching {
            cancelSso(web)
            web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(false)
        } }
        browsers.clear()
    }

    suspend fun prepare(url: String) {
        if (!SafeUrl.school(url)) return
        SicauNativeAuth.exportToWebView()
        // The native and website academic client use the exact same academic origin only.
        if (java.net.URI(url.substringBefore('#')).host == "jiaowu.sicau.edu.cn") SicauJiaowuAuth.exportExistingToWebView()
    }

    /** Called before displaying an academic page, so a valid/recoverable session never
     * flashes a login form. Transient failures stay in the service container. */
    suspend fun academicEntry(url: String): SchoolRead<String>? {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: return null
        if (parsed.host != "jiaowu.sicau.edu.cn") return null
        val owner = com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId
        if (owner.isBlank()) return null
        val target = if (url == SicauJiaowuProtocol.LOGIN_PAGE || parsed.encodedPath == "/")
            SicauJiaowuProtocol.PERSONAL_INFO else url
        return SicauJiaowuAuth.webEntry(target, owner)
    }

    fun reuseSso(url: String): String? {
        SchoolResourceAccess.rememberAuthRedirect(url)
        SchoolWebSessionStore.remember(url)
        val target = SchoolSsoNavigation.reuseSession(url, true) ?: return null
        val header = SchoolSsoNavigation.authLocations(url).joinToString(";") {
            CookieManager.getInstance().getCookie(it).orEmpty()
        }
        val tgt = header.split(';').firstOrNull { it.trim().startsWith("go-admin-tgt=") }
            ?.substringAfter('=')?.takeIf(String::isNotBlank)
        // The server owns the SSO decision. A path-scoped/HttpOnly or proxy-managed
        // session need not expose a root TGT to this client. Try the official GET once.
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(
            (tgt ?: "browser-session:${revision.value}").toByteArray()).joinToString("") { "%02x".format(it) }
        return target.takeIf { attempts.claim(url, fingerprint) }
    }

    /** Intercept only an official login GET. Reuse the central session where it was
     * established (including WebVPN), then let the business site consume its own ticket. */
    fun reuseSso(web: WebView, url: String, lifecycleScope: CoroutineScope): Boolean {
        SchoolResourceAccess.rememberAuthRedirect(url)
        SchoolWebSessionStore.remember(url)
        val request = SchoolSsoRequest.parse(url)
        val owner = SicauNativeAuth.sessionOwner()
        if(request == null || owner.isBlank()) {
            val target = reuseSso(url) ?: return false
            web.loadUrl(target); return true
        }
        if(pendingSso[web]?.source == url) return true
        val epoch = revision.value
        // The original URL and casc callback 'choose' are the same attempt.
        val attemptKey = "ticket:" + request.realm + ":" + request.appId + ":" + request.service
        // Separate screens may request the same appid (portal and unified-auth entries).
        // Loop protection belongs to this WebView, not every service opened in the app.
        if(!browsers.getOrPut(web) { SchoolSsoAttempts() }.claim(attemptKey, "bound:$owner:$epoch")) return false
        cancelSso(web)
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            fun current() = epoch == revision.value &&
                owner == SicauNativeAuth.sessionOwner()
            val broker = SchoolSsoBroker(object : SchoolSsoCookies {
                override suspend fun get(url: String) = withContext(Dispatchers.Main) {
                    if(!current()) throw CancellationException()
                    CookieManager.getInstance().getCookie(url).orEmpty()
                }
                override suspend fun accept(url: String, cookies: List<String>) = withContext(Dispatchers.Main) {
                    for(raw in cookies) {
                        if(!current()) throw CancellationException()
                        SchoolWebSessionStore.setCookie(CookieManager.getInstance(), url, raw)
                    }
                }
            })
            try {
                val callback = try { withTimeoutOrNull(12_000) {
                    var proxy = SchoolResourceAccess.authRealm()
                    if(proxy == null && brokerCookiePresent()) {
                        proxy = broker.discoverProxyRealm()
                        if(current() && proxy != null) SchoolResourceAccess.saveAuthRealm(proxy)
                    }
                    if(current() && proxy != null) SchoolWebSessionStore.remember("$proxy/wui/")
                    broker.callback(request, owner, proxy)
                } } catch(e: CancellationException) { throw e }
                catch(_: Exception) { null }
                if(!current()) return@launch
                if (callback != null) UnifiedAuthState.move(UnifiedAuthPhase.VALID)
                persist(url, epoch)
                // Failure/expiry falls back to the real school UI; never fake success.
                val target = callback ?: SchoolSsoNavigation.reuseSession(url, true) ?: url
                pendingSso.remove(web)
                val routed = SchoolSsoNavigation.routeAcademicCallback(target, SicauWebVpn.route())
                web.loadUrl(routed ?: url)
            } finally {
                if(pendingSso[web]?.job == coroutineContext[Job]) pendingSso.remove(web)
            }
        }
        pendingSso[web] = PendingSso(url, job)
        job.start()
        return true
    }
    private fun brokerCookiePresent() = CookieManager.getInstance()
        .getCookie(SicauJiaowuRoute.VPN_ORIGIN + "/").orEmpty().isNotBlank()

    /** Login completion awaits this; Activity finish/process death cannot cut off the save. */
    suspend fun persistNow(url: String?, epoch: Long = revision.value) {
        if (!SafeUrl.school(url.orEmpty()) || epoch != revision.value) return
        SicauNativeAuth.awaitCleared()
        if (epoch != revision.value) return
        SchoolWebSessionStore.restoreOnce()
        if (epoch != revision.value) return
        SchoolWebSessionStore.capture(url, SchoolSsoNavigation.authLocations(url.orEmpty()))
        SicauNativeAuth.importFromWebView()
        if (url?.startsWith(SicauJiaowuRoute.VPN_ORIGIN) == true) WebVpnSessionStore.capture()
    }

    /** Application scope survives Activity finish. Every write is still fenced by logout. */
    fun persist(url: String?, epoch: Long = revision.value) {
        if (!SafeUrl.school(url.orEmpty()) || epoch != revision.value) return
        scope.launch {
            try {
                persistNow(url, epoch)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Current page remains usable; prepare reports restoration failures. */ }
        }
    }

    /** Official GET /cas/login tries the existing TGT, never repeats a credential POST.
     * This is session validation, not an invented refresh API. Apply only server Set-Cookie.
     * If the server does not renew the deadline, tell the user instead of extending it. */
    suspend fun checkExpiry(): String? = checkMutex.withLock {
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (checkedAt != Long.MIN_VALUE && elapsed - checkedAt < SchoolWebSessionPolicy.CHECK_INTERVAL_MS) return@withLock expiryNotice
        val epoch = revision.value
        val before = authCookies() ?: return@withLock null
        val tgt = before.mapNotNull { Cookie.parse(SicauAuthProtocol.ORIGIN.toHttpUrl(), it) }
            .firstOrNull { it.name == "go-admin-tgt" } ?: run { expiryNotice = null; return@withLock null }
        if (!SchoolWebSessionPolicy.needsCheck(tgt.expiresAt, System.currentTimeMillis())) {
            expiryNotice = null; return@withLock null
        }
        checkedAt = elapsed
        val session = NativeAuthSession(SchoolCookieJar().apply { importAuthCookies(before) })
        pending = session
        try {
            val response = session.verify()
            withContext(Dispatchers.Main) {
                // A newer browser login or logout wins over an in-flight check.
                if (epoch != revision.value || authCookies() != before) return@withContext
                if (SicauAuthProtocol.accepted(response)) {
                    for ((url, raw) in session.receivedCookies()) {
                        if (epoch != revision.value) return@withContext
                        SchoolWebSessionStore.setCookie(CookieManager.getInstance(), url, raw)
                    }
                    expiryNotice = null // Lack of a new deadline is not proof that user interaction is required.
                    persist(SicauAuthProtocol.ORIGIN, epoch)
                } else {
                    // An unrecognized/error response is not proof of expiry.
                    expiryNotice = null
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            if (epoch == revision.value) expiryNotice = "暂时无法检查登录有效期，已保留当前会话；请以学校页面提示为准。"
        } finally { session.cancel(); if (pending === session) pending = null }
        expiryNotice
    }

    private suspend fun authCookies(): List<String>? = withContext(Dispatchers.Main) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) null
        else SchoolSsoNavigation.authLocations(SicauAuthProtocol.ORIGIN).flatMap {
            CookieManagerCompat.getCookieInfo(CookieManager.getInstance(), it)
        }.distinct().sorted()
    }

    fun reset() {
        pending?.cancel(); pending = null; attempts.clear(); checkedAt = Long.MIN_VALUE; expiryNotice = null
    }

    /** No navigation or form submission is performed by the expiry observer. */
    fun observe(owner: LifecycleOwner, notice: (String?) -> Unit) {
        val epoch = revision.value
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive && epoch == revision.value) {
                    delay(30_000)
                    val message = checkExpiry()
                    if (epoch == revision.value && message != null) notice(message)
                }
            }
        }
    }

    fun pageFinished(web: WebView, url: String, epoch: Long, notice: (String?) -> Unit) {
        if (epoch != revision.value || !SafeUrl.school(url)) return
        persist(url, epoch)
        val kind = SchoolWebSessionPolicy.loginKind(url) ?: run { notice(null); return }
        // Observe only whether a login control is visible; never inspect its value.
        web.postDelayed({
            if (epoch == revision.value && web.url == url) web.evaluateJavascript("""
                (function(){return Array.from(document.querySelectorAll('input[type=password],input[autocomplete=one-time-code]'))
                    .some(function(e){return e.getBoundingClientRect().width>0 && e.getBoundingClientRect().height>0;});})();
            """.trimIndent()) { visible ->
                if (epoch == revision.value && web.url == url && visible == "true") {
                    if (kind == SchoolLoginKind.VPN) SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                    else if (kind == SchoolLoginKind.SSO) UnifiedAuthState.move(UnifiedAuthPhase.INTERACTION_REQUIRED)
                    notice(kind.message)
                }
            }
        }, 600)
    }

}

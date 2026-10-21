package com.mychuannong.app.school

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

/** Store full browser attributes, never synthesize cookies from a Cookie request header. */
internal object WebVpnCookiePolicy {
    fun valid(url: String, raw: String, now: Long = System.currentTimeMillis()): Cookie? {
        val target = url.toHttpUrlOrNull() ?: return null
        if (target.scheme != "https" || target.host != "webvpn.sicau.edu.cn") return null
        return SchoolWebCookiePolicy.valid(url, raw, now)
    }
    fun key(cookie: Cookie) = listOf(cookie.name,cookie.domain,cookie.path).joinToString("\n")
    fun durable(url: String, raw: String): String? {
        val cookie = valid(url,raw) ?: return null
        // Max-Age is relative: keep its original absolute deadline on subsequent restores.
        if (!Regex(";\\s*max-age=",RegexOption.IGNORE_CASE).containsMatchIn(raw)) return raw
        val expiry = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
            java.time.Instant.ofEpochMilli(cookie.expiresAt).atZone(java.time.ZoneOffset.UTC))
        return raw.replace(Regex(";\\s*(max-age|expires)=[^;]*",RegexOption.IGNORE_CASE),"") + "; Expires=$expiry"
    }
}

internal object WebVpnSessionStore {
    private val mutex = Mutex()
    private var restored = false
    private val store get() = LearningCacheKeys.store("sicau_webvpn_session_v1")
    private fun urls(): List<String> = listOf("https://webvpn.sicau.edu.cn/") +
        SicauWebVpn.route()?.let { route ->
            listOf(SicauJiaowuProtocol.LOGIN_PAGE,SicauJiaowuProtocol.PERSONAL_INFO).map(route::url) + JiaowuPage.entries.map { route.url(it.url) }
        }.orEmpty()

    suspend fun restoreOnce() = mutex.withLock {
        val modern = SchoolWebSessionStore.hasSnapshot()
        SchoolWebSessionStore.restoreOnce()
        if (restored) return@withLock
        val epoch = SicauNativeAuth.webRevision.value
        // One-time migration of the former encrypted VPN-only vault. Once a unified
        // snapshot exists, an old vault must never resurrect cookies deleted by the server.
        val saved = withContext(Dispatchers.IO) {
            if(modern) JSONArray() else runCatching { JSONArray(store.decodeString("cookies","[]")) }.getOrElse { JSONArray() }
        }
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) return@withContext
            val live = urls().flatMap { url -> CookieManagerCompat.getCookieInfo(manager,url).mapNotNull { WebVpnCookiePolicy.valid(url,it)?.let(WebVpnCookiePolicy::key) } }.toSet()
            for (i in 0 until saved.length()) {
                if (epoch != SicauNativeAuth.webRevision.value) return@withContext
                val item = saved.optJSONObject(i) ?: continue
                val url = item.optString("url"); val raw = item.optString("cookie")
                val cookie = WebVpnCookiePolicy.valid(url,raw) ?: continue
                if (WebVpnCookiePolicy.key(cookie) in live) continue
                SchoolWebSessionStore.setCookie(manager,url,raw)
            }
            manager.flush()
        }
        if(epoch != SicauNativeAuth.webRevision.value) return@withLock
        SchoolWebSessionStore.capture(additionalLocations=urls() + (0 until saved.length()).mapNotNull {
            saved.optJSONObject(it)?.optString("url")
        })
        withContext(Dispatchers.IO) { synchronized(SicauNativeAuth) {
            if(epoch == SicauNativeAuth.webRevision.value) { store.clearAll(); restored=true }
        } }
    }

    suspend fun capture() {
        restoreOnce()
        SchoolWebSessionStore.capture(additionalLocations=urls())
    }

    @Synchronized fun clear() { restored = true;store.clearAll() }
}

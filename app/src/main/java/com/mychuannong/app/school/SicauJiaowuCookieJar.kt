package com.mychuannong.app.school

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Exact HTTPS destination gate is applied in addition to normal RFC cookie matching. */
internal class SicauJiaowuCookieJar(
    var route: SicauJiaowuRoute = SicauJiaowuRoute.Direct,
    private val bridge: WebVpnCookieBridge = AndroidWebVpnCookies
) : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    private val attributes = mutableMapOf<String, String>()
    // Native responses provide complete attributes even on WebViews without GET_COOKIE_INFO.
    // Only this encrypted academic vault may persist these values, never request headers.
    private val proxyCookies = linkedMapOf<String, Pair<String, String>>()
    private fun allowed(url: HttpUrl) = url.scheme == "https" && url.host == "jiaowu.sicau.edu.cn"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (route.isWebVpn) return // Raw Set-Cookie attributes are handled by the network interceptor.
        if (!allowed(url)) return
        cookies.filter { it.name.startsWith("ASPSESSIONID", ignoreCase = true) &&
            it.domain in setOf("jiaowu.sicau.edu.cn", "sicau.edu.cn") }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            val key = SchoolWebCookiePolicy.key(cookie)
            val original = attributes[key]?.let { Cookie.parse(url, it) }
            // HTTP-date has second precision while OkHttp parses Max-Age in milliseconds.
            // Keep the raw SameSite/HttpOnly attributes captured by the network interceptor.
            val same = original != null && original.name == cookie.name && original.value == cookie.value &&
                original.domain == cookie.domain && original.path == cookie.path && original.hostOnly == cookie.hostOnly &&
                original.secure == cookie.secure && original.httpOnly == cookie.httpOnly &&
                kotlin.math.abs(original.expiresAt - cookie.expiresAt) < 1000
            if (!same) attributes[key] = cookie.toString()
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (route.isWebVpn) {
            if (!route.allows(url)) return emptyList()
            // Outgoing values only. Chromium performs the actual expiry/domain/path selection.
            return bridge.get(url.toString()).split(';').mapNotNull { Cookie.parse(url, it.trim() + "; Path=/; Secure") }
        }
        if (!allowed(url)) return emptyList()
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    // For WebVPN this only proves transport cookies exist, never an academic login.
    fun hasSession(url: String) = loadForRequest(route.url(url).toHttpUrl()).isNotEmpty()
    fun acceptRawCookies(url: HttpUrl, headers: List<String>) {
        if (route.isWebVpn && route.allows(url)) synchronized(this) {
            headers.forEach { raw ->
                val cookie = Cookie.parse(url, raw) ?: return@forEach
                val key = SchoolWebCookiePolicy.key(cookie)
                proxyCookies.remove(key)
                val durable = SchoolWebCookiePolicy.durable(url.toString(), raw)
                if(durable != null) proxyCookies[key] =
                    url.newBuilder().query(null).fragment(null).build().toString() to durable
                bridge.accept(url.toString(), raw)
            }
        }
        if (!route.isWebVpn && allowed(url)) synchronized(this) {
            headers.forEach { raw ->
                val cookie = Cookie.parse(url, raw) ?: return@forEach
                if (!cookie.name.startsWith("ASPSESSIONID", true) || cookie.domain !in setOf(url.host, "sicau.edu.cn")) return@forEach
                val scope = url.newBuilder().encodedPath(cookie.path).build().toString()
                val durable = SchoolWebCookiePolicy.durable(scope, raw)
                if(durable == null) {
                    cookies.removeAll { SchoolWebCookiePolicy.key(it) == SchoolWebCookiePolicy.key(cookie) }
                    attributes.remove(SchoolWebCookiePolicy.key(cookie))
                } else {
                    saveFromResponse(url, listOf(cookie))
                    attributes[SchoolWebCookiePolicy.key(cookie)] = durable
                }
            }
        }
    }
    /** Native login must observe each Set-Cookie before it reads the next page's fields.
     * CookieManager.setCookie with a null callback only schedules the Chromium write. */
    suspend fun acceptResponseCookies(url: HttpUrl, headers: List<String>, revision: Long) {
        if (!route.isWebVpn || !route.allows(url)) return
        for (raw in headers) {
            if (Cookie.parse(url, raw) == null) continue
            bridge.acceptAndAwait(url.toString(), raw) { revision == SicauNativeAuth.webRevision.value }
            synchronized(this) {
                if (revision != SicauNativeAuth.webRevision.value) throw kotlinx.coroutines.CancellationException()
                val cookie = Cookie.parse(url, raw) ?: return@synchronized
                val key = SchoolWebCookiePolicy.key(cookie)
                proxyCookies.remove(key)
                SchoolWebCookiePolicy.durable(url.toString(), raw)?.let {
                    proxyCookies[key] = url.newBuilder().query(null).fragment(null).build().toString() to it
                }
            }
        }
    }
    @Synchronized fun clear() { cookies.clear(); attributes.clear(); proxyCookies.clear() }
    /** Join asynchronous legacy restoration before the first native request. */
    suspend fun awaitRestoredCookies(revision: Long) {
        if (!route.isWebVpn) return
        val saved = synchronized(this) { proxyCookies.values.toList() }
        for ((url, raw) in saved) {
            val cookie = SchoolWebCookiePolicy.valid(url, raw) ?: continue
            val live = bridge.get(url).split(';').map(String::trim).filter { it.substringBefore('=') == cookie.name }
            if (live.isEmpty() || live.all { it.substringAfter('=') == cookie.value })
                bridge.acceptAndAwait(url, raw) { revision == SicauNativeAuth.webRevision.value }
        }
    }
    @Synchronized fun rawForRequest(url: HttpUrl): List<String> = loadForRequest(url).mapNotNull { attributes[SchoolWebCookiePolicy.key(it)] }
    @Synchronized fun encode(): String = if (route.isWebVpn) {
        bridge.flush()
        "@webvpn:" + route.prefix + proxyCookies.values.filter { (url, raw) ->
            SchoolWebCookiePolicy.valid(url, raw) != null
        }.joinToString("") { (url, raw) -> "\n$url\t$raw" }
    } else cookies.filter { it.expiresAt > System.currentTimeMillis() }.joinToString("\n") { attributes[SchoolWebCookiePolicy.key(it)] ?: it.toString() }
    @Synchronized fun restore(encoded: String) {
        clear()
        if (encoded.startsWith("@webvpn:")) {
            route = requireNotNull(SicauJiaowuRoute.restore(encoded.lineSequence().first().removePrefix("@webvpn:"))).also { require(it.isWebVpn) }
            encoded.lineSequence().drop(1).take(128).forEach { record ->
                val source = record.substringBefore('\t').let { runCatching { it.toHttpUrl() }.getOrNull() } ?: return@forEach
                val raw = record.substringAfter('\t', "")
                if(!route.allows(source)) return@forEach
                val cookie = SchoolWebCookiePolicy.valid(source.toString(), raw) ?: return@forEach
                // Preserve a newer browser login. Rehydrate only missing same-scope names.
                val live = bridge.get(source.toString()).split(';').map(String::trim)
                    .filter { it.substringBefore('=') == cookie.name }
                if(live.isEmpty()) bridge.accept(source.toString(), raw)
                if(live.isEmpty() || live.all { it.substringAfter('=') == cookie.value })
                    proxyCookies[SchoolWebCookiePolicy.key(cookie)] = source.toString() to raw
            }
            bridge.flush()
            return
        }
        route = SicauJiaowuRoute.Direct
        val origin = SicauJiaowuProtocol.ORIGIN.toHttpUrl()
        // Parse against a URL within each cookie's path; loadForRequest still enforces path matching.
        encoded.lines().forEach { raw ->
            val cookie = Cookie.parse(origin, raw) ?: return@forEach
            acceptRawCookies(origin.newBuilder().encodedPath(cookie.path).build(), listOf(raw))
        }
    }
}

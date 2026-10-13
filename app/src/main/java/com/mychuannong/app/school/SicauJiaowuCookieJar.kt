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
    private fun allowed(url: HttpUrl) = url.scheme == "https" && url.host == "jiaowu.sicau.edu.cn"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (route.isWebVpn) return // Raw Set-Cookie attributes are handled by the network interceptor.
        if (!allowed(url)) return
        cookies.filter { it.name.startsWith("ASPSESSIONID", ignoreCase = true) &&
            it.domain in setOf("jiaowu.sicau.edu.cn", "sicau.edu.cn") }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
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
        if (route.isWebVpn && route.allows(url)) headers.forEach { bridge.accept(url.toString(), it) }
    }
    @Synchronized fun clear() { cookies.clear() }
    @Synchronized fun encode(): String = if (route.isWebVpn) {
        bridge.flush()
        "@webvpn:" + route.prefix
    } else cookies.joinToString("\n") { it.toString() }
    @Synchronized fun restore(encoded: String) {
        clear()
        if (encoded.startsWith("@webvpn:")) {
            route = requireNotNull(SicauJiaowuRoute.restore(encoded.removePrefix("@webvpn:"))).also { require(it.isWebVpn) }
            return
        }
        route = SicauJiaowuRoute.Direct
        val origin = SicauJiaowuProtocol.ORIGIN.toHttpUrl()
        // Parse against a URL within each cookie's path; loadForRequest still enforces path matching.
        encoded.lines().mapNotNull { Cookie.parse(origin, it) }.forEach {
            saveFromResponse(origin.newBuilder().encodedPath(it.path).build(), listOf(it))
        }
    }
}

package com.mychuannong.app.school

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Exact HTTPS destination gate is applied in addition to normal RFC cookie matching. */
internal class SicauJiaowuCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    private fun allowed(url: HttpUrl) = url.scheme == "https" && url.host == "jiaowu.sicau.edu.cn"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!allowed(url)) return
        cookies.filter { it.name.startsWith("ASPSESSIONID", ignoreCase = true) &&
            it.domain in setOf("jiaowu.sicau.edu.cn", "sicau.edu.cn") }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!allowed(url)) return emptyList()
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    fun hasSession(url: String) = loadForRequest(url.toHttpUrl()).isNotEmpty()
    @Synchronized fun clear() { cookies.clear() }
    @Synchronized fun encode(): String = cookies.joinToString("\n") { it.toString() }
    @Synchronized fun restore(encoded: String) {
        clear()
        val origin = SicauJiaowuProtocol.ORIGIN.toHttpUrl()
        // Parse against a URL within each cookie's path; loadForRequest still enforces path matching.
        encoded.lines().mapNotNull { Cookie.parse(origin, it) }.forEach {
            saveFromResponse(origin.newBuilder().encodedPath(it.path).build(), listOf(it))
        }
    }
}

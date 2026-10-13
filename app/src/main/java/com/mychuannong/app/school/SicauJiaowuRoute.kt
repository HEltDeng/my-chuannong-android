package com.mychuannong.app.school

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A prefix learned from an actual official resource page, never generated from a hostname. */
internal class SicauJiaowuRoute private constructor(val prefix: String) {
    val isWebVpn get() = prefix.isNotEmpty()
    val origin get() = if (isWebVpn) VPN_ORIGIN else SicauJiaowuProtocol.ORIGIN

    fun url(direct: String): String {
        val u = direct.toHttpUrl()
        require(u.host == "jiaowu.sicau.edu.cn" && u.scheme == "https" && u.port == 443 && u.username.isEmpty() && u.password.isEmpty())
        return if (!isWebVpn) direct else origin + prefix + u.encodedPath + (u.encodedQuery?.let { "?$it" } ?: "")
    }

    fun allows(url: HttpUrl): Boolean = url.scheme == "https" && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && if (isWebVpn)
        url.host == VPN_HOST && url.encodedPath.startsWith("$prefix/")
        else url.host == "jiaowu.sicau.edu.cn"

    /** Only permit the known academic submit endpoint, whether relative, direct or rewritten. */
    fun loginAction(action: String, page: String): String {
        if (action.isBlank()) return url(SicauJiaowuProtocol.LOGIN_POST)
        if (action.startsWith("/") && !action.startsWith("//") &&
            action.substringBefore('?') == SicauJiaowuProtocol.LOGIN_POST.toHttpUrl().encodedPath)
            return url(SicauJiaowuProtocol.ORIGIN + action)
        val resolved = page.toHttpUrl().resolve(action) ?: throw JiaowuException(JiaowuFailure.LOGIN_PAGE_PARSE_FAILED)
        val candidate = if (resolved.host == "jiaowu.sicau.edu.cn") url(resolved.toString()) else resolved.toString()
        val u = candidate.toHttpUrl()
        if (!allows(u) || u.encodedPath != url(SicauJiaowuProtocol.LOGIN_POST).toHttpUrl().encodedPath)
            throw JiaowuException(JiaowuFailure.LOGIN_PAGE_PARSE_FAILED)
        return candidate
    }

    companion object {
        const val VPN_HOST = "webvpn.sicau.edu.cn"
        const val VPN_ORIGIN = "https://webvpn.sicau.edu.cn"
        val Direct = SicauJiaowuRoute("")
        fun fromResourcePage(value: String): SicauJiaowuRoute? {
            val u = value.toHttpUrlOrNull() ?: return null
            if (u.scheme != "https" || u.host != VPN_HOST || u.port != 443 || u.username.isNotEmpty() || u.password.isNotEmpty()) return null
            // Shape observed in the school's injected WebVPN URL. No token is inferred or hardcoded.
            val match = Regex("^/((?:https|http)(?:-[0-9]+)?)/([0-9a-f]{32,256})/").find(u.encodedPath) ?: return null
            return SicauJiaowuRoute(match.value.removeSuffix("/"))
        }
        fun restore(prefix: String): SicauJiaowuRoute? = if (prefix.isEmpty()) Direct else
            fromResourcePage("$VPN_ORIGIN$prefix/")?.takeIf { it.prefix == prefix }
    }
}

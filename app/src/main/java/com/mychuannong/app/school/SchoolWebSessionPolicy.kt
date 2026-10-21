package com.mychuannong.app.school

/** An unknown deadline is not an expired session. Only the official server decides validity. */
internal object SchoolWebSessionPolicy {
    const val RENEW_WINDOW_MS = 120_000L
    const val CHECK_INTERVAL_MS = 60_000L
    fun needsCheck(expiresAt: Long, now: Long): Boolean = expiresAt - now <= RENEW_WINDOW_MS

    fun loginKind(url: String): SchoolLoginKind? {
        val target = SchoolWebCookiePolicy.location(url)?.let { java.net.URI(it) } ?: return null
        return when {
            target.host == "auth.sicau.edu.cn" && target.path.startsWith("/wui/") -> SchoolLoginKind.SSO
            target.host == "webvpn.sicau.edu.cn" &&
                (target.path == "/login" || target.path.startsWith("/wui/")) -> SchoolLoginKind.VPN
            else -> null
        }
    }
}

internal enum class SchoolLoginKind(val message: String) {
    SSO("请在学校页面完成一次川农 e 统一认证，支持统一认证的其他服务将复用本次登录。"),
    VPN("校园 VPN 需要重新认证，请完成当前 VPN 登录；已保存的教务身份和其他会话会保留。"),
}

/** Bound automatic redirects per browser session, including SPA history callbacks. */
internal class SchoolSsoAttempts {
    private var fingerprint: String? = null
    private val attempted = mutableMapOf<String, Long>()
    @Synchronized fun claim(url: String, session: String, now: Long = System.nanoTime() / 1_000_000): Boolean {
        if (fingerprint != session) { fingerprint = session; attempted.clear() }
        // A later business-session expiry must be able to reuse the same still-valid TGT.
        // Keep a short cooldown for repeated SPA callbacks, not a process-lifetime ban.
        attempted.entries.removeAll { now - it.value >= SchoolWebSessionPolicy.CHECK_INTERVAL_MS }
        if (attempted.size >= 64 || url in attempted) return false
        attempted[url] = now
        return true
    }
    @Synchronized fun clear() { fingerprint = null; attempted.clear() }
}

/** Retry only a safe official entry GET. Never replay payment/forms or treat 403 as expiry. */
internal class SchoolServiceRecovery {
    private var attempted = false
    fun entry(status: Int, method: String, failedUrl: String, initialUrl: String): String? {
        if (attempted || status != 401 || method != "GET" ||
            SchoolWebCookiePolicy.location(initialUrl) == null || SchoolWebCookiePolicy.location(failedUrl) == null) return null
        if (SchoolWebSessionPolicy.loginKind(failedUrl) != null) return null
        val target = when (java.net.URI(failedUrl.substringBefore('#')).host) {
            "webvpn.sicau.edu.cn" -> SicauJiaowuRoute.VPN_ORIGIN + "/"
            "op.sicau.edu.cn" -> "https://op.sicau.edu.cn/CasLogin.aspx"
            "ywtb.sicau.edu.cn" -> SicauAdapter.AUTH
            "jiaowu.sicau.edu.cn" -> SicauJiaowuProtocol.LOGIN_PAGE
            "xsgz.sicau.edu.cn" -> "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"
            else -> return null
        }
        attempted = true
        return target
    }
}

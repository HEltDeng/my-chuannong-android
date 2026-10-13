package com.mychuannong.app.school

import java.net.URI
import java.net.URLDecoder

/** Official /wui/ uses casc callback 'choose' to try the existing SSO session. */
internal object SchoolSsoNavigation {
    // A CAS callback alone is not a completed business landing.
    fun isLoginDestination(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host == "ywtb.sicau.edu.cn" &&
            u.userInfo == null && u.port in listOf(-1,443) &&
            u.path in listOf("", "/", "/index.html") && u.rawQuery == null &&
            (u.rawFragment.isNullOrBlank() || u.rawFragment == "/" || u.rawFragment == "/index")
    }.getOrDefault(false)

    fun entry(target: String): String =
        if(target == "https://ywtb.sicau.edu.cn/") SicauAdapter.AUTH else target

    fun reuseSession(url: String, hasSession: Boolean): String? = runCatching {
        if(!hasSession) return null
        val u = URI(url)
        if(u.scheme != "https" || u.host != "auth.sicau.edu.cn" ||
            u.userInfo != null || u.port !in listOf(-1,443) || u.path != "/wui/") return null
        val fragment = u.rawFragment ?: return null
        if(fragment.substringBefore('?') !in listOf("", "/")) return null
        val params = fragment.substringAfter('?', "").split('&').map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8")
        }
        if("appid" !in params || "service" !in params || "cascallback" in params) return null
        url + "&cascallback=choose"
    }.getOrNull()
}

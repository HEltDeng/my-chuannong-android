package com.mychuannong.app.school

import com.mychuannong.app.core.PlanetApplication
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Resource mappings come only from links rendered by the authenticated official portal. */
internal object SchoolResourceAccess {
    private val hosts = setOf("ehall.sicau.edu.cn", "jiaowu.sicau.edu.cn", "op.sicau.edu.cn", "ywtb.sicau.edu.cn", "xsgz.sicau.edu.cn", "auth.sicau.edu.cn")
    private val prefs get() = PlanetApplication.appContext.getSharedPreferences("sicau_resource_routes", 0)
    fun clear() = prefs.edit().clear().apply()
    fun resolve(direct: String): String? {
        val u = direct.toHttpUrlOrNull() ?: return null
        if (u.host !in hosts || u.scheme != "https" || u.port != 443 || u.username.isNotEmpty() || u.password.isNotEmpty()) return null
        val prefix = prefs.getString(u.host, null) ?: return null
        if (SicauJiaowuRoute.restore(prefix)?.isWebVpn != true) return null
        return SicauJiaowuRoute.VPN_ORIGIN + prefix + u.encodedPath + (u.encodedQuery?.let { "?$it" } ?: "") +
            (u.encodedFragment?.let { "#$it" } ?: "")
    }
    fun capture(web: android.webkit.WebView, attempt: Int = 0, done: () -> Unit = {}) {
        val current = web.url?.toHttpUrlOrNull() ?: return
        if (current.host != SicauJiaowuRoute.VPN_HOST || current.encodedPath !in setOf("/", "/portal", "/m/portal")) return
        web.evaluateJavascript("""JSON.stringify(Array.from(document.querySelectorAll('a[href]')).map(a=>({label:a.innerText,href:a.href})))""") { encoded ->
            if (web.url?.toHttpUrlOrNull()?.let { it.host == current.host && it.encodedPath == current.encodedPath } != true) {
                if(attempt < 5) web.postDelayed({ capture(web, attempt + 1, done) }, 1000)
                return@evaluateJavascript
            }
            var found = false
            runCatching {
                val json = com.google.gson.Gson().fromJson(encoded, String::class.java)
                val links = org.json.JSONArray(json)
                val edit = prefs.edit()
                for (i in 0 until links.length()) {
                    val link = links.getJSONObject(i)
                    val label = link.optString("label").trim().split(Regex("\\s+"))
                    val host = hosts.singleOrNull { it in label } ?: continue
                    val route = SicauJiaowuRoute.fromResourcePage(link.optString("href")) ?: continue
                    edit.putString(host, route.prefix)
                    found = true
                }
                edit.apply()
            }
            // The official portal renders resource cards after the document load event.
            if (!found && attempt < 5) web.postDelayed({ capture(web, attempt + 1, done) }, 1000)
            else done()
        }
    }
}

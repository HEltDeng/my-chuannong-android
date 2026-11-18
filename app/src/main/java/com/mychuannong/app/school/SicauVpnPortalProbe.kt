package com.mychuannong.app.school

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.view.View
import com.mychuannong.app.core.PlanetApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

internal sealed class PortalProbeResult {
    class Connected(val route: SicauJiaowuRoute) : PortalProbeResult()
    data object AuthenticationRequired : PortalProbeResult()
    data object Unavailable : PortalProbeResult()
}

/** Decide from the rendered, authenticated VPN resources, never from an academic login form. */
internal object PortalInspection {
    fun connectedRoute(url: String, needsVerification: Boolean, links: List<Pair<String, String>>): SicauJiaowuRoute? {
        val address = url.toHttpUrlOrNull() ?: return null
        if (needsVerification || address.scheme != "https" || address.host != SicauJiaowuRoute.VPN_HOST ||
            address.port != 443 || address.username.isNotEmpty() || address.password.isNotEmpty() ||
            address.encodedPath !in setOf("/", "/portal", "/m/portal")) return null
        return links.firstNotNullOfOrNull { (label, href) ->
            if ("jiaowu.sicau.edu.cn" !in label.trim().split(Regex("\\s+"))) null
            else SicauJiaowuRoute.fromResourcePage(href)
        }
    }
}

/** Uses a hidden official WebView so dynamic resource cards and the real cookie store are checked. */
internal object SicauVpnPortalProbe {
    private const val script = """(function(){return JSON.stringify({verification:!!document.querySelector('input[type=password],input[autocomplete=one-time-code],input[name=captcha],input[name=otp]'),links:Array.from(document.querySelectorAll('a[href]')).slice(0,400).map(a=>({label:(a.innerText||'').slice(0,512),href:a.href}))});})()"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun check(): PortalProbeResult = withContext(Dispatchers.Main) {
        withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<PortalProbeResult> { continuation ->
                val web = WebView(PlanetApplication.appContext)
                val handler = Handler(Looper.getMainLooper())
                SchoolWebSessions.track(web)
                var finished = false
                var generation = 0
                fun clean() { handler.removeCallbacksAndMessages(null); web.stopLoading(); web.destroy() }
                fun complete(result: PortalProbeResult) {
                    if (finished) return
                    finished = true
                    clean()
                    if (continuation.isActive) continuation.resume(result)
                }
                // This WebView is intentionally unattached; View.post callbacks can remain
                // queued forever. Use the main looper for retries and cancellation cleanup.
                continuation.invokeOnCancellation { handler.post { if (!finished) { finished = true; clean() } } }
                web.settings.apply {
                    javaScriptEnabled = true; domStorageEnabled = true
                    allowFileAccess = false; allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
                // Give an unattached WebView a viewport so the portal's responsive
                // resource cards are rendered and their labels can be inspected.
                web.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                web.layout(0, 0, 1080, 1920)
                fun inspect(page: Int, url: String, attempt: Int) {
                    if (finished || page != generation || web.url != url) return
                    web.evaluateJavascript(script) { encoded ->
                        if (finished || page != generation || web.url != url) return@evaluateJavascript
                        val result = runCatching {
                            val json = com.google.gson.Gson().fromJson(encoded, String::class.java)
                            JSONObject(json)
                        }.getOrNull()
                        val verification = result?.optBoolean("verification") == true
                        val links = result?.optJSONArray("links")?.let { array ->
                            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let {
                                it.optString("label") to it.optString("href")
                            } }
                        }.orEmpty()
                        val route = PortalInspection.connectedRoute(url, verification, links)
                        when {
                            route != null -> complete(PortalProbeResult.Connected(route))
                            verification -> complete(PortalProbeResult.AuthenticationRequired)
                            attempt < 8 -> handler.postDelayed({ inspect(page, url, attempt + 1) }, 900)
                            android.net.Uri.parse(url).let { address ->
                                address.host == "auth.sicau.edu.cn" || address.host == SicauJiaowuRoute.VPN_HOST &&
                                    (address.path.orEmpty().endsWith("/wui/") || address.path.orEmpty() in setOf("/wui", "/login"))
                            } -> complete(PortalProbeResult.AuthenticationRequired)
                            else -> { schoolDiagnostic("vpn_probe=no_resource"); complete(PortalProbeResult.Unavailable) }
                        }
                    }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val url = request.url
                        val allowed = url.scheme == "https" && url.host in setOf(SicauJiaowuRoute.VPN_HOST, "auth.sicau.edu.cn") &&
                            url.port in setOf(-1, 443) && url.userInfo == null
                        if (!allowed) { schoolDiagnostic("vpn_probe=blocked_redirect"); complete(PortalProbeResult.Unavailable) }
                        return !allowed
                    }
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { generation++ }
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (!finished && url != null && url == web.url) inspect(generation, url, 0)
                    }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel(); schoolDiagnostic("vpn_probe=tls"); complete(PortalProbeResult.Unavailable)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame && request.url.toString().substringBefore('#') == web.url?.substringBefore('#')) {
                            schoolDiagnostic("vpn_probe=web_error_" + error.errorCode); complete(PortalProbeResult.Unavailable)
                        }
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                        if (request.isForMainFrame && error.statusCode >= 400 && request.url.toString().substringBefore('#') == web.url?.substringBefore('#')) {
                            schoolDiagnostic("vpn_probe=http_" + error.statusCode); complete(PortalProbeResult.Unavailable)
                        }
                    }
                }
                web.loadUrl(SicauJiaowuRoute.VPN_ORIGIN + "/")
            }
        } ?: PortalProbeResult.Unavailable.also { schoolDiagnostic("vpn_probe=timeout") }
    }
}

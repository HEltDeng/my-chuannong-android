package com.mychuannong.app.school

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.SafeUrl
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryPageHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The actual school UI handles its own SMS/password challenge. No credential injection,
 * JavaScript bridge, page extraction, or automatic credential/verification submission. */
class SchoolLoginVerificationActivity : ComposeActivity() {
    companion object { const val EXTRA_ROUTE = "academic_route" }
    private lateinit var web: WebView
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private val revision = SicauNativeAuth.webRevision.value

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent.getStringExtra(EXTRA_ROUTE)?.let(SicauJiaowuRoute::restore)
            ?: run { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WebView.setWebContentsDebuggingEnabled(false)
        web = WebView(this).apply {
            SchoolWebSessions.track(this)
            if (android.os.Build.VERSION.SDK_INT >= 26)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true; loadWithOverviewMode = true
                builtInZoomControls = true; displayZoomControls = false
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?) = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if (!SchoolVerificationPolicy.allowed(url)) {
                        error = "请在学校教务或认证页面完成验证"
                        return true
                    }
                    if (route.isWebVpn && request.url.host == "jiaowu.sicau.edu.cn") {
                        // Keep off-campus navigation on the already selected VPN route.
                        // Never replay or redirect a credential POST.
                        if (request.method == "GET") view.loadUrl(route.url(url))
                        else error = "学校页面连接已变化，请重新打开验证页面"
                        return true
                    }
                    return SchoolWebSessions.reuseSso(view, url, lifecycleScope)
                }
                override fun onPageStarted(view: WebView, url: String?, icon: Bitmap?) {
                    loading = true; error = null
                    SchoolWebSessions.pageStarted(view, url)
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    CookieManager.getInstance().flush()
                    if (url != null) SchoolWebSessions.pageFinished(view, url, revision) { }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) { loading = false; error = "学校页面连接失败，请检查网络后重新打开" }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) { loading = false; error = "学校页面暂时不可用，请稍后重试" }
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                    handler.cancel(); loading = false; error = "学校页面证书校验失败"
                }
                override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                    dontResend.sendToTarget()
                    error = "请重新打开学校页面继续验证"
                }
            }
        }
        setComposeContent {
            BackHandler { leave() }
            Surface(color = AppTheme.colors.bgPrimaryColor) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SecondaryPageHeader("学校登录验证", ::leave) }
                    Text("请在学校页面完成登录及短信验证。完成后点击下方按钮继续绑定。",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                    Button(onClick = {
                        web.stopLoading()
                        CookieManager.getInstance().flush()
                        // The caller performs a fresh protected profile/owner check.
                        setResult(RESULT_OK); finish()
                    }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !loading && error == null) {
                        Text("完成验证，继续绑定")
                    }
                }
            }
        }
        lifecycleScope.launch {
            try { SchoolWebSessions.prepare(SicauJiaowuProtocol.LOGIN_PAGE) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { loading = false; error = "学校会话暂时无法恢复，请重新打开验证页面"; return@launch }
            if (revision != SicauNativeAuth.webRevision.value) { finish(); return@launch }
            web.loadUrl(route.url(SicauJiaowuProtocol.LOGIN_PAGE))
        }
        lifecycleScope.launch { SicauNativeAuth.webRevision.collect {
            if (it != revision) { web.stopLoading(); finish() }
        } }
    }
    private fun leave() { if (::web.isInitialized) web.stopLoading(); finish() }
    override fun onDestroy() {
        if (::web.isInitialized) { SchoolWebSessions.cancelSso(web); web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}

internal object SchoolVerificationPolicy {
    fun allowed(url: String): Boolean = SafeUrl.school(url) &&
        url.toHttpUrlOrNull()?.host in setOf("jiaowu.sicau.edu.cn", "webvpn.sicau.edu.cn", "auth.sicau.edu.cn")
}

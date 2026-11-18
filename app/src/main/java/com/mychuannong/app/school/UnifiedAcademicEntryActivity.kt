package com.mychuannong.app.school

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.SafeUrl
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Follow the school's own unified entry, then verify the exact protected student profile. */
class UnifiedAcademicEntryActivity : ComposeActivity() {
    companion object {
        const val EXTRA_SILENT = "silent_academic_recovery"
        const val RESULT_VERIFICATION_REQUIRED = RESULT_FIRST_USER + 1
    }
    private lateinit var web: WebView
    private var showSchoolPage by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var navigation = 0
    private var committing = false
    private var entryJob: Job? = null
    private var proofJob: Job? = null
    private var watchdog: Job? = null
    private val silent get() = intent.getBooleanExtra(EXTRA_SILENT, false)
    private val epoch = SicauNativeAuth.webRevision.value
    private val owner = SicauNativeAuth.sessionOwner()
    private var route: SicauJiaowuRoute? = null

    private fun current() = !isFinishing && !isDestroyed && epoch == SicauNativeAuth.webRevision.value &&
        owner.isNotBlank() && owner == SicauNativeAuth.sessionOwner()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route = SicauWebVpn.route()?.takeIf { it.isWebVpn }
        if (owner.isBlank() || route == null) { setResult(RESULT_VERIFICATION_REQUIRED); finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WebView.setWebContentsDebuggingEnabled(false)
        web = WebView(this).apply {
            visibility = View.INVISIBLE
            SchoolWebSessions.track(this)
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true; loadWithOverviewMode = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() { override fun onConsoleMessage(message: ConsoleMessage?) = true }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if (!SafeUrl.school(url)) { fail("学校认证页面暂不可用，请重试。"); return true }
                    if (request.url.host == "jiaowu.sicau.edu.cn") {
                        if (request.method != "GET") { fail("学校页面连接已变化，请重试。"); return true }
                        view.loadUrl(requireNotNull(route).url(url)); return true
                    }
                    return request.method == "GET" && SchoolWebSessions.reuseSso(view, url, lifecycleScope)
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (!current() || message != null) return
                    if (committing) { view.stopLoading(); return }
                    val resumedAfterUserVerification = showSchoolPage
                    navigation++
                    proofJob?.cancel()
                    showSchoolPage = false; view.visibility = View.INVISIBLE
                    SchoolWebSessions.pageStarted(view, url)
                    if (resumedAfterUserVerification) armWatchdog()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (!current() || message != null || url == null || !SafeUrl.school(url)) return
                    if (SchoolWebSessions.reuseSso(view, url, lifecycleScope)) return
                    CookieManager.getInstance().flush()
                    SchoolWebSessions.pageFinished(view, url, epoch) { }
                    inspectForVerification(view, url)
                    verifyProfile(navigation)
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel(); fail("学校页面证书校验失败。")
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fail("学校页面连接失败，请检查网络后重试。")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) fail("学校页面暂时不可用，请稍后重试。")
                }
                override fun onFormResubmission(view: WebView, dontResend: android.os.Message, resend: android.os.Message) { dontResend.sendToTarget() }
            }
        }
        setComposeContent {
            BackHandler { finish() }
            Scaffold(containerColor = Color.White, topBar = { SecondaryScaffoldHeader("学校验证", ::finish) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                    if (!showSchoolPage) Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (message == null) {
                            CircularProgressIndicator(color = AppTheme.colors.functionalTextColor)
                            Spacer(Modifier.height(20.dp)); Text("正在完成学校身份核验…")
                        } else {
                            Text(message.orEmpty()); Spacer(Modifier.height(20.dp))
                            Button(onClick = ::startEntry) { Text("重试") }
                        }
                    }
                }
            }
        }
        startEntry()
    }

    private fun startEntry() {
        if (!current()) return
        navigation++; proofJob?.cancel(); entryJob?.cancel(); message = null; showSchoolPage = false; committing = false
        val attempt = navigation
        armWatchdog()
        web.visibility = View.INVISIBLE
        entryJob = lifecycleScope.launch {
            try {
                SchoolWebSessions.prepare(SicauJiaowuProtocol.LOGIN_PAGE)
                if (current() && attempt == navigation && message == null)
                    web.loadUrl(requireNotNull(route).url(SicauJiaowuProtocol.ORIGIN + "/jiaoshi/aspsso/caslogin.asp"))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current()) fail("学校会话暂时无法恢复，请重试。") }
        }
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = lifecycleScope.launch {
            delay(35_000)
            if (current() && !showSchoolPage) fail("学校身份核验超时，请检查网络后重试。")
        }
    }

    private fun inspectForVerification(view: WebView, url: String) {
        view.evaluateJavascript("(function(){return !!document.querySelector('input[type=password],input[autocomplete=one-time-code],input[name=captcha],input[name=otp]');})()") { result ->
            if (current() && view.url == url && result == "true") {
                val address = android.net.Uri.parse(url)
                val isUnified = address.host == "auth.sicau.edu.cn" || address.path.orEmpty().contains("/wui/")
                if (silent) { setResult(RESULT_VERIFICATION_REQUIRED); finish() }
                else if (isUnified) { watchdog?.cancel(); showSchoolPage = true; view.visibility = View.VISIBLE }
                else fail("学校统一认证尚未完成，请重试。")
            }
        }
    }

    private fun verifyProfile(page: Int) {
        if (!current() || proofJob?.isActive == true || message != null) return
        proofJob = lifecycleScope.launch {
            delay(500) // Allow the school's redirects and cookie writes to settle.
            repeat(4) { attempt ->
                if (!current() || page != navigation || showSchoolPage) return@launch
                val academic = SicauJiaowuSession(SicauJiaowuCookieJar(requireNotNull(route)))
                try {
                    academic.fetchPersonalInfo(owner)
                    if (!current() || page != navigation) throw CancellationException()
                    committing = true
                    web.stopLoading()
                    SchoolSyncEngine.bindJiaowu(academic, expectedRevision = epoch)
                    if (!current()) throw CancellationException()
                    watchdog?.cancel()
                    setResult(RESULT_OK); finish(); return@launch
                } catch (e: CancellationException) { academic.cancel(); throw e }
                catch (e: JiaowuException) {
                    academic.cancel()
                    if (!current() || page != navigation) return@launch
                    if (e.reason == JiaowuFailure.IDENTITY_MISMATCH) {
                        fail("学校返回的学号与当前账号不一致，尚未绑定。"); return@launch
                    }
                    if (e.reason in setOf(JiaowuFailure.NETWORK_UNREACHABLE, JiaowuFailure.PERSONAL_INFO_HTTP_ERROR)) {
                        fail("学校身份暂时无法核验，请检查网络后重试。"); return@launch
                    }
                    if (attempt == 3) {
                        if (silent && (e.reason.requiresLogin || e.reason == JiaowuFailure.WEBVPN_REQUIRED || e.reason.supportsSchoolVerification)) {
                            setResult(RESULT_VERIFICATION_REQUIRED); finish()
                        } else fail("学校统一认证尚未完成，请重试。")
                        return@launch
                    }
                    delay(1_000)
                } catch (_: Exception) {
                    academic.cancel(); if (current()) fail("学校身份暂时无法核验，请重试。"); return@launch
                }
            }
        }
    }

    private fun fail(text: String) {
        if (!current()) return
        navigation++
        watchdog?.cancel()
        entryJob?.cancel()
        proofJob?.cancel()
        if (::web.isInitialized) { SchoolWebSessions.cancelSso(web); web.stopLoading() }
        committing = false
        if (silent) { setResult(RESULT_FIRST_USER, intent.putExtra("school_error", text)); finish() }
        else message = text
    }

    override fun onDestroy() {
        entryJob?.cancel(); proofJob?.cancel(); watchdog?.cancel()
        if (::web.isInitialized) { SchoolWebSessions.cancelSso(web); web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}

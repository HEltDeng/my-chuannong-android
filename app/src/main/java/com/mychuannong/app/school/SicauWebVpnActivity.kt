package com.mychuannong.app.school

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.*

/** Official authentication stays in WebView; resource discovery stays behind a native screen. */
class SicauWebVpnActivity : ComposeActivity() {
    private lateinit var web: WebView
    private var authenticationVisible by mutableStateOf(false)
    private var failure by mutableStateOf<String?>(null)
    private var checking = false
    private var generation = 0
    private var pageUrl: String? = null
    private var capturedGeneration = -1
    private val openedRevision = SicauNativeAuth.webRevision.value
    private var attemptedRoute: String? = null
    private var entryJob: Job? = null
    private var verificationJob: Job? = null
    private var watchdog: Job? = null
    private var inspectionJob: Job? = null
    private fun current(epoch: Int) = epoch == generation && !isFinishing && !isDestroyed &&
        openedRevision == SicauNativeAuth.webRevision.value
    private fun reuseSso(url: String) = SchoolWebSessions.reuseSso(web, url, lifecycleScope)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SicauWebVpn.move(WebVpnPhase.AUTHENTICATING)
        web = WebView(this).apply {
            visibility = View.INVISIBLE
            isEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener { _, _ -> !authenticationVisible }
            SchoolWebSessions.track(this)
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true; loadWithOverviewMode = true
                builtInZoomControls = true; displayZoomControls = false
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)
        web.webChromeClient = object : WebChromeClient() { override fun onConsoleMessage(message: ConsoleMessage?) = true }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = request.url.scheme == "https" && request.url.host in setOf(SicauJiaowuRoute.VPN_HOST, "auth.sicau.edu.cn") &&
                    request.url.port in setOf(-1, 443) && request.url.userInfo == null
                if (request.isForMainFrame) {
                    if (!authenticationVisible && request.hasGesture()) return true
                    conceal()
                    if (!allowed) { showFailure(); return true }
                    if (request.method == "GET" && reuseSso(request.url.toString())) return true
                }
                return !allowed
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                SchoolWebSessions.pageStarted(view, url)
                navigationStarted(url)
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != pageUrl) navigationStarted(url)
                if (url == null || !WebVpnPagePresentation.isAuthentication(url)) conceal()
                val epoch = generation
                view.postDelayed({
                    if (current(epoch) && web.url == url && failure == null && (url == null || !reuseSso(url))) discoverPortal()
                }, 300)
            }
            override fun onPageCommitVisible(view: WebView, url: String?) { inspectAuthentication(url) }
            override fun onPageFinished(view: WebView, url: String?) {
                if (failure != null || isFinishing || isDestroyed) return
                if (url != null && reuseSso(url)) { conceal(); return }
                CookieManager.getInstance().flush()
                if (url != null) SchoolWebSessions.pageFinished(web, url, openedRevision) { }
                inspectAuthentication(url)
                discoverPortal()
                discover()
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (error.url.substringBefore('#') == pageUrl?.substringBefore('#')) showFailure()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (isCurrentDocument(request)) showFailure()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (isCurrentDocument(request) && response.statusCode >= 400) showFailure()
            }
            override fun onFormResubmission(view: WebView, dontResend: android.os.Message, resend: android.os.Message) { dontResend.sendToTarget() }
        }
        setComposeContent {
            BackHandler { leave() }
            Scaffold(containerColor = AppTheme.colors.bgPrimaryColor, topBar = {
                com.mychuannong.app.core.theme.SecondaryScaffoldHeader(CampusLoginCopy.vpnTitle, ::leave)
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                    if (!authenticationVisible) {
                        Column(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (failure == null) {
                                CircularProgressIndicator(color = AppTheme.colors.commonColor)
                                Spacer(Modifier.height(20.dp))
                                Text("正在验证校园 VPN，请稍候……", color = AppTheme.colors.primaryTextColor,
                                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            } else {
                                Text(failure.orEmpty(), color = AppTheme.colors.primaryTextColor)
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = ::connect, colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.commonColor)) {
                                    Text("重新连接")
                                }
                            }
                        }
                    }
                }
            }
        }
        connect()
        lifecycleScope.launch { SicauNativeAuth.webRevision.collect { if (it != openedRevision) { leave() } } }
    }

    private fun isCurrentDocument(request: WebResourceRequest) = request.isForMainFrame &&
        request.url.toString().substringBefore('#') == pageUrl?.substringBefore('#')

    /** Synchronous native hiding prevents a rendered portal frame or touch before recomposition. */
    private fun conceal() {
        authenticationVisible = false
        web.visibility = View.INVISIBLE
        web.isEnabled = false
        web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (web.hasFocus()) {
            web.clearFocus()
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(web.windowToken, 0)
        }
    }

    private fun navigationStarted(url: String?) {
        conceal()
        generation++
        pageUrl = url
        verificationJob?.cancel(); checking = false
        inspectionJob?.cancel()
        if (failure == null) startWatchdog()
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        val epoch = generation
        watchdog = lifecycleScope.launch {
            delay(35_000)
            if (current(epoch) && !authenticationVisible && failure == null) showFailure()
        }
    }

    private fun inspectAuthentication(url: String?) {
        if (url == null || url != web.url || checking || failure != null || isFinishing || isDestroyed) return
        val epoch = generation
        fun reveal() {
            web.postVisualStateCallback(epoch.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!current(epoch) || web.url != url || checking || failure != null) return
                    authenticationVisible = true
                    web.visibility = View.VISIBLE
                    web.isEnabled = true
                    web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    watchdog?.cancel()
                }
            })
        }
        if (WebVpnPagePresentation.isAuthentication(url)) { reveal(); return }
        if (!WebVpnPagePresentation.mayContainRootLogin(url)) return
        if (inspectionJob?.isActive == true) return
        inspectionJob = lifecycleScope.launch {
            while (current(epoch) && web.url == url && !checking && failure == null) {
                web.evaluateJavascript(WebVpnPagePresentation.rootLoginGuard) { result ->
                    if (!current(epoch) || web.url != url || checking || failure != null) return@evaluateJavascript
                    if (result == "true") reveal()
                    else if (authenticationVisible) {
                        conceal(); capturedGeneration = -1; startWatchdog(); discoverPortal()
                    }
                }
                delay(400)
            }
        }
    }

    private fun connect() {
        entryJob?.cancel(); verificationJob?.cancel(); watchdog?.cancel(); inspectionJob?.cancel()
        SchoolWebSessions.cancelSso(web)
        web.stopLoading()
        generation++; pageUrl = null; attemptedRoute = null; checking = false; failure = null
        conceal(); startWatchdog()
        val epoch = generation
        SicauWebVpn.move(WebVpnPhase.AUTHENTICATING)
        entryJob = lifecycleScope.launch {
            try {
                // Keep the existing official cookie restore and native resource verification.
                SchoolWebSessions.prepare(SicauJiaowuRoute.VPN_ORIGIN)
                if (!current(epoch)) return@launch
                if (SicauWebVpn.check()) {
                    if (current(epoch) && failure == null) { setResult(RESULT_OK); finish() }
                } else if (current(epoch) && failure == null) {
                    SicauWebVpn.move(WebVpnPhase.AUTHENTICATING)
                    web.loadUrl(SicauJiaowuRoute.VPN_ORIGIN + "/")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current(epoch)) showFailure() }
        }
    }

    private fun showFailure() {
        if (isFinishing || isDestroyed) return
        conceal()
        failure = "校园 VPN 验证失败，请检查网络后重新连接。"
        watchdog?.cancel(); inspectionJob?.cancel(); verificationJob?.cancel(); entryJob?.cancel()
        SchoolWebSessions.cancelSso(web)
        web.stopLoading()
        checking = false
        SicauWebVpn.move(WebVpnPhase.NETWORK_ERROR)
    }

    private fun discoverPortal() {
        if (!current(generation) || checking || failure != null || capturedGeneration == generation) return
        val url = android.net.Uri.parse(web.url.orEmpty())
        if (url.host != SicauJiaowuRoute.VPN_HOST || url.path !in setOf("/", "/portal", "/m/portal")) return
        val epoch = generation
        capturedGeneration = epoch
        SchoolResourceAccess.capture(web) {
            if (!current(epoch) || failure != null || authenticationVisible) return@capture
            val route = SchoolResourceAccess.resolve(SicauJiaowuProtocol.LOGIN_PAGE)?.let(SicauJiaowuRoute::fromResourcePage)
            if (route != null && attemptedRoute != route.prefix) {
                attemptedRoute = route.prefix; verify(route, epoch)
            }
        }
    }

    private fun discover() {
        if (checking || failure != null || isFinishing || android.net.Uri.parse(web.url.orEmpty()).host != SicauJiaowuRoute.VPN_HOST) return
        val epoch = generation
        web.evaluateJavascript("""(function(){
            function find(w,depth){try {
                if(depth>3 || w.location.hostname!=='webvpn.sicau.edu.cn')return '';
                var d=w.document;
                if(d.querySelector('input[name=sign]') && d.querySelector('input[name=hour_key]') && d.querySelector('input[name=pwd]'))return w.location.href;
                for(var i=0;i<w.frames.length;i++){var u=find(w.frames[i],depth+1);if(u)return u;}
            }catch(e){}return '';}
            return find(window,0);
        })();""") { result ->
            if (!current(epoch) || checking || failure != null) return@evaluateJavascript
            val url = runCatching { com.google.gson.Gson().fromJson(result, String::class.java) }.getOrNull().orEmpty()
            val route = SicauJiaowuRoute.fromResourcePage(url) ?: return@evaluateJavascript
            verify(route, epoch)
        }
    }

    private fun verify(route: SicauJiaowuRoute, epoch: Int) {
        if (checking || !current(epoch) || failure != null) return
        val network = SicauWebVpn.networkRevision.value
        checking = true
        conceal(); inspectionJob?.cancel()
        verificationJob = lifecycleScope.launch {
            val session = SicauJiaowuSession(SicauJiaowuCookieJar(route))
            try {
                session.checkLoginPage()
                if (!current(epoch) || network != SicauWebVpn.networkRevision.value) return@launch
                SicauWebVpn.discovered(route)
                WebVpnSessionStore.capture()
                if (!current(epoch) || network != SicauWebVpn.networkRevision.value) return@launch
                SicauWebVpn.move(WebVpnPhase.AVAILABLE)
                UnifiedAuthState.move(UnifiedAuthPhase.VALID)
                CookieManager.getInstance().flush()
                SchoolWebSessions.persistNow(web.url ?: SicauJiaowuRoute.VPN_ORIGIN, openedRevision)
                if (!current(epoch) || network != SicauWebVpn.networkRevision.value) return@launch
                setResult(RESULT_OK); finish()
            } catch (e: CancellationException) { throw e }
            catch (e: JiaowuException) {
                if (current(epoch)) {
                    showFailure()
                    SicauWebVpn.move(if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) WebVpnPhase.AUTH_REQUIRED else WebVpnPhase.NETWORK_ERROR)
                }
            } catch (_: Exception) { if (current(epoch)) showFailure() }
            finally { session.cancel(); if (epoch == generation) checking = false }
        }
    }

    override fun onPause() { if (::web.isInitialized) { CookieManager.getInstance().flush(); SchoolWebSessions.persist(web.url, openedRevision) }; super.onPause() }
    private fun leave() {
        generation++
        entryJob?.cancel(); verificationJob?.cancel(); watchdog?.cancel(); inspectionJob?.cancel()
        SchoolWebSessions.cancelSso(web)
        conceal(); web.stopLoading(); finish()
    }
    override fun onDestroy() {
        generation++
        if (::web.isInitialized) { SchoolWebSessions.cancelSso(web); web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}

package com.mychuannong.app.school

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Official authentication stays in WebView. Only a resource URL is read, never form values. */
class SicauWebVpnActivity : ComposeActivity() {
    private lateinit var web: WebView
    private var message by mutableStateOf(CampusLoginCopy.vpnDescription)
    private var checking by mutableStateOf(false)
    private var generation = 0
    private val openedRevision = SicauNativeAuth.webRevision.value
    private var attemptedRoute: String? = null
    private val attemptedSso = mutableSetOf<String>()
    private fun reuseSso(url: String): Boolean {
        val header = CookieManager.getInstance().getCookie(SicauAuthProtocol.ORIGIN).orEmpty()
        val hasSession = header.split(';').any { it.trim().startsWith("go-admin-tgt=") && it.substringAfter('=').isNotBlank() }
        val target = SchoolSsoNavigation.reuseSession(url, hasSession) ?: return false
        if(!attemptedSso.add(url)) return false
        web.loadUrl(target); return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SicauWebVpn.move(WebVpnPhase.AUTHENTICATING)
        web = WebView(this).apply {
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if(request.isForMainFrame && reuseSso(request.url.toString())) return true
                val allowed = request.url.scheme == "https" && request.url.host in setOf(SicauJiaowuRoute.VPN_HOST, "auth.sicau.edu.cn") &&
                    request.url.port in setOf(-1, 443) && request.url.userInfo == null
                if (!allowed && request.isForMainFrame) message = "登录失败，请检查账号、密码或网络连接。"
                return !allowed
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { generation++ }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                view.postDelayed({ if(!isFinishing && !isDestroyed) { if(url==null || !reuseSso(url)) discoverPortal() } }, 300)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if(url!=null && reuseSso(url)) return
                CookieManager.getInstance().flush()
                discoverPortal()
                discover()
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); message = CampusLoginCopy.failure
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { message = CampusLoginCopy.failure; SicauWebVpn.move(WebVpnPhase.NETWORK_ERROR) }
            }
            override fun onFormResubmission(view: WebView, dontResend: android.os.Message, resend: android.os.Message) { dontResend.sendToTarget() }
        }
        setComposeContent {
            BackHandler { leave() }
            Scaffold(containerColor = AppTheme.colors.bgPrimaryColor, topBar = {
                com.mychuannong.app.core.theme.SecondaryScaffoldHeader(CampusLoginCopy.vpnTitle, ::leave)
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    if(message == CampusLoginCopy.failure) TextButton(onClick = {
                        attemptedRoute = null; message = CampusLoginCopy.vpnDescription
                        web.loadUrl(SicauJiaowuRoute.VPN_ORIGIN + "/")
                    }) { Text("重试") }
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        // First navigation is always the official entry. A saved mapping alone is not a valid session.
        lifecycleScope.launch {
            SicauNativeAuth.exportToWebView()
            if(openedRevision != SicauNativeAuth.webRevision.value) { finish(); return@launch }
            if(SicauWebVpn.check()) { setResult(RESULT_OK); finish() }
            else { SicauWebVpn.move(WebVpnPhase.AUTHENTICATING); web.loadUrl(SicauJiaowuRoute.VPN_ORIGIN + "/") }
        }
        lifecycleScope.launch { SicauNativeAuth.webRevision.collect { if(it != openedRevision) { web.stopLoading(); finish() } } }
    }

    private fun discoverPortal() {
        if(isFinishing || isDestroyed) return
        SchoolResourceAccess.capture(web) {
            val route = SchoolResourceAccess.resolve(SicauJiaowuProtocol.LOGIN_PAGE)?.let(SicauJiaowuRoute::fromResourcePage)
            if(route != null && attemptedRoute != route.prefix && !isFinishing && !isDestroyed) {
                attemptedRoute = route.prefix; verify(route, generation)
            }
        }
    }
    private fun discover() {
        if (checking || isFinishing || android.net.Uri.parse(web.url.orEmpty()).host != SicauJiaowuRoute.VPN_HOST) return
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
            if (isFinishing || epoch != generation || checking) return@evaluateJavascript
            val url = runCatching { com.google.gson.Gson().fromJson(result, String::class.java) }.getOrNull().orEmpty()
            val route = SicauJiaowuRoute.fromResourcePage(url) ?: return@evaluateJavascript
            verify(route, epoch)
        }
    }
    private fun verify(route: SicauJiaowuRoute, epoch: Int) {
        if(checking || isFinishing || openedRevision != SicauNativeAuth.webRevision.value) return
        val network = SicauWebVpn.networkRevision.value
        checking = true
        message = "正在验证校园 VPN…"
        lifecycleScope.launch {
            val session = SicauJiaowuSession(SicauJiaowuCookieJar(route))
            try {
                session.checkLoginPage()
                if(epoch != generation || openedRevision != SicauNativeAuth.webRevision.value || network != SicauWebVpn.networkRevision.value) return@launch
                SicauWebVpn.discovered(route)
                WebVpnSessionStore.capture()
                if(epoch != generation || openedRevision != SicauNativeAuth.webRevision.value || network != SicauWebVpn.networkRevision.value) return@launch
                SicauWebVpn.move(WebVpnPhase.AVAILABLE)
                CookieManager.getInstance().flush()
                SicauNativeAuth.persistFromWebView()
                setResult(RESULT_OK); finish()
            } catch(e: CancellationException) { throw e }
            catch(e: JiaowuException) { message = CampusLoginCopy.failure; SicauWebVpn.move(if(e.reason == JiaowuFailure.WEBVPN_REQUIRED) WebVpnPhase.AUTH_REQUIRED else WebVpnPhase.NETWORK_ERROR) }
            catch(_: Exception) { message = CampusLoginCopy.failure; SicauWebVpn.move(WebVpnPhase.NETWORK_ERROR) }
            finally { session.cancel(); checking = false }
        }
    }
    override fun onPause() { if(::web.isInitialized) { CookieManager.getInstance().flush(); SicauNativeAuth.persistFromWebView() }; super.onPause() }
    private fun leave() {
        web.stopLoading(); finish()
    }
    override fun onDestroy() { if (::web.isInitialized) { web.stopLoading(); web.destroy() }; super.onDestroy() }
}

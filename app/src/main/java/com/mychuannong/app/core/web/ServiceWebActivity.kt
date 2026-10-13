package com.mychuannong.app.core.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.*

/** Native wrapper shared by public services. No JavaScript bridge or SSL bypass. */
open class ServiceWebActivity : ComposeActivity() {
    protected open val serviceTitle get() = intent.getStringExtra("title_tag")?.takeIf { it.isNotBlank() } ?: "服务网页"
    protected open val initialUrl get() = intent.getStringExtra("url_tag").orEmpty()
    protected open val supportsPayment = false
    private val electricAuthorization get() = supportsPayment && ElectricAuthorizationPolicy.provider(initialUrl)
    private var externalAuthorizationAttempted = false
    private var externalAuthorizationReturn = false
    private var authorizationNotice by mutableStateOf<String?>(null)

    private val attemptedSso = mutableSetOf<String>()
    private fun reuseSso(view: WebView, url: String): Boolean {
        if(!com.mychuannong.app.SafeUrl.school(initialUrl)) return false
        val header = CookieManager.getInstance().getCookie(com.mychuannong.app.school.SicauAuthProtocol.ORIGIN).orEmpty()
        val hasSession = header.split(';').any { it.trim().startsWith("go-admin-tgt=") && it.substringAfter('=').isNotBlank() }
        val target = com.mychuannong.app.school.SchoolSsoNavigation.reuseSession(url,hasSession) ?: return false
        if(!attemptedSso.add(url)) return false
        view.loadUrl(target);return true
    }
    private var lastUserTouch = 0L
    private var paymentReturn = false
    private var currentPage = ""
    private var vpnReturnPending = false
    private val webPages = mutableStateListOf<WebView>()
    private val web: WebView get() = webPages.last()
    private var loadProgress by mutableStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var external by mutableStateOf<String?>(null)
    private var refreshConfirm by mutableStateOf(false)
    private val openedSessionRevision = com.mychuannong.app.school.SicauNativeAuth.webRevision.value
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if(loadProgress < 100) {
        error = "页面加载较慢，可继续等待或重新打开"
        if(electricAuthorization && ElectricAuthorizationPolicy.wechatAuthorization(web.url.orEmpty())) continueAuthorizationExternally()
    } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WebView.setWebContentsDebuggingEnabled(false)
        currentPage = state?.getString("current_page") ?: initialUrl
        paymentReturn = state?.getBoolean("payment_return") ?: false
        externalAuthorizationAttempted = state?.getBoolean("external_auth_attempted") ?: false
        externalAuthorizationReturn = state?.getBoolean("external_auth_return") ?: false
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        webPages.add(createWeb())
        setComposeContent {

            BackHandler { goBack() }
            Surface(color = AppTheme.colors.overviewPageBackgroundColor) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SecondaryPageHeader(serviceTitle, ::goBack) {
                        WebMoreMenu({ web }, ::finish, onRefresh = { refreshConfirm = true })
                    } }
                    if(loadProgress < 100 && error == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    authorizationNotice?.let { message -> SecondaryCard {
                        Text(message)
                        TextButton(onClick={continueAuthorizationExternally(manual=true)}) { Text("在系统浏览器继续授权") }
                        TextButton(onClick={authorizationNotice=null;error=null;web.loadUrl(initialUrl)}) { Text("重试 APP 内电费入口") }
                    } }
                    error?.let { message -> SecondaryCard {
                        Text(message)
                        TextButton(onClick = { error = null; refreshConfirm = true }) { Text("重试") }
                        TextButton(onClick = { launchExternal(initialUrl) }) { Text("在浏览器打开原始入口") }
                        if(Uri.parse(initialUrl).host == "xsgz.sicau.edu.cn") {
                            Text("请检查网络，或登录后重试。")
                            TextButton(onClick = { startActivity(Intent(this@ServiceWebActivity, com.mychuannong.app.SchoolWebActivity::class.java)
                                .putExtra("url", "https://webvpn.sicau.edu.cn/").putExtra("title", "校内服务登录")) }) { Text("打开校内服务登录") }
                        }
                        if(electricAuthorization) TextButton(onClick={continueAuthorizationExternally(manual=true)}) { Text("在系统浏览器打开电费入口") }
                        TextButton(onClick = { error = null }) { Text("继续查看") }
                    } }
                    val activeWeb = web
                    key(activeWeb) {
                        AndroidView(factory = { activeWeb }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            if(refreshConfirm) AlertDialog(onDismissRequest = { refreshConfirm = false }, title = { Text("重新打开当前页面") },
                text = { Text("未提交的表单内容可能丢失。不会重新发送上一次提交。") },
                confirmButton = { Button(onClick = { refreshConfirm = false; currentPage.takeIf(ServiceWebPolicy::httpUrl)?.let { error = null; web.loadUrl(it) } }) { Text("继续") } },
                dismissButton = { TextButton(onClick = { refreshConfirm = false }) { Text("取消") } })
            external?.let { url -> AlertDialog(onDismissRequest = { external = null }, title = { Text("打开外部应用") },
                text = { Text("由系统浏览器或对应应用继续处理，可返回我的川农。") },
                confirmButton = { Button(onClick = { external = null; launchExternal(url) }) { Text("打开") } },
                dismissButton = { TextButton(onClick = { external = null }) { Text("取消") } }) }
        }
        lifecycleScope.launch {
            if(com.mychuannong.app.SafeUrl.school(initialUrl)) {
                runCatching { com.mychuannong.app.school.SicauNativeAuth.exportToWebView() }
                if(Uri.parse(initialUrl).host == "jiaowu.sicau.edu.cn")
                    runCatching { com.mychuannong.app.school.SicauJiaowuAuth.exportExistingToWebView() }
            }
            if(openedSessionRevision != com.mychuannong.app.school.SicauNativeAuth.webRevision.value) { finish();return@launch }
            if(state == null || web.restoreState(state) == null) {
                currentPage = com.mychuannong.app.school.SchoolResourceAccess.resolve(currentPage) ?: currentPage
                if(ServiceWebPolicy.httpUrl(currentPage)) web.loadUrl(currentPage) else error = "网页地址不可用"
            }
        }
        lifecycleScope.launch {
            com.mychuannong.app.school.SicauNativeAuth.webRevision.collect { if(it != openedSessionRevision) { web.stopLoading();finish() } }
        }
    }
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWeb(): WebView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
            setOnTouchListener { _, event ->
                if(event.action == android.view.MotionEvent.ACTION_UP) lastUserTouch = android.os.SystemClock.elapsedRealtime()
                false
            }
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(true)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, electricAuthorization || com.mychuannong.app.SafeUrl.school(initialUrl))
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean = true
                override fun onProgressChanged(view: WebView?, value: Int) { if(view == web) this@ServiceWebActivity.loadProgress = value }
                override fun onCloseWindow(window: WebView?) {
                    if(window != null && webPages.size > 1 && window == web) closePopup()
                }
                override fun onCreateWindow(view: WebView?, dialog: Boolean, gesture: Boolean, msg: Message?): Boolean {
                    if(!gesture) return false
                    val popup = createWeb()
                    val transport = msg?.obj as? WebView.WebViewTransport ?: run { popup.destroy(); return false }
                    webPages.add(popup)
                    transport.webView = popup; msg.sendToTarget(); return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(v: WebView?, url: String?, reload: Boolean) {
                    if(v!=null && url!=null) v.post { if(!isFinishing && v.url==url) reuseSso(v,url) }
                }
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val url = r?.url?.toString() ?: return true
                    if(v!=null && r.isForMainFrame && reuseSso(v,url)) return true
                    if(ServiceWebPolicy.httpUrl(url)) return false
                    if(r.isForMainFrame || supportsPayment && (ServiceWebPolicy.paymentScheme(url) || Uri.parse(url).scheme == "intent")) requestExternal(url, r.hasGesture())
                    return true
                }
                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                    if(v != web) return
                    if(Uri.parse(initialUrl).host == "jiaowu.sicau.edu.cn" && Uri.parse(url.orEmpty()).host == "webvpn.sicau.edu.cn" &&
                        (Uri.parse(url.orEmpty()).path.orEmpty().contains("/wui/") || Uri.parse(url.orEmpty()).path == "/login")) vpnReturnPending = true
                    if(url != null && ServiceWebPolicy.httpUrl(url)) currentPage = url
                    error = null; this@ServiceWebActivity.loadProgress = 0; handler.removeCallbacks(timeout); handler.postDelayed(timeout, 30000)
                }
                override fun onPageFinished(v: WebView?, url: String?) {
                    if(v != web) return
                    if(error==null && url!=null && v!=null && reuseSso(v,url))return
                    handler.removeCallbacks(timeout); this@ServiceWebActivity.loadProgress = 100
                    if(error == null && url != null) {
                        if(Uri.parse(url).host in setOf("bd.bdfairy.cn", "xsgz.sicau.edu.cn", "www.sicau.edu.cn", "ncre.neea.edu.cn")) evaluateJavascript(ServiceWebPolicy.mobileViewport, null)
                        ServiceWebPolicy.adaptation(url)?.let { evaluateJavascript(it, null) }
                    }
                    CookieManager.getInstance().flush()
                    if(electricAuthorization && url != null && ElectricAuthorizationPolicy.inspect(url) && error == null) {
                        inspectElectricPage(v,url)
                        // Vue may render its error message after onPageFinished.
                        handler.postDelayed({inspectElectricPage(v,url)},1500)
                        handler.postDelayed({inspectElectricPage(v,url)},5000)
                    }
                    if(vpnReturnPending) com.mychuannong.app.school.SchoolResourceAccess.capture(web) {
                        if(!isFinishing && vpnReturnPending) {
                            vpnReturnPending = false
                            com.mychuannong.app.school.SchoolResourceAccess.resolve(initialUrl)?.let { web.loadUrl(it) }
                        }
                    }
                    if(url != null && com.mychuannong.app.SafeUrl.school(url)) lifecycleScope.launch {
                        runCatching { com.mychuannong.app.school.SicauNativeAuth.importFromWebView() }
                    }
                    // Chromium may finish its internal error document after onReceivedError.
                    // Inspect only a status marker, never collect page or form contents.
                    v?.evaluateJavascript(ServiceWebPolicy.connectionErrorProbe) { failed ->
                        if(!isFinishing && !isDestroyed && v == web && failed == "true")
                            error = "网站连接失败（连接被拒绝或重置），请重试或在浏览器核对原始入口"
                    }
                    handler.postDelayed({
                        if(!isFinishing && !isDestroyed && v == web && v?.url == url && error == null) {
                            v?.evaluateJavascript(ServiceWebPolicy.emptyPageProbe) { blank ->
                                if(!isFinishing && !isDestroyed && v == web && v?.url == url && blank == "true")
                                    error = "网站未返回可显示的内容，请重试或在浏览器核对原始入口"
                            }
                        }
                    }, 5000)
                }
                override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                    if(v == web && r?.isForMainFrame == true) {
                        handler.removeCallbacks(timeout)
                        error = when(e?.errorCode) {
                            ERROR_FAILED_SSL_HANDSHAKE -> "网站 HTTPS 连接失败，请在浏览器核对原始入口"
                            ERROR_HOST_LOOKUP -> "无法解析网站地址，请检查网络或切换校园网"
                            ERROR_TIMEOUT -> "网站连接超时，请稍后重试"
                            else -> "网页连接失败，请检查网络后重试"
                        }
                        if(electricAuthorization && ElectricAuthorizationPolicy.wechatAuthorization(r.url.toString())) continueAuthorizationExternally()
                    }
                }
                override fun onReceivedHttpError(v: WebView?, r: WebResourceRequest?, e: WebResourceResponse?) {
                    if(v == web && r?.isForMainFrame == true) {
                        handler.removeCallbacks(timeout)
                        error = if(e?.statusCode in listOf(401,403)) "网站拒绝访问或登录会话已失效，请重新打开入口授权" else "网页服务暂不可用，请稍后重试"
                        if(electricAuthorization && ElectricAuthorizationPolicy.wechatAuthorization(r.url.toString())) continueAuthorizationExternally()
                    }
                }
                override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) {
                    h?.cancel(); if(e?.url == v?.url || e?.url == initialUrl) { handler.removeCallbacks(timeout); error = "网站证书校验失败" }
                }
                override fun onFormResubmission(v: WebView?, dontResend: Message?, resend: Message?) { dontResend?.sendToTarget(); error = "已阻止重复提交，请返回业务页面核对结果" }
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    if(view != null) {
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        webPages.remove(view)
                        view.destroy()
                        webPages.add(createWeb())
                        handler.removeCallbacks(timeout)
                        loadProgress = 100
                        error = "网页显示进程已停止，请点击重试重新加载"
                    }
                    return true
                }
            }
            setDownloadListener { url, _, _, _, _ -> if(ServiceWebPolicy.httpUrl(url)) external = url }
        }

    private fun closePopup() {
        handler.removeCallbacks(timeout)
        val popup = webPages.removeAt(webPages.lastIndex)
        popup.stopLoading()
        (popup.parent as? android.view.ViewGroup)?.removeView(popup)
        popup.destroy()
        currentPage = web.url ?: initialUrl
        error = null; loadProgress = web.progress
    }

    private fun requestExternal(url: String, gesture: Boolean) {
        if(electricAuthorization && ElectricAuthorizationPolicy.wechatAuthorization(web.url.orEmpty()) && Uri.parse(url).scheme == "weixin") {
            // Only follow a client link actually issued by the official authorization page.
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                externalAuthorizationReturn=true
            }.onFailure { continueAuthorizationExternally() }
            return
        }
        val recentTouch = android.os.SystemClock.elapsedRealtime() - lastUserTouch in 0..30000 && lastUserTouch > 0
        val payment = ServiceWebPolicy.paymentScheme(url) || Uri.parse(url).scheme == "intent"
        if(payment && supportsPayment && (gesture || recentTouch)) external = url
        else if(gesture && ServiceWebPolicy.externalScheme(url)) external = url
        else error = "此链接无法在网页内打开，可从更多菜单在浏览器继续"
    }

    private fun launchExternal(url: String) {
        // Never execute arbitrary components, extras or flags supplied by an intent URI.
        val parsed = if(Uri.parse(url).scheme == "intent") runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() else null
        val target = if(parsed != null) parsed.data?.toString() else url
        val fallback = parsed?.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") && ServiceWebPolicy.httpUrl(it) }
        val payment = target?.let(ServiceWebPolicy::paymentScheme) == true
        if(target == null || !(ServiceWebPolicy.httpUrl(target) || ServiceWebPolicy.externalScheme(target))) {
            if(fallback != null) web.loadUrl(fallback) else error = "不支持此应用跳转，请在浏览器中继续"
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addCategory(Intent.CATEGORY_BROWSABLE))
            paymentReturn = payment
        }.onFailure {
            if(fallback != null) web.loadUrl(fallback) else error = "未安装或无法打开对应应用，可从更多菜单在浏览器继续"
        }
    }
    private fun goBack() {
        val history = web.copyBackForwardList()
        val index = ServiceWebPolicy.previousBusinessIndex((0 until history.size).map { history.getItemAtIndex(it).url }, history.currentIndex)
        if(index != null) { error = null; web.goBackOrForward(index - history.currentIndex) } else if(webPages.size > 1) closePopup() else finish()
    }
    private fun inspectElectricPage(view:WebView?,url:String) {
        if(isFinishing || isDestroyed || view==null || view!=web || view.url!=url || error!=null)return
        view.evaluateJavascript(ElectricAuthorizationPolicy.statusProbe) { status ->
            if(!isFinishing && !isDestroyed && view==web && view.url==url && error==null) {
                when(status) {
                    "\"external\"" -> continueAuthorizationExternally()
                    "\"expired\"" -> error="微信授权失败、已取消或会话已过期，请重新打开电费入口授权"
                    "\"provider_error\"" -> if(ElectricAuthorizationPolicy.provider(url))
                        error="电费网站返回了“操作失败／网络异常”，尚未进入微信授权。可重试，或在系统浏览器打开入口；若仍失败，请从微信中的学校后勤官方电精灵入口进入。"
                }
            }
        }
    }
    private fun continueAuthorizationExternally(manual:Boolean=false) {
        if(!electricAuthorization || isFinishing || isDestroyed)return
        authorizationNotice=if(manual) "正在外部打开电费入口，可继续在外部使用。返回 APP 不代表已授权或已支付。" else "该网页的微信授权无法在当前容器完成。外部授权后可继续在原电费网站使用；返回 APP 不代表已授权或已支付。若浏览器仍要求微信，请从学校后勤官方电精灵入口打开。"
        if(externalAuthorizationAttempted && !manual)return
        externalAuthorizationAttempted=true
        // Start a fresh provider flow in the browser's own cookie jar. Never transplant an
        // OAuth URL/state from the WebView, nor import the browser's session into the app.
        runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(this,Uri.parse(initialUrl))
            externalAuthorizationReturn=true
        }.onFailure { error="无法打开系统浏览器，请安装可用浏览器后重试，或从微信中的官方电精灵入口继续" }
    }
    override fun onSaveInstanceState(out: Bundle) {
        // Provider/OAuth/payment URLs can carry codes or tokens: never persist their history.
        if(!electricAuthorization) { out.putString("current_page", currentPage); webPages.lastOrNull()?.saveState(out) }
        out.putBoolean("payment_return", paymentReturn)
        out.putBoolean("external_auth_attempted",externalAuthorizationAttempted)
        out.putBoolean("external_auth_return",externalAuthorizationReturn)
        super.onSaveInstanceState(out)
    }
    override fun onPause() {
        webPages.lastOrNull()?.onPause();CookieManager.getInstance().flush()
        if(openedSessionRevision == com.mychuannong.app.school.SicauNativeAuth.webRevision.value && com.mychuannong.app.SafeUrl.school(initialUrl)) lifecycleScope.launch {
            runCatching { com.mychuannong.app.school.SicauNativeAuth.importFromWebView() }
        }
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        if(webPages.isNotEmpty()) web.onResume()
        if(externalAuthorizationReturn) {
            externalAuthorizationReturn=false
            authorizationNotice="已返回电费服务。外部浏览器或微信的登录状态由其自行管理，可继续在外部使用；APP 未复制登录信息，请以电费网站显示为准。"
        }
        if(paymentReturn) {
            paymentReturn = false
            android.widget.Toast.makeText(this, "已返回电费网页，请以网站订单页显示的支付结果为准", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroy() { handler.removeCallbacks(timeout); webPages.forEach { it.stopLoading(); (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }; super.onDestroy() }
}

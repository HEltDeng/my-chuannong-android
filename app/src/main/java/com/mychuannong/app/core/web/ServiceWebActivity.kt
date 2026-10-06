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
    private lateinit var web: WebView
    private var loadProgress by mutableStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var external by mutableStateOf<String?>(null)
    private var refreshConfirm by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if(loadProgress < 100) error = "页面加载较慢，可继续等待或重新打开" }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = WebView(this).apply {
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
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, value: Int) { this@ServiceWebActivity.loadProgress = value }
                override fun onCreateWindow(view: WebView?, dialog: Boolean, gesture: Boolean, msg: Message?): Boolean {
                    if(!gesture) return false
                    val popup = WebView(this@ServiceWebActivity)
                    popup.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            request?.url?.toString()?.let { if(ServiceWebPolicy.httpUrl(it)) web.loadUrl(it) else external = it }
                            popup.destroy(); return true
                        }
                    }
                    val transport = msg?.obj as? WebView.WebViewTransport ?: run { popup.destroy(); return false }
                    transport.webView = popup; msg.sendToTarget(); return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val url = r?.url?.toString() ?: return true
                    if(ServiceWebPolicy.httpUrl(url)) return false
                    if(r.isForMainFrame && r.hasGesture() && Uri.parse(url).scheme !in setOf("file","data","javascript","content","intent")) external = url
                    return true
                }
                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                    error = null; this@ServiceWebActivity.loadProgress = 0; handler.removeCallbacks(timeout); handler.postDelayed(timeout, 30000)
                }
                override fun onPageFinished(v: WebView?, url: String?) {
                    handler.removeCallbacks(timeout); this@ServiceWebActivity.loadProgress = 100
                    if(error == null && url != null) ServiceWebPolicy.adaptation(url)?.let { evaluateJavascript(it, null) }
                }
                override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                    if(r?.isForMainFrame == true) { handler.removeCallbacks(timeout); error = "网页连接失败，请检查网络后重试" }
                }
                override fun onReceivedHttpError(v: WebView?, r: WebResourceRequest?, e: WebResourceResponse?) {
                    if(r?.isForMainFrame == true) error = "网页服务暂不可用，请稍后重试"
                }
                override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) {
                    h?.cancel(); if(e?.url == v?.url || e?.url == initialUrl) error = "网站证书校验失败"
                }
                override fun onFormResubmission(v: WebView?, dontResend: Message?, resend: Message?) { dontResend?.sendToTarget() }
            }
            setDownloadListener { url, _, _, _, _ -> if(ServiceWebPolicy.httpUrl(url)) external = url }
        }
        setComposeContent {
            var source by remember { mutableStateOf(false) }
            BackHandler { goBack() }
            Surface(color = AppTheme.colors.overviewPageBackgroundColor) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SecondaryPageHeader(serviceTitle, ::goBack) {
                        WebMoreMenu({ web }, ::finish)
                    } }
                    if(loadProgress < 100 && error == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    error?.let { message -> SecondaryCard {
                        Text(message)
                        TextButton(onClick = { error = null; refreshConfirm = true }) { Text("重试") }
                        TextButton(onClick = { error = null }) { Text("继续查看") }
                    } }
                    AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
            if(source) AlertDialog(onDismissRequest = { source = false }, title = { Text("页面来源") },
                text = { Text(Uri.parse(web.url ?: initialUrl).host.orEmpty() + "\n此页面由对应网站运营方提供。") },
                confirmButton = { TextButton(onClick = { source = false }) { Text("知道了") } })
            if(refreshConfirm) AlertDialog(onDismissRequest = { refreshConfirm = false }, title = { Text("重新打开当前页面") },
                text = { Text("未提交的表单内容可能丢失。不会重新发送上一次提交。") },
                confirmButton = { Button(onClick = { refreshConfirm = false; (web.url ?: initialUrl).takeIf(ServiceWebPolicy::httpUrl)?.let(web::loadUrl) }) { Text("继续") } },
                dismissButton = { TextButton(onClick = { refreshConfirm = false }) { Text("取消") } })
            external?.let { url -> AlertDialog(onDismissRequest = { external = null }, title = { Text("打开外部应用") },
                text = { Text("由系统浏览器或对应应用继续处理，可返回我的川农。") },
                confirmButton = { Button(onClick = { external = null; runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { error = "没有可处理此链接的应用" } }) { Text("打开") } },
                dismissButton = { TextButton(onClick = { external = null }) { Text("取消") } }) }
        }
        if(state == null || web.restoreState(state) == null) {
            if(ServiceWebPolicy.httpUrl(initialUrl)) web.loadUrl(initialUrl) else error = "网页地址不可用"
        }
    }
    private fun goBack() {
        val history = web.copyBackForwardList()
        val index = ServiceWebPolicy.previousBusinessIndex((0 until history.size).map { history.getItemAtIndex(it).url }, history.currentIndex)
        if(index != null) web.goBackOrForward(index - history.currentIndex) else finish()
    }
    override fun onSaveInstanceState(out: Bundle) { web.saveState(out); super.onSaveInstanceState(out) }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if(::web.isInitialized) web.onResume() }
    override fun onDestroy() { handler.removeCallbacks(timeout); web.stopLoading(); (web.parent as? android.view.ViewGroup)?.removeView(web); web.destroy(); super.onDestroy() }
}

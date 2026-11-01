package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import com.mychuannong.app.core.network.campustools.CampusContentBridge
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.webkit.WebViewAssetLoader
import com.mychuannong.app.core.account.AccountProtectedActivity
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader

/** Local marketplace listings with publisher contact details and image selection. */
class SecondhandActivity : AccountProtectedActivity() {
    private lateinit var web: WebView
    private var failed by mutableStateOf(false)
    private var imageCallback: ValueCallback<Array<Uri>>? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { values ->
        val images = values.filter { uri ->
            uri.scheme == "content" && runCatching { contentResolver.getType(uri)?.startsWith("image/") == true }.getOrDefault(false)
        }.take(4)
        imageCallback?.onReceiveValue(images.toTypedArray().takeIf { it.isNotEmpty() })
        imageCallback = null
    }
    private val home = "https://appassets.androidplatform.net/assets/secondhand/index.html"
    private fun back() {
        if (!::web.isInitialized) { finish(); return }
        web.evaluateJavascript("Boolean(window.CampusSecondhand && window.CampusSecondhand.back())") { handled ->
            if (handled != "true" && !isFinishing && !isDestroyed) finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onAccountReady(savedInstanceState: Bundle?) {
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.rgb(247, 248, 244))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) setDefaultFocusHighlightEnabled(false)
            settings.javaScriptEnabled = true
            addJavascriptInterface(CampusContentBridge("secondhand"), "ChuannongContent")
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    if (view.url?.substringBefore('#') != home) return false
                    imageCallback?.onReceiveValue(null)
                    imageCallback = callback
                    runCatching { imagePicker.launch(arrayOf("image/*")) }
                        .onFailure { imageCallback?.onReceiveValue(null); imageCallback = null }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    CampusContentBridge.media(request.url) ?:                     loader.shouldInterceptRequest(request.url) ?: WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.toString().substringBefore('#') != home
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }
            }
        }
        web.doOnLayout { web.loadUrl(home) }
        setComposeContent {
            FeatureAvailabilityGate("secondhand", "二手好物", ::finish) {
                BackHandler { back() }
                Scaffold(containerColor = androidx.compose.ui.graphics.Color(0xFFF7F8F4), topBar = {
                    SecondaryScaffoldHeader("二手好物", ::back)
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        if (failed) {
                            Text("页面加载失败，请重试", Modifier.padding(20.dp))
                            Button(onClick = { failed = false; web.loadUrl(home) }) { Text("重新加载") }
                        } else AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }

            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (!isFinishing && ::web.isInitialized) web.evaluateJavascript("window.CampusSecondhand?.refresh()", null)
    }
    override fun onDestroy() {
        imageCallback?.onReceiveValue(null)
        imageCallback = null
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }; super.onDestroy()
    }
}

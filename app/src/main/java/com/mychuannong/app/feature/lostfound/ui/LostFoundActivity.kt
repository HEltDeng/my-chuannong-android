package com.mychuannong.app.feature.lostfound.ui

import com.mychuannong.app.core.network.campustools.CampusContentBridge
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.webkit.WebViewAssetLoader
import com.mychuannong.app.core.account.AccountProtectedActivity
import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import com.mychuannong.app.freshNews.data.local.model.LostFoundType

/** The approved lost-and-found UI, bundled offline with durable on-device records. */
class LostFoundActivity : AccountProtectedActivity() {
    private lateinit var web: WebView
    private var failed by mutableStateOf(false)
    private var imageCallback: ValueCallback<Array<Uri>>? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val allowed = uri?.takeIf {
            it.scheme == "content" && runCatching {
                contentResolver.getType(it) in setOf("image/png", "image/jpeg", "image/webp")
            }.getOrDefault(false)
        }
        imageCallback?.onReceiveValue(allowed?.let { arrayOf(it) })
        imageCallback = null
    }
    private fun startUrl(): String = Uri.parse(LostFoundWebPolicy.HOME).buildUpon().apply {
        when (intent.getStringExtra(EXTRA_START)) {
            START_DETAIL -> intent.getStringExtra(EXTRA_POST_ID)?.takeIf(String::isNotBlank)?.let {
                appendQueryParameter("post", it)
            }
            START_PUBLISH_LOST -> appendQueryParameter("start", "publish-lost")
            START_PUBLISH_FOUND -> appendQueryParameter("start", "publish-found")
        }
    }.build().toString()

    private fun back() {
        if (!::web.isInitialized || failed) { finish(); return }
        web.evaluateJavascript("Boolean(window.CampusLostFound && window.CampusLostFound.back())") { handled ->
            if (handled != "true" && !isFinishing && !isDestroyed) finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onAccountReady(savedInstanceState: Bundle?) {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.rgb(248, 250, 247))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) setDefaultFocusHighlightEnabled(false)
            settings.javaScriptEnabled = true
            addJavascriptInterface(CampusContentBridge("lost-found"), "ChuannongContent")
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    if (!LostFoundWebPolicy.allowsDocument(view.url.orEmpty())) return false
                    imageCallback?.onReceiveValue(null)
                    imageCallback = callback
                    runCatching { imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
                        .onFailure { imageCallback?.onReceiveValue(null); imageCallback = null }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    CampusContentBridge.media(request.url) ?:                     if (LostFoundWebPolicy.allowsAsset(request.url.toString())) {
                        loader.shouldInterceptRequest(request.url) ?: blocked()
                    } else blocked()

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (request.isForMainFrame && LostFoundWebPolicy.isExit(url)) finish()
                    return !LostFoundWebPolicy.allowsDocument(url)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) failed = true
                }
            }
        }
        web.doOnLayout { web.loadUrl(startUrl()) }
        setComposeContent {
            BackHandler { back() }
            FeatureAvailabilityGate(featureKey = "lost_found", title = "失物招领", onBack = ::finish) {
                Scaffold(containerColor = Color(0xFFF8FAF7), contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        if (failed) {
                            Text("页面加载失败，请重试", Modifier.padding(20.dp))
                            Button(onClick = { failed = false; web.loadUrl(startUrl()) }) { Text("重新加载") }
                        } else AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && ::web.isInitialized) web.evaluateJavascript("window.CampusLostFound?.refresh()", null)
    }

    override fun onDestroy() {
        imageCallback?.onReceiveValue(null)
        imageCallback = null
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArray(0).inputStream())

    companion object {
        const val EXTRA_POST_ID = "local_campus_post_id"
        private const val EXTRA_START = "lost_found_start"
        private const val START_DETAIL = "detail"
        private const val START_PUBLISH_LOST = "publish_lost"
        private const val START_PUBLISH_FOUND = "publish_found"
        fun listIntent(context: Context): Intent = Intent(context, LostFoundActivity::class.java)
        fun detailIntent(context: Context, postId: String): Intent = listIntent(context)
            .putExtra(EXTRA_START, START_DETAIL).putExtra(EXTRA_POST_ID, postId)
        fun publishIntent(context: Context, type: LostFoundType): Intent = listIntent(context)
            .putExtra(EXTRA_START, if (type == LostFoundType.LOST) START_PUBLISH_LOST else START_PUBLISH_FOUND)
    }
}

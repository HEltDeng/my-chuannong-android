package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.core.network.campustools.CampusContentBridge
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.webkit.WebViewAssetLoader
import com.mychuannong.app.R
import com.mychuannong.app.core.account.AccountProtectedActivity
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader

/** Approved service prototypes bundled offline. Bookings remain explicitly simulated. */
class CampusServicesActivity : AccountProtectedActivity() {
    private lateinit var web: WebView
    private var failed by mutableStateOf(false)
    private val pageBackground = Color(0xFFF5F9FC)
    private val pageAccent = Color(0xFF326B96)

    private fun back() {
        if (!::web.isInitialized || failed) { finish(); return }
        web.evaluateJavascript("Boolean(window.ChuannongManaged ? window.ChuannongManaged.back() : window.CampusServices && window.CampusServices.back())") { handled ->
            if (handled != "true" && !isFinishing && !isDestroyed) finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onAccountReady(savedInstanceState: Bundle?) {
        val service = CampusService.fromRoute(intent.getStringExtra(EXTRA_SERVICE))
            ?: run { finish(); return }
        val title = getString(when (service) {
            CampusService.Language -> R.string.feature_function_ielts_toefl
            CampusService.Exam -> R.string.feature_function_civil_postgrad
            CampusService.Gym -> R.string.feature_function_gym
        })
        val homeUrl = CampusServicesWebPolicy.homeUrl(service)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.rgb(245, 249, 252))
            settings.javaScriptEnabled = true
            addJavascriptInterface(CampusContentBridge("services"), "ChuannongContent")
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            isVerticalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    CampusContentBridge.media(request.url) ?:                     if (CampusServicesWebPolicy.allowsAssetRequest(request.url.toString())) {
                        loader.shouldInterceptRequest(request.url) ?: blockedResponse()
                    } else blockedResponse()

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !CampusServicesWebPolicy.allowsNavigation(request.url.toString())

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame) failed = true
                }
            }
        }
        // Wait for the native viewport before sizing the fixed dock and sheets.
        web.doOnLayout { web.loadUrl(homeUrl) }
        setComposeContent {
            BackHandler { back() }
            Scaffold(containerColor = pageBackground, topBar = {
                SecondaryScaffoldHeader(title, ::back)
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (failed) {
                        Text("${title}页面加载失败，请重试", Modifier.padding(20.dp), color = pageAccent)
                        Button(onClick = { failed = false; web.loadUrl(homeUrl) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = pageAccent)) {
                            Text("重新加载")
                        }
                    } else AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    private fun blockedResponse() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())

    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SERVICE = "campus_service"
        fun createIntent(context: Context, service: CampusService): Intent =
            Intent(context, CampusServicesActivity::class.java).putExtra(EXTRA_SERVICE, service.route)
    }
}

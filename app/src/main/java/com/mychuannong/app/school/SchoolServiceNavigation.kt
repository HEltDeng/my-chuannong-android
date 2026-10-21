package com.mychuannong.app.school

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.main.navigation.MainTabDestination

object SchoolServiceNavigation {
    const val EXTRA_AUTH = "school_service_auth"
    fun intent(context: Context, service: SchoolService) = Intent(context, SchoolWebActivity::class.java)
        .putExtra("url", service.url).putExtra("title", service.title).putExtra(EXTRA_AUTH, service.auth.name)
    fun open(context: Context, service: SchoolService) = context.startActivity(intent(context, service))
    fun open(context: Context, id: String) = open(context, SchoolServices.entries.getValue(id))

    fun returnToServices(activity: Activity) {
        // Reuse the existing MainActivity and its Compose scroll/saveable state.
        activity.startActivity(Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("main_tab", MainTabDestination.Feature.index))
        activity.finish()
    }
}

/** A close-only channel: no credentials, page data or native account access. */
internal object SchoolServiceBack {
    const val CLOSE_URL = "mychuannong-service://close"
    val script = """
        (function(){
          if(window.top!==window || window.__sicauServiceBack)return;
          window.__sicauServiceBack=true;
          function closePage(){window.location.href='$CLOSE_URL';}
          var go=window.history.go.bind(window.history);
          window.history.back=closePage;
          window.history.go=function(n){if(Number(n)<0)closePage();else go(n);};
          window.close=closePage;
          document.addEventListener('click',function(e){
            var el=e.target.closest && e.target.closest('a,button,[role=button],[onclick],[aria-label],[title]');
            if(!el)return;
            var label=(el.getAttribute('aria-label')||el.getAttribute('title')||el.textContent||'').trim();
            if(/^(返回|返回上页|返回上一页|返回首页|关闭|关闭页面|back|close)$/i.test(label)){
              e.preventDefault();e.stopImmediatePropagation();closePage();
            }
          },true);
        })();
    """.trimIndent()

    fun install(web: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf("https://*.sicau.edu.cn"))
    }
    fun pageFinished(web: WebView) { web.evaluateJavascript(script, null) }
}

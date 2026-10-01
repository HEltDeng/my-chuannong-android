package com.mychuannong.app
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.webkit.*
import android.widget.*
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Adapted from the accepted WebViewActivity: retain progress, history and external-open flow;
// remove mixed-content allowance and add exact host checks, errors, timeout and safe redirects.
class SchoolWebActivity:ComponentActivity(){
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var progress:ProgressBar
 private val handler=Handler(Looper.getMainLooper())
 private var generation=0
 private var failed=false
 private var startUrl=""
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(state:Bundle?){super.onCreate(state)
 startUrl=intent.getStringExtra("url").orEmpty();if(!SafeUrl.school(startUrl)){finish();return}
 val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(android.graphics.Color.WHITE)}
 val title=TextView(this).apply{text=intent.getStringExtra("title")?:"学校网页";textSize=18f;setPadding(20,12,20,12)};layout.addView(title)
 val toolbar=LinearLayout(this);fun button(text:String,action:()->Unit){toolbar.addView(Button(this).apply{this.text=text;textSize=12f;isAllCaps=false;setTextColor(android.graphics.Color.rgb(37,124,113));backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(237,247,244));stateListAnimator=null;setOnClickListener{action()}},LinearLayout.LayoutParams(0,52.dp(),1f))}
 button("返回"){if(web.canGoBack())web.goBack()else finish()};button("关闭"){finish()};button("刷新"){web.loadUrl(startUrl)};button("浏览器"){external(web.url?:startUrl)};layout.addView(toolbar)
 progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);layout.addView(progress)
 status=TextView(this).apply{text="请在学校页面自行登录；本产品不判断登录状态。";setPadding(18,10,18,10);textSize=13f};layout.addView(status)
 web=WebView(this);layout.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(layout)
 ViewCompat.setOnApplyWindowInsetsListener(layout){v,i->val p=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(p.left,p.top,p.right,p.bottom);i}
 web.settings.apply{javaScriptEnabled=true;domStorageEnabled=true;loadWithOverviewMode=true;useWideViewPort=true;builtInZoomControls=true;displayZoomControls=false;allowFileAccess=false;allowContentAccess=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false)}
 CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
 WebView.setWebContentsDebuggingEnabled(false)
 web.webChromeClient=object:WebChromeClient(){override fun onProgressChanged(v:WebView?,p:Int){progress.progress=p;progress.visibility=if(p<100)View.VISIBLE else View.GONE}}
 web.webViewClient=object:WebViewClient(){
 override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean{val u=r?.url?.toString().orEmpty();if(SafeUrl.school(u))return false;if(r?.isForMainFrame==true){if(SafeUrl.https(u)){android.app.AlertDialog.Builder(this@SchoolWebActivity).setTitle("在外部浏览器打开？").setMessage("此跳转不在已核验的学校网页清单中，请确认来源。").setPositiveButton("打开"){_,_->external(u)}.setNegativeButton("取消",null).show()}else status.text="此第三方应用跳转暂不支持，请通过外部浏览器或官方应用继续。"};return true}
 override fun onPageStarted(v:WebView?,u:String?,b:Bitmap?){failed=false;web.visibility=View.VISIBLE;val current=++generation;status.text="正在加载学校网页…";handler.postDelayed({if(current==generation&&progress.visibility==View.VISIBLE){web.stopLoading();failed=true;web.visibility=View.INVISIBLE;status.text="加载超时。可刷新或用浏览器打开；教务访问可能与网络环境有关。";progress.visibility=View.GONE}},20000)}
 override fun onPageFinished(v:WebView?,u:String?){if(!failed)status.text="页面加载结束。若白屏或登录跳转异常，请刷新或用浏览器打开。";progress.visibility=View.GONE}
 override fun onReceivedError(v:WebView?,r:WebResourceRequest?,e:WebResourceError?){if(r?.isForMainFrame==true){failed=true;web.visibility=View.INVISIBLE;status.text="网页加载失败。请重试或用外部浏览器打开；不代表学校服务不存在。";progress.visibility=View.GONE}}
 override fun onReceivedHttpError(v:WebView?,r:WebResourceRequest?,e:WebResourceResponse?){if(r?.isForMainFrame==true){failed=true;web.visibility=View.INVISIBLE;status.text="学校服务器暂未正常响应，请稍后重试。"}}
 override fun onReceivedSslError(v:WebView?,h:SslErrorHandler?,e:SslError?){h?.cancel();failed=true;web.visibility=View.INVISIBLE;status.text="网站证书校验失败，已停止加载。"}
 }
 web.setDownloadListener{url,_,_,_,_->if(SafeUrl.https(url)){android.app.AlertDialog.Builder(this).setTitle("交由浏览器下载").setMessage("浏览器会独立处理下载和文件打开；不会导出本应用的学校登录 Cookie。").setPositiveButton("继续"){_,_->external(url)}.setNegativeButton("取消",null).show()}else status.text="不支持此下载链接，请使用学校官方渠道。"}
 onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(web.canGoBack())web.goBack()else finish()}})
 web.loadUrl(startUrl)
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun external(url:String){if(!SafeUrl.https(url)){status.text="不支持此协议";return};try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){status.text="未找到可用浏览器"}}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);if(::web.isInitialized){web.stopLoading();web.destroy()};super.onDestroy()}
}

package com.mychuannong.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
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
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.school.SchoolPageTopBar
import com.mychuannong.app.school.SchoolWebPolicy

class SchoolWebActivity:ComposeActivity() {
    private lateinit var web:WebView
    private var error by mutableStateOf<String?>(null)
    private var loadingProgress by mutableStateOf(100)
    private var serviceTitle=""
    private var loginEntry=false
    private var startUrl=""
    private var requestedUrl=""
    private var returnFromVpn=false
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    private var loading by mutableStateOf(false)
    private var lastUserTouch=0L
    private var paymentReturn=false
    private var backSource:String?=null
    private var readingTimetable by mutableStateOf(false)
    private var timetableStatus by mutableStateOf("")
    private var importOpened=false
    private val followedTimetableLinks=mutableSetOf<String>()
    private var discoveringTimetable=false
    private var authenticationStarted=false
    private var returnedToTarget=false
    private val attemptedSso = mutableSetOf<String>()
    private fun reuseSso(url: String): Boolean {
        val cookie = CookieManager.getInstance().getCookie(com.mychuannong.app.school.SicauAuthProtocol.ORIGIN).orEmpty()
        val hasSession = cookie.split(';').any { it.trim().startsWith("go-admin-tgt=") && it.substringAfter('=').isNotBlank() }
        val target = com.mychuannong.app.school.SchoolSsoNavigation.reuseSession(url,hasSession) ?: return false
        if(!attemptedSso.add(url)) return false
        web.loadUrl(target)
        return true
    }
    private val openedSessionRevision=com.mychuannong.app.school.SicauNativeAuth.webRevision.value
    private fun readTimetable(automatic:Boolean=false) {
        if(readingTimetable || importOpened || isFinishing) return
        if(Uri.parse(web.url.orEmpty()).host!="jiaowu.sicau.edu.cn") {
            timetableStatus="请先完成学校登录，随后继续读取课表"
            return
        }
        readingTimetable=true
        timetableStatus="正在读取学校课表…"
        val pageGeneration=generation
        web.evaluateJavascript(com.mychuannong.app.school.TimetableHtmlReader.extractionScript) { encoded ->
            readingTimetable=false
            if(isFinishing || pageGeneration!=generation || importOpened) return@evaluateJavascript
            runCatching {
                val json=com.google.gson.Gson().fromJson(encoded,String::class.java)
                val value=org.json.JSONObject(json)
                val html=value.optString("html")
                require(html.isNotBlank() && html.length<=200_000)
                importOpened=true
                timetableStatus="已读取课表，请核对并保存"
                startActivity(Intent(this,com.mychuannong.app.school.TimetableImportActivity::class.java)
                    .putExtra("term",value.getString("term")).putExtra("timetable_html",html).putExtra("open_timetable",true))
            }.onFailure {
                timetableStatus="尚未找到课表。若显示登录页，请先完成教务登录；进入后可点击打开课表。"
                if(automatic) openDiscoveredTimetable()
            }
        }
    }
    private fun openDiscoveredTimetable() {
        if(discoveringTimetable || readingTimetable || isFinishing || importOpened) return
        discoveringTimetable=true
        val pageGeneration=generation
        web.evaluateJavascript(com.mychuannong.app.school.TimetableNavigation.script) { encoded ->
            discoveringTimetable=false
            if(isFinishing || pageGeneration!=generation || importOpened) return@evaluateJavascript
            val link=runCatching { com.google.gson.Gson().fromJson(encoded,String::class.java) }.getOrNull().orEmpty()
            if(com.mychuannong.app.school.TimetableNavigation.allowed(link) && followedTimetableLinks.size<3 && followedTimetableLinks.add(link)) {
                timetableStatus="正在打开当前学校页面提供的课表…"
                web.loadUrl(link)
            } else timetableStatus="请在学校页面进入 网上选(退)课 → 选择学期 → 选课情况（课表），打开后将自动读取。"
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        startUrl=intent.getStringExtra("url").orEmpty()
        if(!SafeUrl.school(startUrl)){finish();return}
        requestedUrl=startUrl
        startUrl=com.mychuannong.app.school.SchoolResourceAccess.resolve(requestedUrl)
            ?: com.mychuannong.app.school.SchoolSsoNavigation.entry(requestedUrl)
        serviceTitle=intent.getStringExtra("title")?:"学校服务"
        loginEntry=intent.getBooleanExtra("campus_login",false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        web=WebView(this).apply { layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT) }
        if(android.os.Build.VERSION.SDK_INT>=26) web.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        web.setOnTouchListener { _, event -> if(event.action==android.view.MotionEvent.ACTION_UP){lastUserTouch=android.os.SystemClock.elapsedRealtime();backSource=null};false }
        web.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true;loadWithOverviewMode=true;useWideViewPort=true
            builtInZoomControls=true;displayZoomControls=false;allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
        WebView.setWebContentsDebuggingEnabled(false)
        web.webChromeClient=object:WebChromeClient(){
            override fun onConsoleMessage(message:ConsoleMessage?):Boolean = true
            override fun onProgressChanged(v:WebView?,p:Int){loadingProgress=p}
            override fun onCreateWindow(v:WebView?,dialog:Boolean,gesture:Boolean,message:Message?):Boolean {
                if(!gesture) return false
                val transport=message?.obj as? WebView.WebViewTransport ?: return false
                val popup=WebView(this@SchoolWebActivity)
                popup.webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                        val target=request?.url?.toString().orEmpty()
                        if(SafeUrl.school(target))web.loadUrl(target) else confirmExternal(target)
                        popup.post { popup.destroy() };return true
                    }
                }
                transport.webView=popup;message.sendToTarget();return true
            }
        }
        web.webViewClient=object:WebViewClient(){
            override fun doUpdateVisitedHistory(v:WebView?,url:String?,reload:Boolean) {
                if(url!=null) web.post {
                    if(!isFinishing && web.url==url)reuseSso(url)
                }
            }
            override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean {
                val url=r?.url?.toString().orEmpty()
                if(r?.isForMainFrame==true && reuseSso(url))return true
                if(SafeUrl.school(url))return false
                if(r?.isForMainFrame==true) {
                    if(r.hasGesture() || serviceTitle=="校园缴费" && android.os.SystemClock.elapsedRealtime()-lastUserTouch in 0..10000) confirmExternal(url)
                    else showError("学校页面需要跳转到其他入口，请使用右上角菜单在浏览器继续")
                }
                return true
            }
            override fun onPageStarted(v:WebView?,u:String?,b:Bitmap?) {
                // Chromium's internal error document must not erase the main-frame failure.
                if(u?.startsWith("chrome-error:")==true || u=="about:blank" && error!=null)return
                if(Uri.parse(u.orEmpty()).host=="auth.sicau.edu.cn")authenticationStarted=true
                if(Uri.parse(startUrl).host == "webvpn.sicau.edu.cn" && Uri.parse(u.orEmpty()).host == "webvpn.sicau.edu.cn" &&
                    (Uri.parse(u.orEmpty()).path.orEmpty().contains("/wui/") || Uri.parse(u.orEmpty()).path == "/login")) {
                    returnFromVpn = true
                    com.mychuannong.app.school.SicauWebVpn.move(com.mychuannong.app.school.WebVpnPhase.AUTH_REQUIRED)
                }
                error=null;loading=true;loadingProgress=1;val current=++generation
                handler.postDelayed({if(current==generation && loading){web.stopLoading();showError("加载超时，请重试")}},25000)
            }
            override fun onPageFinished(v:WebView?,u:String?) {
                if(error==null && u!=null && reuseSso(u)) return
                if(error==null && loginEntry && u!=null && com.mychuannong.app.school.SchoolSsoNavigation.isLoginDestination(u)) {
                    com.mychuannong.app.school.SicauNativeAuth.persistFromWebView()
                    setResult(RESULT_OK);finish();return
                }
                loading=false;loadingProgress=100;generation++
                if(error==null && u!=null) SchoolWebPolicy.adaptation(u)?.let{web.evaluateJavascript(it,null)}
                if(error==null && intent.getBooleanExtra("auto_timetable",false) && !importOpened) {
                    val completed=generation
                    handler.postDelayed({ if(completed==generation && !loading && error==null && !isFinishing) readTimetable(true) },500)
                }
                CookieManager.getInstance().flush()
                val completed=generation
                handler.postDelayed({
                    if(completed==generation && !loading && error==null && !isFinishing) {
                        web.evaluateJavascript(com.mychuannong.app.core.web.ServiceWebPolicy.emptyPageProbe) { blank ->
                            if(completed==generation && blank=="true")showError("学校页面未显示内容，请重试；如登录已过期，请重新认证")
                        }
                    }
                },5000)
                // Only return after the official callback has reached the destination origin.
                // Never replace or invent the school's CAS service/callback parameters.
                if(authenticationStarted && !returnedToTarget && SchoolWebPolicy.isStudentHall(requestedUrl) && u!=null && Uri.parse(u).host==Uri.parse(requestedUrl).host &&
                    Uri.parse(u).path in listOf("", "/") && Uri.parse(requestedUrl).path !in listOf("", "/")) {
                    authenticationStarted=false
                    returnedToTarget=true
                    web.loadUrl(requestedUrl)
                }
                com.mychuannong.app.school.SchoolResourceAccess.capture(web) {
                    if(returnFromVpn) {
                        val target=com.mychuannong.app.school.SchoolResourceAccess.resolve(requestedUrl)
                        returnFromVpn=false // At most one automatic return per explicit user action.
                        if(target!=null) { startUrl=target;web.loadUrl(target) }
                        else showError("请在学校服务列表中选择原先访问的功能")
                    }
                }
                if(u!=null && SafeUrl.school(u)) lifecycleScope.launch {
                    runCatching { com.mychuannong.app.school.SicauNativeAuth.importFromWebView() }
                }
            }
            override fun onReceivedError(v:WebView?,r:WebResourceRequest?,e:WebResourceError?) {if(r?.isForMainFrame==true)showError("连接失败，请检查网络后重试")}
            @Suppress("DEPRECATION")
            override fun onReceivedError(v:WebView?,code:Int,description:String?,failingUrl:String?) {
                showError("连接失败，请重试。若手机端可打开，请检查电脑或模拟器的网络连接。")
            }
            override fun onReceivedHttpError(v:WebView?,r:WebResourceRequest?,e:WebResourceResponse?) {
                if(r?.isForMainFrame==true)showError(if(e?.statusCode==401) "学校登录已失效，请重新登录" else "学校服务暂不可用，请稍后重试")
            }
            override fun onReceivedSslError(v:WebView?,h:SslErrorHandler?,e:SslError?) {h?.cancel();if(e?.url==v?.url || e?.url==startUrl)showError("网站证书校验失败")}
            override fun onFormResubmission(v:WebView?,dontResend:Message?,resend:Message?) {
                dontResend?.sendToTarget();showError("已阻止重复提交，请返回业务页面核对结果")
            }
        }
        web.setDownloadListener{url,_,_,_,_->confirmExternal(url)}
        setComposeContent {
            BackHandler { goBack() }
            Scaffold(containerColor=AppTheme.colors.bgPrimaryColor,topBar={com.mychuannong.app.core.theme.SecondaryScaffoldHeader(serviceTitle,::goBack) {
                com.mychuannong.app.core.web.WebMoreMenu({web},::finish,onRefresh=::refresh)
            }}) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    if(loginEntry) Text(com.mychuannong.app.school.CampusLoginCopy.description,Modifier.padding(16.dp))
                    if(error!=null && Uri.parse(requestedUrl).host in setOf("ehall.sicau.edu.cn","jiaowu.sicau.edu.cn","op.sicau.edu.cn","ywtb.sicau.edu.cn"))
                        TextButton(onClick={returnFromVpn=true;web.loadUrl("https://webvpn.sicau.edu.cn/")}) { Text("校内服务登录") }
                    if(Uri.parse(startUrl).host=="jiaowu.sicau.edu.cn") TextButton(onClick={importOpened=false;readTimetable()},enabled=!readingTimetable && !loading) { Text(if(readingTimetable) "正在读取课表…" else "读取当前学期课表") }
                    if(intent.getBooleanExtra("auto_timetable",false)) {
                        TextButton(onClick={importOpened=false;openDiscoveredTimetable()},enabled=!loading && !readingTimetable) { Text("查找本页课表入口") }
                        if(timetableStatus.isNotBlank()) Text(timetableStatus,Modifier.padding(horizontal=16.dp,vertical=4.dp))
                    }
                    if(loading && error==null) {
                        LinearProgressIndicator(modifier=Modifier.fillMaxWidth().height(2.dp),color=AppTheme.colors.titleTopColor)
                        Text("正在连接学校服务…",Modifier.padding(16.dp))
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(factory={web},modifier=Modifier.fillMaxSize(),update={
                            it.visibility=if(error==null)android.view.View.VISIBLE else android.view.View.INVISIBLE
                        })
                        error?.let { message ->
                            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.bgPrimaryColor) {
                                Column(Modifier.padding(24.dp)) {
                                    Text(if(loginEntry) "校内服务登录" else "学校页面暂时无法打开",style=MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(16.dp))
                                    Text(message)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick={error=null;web.loadUrl(startUrl)}) { Text("重新加载") }
                                    TextButton(onClick={finish()}) { Text("返回服务栏目") }
                                }
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            runCatching { com.mychuannong.app.school.SicauNativeAuth.exportToWebView() }
            if(openedSessionRevision!=com.mychuannong.app.school.SicauNativeAuth.webRevision.value){finish();return@launch}
            // Let each official service validate the shared session and perform its own SSO redirect.
            if(state==null || web.restoreState(state)==null)if(!reuseSso(startUrl))web.loadUrl(startUrl)
        }
        lifecycleScope.launch {
            com.mychuannong.app.school.SicauNativeAuth.webRevision.collect { if(it!=openedSessionRevision){web.stopLoading();finish()} }
        }
    }
    private fun showError(message:String){loading=false;generation++;loadingProgress=100;error=if(loginEntry || Uri.parse(web.url.orEmpty()).host=="auth.sicau.edu.cn") com.mychuannong.app.school.CampusLoginCopy.failure else message}
    private fun goBack(){
        val studentService=SchoolWebPolicy.isStudentHall(requestedUrl)
        val current=web.url.orEmpty()
        // The hall is the service root. SSO/redirect history must not trap the user there.
        // A second back from an unchanged page also escapes JS history/redirect loops.
        if(backSource==current || studentService && (SchoolWebPolicy.isStudentHall(current) ||
            SchoolWebPolicy.isAuthentication(current) || error!=null || current.isBlank())) {
            web.stopLoading();finish();return
        }
        val h=web.copyBackForwardList()
        val index=SchoolWebPolicy.previousBusinessIndex((0 until h.size).map{h.getItemAtIndex(it).url},h.currentIndex)
        if(index==null)finish()else{
            backSource=current;web.stopLoading()
            error=null;web.goBackOrForward(index-h.currentIndex)
        }
    }
    private fun refresh(){
        if(serviceTitle=="校园缴费") android.app.AlertDialog.Builder(this).setTitle("重新打开缴费入口？").setMessage("请先核对未完成订单。本操作不会重新提交支付表单。").setNegativeButton("取消",null).setPositiveButton("打开入口"){_,_->web.loadUrl(startUrl)}.show()
        else web.reload()
    }
    private fun confirmExternal(url:String){
        val scheme=Uri.parse(url).scheme
        if(!SafeUrl.https(url) && scheme !in setOf("alipays","weixin")){showError("此跳转暂不支持，请使用官方渠道继续");return}
        android.app.AlertDialog.Builder(this).setTitle("打开外部应用？").setMessage(if(SafeUrl.https(url))"此页面将由浏览器打开。" else "继续官方支付流程；返回后请在订单页核对实际结果。").setNegativeButton("取消",null).setPositiveButton("继续"){_,_->external(url)}.show()
    }
    private fun external(url:String){
        if(!SafeUrl.https(url) && Uri.parse(url).scheme !in setOf("alipays","weixin"))return
        if(Uri.parse(url).scheme in setOf("alipays","weixin"))paymentReturn=true
        runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))}.onFailure{showError("未找到可打开此链接的应用")}
    }
    override fun onResume(){super.onResume();if(::web.isInitialized)web.onResume();if(paymentReturn){paymentReturn=false;android.app.AlertDialog.Builder(this).setTitle("核对支付状态").setMessage("请在学校订单页查看实际支付结果；不要重复提交订单。").setPositiveButton("查看当前页面",null).show()}}
    override fun onPause(){
        if(::web.isInitialized){web.onPause();CookieManager.getInstance().flush()}
        if(openedSessionRevision==com.mychuannong.app.school.SicauNativeAuth.webRevision.value)
            com.mychuannong.app.school.SicauNativeAuth.persistFromWebView()
        super.onPause()
    }
    override fun onSaveInstanceState(out:Bundle){
        // CAS history can contain one-use tickets. Reopen the module with shared SSO instead
        // of persisting its full authentication history in an unencrypted activity Bundle.
        if(Uri.parse(requestedUrl).host !in setOf("auth.sicau.edu.cn","xsgz.sicau.edu.cn","ywtb.sicau.edu.cn","op.sicau.edu.cn")) web.saveState(out)
        super.onSaveInstanceState(out)
    }
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);if(::web.isInitialized){web.stopLoading();web.destroy()};super.onDestroy()}
}

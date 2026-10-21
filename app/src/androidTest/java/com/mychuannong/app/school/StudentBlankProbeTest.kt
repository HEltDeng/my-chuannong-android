package com.mychuannong.app.school

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit diagnostic: only counts and error categories; no identity, tokens or page text. */
class StudentBlankProbeTest {
    @Test fun inspectStudentDocument() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("probeStudentBlank") == "true")
        val i=InstrumentationRegistry.getInstrumentation()
        val activity=i.startActivitySync(Intent(i.targetContext,com.mychuannong.app.SchoolWebActivity::class.java)
            .putExtra("url","https://webvpn.sicau.edu.cn/").putExtra("title","学工连接检查").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        android.os.SystemClock.sleep(4000)
        i.runOnMainSync {
            val field=activity.javaClass.getDeclaredField("web").apply { isAccessible=true }
            val w=field.get(activity) as WebView
            val original=w.webChromeClient
            w.webChromeClient=object:android.webkit.WebChromeClient() {
                override fun onCreateWindow(v:WebView?,d:Boolean,g:Boolean,m:android.os.Message?):Boolean = original?.onCreateWindow(v,d,g,m) ?: false
                override fun onConsoleMessage(m:android.webkit.ConsoleMessage):Boolean {
                    val text=m.message().lowercase()
                    if(text.contains("syntaxerror")) println("STUDENT_SYNTAX "+m.message().replace(Regex("https?://[^ ]+"),"[url]").replace(Regex("'[^']{4,}'"),"'[identifier]' ").take(180)+" line="+m.lineNumber()+" file="+android.net.Uri.parse(m.sourceId()).lastPathSegment)
                    println("STUDENT_CONSOLE level="+m.messageLevel()+" cors="+text.contains("cors")+" csp="+text.contains("content security policy")+" mime="+text.contains("mime")+" mixed="+text.contains("mixed content")+" module="+text.contains("module")+" syntax="+text.contains("syntaxerror")+" reference="+text.contains("referenceerror")+" type="+text.contains("typeerror")+" exportToken="+text.contains("token 'export'")+" importToken="+text.contains("token 'import'"))
                    return true
                }
            }
        }
        android.os.SystemClock.sleep(7000)
        i.runOnMainSync {
            val field=activity.javaClass.getDeclaredField("web").apply { isAccessible=true }
            (field.get(activity) as WebView).evaluateJavascript("""(function(){var e=Array.from(document.querySelectorAll('*')).find(e=>e.childElementCount===0 && e.textContent.trim().startsWith('https://xsgz.sicau.edu.cn/'));if(e)e.click();return !!e;})()""") { println("STUDENT_CLICK "+it) }
        }
        android.os.SystemClock.sleep(40000)
        val done=CountDownLatch(1)
        i.runOnMainSync {
            fun find(v:View):WebView? {
                if(v is WebView)return v
                if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n))?.let{return it}
                return null
            }
            val w=find(activity.window.decorView)
            if(w==null){println("STUDENT_PROBE no_webview");done.countDown()}
            else w.evaluateJavascript("""JSON.stringify({host:location.hostname,state:document.readyState,html:document.documentElement.outerHTML.length,text:(document.body&&document.body.innerText||'').length,scripts:document.scripts.length,frames:document.querySelectorAll('iframe,frame').length,resourceCount:performance.getEntriesByType('resource').length,error:(document.body.innerText.match(/net::ERR_[A-Z_]+/)||[])[0]||'',gateway:location.pathname==='/'||location.pathname==='/portal',httpsProxy:location.pathname.startsWith('/https/'),httpProxy:location.pathname.startsWith('/http/'),scriptFiles:Array.from(document.scripts).map(s=>({file:s.src?new URL(s.src).pathname.split('/').pop():'inline',type:s.type,host:s.src?new URL(s.src).hostname:'',protocol:s.src?new URL(s.src).protocol:'',integrity:!!s.integrity,crossOrigin:s.crossOrigin})),resources:performance.getEntriesByType('resource').map(r=>({file:new URL(r.name).pathname.split('/').pop(),status:r.responseStatus,bytes:r.transferSize})),redirectScript:Array.from(document.scripts).some(s=>/location[.](href|replace)/.test(s.text)),wechatScript:Array.from(document.scripts).some(s=>/MicroMessenger/.test(s.text)),elements:document.body?Array.from(document.body.children).map(e=>e.tagName):[]})""") {
                println("STUDENT_PROBE "+it);done.countDown()
            }
        }
        check(done.await(5,TimeUnit.SECONDS))
    }
}

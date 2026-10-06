package com.mychuannong.app.school

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TimetableWebExtractionTest {
    @Test fun renderedPageExtractsOnlyTimetableAndParses() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val real=InstrumentationRegistry.getArguments().getString("localHtmlProbe")=="true"
        val term="2026-2027-1"
        val page=if(real) {
            // Opt-in local private file, never packaged, uploaded, or included in test output.
            val document=Jsoup.parse(java.io.File(context.filesDir,"timetable-probe/page.html").readText())
            val selected=document.select("table").filter { t ->
                val row=t.select("tr").firstOrNull { it.closest("table")==t }
                row!=null && row.children().any { it.text().trim() in listOf("星期一","上课时间") }
            }
            assertEquals(2,selected.size)
            selected.joinToString("") { it.clone().apply { select("script,iframe,img,link,style,input,form").remove() }.outerHtml() }
        } else {
            "<table><tr><td colspan=2>时间</td>"+listOf("一","二","三","四","五","六","日").joinToString(""){"<td>星期$it</td>"}+"</tr>"+
            (0..4).joinToString("") { row -> "<tr><td>时段</td><td>${row*2+1}${row*2+2}节</td>"+(0..6).joinToString(""){day->"<td>"+(if(row==0&&day==0)"合成课程：合成教师<br>测试校区：测试室<br>1-16周(单周)(2学时)" else "")+"</td>"}+"</tr>" }+"</table>"
        }
        val latch=CountDownLatch(1)
        var result:String?=null
        var web:WebView?=null
        instrumentation.runOnMainSync {
            web=WebView(context).apply {
                settings.javaScriptEnabled=true
                settings.blockNetworkLoads=true
                webViewClient=object:WebViewClient() {
                    override fun onPageFinished(view:WebView,url:String) {
                        view.evaluateJavascript(TimetableHtmlReader.extractionScript) { value -> result=value;latch.countDown() }
                    }
                }
                loadDataWithBaseURL("https://jiaowu.sicau.edu.cn/","<html><body><h1>四川农业大学${term}学期课程表</h1><form><input value='synthetic-private'></form><table><tr><td>$page</td></tr></table></body></html>","text/html","UTF-8",null)
            }
        }
        try {
            assertTrue("WebView extraction timed out",latch.await(25,TimeUnit.SECONDS))
            val extracted=JSONObject(Gson().fromJson(result,String::class.java))
            assertEquals(term,extracted.optString("term"))
            val html=extracted.getString("html")
            assertFalse(html.contains("synthetic-private"))
            val parsed=TimetableHtmlReader.read(term,html)
            assertEquals(if(real)25 else 1,parsed.drafts.size)
        } finally { instrumentation.runOnMainSync { web?.destroy() } }
    }
    @Test fun discoversCurrentPageQueryAndRejectsAmbiguousLinks() {
        val inst=InstrumentationRegistry.getInstrumentation()
        fun discover(links:String):String {
            val ready=CountDownLatch(1); var value:String?=null;var web:WebView?=null
            inst.runOnMainSync {
                web=WebView(inst.targetContext).apply {
                    settings.javaScriptEnabled=true;settings.blockNetworkLoads=true
                    webViewClient=object:WebViewClient(){ override fun onPageFinished(view:WebView,url:String) {
                        view.evaluateJavascript(TimetableNavigation.script) { value=Gson().fromJson(it,String::class.java);ready.countDown() }
                    } }
                    loadDataWithBaseURL("https://jiaowu.sicau.edu.cn/xuesheng/gongxuan/gongxuan/index.asp","<html><body>$links</body></html>","text/html","UTF-8",null)
                }
            }
            try { assertTrue(ready.await(20,TimeUnit.SECONDS));return value.orEmpty() }
            finally { inst.runOnMainSync { web?.destroy() } }
        }
        for(id in listOf("27","93")) {
            assertEquals("https://jiaowu.sicau.edu.cn/xuesheng/gongxuan/gongxuan/kbbanji.asp?title_id1=$id&term=2027-2028-2",
                discover("<a href='kbbanji.asp?title_id1=$id&amp;term=2027-2028-2'>选课情况（课表）</a><a href='https://untrusted.example/kbbanji.asp'>课表</a>"))
        }
        assertEquals("",discover("<a href='kbbanji.asp?title_id1=27'>课表</a><a href='kbbanji.asp?title_id1=93'>课表</a>"))
        assertEquals("",discover("<a style='display:none' href='kbbanji.asp?title_id1=27'>课表</a>"))
    }
}

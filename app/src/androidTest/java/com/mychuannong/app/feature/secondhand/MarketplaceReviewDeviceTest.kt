package com.mychuannong.app.feature.secondhand

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.feature.common.ui.SecondhandActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.mychuannong.app.school.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate

/** Real WebView -> native bridge -> local API. Run only in the isolated test install.
 * The host test driver performs admin operations through the real authenticated API;
 * no administrator credential or token is ever placed on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class MarketplaceReviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefix = "联调验收-" + System.currentTimeMillis()
    private var sequence = 0
    @Test fun sellAndWantedCompleteTheDurableReviewWorkflow() {
        check(context.packageName == "com.mychuannong.app.marketreviewtest")
        prepareSyntheticBinding()
        ActivityScenario.launch<SecondhandActivity>(SecondhandActivity::class.java).use { scenario ->
            waitFor(scenario,"window.CampusSecondhand?.ready===true",45)
            for (kind in listOf("sell","want")) {
                val id=publish(scenario,kind,"通过下架")
                assertPrivate(scenario,id,kind)
                admin("approve",id)
                waitStatus(scenario,id,"PUBLISHED")
                home(scenario,kind)
                waitFor(scenario,"Boolean(document.querySelector(${quote("#listing [data-id='$id']")}))")
                click(scenario,"#listing [data-id='$id']")
                waitFor(scenario,"document.querySelectorAll('.detail-gallery img').length===2 && Array.from(document.querySelectorAll('.detail-gallery img')).every(i=>i.complete&&i.naturalWidth>0)")
                assertJs(scenario,"(()=>{const i=document.querySelector('.detail-gallery img');return Math.abs(i.getBoundingClientRect().height/i.getBoundingClientRect().width-i.naturalHeight/i.naturalWidth)<0.03})()")
                screenshot(scenario,"market-$kind-published.png")
                admin("take_down",id)
                waitStatus(scenario,id,"VIOLATION")
                notifications(scenario,id,"已下架")
                screenshot(scenario,"market-$kind-notification.png")
                assertPrivate(scenario,id,kind)
                admin("delete",id)
                waitDeleted(scenario,id)
                val rejected=publish(scenario,kind,"驳回")
                admin("reject",rejected)
                waitStatus(scenario,rejected,"VIOLATION")
                notifications(scenario,rejected,"审核驳回")
                assertPrivate(scenario,rejected,kind)
                admin("delete",rejected)
                waitDeleted(scenario,rejected)
                val pending=publish(scenario,kind,"待审删除")
                admin("delete",pending)
                waitDeleted(scenario,pending)
                val approved=publish(scenario,kind,"发布删除")
                admin("approve",approved)
                waitStatus(scenario,approved,"PUBLISHED")
                click(scenario,".my-post[data-id='$approved'] [data-action=detail]")
                waitFor(scenario,"Boolean(document.querySelector('.detail-gallery'))")
                click(scenario,"[data-action=photo]")
                waitFor(scenario,"document.querySelector('#market-photo-viewer').open")
                admin("delete",approved)
                waitFor(scenario,"!document.querySelector('#market-photo-viewer').open && !document.querySelector('.detail-gallery')",70,true)
                waitDeleted(scenario,approved)
            }
            val previewSell=publish(scenario,"sell","详情预览")
            val previewWant=publish(scenario,"want","详情预览")
            File(context.getExternalFilesDir(null),"market-review-device-result.json").writeText(JSONObject().put("passed",true).put("run",prefix).put("adminOperations",sequence).put("previewSell",previewSell).put("previewWant",previewWant).toString())
        }
    }
    private fun prepareSyntheticBinding() = runBlocking {
        // Same authenticated fixture approach as BindingEntryDeviceTest. Every school
        // request is intercepted; only the separate marketplace API uses the network.
        val owner="20269999"
        PrivacyConsentManager.acceptAppConsent(context)
        val jar=SicauJiaowuCookieJar()
        val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request=chain.request()
            val body=when(request.url.toString()) {
                SicauJiaowuProtocol.LOGIN_PAGE -> {
                    jar.saveFromResponse(request.url,listOf(requireNotNull(Cookie.parse(request.url,"ASPSESSIONIDTEST=synthetic-cookie; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                    "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                }
                SicauJiaowuProtocol.LOGIN_POST -> "<script>location='student.asp'</script>"
                else -> "<table><tr><td>学号</td><td>$owner</td></tr></table>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic").body(body.toByteArray(charset("GBK")).toResponseBody()).build()
        }.build()
        val session=SicauJiaowuSession(jar,client,{})
        session.login(owner,"synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
    }
    private fun publish(s:ActivityScenario<SecondhandActivity>,kind:String,label:String):String {
        home(s,kind);click(s,"[data-action=publish]");waitFor(s,"Boolean(document.querySelector('#publish-form'))")
        val title="$prefix-$kind-$label"
        for((name,value) in mapOf("title" to title,"description" to "仅用于联调验收。\n标题、正文、校区、品类、联系方式与两张图片应完整展示。".repeat(7),"wechat" to "market_synthetic_test")) {
            js(s,"(()=>{const e=document.querySelector('[name=$name]');e.value=${quote(value)};e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
        }
        js(s,"""(()=>{const input=document.querySelector('#publish-images'),dt=new DataTransfer();for(const [w,h] of [[180,1200],[1000,160]]){const c=document.createElement('canvas');c.width=w;c.height=h;const g=c.getContext('2d');g.fillStyle='#28744f';g.fillRect(0,0,w,h);g.fillStyle='#f6d970';g.fillRect(0,h-80,w,80);g.fillStyle='white';g.font='24px sans-serif';g.fillText('IMAGE TOP',5,30);g.fillText('IMAGE END',5,h-25);const bytes=atob(c.toDataURL('image/png').split(',')[1]);dt.items.add(new File([Uint8Array.from(bytes,x=>x.charCodeAt(0))],'fixture-'+h+'.png',{type:'image/png'}));}input.files=dt.files;input.dispatchEvent(new Event('change',{bubbles:true}));return true})()""")
        waitFor(s,"document.querySelectorAll('.photo-thumb').length===2 && !document.querySelector('#publish-form [type=submit]').disabled")
        click(s,"#publish-form [type=submit]")
        waitFor(s,"location.hash==='#mine' && document.body.innerText.includes(${quote(title)})")
        val id=JSONArray("["+js(s,"Array.from(document.querySelectorAll('.my-post')).find(e=>e.innerText.includes(${quote(title)})).dataset.id")+"]").getString(0)
        assertJs(s,"document.querySelector(${quote(".my-post[data-id='$id'] .review-status")}).textContent==='待审核'")
        screenshot(s,"market-$kind-pending.png")
        return id
    }
    private fun admin(action:String,id:String) {
        sequence++
        File(context.getExternalFilesDir(null),"market-review-stage.json").writeText(JSONObject().put("run",prefix).put("sequence",sequence).put("action",action).put("id",id).toString())
    }
    private fun home(s:ActivityScenario<SecondhandActivity>,kind:String) {
        js(s,"location.hash='';true");waitFor(s,"Boolean(document.querySelector('[data-action=tab]'))");click(s,"[data-action=tab][data-value='$kind']")
    }
    private fun mine(s:ActivityScenario<SecondhandActivity>) {js(s,"location.hash='mine';true");waitFor(s,"Boolean(document.querySelector('#mine-listing'))")}
    private fun waitStatus(s:ActivityScenario<SecondhandActivity>,id:String,status:String) {
        mine(s)
        waitFor(s,"Boolean(document.querySelector(${quote(".my-post[data-id='$id'] .review-status.${status.lowercase()}")}))",70,true)
    }
    private fun assertPrivate(s:ActivityScenario<SecondhandActivity>,id:String,kind:String) {
        home(s,kind);js(s,"window.CampusSecondhand.refresh();true");Thread.sleep(300)
        assertJs(s,"!document.querySelector(${quote("#listing [data-id='$id']")})")
    }
    private fun notifications(s:ActivityScenario<SecondhandActivity>,id:String,result:String) {
        click(s,"[data-sync=notices]")
        waitFor(s,"Array.from(document.querySelectorAll('.review-notice')).some(e=>e.querySelector(${quote("[data-id='$id']")}) && e.textContent.includes(${quote(result)}) && e.textContent.includes('验收测试：请补充真实描述与清晰图片。'))")
    }
    private fun waitDeleted(s:ActivityScenario<SecondhandActivity>,id:String) {
        mine(s);waitFor(s,"!document.querySelector(${quote(".my-post[data-id='$id']")})",70,true)
        s.onActivity{findWeb(it.window.decorView)!!.reload()};waitFor(s,"window.CampusSecondhand?.ready===true",40);mine(s)
        assertJs(s,"!document.querySelector(${quote(".my-post[data-id='$id']")})")
        click(s,"[data-sync=notices]");assertJs(s,"!document.querySelector(${quote(".review-notice [data-id='$id']")})")
    }
    private fun findWeb(v:View):WebView? {if(v is WebView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findWeb(v.getChildAt(i))?.let{return it};return null}
    private fun quote(s:String)=JSONObject.quote(s)
    private fun js(s:ActivityScenario<SecondhandActivity>,expression:String):String {
        val done=CountDownLatch(1);var result="null"
        s.onActivity{findWeb(it.window.decorView)!!.evaluateJavascript(expression){v->result=v;done.countDown()}}
        assertTrue("JS callback timeout",done.await(15,TimeUnit.SECONDS));return result
    }
    private fun assertJs(s:ActivityScenario<SecondhandActivity>,expression:String) {assertEquals(expression,"true",js(s,"Boolean($expression)"))}
    private fun click(s:ActivityScenario<SecondhandActivity>,selector:String) {assertJs(s,"(()=>{const e=document.querySelector(${quote(selector)});if(!e||e.disabled)return false;e.click();return true})()")}
    private fun waitFor(s:ActivityScenario<SecondhandActivity>,expression:String,seconds:Long=20,refresh:Boolean=false) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);var nextRefresh=0L
        while(System.nanoTime()<end){if(js(s,"Boolean($expression)")=="true")return;if(refresh&&System.currentTimeMillis()>nextRefresh){js(s,"window.CampusSecondhand.refresh();true");nextRefresh=System.currentTimeMillis()+1500};Thread.sleep(100)}
        assertJs(s,expression)
    }
    private fun screenshot(s:ActivityScenario<SecondhandActivity>,name:String) {
        val painted=CountDownLatch(1);s.onActivity{findWeb(it.window.decorView)!!.postVisualStateCallback(0,object:WebView.VisualStateCallback(){override fun onComplete(requestId:Long){painted.countDown()}})}
        assertTrue(painted.await(10,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()?:return
        File(context.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}

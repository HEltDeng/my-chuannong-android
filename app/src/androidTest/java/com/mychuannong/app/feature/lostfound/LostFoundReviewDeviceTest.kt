package com.mychuannong.app.feature.lostfound

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
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
class LostFoundReviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefix = "失物验收-" + (System.currentTimeMillis() % 1000000000)
    private var sequence = 0
    @Test fun lostAndFoundCompleteReviewedLifecycleWithCompletion() {
        check(context.packageName == "com.mychuannong.app.lostreviewtest")
        prepareSyntheticBinding()
        ActivityScenario.launch<LostFoundActivity>(LostFoundActivity::class.java).use { s ->
            waitFor(s,"window.CampusLostFound?.ready===true",45)
            for(kind in listOf("lost","found")) {
                val id=publish(s,kind,"通过下架")
                privatePost(s,id,kind)
                admin("approve",id);waitStatus(s,id,"published")
                home(s,kind);waitFor(s,"Boolean(document.querySelector(${quote(".lf-home-scroll [data-id='$id']")}))")
                click(s,".lf-item[data-id='$id']");waitFor(s,"Boolean(document.querySelector('.lf-detail-photo'))")
                waitFor(s,"document.querySelector('.lf-detail-image').complete && document.querySelector('.lf-detail-image').naturalWidth>0")
                assertJs(s,"(()=>{const i=document.querySelector('.lf-detail-image');return Math.abs(i.getBoundingClientRect().height/i.getBoundingClientRect().width-i.naturalHeight/i.naturalWidth)<0.03})()")
                click(s,".lf-dock>.lf-primary");waitFor(s,"document.querySelector('.lf-dock>.lf-primary')?.textContent==='重新开放'")
                privatePost(s,id,kind)
                mine(s);click(s,".lf-item[data-id='$id']");waitFor(s,"Boolean(document.querySelector('.lf-detail'))")
                click(s,".lf-dock>.lf-primary");waitFor(s,"document.querySelector('.lf-dock>.lf-primary')?.textContent!=='重新开放'")
                home(s,kind);waitFor(s,"Boolean(document.querySelector(${quote(".lf-home-scroll [data-id='$id']")}))")
                admin("take_down",id);waitStatus(s,id,"violation");notifications(s,id,"已下架")
                screenshot(s,"lost-$kind-notification.png");privatePost(s,id,kind)
                admin("delete",id);waitDeleted(s,id)
                val rejected=publish(s,kind,"驳回")
                admin("reject",rejected);waitStatus(s,rejected,"violation");notifications(s,rejected,"审核驳回")
                mine(s);click(s,".lf-item[data-id='$rejected']");waitFor(s,"Boolean(document.querySelector('.lf-detail'))")
                assertJs(s,"document.querySelector('.lf-dock>.lf-primary').disabled")
                click(s,".lf-management button:first-child");waitFor(s,"Boolean(document.querySelector('#lf-publish-form'))")
                fill(s,"补充描述","修改补充后的真实物品特征，仅用于联调测试")
                click(s,"button[form=lf-publish-form]");waitStatus(s,rejected,"pending");privatePost(s,rejected,kind)
                admin("delete",rejected);waitDeleted(s,rejected)
                val pending=publish(s,kind,"待审删除");admin("delete",pending);waitDeleted(s,pending)
                val approved=publish(s,kind,"发布删除");admin("approve",approved);waitStatus(s,approved,"published")
                click(s,".lf-item[data-id='$approved']");waitFor(s,"Boolean(document.querySelector('.lf-detail-photo'))")
                click(s,".lf-detail-photo");waitFor(s,"Boolean(document.querySelector('.lf-full-image'))")
                admin("delete",approved);waitFor(s,"!document.querySelector('.lf-full-image') && !document.querySelector('.lf-detail')",70,true);waitDeleted(s,approved)
            }
            val lost=publish(s,"lost","详情预览");val found=publish(s,"found","详情预览")
            File(context.getExternalFilesDir(null),"lost-review-device-result.json").writeText(JSONObject().put("passed",true).put("run",prefix).put("adminOperations",sequence).put("previewLost",lost).put("previewFound",found).toString())
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
    private fun publish(s:ActivityScenario<LostFoundActivity>,kind:String,label:String):String {
        home(s,kind);click(s,".lf-fab");waitFor(s,"Boolean(document.querySelector('#lf-publish-form'))")
        val title="$prefix-$kind-$label"
        fill(s,"物品名称",title);fill(s,"具体地点","图书馆二楼阅览区");fill(s,"物品日期","2026-10-19")
        fill(s,"补充描述","仅用于联调验收，物品特征、地点、日期和联系方式均需完整展示。".repeat(7));fill(s,"联系方式","lost_synthetic_test")
        js(s,"""(()=>{const dt=new DataTransfer(),c=document.createElement('canvas');c.width=180;c.height=1200;const g=c.getContext('2d');g.fillStyle='#28744f';g.fillRect(0,0,180,1200);g.fillStyle='#f6d970';g.fillRect(0,1120,180,80);g.fillStyle='white';g.font='24px sans-serif';g.fillText('IMAGE TOP',5,30);g.fillText('IMAGE END',5,1180);const bytes=atob(c.toDataURL('image/png').split(',')[1]);dt.items.add(new File([Uint8Array.from(bytes,x=>x.charCodeAt(0))],'fixture.png',{type:'image/png'}));const input=document.querySelector('input[type=file]');input.files=dt.files;input.dispatchEvent(new Event('change',{bubbles:true}));return true})()""")
        waitFor(s,"Boolean(document.querySelector('.lf-photo-picker img')) && !document.querySelector('button[form=lf-publish-form]').disabled")
        click(s,"button[form=lf-publish-form]");waitFor(s,"document.querySelector('h1').textContent==='我的发布' && document.body.innerText.includes(${quote(title)})")
        val id=JSONArray("["+js(s,"Array.from(document.querySelectorAll('.lf-item')).find(e=>e.textContent.includes(${quote(title)})).dataset.id")+"]").getString(0)
        assertJs(s,"Boolean(document.querySelector(${quote(".lf-item[data-id='$id'] .lf-review-status.pending")}))")
        screenshot(s,"lost-$kind-pending.png");return id
    }
    private fun fill(s:ActivityScenario<LostFoundActivity>,label:String,value:String) {
        assertJs(s,"(()=>{const e=document.querySelector(${quote("[aria-label='$label']")});const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(e,${quote(value)});e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
    }
    private fun home(s:ActivityScenario<LostFoundActivity>,kind:String) {
        repeat(5){if(js(s,"document.querySelector('h1')?.textContent==='失物招领'")=="true")return@repeat;js(s,"window.CampusLostFound.back();true");Thread.sleep(100)}
        waitFor(s,"document.querySelector('h1').textContent==='失物招领'")
        click(s,if(kind=="lost")".lf-tabs button:first-child" else ".lf-tabs button:last-child")
    }
    private fun mine(s:ActivityScenario<LostFoundActivity>) {
        home(s,"found");click(s,".lf-header .lf-text-button");waitFor(s,"document.querySelector('h1').textContent==='我的发布'")
        click(s,".lf-mine-tabs button:first-child")
    }
    private fun waitStatus(s:ActivityScenario<LostFoundActivity>,id:String,status:String) {
        mine(s);waitFor(s,"Boolean(document.querySelector(${quote(".lf-item[data-id='$id'] .lf-review-status.$status")}))",70,true)
    }
    private fun privatePost(s:ActivityScenario<LostFoundActivity>,id:String,kind:String) {
        home(s,kind);js(s,"window.CampusLostFound.refresh();true");Thread.sleep(300)
        assertJs(s,"!document.querySelector(${quote(".lf-home-scroll [data-id='$id']")})")
    }
    private fun notifications(s:ActivityScenario<LostFoundActivity>,id:String,result:String) {
        click(s,".lf-sync button:first-child");waitFor(s,"Array.from(document.querySelectorAll('.lf-notice')).some(n=>n.dataset.post===${quote(id)}&&n.textContent.includes(${quote(result)})&&n.textContent.includes('验收测试：请补充真实描述与清晰图片。'))")
    }
    private fun waitDeleted(s:ActivityScenario<LostFoundActivity>,id:String) {
        mine(s);waitFor(s,"!document.querySelector(${quote(".lf-item[data-id='$id']")})",70,true)
        s.onActivity{findWeb(it.window.decorView)!!.reload()};waitFor(s,"window.CampusLostFound?.ready===true",40);mine(s)
        assertJs(s,"!document.querySelector(${quote(".lf-item[data-id='$id']")})")
        click(s,".lf-sync button:first-child");assertJs(s,"!document.querySelector(${quote(".lf-notice[data-post='$id']")})")
    }
    private fun admin(action:String,id:String) {
        sequence++;File(context.getExternalFilesDir(null),"lost-review-stage.json").writeText(JSONObject().put("run",prefix).put("sequence",sequence).put("action",action).put("id",id).toString())
    }
    private fun findWeb(v:View):WebView? {if(v is WebView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findWeb(v.getChildAt(i))?.let{return it};return null}
    private fun quote(s:String)=JSONObject.quote(s)
    private fun js(s:ActivityScenario<LostFoundActivity>,expression:String):String {
        val done=CountDownLatch(1);var result="null"
        s.onActivity{findWeb(it.window.decorView)!!.evaluateJavascript(expression){v->result=v;done.countDown()}}
        assertTrue("JS callback timeout",done.await(15,TimeUnit.SECONDS));return result
    }
    private fun assertJs(s:ActivityScenario<LostFoundActivity>,expression:String) {assertEquals(expression,"true",js(s,"Boolean($expression)"))}
    private fun click(s:ActivityScenario<LostFoundActivity>,selector:String) {assertJs(s,"(()=>{const e=document.querySelector(${quote(selector)});if(!e||e.disabled)return false;e.click();return true})()")}
    private fun waitFor(s:ActivityScenario<LostFoundActivity>,expression:String,seconds:Long=20,refresh:Boolean=false) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);var nextRefresh=0L
        while(System.nanoTime()<end){if(js(s,"Boolean($expression)")=="true")return;if(refresh&&System.currentTimeMillis()>nextRefresh){js(s,"window.CampusLostFound.refresh();true");nextRefresh=System.currentTimeMillis()+1500};Thread.sleep(100)}
        assertJs(s,expression)
    }
    private fun screenshot(s:ActivityScenario<LostFoundActivity>,name:String) {
        val painted=CountDownLatch(1);s.onActivity{findWeb(it.window.decorView)!!.postVisualStateCallback(0,object:WebView.VisualStateCallback(){override fun onComplete(requestId:Long){painted.countDown()}})}
        assertTrue(painted.await(10,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()?:return
        File(context.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}

package com.mychuannong.app.school
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PaymentRenderingTest {
 @Test fun officialMobilePageRenders() {
  val i=InstrumentationRegistry.getInstrumentation()
  val a=i.startActivitySync(android.content.Intent(i.targetContext,com.mychuannong.app.SchoolWebActivity::class.java).putExtra("url","https://op.sicau.edu.cn/").putExtra("title","校园缴费").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  android.os.SystemClock.sleep(20000)
  val done=java.util.concurrent.CountDownLatch(1)
  var rendered=false
  i.runOnMainSync {
   fun find(v:android.view.View):android.webkit.WebView? {if(v is android.webkit.WebView)return v;if(v is android.view.ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n))?.let{return it};return null}
   find(a.window.decorView)!!.evaluateJavascript("document.querySelector('ion-nav-view').getBoundingClientRect().height>300 && Array.from(document.querySelectorAll('button')).some(b=>b.textContent.includes('校内师生登录') && b.getBoundingClientRect().width>200)"){rendered=it=="true";done.countDown()}
  }
  org.junit.Assert.assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS))
  org.junit.Assert.assertTrue("Official mobile viewport must have a nonzero visible height",rendered)
  val b=i.uiAutomation.takeScreenshot();java.io.File(i.targetContext.filesDir,"payment-render.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  i.runOnMainSync{a.finish()}
 }
}

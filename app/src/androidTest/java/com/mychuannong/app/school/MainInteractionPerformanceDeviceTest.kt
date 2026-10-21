package com.mychuannong.app.school

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import com.mychuannong.app.core.main.navigation.MainTabNavigator
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.security.SchoolAccessGate
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same device/build mode/gestures before and after. Emulator timings are diagnostic,
 * not a claim of physical-device FPS. Only isolated synthetic school data is used. */
class MainInteractionPerformanceDeviceTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val owner="20269982"
    private lateinit var navigator: MainTabNavigator

    @Before fun setup()=runBlocking {
        check(context.packageName.endsWith(".perftest"))
        SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
        val jar=SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.ORIGIN.toHttpUrl(), listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type","text/html; charset=utf-8")
                .body("<table><tr><td>学号</td><td>$owner</td></tr></table>".toResponseBody()).build()
        }.build()
        val session=SicauJiaowuSession(jar,client,{})
        session.fetchPersonalInfo(owner);SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner);SchoolAccessGate.restoreBinding()
        Unit
    }
    @After fun cleanup()=runBlocking { SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared() }

    private fun select(label:String) {
        // Use the same navigator as the bar while leaving animations on the real
        // Choreographer. Compose's test clock can otherwise fast-forward whole animations.
        val index=listOf("首页","服务","我的").indexOf(label)
        inst.runOnMainSync { assertTrue(navigator.select(index)) }
        SystemClock.sleep(550)
        inst.waitForIdleSync()
    }
    private fun swipe(up:Boolean) {
        val width=context.resources.displayMetrics.widthPixels.toFloat()
        val height=context.resources.displayMetrics.heightPixels.toFloat()
        val start=if(up) height*.72f else height*.32f
        val end=if(up) height*.32f else height*.72f
        val down=SystemClock.uptimeMillis()
        for(i in 0..24) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),when(i){0->MotionEvent.ACTION_DOWN;24->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                width*.5f,start+(end-start)*i/24f,0)
            inst.sendPointerSync(event);event.recycle();SystemClock.sleep(16)
        }
        SystemClock.sleep(450)
    }
    private fun measure(window:Window,name:String,block:()->Unit):JSONObject {
        val thread=HandlerThread("frame-test").apply { start() }
        val handler=Handler(thread.looper)
        val frames=Collections.synchronizedList(mutableListOf<LongArray>())
        val listener=Window.OnFrameMetricsAvailableListener { _,metric,_ ->
            if(metric.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0L) frames.add(longArrayOf(
                metric.getMetric(FrameMetrics.TOTAL_DURATION),
                metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                metric.getMetric(FrameMetrics.DRAW_DURATION)))
        }
        inst.runOnMainSync { window.addOnFrameMetricsAvailableListener(listener,handler) }
        try { block() } finally { inst.runOnMainSync { window.removeOnFrameMetricsAvailableListener(listener) } }
        val drained=CountDownLatch(1);handler.post { drained.countDown() };assertTrue(drained.await(5,TimeUnit.SECONDS));thread.quitSafely()
        val sample=synchronized(frames){frames.toList()}
        assertTrue("Expected real rendered frames",sample.size>20)
        fun percentile(index:Int,p:Double):Double {
            val sorted=sample.map { it[index]/1_000_000.0 }.sorted()
            return sorted[((sorted.size-1)*p).toInt()]
        }
        return JSONObject().put("scenario",name).put("frames",sample.size)
            .put("totalP50Ms",percentile(0,.5)).put("totalP95Ms",percentile(0,.95))
            .put("layoutP95Ms",percentile(1,.95)).put("drawP95Ms",percentile(2,.95))
            .put("over32ms",sample.count{it[0]>32_000_000}).put("over100ms",sample.count{it[0]>100_000_000})
    }
    @Test fun repeatedTabSwitchesAndServiceScrolls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var window:Window?=null
            scenario.onActivity {
                window=it.window
                navigator=MainActivity::class.java.getDeclaredMethod("getMainTabNavigator")
                    .apply { isAccessible=true }.invoke(it) as MainTabNavigator
            }
            inst.waitForIdleSync();SystemClock.sleep(1500)
            repeat(2){select("服务");select("我的");select("首页")}
            val result=JSONObject()
            result.put("tabs",measure(requireNotNull(window),"tabs"){repeat(4){select("服务");select("我的");select("首页")}})
            select("服务")
            result.put("scroll",measure(requireNotNull(window),"service-scroll"){repeat(4){swipe(true);swipe(false)}})
            assertTrue(SchoolAccessGate.isAllowed)
            val label=InstrumentationRegistry.getArguments().getString("performanceLabel")?:"sample"
            File(context.getExternalFilesDir(null),"performance-$label.json").writeText(result.toString(2))
        }
    }
}

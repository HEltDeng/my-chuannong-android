package com.mychuannong.app.school

import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.common.ui.ExamArrangementActivity
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.mychuannong.app.settings.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** No actual school network; isolated install and synthetic cache/credential fixtures only. */
class DualAuthDeviceTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val owner="20260001"
    private val restart get()=InstrumentationRegistry.getArguments().getString("dualRestart")
    private val credential get()=AcademicCredential(owner,"synthetic-secret-for-device-test",SicauJiaowuRoute.Direct)
    @Before fun setup()=runBlocking {
        check(listOf(".dualauth", ".serviceflowtest", ".accountflowtest").any(context.packageName::endsWith))
        if(restart != "verify") { SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared() }
        PrivacyConsentManager.acceptAppConsent(context)
    }
    @After fun cleanup()=runBlocking {
        if(restart != "seed") { SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared() }
    }
    @Test fun credentialVaultIsEncryptedAndTamperFailsClosed() {
        assertNull(AcademicCredentialVault.read())
        AcademicCredentialVault.remember(credential)
        val value=requireNotNull(AcademicCredentialVault.read())
        assertEquals(owner,value.owner); assertEquals(credential.password,value.password)
        val file=File(context.noBackupFilesDir,"academic-opt-in.enc")
        val bytes=file.readBytes()
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(credential.password))
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(owner))
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(AcademicCredentialVault.read())
    }
    @Test fun logoutDeletesConsentAndAllSecrets()=runBlocking {
        AcademicCredentialVault.remember(credential)
        val before=AcademicCredentialVault.revision
        SicauNativeAuth.clear(); SicauNativeAuth.awaitCleared()
        assertNull(AcademicCredentialVault.read())
        assertTrue(AcademicCredentialVault.revision>before)
        assertFalse(File(context.noBackupFilesDir,"academic-opt-in.enc").exists())
    }
    @Test fun clearingAcademicSessionDoesNotClearUnifiedStateOrConsent() {
        AcademicCredentialVault.remember(credential)
        UnifiedAuthState.move(UnifiedAuthPhase.VALID)
        SicauWebVpn.move(WebVpnPhase.AVAILABLE)
        SicauJiaowuAuth.clear()
        assertEquals(UnifiedAuthPhase.VALID,UnifiedAuthState.phase.value)
        assertEquals(WebVpnPhase.AVAILABLE,SicauWebVpn.phase.value)
        assertNotNull(AcademicCredentialVault.read())
    }
    @Test fun recoveryDefaultsOnAndFirstVpnLoginIsRequired() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[NativeBindingViewModel::class.java]
                assertTrue(model.rememberAcademic)
                assertFalse(model.state.value.canLogin)
                assertFalse(model.state.value.canLogin)
                assertNull(AcademicCredentialVault.read())
                assertTrue(model.startVpnBinding()); model.vpnFinished(true)
            }
            inst.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(ViewModelProvider(activity)[NativeBindingViewModel::class.java].rememberAcademic)
                // Enabling recovery is not a credential: only successful matched login can save one.
                assertNull(AcademicCredentialVault.read())
            }
            if(InstrumentationRegistry.getArguments().getString("dualScreenshots")=="true") {
                scenario.onActivity { activity ->
                    activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    val field=activity.findViewById<View>(R.id.et_student_password)
                    field.requestRectangleOnScreen(android.graphics.Rect(0,0,field.width,field.height),true)
                }
                snapshot("dual-auth-default-recovery")
            }
        }
    }
    private fun snapshot(name:String) {
        inst.waitForIdleSync(); inst.uiAutomation.waitForIdle(300,5000)
        val bitmap=inst.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private val course get()=TimeTableMySubject(courseName="合成课程",classroom="合成教室",teacher="合成教师",
        weeks=listOf(1,2),start=1,step=2,weekday=1,term="2026-2027-1",studentId=owner,source="SCHOOL")
    private val grade get()=Grade("synthetic","2026-2027-1","合成课程","88","","2","32","3.5","","考试","必修","专业","")
    private val exam get()=ExamArrange("雅安","2026-2027-1","synthetic","合成考试","合成教师","2026-12-01 09:00",
        null,null,"合成考场","1","","")
    private fun reader(failed: Boolean)=object : SchoolReader {
        override suspend fun identity():SchoolRead<String> = SchoolRead.Success(owner)
        override suspend fun courses():SchoolRead<CourseBatch> = if(failed) SchoolRead.Failure(SyncPhase.FAILED,"网络异常") else SchoolRead.Success(CourseBatch("2026-2027-1",listOf(course)))
        override suspend fun grades():SchoolRead<List<Grade>> = if(failed) SchoolRead.Failure(SyncPhase.FAILED,"网络异常") else SchoolRead.Success(listOf(grade))
        override suspend fun exams():SchoolRead<List<ExamArrange>> = if(failed) SchoolRead.Failure(SyncPhase.FAILED,"网络异常") else SchoolRead.Success(listOf(exam))
    }
    @Test fun failedRefreshKeepsAllThreeCachesAndSuccessfulTimestamps()=runBlocking {
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolSyncEngine.perform(reader(false),true)
        val stamps=SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess }
        assertTrue(stamps.values.all { it != null })
        SchoolSyncEngine.perform(reader(true),true)
        assertEquals(stamps,SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess })
        assertTrue(SchoolSyncEngine.items.value.values.all { it.phase == SyncPhase.FAILED })
        assertEquals("合成课程",CoursesDataBase.getDatabase(context).courseDao().getAllCoursesNow().single().courseName)
        assertEquals("88",ScoreCache.getGrades()!!.single().grade)
        assertEquals("合成考试",ExamArrangementCache().getExamArrangement()!!.single().courseNameval)
    }
    @Test fun seedCredentialAcrossProcessDeath() {
        Assume.assumeTrue(restart=="seed")
        AcademicCredentialVault.remember(credential)
        AcademicCredentialVault.save(credential.attempted(123456))
    }
    @Test fun examOpensCachedAndFailedPullNeverNavigatesToLogin()=runBlocking {
        var networkDown=false
        val requests=java.util.concurrent.atomic.AtomicInteger()
        val jar=SicauJiaowuCookieJar()
        jar.acceptRawCookies(okhttp3.HttpUrl.Builder().scheme("https").host("jiaowu.sicau.edu.cn").build(),
            listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            requests.incrementAndGet()
            if(networkDown) throw java.io.IOException("synthetic offline")
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type","text/html; charset=utf-8")
                .body(okhttp3.ResponseBody.create(null,"<table><tr><td>学号</td><td>$owner</td></tr></table>")).build()
        }.build()
        val session=SicauJiaowuSession(jar,client,{})
        session.fetchPersonalInfo(owner); SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        com.mychuannong.app.core.security.SchoolAccessGate.restoreBinding()
        SchoolSyncEngine.perform(reader(false),true)
        val before=requests.get(); networkDown=true
        val monitor=inst.addMonitor(BindingUserActivity::class.java.name,null,false)
        try {
            ActivityScenario.launch(ExamArrangementActivity::class.java).use { scenario ->
                inst.waitForIdleSync()
                assertEquals(before,requests.get())
                var x=0f; var y=0f
                scenario.onActivity { activity ->
                    val view=activity.findViewById<View>(R.id.pull_refresh)
                    val location=IntArray(2);view.getLocationOnScreen(location)
                    x=location[0]+view.width/2f;y=location[1]+40f
                    assertTrue(activity.findViewById<android.widget.TextView>(R.id.cache_status).text.contains("最后更新"))
                }
                val down=android.os.SystemClock.uptimeMillis()
                for(step in 0..24) {
                    val action=when(step) { 0 -> android.view.MotionEvent.ACTION_DOWN;24 -> android.view.MotionEvent.ACTION_UP;else -> android.view.MotionEvent.ACTION_MOVE }
                    inst.sendPointerSync(android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y+step*22f,0))
                    kotlinx.coroutines.delay(18)
                }
                kotlinx.coroutines.withTimeout(10000) { while(requests.get()==before || SchoolSyncEngine.items.value["exams"]?.phase==SyncPhase.LOADING) kotlinx.coroutines.delay(50) }
                inst.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<android.widget.TextView>(R.id.cache_status).text.contains("最后更新"))
                    assertTrue(activity.findViewById<View>(R.id.recycler_view).isShown)
                }
                assertEquals("合成考试",ExamArrangementCache().getExamArrangement()!!.single().courseNameval)
                assertEquals(0,monitor.hits)
                if(InstrumentationRegistry.getArguments().getString("dualScreenshots")=="true") snapshot("dual-auth-cached-exam")
            }
        } finally { inst.removeMonitor(monitor) }
    }
    @Test fun verifyCredentialAcrossProcessDeath() {
        Assume.assumeTrue(restart=="verify")
        val value=requireNotNull(AcademicCredentialVault.read())
        assertEquals(owner,value.owner); assertEquals(credential.password,value.password)
        assertEquals(1,value.failures); assertEquals(183456,value.retryAfter)
    }
}

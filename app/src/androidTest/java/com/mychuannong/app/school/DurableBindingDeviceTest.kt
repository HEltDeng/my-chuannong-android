package com.mychuannong.app.school

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.network.ApiResponse
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.core.security.SchoolEntryActivity
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.common.ui.ScoreInquiryActivity
import com.mychuannong.app.feature.common.viewModel.ScoreInquiryViewModel
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.feature.timetable.viewmodel.TimeTableViewModel
import com.mychuannong.app.overview.viewmodel.OverviewViewModel
import com.mychuannong.app.settings.ui.BindingUserActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import okhttp3.Cookie
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic HTTP 401 and cached records only; never run in a user's installed package. */
class DurableBindingDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val owner = "20260001"
    private val term = "2026-2027-1"
    private val requests = AtomicInteger()
    private var expired = false
    private var responseOwner = owner
    private val restartPhase get() = InstrumentationRegistry.getArguments().getString("bindingRestartPhase")

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".bindingtest"))
        if(restartPhase == "verify") return@runBlocking
        SchoolSyncEngine.clear()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
    }
    @After fun cleanup() = runBlocking {
        inst.runOnMainSync {
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().forEach { it.finish() }
        }
        inst.waitForIdleSync()
        if(restartPhase != "seed") SchoolSyncEngine.clear()
    }

    @Test fun startupAndResumeReadCacheWithoutAcademicRequests() = runBlocking {
        bindAndSeed()
        expired = true
        val before = requests.get()
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        lateinit var model: OverviewViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(current() as androidx.activity.ComponentActivity)[OverviewViewModel::class.java]
        }
        await { model.uiState.value.isBoundStudent }
        repeat(3) { inst.runOnMainSync { model.onResume() }; delay(150) }
        assertEquals(before, requests.get())
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
        // Home only reads cached data. Online refresh belongs to the individual feature pages.
        assertEquals(before, requests.get())
        awaitActivity(MainActivity::class.java)
        compose.onNodeWithText("更新数据需要登录").assertDoesNotExist()
        compose.onNodeWithText("重新登录").assertDoesNotExist()
        compose.onNodeWithText("绑定学号").assertDoesNotExist()
        inst.runOnMainSync { model.onResume() }
        assertEquals(before, requests.get())
        compose.onNodeWithText("更新数据需要登录").assertDoesNotExist()
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
    }

    @Test fun timetableRefreshAfterExpiryKeepsCacheWithoutLoginPrompt() = runBlocking {
        bindAndSeed()
        expired = true
        context.startActivity(Intent(context, TimeTableActivity::class.java)
            .putExtra("imported_term", term)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitActivity(TimeTableActivity::class.java)
        lateinit var model: TimeTableViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(current() as androidx.activity.ComponentActivity)[TimeTableViewModel::class.java]
        }
        await { model.uiState.value?.subjects?.any { it.courseName == "本地合成课程" } == true }
        repeat(2) {
            val completed = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(15_000) { model.coursesResponse.drop(1).first { it is ApiResponse.Error } }
            }
            compose.onNodeWithText("刷新").assertIsEnabled().performClick()
            completed.await()
            compose.waitForIdle()
            assertEquals(SyncPhase.EXPIRED, SchoolSyncEngine.items.value["courses"]?.phase)
            assertTrue(inst.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("更新课表需要登录").isNullOrEmpty())
            awaitActivity(TimeTableActivity::class.java)
            compose.onNodeWithText("刷新").assertIsEnabled()
            assertTrue(SchoolAccessGate.isAllowed)
            assertCaches()
        }
    }

    @Test fun expiryPreservesCacheAndReloginIsOnlyOpenedByUser() = runBlocking {
        bindAndSeed()
        expired = true
        SchoolSyncEngine.sync(true)
        assertTrue(SchoolSyncEngine.items.value.values.all { it.phase == SyncPhase.EXPIRED })
        assertNull(SicauJiaowuSessionVault.read())
        for(phase in listOf(SchoolAuthPhase.SESSION_EXPIRED, SchoolAuthPhase.AUTH_REQUIRED, SchoolAuthPhase.UNBOUND))
            SchoolAuthState.move(phase)
        StudentInfoManager.expire()
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
        launch(ScoreInquiryActivity::class.java)
        awaitActivity(ScoreInquiryActivity::class.java)
        lateinit var model: ScoreInquiryViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(current() as androidx.activity.ComponentActivity)[ScoreInquiryViewModel::class.java]
        }
        await { model.state.value.transcript != null }
        assertEquals(SyncPhase.CACHED, model.state.value.phase)
        inst.runOnMainSync { current()!!.findViewById<View>(R.id.refresh).performClick() }
        await { model.state.value.phase == SyncPhase.EXPIRED }
        awaitActivity(ScoreInquiryActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        // Tapping the inline login action opens the editable form; Back keeps the cache.
        inst.runOnMainSync {
            val root = current()!!.window.decorView
            fun find(view: View): android.widget.TextView? {
                if(view is android.widget.TextView && view.text.toString() == "重新登录教务系统") return view
                if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            assertNotNull(find(root))
            find(root)!!.performClick()
        }
        awaitActivity(BindingUserActivity::class.java)
        inst.runOnMainSync {
            assertEquals(View.VISIBLE, current()!!.findViewById<View>(R.id.binding_card).visibility)
            (current() as androidx.activity.ComponentActivity).onBackPressedDispatcher.onBackPressed()
        }
        awaitActivity(ScoreInquiryActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
    }

    @Test fun mismatchedOnlineAccountCannotOverwriteBoundOwnerOrCaches() = runBlocking {
        bindAndSeed()
        responseOwner = "20260002"
        SchoolSyncEngine.sync(true)
        assertEquals(owner, StudentInfoManager.studentId)
        assertTrue(SchoolAccessGate.isAllowed)
        assertNull(SicauJiaowuSessionVault.read())
        assertCaches()
    }

    @Test fun prepareExpiredBindingForColdStart() = runBlocking {
        Assume.assumeTrue(restartPhase == "seed")
        bindAndSeed()
        expired = true
        SchoolSyncEngine.sync(true)
        assertNull(SicauJiaowuSessionVault.read())
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
    }

    @Test fun verifyExpiredBindingAfterProcessDeath() = runBlocking {
        Assume.assumeTrue(restartPhase == "verify")
        assertEquals(owner, StudentInfoManager.studentId)
        assertNull(SicauJiaowuSessionVault.read())
        assertTrue(SchoolAccessGate.isAllowed)
        assertCaches()
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        launch(ScoreInquiryActivity::class.java)
        awaitActivity(ScoreInquiryActivity::class.java)
        lateinit var model: ScoreInquiryViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(current() as androidx.activity.ComponentActivity)[ScoreInquiryViewModel::class.java]
        }
        await { model.state.value.transcript != null }
        assertEquals(SyncPhase.CACHED, model.state.value.phase)
        assertCaches()
    }

    private suspend fun bindAndSeed() {
        val jar = SicauJiaowuCookieJar()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            requests.incrementAndGet()
            val body = when(request.url.toString()) {
                SicauJiaowuProtocol.LOGIN_PAGE -> {
                    jar.saveFromResponse(request.url, listOf(requireNotNull(Cookie.parse(request.url,
                        "ASPSESSIONIDTEST=synthetic; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                    "<input name=sign value=synthetic><input name=hour_key value=synthetic>"
                }
                SicauJiaowuProtocol.LOGIN_POST -> "<script>location='student.asp'</script>"
                else -> "<table><tr><td>学号</td><td>$responseOwner</td></tr></table>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if(expired) 401 else 200).message("synthetic")
                .body(body.toByteArray(charset("GBK")).toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        ScoreCache.saveTranscript(owner, SchoolTranscript(
            listOf(TranscriptCourse("本地合成成绩", "88", "2", "必修", "", "TEST")),
            listOf(TranscriptField("平均成绩绩点", "3.50")), updatedAt = 123456789L))
        val db = CoursesDataBase.getDatabase(context)
        SchoolSyncEngine.saveSchoolRows(db, owner, CourseBatch(term, listOf(
            TimeTableMySubject(courseName = "本地合成课程", teacher = "合成教师", classroom = "A",
                weeks = (1..30).toList(), start = 1, step = 2, weekday = 1, term = term, studentId = owner))))
    }
    private suspend fun assertCaches() {
        assertEquals("本地合成成绩", ScoreCache.getTranscript(owner)?.courses?.single()?.name)
        val rows = CoursesDataBase.getDatabase(context).courseDao().getCoursesByTerm(term, owner)
        assertEquals("本地合成课程", rows.single().courseName)
        assertEquals(owner, SicauJiaowuSessionVault.bindingOwner())
    }
    private fun launch(type: Class<out Activity>) = context.startActivity(Intent(context, type)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    private fun current() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
    private fun awaitActivity(type: Class<out Activity>) = await { type.isInstance(current()) }
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while(android.os.SystemClock.uptimeMillis() < end) {
            compose.waitForIdle()
            var ready = false
            inst.runOnMainSync { ready = condition() }
            if(ready) return
            android.os.SystemClock.sleep(100)
        }
        fail("Expected local access/cache state did not arrive")
    }
}

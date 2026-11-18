package com.mychuannong.app.school

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.settings.ui.BindingUserActivity
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The isolated package uses synthetic school HTTP responses and never submits real credentials. */
class SchoolLoginContinuationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val owner = "20260001"

    @Before fun prepare() = runBlocking {
        check(context.packageName.endsWith(".bindingtest") || context.packageName.endsWith(".validation"))
        finishActivities()
        SchoolSyncEngine.clear()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
        launchHome()
    }

    @After fun clean() = runBlocking {
        finishActivities()
        SchoolSyncEngine.clear()
    }

    @Test fun learningEntryOpensThreeFieldsDirectlyAndCancelReturnsHome() {
        openLearningPage()
        compose.onNodeWithText("学号").assertIsDisplayed()
        compose.onNodeWithText("密码").assertIsDisplayed()
        compose.onNodeWithText("验证码").assertIsDisplayed()
        compose.onNodeWithText("川农 e 认证登录").assertDoesNotExist()
        compose.onNodeWithText("教务密码").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        awaitActivity(MainActivity::class.java)
        assertFalse(SchoolAccessGate.isAllowed)
        assertNoBindingScreen()
    }

    @Test fun settingsFirstBindingAlsoOpensThreeFieldsDirectly() {
        inst.runOnMainSync {
            currentActivity()!!.startActivity(Intent(context, BindingUserActivity::class.java))
        }
        awaitActivity(UnifiedSchoolAuthenticationActivity::class.java)
        compose.onNodeWithText("学号").assertIsDisplayed()
        compose.onNodeWithText("密码").assertIsDisplayed()
        compose.onNodeWithText("验证码").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        awaitActivity(MainActivity::class.java)
        assertFalse(SchoolAccessGate.isAllowed)
    }

    @Test fun verifiedBindingNavigatesOnceWithoutWaitingForLearningData() = runBlocking {
        openLearningPage()
        bindSyntheticSchoolAccount()
        returnAuthentication(Activity.RESULT_OK)
        awaitActivity(TimeTableActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        // If a duplicate continuation reopened the destination, Back would reveal
        // a second timetable. The login/binding screens must also be gone.
        inst.runOnMainSync {
            (currentActivity() as androidx.activity.ComponentActivity).onBackPressedDispatcher.onBackPressed()
        }
        awaitActivity(MainActivity::class.java)
        assertNoBindingScreen()
    }

    @Test fun successResultWithoutProtectedSchoolIdentityCannotNavigate() {
        openLearningPage()
        returnAuthentication(Activity.RESULT_OK)
        awaitActivity(MainActivity::class.java)
        assertFalse(SchoolAccessGate.isAllowed)
        assertNoBindingScreen()
    }

    @Test fun cancellationDoesNotNavigateEvenIfBindingFinishedConcurrently() = runBlocking {
        openLearningPage()
        bindSyntheticSchoolAccount()
        returnAuthentication(Activity.RESULT_CANCELED)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        assertNoBindingScreen()
    }

    @Test fun cancellationBlocksLateAcademicNavigationBeforeDestruction() {
        openLearningPage()
        val monitor = inst.addMonitor(UnifiedAcademicEntryActivity::class.java.name,
            android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            inst.runOnMainSync {
                val activity = currentActivity() as UnifiedSchoolAuthenticationActivity
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue(activity.isFinishing)
                // Deliver the pending VPN-success continuation in the same main
                // loop turn, before onDestroy has a chance to cancel its request.
                activity.javaClass.getDeclaredMethod("openAcademic").apply { isAccessible = true }.invoke(activity)
            }
            awaitActivity(MainActivity::class.java)
            assertEquals("A canceled login must not open another authentication page", 0, monitor.hits)
            assertFalse(SchoolAccessGate.isAllowed)
        } finally { inst.removeMonitor(monitor) }
    }

    private fun openLearningPage() {
        inst.runOnMainSync {
            currentActivity()!!.startActivity(Intent(context, TimeTableActivity::class.java))
        }
        awaitActivity(UnifiedSchoolAuthenticationActivity::class.java)
    }

    private suspend fun bindSyntheticSchoolAccount() {
        val jar = SicauJiaowuCookieJar()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val (status, body) = when (request.url.toString()) {
                SicauJiaowuProtocol.LOGIN_PAGE -> {
                    jar.saveFromResponse(request.url, listOf(requireNotNull(Cookie.parse(request.url,
                        "ASPSESSIONIDTEST=synthetic-cookie; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                    200 to "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                }
                SicauJiaowuProtocol.LOGIN_POST -> 200 to "<script>location='student.asp'</script>"
                SicauJiaowuProtocol.PERSONAL_INFO -> 200 to "<table><tr><td>学号</td><td>$owner</td></tr></table>"
                else -> 503 to "Learning data unavailable"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("synthetic")
                .body(body.toByteArray(charset("GBK")).toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        session.login(owner, "synthetic-password")
        SchoolSyncEngine.bindJiaowu(session)
    }

    private fun returnAuthentication(result: Int) {
        inst.runOnMainSync {
            (currentActivity() as UnifiedSchoolAuthenticationActivity).apply {
                setResult(result)
                finish()
            }
        }
    }

    private fun launchHome() {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitActivity(MainActivity::class.java)
    }

    private fun assertNoBindingScreen() = inst.runOnMainSync {
        assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .none { it is BindingUserActivity || it is UnifiedSchoolAuthenticationActivity })
    }

    private fun currentActivity(): Activity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull()

    private fun awaitActivity(type: Class<out Activity>) {
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            compose.waitForIdle()
            var ready = false
            inst.runOnMainSync { ready = type.isInstance(currentActivity()) }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        fail("Timed out waiting for ${type.simpleName}")
    }

    private fun finishActivities() {
        inst.runOnMainSync {
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().forEach(Activity::finish)
        }
        inst.waitForIdleSync()
    }
}

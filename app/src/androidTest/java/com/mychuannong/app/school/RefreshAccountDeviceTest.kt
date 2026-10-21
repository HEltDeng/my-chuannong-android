package com.mychuannong.app.school

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.core.account.ui.AccountRequiredActivity
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.feature.common.compose_ui.*
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.common.ui.*
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
import com.mychuannong.app.feature.resources.ResourceLibraryActivity
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Real local platform API, synthetic school transport; never uses the installed user's accounts. */
class RefreshAccountDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val owner="20269981"
    private val term="2026-2027-1"
    private val requests=AtomicInteger()
    @Volatile private var offline=false
    private val accounts=mutableListOf<Pair<String,String>>()
    private val screenshots get()=InstrumentationRegistry.getArguments().getString("screenshots")=="true"
    private val cases=listOf(
        FunctionDestination.ResourceLibrary to ResourceLibraryActivity::class.java,
        FunctionDestination.Secondhand to SecondhandActivity::class.java,
        FunctionDestination.LostFound to LostFoundActivity::class.java,
        FunctionDestination.DrivingSchool to DrivingSchoolActivity::class.java,
        FunctionDestination.IeltsToefl to CampusServicesActivity::class.java,
        FunctionDestination.CivilPostgrad to CampusServicesActivity::class.java,
        FunctionDestination.Gym to CampusServicesActivity::class.java,
        FunctionDestination.Errand to ErrandActivity::class.java,
    )

    @Before fun setup()=runBlocking {
        check(context.packageName.endsWith(".accountflowtest") || context.packageName.endsWith(".perftest"))
        finishActivities(); CampusToolsSession.invalidate()
        SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared()
        PrivacyConsentManager.acceptAppConsent(context)
        NotificationPermissionPreferences.markEducationShown(context)
        val jar=SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.ORIGIN.toHttpUrl(),listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            requests.incrementAndGet()
            if(offline) { Thread.sleep(if(screenshots) 2_000 else 900); throw java.io.IOException("synthetic offline") }
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type","text/html; charset=utf-8")
                .body("<table><tr><td>学号</td><td>$owner</td></tr></table>".toResponseBody()).build()
        }.build()
        val session=SicauJiaowuSession(jar,client,{})
        session.fetchPersonalInfo(owner); SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner); SchoolAccessGate.restoreBinding()
        SchoolSyncEngine.perform(object:SchoolReader {
            override suspend fun identity()=SchoolRead.Success(owner)
            override suspend fun courses()=SchoolRead.Success(CourseBatch(term,listOf(TimeTableMySubject(
                courseName="刷新保留测试课程",classroom="合成教室",teacher="合成教师",weeks=(1..20).toList(),
                start=1,step=2,weekday=1,term=term,studentId=owner,source="SCHOOL"))))
            override suspend fun grades()=SchoolRead.Success(emptyList<com.mychuannong.app.feature.common.data.local.entity.Grade>())
            override suspend fun exams()=SchoolRead.Success(emptyList<com.mychuannong.app.school.education.data.remote.model.ExamArrange>())
        },true)
        Unit
    }
    @After fun cleanup()=runBlocking {
        finishActivities()
        accounts.forEach { (phone,password) ->
            CampusToolsSession.login(phone,password)
            CampusToolsSession.deleteAccount(password)
        }
        CampusToolsSession.invalidate(); SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared()
    }
    private fun onMain(block:()->Unit)=inst.runOnMainSync(block)
    private fun finishActivities()=onMain {
        listOf(Stage.RESUMED,Stage.STARTED,Stage.CREATED,Stage.STOPPED).flatMap {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
        }.distinct().forEach { it.finish() }
    }
    private fun resumed():Activity? {
        var value:Activity?=null
        onMain { value=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).lastOrNull() }
        return value
    }
    private fun awaitActivity(type:Class<out Activity>):Activity {
        compose.waitUntil(20_000) { resumed()?.javaClass==type }
        return requireNotNull(resumed())
    }
    private fun credentials()="199"+(System.nanoTime()%100_000_000).toString().padStart(8,'0') to UUID.randomUUID().toString()
    private fun register()=runBlocking {
        val pair=credentials(); CampusToolsSession.register(pair.first,pair.second,"权限合成测试")
        accounts+=pair; pair
    }
    private fun main(tab:Int=1,block:(MainActivity)->Unit) {
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("main_tab",tab)).use {
            val main=awaitActivity(MainActivity::class.java) as MainActivity
            compose.waitForIdle(); block(main)
        }
    }
    private fun fill(label:String,value:String) {
        fun fields(v:View):List<EditText> = when(v) {
            is EditText -> listOf(v)
            is ViewGroup -> (0 until v.childCount).flatMap { fields(v.getChildAt(it)) }
            else -> emptyList()
        }
        var field:EditText?=null
        compose.waitUntil(5_000) {
            val activity=resumed()
            onMain { field=activity?.let { fields(it.window.decorView).singleOrNull { it.hint?.toString()?.startsWith(label)==true } } }
            field!=null
        }
        onMain { requireNotNull(field).setText(value) }
    }
    private fun assertCourseVisible() {
        val nodes=compose.onAllNodesWithText("刷新保留测试课程",substring=true)
        assertTrue("The current timetable must stay visible",nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() })
    }
    private fun capture(name:String) {
        if(!screenshots) return
        val activity=requireNotNull(resumed())
        onMain { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        inst.waitForIdleSync()
        val bitmap=requireNotNull(inst.uiAutomation.takeScreenshot())
        java.io.File(context.getExternalFilesDir(null),"$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    private fun assertSchoolIntact()=runBlocking {
        assertTrue(SchoolAccessGate.isAllowed)
        assertEquals(owner,StudentInfoManager.studentId)
        assertEquals("刷新保留测试课程",CoursesDataBase.getDatabase(context).courseDao().getAllCoursesNow().single().courseName)
    }
    private fun selectTab(main: MainActivity, index: Int) {
        onMain {
            val navigator = MainActivity::class.java.getDeclaredMethod("getMainTabNavigator")
                .apply { isAccessible = true }.invoke(main) as com.mychuannong.app.core.main.navigation.MainTabNavigator
            navigator.select(index)
        }
        compose.waitForIdle()
    }
    @Test fun retainedProfileDoesNotInterceptHomeBackAndKeepsItsSubpage()=main(tab=0) { main ->
        var homeBacks=0
        onMain {
            main.onBackPressedDispatcher.addCallback(main,object:androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { homeBacks++ }
            })
        }
        selectTab(main,2)
        compose.onNodeWithText("隐私与合规").performScrollTo().performClick()
        compose.onNodeWithText("个人信息清单").assertExists()
        selectTab(main,0)
        onMain { main.onBackPressedDispatcher.onBackPressed() }
        assertEquals("An offscreen profile must not consume Home back",1,homeBacks)
        selectTab(main,2)
        compose.onNodeWithText("个人信息清单").assertExists()
        onMain { main.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("账号与应用设置").assertExists()
        assertEquals(1,homeBacks)
    }
    @Test fun serviceScrollPositionSurvivesTabSwitches()=main { main ->
        val lists=compose.onAllNodes(hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val visible=lists.fetchSemanticsNodes().indices.single { lists[it].isDisplayed() }
        lists[visible].performScrollToNode(hasText("学校服务"))
        val before=compose.onNodeWithText("学校服务").fetchSemanticsNode().boundsInRoot
        capture("service-lazy-list")
        selectTab(main,2)
        selectTab(main,0)
        selectTab(main,1)
        compose.onNodeWithText("学校服务").assertIsDisplayed()
        assertEquals(before,compose.onNodeWithText("学校服务").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun guestRoutesAndDirectEntriesRequirePlatformAccountAndCancelReturns()=main { main ->
        assertFalse(AccountAccess.isSignedIn)
        cases.forEach { (destination,_) ->
            onMain { openFunctionShortcut(main,destination) }
            awaitActivity(AccountRequiredActivity::class.java)
            compose.onNodeWithText("登录我的川农账号").assertIsDisplayed()
            if(destination==FunctionDestination.ResourceLibrary) capture("platform-account-gate")
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertSame(main,awaitActivity(MainActivity::class.java))
        }
        // Direct Activity launch must not bypass the same boundary or lose the selected module.
        onMain { main.startActivity(CampusServicesActivity.createIntent(main,CampusService.Gym)) }
        awaitActivity(AccountRequiredActivity::class.java)
        compose.onNodeWithText("暂不登录").performScrollTo().performClick()
        assertSame(main,awaitActivity(MainActivity::class.java));assertSchoolIntact()
        onMain { main.startActivity(Intent(main,ErrandActivity::class.java)) }
        awaitActivity(AccountRequiredActivity::class.java)
        compose.onNodeWithText("暂不登录").performScrollTo().performClick()
        assertSame(main,awaitActivity(MainActivity::class.java))
    }
    @Test fun registrationResumesExactModuleAfterRotationThenSignedInEntriesOpenDirectly()=main { main ->
        onMain { openFunctionShortcut(main,FunctionDestination.Gym) }
        val original=awaitActivity(AccountRequiredActivity::class.java)
        onMain { original.recreate() }
        compose.waitUntil(10_000) { resumed() is AccountRequiredActivity && resumed()!==original }
        val (phone,password)=credentials()
        compose.onNodeWithText("没有账号？创建账号").performScrollTo().performClick()
        fill("昵称","权限合成测试");fill("手机号",phone);fill("密码",password)
        compose.onNodeWithText("注册并进入").performScrollTo().performClick()
        compose.waitUntil(20_000) { AccountAccess.isSignedIn }
        accounts+=phone to password
        assertEquals("gym",awaitActivity(CampusServicesActivity::class.java).intent.getStringExtra("campus_service"))
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);awaitActivity(MainActivity::class.java)
        cases.forEach { (destination,type) ->
            onMain { openFunctionShortcut(main,destination) }
            val target=awaitActivity(type)
            if(destination in setOf(FunctionDestination.Gym,FunctionDestination.CivilPostgrad,FunctionDestination.IeltsToefl))
                assertEquals(when(destination) { FunctionDestination.Gym -> "gym"; FunctionDestination.CivilPostgrad -> "exam"; else -> "language" },target.intent.getStringExtra("campus_service"))
            // Native finish keeps this test independent of each module's own WebView history.
            onMain { target.finish() }; assertSame(main,awaitActivity(MainActivity::class.java))
        }
        val user=CampusToolsSession.currentUser!!.id
        runBlocking { CampusToolsSession.restore() }
        assertEquals(user,CampusToolsSession.currentUser?.id);assertTrue(AccountAccess.isSignedIn);assertSchoolIntact()
    }
    @Test fun explicitLogoutKeepsSchoolBindingAndLoginResumesOriginalPost() {
        val (phone,password)=register()
        main { main ->
            onMain { Route.goAccountCenter(main) }
            awaitActivity(com.mychuannong.app.core.account.ui.AccountCenterActivity::class.java)
            compose.onNodeWithText("退出登录").performScrollTo().performClick()
            compose.onNodeWithText("确认",substring=false).performClick()
            awaitActivity(MainActivity::class.java)
            assertFalse(AccountAccess.isSignedIn);assertSchoolIntact()
            val home=resumed() as MainActivity
            onMain { home.startActivity(LostFoundActivity.detailIntent(home,"synthetic-post-id")) }
            awaitActivity(AccountRequiredActivity::class.java)
            fill("手机号",phone);fill("密码","incorrect-password")
            compose.onNodeWithText("登录",substring=false).performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("账号或密码错误",substring=true)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(AccountAccess.isSignedIn)
            fill("密码",password);compose.onNodeWithText("登录",substring=false).performScrollTo().performClick()
            assertEquals("synthetic-post-id",awaitActivity(LostFoundActivity::class.java).intent.getStringExtra(LostFoundActivity.EXTRA_POST_ID))
            assertSchoolIntact()
        }
    }
    @Test fun switchingPlatformAccountAndDeletingItDoNotRevokeSchoolIdentity() {
        register();val stamp=SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess }
        runBlocking { CampusToolsSession.logout() };assertSchoolIntact()
        register();assertSchoolIntact()
        assertEquals(stamp,SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess })
        val last=accounts.removeAt(accounts.lastIndex)
        runBlocking { CampusToolsSession.deleteAccount(last.second) };assertSchoolIntact()
    }
    @Test fun resumedProtectedPageRejectsLoggedOutSession() {
        register()
        main { main ->
            onMain { openFunctionShortcut(main,FunctionDestination.DrivingSchool) }
            awaitActivity(DrivingSchoolActivity::class.java)
            runBlocking { CampusToolsSession.logout() }
            awaitActivity(AccountRequiredActivity::class.java);assertSchoolIntact()
        }
    }
    @Test fun errandResumesAfterRegistrationAndLoginAndRejectsLogout()=main { main ->
        onMain { openFunctionShortcut(main,FunctionDestination.Errand) }
        val original=awaitActivity(AccountRequiredActivity::class.java)
        onMain { original.recreate() }
        compose.waitUntil(10_000) { resumed() is AccountRequiredActivity && resumed()!==original }
        val (phone,password)=credentials()
        compose.onNodeWithText("没有账号？创建账号").performScrollTo().performClick()
        fill("昵称","跑腿权限合成测试");fill("手机号",phone);fill("密码",password)
        compose.onNodeWithText("注册并进入").performScrollTo().performClick()
        compose.waitUntil(20_000) { AccountAccess.isSignedIn }
        accounts+=phone to password
        val errand=awaitActivity(ErrandActivity::class.java)
        compose.onNodeWithText("跑腿代取").assertIsDisplayed()
        capture("errand-after-registration")
        onMain { errand.finish() };awaitActivity(MainActivity::class.java)
        runBlocking { CampusToolsSession.logout() }
        assertFalse(AccountAccess.isSignedIn);assertSchoolIntact()
        onMain { openFunctionShortcut(main,FunctionDestination.Errand) }
        awaitActivity(AccountRequiredActivity::class.java)
        fill("手机号",phone);fill("密码",password)
        compose.onNodeWithText("登录",substring=false).performScrollTo().performClick()
        awaitActivity(ErrandActivity::class.java)
        runBlocking { CampusToolsSession.restore() }
        assertTrue(AccountAccess.isSignedIn);assertSchoolIntact()
        runBlocking { CampusToolsSession.logout() }
        awaitActivity(AccountRequiredActivity::class.java)
        assertFalse(AccountAccess.isSignedIn);assertSchoolIntact()
    }
    @Test fun homeKeepsGradeFieldsAndEmptyExamMessageWithoutCacheFooter()=main(tab=0) {
        compose.onNodeWithText("成绩汇总").performScrollTo()
        val eye=compose.onAllNodesWithContentDescription("展开成绩汇总")
        if(eye.fetchSemanticsNodes().isNotEmpty()) eye[0].performClick()
        listOf("加权平均成绩","专业排名","平均成绩绩点","有效学分").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onAllNodesWithText("当前为本地缓存数据",substring=true).assertCountEquals(0)
        compose.onNodeWithContentDescription("折叠成绩汇总").performClick()
        compose.onNodeWithText("加权平均成绩").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开成绩汇总").performClick()
        compose.onNodeWithText("查询成功，暂无数据").performScrollTo().assertIsDisplayed()
        val box=compose.onNodeWithText("查询成功，暂无数据").fetchSemanticsNode().boundsInRoot
        val display=context.resources.displayMetrics
        // Text semantics exclude the card's 18 dp horizontal / 20 dp vertical padding.
        assertEquals(display.widthPixels-40*display.density,box.width+36*display.density,3f)
        assertTrue("Empty exam card should stay compact",box.height+40*display.density<80*display.density)
        capture("home-compact-exam-card")
        // The existing exam section action still opens its original query page.
        compose.onAllNodesWithText("查看全部").onLast().performClick()
        awaitActivity(ExamArrangementActivity::class.java)
    }
    @Test fun homeSwipesDoNotRefreshOrShowProgress()=main(tab=0) {
        val before=requests.get();offline=true
        compose.onRoot().performTouchInput { swipeDown();swipeUp();swipeDown() }
        compose.waitForIdle()
        assertEquals(before,requests.get())
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        capture("home-after-swipes")
    }
    @Test fun timetableRefreshOnlyFromHeaderPreservesContentAndHasNoOverlay() {
        offline=true
        ActivityScenario.launch(TimeTableActivity::class.java).use {
            awaitActivity(TimeTableActivity::class.java)
            assertCourseVisible()
            val before=requests.get();val stamps=SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess }
            compose.onRoot().performTouchInput { swipeDown() };compose.waitForIdle()
            assertEquals(before,requests.get())
            compose.onNodeWithText("刷新",substring=false).performClick()
            compose.waitUntil(5_000) { requests.get()>before }
            val progress=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
            assertEquals(1,progress.size)
            assertTrue("Progress must remain in the top-right button",progress.single().boundsInRoot.center.x>context.resources.displayMetrics.widthPixels*0.65f)
            assertCourseVisible()
            capture("timetable-button-refresh")
            compose.waitUntil(15_000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty() }
            assertEquals(stamps,SchoolSyncEngine.items.value.mapValues { it.value.lastSuccess })
            assertCourseVisible();assertSchoolIntact()
        }
    }
}

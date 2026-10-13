package com.mychuannong.app.school

import android.app.NotificationManager
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.mychuannong.app.R
import com.mychuannong.app.common.cache.CommonInfo
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.schedule.*
import com.mychuannong.app.feature.schedule.reminder.*
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.settings.ui.BindingUserActivity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.*
import java.time.ZonedDateTime

class StudentFlowDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val owner="synthetic-flow"
    @Before fun setup()=runBlocking {
        Assume.assumeTrue(context.packageName.endsWith(".bindingtest"))
        SchoolSyncEngine.clear()
        StudentInfoManager.acceptVerifiedIdentity(owner)
        StudentInfoManager.saveVerifiedName(owner,"林晓明")
        CourseReminderPreferences.enabled=false
    }
    @After fun cleanup()=runBlocking {
        CourseReminderPreferences.enabled=false
        CourseReminderScheduler.cancelAll(context)
        context.getSystemService(NotificationManager::class.java).cancelAll()
        com.tencent.mmkv.MMKV.mmkvWithID("course_time_preferences_v1").clearAll()
        SchoolSyncEngine.clear()
    }
    @Test fun boundScreenSurvivesRecreationAndOffersExplicitRelogin() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(it.findViewById<View>(R.id.bound_summary).isShown)
                assertFalse(it.findViewById<View>(R.id.binding_card).isShown)
                assertTrue(it.findViewById<TextView>(R.id.bound_account).text.startsWith("林同学"))
            }
            capture("bound-account")
            scenario.recreate()
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.bound_summary).isShown);it.findViewById<View>(R.id.bound_relogin).performClick() }
            inst.waitForIdleSync()
            scenario.onActivity {
                assertTrue(it.findViewById<View>(R.id.binding_card).isShown)
                assertFalse(it.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(it.findViewById<View>(R.id.connect_webvpn).isShown)
            }
            capture("vpn-first")
        }
        ActivityScenario.launch<BindingUserActivity>(android.content.Intent(context,BindingUserActivity::class.java)
            .putExtra(BindingUserActivity.EXTRA_REAUTHENTICATE,true)).use { scenario ->
            scenario.onActivity { assertFalse(it.findViewById<View>(R.id.bound_summary).isShown);assertTrue(it.findViewById<View>(R.id.connect_webvpn).isShown) }
        }
    }
    private suspend fun course(start: Int=1,weekday:Int=2):TimeTableMySubject {
        val value=TimeTableMySubject(courseName="合成课程",classroom="测试教室",teacher="合成教师",weeks=(1..30).toList(),start=start,step=1,weekday=weekday,term=CommonInfo.getCurrentTerm(),studentId=owner,source="SCHOOL")
        val id=CoursesDataBase.getDatabase(context).courseDao().insertCourse(value)
        return value.copy(id=id.toInt())
    }
    @Test fun expiredSessionStillOpensCachedTimetableWithoutBindingPopup()=runBlocking {
        course()
        val reader=object:SchoolReader {
            override suspend fun identity()=SchoolRead.Failure(SyncPhase.EXPIRED,"合成过期会话")
            override suspend fun courses():SchoolRead<CourseBatch> = error("must not fetch")
            override suspend fun grades():SchoolRead<List<com.mychuannong.app.feature.common.data.local.entity.Grade>> = error("must not fetch")
            override suspend fun exams():SchoolRead<List<com.mychuannong.app.school.education.data.remote.model.ExamArrange>> = error("must not fetch")
        }
        SchoolSyncEngine.perform(reader,true)
        assertTrue(StudentInfoManager.hasCompleteBinding)
        val monitor=inst.addMonitor(BindingUserActivity::class.java.name,null,false)
        try {
            ActivityScenario.launch(TimeTableActivity::class.java).use { scenario ->
                inst.waitForIdleSync();delay(1500)
                assertEquals(0,monitor.hits)
                scenario.recreate();inst.waitForIdleSync();delay(500)
                assertEquals(0,monitor.hits)
                capture("cached-timetable")
                compose.onNodeWithText("刷新").assertIsDisplayed().performClick()
                compose.onNodeWithText("从图片导入课表").assertDoesNotExist()
                compose.onNodeWithText("更新课表").assertDoesNotExist()
                inst.waitForIdleSync();assertEquals(0,monitor.hits)
            }
        } finally { inst.removeMonitor(monitor) }
    }
    @Test fun offlineAlarmActuallyPostsAndRescheduleDoesNotDuplicate()=runBlocking {
        val now=ZonedDateTime.now(SicauAcademicData.zone)
        Assume.assumeTrue(now.toLocalTime().hour<23)
        CourseTimePreferences.save(listOf(CoursePeriod(now.plusSeconds(40).toLocalTime(),now.plusMinutes(40).toLocalTime())))
        course(weekday=now.dayOfWeek.value)
        StudentInfoManager.expire()
        SicauWebVpn.forgetRoute()
        CourseReminderPreferences.leadMinutes=5
        CourseReminderPreferences.silent=false
        CourseReminderPreferences.enabled=true
        CourseReminderScheduler.reschedule(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        withTimeout(15_000) { while(manager.activeNotifications.isEmpty()) delay(200) }
        assertEquals(1,manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.single().notification.extras.getCharSequence("android.text").toString().contains("合成课程"))
        CourseReminderScheduler.reschedule(context)
        assertTrue(CourseReminderScheduler.effectiveEntries(context).none { it.start<now.plusMinutes(1).toInstant().toEpochMilli() })
        delay(1500);assertEquals(1,manager.activeNotifications.size)
    }
    @Test fun widgetUsesGreenPaletteWithCachedCourses()=runBlocking {
        val now=ZonedDateTime.now(SicauAcademicData.zone)
        course(weekday=now.dayOfWeek.value)
        CourseTimePreferences.save(listOf(CoursePeriod(java.time.LocalTime.of(23,0),java.time.LocalTime.of(23,45))))
        val snapshot=CourseScheduleRepository.snapshot(context)
        inst.runOnMainSync {
            val root=com.mychuannong.app.WidgetUpdateCoordinator.todayViews(context,8,snapshot).apply(context,null)
            val title=root.findViewById<TextView>(R.id.today_widget_week)
            assertEquals(context.getColor(R.color.widget_primary),title.currentTextColor)
            val green=android.graphics.Color.green(title.currentTextColor);val blue=android.graphics.Color.blue(title.currentTextColor)
            assertTrue(green>blue)
            root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY));root.layout(0,0,960,420)
            val bitmap=android.graphics.Bitmap.createBitmap(960,420,android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap));save(bitmap,"green-widget")
        }
    }
    private fun capture(name:String) {
        inst.waitForIdleSync();inst.uiAutomation.waitForIdle(500,5000)
        inst.uiAutomation.takeScreenshot()?.let { save(it,name) }
    }
    private fun save(bitmap:android.graphics.Bitmap,name:String) {
        java.io.File(context.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}

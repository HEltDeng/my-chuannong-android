package com.mychuannong.app.core

import android.content.Context
import android.content.Intent
import com.mychuannong.app.common.ui.WebViewActivity
import com.mychuannong.app.core.account.ui.AccountCenterActivity
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.feature.common.ui.CalendarActivity
import com.mychuannong.app.feature.common.ui.CampusActivityPlaceholderActivity
import com.mychuannong.app.feature.common.ui.CetActivity
import com.mychuannong.app.feature.common.ui.ContractActivity
import com.mychuannong.app.feature.common.ui.ElectronicActivity
import com.mychuannong.app.feature.common.ui.ExamArrangementActivity
import com.mychuannong.app.feature.common.ui.MandeActivity
import com.mychuannong.app.feature.common.ui.ScoreInquiryActivity
import com.mychuannong.app.feature.ledger.ui.AccountBookActivity
import com.mychuannong.app.feature.buddy.ui.BuddyClubActivity
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
import com.mychuannong.app.feature.feedback.ui.FeedbackActivity
import com.mychuannong.app.feature.ledger.ui.AddSomethingAccountActivity
import com.mychuannong.app.feature.ledger.ui.FixSomethingAccountActivity
import com.mychuannong.app.feature.mooc.ui.MoocCoursePageActivity
import com.mychuannong.app.feature.resources.ResourceLibraryActivity
import com.mychuannong.app.feature.schedule.reminder.CourseReminderSettingsActivity
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.freshNews.ui.LocalCampusPostDetailActivity
import com.mychuannong.app.freshNews.ui.LocalCampusPublishActivity
import com.mychuannong.app.freshNews.ui.UserHomeActivity
import com.mychuannong.app.profileSettings.ui.AboutActivity
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.privacy.ReleaseInformationActivity
import com.mychuannong.app.settings.ui.BindingUserActivity
import com.mychuannong.app.skin.ui.SkinSelectionActivity

/**
 * 所有页面跳转逻辑都应卸载Route中，方便统一管理
 * 使用方法：Route.goxx()
 *
 * 学校账号绑定与我的川农账号登录分别管理。
 */
object Route {

    fun goAccountCenter(context: Context) {
        AccountAccess.openOrRequestLogin(context, Intent(context, AccountCenterActivity::class.java))
    }

    fun goFeedback(context: Context) {
        AccountAccess.openOrRequestLogin(context, Intent(context, FeedbackActivity::class.java))
    }

    fun goCourseReminders(context: Context) {
        context.startActivity(Intent(context, CourseReminderSettingsActivity::class.java))
    }

    fun goLostFound(context: Context) {
        AccountAccess.openOrRequestLogin(context, LostFoundActivity.listIntent(context))
    }

    /** Compatibility route retained for old callers; the campus channel is no longer opened. */
    fun goLostFoundChannel(context: Context) {
        goLostFound(context)
    }

    fun goCampusActivityPlaceholder(context: Context) {
        val intent = Intent(context, CampusActivityPlaceholderActivity::class.java)
        context.startActivity(intent)
    }

    fun goBuddyClub(context: Context, campusMode: Boolean = false) {
        context.startActivity(BuddyClubActivity.intent(context, campusMode = campusMode))
    }

    fun goBuddyActivityDetail(context: Context, activityId: String) {
        if (activityId.isNotBlank()) context.startActivity(BuddyClubActivity.intent(context, activityId))
    }

    fun goUserHomeActivity(context: Context) {
        val intent = Intent(context, UserHomeActivity::class.java)
        context.startActivity(intent)
    }

    fun goUserHomeActivity(context: Context, userId: Int) {
        val intent = Intent(context, UserHomeActivity::class.java)
        intent.putExtra("userId", userId)
        context.startActivity(intent)
    }

    fun goElectronic(context: Context) {
        try {
            val intent = Intent(context, ElectronicActivity::class.java)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun goHome(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
    }

    fun goHomeForcibly(context: Context) {
        val intent = Intent(
            context,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    fun goTimetable(context: Context) {
        val intent = Intent(context, TimeTableActivity::class.java)
        context.startActivity(intent)
    }

    fun goScoreInquiry(context: Context) {
        val intent = Intent(context, ScoreInquiryActivity::class.java)
        context.startActivity(intent)
    }

    fun goExamArrangement(context: Context) {
        val intent = Intent(context, ExamArrangementActivity::class.java)
        context.startActivity(intent)
    }

    fun goCet(context: Context) {
        val intent = Intent(context, CetActivity::class.java)
        context.startActivity(intent)
    }

    fun goComputerExam(context: Context) {
        context.startActivity(Intent(context, com.mychuannong.app.core.web.ServiceWebActivity::class.java)
            .putExtra("url_tag", "https://ncre.neea.edu.cn")
            .putExtra("title_tag", context.getString(com.mychuannong.app.R.string.feature_function_computer_exam)))
    }

    fun goMande(context: Context) {
        val intent = Intent(context, MandeActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * 应用唯一登录入口：跳转到绑定学号页面。
     *
     * @param isSwitchAccount 是否是"切换学号"场景。true 时，绑定成功后会由 BindingUserActivity 负责
     *   清理旧账号的内容缓存；进入绑定页前不会清除任何数据，避免中途放弃导致账号信息丢失。
     */
    fun goBindingUser(context: Context, isSwitchAccount: Boolean = false, reauthenticate: Boolean = false) {
        val intent = Intent(context, BindingUserActivity::class.java)
            .putExtra(BindingUserActivity.EXTRA_IS_SWITCH_ACCOUNT, isSwitchAccount)
            .putExtra(BindingUserActivity.EXTRA_REAUTHENTICATE, reauthenticate)
        context.startActivity(intent)
    }

    /**
     * 强制清栈并跳转到绑定学号页面，用于 token 过期等场景。
     */
    fun goBindingUserForcibly(context: Context) {
        val intent = Intent(context, BindingUserActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    fun localCampusPublishIntent(context: Context): Intent =
        Intent(context, LocalCampusPublishActivity::class.java)

    fun goLocalCampusPublish(context: Context) {
        context.startActivity(localCampusPublishIntent(context))
    }

    fun goLocalCampusPostDetail(context: Context, postId: String) {
        if (postId.isBlank()) return
        if (
            com.mychuannong.app.feature.lostfound.data.LostFoundRepository
                .find(postId) != null
        ) {
            AccountAccess.openOrRequestLogin(context, LostFoundActivity.detailIntent(context, postId))
            return
        }
        val intent = Intent(context, LocalCampusPostDetailActivity::class.java)
            .putExtra(LocalCampusPostDetailActivity.EXTRA_POST_ID, postId)
        context.startActivity(intent)
    }

    fun goContract(context: Context) {
        val intent = Intent(context, ContractActivity::class.java)
        context.startActivity(intent)
    }

    fun goResourceLibrary(context: Context) {
        AccountAccess.openOrRequestLogin(
            context,
            Intent(context, ResourceLibraryActivity::class.java),
        )
    }

    fun goAccountBook(context: Context) {
        val intent = Intent(context, AccountBookActivity::class.java)
        context.startActivity(intent)
    }

    fun goAddSomethingAccount(context: Context) {
        val intent = Intent(context, AddSomethingAccountActivity::class.java)
        context.startActivity(intent)
    }

    fun goFixSomethingAccount(context: Context, itemId: Int) {
        val intent = Intent(context, FixSomethingAccountActivity::class.java).apply {
            putExtra("ITEM_ID", itemId)
        }
        context.startActivity(intent)
    }

    fun goWebView(context: Context, url: String) {
        val intent = Intent(context, WebViewActivity::class.java).apply {
            putExtra("url_tag", url)
        }
        context.startActivity(intent)
    }

    fun goAbout(context: Context) {
        val intent = Intent(context, AboutActivity::class.java)
        context.startActivity(intent)
    }

    fun goReleaseInformation(context: Context, type: ReleaseDocumentType) {
        context.startActivity(ReleaseInformationActivity.intent(context, type))
    }

    /**
     * 跳转到 Mooc 课程主页 WebView（自动注入 cookie，免登录）。
     *
     * @param courseId 课程 ID（已经 `cleanCourseId()` 后的纯数字/字母串）
     */
    fun goMoocCoursePage(context: Context, courseId: String) {
        if (courseId.isBlank()) return
        val intent = Intent(context, MoocCoursePageActivity::class.java).apply {
            putExtra(MoocCoursePageActivity.EXTRA_COURSE_ID, courseId)
        }
        context.startActivity(intent)
    }

    fun goCalendar(context: Context) {
        val intent = Intent(context, CalendarActivity::class.java)
        context.startActivity(intent)
    }

    fun goSkinSecletion(context: Context) {
        val intent = Intent(context, SkinSelectionActivity::class.java)
        context.startActivity(intent)
    }
}

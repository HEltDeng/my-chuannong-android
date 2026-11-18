package com.mychuannong.app.core.security

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.school.SicauJiaowuSessionVault
import com.mychuannong.app.settings.ui.BindingUserActivity
import com.mychuannong.app.school.SicauWebVpnActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Consent boundary for app entry; verified school binding remains a separate local proof. */
internal object SchoolAccessGate {
    private val learningPages = setOf(
        "com.mychuannong.app.feature.timetable.ui.TimeTableActivity",
        "com.mychuannong.app.feature.common.ui.ScoreInquiryActivity",
        "com.mychuannong.app.feature.common.ui.ExamArrangementActivity",
        "com.mychuannong.app.school.SchoolAcademicActivity",
    )
    private const val AFTER_BINDING = "learning_page_after_binding"

    fun resumeLearningPage(activity: BindingUserActivity): Boolean {
        val page = activity.intent.getStringExtra(AFTER_BINDING) ?: return false
        if (page !in learningPages || !PrivacyConsentManager.hasAppConsent(activity) || !isAllowed) return false
        activity.intent.removeExtra(AFTER_BINDING)
        activity.startActivity(Intent().setClassName(activity, page).apply {
            activity.intent.getStringExtra("imported_term")?.let { putExtra("imported_term", it) }
        })
        return true
    }
    private val controller = SchoolAccessController(
        currentIdentity = { StudentInfoManager.studentId },
        trustedBindingOwner = { SicauJiaowuSessionVault.bindingOwner() },
        persistRevocation = { SicauJiaowuSessionVault.revokeBinding() },
    )
    val state get() = controller.state
    val isAllowed get() = controller.isAllowed()
    fun restoreBinding() = controller.restoreBinding()
    fun revokeBinding() = controller.revokeBinding()

    private fun allowedWithoutBinding(activity: Activity): Boolean = when (activity.javaClass.name) {
        SchoolEntryActivity::class.java.name -> true
        "com.mychuannong.app.core.privacy.ReleaseInformationActivity" ->
            activity.intent.getStringExtra("release_document_type") in setOf(null, "privacy", "agreement", "third_party", "personal_information", "service_information") ||
                PrivacyConsentManager.hasAppConsent(activity)
        else -> PrivacyConsentManager.hasAppConsent(activity)
    }

    fun enforce(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        val page = activity.javaClass.name
        val requiresBinding = page in learningPages || page == "com.mychuannong.app.school.TimetableImportActivity"
        if (requiresBinding && PrivacyConsentManager.hasAppConsent(activity) && !isAllowed) {
            activity.window.decorView.visibility = View.GONE
            activity.startActivity(Intent(activity, BindingUserActivity::class.java).apply {
                putExtra(BindingUserActivity.EXTRA_REQUIRED, true)
                if (page in learningPages) putExtra(AFTER_BINDING, page)
                activity.intent.getStringExtra("imported_term")?.let { putExtra("imported_term", it) }
            })
            activity.finish()
            return false
        }
        if (allowedWithoutBinding(activity)) return true
        // Called from super.onCreate (and pre-resume), before content gets its first frame.
        activity.window.decorView.visibility = View.GONE
        activity.startActivity(Intent(activity, SchoolEntryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity.finish()
        return false
    }

    fun install(application: Application) {
        var resumed = WeakReference<Activity>(null)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { enforce(activity) }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPreResumed(activity: Activity) { enforce(activity) }
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
                enforce(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (resumed.get() === activity) resumed.clear()
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            state.collect { if (!isAllowed) resumed.get()?.let(::enforce) }
        }
    }
}

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

/** Default-deny activity boundary, including restored tasks, widgets and notification intents. */
internal object SchoolAccessGate {
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
            activity.intent.getStringExtra("release_document_type") in setOf(null, "privacy", "agreement", "third_party")
        BindingUserActivity::class.java.name, SicauWebVpnActivity::class.java.name,
        com.mychuannong.app.school.SchoolLoginVerificationActivity::class.java.name ->
            PrivacyConsentManager.hasAppConsent(activity)
        else -> false
    }

    fun enforce(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        if (allowedWithoutBinding(activity)) return true
        if (PrivacyConsentManager.hasAppConsent(activity) && isAllowed) return true
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

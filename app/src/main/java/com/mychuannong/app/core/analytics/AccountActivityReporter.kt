package com.mychuannong.app.core.analytics

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground only. School identity and platform login are independent. */
object AccountActivityReporter {
    private var installed = false
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val owner = ProcessLifecycleOwner.get()
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var signature: String? = null
                var lastSuccess = 0L
                while (isActive) {
                    val current = CampusToolsSession.presenceSignature()
                    val now = SystemClock.elapsedRealtime()
                    if (PrivacyConsentManager.hasAppConsent(app) && (current != signature || now-lastSuccess >= 300_000L)) {
                        try {
                            if (CampusToolsSession.reportStudentPresence()) { signature=current;lastSuccess=now }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Retry without logging personal data. */ }
                    }
                    delay(5_000L)
                }
            }
        }
    }
}

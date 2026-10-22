package com.mychuannong.app.core.analytics

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** No WorkManager, offline replay, or guest reporting: only a visible, consented account session. */
object AccountActivityReporter {
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val owner = ProcessLifecycleOwner.get()
        val policy = ForegroundActivityPolicy()
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CampusToolsSession.state.collectLatest { state ->
                    val user = (state as? AccountSessionState.SignedIn)?.user ?: return@collectLatest
                    if (user.accountType != "PHONE" || user.accountStatus !in setOf("ACTIVE", "LIMITED")) return@collectLatest
                    while (isActive) {
                        val day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
                        val elapsed = SystemClock.elapsedRealtime()
                        if (policy.shouldSend(user.id, day, elapsed,
                                PrivacyConsentManager.hasAppConsent(app),
                                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                            try {
                                if (CampusToolsSession.reportForeground(user.id)) policy.accepted(user.id, day, elapsed)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Retry while still foreground. Never log payloads or delay normal app use.
                            }
                        }
                        delay(60_000L)
                    }
                }
            }
        }
    }
}

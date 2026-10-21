package com.mychuannong.app.core.security

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.privacy.*
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.settings.ui.BindingUserActivity

/** First-use consent only. Returning users route locally without a network interstitial. */
class SchoolEntryActivity : ComponentActivity() {
    private var consent by mutableStateOf(PrivacyConsentManager.AppConsentState.UNDECIDED)
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consent = PrivacyConsentManager.appConsentState(this)
        if (consent == PrivacyConsentManager.AppConsentState.ACCEPTED) {
            openDestination()
            return
        }
        setContent {
            AppSkinTheme {
                BackHandler { finishAffinity() }
                when (consent) {
                    PrivacyConsentManager.AppConsentState.UNDECIDED -> PrivacyConsentScreen(
                        onAccept = {
                            PrivacyConsentManager.acceptAppConsent(this)
                            consent = PrivacyConsentManager.AppConsentState.ACCEPTED
                            openDestination()
                        },
                        onDecline = {
                            PrivacyConsentManager.declineAppConsent(this)
                            consent = PrivacyConsentManager.AppConsentState.DECLINED
                        },
                        onOpenPrivacy = { openDocument(ReleaseDocumentType.PRIVACY) },
                        onOpenAgreement = { openDocument(ReleaseDocumentType.AGREEMENT) },
                    )
                    PrivacyConsentManager.AppConsentState.DECLINED -> PrivacyDeclinedScreen(
                        onReviewDecision = {
                            PrivacyConsentManager.clearDeclinedDecision(this)
                            consent = PrivacyConsentManager.AppConsentState.UNDECIDED
                        },
                        onOpenPrivacy = { openDocument(ReleaseDocumentType.PRIVACY) },
                        onOpenAgreement = { openDocument(ReleaseDocumentType.AGREEMENT) },
                        onExit = { finishAffinity() },
                    )
                    PrivacyConsentManager.AppConsentState.ACCEPTED -> Unit
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        consent = PrivacyConsentManager.appConsentState(this)
        if (consent == PrivacyConsentManager.AppConsentState.ACCEPTED) openDestination()
    }

    private fun openDestination() {
        if (navigating) return
        if (!SchoolAccessGate.isAllowed) { openBinding(); return }
        navigating = true
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    private fun openDocument(type: ReleaseDocumentType) = Route.goReleaseInformation(this, type)
    private fun openBinding() {
        if (navigating) return
        navigating = true
        startActivity(Intent(this, BindingUserActivity::class.java)
            .putExtra(BindingUserActivity.EXTRA_REQUIRED, true)
            .putExtra(BindingUserActivity.EXTRA_REAUTHENTICATE, true))
        finish()
    }
}

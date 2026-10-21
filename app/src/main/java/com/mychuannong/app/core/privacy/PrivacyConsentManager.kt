package com.mychuannong.app.core.privacy

import android.content.Context

/** Stores the versioned app-level privacy decision for this V1 build. */
object PrivacyConsentManager {
    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_APP_CONSENT_VERSION = "privacy_consent_version"
    private const val KEY_APP_DECLINED_VERSION = "privacy_declined_version"

    /** Increment when a material policy change requires renewed consent. */
    const val CURRENT_APP_CONSENT_VERSION = 2

    enum class AppConsentState { ACCEPTED, DECLINED, UNDECIDED }

    fun appConsentState(context: Context): AppConsentState {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when {
            preferences.getInt(KEY_APP_CONSENT_VERSION, 0) >= CURRENT_APP_CONSENT_VERSION ->
                AppConsentState.ACCEPTED
            preferences.getInt(KEY_APP_DECLINED_VERSION, 0) >= CURRENT_APP_CONSENT_VERSION ->
                AppConsentState.DECLINED
            else -> AppConsentState.UNDECIDED
        }
    }

    fun hasAppConsent(context: Context): Boolean =
        appConsentState(context) == AppConsentState.ACCEPTED

    fun acceptAppConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_APP_CONSENT_VERSION, CURRENT_APP_CONSENT_VERSION)
            .remove(KEY_APP_DECLINED_VERSION)
            .apply()
    }

    fun declineAppConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_CONSENT_VERSION)
            .putInt(KEY_APP_DECLINED_VERSION, CURRENT_APP_CONSENT_VERSION)
            .apply()
    }

    fun clearDeclinedDecision(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_DECLINED_VERSION)
            .apply()
    }

}

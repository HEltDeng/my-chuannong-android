package com.mychuannong.app.core.privacy

import android.content.Context

/** Stores the versioned app-level privacy decision for this V1 build. */
object PrivacyConsentManager {
    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_APP_CONSENT_VERSION = "privacy_consent_version"
    private const val KEY_APP_DECLINED_VERSION = "privacy_declined_version"
    private const val KEY_ACCEPTED_DOCUMENT_REVISION = "privacy_document_revision"
    private const val KEY_DECLINED_DOCUMENT_REVISION = "privacy_declined_document_revision"

    /** Increment when a material policy change requires renewed consent. */
    const val CURRENT_APP_CONSENT_VERSION = 10
    const val POLICY_VERSION = "2026-11-17.1"
    // Revision is local; receipt and policy version match the approved server contract.
    private const val CURRENT_DOCUMENT_REVISION = 2
    fun acceptedAt(context: Context): Long = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong("consent_accepted_at", 0L)

    enum class AppConsentState { ACCEPTED, DECLINED, UNDECIDED }

    fun appConsentState(context: Context): AppConsentState {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return decision(
            preferences.getInt(KEY_APP_CONSENT_VERSION, 0), preferences.getInt(KEY_ACCEPTED_DOCUMENT_REVISION, 0),
            preferences.getInt(KEY_APP_DECLINED_VERSION, 0), preferences.getInt(KEY_DECLINED_DOCUMENT_REVISION, 0),
        )
    }

    internal fun decision(acceptedVersion: Int, acceptedRevision: Int, declinedVersion: Int, declinedRevision: Int): AppConsentState =
        when {
            acceptedVersion >= CURRENT_APP_CONSENT_VERSION && acceptedRevision >= CURRENT_DOCUMENT_REVISION ->
                AppConsentState.ACCEPTED
            declinedVersion >= CURRENT_APP_CONSENT_VERSION && declinedRevision >= CURRENT_DOCUMENT_REVISION ->
                AppConsentState.DECLINED
            else -> AppConsentState.UNDECIDED
        }

    fun hasAppConsent(context: Context): Boolean =
        appConsentState(context) == AppConsentState.ACCEPTED

    fun acceptAppConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_APP_CONSENT_VERSION, CURRENT_APP_CONSENT_VERSION)
            .putInt(KEY_ACCEPTED_DOCUMENT_REVISION, CURRENT_DOCUMENT_REVISION)
            .putLong("consent_accepted_at", System.currentTimeMillis())
            .remove(KEY_APP_DECLINED_VERSION)
            .remove(KEY_DECLINED_DOCUMENT_REVISION)
            .apply()
    }

    fun declineAppConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_CONSENT_VERSION)
            .remove(KEY_ACCEPTED_DOCUMENT_REVISION)
            .remove("consent_accepted_at")
            .putInt(KEY_APP_DECLINED_VERSION, CURRENT_APP_CONSENT_VERSION)
            .putInt(KEY_DECLINED_DOCUMENT_REVISION, CURRENT_DOCUMENT_REVISION)
            .apply()
    }

    fun clearDeclinedDecision(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_DECLINED_VERSION)
            .remove(KEY_DECLINED_DOCUMENT_REVISION)
            .apply()
    }

}

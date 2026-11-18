package com.mychuannong.app.core.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyConsentDecisionTest {
    @Test fun previousPolicyRequiresRenewedConsentEvenWithItsLatestLocalRevision() {
        assertEquals(PrivacyConsentManager.AppConsentState.UNDECIDED, PrivacyConsentManager.decision(9, 2, 0, 0))
        assertEquals(PrivacyConsentManager.AppConsentState.UNDECIDED, PrivacyConsentManager.decision(0, 0, 9, 2))
    }

    @Test fun changedDisclosureRequiresAConcreteNewDecision() {
        val current = PrivacyConsentManager.CURRENT_APP_CONSENT_VERSION
        assertEquals(PrivacyConsentManager.AppConsentState.UNDECIDED, PrivacyConsentManager.decision(6, 0, 0, 0))
        assertEquals(PrivacyConsentManager.AppConsentState.UNDECIDED, PrivacyConsentManager.decision(current, 1, 0, 0))
        assertEquals(PrivacyConsentManager.AppConsentState.UNDECIDED, PrivacyConsentManager.decision(0, 0, current, 1))
        assertEquals(PrivacyConsentManager.AppConsentState.ACCEPTED, PrivacyConsentManager.decision(current, 2, 0, 0))
        assertEquals(PrivacyConsentManager.AppConsentState.DECLINED, PrivacyConsentManager.decision(0, 0, current, 2))
    }
}

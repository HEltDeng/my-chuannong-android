package com.mychuannong.app.core.security

import org.junit.Assert.*
import org.junit.Test

class SchoolAccessControllerTest {
    @Test fun firstUseCannotReachHome() {
        val gate = SchoolAccessController({ "" }, { null }, {})
        assertFalse(gate.isAllowed())
        assertEquals(SchoolAccess.BINDING_REQUIRED, gate.state.value.access)
    }

    @Test fun localIdentityAndFlagsAloneDoNotAuthorizeAnything() {
        val gate = SchoolAccessController({ "forged-local-id" }, { null }, {})
        assertFalse(gate.isAllowed())
    }

    @Test fun matchedEncryptedBindingGrantsAccessWithoutNetworkOrRepeatedReads() {
        var reads = 0
        val gate = SchoolAccessController({ "test-id" }, { reads++; "test-id" }, {})
        repeat(10) { assertTrue(gate.isAllowed()) }
        assertEquals(1, reads)
    }

    @Test fun differentPersistedOwnerIsRejected() {
        val gate = SchoolAccessController({ "test-id" }, { "another-id" }, {})
        assertFalse(gate.isAllowed())
    }

    @Test fun newProcessRestoresVerifiedBindingWithoutOnlineCheck() {
        val first = SchoolAccessController({ "test-id" }, { "test-id" }, {})
        assertTrue(first.isAllowed())
        val restarted = SchoolAccessController({ "test-id" }, { "test-id" }, {})
        assertTrue(restarted.isAllowed())
    }

    @Test fun bindingCompletionOpensHomeDirectlyFromNewProof() {
        var proof: String? = null
        val gate = SchoolAccessController({ "test-id" }, { proof }, { proof = null })
        assertFalse(gate.isAllowed())
        proof = "test-id"
        assertEquals(SchoolAccess.ALLOWED, gate.restoreBinding().access)
        assertTrue(gate.isAllowed())
    }

    @Test fun explicitUnbindingPersistsAcrossRestoreAndProcessRestart() {
        var proof: String? = "test-id"
        val gate = SchoolAccessController({ "test-id" }, { proof }, { proof = null })
        assertTrue(gate.isAllowed())
        gate.revokeBinding()
        assertFalse(gate.isAllowed())
        assertEquals(SchoolAccess.BINDING_REQUIRED, gate.restoreBinding().access)
        val restarted = SchoolAccessController({ "test-id" }, { proof }, { proof = null })
        assertFalse(restarted.isAllowed())
        proof = "test-id" // Only a new, successful school login writes another proof.
        assertEquals(SchoolAccess.ALLOWED, gate.restoreBinding().access)
    }

    @Test fun changingLocalOwnerCannotReuseAnotherAccountsGrant() {
        var owner = "test-id"
        val gate = SchoolAccessController({ owner }, { "test-id" }, {})
        assertTrue(gate.isAllowed())
        owner = "another-id"
        assertFalse(gate.isAllowed())
        assertEquals(SchoolAccess.BINDING_REQUIRED, gate.restoreBinding().access)
    }

    @Test fun unreadableOrTamperedProofFailsClosed() {
        val gate = SchoolAccessController({ "test-id" }, { error("sensitive-internal-error") }, {})
        assertFalse(gate.isAllowed())
        assertEquals(SchoolAccess.BINDING_REQUIRED, gate.state.value.access)
    }
}

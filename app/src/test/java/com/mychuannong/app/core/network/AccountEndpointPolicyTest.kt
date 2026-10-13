package com.mychuannong.app.core.network

import org.junit.Assert.*
import org.junit.Test

class AccountEndpointPolicyTest {
    @Test fun rejectsDeviceLocalAndUnconfiguredCredentialEndpoints() {
        listOf("http://127.0.0.1:8792/api/v1/", "https://127.0.0.2/", "https://[::1]/",
            "https://localhost/", "https://test.localhost/", "https://10.0.2.2/", "https://0.0.0.0/",
            "https://service.invalid/", "http://accounts.example.org/", "not a url").forEach {
            assertFalse(it,ServiceEndpointPolicy.isAccountEndpointConfigured(it))
        }
    }
    @Test fun acceptsConfiguredTlsServiceWithoutClaimingItIsReachable() {
        assertTrue(ServiceEndpointPolicy.isAccountEndpointConfigured("https://accounts.example.org/api/v1/"))
    }
}

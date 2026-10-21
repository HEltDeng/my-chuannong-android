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
    @Test fun debugPreviewOnlyAllowsTheExactAdbForwardedBackend() {
        assertTrue(ServiceEndpointPolicy.isAccountEndpointConfigured("http://127.0.0.1:8792/api/v1/", true))
        listOf("http://127.0.0.1:8793/api/v1/", "http://127.0.0.1:8792/other/",
            "http://127.0.0.1:8792/api/v1/?other=1", "http://127.0.0.1:8792/api/v1/#other",
            "http://user:pass@127.0.0.1:8792/api/v1/", "http://10.0.2.2:8792/api/v1/",
            "http://192.168.1.10:8792/api/v1/", "http://accounts.example.org/api/v1/",
            "http://service.invalid/api/v1/", "not a url").forEach {
            assertFalse(it, ServiceEndpointPolicy.isAccountEndpointConfigured(it, true))
        }
    }
}

package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.interceptor.NetworkLogPolicy
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityPolicyTest {
    @Test
    fun `invalid endpoint is fail closed`() {
        assertFalse(ServiceEndpointPolicy.isConfigured("https://campus-tools.invalid/api/v1/"))
        assertFalse(ServiceEndpointPolicy.isConfigured("https://api.mychangliapp.cn/api/v1/legacy-disabled/"))
        assertFalse(ServiceEndpointPolicy.isConfigured("not a url"))
        assertTrue(ServiceEndpointPolicy.isConfigured("https://api.mychangliapp.cn/api/v1/"))
        assertTrue(ServiceEndpointPolicy.isConfigured("https://example.com/api/"))
    }

    @Test
    fun `release logging is disabled and debug never enables body`() {
        assertEquals(HttpLoggingInterceptor.Level.NONE, NetworkLogPolicy.level(false))
        assertEquals(HttpLoggingInterceptor.Level.BASIC, NetworkLogPolicy.level(true))
    }

    @Test
    fun `sensitive query values are redacted`() {
        val redacted = NetworkLogPolicy.redact(
            "GET https://example.com/?phone=13800138000&studentId=20260001&token=secret",
        )
        assertFalse(redacted.contains("13800138000"))
        assertFalse(redacted.contains("20260001"))
        assertFalse(redacted.contains("secret"))
    }
}

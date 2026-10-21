package com.mychuannong.app.core.network

import com.mychuannong.app.BuildConfig
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ServiceUnavailableException(
    override val message: String = "服务暂未开放",
) : IOException(message)

object ServiceEndpointPolicy {
    private const val LEGACY_DISABLED_PATH_PREFIX = "/api/v1/legacy-disabled/"

    fun isConfigured(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.host.isNotBlank() &&
            !parsed.host.endsWith(".invalid", ignoreCase = true) &&
            !parsed.encodedPath.startsWith(LEGACY_DISABLED_PATH_PREFIX)
    }

    const val ACCOUNT_UNAVAILABLE = "当前版本尚未接入账号服务，暂不支持注册和登录。校内服务请使用川农账号登录。"

    // The local Debug preview reaches our backend through adb reverse 8792.
    // Release and validation builds still require the configured HTTPS service.
    fun isAccountEndpointConfigured(url: String, allowLocalDebug: Boolean = false): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (allowLocalDebug && parsed.scheme == "http" && parsed.host == "127.0.0.1" &&
            parsed.port == 8792 && parsed.encodedPath == "/api/v1/" &&
            parsed.query == null && parsed.fragment == null &&
            parsed.username.isEmpty() && parsed.password.isEmpty()
        ) return true
        val host = parsed.host.lowercase()
        return isConfigured(url) && parsed.isHttps && host != "localhost" &&
            !host.endsWith(".localhost") && host != "::1" &&
            !host.startsWith("127.") && host != "0.0.0.0" && host != "10.0.2.2"
    }

    fun isAccountServiceConfigured(): Boolean = isAccountEndpointConfigured(
        BuildConfig.CAMPUS_TOOLS_API_BASE_URL,
        allowLocalDebug = BuildConfig.DEBUG && !BuildConfig.RELEASE_USER_SURFACE,
    )

    fun isCampusToolsConfigured(): Boolean =
        isConfigured(BuildConfig.CAMPUS_TOOLS_API_BASE_URL)

}

/** Prevents placeholder hosts from ever reaching DNS or the network stack. */
class UnavailableEndpointInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (
            url.host.endsWith(".invalid", ignoreCase = true) ||
            url.encodedPath.startsWith("/api/v1/legacy-disabled/")
        ) {
            throw ServiceUnavailableException()
        }
        return chain.proceed(chain.request())
    }
}

/** Release-only circuit breaker for inherited clients, including absolute Retrofit paths. */
class LegacyServiceGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (BuildConfig.RELEASE_USER_SURFACE && !BuildConfig.LEGACY_SERVICES_ENABLED) {
            throw ServiceUnavailableException()
        }
        return chain.proceed(chain.request())
    }
}

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

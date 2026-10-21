package com.mychuannong.app.core.network.interceptor

import android.util.Log
import com.mychuannong.app.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor

internal object NetworkLogPolicy {
    private val sensitiveQuery = Regex(
        "(?i)(password|token|authorization|cookie|phone|student_?id|account)=([^&\\s]*)",
    )

    fun redact(message: String): String =
        sensitiveQuery.replace(message) { match -> "${match.groupValues[1]}=██" }

    fun level(debug: Boolean): HttpLoggingInterceptor.Level =
        if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
}

/** One logging policy for every app-owned OkHttp client. Bodies are never logged. */
object NetworkLogger {
    private const val TAG = "NetworkLogger"

    fun getLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) Log.d(TAG, NetworkLogPolicy.redact(message))
        }.apply {
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            level = NetworkLogPolicy.level(BuildConfig.DEBUG)
        }
}

package com.mychuannong.app.school

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only this isolated client sees the school credentials. No shared app interceptors or redirects. */
internal class SicauJiaowuSession(
    val jar: SicauJiaowuCookieJar = SicauJiaowuCookieJar(),
    private val client: OkHttpClient = client(jar),
    private val trace: (String) -> Unit = ::jiaowuTrace
) {
    var verifiedId: String? = null
        private set

    companion object {
        fun client(jar: SicauJiaowuCookieJar): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(jar).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS).build()
    }

    private fun request(url: String, referer: String): Request.Builder = Request.Builder().url(url)
        .header("Referer", referer)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0")
        .header("Upgrade-Insecure-Requests", "1")
        .header("Sec-Fetch-Dest", "document").header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", if (url == SicauJiaowuProtocol.LOGIN_PAGE) "none" else "same-origin")
        .header("Sec-Fetch-User", "?1")
        .header("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Microsoft Edge\";v=\"151\", \"Chromium\";v=\"151\"")
        .header("sec-ch-ua-mobile", "?0").header("sec-ch-ua-platform", "\"Windows\"")

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(JiaowuException(JiaowuFailure.NETWORK_UNREACHABLE))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun html(response: Response): String {
        try {
            val source = response.body.source()
            source.request(2_000_001)
            if (source.buffer.size > 2_000_000) throw JiaowuException(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN)
            return SicauJiaowuProtocol.decode(source.readByteArray(), response.header("Content-Type"))
        } catch (_: IOException) { throw JiaowuException(JiaowuFailure.NETWORK_UNREACHABLE) }
    }

    suspend fun login(entered: String, password: String): JiaowuPersonalInfo = withContext(Dispatchers.IO) {
        jar.clear(); verifiedId = null
        try {
            val fields = execute(request(SicauJiaowuProtocol.LOGIN_PAGE, SicauJiaowuProtocol.ORIGIN + "/").build()).use {
                trace("GET_LOGIN_PAGE status=${it.code}")
                if (!it.isSuccessful) throw JiaowuException(JiaowuFailure.LOGIN_PAGE_HTTP_ERROR)
                trace("sessionCookiePresent=${jar.hasSession(SicauJiaowuProtocol.LOGIN_POST)}")
                SicauJiaowuProtocol.loginFields(html(it)) { sign, hour ->
                    trace("dynamicFields sign=$sign hourKey=$hour")
                }
            }
            val cookiePresent = jar.hasSession(SicauJiaowuProtocol.LOGIN_POST)
            if (!cookiePresent) throw JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)
            // Match URLSearchParams UTF-8 form encoding and field order from the protocol reference.
            val body = FormBody.Builder().add("user", entered).add("pwd", password).add("lb", "S")
                .add("submit", "").add("sign", fields.sign).add("hour_key", fields.hourKey).build()
            execute(request(SicauJiaowuProtocol.LOGIN_POST, SicauJiaowuProtocol.LOGIN_PAGE)
                .header("Origin", SicauJiaowuProtocol.ORIGIN).post(body).build()).use {
                trace("POST_LOGIN status=${it.code}")
                val classification = SicauJiaowuProtocol.classifyLogin(it.code, html(it))
                trace("loginClassification=${classification.name}")
            }
            fetchPersonalInfo(entered).also { verifiedId = it.studentId }
        } catch (e: Exception) {
            jar.clear(); verifiedId = null
            if (e is JiaowuException) trace("failure=${e.reason.name}")
            throw e
        }
    }

    suspend fun fetchPersonalInfo(expected: String): JiaowuPersonalInfo = withContext(Dispatchers.IO) {
        if (!jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
            throw JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)
        execute(request(SicauJiaowuProtocol.PERSONAL_INFO, SicauJiaowuProtocol.LOGIN_PAGE).build()).use {
            trace("PERSONAL_INFO status=${it.code}")
            if (it.code in 300..399 || it.code in setOf(401, 403))
                throw JiaowuException(JiaowuFailure.SESSION_EXPIRED)
            if (!it.isSuccessful) throw JiaowuException(JiaowuFailure.PERSONAL_INFO_HTTP_ERROR)
            val info = SicauJiaowuProtocol.personalInfo(html(it))
            trace("studentIdParsed=true")
            trace("identityMatch=${info.studentId == expected}")
            if (info.studentId != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
            if (!jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
                throw JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)
            verifiedId = info.studentId
            info
        }
    }
    fun cancel() { client.dispatcher.cancelAll() }
}

/** Call sites only pass fixed event names, booleans, HTTP codes and enums. */
internal fun jiaowuTrace(event: String) {
    if (com.mychuannong.app.BuildConfig.DEBUG) android.util.Log.d("JIAOWU", event)
}

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
    private val trace: (String) -> Unit = ::jiaowuTrace,
    private val authorized: () -> Boolean = { true },
) {
    @Volatile private var openedRevision = SicauNativeAuth.webRevision.value
    @Volatile private var cancelled = false
    internal fun adoptCurrentRevision() { openedRevision = SicauNativeAuth.webRevision.value }
    var verifiedName: String? = null
        private set
    var verifiedId: String? = null
        private set

    companion object {
        fun client(jar: SicauJiaowuCookieJar): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(jar).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            // After observed first-read failures following idle periods, avoid stale pooled
            // proxy connections without retrying credential POSTs. The server cause is unconfirmed.
            .apply { if (jar.route.isWebVpn) connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)) }
            .addNetworkInterceptor { chain ->
                // Every academic request is gated. No redirect may silently fall back to direct access.
                if (!jar.route.allows(chain.request().url)) throw IOException("School route rejected")
                val revision = SicauNativeAuth.webRevision.value
                chain.proceed(chain.request()).also { response ->
                    synchronized(SicauNativeAuth) {
                        if (revision == SicauNativeAuth.webRevision.value && !jar.route.isWebVpn)
                            jar.acceptRawCookies(response.request.url, response.headers("Set-Cookie"))
                    }
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS).build()
    }

    private fun request(url: String, referer: String): Request.Builder = Request.Builder().url(jar.route.url(url))
        .header("Referer", jar.route.url(referer))
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0")
        .header("Upgrade-Insecure-Requests", "1")
        .header("Sec-Fetch-Dest", "document").header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", if (url == SicauJiaowuProtocol.LOGIN_PAGE) "none" else "same-origin")
        .header("Sec-Fetch-User", "?1")
        .header("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Microsoft Edge\";v=\"151\", \"Chromium\";v=\"151\"")
        .header("sec-ch-ua-mobile", "?0").header("sec-ch-ua-platform", "\"Windows\"")

    private suspend fun execute(request: Request): Response {
        val revision = SicauNativeAuth.webRevision.value
        val response = executeCall(request)
        try {
            jar.acceptResponseCookies(response.request.url, response.headers("Set-Cookie"), revision)
            if (revision != SicauNativeAuth.webRevision.value) throw kotlinx.coroutines.CancellationException()
            return response
        } catch (e: Exception) { response.close(); throw e }
    }

    private suspend fun executeCall(request: Request): Response = suspendCancellableCoroutine { continuation ->
        if (cancelled || openedRevision != SicauNativeAuth.webRevision.value || verifiedId == null && !authorized()) {
            continuation.cancel(kotlinx.coroutines.CancellationException("School session changed"))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trace("networkFailure=" + when (e) {
                    is java.net.SocketTimeoutException -> "TIMEOUT"
                    is javax.net.ssl.SSLException -> "TLS"
                    is java.net.UnknownHostException -> "DNS"
                    else -> if (call.isCanceled()) "CANCELLED" else "CONNECTION"
                })
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
            return SicauJiaowuProtocol.decode(source.readByteArray(), response.header("Content-Type")).also {
                if (jar.route.isWebVpn && (it.contains("川农e认证") || it.contains("/wui/assets/index-")))
                    throw JiaowuException(JiaowuFailure.WEBVPN_REQUIRED)
            }
        } catch (_: IOException) { throw JiaowuException(JiaowuFailure.NETWORK_UNREACHABLE) }
    }

    private fun checkAccess(response: Response) {
        if (!jar.route.isWebVpn) return
        trace("transport=WEBVPN")
        val target = response.header("Location")?.let { response.request.url.resolve(it) }
        if (response.code in setOf(401, 403) || response.code in 300..399 &&
            (target == null || !jar.route.allows(target))) throw JiaowuException(JiaowuFailure.WEBVPN_REQUIRED)
    }

    /** Follow only bounded same-resource GET redirects. A 302 or permission error is
     * not evidence that a previously verified identity has become invalid. */
    private suspend fun readPage(url: String, referer: String, failure: JiaowuFailure): String {
        var current = request(url, referer).build()
        val visited = mutableSetOf<String>()
        repeat(4) {
            if (!visited.add(current.url.toString())) throw JiaowuException(JiaowuFailure.BUSINESS_REDIRECT)
            execute(current).use { response ->
                checkAccess(response)
                if (response.code in 300..399) {
                    val target = response.header("Location")?.let { current.url.resolve(it) }
                    if (target == null || !jar.route.allows(target))
                        throw JiaowuException(JiaowuFailure.BUSINESS_REDIRECT)
                    current = current.newBuilder().url(target).header("Referer", current.url.toString()).get().build()
                } else {
                    if (response.code == 401) throw JiaowuException(JiaowuFailure.SESSION_EXPIRED)
                    if (!response.isSuccessful) throw JiaowuException(failure)
                    val type = response.header("Content-Type").orEmpty()
                    if (type.isNotEmpty() && !type.contains("text/html", true))
                        throw JiaowuException(JiaowuFailure.BUSINESS_STRUCTURE)
                    return html(response)
                }
            }
        }
        throw JiaowuException(JiaowuFailure.BUSINESS_REDIRECT)
    }

    /** Fresh native GET proves the discovered WebView resource is usable by OkHttp. */
    suspend fun checkLoginPage() = withContext(Dispatchers.IO) {
        execute(request(SicauJiaowuProtocol.LOGIN_PAGE, SicauJiaowuProtocol.ORIGIN + "/").build()).use {
            checkAccess(it)
            if (!it.isSuccessful) throw JiaowuException(JiaowuFailure.LOGIN_PAGE_HTTP_ERROR)
            try { SicauJiaowuProtocol.loginFields(html(it)) }
            catch(e: JiaowuException) { if(e.reason != JiaowuFailure.CAPTCHA_REQUIRED) throw e }
            trace("WEBVPN_NATIVE_LOGIN_PAGE verified=true")
        }
    }

    suspend fun login(entered: String, password: String): JiaowuPersonalInfo = withContext(Dispatchers.IO) {
        jar.clear(); verifiedId = null
        try {
            val fields = execute(request(SicauJiaowuProtocol.LOGIN_PAGE, SicauJiaowuProtocol.ORIGIN + "/").build()).use {
                trace("GET_LOGIN_PAGE status=${it.code}")
                checkAccess(it)
                if (!it.isSuccessful) throw JiaowuException(JiaowuFailure.LOGIN_PAGE_HTTP_ERROR)
                trace("sessionCookiePresent=${jar.hasSession(SicauJiaowuProtocol.LOGIN_POST)}")
                SicauJiaowuProtocol.loginFields(html(it), it.header("Content-Type")) { sign, hour ->
                    trace("dynamicFields sign=$sign hourKey=$hour")
                }
            }
            val cookiePresent = jar.hasSession(SicauJiaowuProtocol.LOGIN_POST)
            if (!cookiePresent) throw JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)
            // The official page submits an HTML form (currently GB2312/GBK), not URLSearchParams.
            val body = FormBody.Builder(fields.charset).add("user", entered).add("pwd", password).add("lb", "S")
                .add("submit", "").add("sign", fields.sign).add("hour_key", fields.hourKey).build()
            val action = jar.route.loginAction(fields.action, jar.route.url(SicauJiaowuProtocol.LOGIN_PAGE))
            execute(request(SicauJiaowuProtocol.LOGIN_POST, SicauJiaowuProtocol.LOGIN_PAGE).url(action)
                .header("Origin", jar.route.origin).post(body).build()).use {
                trace("POST_LOGIN status=${it.code}")
                checkAccess(it)
                val responseHtml = html(it)
                if (com.mychuannong.app.BuildConfig.DEBUG) {
                    val signals = SicauLoginResponseSignals.inspect(responseHtml)
                    trace("loginResponseSignals=" + signals.joinToString(",") { signal -> signal.name }.ifEmpty { "NONE" })
                }
                val classification = SicauJiaowuProtocol.classifyLogin(it.code, responseHtml)
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
            throw JiaowuException(if (jar.route.isWebVpn) JiaowuFailure.WEBVPN_REQUIRED else JiaowuFailure.SESSION_COOKIE_MISSING)
        run {
            val info = SicauJiaowuProtocol.personalInfo(readPage(SicauJiaowuProtocol.PERSONAL_INFO,
                SicauJiaowuProtocol.LOGIN_PAGE, JiaowuFailure.PERSONAL_INFO_HTTP_ERROR))
            trace("studentIdParsed=true")
            trace("identityMatch=${info.studentId == expected}")
            if (info.studentId != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
            if (!jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
                throw JiaowuException(JiaowuFailure.SESSION_COOKIE_MISSING)
            verifiedId = info.studentId
            verifiedName = info.name
            info
        }
    }
    suspend fun fetchBusiness(page:JiaowuPage):String = withContext(Dispatchers.IO) {
        if(verifiedId==null) throw JiaowuException(JiaowuFailure.SESSION_UNAVAILABLE)
        if(!jar.hasSession(page.url)) throw JiaowuException(if(jar.route.isWebVpn) JiaowuFailure.WEBVPN_REQUIRED else JiaowuFailure.SESSION_COOKIE_MISSING)
        readPage(page.url, SicauJiaowuProtocol.PERSONAL_INFO, JiaowuFailure.BUSINESS_HTTP_ERROR)
            .also { SicauBusinessParser.document(it) }
    }
    private suspend fun transcriptPage(url: String): String {
        if(verifiedId == null) throw JiaowuException(JiaowuFailure.SESSION_UNAVAILABLE)
        if(!jar.hasSession(url)) throw JiaowuException(if(jar.route.isWebVpn) JiaowuFailure.WEBVPN_REQUIRED else JiaowuFailure.SESSION_COOKIE_MISSING)
        return readPage(url, JiaowuPage.GRADES.url, JiaowuFailure.BUSINESS_HTTP_ERROR)
            .also { SicauTranscriptParser.document(it) }
    }

    suspend fun transcript(expected: String): SchoolTranscript = withContext(Dispatchers.IO) {
        fetchPersonalInfo(expected)
        val pending = java.util.ArrayDeque<TranscriptLink>()
        pending.add(TranscriptLink("个人成绩单", SicauTranscriptNavigation.TRANSCRIPT_URL))
        pending.add(TranscriptLink("有效必修加权排名", SicauTranscriptNavigation.RANKING_URL))
        pending.add(TranscriptLink("成绩查询", JiaowuPage.GRADES.url))
        pending.add(TranscriptLink("成绩信息", SicauJiaowuProtocol.PERSONAL_INFO))
        val visited = mutableSetOf<String>()
        var report: SchoolTranscript? = null
        var rank: List<TranscriptField>? = null
        var rankPhase = SyncPhase.PARSER_PENDING
        var rankMessage = "未在学校页面找到有效必修加权排名入口，请在教务网页核对"
        var transcriptFailure: JiaowuFailure? = null
        while(pending.isNotEmpty() && visited.size < 12) {
            val link = pending.removeFirst()
            if(!visited.add(link.url)) continue
            try {
                val body = transcriptPage(link.url)
                val doc = SicauTranscriptParser.document(body)
                if(link.title == "个人成绩单" || doc.select("title,h1,h2,h3,caption,center").any { it.text().contains("四川农业大学学生成绩单") || it.text().contains("个人成绩单") }) {
                    report = SicauTranscriptParser.transcript(body, expected)
                }
                if(link.title == "有效必修加权排名") {
                    rank = SicauTranscriptParser.ranking(body, expected)
                    rankPhase = SyncPhase.DATA; rankMessage = "学校页面提供的本人排名"
                }
                SicauTranscriptNavigation.links(body, link.url, jar.route, expected).filter { it.url !in visited }.forEach(pending::addLast)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: JiaowuException) {
                if(e.reason in setOf(JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.IDENTITY_MISMATCH, JiaowuFailure.WEBVPN_REQUIRED)) throw e
                if(link.title == "有效必修加权排名") {
                    rankPhase = if(e.reason == JiaowuFailure.BUSINESS_STRUCTURE) SyncPhase.PARSER_PENDING else SyncPhase.FAILED
                    rankMessage = if(rankPhase == SyncPhase.PARSER_PENDING) "排名页面解析失败，未展示未经确认的排名" else e.reason.userMessage
                } else transcriptFailure = e.reason
            }
            if(report != null && rank != null) break
        }
        val data = report ?: throw JiaowuException(transcriptFailure ?: JiaowuFailure.BUSINESS_STRUCTURE)
        // Do not accept data if cookies changed identity during navigation.
        fetchPersonalInfo(expected)
        data.copy(ranking = rank.orEmpty(), rankingPhase = rankPhase, rankingMessage = rankMessage)
    }
    fun cancel() { cancelled = true; client.dispatcher.cancelAll() }
}

/** Call sites only pass fixed event names, booleans, HTTP codes and enums. */
internal fun jiaowuTrace(event: String) {
    if (com.mychuannong.app.BuildConfig.DEBUG) android.util.Log.d("JIAOWU", event)
}

internal enum class JiaowuPage(val url:String) {
    COURSES(SicauJiaowuProtocol.ORIGIN+"/xuesheng/gongxuan/gongxuan/kbbanji.asp?title_id1=4"),
    GRADES(SicauJiaowuProtocol.ORIGIN+"/xuesheng/chengji/chengji/sear_ch_all.asp"),
    EXAMS(SicauJiaowuProtocol.ORIGIN+"/xuesheng/kao/kao/xuesheng.asp?title_id1=01")
}

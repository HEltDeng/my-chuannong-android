package com.mychuannong.app.school

import com.google.gson.JsonObject
import com.mychuannong.app.SafeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** A service ticket stays in memory until navigation. Never log this object or a callback URL. */
internal class SchoolSsoRequest private constructor(
    val realm: String, val appId: String, val service: String,
) {
    fun endpoint(base: String) = (base + "/api/v1/cas/login").toHttpUrl().newBuilder()
        .addQueryParameter("appid", appId).build()

    /** Reproduce the school's public JumpCallbackUrl for the verified primary owner.
     * Delegated identities never replace the owner, even when listed by the service. */
    fun callback(response: JsonObject, owner: String): String? = runCatching {
        if(owner.isBlank() || SicauAuthProtocol.confirmedNumber(response, owner) != owner) return null
        val data = response.getAsJsonObject("data")
        val users = data.get("users")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val primary = data.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
        val primaryId = primary?.get("id")?.takeUnless { it.isJsonNull }?.asString
        val user = if(users.size() == 0) primary ?: return null else users.map { it.asJsonObject }.filter {
            val id = it.get("id")?.takeUnless { value -> value.isJsonNull }?.asString
            val number = it.get("number")?.takeUnless { value -> value.isJsonNull }?.asString
            !id.isNullOrBlank() && (primaryId == null || id == primaryId) &&
                (number == owner || number.isNullOrBlank() && id == primaryId)
        }.singleOrNull() ?: return null
        val id = user.get("id").asString
        val number = user.get("number")?.takeUnless { it.isJsonNull }?.asString
        if(number != owner && id != primaryId) return null
        val ticket = data.get("st").asString
        if(id.isBlank() || id.length > 128 || ticket.isBlank() || ticket.length > 4096 ||
            (id + ticket).any { it.code < 32 || it.code == 127 }) return null
        service.toHttpUrl().newBuilder().setQueryParameter("ticket", "$ticket:$id")
            .setQueryParameter("userId", id).build().toString()
    }.getOrNull()

    companion object {
        private val serviceHosts = setOf("webvpn.sicau.edu.cn", "ywtb.sicau.edu.cn",
            "xsgz.sicau.edu.cn", "ehall.sicau.edu.cn", "op.sicau.edu.cn", "ecology.sicau.edu.cn")
        fun parse(url: String): SchoolSsoRequest? = runCatching {
            val u = url.toHttpUrl()
            val login = SchoolSsoNavigation.authLocations(url).getOrNull(1) ?: return null
            val realm = login.removeSuffix("/wui/")
            if(realm != SicauAuthProtocol.ORIGIN && proxyRealm(realm) == null) return null
            if(u.newBuilder().query(null).fragment(null).build().toString() != login) return null
            val fragment = u.encodedFragment ?: return null
            if(fragment.substringBefore('?') !in listOf("", "/")) return null
            val entries = fragment.substringAfter('?', "").split('&').filter(String::isNotBlank).map {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            if(entries.map { it.first }.distinct().size != entries.size) return null
            val params = entries.toMap()
            if(params.keys.any { it !in setOf("appid", "service", "cascallback") } ||
                params["cascallback"] !in setOf(null, "", "choose")) return null
            val app = params["appid"] ?: return null
            if(!Regex("[A-Za-z0-9_-]{1,128}").matches(app)) return null
            val service = params["service"] ?: return null
            if(!SafeUrl.school(service)) return null
            val target = service.toHttpUrl()
            // The portal also returns to its own hash routes; keep that route intact.
            if(target.host !in serviceHosts ||
                target.fragment?.let { target.host != "ywtb.sicau.edu.cn" || !it.startsWith("/") } == true ||
                target.queryParameterNames.any { it in setOf("ticket", "userId") }) return null
            SchoolSsoRequest(realm, app, service)
        }.getOrNull()

        /** Learn only an auth redirect whose original service is the official VPN CAS callback. */
        fun vpnAuthRealm(url: String): String? {
            val request = parse(url) ?: return null
            val callback = request.service.toHttpUrl()
            if(callback.host != SicauJiaowuRoute.VPN_HOST || callback.encodedPath != "/login" ||
                callback.queryParameter("cas_login") != "true") return null
            return proxyRealm(request.realm)
        }
        fun proxyRealm(base: String): String? = SicauJiaowuRoute.fromResourcePage("$base/wui/")
            ?.takeIf { it.prefix.startsWith("/https/") && base == SicauJiaowuRoute.VPN_ORIGIN + it.prefix }
            ?.let { base }
    }
}

internal interface SchoolSsoCookies {
    suspend fun get(url: String): String
    suspend fun accept(url: String, cookies: List<String>)
}

/** Use the existing central session in its original realm. No password, no cross-domain
 * cookie copying, no automatic credential POST. The school still issues every ticket. */
internal class SchoolSsoBroker(
    private val cookies: SchoolSsoCookies,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS).build(),
) {
    /** Re-establish VPN through its actual official CAS redirect and a fresh school ticket.
     * No synthesized callback, credentials, cross-domain cookies or POST replay. */
    suspend fun restoreVpn(owner: String, proxyRealm: String?): Boolean {
        var url = SicauJiaowuRoute.VPN_ORIGIN + "/"
        val visited = mutableSetOf<String>()
        var ticketIssued = false
        repeat(8) {
            if (!visited.add(url)) return false
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.scheme != "https" || parsed.port != 443 || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.host !in setOf(SicauJiaowuRoute.VPN_HOST, "auth.sicau.edu.cn")) return false
            SchoolSsoRequest.parse(url)?.let { request ->
                if (ticketIssued) return false
                val service = request.service.toHttpUrl()
                if (service.host != SicauJiaowuRoute.VPN_HOST || service.encodedPath != "/login" || service.queryParameter("cas_login") != "true") return false
                url = callback(request, owner, proxyRealm) ?: return false
                ticketIssued = true
                visited.clear() // The successful CAS callback may legitimately return to '/'.
                return@repeat
            }
            val http = Request.Builder().url(url).apply {
                val header = cookies.get(url)
                if (header.isNotBlank()) header("Cookie", header)
            }.build()
            execute(http).use { response ->
                cookies.accept(url, response.headers("Set-Cookie"))
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    url = parsed.resolve(response.header("Location") ?: return false)?.toString() ?: return false
                } else return response.isSuccessful && parsed.host == SicauJiaowuRoute.VPN_HOST &&
                    parsed.encodedPath in setOf("/", "/portal", "/m/portal")
            }
        }
        return false
    }

    suspend fun callback(request: SchoolSsoRequest, owner: String, proxyRealm: String?): String? {
        if(owner.isBlank()) return null
        val direct = SicauAuthProtocol.ORIGIN
        val proxy = proxyRealm?.let(SchoolSsoRequest::proxyRealm)
        val original = request.realm
        val candidates = listOf(original, proxy, direct).filterNotNull().distinct()
        for(base in candidates) {
            val url = request.endpoint(base).toString()
            val header = cookies.get(url)
            if(header.isBlank()) continue
            val http = Request.Builder().url(url).header("Cookie", header)
                .header("Referer", "$base/wui/").header("Accept", "application/json").build()
            val callback = try {
                execute(http).use { response ->
                    if(response.request.url != http.url || !response.isSuccessful) return@use null
                    cookies.accept(url, response.headers("Set-Cookie"))
                    val body = withContext(Dispatchers.IO) {
                        val source = response.body.source()
                        source.request(2_000_001)
                        if(source.buffer.size > 2_000_000) null else source.readUtf8()
                    } ?: return@use null
                    request.callback(SicauAuthProtocol.decode(body), owner)
                }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { null }
            if(callback != null) return callback
        }
        return null
    }

    /** Upgrade discovery is public and deliberately sends/saves no cookies, so it cannot
     * replace an already authenticated VPN session with an anonymous one. */
    suspend fun discoverProxyRealm(): String? {
        var url = SicauJiaowuRoute.VPN_ORIGIN + "/"
        repeat(5) {
            SchoolSsoRequest.vpnAuthRealm(url)?.let { return it }
            val parsed = url.toHttpUrlOrNull() ?: return null
            if(parsed.host != SicauJiaowuRoute.VPN_HOST || parsed.scheme != "https" ||
                parsed.port != 443 || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            url = try {
                execute(Request.Builder().url(url).build()).use { response ->
                    if(response.code !in setOf(301, 302, 303, 307, 308)) return null
                    parsed.resolve(response.header("Location") ?: return null)?.toString() ?: return null
                }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { return null }
        }
        return SchoolSsoRequest.vpnAuthRealm(url)
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if(continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}

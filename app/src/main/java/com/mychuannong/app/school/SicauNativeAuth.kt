package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Exact auth origin only. No platform API, HTTP logging, WebView bridge, or automatic redirects. */
internal class SchoolCookieJar(private val revision: () -> Long = { SicauNativeAuth.webRevision.value }) : CookieJar {
    private var openedRevision = revision()
    internal fun adoptCurrentRevision() { openedRevision = revision() }
    private val cookies = mutableListOf<Cookie>()
    private val attributes = mutableMapOf<String, String>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if(openedRevision != revision() || url.scheme != "https" || url.host != "auth.sicau.edu.cn") return
        incoming.filter { it.domain == "auth.sicau.edu.cn" || it.domain == "sicau.edu.cn" }.forEach { c ->
            val key = SchoolWebCookiePolicy.key(c)
            if (attributes[key]?.let { Cookie.parse(url, it) } != c) attributes[key] = c.toString()
            cookies.removeAll { it.name==c.name && it.domain==c.domain && it.path==c.path };cookies.add(c)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if(openedRevision != revision() || url.scheme!="https" || url.host!="auth.sicau.edu.cn") return emptyList()
        cookies.removeAll { it.expiresAt < System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    @Synchronized fun encode(): ByteArray = ("full-cookie-v2\n" + rawSnapshot().joinToString("\n")).toByteArray()
    @Synchronized fun snapshot(): List<Cookie> = loadForRequest(SicauAuthProtocol.ORIGIN.toHttpUrl()).toList()
    @Synchronized fun rawSnapshot(): List<String> = if(openedRevision != revision()) emptyList() else
        cookies.filter { it.expiresAt > System.currentTimeMillis() }.mapNotNull { attributes[SchoolWebCookiePolicy.key(it)] }
    @Synchronized fun importAuthCookies(rawCookies: List<String>) {
        cookies.clear(); attributes.clear()
        acceptRawCookies(SicauAuthProtocol.ORIGIN.toHttpUrl(), rawCookies)
    }
    @Synchronized fun acceptRawCookies(url: HttpUrl, rawCookies: List<String>) {
        if (openedRevision != revision() || url.scheme != "https" || url.host != "auth.sicau.edu.cn") return
        rawCookies.forEach { raw ->
            val parsed = Cookie.parse(url, raw) ?: return@forEach
            if (parsed.domain !in setOf("auth.sicau.edu.cn", "sicau.edu.cn")) return@forEach
            val key = SchoolWebCookiePolicy.key(parsed)
            cookies.removeAll { SchoolWebCookiePolicy.key(it) == key }
            attributes.remove(key)
            val scope = url.newBuilder().encodedPath(parsed.path).build().toString()
            val durable = SchoolWebCookiePolicy.durable(scope, raw) ?: return@forEach
            val cookie = Cookie.parse(url, durable) ?: return@forEach
            cookies.add(cookie); attributes[key] = durable
        }
    }
    fun restore(bytes:ByteArray) {
        val text = String(bytes, Charsets.UTF_8)
        // Legacy request-header snapshots invented expiry/flags. Prefer the live browser;
        // never resurrect those snapshots with an unknown server deadline.
        if (text.startsWith("full-cookie-v2\n")) importAuthCookies(text.removePrefix("full-cookie-v2\n").lines())
    }
}
internal object SchoolSessionVault {
    private const val alias="mychuannong_sicau_session_v1"
    private val file get()=File(PlanetApplication.appContext.noBackupFilesDir,"sicau-session.enc")
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(jar:SchoolCookieJar) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val atomic=android.util.AtomicFile(file)
        val stream=atomic.startWrite()
        try { stream.write(cipher.iv+cipher.doFinal(jar.encode()));atomic.finishWrite(stream) }
        catch(e:Exception) { atomic.failWrite(stream);throw e }
    }
    fun read():SchoolCookieJar = SchoolCookieJar().also { jar ->
        runCatching { if(file.exists()) { val b=android.util.AtomicFile(file).readFully();require(b.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)))
            jar.restore(cipher.doFinal(b.copyOfRange(12,b.size)))
        } }
    }
    @Synchronized fun clear() { android.util.AtomicFile(file).delete() }
    fun saveIdentity(identity: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val target=File(PlanetApplication.appContext.noBackupFilesDir,"sicau-identity.enc")
        val atomic=android.util.AtomicFile(target)
        val stream=atomic.startWrite()
        try { stream.write(cipher.iv+cipher.doFinal(identity.toByteArray()));atomic.finishWrite(stream) }
        catch(e:Exception) { atomic.failWrite(stream);throw e }
    }
    fun readIdentity(): String {
        val target=File(PlanetApplication.appContext.noBackupFilesDir,"sicau-identity.enc")
        if(!target.exists()) return ""
        val b=android.util.AtomicFile(target).readFully();require(b.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)))
        return String(cipher.doFinal(b.copyOfRange(12,b.size)))
    }
    fun clearIdentity() { android.util.AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir,"sicau-identity.enc")).delete() }
}
internal class NativeAuthSession(val jar:SchoolCookieJar=SchoolCookieJar()) {
    private val received = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    fun receivedCookies(): List<Pair<String, String>> = synchronized(received) { received.toList() }
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val cookies = jar.loadForRequest(chain.request().url)
            val request = chain.request().newBuilder().apply {
                if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
            }.build()
            chain.proceed(request)
        }
        .addNetworkInterceptor { chain -> chain.proceed(chain.request()).also { response ->
            jar.acceptRawCookies(response.request.url, response.headers("Set-Cookie"))
            response.headers("Set-Cookie").forEach { received.add(response.request.url.toString() to it) }
        } }
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build()
    suspend fun request(path:String,body:JsonObject?=null):String = withContext(Dispatchers.IO) {
        require(path in setOf("/wui/","/api/v1/captcha","/api/v1/cas/login","/api/v1/cas/login?appid=sicau-cas"))
        val builder=Request.Builder().url(SicauAuthProtocol.ORIGIN+path).header("Referer",SicauAuthProtocol.ORIGIN+"/wui/")
        if(body!=null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val call=client.newCall(builder.build())
        try { call.execute().use { response ->
            if(!response.isSuccessful) throw java.io.IOException("SCHOOL_HTTP")
            val source=response.body?.source() ?: throw java.io.IOException("SCHOOL_EMPTY")
            source.request(2_000_001);require(source.buffer.size<=2_000_000);source.readUtf8()
        } } finally { call.cancel() }
    }
    suspend fun challenge():AuthChallenge {
        request("/wui/")
        val r=SicauAuthProtocol.decode(request("/api/v1/captcha"))
        require(r.get("code").asInt==200)
        return AuthChallenge(r.get("id").asString,r.get("data").asString).also { require(it.image.startsWith("data:image/")) }
    }
    suspend fun login(number:String,password:String,code:String,challenge:AuthChallenge):JsonObject =
        SicauAuthProtocol.decode(request("/api/v1/cas/login",SicauAuthProtocol.loginBody(number,password,code,challenge.id)))
    suspend fun verify():JsonObject = SicauAuthProtocol.decode(request("/api/v1/cas/login?appid=sicau-cas"))
    fun cancel() { client.dispatcher.cancelAll() }
}
internal class AuthChallenge(val id:String,val image:String) // Do not generate a toString containing challenge data.
internal object SicauNativeAuth {
    val webRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    @Volatile private var clearing: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var active:NativeAuthSession?=null
    private var activeAccount:String?=null
    private var justVerified:String?=null
    suspend fun activate(session:NativeAuthSession,id:String, expectedRevision: Long = webRevision.value) {
        val epoch = expectedRevision
        clearing?.await()
        if (epoch != webRevision.value) return
        session.jar.adoptCurrentRevision()
        withContext(Dispatchers.IO) { synchronized(this@SicauNativeAuth) {
            if(epoch == webRevision.value) { SchoolSessionVault.save(session.jar);active=session;activeAccount=id;justVerified=id }
        } }
        if (epoch != webRevision.value) return
        exportToWebView(replaceExisting = true)
        if(epoch != webRevision.value) return
        UnifiedAuthState.move(UnifiedAuthPhase.VALID)
    }
    suspend fun exportToWebView(replaceExisting: Boolean = false) = withContext(Dispatchers.Main) {
        val epoch = webRevision.value
        clearing?.await()
        if(epoch != webRevision.value) return@withContext
        val browserSnapshotExists = SchoolWebSessionStore.hasSnapshot()
        SchoolWebSessionStore.restoreOnce()
        WebVpnSessionStore.restoreOnce()
        if(epoch != webRevision.value) return@withContext
        val manager=android.webkit.CookieManager.getInstance()
        manager.setAcceptCookie(true)
        // A newer browser login wins over an older encrypted native snapshot.
        if(!replaceExisting && (browserSnapshotExists || SchoolSsoNavigation.authLocations(SicauAuthProtocol.ORIGIN).any { url ->
                manager.getCookie(url).orEmpty().split(';').any { it.trim().startsWith("go-admin-tgt=") }
            })) {
            importFromWebView()
            return@withContext
        }
        val session=active ?: withContext(Dispatchers.IO) { NativeAuthSession(SchoolSessionVault.read()) }
        if(epoch != webRevision.value) return@withContext
        active=session
        for(cookie in session.jar.rawSnapshot()) kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            if (epoch != webRevision.value) { continuation.resumeWith(Result.success(Unit)); return@suspendCancellableCoroutine }
            manager.setCookie(SicauAuthProtocol.ORIGIN,cookie) {
                if(continuation.isActive) continuation.resumeWith(Result.success(Unit))
            }
        }
        manager.flush()
        SchoolWebSessionStore.capture()
    }
    suspend fun importFromWebView() {
        if(clearing?.isCompleted == false) return
        val epoch = webRevision.value
        val cookies=withContext(Dispatchers.Main) {
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.GET_COOKIE_INFO)) null
            else SchoolSsoNavigation.authLocations(SicauAuthProtocol.ORIGIN).flatMap {
                androidx.webkit.CookieManagerCompat.getCookieInfo(android.webkit.CookieManager.getInstance(), it)
            }.distinct()
        } ?: return // Never reconstruct attributes from the Cookie request header.
        val session=active ?: withContext(Dispatchers.IO) { NativeAuthSession(SchoolSessionVault.read()) }
        if(epoch != webRevision.value) return
        withContext(Dispatchers.IO) { synchronized(this@SicauNativeAuth) {
            if(epoch == webRevision.value) {
                active=session
                session.jar.importAuthCookies(cookies)
                SchoolSessionVault.save(session.jar)
            }
        } }
        justVerified=null
        SchoolWebSessionStore.capture()
    }
    suspend fun identity():SchoolRead<String> = withContext(Dispatchers.IO) {
        val epoch = webRevision.value
        justVerified?.let { justVerified=null;return@withContext SchoolRead.Success(it) }
        val id=activeAccount ?: StudentInfoManager.studentId
        if(id.isBlank()) return@withContext SchoolRead.Failure(SyncPhase.NOT_SYNCED,"请绑定学校账号")
        val session=active ?: NativeAuthSession(SchoolSessionVault.read()).also { active=it }
        try {
            val r=session.verify()
            if(epoch != webRevision.value) throw kotlinx.coroutines.CancellationException("School session changed")
            val verified=SicauAuthProtocol.confirmedNumber(r,id)
            if(verified!=null) {
                synchronized(this@SicauNativeAuth) { if(epoch == webRevision.value) SchoolSessionVault.save(session.jar) }
                SchoolRead.Success(verified)
            }
            else if(!SicauAuthProtocol.accepted(r)) SchoolRead.Failure(SyncPhase.EXPIRED,"学校登录已过期，请重新认证")
            else SchoolRead.Failure(SyncPhase.PARSER_PENDING,"学校登录尚未完成，请重新认证")
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(_:Exception) { SchoolRead.Failure(SyncPhase.FAILED,"暂时无法连接学校，已保留上次课程") }
    }
    @Synchronized fun clear() {
        webRevision.value++
        AcademicCredentialVault.clear()
        SicauJiaowuAuth.clear()
        UnifiedAuthState.move(UnifiedAuthPhase.UNKNOWN)
        SchoolWebSessions.reset()
        SchoolWebSessionStore.clear()
        WebVpnSessionStore.clear()
        SicauWebVpn.forgetRoute()
        active?.cancel();active=null;activeAccount=null;justVerified=null;SchoolSessionVault.clear()
        // All official WebViews share Chromium's cookie store, including service sessions.
        val cleared = kotlinx.coroutines.CompletableDeferred<Unit>()
        clearing = cleared
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            SchoolWebSessions.stopBrowsers()
            android.webkit.CookieManager.getInstance().removeAllCookies {
                android.webkit.CookieManager.getInstance().flush()
                val storage = android.webkit.WebStorage.getInstance()
                if(androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DELETE_BROWSING_DATA)) {
                    // Also remove service-worker/IndexedDB tokens, while preserving offline
                    // appassets records (lost-and-found, local drafts, etc.).
                    androidx.webkit.WebStorageCompat.deleteBrowsingDataForSite(storage,"https://sicau.edu.cn") {
                        cleared.complete(Unit)
                    }
                } else {
                    // Compatibility fallback for older system WebViews.
                    storage.deleteAllData()
                    cleared.complete(Unit)
                }
            }
        }
    }
    suspend fun awaitCleared() { clearing?.await() }
}

package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
internal class SchoolCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if(url.scheme != "https" || url.host != "auth.sicau.edu.cn") return
        incoming.filter { it.domain == "auth.sicau.edu.cn" || it.domain == "sicau.edu.cn" }.forEach { c ->
            cookies.removeAll { it.name==c.name && it.domain==c.domain && it.path==c.path };cookies.add(c)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if(url.scheme!="https" || url.host!="auth.sicau.edu.cn") return emptyList()
        cookies.removeAll { it.expiresAt < System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    @Synchronized fun encode(): ByteArray = cookies.joinToString("\n") { it.toString() }.toByteArray()
    @Synchronized fun snapshot(): List<Cookie> = loadForRequest(SicauAuthProtocol.ORIGIN.toHttpUrl()).toList()
    @Synchronized fun importAuthCookie(header: String?) {
        // The observed SSO cookie is host-scoped at /. Do not invent domain-wide cookies.
        val value = header.orEmpty().split(';').map { it.trim() }
            .firstOrNull { it.startsWith("go-admin-tgt=") }?.substringAfter('=')
        cookies.removeAll { it.name == "go-admin-tgt" }
        if (!value.isNullOrBlank()) saveFromResponse(SicauAuthProtocol.ORIGIN.toHttpUrl(), listOf(
            Cookie.Builder().name("go-admin-tgt").value(value).hostOnlyDomain("auth.sicau.edu.cn")
                .path("/").secure().httpOnly().build()))
    }
    fun restore(bytes:ByteArray) { saveFromResponse(SicauAuthProtocol.ORIGIN.toHttpUrl(),String(bytes).lines().mapNotNull { Cookie.parse(SicauAuthProtocol.ORIGIN.toHttpUrl(),it) }) }
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
    private val client=OkHttpClient.Builder().cookieJar(jar).followRedirects(false).followSslRedirects(false)
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
    private val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    fun persistFromWebView() {
        val epoch = webRevision.value
        persistenceScope.launch {
            if(epoch == webRevision.value) runCatching { importFromWebView() }
        }
    }
    val webRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    @Volatile private var clearing: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var active:NativeAuthSession?=null
    private var activeAccount:String?=null
    private var justVerified:String?=null
    suspend fun activate(session:NativeAuthSession,id:String) {
        withContext(Dispatchers.IO) { SchoolSessionVault.save(session.jar) }
        active=session;activeAccount=id;justVerified=id
        exportToWebView(replaceExisting = true)
        SchoolAuthState.move(SchoolAuthPhase.SESSION_VALID)
    }
    suspend fun exportToWebView(replaceExisting: Boolean = false) = withContext(Dispatchers.Main) {
        val epoch = webRevision.value
        clearing?.await()
        if(epoch != webRevision.value) return@withContext
        WebVpnSessionStore.restoreOnce()
        if(epoch != webRevision.value) return@withContext
        val manager=android.webkit.CookieManager.getInstance()
        manager.setAcceptCookie(true)
        // A newer browser login wins over an older encrypted native snapshot.
        if(!replaceExisting && manager.getCookie(SicauAuthProtocol.ORIGIN).orEmpty()
                .split(';').any { it.trim().startsWith("go-admin-tgt=") }) {
            importFromWebView()
            return@withContext
        }
        val session=active ?: withContext(Dispatchers.IO) { NativeAuthSession(SchoolSessionVault.read()) }
        if(epoch != webRevision.value) return@withContext
        active=session
        for(cookie in session.jar.snapshot()) kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            manager.setCookie(SicauAuthProtocol.ORIGIN,cookie.toString()) {
                if(continuation.isActive) continuation.resumeWith(Result.success(Unit))
            }
        }
        manager.flush()
    }
    suspend fun importFromWebView() {
        if(clearing?.isCompleted == false) return
        val epoch = webRevision.value
        val header=withContext(Dispatchers.Main) { android.webkit.CookieManager.getInstance().getCookie(SicauAuthProtocol.ORIGIN) }
        val session=active ?: withContext(Dispatchers.IO) { NativeAuthSession(SchoolSessionVault.read()) }
        if(epoch != webRevision.value) return
        active=session
        session.jar.importAuthCookie(header)
        withContext(Dispatchers.IO) { synchronized(this@SicauNativeAuth) {
            if(epoch == webRevision.value) SchoolSessionVault.save(session.jar)
        } }
        justVerified=null
    }
    suspend fun identity():SchoolRead<String> = withContext(Dispatchers.IO) {
        justVerified?.let { justVerified=null;return@withContext SchoolRead.Success(it) }
        val id=activeAccount ?: StudentInfoManager.studentId
        if(id.isBlank()) return@withContext SchoolRead.Failure(SyncPhase.NOT_SYNCED,"请绑定学校账号")
        val session=active ?: NativeAuthSession(SchoolSessionVault.read()).also { active=it }
        try {
            val r=session.verify()
            val verified=SicauAuthProtocol.confirmedNumber(r,id)
            if(verified!=null) SchoolRead.Success(verified)
            else if(!SicauAuthProtocol.accepted(r)) SchoolRead.Failure(SyncPhase.EXPIRED,"学校登录已过期，请重新认证")
            else SchoolRead.Failure(SyncPhase.PARSER_PENDING,"学校登录尚未完成，请重新认证")
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(_:Exception) { SchoolRead.Failure(SyncPhase.FAILED,"暂时无法连接学校，已保留上次课程") }
    }
    @Synchronized fun clear() {
        webRevision.value++
        WebVpnSessionStore.clear()
        SicauWebVpn.forgetRoute()
        active=null;activeAccount=null;justVerified=null;SchoolSessionVault.clear()
        // All official WebViews share Chromium's cookie store, including service sessions.
        val cleared = kotlinx.coroutines.CompletableDeferred<Unit>()
        clearing = cleared
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.webkit.CookieManager.getInstance().removeAllCookies {
                android.webkit.CookieManager.getInstance().flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
                cleared.complete(Unit)
            }
        }
    }
}

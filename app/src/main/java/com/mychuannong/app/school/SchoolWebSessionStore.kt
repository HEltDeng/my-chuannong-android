package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import com.mychuannong.app.core.PlanetApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/** Encrypted session cookies survive process death without extending their server deadlines.
 * WebView retains ownership of normal cookie matching, SameSite and HttpOnly enforcement.
 * URLs contain only origin/path: no CAS tickets, OAuth codes, query strings or fragments. */
internal object SchoolWebSessionStore {
    private val mutex = Mutex()
    private var restored = false
    private val locations = linkedSetOf(
        "https://auth.sicau.edu.cn/", "https://auth.sicau.edu.cn/wui/",
        "https://auth.sicau.edu.cn/api/v1/cas/login",
        "https://webvpn.sicau.edu.cn/", "https://www.sicau.edu.cn/",
        "https://ywtb.sicau.edu.cn/", "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex",
        "https://op.sicau.edu.cn/CasLogin.aspx", SicauJiaowuProtocol.PERSONAL_INFO,
    )
    private val file get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "sicau-web-sessions.enc"))
    fun hasSnapshot(): Boolean = file.baseFile.exists()

    /** Called on the UI thread when navigation starts, including short-lived SSO hops. */
    fun remember(url: String) {
        (listOf(url) + SchoolSsoNavigation.authLocations(url)).forEach { raw ->
            SchoolWebCookiePolicy.location(raw)?.let { if(locations.size < 256) locations.add(it) }
        }
    }

    private fun key(): SecretKey {
        val alias = "mychuannong_school_web_sessions_v1"
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keys.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun read(): JSONArray {
        if (!file.baseFile.exists()) return JSONArray()
        val bytes = file.readFully()
        require(bytes.size in 29..2_000_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        val plaintext = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        return try { JSONArray(String(plaintext, Charsets.UTF_8)) } finally { plaintext.fill(0) }
    }

    private fun write(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stream = file.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(bytes)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        finally { bytes.fill(0) }
    }

    suspend fun restoreOnce() = mutex.withLock {
        if (restored) return@withLock
        val epoch = SicauNativeAuth.webRevision.value
        // A corrupt/key-invalidated snapshot is discarded; never fall back to plaintext.
        val saved = withContext(Dispatchers.IO) { runCatching { read() }.getOrElse { file.delete(); JSONArray() } }
        withContext(Dispatchers.Main) {
            if (epoch != SicauNativeAuth.webRevision.value) return@withContext
            val manager = CookieManager.getInstance()
            val fullCookieInfo = WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
            val items = (0 until saved.length().coerceAtMost(1024)).mapNotNull { saved.optJSONObject(it) }
            val urls = items.mapNotNull { SchoolWebCookiePolicy.location(it.optString("url")) }.distinct()
            locations.addAll(urls)
            val live = if (!fullCookieInfo) emptySet() else urls.flatMap { url -> CookieManagerCompat.getCookieInfo(manager, url)
                .mapNotNull { SchoolWebCookiePolicy.valid(url, it)?.let(SchoolWebCookiePolicy::key) } }.toSet()
            for (item in items) {
                if (epoch != SicauNativeAuth.webRevision.value) return@withContext
                val url = SchoolWebCookiePolicy.location(item.optString("url")) ?: continue
                val raw = item.optString("cookie")
                val cookie = SchoolWebCookiePolicy.valid(url, raw) ?: continue
                if (SchoolWebCookiePolicy.key(cookie) in live) continue
                // Older WebViews can replay full encrypted attributes even though they
                // cannot export them. Prefer any current same-name cookie at this scope.
                if (!fullCookieInfo && manager.getCookie(url).orEmpty().split(';')
                        .any { it.trim().substringBefore('=') == cookie.name }) continue
                setCookie(manager, url, raw)
            }
            manager.flush()
        }
        if (epoch == SicauNativeAuth.webRevision.value) restored = true
    }

    suspend fun capture(url: String? = null, additionalLocations: List<String> = emptyList()) = mutex.withLock {
        // Restore before replacing a snapshot, otherwise the first page could erase other services.
        if (!restored) return@withLock
        val epoch = SicauNativeAuth.webRevision.value
        val snapshot = withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            manager.flush()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) return@withContext null
            SchoolWebCookiePolicy.location(url.orEmpty())?.let { if (locations.size < 256) locations.add(it) }
            additionalLocations.forEach { raw -> SchoolWebCookiePolicy.location(raw)?.let { if(locations.size < 256) locations.add(it) } }
            val items = JSONArray()
            val seen = mutableSetOf<String>()
            locations.forEach { location ->
                CookieManagerCompat.getCookieInfo(manager, location).forEach { raw ->
                    val durable = SchoolWebCookiePolicy.durable(location, raw)
                    val cookie = durable?.let { SchoolWebCookiePolicy.valid(location, it) }
                    if (cookie != null && seen.size < 1024 && seen.add(SchoolWebCookiePolicy.key(cookie))) {
                        items.put(JSONObject().put("url", location).put("cookie", durable))
                    }
                }
            }
            items.toString()
        } ?: return@withLock
        withContext(Dispatchers.IO) {
            synchronized(SicauNativeAuth) {
                if (epoch == SicauNativeAuth.webRevision.value) write(snapshot)
            }
        }
    }

    /** Caller holds the logout revision lock. Queued captures cannot resurrect this snapshot. */
    fun clear() { restored = true; file.delete() }

    internal suspend fun setCookie(manager: CookieManager, url: String, raw: String) = suspendCancellableCoroutine<Unit> { c ->
        manager.setCookie(url, raw) { accepted ->
            if (c.isActive) {
                if (accepted) c.resume(Unit) else c.resumeWith(Result.failure(IllegalStateException("Cookie storage unavailable")))
            }
        }
    }
}

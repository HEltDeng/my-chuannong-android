package com.mychuannong.app.school

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Synthetic cookies only; use the isolated test package, never a user's session. */
class UnifiedWebSessionDeviceTest {
    @Test fun newerBrowserSessionWinsAndLogoutClearsEveryModule() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(i.targetContext.packageName.endsWith(".cachetest"))
        SicauNativeAuth.clear()
        SicauNativeAuth.exportToWebView() // Await pending Chromium cleanup.
        try {
            val jar = SchoolCookieJar().apply { importAuthCookie("go-admin-tgt=synthetic-old") }
            SicauNativeAuth.activate(NativeAuthSession(jar), "synthetic-owner")
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c ->
                    CookieManager.getInstance().setCookie(SicauAuthProtocol.ORIGIN,
                        "go-admin-tgt=synthetic-new; Path=/; Secure; HttpOnly") {
                        c.resumeWith(Result.success(Unit))
                    }
                }
            }
            SicauNativeAuth.exportToWebView()
            withContext(Dispatchers.Main) {
                assertTrue(CookieManager.getInstance().getCookie(SicauAuthProtocol.ORIGIN).contains("synthetic-new"))
            }
            assertTrue(SchoolSessionVault.read().snapshot().single().value == "synthetic-new")
            assertFalse(java.io.File(i.targetContext.noBackupFilesDir,"sicau-session.enc")
                .readBytes().toString(Charsets.UTF_8).contains("synthetic-new"))
            SicauNativeAuth.clear()
            SicauNativeAuth.exportToWebView()
            withContext(Dispatchers.Main) {
                assertTrue(CookieManager.getInstance().getCookie(SicauAuthProtocol.ORIGIN).isNullOrBlank())
            }
            assertTrue(SchoolSessionVault.read().snapshot().isEmpty())
        } finally { SicauNativeAuth.clear(); SicauNativeAuth.exportToWebView() }
    }
}

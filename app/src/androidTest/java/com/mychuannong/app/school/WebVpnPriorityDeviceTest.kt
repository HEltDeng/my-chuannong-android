package com.mychuannong.app.school

import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** No school requests or real credentials; requires the isolated validation package. */
class WebVpnPriorityDeviceTest {
    @Test fun missingSessionRequiresAuthenticationAndLogoutRemovesVpnAndUnifiedCookies() = runBlocking {
        assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".validation"))
        SicauNativeAuth.clear()
        SicauNativeAuth.exportToWebView()
        try {
            assertFalse(SicauWebVpn.check())
            assertEquals(WebVpnPhase.AUTH_REQUIRED, SicauWebVpn.phase.value)
            val prefix = "/https/" + "a".repeat(64)
            SicauWebVpn.discovered(requireNotNull(SicauJiaowuRoute.restore(prefix)))
            withContext(Dispatchers.Main) {
                for(origin in listOf(SicauJiaowuRoute.VPN_ORIGIN, SicauAuthProtocol.ORIGIN)) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c ->
                        CookieManager.getInstance().setCookie(origin, "synthetic=only-test; Secure; HttpOnly; Path=/") { c.resumeWith(Result.success(Unit)) }
                    }
                    assertTrue(CookieManager.getInstance().getCookie(origin).contains("only-test"))
                }
            }
            SicauNativeAuth.clear()
            SicauNativeAuth.exportToWebView()
            assertNull(SicauWebVpn.route())
            assertEquals(WebVpnPhase.UNKNOWN, SicauWebVpn.phase.value)
            withContext(Dispatchers.Main) {
                for(origin in listOf(SicauJiaowuRoute.VPN_ORIGIN, SicauAuthProtocol.ORIGIN))
                    assertTrue(CookieManager.getInstance().getCookie(origin).isNullOrBlank())
            }
        } finally { SicauNativeAuth.clear(); SicauNativeAuth.exportToWebView() }
    }
}

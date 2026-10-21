package com.mychuannong.app.school

import android.webkit.CookieManager
import android.webkit.WebView
import android.content.Intent
import android.view.WindowManager
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.settings.ui.NativeBindingViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import java.io.File

/** Synthetic cookies/identity only. The screen smoke opens the public entry GET, then
 * replaces it with a local fixture; it never submits a school login or SMS request. */
class AcademicLoginVerificationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    @Before fun isolatedPackageOnly() = runBlocking {
        check(inst.targetContext.packageName.endsWith(".bindingtest"))
        SchoolSyncEngine.clear()
        PrivacyConsentManager.acceptAppConsent(inst.targetContext)
        SchoolAccessGate.restoreBinding()
        Unit
    }
    @After fun clearFixtures() = runBlocking { SchoolSyncEngine.clear() }

    @Test fun chromiumCookieWriteIsVisibleBeforeTheNextNativeRequest() = runBlocking {
        val route = requireNotNull(SicauJiaowuRoute.restore("/https/" + "a".repeat(64)))
        val url = route.url(SicauJiaowuProtocol.LOGIN_PAGE).toHttpUrl()
        val jar = SicauJiaowuCookieJar(route)
        val raw = "ASPSESSIONIDSYNTHETIC=fresh-device-test; Path=${route.prefix}/; Secure; HttpOnly; SameSite=Lax"
        jar.acceptResponseCookies(url, listOf(raw), SicauNativeAuth.webRevision.value)
        assertEquals("fresh-device-test", jar.loadForRequest(url).single { it.name == "ASPSESSIONIDSYNTHETIC" }.value)
        assertTrue(jar.encode().contains("SameSite=Lax"))
        assertTrue(jar.encode().contains("HttpOnly"))
        assertFalse(SchoolAccessGate.isAllowed)
        inst.runOnMainSync { CookieManager.getInstance().setCookie(url.toString(),
            "ASPSESSIONIDSYNTHETIC=; Path=${route.prefix}/; Secure; Max-Age=0") }
    }
    @Test fun anActivityResultWithoutAPendingLoginCannotGrantFirstUseAccess() {
        inst.runOnMainSync {
            val task = NativeBindingViewModel(checkVpn = { false })
            task.schoolVerificationFinished(true)
            assertFalse(task.state.value.schoolVerificationAvailable)
            assertNull(task.startSchoolVerification())
            assertFalse(SchoolAccessGate.isAllowed)
            task.cancel()
        }
    }
    @Test fun schoolVerificationScreenCanOpenBeforeBindingButCannotGrantAccess() {
        val intent = Intent(inst.targetContext, SchoolLoginVerificationActivity::class.java)
            .putExtra(SchoolLoginVerificationActivity.EXTRA_ROUTE, "")
        ActivityScenario.launch<SchoolLoginVerificationActivity>(intent).use { scenario ->
            compose.onNodeWithText("学校登录验证").assertIsDisplayed()
            var navigated = false
            compose.waitUntil(10_000) {
                scenario.onActivity { activity ->
                    val web = SchoolLoginVerificationActivity::class.java.getDeclaredField("web")
                        .apply { isAccessible = true }.get(activity) as WebView
                    navigated = web.url != null
                }
                navigated
            }
            scenario.onActivity { activity ->
                val web = SchoolLoginVerificationActivity::class.java.getDeclaredField("web")
                    .apply { isAccessible = true }.get(activity) as WebView
                web.stopLoading()
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                web.loadDataWithBaseURL(SicauJiaowuProtocol.LOGIN_PAGE,
                    "<html><meta name='viewport' content='width=device-width'><body style='font-family:sans-serif;padding:24px'><h2>合成学校验证页面</h2><p>此页仅验证页面布局与返回流程。</p></body></html>",
                    "text/html", "UTF-8", null)
            }
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText("完成验证，继续绑定").assertIsEnabled(); true }.getOrDefault(false)
            }
            assertFalse(SchoolAccessGate.isAllowed)
            val picture = inst.uiAutomation.takeScreenshot()
            if (picture != null) {
                val file = File(inst.targetContext.getExternalFilesDir("login-verification"), "verification-screen-synthetic.png")
                file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                picture.recycle()
            }
            compose.onNodeWithText("完成验证，继续绑定").performClick()
            compose.waitForIdle()
            assertFalse(SchoolAccessGate.isAllowed)
        }
    }
}

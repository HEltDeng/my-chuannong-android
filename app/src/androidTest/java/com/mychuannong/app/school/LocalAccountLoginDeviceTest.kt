package com.mychuannong.app.school

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.account.ui.AccountCenterActivity
import com.mychuannong.app.core.account.ui.AccountRequiredActivity
import com.mychuannong.app.core.network.ServiceEndpointPolicy
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic accounts only, in an isolated install, against the actual local API. */
@RunWith(AndroidJUnit4::class)
class LocalAccountLoginDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext

    @Test fun registerThenLoginAndRestoreThroughLocalBackend() {
        check(context.packageName == "com.mychuannong.app.accounttest")
        assertTrue(ServiceEndpointPolicy.isAccountServiceConfigured())
        prepareSyntheticBinding()
        val phone = "199" + (System.currentTimeMillis() % 100_000_000).toString().padStart(8, '0')
        val password = UUID.randomUUID().toString()
        var created = false
        var owner: String? = null
        try {
            ActivityScenario.launch<AccountRequiredActivity>(loginIntent()).use { scenario ->
                compose.onNodeWithText("登录我的川农账号").assertIsDisplayed()
                capture("account-login")
                compose.onNodeWithText("没有账号？创建账号").performScrollTo().performClick()
                compose.onNodeWithText("创建我的川农账号").assertIsDisplayed()
                fill(scenario, "昵称", "账号联调合成用户")
                fill(scenario, "手机号", phone)
                fill(scenario, "密码", password)
                compose.onNodeWithText("注册并进入").performScrollTo().performClick()
                compose.waitUntil(20_000) { CampusToolsSession.currentUser != null }
                created = true
                owner = requireNotNull(CampusToolsSession.currentUser).id
                compose.waitUntil(15_000) {
                    compose.onAllNodes(androidx.compose.ui.test.hasText("账号与安全")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("账号与安全").assertIsDisplayed()
            }
            runBlocking { CampusToolsSession.logout() }
            assertNull(CampusToolsSession.currentUser)
            prepareSyntheticBinding()
            ActivityScenario.launch<AccountRequiredActivity>(loginIntent()).use { scenario ->
                fill(scenario, "手机号", phone)
                fill(scenario, "密码", "wrong-password")
                compose.onNodeWithText("登录", substring = false).performScrollTo().performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodes(androidx.compose.ui.test.hasText("账号或密码错误", substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
                assertNull(CampusToolsSession.currentUser)
                fill(scenario, "密码", password)
                compose.onNodeWithText("登录", substring = false).performScrollTo().performClick()
                compose.waitUntil(20_000) { CampusToolsSession.currentUser?.id == owner }
            }
            runBlocking { CampusToolsSession.restore() }
            assertEquals(owner, CampusToolsSession.currentUser?.id)
            assertFalse(CampusToolsSession.token.isNullOrBlank())
        } finally {
            if (created) runBlocking {
                if (CampusToolsSession.currentUser?.id != owner) CampusToolsSession.login(phone, password)
                CampusToolsSession.deleteAccount(password)
            }
        }
        assertNull(CampusToolsSession.currentUser)
    }

    private fun loginIntent() = AccountRequiredActivity.intent(
        context, Intent(context, AccountCenterActivity::class.java),
    )

    private fun fill(scenario: ActivityScenario<AccountRequiredActivity>, label: String, value: String) {
        scenario.onActivity { activity ->
            fun fields(view: View): List<EditText> = when (view) {
                is EditText -> listOf(view)
                is ViewGroup -> (0 until view.childCount).flatMap { fields(view.getChildAt(it)) }
                else -> emptyList()
            }
            fields(activity.window.decorView).single { it.hint?.toString()?.startsWith(label) == true }.setText(value)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        inst.waitForIdleSync()
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun prepareSyntheticBinding() = runBlocking {
        // School requests remain inside this authenticated test fixture.
        val owner = "20269999"
        PrivacyConsentManager.acceptAppConsent(context)
        val jar = SicauJiaowuCookieJar()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.toString()) {
                SicauJiaowuProtocol.LOGIN_PAGE -> {
                    jar.saveFromResponse(request.url, listOf(requireNotNull(Cookie.parse(request.url,
                        "ASPSESSIONIDTEST=synthetic-cookie; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                    "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                }
                SicauJiaowuProtocol.LOGIN_POST -> "<script>location='student.asp'</script>"
                else -> "<table><tr><td>学号</td><td>$owner</td></tr></table>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body(body.toByteArray(charset("GBK")).toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
    }
}

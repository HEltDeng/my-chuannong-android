package com.mychuannong.app.school

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.textfield.TextInputLayout
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.core.security.SchoolEntryActivity
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.feature.common.ui.ScoreInquiryActivity
import com.mychuannong.app.feature.common.ui.SecondhandActivity
import com.mychuannong.app.feature.common.ui.CampusServicesActivity
import com.mychuannong.app.settings.ui.BindingUserActivity
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** All identity/HTTP fixtures are synthetic. Never install or run against a user's package. */
class BindingEntryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val owner = "20260001"

    @Before fun resetIsolatedState() = runBlocking {
        check(context.packageName.endsWith(".bindingtest"))
        finishActivities()
        SchoolSyncEngine.clear()
        context.getSharedPreferences("privacy_consent", 0).edit().clear().commit()
        SchoolAccessGate.restoreBinding()
        NotificationPermissionPreferences.markEducationShown(context)
    }

    @After fun clearFixtures() = runBlocking {
        finishActivities()
        if (InstrumentationRegistry.getArguments().getString("keepBindingFixture") != "true") SchoolSyncEngine.clear()
    }

    @Test fun firstConsentOpensHomeAndLearningPageOpensSchoolLoginDirectly() {
        launch(SchoolEntryActivity::class.java)
        awaitActivity(SchoolEntryActivity::class.java)
        compose.onNodeWithText("用户协议及隐私保护").assertIsDisplayed()
        compose.onNodeWithText("同意").performClick()
        awaitActivity(MainActivity::class.java)
        inst.runOnMainSync { currentActivity()!!.startActivity(Intent(context, TimeTableActivity::class.java)) }
        assertFalse(SchoolAccessGate.isAllowed)
        awaitActivity(UnifiedSchoolAuthenticationActivity::class.java)
        compose.onNodeWithText("学号").assertIsDisplayed()
        compose.onNodeWithText("密码").assertIsDisplayed()
        compose.onNodeWithText("验证码").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        awaitActivity(MainActivity::class.java)
        assertTrue(PrivacyConsentManager.hasAppConsent(context))
        assertFalse(SchoolAccessGate.isAllowed)
    }

    @Test fun verifiedFirstBindingContinuesTheRequestedTimetable() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        launch(TimeTableActivity::class.java)
        awaitActivity(UnifiedSchoolAuthenticationActivity::class.java)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SchoolSyncEngine.bindJiaowu(session)
        inst.runOnMainSync {
            (currentActivity() as UnifiedSchoolAuthenticationActivity).apply {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
        awaitActivity(TimeTableActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @Test fun localFlagsAndDirectFeatureIntentsCannotBypassBinding() {
        PrivacyConsentManager.acceptAppConsent(context)
        MMKV.mmkvWithID("sicau_verified_identity").encode("jiaowu_verified_v1", true)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        for (target in listOf(MainActivity::class.java, TimeTableActivity::class.java,
            ScoreInquiryActivity::class.java, SecondhandActivity::class.java, CampusServicesActivity::class.java)) {
            launch(target)
            awaitActivity(BindingUserActivity::class.java)
            assertFalse("Blocked ${target.simpleName}", SchoolAccessGate.isAllowed)
        }
    }

    @Test fun verifiedSchoolSessionEntersHomeAndRevocationReturnsToBinding() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        // Restored consent does not show introduction a second time.
        compose.onNodeWithText("欢迎使用我的川农").assertDoesNotExist()
        SchoolSyncEngine.clear()
        awaitActivity(BindingUserActivity::class.java)
        assertFalse(SchoolAccessGate.isAllowed)
    }

    @Test fun corruptedBindingProofCannotFallBackToValidCookiesOrLocalMarker() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        val file = File(context.noBackupFilesDir, "sicau-binding.enc")
        val ciphertext = file.readBytes()
        assertFalse(String(ciphertext).contains("synthetic-password"))
        assertFalse(String(ciphertext).contains("synthetic-cookie"))
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
        file.writeBytes(ciphertext)
        assertNull(SicauJiaowuSessionVault.bindingOwner())
        assertNotNull(SicauJiaowuSessionVault.read())
        SchoolAccessGate.restoreBinding()
        launch(SchoolEntryActivity::class.java)
        awaitActivity(BindingUserActivity::class.java)
        assertFalse(SchoolAccessGate.isAllowed)
    }

    @Test fun keyboardKeepsFocusedFieldErrorsAndSubmitVisibleAndRestoresLayout() {
        PrivacyConsentManager.acceptAppConsent(context)
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Screenshots contain only synthetic data; production keeps FLAG_SECURE.
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                androidx.lifecycle.ViewModelProvider(activity)[com.mychuannong.app.settings.ui.NativeBindingViewModel::class.java].let {
                    assertTrue(it.startVpnBinding()); it.vpnFinished(true)
                }
                activity.findViewById<TextView>(R.id.et_student_id).text = owner
            }
            inst.waitForIdleSync()
            capture("binding-form")
            var originalPadding = 0
            scenario.onActivity { activity ->
                originalPadding = (activity.findViewById<View>(R.id.binding_scroll).parent as View).paddingBottom
                focusAndShowKeyboard(activity, R.id.et_student_id)
            }
            waitForKeyboard(true)
            assertFieldAndButtonVisible(scenario, R.id.student_id_input_layout)
            capture("binding-id-keyboard")
            scenario.onActivity { activity -> focusAndShowKeyboard(activity, R.id.et_student_password) }
            waitForKeyboard(true)
            assertFieldAndButtonVisible(scenario, R.id.password_input_layout)
            scenario.onActivity { activity ->
                val password = activity.findViewById<TextView>(R.id.et_student_password)
                assertTrue(password.transformationMethod is PasswordTransformationMethod)
                assertFalse(password.isSaveEnabled)
                activity.findViewById<View>(R.id.save_user).performClick()
                assertEquals("请输入教务密码", activity.findViewById<TextInputLayout>(R.id.password_input_layout).error)
            }
            assertFieldAndButtonVisible(scenario, R.id.password_input_layout)
            capture("binding-password-error-keyboard")
            scenario.onActivity { activity ->
                val password = activity.findViewById<TextView>(R.id.et_student_password)
                password.text = "synthetic-hidden-password"
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(password.windowToken, 0)
            }
            waitForKeyboard(false)
            scenario.onActivity { activity ->
                assertEquals(originalPadding, (activity.findViewById<View>(R.id.binding_scroll).parent as View).paddingBottom)
                val button = activity.findViewById<View>(R.id.save_user)
                assertTrue(button.getGlobalVisibleRect(Rect()))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val password = activity.findViewById<TextView>(R.id.et_student_password)
                assertTrue(password.transformationMethod is PasswordTransformationMethod)
                assertFalse(password.isSaveEnabled)
                assertFalse(SchoolAccessGate.isAllowed)
            }
        }
    }

    @Test fun successfulBindingAndOfflineRefreshDoNotBlockHome() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        var profileRequests = 0
        var online = true
        val session = fixtureSession { url ->
            if (url == SicauJiaowuProtocol.PERSONAL_INFO) profileRequests++
            if (!online) throw java.io.IOException("synthetic-network-unavailable")
        }
        session.login(owner, "synthetic-password")
        SchoolSyncEngine.bindJiaowu(session)
        assertTrue(SchoolAccessGate.isAllowed)
        val completedBindingRequests = profileRequests
        online = false
        // Restoring access is local-only. The home dashboard can separately attempt
        // its existing background data refresh, which must never block navigation.
        SchoolAccessGate.restoreBinding()
        assertEquals(completedBindingRequests, profileRequests)
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        compose.onNodeWithText("正在验证教务登录…").assertDoesNotExist()
        compose.onNodeWithText("重新验证").assertDoesNotExist()
        // A normal refresh failing offline leaves the already bound user in the app.
        SchoolSyncEngine.sync(force = true)
        assertTrue(SchoolAccessGate.isAllowed)
        awaitActivity(MainActivity::class.java)
        // Foreground/background transitions no longer revoke the grant.
        inst.runOnMainSync { currentActivity()!!.moveTaskToBack(true) }
        awaitCondition { currentActivity() == null }
        assertTrue(SchoolAccessGate.isAllowed)
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @Test fun legacyEncryptedSessionMigratesButRevokedBindingCannotBeRestoredByCookies() = runBlocking {
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuSessionVault.save(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        // Simulate upgrading from the version without a separate binding receipt.
        val key = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey("mychuannong_sicau_jiaowu_v1", null)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        }
        File(context.noBackupFilesDir, "sicau-jiaowu.enc").writeBytes(cipher.iv +
            cipher.doFinal((owner + "\n" + session.jar.encode()).toByteArray(Charsets.UTF_8)))
        assertTrue(File(context.noBackupFilesDir, "sicau-binding.enc").delete())
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
        SchoolAccessGate.revokeBinding()
        SicauJiaowuSessionVault.save(session) // A late cookie refresh cannot undo revocation.
        SchoolAccessGate.restoreBinding()
        assertFalse(SchoolAccessGate.isAllowed)
        assertNull(SicauJiaowuSessionVault.bindingOwner())
        assertTrue(File(context.noBackupFilesDir, "sicau-binding.enc").delete())
        SchoolAccessGate.restoreBinding()
        assertFalse(SchoolAccessGate.isAllowed)
    }

    /** Optional isolated fixture for a shell-driven force-stop / cold-start check. */
    @Test fun prepareVerifiedBindingForColdStart() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @Test fun confirmedSchoolLoginExpiryKeepsBindingAndHomeAccessible() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        var expired = false
        val session = fixtureSession(loginExpired = { expired })
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        SchoolAccessGate.restoreBinding()
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        expired = true
        SchoolSyncEngine.sync(force = true)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
        assertEquals(owner, SicauJiaowuSessionVault.bindingOwner())
        assertNull(SicauJiaowuSessionVault.read())
        SchoolAccessGate.restoreBinding()
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @Test fun lostSessionFileAfterProcessRestoreKeepsVerifiedBinding() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        // Simulate an unavailable session vault plus a new process's empty memory.
        // The authenticated binding receipt is deliberately retained.
        SicauJiaowuSessionVault.clear()
        for(name in listOf("active")) SicauJiaowuAuth::class.java.getDeclaredField(name).apply {
            isAccessible = true; set(SicauJiaowuAuth, null)
        }
        SchoolAccessGate.restoreBinding()
        SchoolSyncEngine.sync(force = true)
        assertTrue(SchoolAccessGate.isAllowed)
        assertEquals(owner, SicauJiaowuSessionVault.bindingOwner())
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        // Exercise the same follow-up refresh the dashboard starts after cold launch.
        SchoolSyncEngine.readTranscript(owner, replaceExisting = true)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @Test fun missingSessionCookiesDoNotRevokeBindingDuringHomeRefresh() = runBlocking {
        PrivacyConsentManager.acceptAppConsent(context)
        val session = fixtureSession()
        session.login(owner, "synthetic-password")
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        session.jar.clear()
        SchoolAccessGate.restoreBinding()
        SchoolSyncEngine.sync(force = true)
        assertTrue(SchoolAccessGate.isAllowed)
        assertEquals(owner, SicauJiaowuSessionVault.bindingOwner())
        launch(SchoolEntryActivity::class.java)
        awaitActivity(MainActivity::class.java)
        assertTrue(SchoolAccessGate.isAllowed)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun autofillUsesTransparentHighlightAndKeepsPasswordHidden() {
        PrivacyConsentManager.acceptAppConsent(context)
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                androidx.lifecycle.ViewModelProvider(activity)[com.mychuannong.app.settings.ui.NativeBindingViewModel::class.java].let {
                    assertTrue(it.startVpnBinding()); it.vpnFinished(true)
                }
                val username = activity.findViewById<android.widget.EditText>(R.id.et_student_id)
                val password = activity.findViewById<android.widget.EditText>(R.id.et_student_password)
                username.autofill(android.view.autofill.AutofillValue.forText(owner))
                password.autofill(android.view.autofill.AutofillValue.forText("synthetic-password"))
                val colors = activity.obtainStyledAttributes(intArrayOf(android.R.attr.autofilledHighlight))
                try { assertEquals(android.graphics.Color.TRANSPARENT, colors.getColor(0, android.graphics.Color.YELLOW)) }
                finally { colors.recycle() }
                assertEquals(owner, username.text.toString())
                assertTrue(password.transformationMethod is PasswordTransformationMethod)
            }
        }
    }

    private fun fixtureSession(loginExpired: () -> Boolean = { false }, beforeRequest: (String) -> Unit = {}): SicauJiaowuSession {
        val jar = SicauJiaowuCookieJar()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            beforeRequest(request.url.toString())
            val body = when (request.url.toString()) {
                SicauJiaowuProtocol.LOGIN_PAGE -> {
                    jar.saveFromResponse(request.url, listOf(requireNotNull(Cookie.parse(request.url,
                        "ASPSESSIONIDTEST=synthetic-cookie; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                    "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                }
                SicauJiaowuProtocol.LOGIN_POST -> "<script>location='student.asp'</script>"
                else -> "<table><tr><td>学号</td><td>$owner</td></tr></table>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (loginExpired()) 401 else 200).message("synthetic")
                .body(body.toByteArray(charset("GBK")).toResponseBody()).build()
        }.build()
        return SicauJiaowuSession(jar, client, {})
    }

    private fun focusAndShowKeyboard(activity: Activity, id: Int) {
        val input = activity.findViewById<TextView>(id)
        input.requestFocus()
        activity.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun assertFieldAndButtonVisible(scenario: ActivityScenario<BindingUserActivity>, fieldId: Int) {
        awaitCondition {
            val activity = currentActivity() ?: return@awaitCondition false
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return@awaitCondition false
            val keyboardTop = activity.window.decorView.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            listOf(fieldId, R.id.save_user).all { id ->
                val view = activity.findViewById<View>(id)
                val visible = Rect()
                view.getGlobalVisibleRect(visible) && visible.height() == view.height &&
                    visible.bottom <= keyboardTop && visible.top >= insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            }
        }
        scenario.onActivity { assertTrue(it.findViewById<View>(R.id.save_user).isEnabled) }
    }

    private fun waitForKeyboard(visible: Boolean) = awaitCondition {
        currentActivity()?.window?.decorView?.let {
            val insets = ViewCompat.getRootWindowInsets(it)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == visible &&
                (!visible || insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 100 * it.resources.displayMetrics.density)
        } == true
    }

    private fun launch(type: Class<out Activity>) = context.startActivity(Intent(context, type)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

    private fun awaitActivity(type: Class<out Activity>) = awaitCondition { type.isInstance(currentActivity()) }
    private fun currentActivity(): Activity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull()

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            compose.waitForIdle()
            var passed = false
            inst.runOnMainSync { passed = condition() }
            if (passed) { inst.waitForIdleSync(); return }
            android.os.SystemClock.sleep(100)
        }
        fail("Timed out waiting for the expected safe screen/layout")
    }

    private fun finishActivities() {
        inst.runOnMainSync {
            (Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) })
                .distinct().forEach { it.finish() }
        }
        inst.waitForIdleSync()
    }

    private fun Activity.onBackPressedDispatcherCompat() {
        (this as androidx.activity.ComponentActivity).onBackPressedDispatcher.onBackPressed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("bindingScreenshots") != "true") return
        inst.waitForIdleSync()
        inst.uiAutomation.waitForIdle(250, 5_000)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

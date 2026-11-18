package com.mychuannong.app.core.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.account.ui.AccountGateScreen
import com.mychuannong.app.core.network.campustools.AuthUser
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises native IME edits, not setText, on the real shared login/register composable.
 * Only synthetic strings and callback captures: no account or school network requests. */
class PasswordCursorDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private lateinit var activity: ComponentActivity

    @Before fun setup() {
        check(context.packageName.endsWith(".cursorfix") || context.packageName.endsWith(".uitransition") || context.packageName.endsWith(".validation"))
        PrivacyConsentManager.acceptAppConsent(context)
    }

    private fun screen(content: @Composable () -> Unit, test: () -> Unit) {
        // The ui-test-manifest dependency supplies this empty host only to test builds.
        // It cannot launch school authentication or alter production entry behavior.
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { host -> activity = host; host.setContent { AppSkinTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    private fun main(block: () -> Unit) = inst.runOnMainSync(block)
    private fun field(label: String): EditText {
        fun fields(view: View): List<EditText> = when (view) {
            is EditText -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { fields(view.getChildAt(it)) }
            else -> emptyList()
        }
        var found: EditText? = null
        compose.waitUntil(5_000) {
            main { found = fields(activity.window.decorView).singleOrNull { it.hint?.toString()?.startsWith(label) == true } }
            found != null
        }
        return requireNotNull(found)
    }

    private fun connection(input: EditText): InputConnection {
        var connection: InputConnection? = null
        main { input.requestFocus(); connection = input.onCreateInputConnection(EditorInfo()) }
        return requireNotNull(connection)
    }

    private fun assertEdit(input: EditText, expected: String, start: Int = expected.length, end: Int = start) {
        compose.waitForIdle()
        main {
            assertEquals(expected, input.text.toString())
            assertEquals("selectionStart", start, input.selectionStart)
            assertEquals("selectionEnd", end, input.selectionEnd)
        }
    }

    private fun paste(input: EditText, text: String) {
        main {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val previous = clipboard.primaryClip
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText("synthetic-cursor-test", text))
                assertTrue(input.onTextContextMenuItem(android.R.id.paste))
            } finally {
                if (previous != null) clipboard.setPrimaryClip(previous)
                else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            }
        }
        compose.waitForIdle()
    }

    private fun checkForm(registering: Boolean) {
        var submitted: String? = null
        var authenticated = false
        val user = AuthUser("synthetic-cursor-user", "USER", accountType = "PHONE")
        screen(content = {
            AccountGateScreen(
                onLogin = { _, password -> check(!registering); submitted = password; user },
                onRegister = { _, password, _, confirmation -> check(registering); assertEquals(password, confirmation); submitted = password; user },
                onAuthenticated = { authenticated = true },
            )
        }) {
            if (registering) {
                compose.onNodeWithText("创建账号").performScrollTo().performClick()
                compose.onNodeWithText("创建我的川农账号").assertIsDisplayed()
                val name = field("昵称")
                val nameConnection = connection(name)
                main { nameConnection.commitText("合成用户", 1) }
            }
            compose.onNodeWithText("短信验证码").assertDoesNotExist()
            compose.onNodeWithText("获取验证码").assertDoesNotExist()
            compose.onNodeWithText("忘记密码").assertDoesNotExist()
            main {
                fun fieldCount(view: View): Int = when (view) {
                    is EditText -> 1
                    is ViewGroup -> (0 until view.childCount).sumOf { fieldCount(view.getChildAt(it)) }
                    else -> 0
                }
                assertEquals(if (registering) 4 else 2, fieldCount(activity.window.decorView))
            }
            val phone = field("手机号")
            val phoneConnection = connection(phone)
            main { phoneConnection.commitText("19900000001", 1) }
            compose.waitForIdle()
            val password = field("密码")
            val ime = connection(password)
            "123456".forEachIndexed { index, char ->
                main { ime.commitText(char.toString(), 1) }
                assertEdit(password, "123456".take(index + 1))
            }
            val action = if (registering) "注册并进入" else "登录"
            compose.onNodeWithText(action, substring = false).assertIsNotEnabled()
            main { ime.deleteSurroundingText(1, 0) }
            assertEdit(password, "12345")
            paste(password, "678")
            assertEdit(password, "12345678")
            // Selection replacement and middle-of-text edits remain native, without forcing
            // a manually placed caret to the end on every state update.
            main { password.setSelection(2, 4) }
            paste(password, "AB")
            assertEdit(password, "12AB5678", 4)
            main { ime.commitText("X", 1); ime.commitText("Y", 1) }
            assertEdit(password, "12ABXY5678", 6)
            main { ime.deleteSurroundingText(2, 0) }
            assertEdit(password, "12AB5678", 4)
            main {
                assertTrue(password.transformationMethod is PasswordTransformationMethod)
                assertFalse(password.isSaveEnabled)
                password.selectAll()
            }
            paste(password, "12345678")
            assertEdit(password, "12345678")
            if (registering) {
                val confirm = field("确认密码")
                val confirmIme = connection(confirm)
                main { confirmIme.commitText("12345679", 1) }
                compose.waitForIdle()
                compose.onNodeWithText(action, substring = false).assertIsNotEnabled()
                compose.onNodeWithText("两次输入的密码不一致").assertExists()
                assertNull(submitted)
                main { confirm.selectAll() }
                paste(confirm, "12345678")
            }
            compose.onNodeWithText(action, substring = false).assertIsEnabled().performScrollTo().performClick()
            if (registering) {
                compose.onNodeWithText("请牢记密码").assertIsDisplayed()
                assertNull(submitted)
                compose.onNodeWithText("返回检查").performClick()
                assertNull(submitted)
                compose.onNodeWithText(action, substring = false).performClick()
                compose.onNodeWithText("确认并继续").performClick()
            }
            compose.waitUntil(5_000) { authenticated }
            assertEquals("12345678", submitted)
        }
    }

    @Test fun loginKeepsTypedOrderDeletionPasteAndSubmittedPassword() = checkForm(false)
    @Test fun registrationKeepsTypedOrderDeletionPasteAndSubmittedPassword() = checkForm(true)

    @Test fun maskingAndRecompositionPreserveSelectionAndImeComposition() {
        val value = mutableStateOf("")
        val masked = mutableStateOf(true)
        val hint = mutableStateOf("初始提示")
        screen(content = {
            ImeCompatibleTextField(value.value, { value.value = it }, label = "密码", password = masked.value, placeholder = hint.value)
        }) {
            val input = field("密码")
            val ime = connection(input)
            main { ime.commitText("abc123", 1) }
            assertEdit(input, "abc123")
            main { input.setSelection(1, 4); hint.value = "更新提示" }
            assertEdit(input, "abc123", 1, 4)
            compose.runOnIdle { masked.value = false }
            assertEdit(input, "abc123", 1, 4)
            main { assertFalse(input.transformationMethod is PasswordTransformationMethod) }
            compose.runOnIdle { masked.value = true }
            assertEdit(input, "abc123", 1, 4)
            main { assertTrue(input.transformationMethod is PasswordTransformationMethod); input.setSelection(input.length()) }
            // Text type changes may restart an IME; use its replacement connection.
            val nextIme = connection(input)
            main { nextIme.setComposingText("Z", 1) }
            assertEdit(input, "abc123Z")
            compose.runOnIdle { hint.value = "再次更新提示" }
            main { nextIme.commitText("XY", 1) }
            assertEdit(input, "abc123XY")
        }
    }

    @Test fun upstreamLengthLimitAndClearKeepCaretWithinText() {
        val value = mutableStateOf("")
        screen(content = { ImeCompatibleTextField(value.value, { value.value = it.take(200) }, label = "密码", password = true) }) {
            val input = field("密码")
            val ime = connection(input)
            paste(input, "1".repeat(201))
            assertEdit(input, "1".repeat(200))
            main { ime.commitText("2", 1) }
            assertEdit(input, "1".repeat(200))
            compose.runOnIdle { value.value = "" }
            assertEdit(input, "")
            main { ime.commitText("123456", 1) }
            assertEdit(input, "123456")
        }
    }
}

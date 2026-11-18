package com.mychuannong.app.school

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The school issues the challenge; the student enters it and no verification is automated. */
class UnifiedSchoolAuthenticationActivity : ComposeActivity() {
    private var account by mutableStateOf("")
    private var password by mutableStateOf("")
    private var code by mutableStateOf("")
    private var saved by mutableStateOf(false)
    private var loading by mutableStateOf(false)
    private var submitting by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var challenge by mutableStateOf<AuthChallenge?>(null)
    private var challengeImage by mutableStateOf<android.graphics.Bitmap?>(null)
    private var session: NativeAuthSession? = null
    private var childOpen = false
    private var ending = false
    private var job: Job? = null
    private val openedRevision = SicauNativeAuth.webRevision.value

    private fun current() = !ending && !isFinishing && !isDestroyed &&
        openedRevision == SicauNativeAuth.webRevision.value

    private val vpn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!current()) return@registerForActivityResult
        childOpen = false
        if (result.resultCode == RESULT_OK && openedRevision == SicauNativeAuth.webRevision.value) {
            job = lifecycleScope.launch {
                try {
                    val expected = account.trim().takeIf(String::isNotBlank)
                    val verified = withTimeoutOrNull(15_000) {
                        SicauWebVpn.confirmBrowserOwner(expected, openedRevision)
                    } == true
                    if (verified) openAcademic()
                    else resumeForm("学校登录会话尚未确认，请重试。")
                } catch (e: CancellationException) { throw e }
                catch (e: JiaowuException) { resumeForm(e.reason.userMessage) }
                catch (_: Exception) { resumeForm("学校会话暂时无法恢复，请重试。") }
            }
        } else {
            submitting = false
            error = "校园网络尚未连接，请重试。"
            restoreStoredCredentials()
        }
    }
    private val academic = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!current()) return@registerForActivityResult
        childOpen = false
        submitting = false
        if (result.resultCode == RESULT_OK && openedRevision == SicauNativeAuth.webRevision.value) {
            ending = true
            password = ""; code = ""; setResult(RESULT_OK); finish()
        } else { error = "学校身份尚未核验，请重试。"; restoreStoredCredentials() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childOpen = savedInstanceState?.getBoolean("auth_child_open") == true
        submitting = childOpen
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 26)
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setComposeContent {
            val passwordFocus = remember { FocusRequester() }
            val codeFocus = remember { FocusRequester() }
            val focus = LocalFocusManager.current
            BackHandler { cancelAuthentication() }
            Scaffold(containerColor = Color.White, topBar = {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp)) {
                    TextButton(onClick = ::cancelAuthentication, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                        Text("取消", color = AppTheme.colors.functionalTextColor)
                    }
                    Text(if (saved) "学校验证" else "川农 e 登录", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 528.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        if (submitting) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("正在登录…", color = AppTheme.colors.primaryTextColor)
                        } else {
                            if (!saved) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("学号", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    SchoolAuthField(account, { account = it.take(32) }, "请输入学号", KeyboardType.Ascii,
                                        actions = KeyboardActions(onNext = { passwordFocus.requestFocus() }))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("密码", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    SchoolAuthField(password, { password = it }, "请输入川农 e 密码", KeyboardType.Password, PasswordVisualTransformation(),
                                        modifier = Modifier.focusRequester(passwordFocus), actions = KeyboardActions(onNext = { codeFocus.requestFocus() }))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("验证码", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SchoolAuthField(code, { code = it.take(32) }, "请输入验证码", KeyboardType.Ascii,
                                        modifier = Modifier.weight(1f).focusRequester(codeFocus), imeAction = ImeAction.Go,
                                        actions = KeyboardActions(onGo = { focus.clearFocus(); submit() }))
                                    Box(Modifier.width(120.dp).height(48.dp).clickable(enabled = !loading, onClick = ::loadChallenge)
                                        .semantics { contentDescription = "更换验证码" }, contentAlignment = Alignment.Center) {
                                        val bitmap = challengeImage
                                        when {
                                            bitmap != null -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
                                            loading -> CircularProgressIndicator(Modifier.size(24.dp))
                                            else -> Text("重新加载", color = AppTheme.colors.functionalTextColor)
                                        }
                                    }
                                }
                            }
                            error?.let { Text(it, color = AppTheme.colors.primaryTextColor) }
                            Button(onClick = ::submit, enabled = !loading && challenge != null && account.isNotBlank() && password.isNotBlank() && code.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.functionalTextColor)) {
                                Text(if (saved) "验证并继续" else "登录")
                            }
                            if (saved) TextButton(onClick = { saved = false; password = "" }) { Text("修改登录密码") }
                            if (error != null) TextButton(onClick = ::openOfficialAuthentication) { Text("打开学校认证页面") }
                        }
                    }
                }
            }
        }
        if (!childOpen) restoreStoredCredentials()
    }

    private fun restoreStoredCredentials() {
        if (!current()) return
        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) { UnifiedCredentialVault.read() }
            val bound = withContext(Dispatchers.IO) { SicauJiaowuSessionVault.bindingOwner() }
            if (!current()) return@launch
            if (stored != null && (bound == null || stored.owner == bound)) {
                account = stored.owner; password = stored.password; saved = true
            } else { if (account.isBlank()) account = bound.orEmpty(); saved = false }
            loadChallenge(preserveError = error != null)
        }
    }

    private fun loadChallenge() = loadChallenge(preserveError = false)

    private fun loadChallenge(preserveError: Boolean) {
        if (!current()) return
        job?.cancel(); session?.cancel(); code = ""; challenge = null; challengeImage = null; loading = true
        job = lifecycleScope.launch {
            val pending = NativeAuthSession(); session = pending
            try {
                val next = withTimeoutOrNull(20_000) { pending.challenge() }
                    ?: throw java.io.IOException("Challenge timed out")
                val split = next.image.indexOf(',')
                require(split > 0 && next.image.substring(0, split) in setOf("data:image/png;base64", "data:image/jpeg;base64", "data:image/gif;base64"))
                val bytes = Base64.decode(next.image.substring(split + 1), Base64.DEFAULT)
                require(bytes.isNotEmpty() && bytes.size < 1_000_000)
                val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                if (!current()) throw CancellationException()
                challenge = next; challengeImage = image
                schoolDiagnostic("challenge=ready")
                if (!preserveError) error = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                schoolDiagnostic("challenge=unavailable")
                error = "验证码加载失败，请检查网络后重试。"
            }
            finally { if (session === pending) loading = false }
        }
    }

    private fun submit() {
        if (!current()) return
        val entered = account.trim(); val secret = password; val answer = code.trim()
        val pending = session ?: return
        val currentChallenge = challenge ?: return
        if (submitting || entered.isEmpty() || secret.isEmpty() || answer.isEmpty()) return
        submitting = true; error = null
        job = lifecycleScope.launch {
            try {
                schoolDiagnostic("auth=submit_start")
                val completed = withTimeoutOrNull(70_000) {
                    val bound = withContext(Dispatchers.IO) { SicauJiaowuSessionVault.bindingOwner() }
                    val localOwner = StudentInfoManager.studentId.takeIf { it.isNotBlank() }
                    if (listOfNotNull(bound, localOwner).any { it != entered }) throw IllegalStateException("请先解除当前学校账号绑定。")
                    val response = pending.login(entered, secret, answer, currentChallenge)
                    if (SicauAuthProtocol.confirmedNumber(response, entered) != entered) {
                        val failure = SicauAuthProtocol.safeFailure(response)
                        if (saved && failure.contains("账号信息")) {
                            withContext(Dispatchers.IO) { UnifiedCredentialVault.clear() }
                            saved = false; password = ""
                        }
                        throw IllegalStateException(failure)
                    }
                    if (!current()) throw CancellationException()
                    schoolDiagnostic("auth=verified")
                    SicauNativeAuth.activate(pending, entered, openedRevision)
                    withContext(Dispatchers.IO) { synchronized(SicauNativeAuth) {
                        if (openedRevision != SicauNativeAuth.webRevision.value) throw CancellationException()
                        UnifiedCredentialVault.save(UnifiedCredential(entered, secret))
                    } }
                    password = ""; code = ""; session = null
                    if (SicauWebVpn.connectUnified(entered, openedRevision)) {
                        schoolDiagnostic("auth=vpn_ready")
                        openAcademic()
                    } else {
                        schoolDiagnostic("auth=vpn_unavailable")
                        submitting = false
                        error = "校园网络暂时未能连接，请重试。"
                        restoreStoredCredentials()
                    }
                    true
                }
                if (completed == null) {
                    schoolDiagnostic("auth=timeout")
                    pending.cancel(); submitting = false
                    error = "学校认证超时，请重试。"
                    restoreStoredCredentials()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalStateException) { submitting = false; error = e.message; loadChallenge(preserveError = true) }
            catch (_: Exception) { submitting = false; error = "学校认证暂时无法完成，请检查网络后重试。"; loadChallenge(preserveError = true) }
        }
    }

    private fun resumeForm(message: String) {
        if (!current()) return
        submitting = false
        error = message
        restoreStoredCredentials()
    }

    private fun openAcademic() {
        if (!current() || childOpen) return
        childOpen = true
        academic.launch(Intent(this, UnifiedAcademicEntryActivity::class.java))
    }

    private fun openOfficialAuthentication() {
        if (!current() || childOpen) return
        submitting = true; error = null; childOpen = true
        vpn.launch(Intent(this, SicauWebVpnActivity::class.java))
    }

    private fun cancelAuthentication() {
        if (ending || isFinishing) return
        ending = true
        // Stop the request at the user's cancellation, before the Activity's
        // delayed destruction can race with a successful VPN response.
        job?.cancel(); session?.cancel()
        password = ""; code = ""; challenge = null; challengeImage = null
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("auth_child_open", childOpen)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        ending = true
        job?.cancel(); session?.cancel(); password = ""; code = ""; challenge = null; challengeImage = null
        super.onDestroy()
    }
}

@Composable
private fun SchoolAuthField(value: String, change: (String) -> Unit, placeholder: String, keyboard: KeyboardType,
    transformation: VisualTransformation = VisualTransformation.None, modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next, actions: KeyboardActions = KeyboardActions.Default) {
    BasicTextField(value = value, onValueChange = change, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = actions,
        visualTransformation = transformation, cursorBrush = SolidColor(AppTheme.colors.functionalTextColor),
        textStyle = TextStyle(color = AppTheme.colors.primaryTextColor, fontSize = 17.sp),
        modifier = modifier.fillMaxWidth().background(Color(0xFFF2F2F7), RoundedCornerShape(12.dp)).padding(14.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = AppTheme.colors.primaryTextColor, fontSize = 17.sp); inner() } })
}

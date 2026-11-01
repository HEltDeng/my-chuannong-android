package com.mychuannong.app.core.account.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.account.AccountInputRules
import com.mychuannong.app.core.network.campustools.*
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountLoadingScreen() {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppTheme.colors.functionalTextColor)
    }
}

@Composable
fun AccountGateScreen(
    onLogin: suspend (String, String) -> AuthUser,
    onRegister: suspend (String, String, String, String, String, String) -> AuthUser,
    onAuthenticated: () -> Unit = {},
    onContinueAsGuest: (() -> Unit)? = null,
    initialMode: String = "login",
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var phone by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    // Passwords and verification grants are never saved into Bundle or preferences.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<SmsChallenge?>(null) }
    var resendAt by remember { mutableStateOf(0L) }
    var expiresAt by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showReminder by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf<PhoneAuthCapabilities?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val purpose = if (mode == "verify") "verify_phone" else mode
    val smsAvailable = capabilities?.availablePurposes?.contains(purpose) == true
    val remaining = ((resendAt - clock + 999) / 1000).coerceAtLeast(0)
    val newPassword = mode == "register" || mode == "reset"
    val validProof = challenge != null && expiresAt > clock && AccountInputRules.isValidCode(code)
    val canSubmit = !submitting && AccountInputRules.isValidPhone(phone) && when (mode) {
        "login" -> AccountInputRules.isValidPassword(password)
        "verify" -> validProof
        else -> validProof && AccountInputRules.isConfirmedPassword(password, confirmation) && (mode != "register" || nickname.isNotBlank())
    }
    fun switchMode(next: String) {
        mode = next; password = ""; confirmation = ""; code = ""; challenge = null; error = ""; message = ""
    }
    fun submit() {
        if (!canSubmit) return
        submitting = true; error = ""; message = ""
        scope.launch {
            try {
                when (mode) {
                    "login" -> { onLogin(phone, password); password = ""; onAuthenticated() }
                    "register" -> {
                        onRegister(phone, password, nickname, confirmation, challenge!!.challengeId, code)
                        password = ""; confirmation = ""; code = ""; onAuthenticated()
                    }
                    "reset" -> {
                        val result = CampusToolsSession.resetPassword(phone, password, confirmation, challenge!!.challengeId, code)
                        switchMode("login"); message = "密码已重置，请重新登录。今日剩余找回次数：${result.remainingToday}。"
                    }
                    "verify" -> {
                        CampusToolsSession.verifyPhone(challenge!!.challengeId, code); code = ""; onAuthenticated()
                    }
                }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                error = cause.message ?: "操作失败，请稍后重试"
            } finally { submitting = false }
        }
    }
    LaunchedEffect(refresh) {
        capabilities = try { CampusToolsSession.phoneCapabilities() } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            null
        }
    }
    LaunchedEffect(Unit) { while (true) { clock = SystemClock.elapsedRealtime(); delay(1000) } }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor).statusBarsPadding().navigationBarsPadding()
        .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Image(painterResource(R.drawable.my_chuannong_brand_logo), "我的川农", Modifier.size(64.dp)
            .clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally))
        Text(when(mode) { "register" -> "创建我的川农账号"; "reset" -> "找回密码"; "verify" -> "验证绑定手机号"; else -> "登录我的川农账号" },
            fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = AppTheme.colors.primaryTextColor, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.colors.bgCardColor) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (mode == "register") ImeCompatibleTextField(value = nickname, onValueChange = { if (!submitting) nickname = it.take(30) }, label = "昵称")
                ImeCompatibleTextField(value = phone, onValueChange = {
                    if (!submitting) { phone = it.filter { ch -> ch in '0'..'9' }.take(11); challenge = null; code = "" }
                }, label = "手机号", placeholder = if (mode == "verify") "当前账号的注册手机号" else "11 位手机号", numeric = true)
                if (mode != "login") {
                    if (!smsAvailable) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("短信验证暂未开放", Modifier.weight(1f), color = AppTheme.colors.primaryTextColor)
                        TextButton(enabled = !submitting, onClick = { refresh++ }) { Text("刷新") }
                    }
                    ImeCompatibleTextField(value = code, onValueChange = { if (!submitting) code = it.filter { ch -> ch in '0'..'9' }.take(6) },
                        label = "短信验证码", placeholder = "6 位验证码", numeric = true)
                    OutlinedButton(enabled = !submitting && smsAvailable && AccountInputRules.isValidPhone(phone) && remaining == 0L,
                        onClick = {
                            submitting = true; error = ""; message = ""
                            scope.launch {
                                try {
                                    val result = CampusToolsSession.requestSms(phone, purpose)
                                    challenge = result; code = ""
                                    resendAt = SystemClock.elapsedRealtime() + result.retryAfter * 1000L
                                    expiresAt = SystemClock.elapsedRealtime() + result.expiresIn * 1000L
                                    message = "若手机号符合当前操作条件，验证码将发送至该手机，5 分钟内有效。"
                                } catch (cause: Exception) {
                                    if (cause is kotlinx.coroutines.CancellationException) throw cause
                                    error = cause.message ?: "验证码发送失败"
                                } finally { submitting = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (remaining > 0) "$remaining 秒后重发" else "获取验证码") }
                }
                if (mode != "verify") ImeCompatibleTextField(value = password, onValueChange = { if (!submitting) password = it.take(200) },
                    label = if (mode == "reset") "新密码" else "密码", placeholder = if (newPassword) "至少 8 位" else "请输入密码", password = true)
                if (newPassword) ImeCompatibleTextField(value = confirmation, onValueChange = { if (!submitting) confirmation = it.take(200) }, label = "确认密码", password = true)
                if (newPassword && confirmation.isNotEmpty() && confirmation != password) Text("两次输入的密码不一致", color = AppTheme.colors.errorRedColor, fontSize = 14.sp)
                if (error.isNotBlank()) Text(error, color = AppTheme.colors.errorRedColor, fontSize = 14.sp)
                if (message.isNotBlank()) Text(message, color = AppTheme.colors.successGreenColor, fontSize = 14.sp)
                Button(enabled = canSubmit, onClick = { if (newPassword) showReminder = true else submit() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.functionalTextColor),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    if (submitting) CircularProgressIndicator(Modifier.size(20.dp), color = AppTheme.colors.bgCardColor, strokeWidth = 2.dp)
                    else Text(when(mode) { "register" -> "注册并进入"; "reset" -> "重置密码"; "verify" -> "确认验证"; else -> "登录" })
                }
                if (mode == "login") TextButton(enabled = !submitting, onClick = { switchMode("reset") }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("忘记密码") }
                if (mode != "verify") TextButton(enabled = !submitting, onClick = { switchMode(if(mode == "login") "register" else "login") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if(mode == "login") "创建账号" else "返回登录")
                }
                if (onContinueAsGuest != null) TextButton(enabled = !submitting, onClick = onContinueAsGuest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (initialMode == "login") "暂不登录" else "返回")
                }
                TextButton(onClick = { Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("隐私政策与用户协议") }
            }
        }
    }
    if (showReminder) AlertDialog(onDismissRequest = { showReminder = false }, title = { Text("请牢记密码") },
        text = { Text(AccountInputRules.PASSWORD_REMINDER) },
        confirmButton = { TextButton(onClick = { showReminder = false; submit() }) { Text("确认并继续") } },
        dismissButton = { TextButton(onClick = { showReminder = false }) { Text("返回检查") } })
}

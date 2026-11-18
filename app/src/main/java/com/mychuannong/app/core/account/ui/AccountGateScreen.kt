package com.mychuannong.app.core.account.ui

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
    onRegister: suspend (String, String, String, String) -> AuthUser,
    onAuthenticated: () -> Unit = {},
    onContinueAsGuest: (() -> Unit)? = null,
    initialMode: String = "login",
) {
    var registering by rememberSaveable { mutableStateOf(initialMode == "register") }
    var phone by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    // Passwords never enter saved instance state or preferences.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showReminder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canSubmit = !submitting && AccountInputRules.canSubmit(
        phone, password, nickname, registering, confirmation,
    )
    fun submit() {
        if (!canSubmit) return
        submitting = true
        error = ""
        scope.launch {
            try {
                if (registering) onRegister(phone, password, nickname, confirmation)
                else onLogin(phone, password)
                password = ""
                confirmation = ""
                onAuthenticated()
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                error = cause.message ?: "操作失败，请稍后重试"
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor).statusBarsPadding().navigationBarsPadding()
        .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Image(painterResource(R.drawable.my_chuannong_brand_logo), "我的川农", Modifier.size(64.dp)
            .clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally))
        Text(if (registering) "创建我的川农账号" else "登录我的川农账号",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = AppTheme.colors.primaryTextColor, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.colors.bgCardColor) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (registering) ImeCompatibleTextField(value = nickname, onValueChange = { if (!submitting) nickname = it.take(30) }, label = "昵称")
                ImeCompatibleTextField(value = phone, onValueChange = {
                    if (!submitting) phone = it.filter { ch -> ch in '0'..'9' }.take(11)
                }, label = "手机号", placeholder = "11 位手机号", numeric = true)
                ImeCompatibleTextField(value = password, onValueChange = { if (!submitting) password = it.take(200) },
                    label = "密码", placeholder = if (registering) "至少 8 位" else "请输入密码", password = true)
                if (registering) ImeCompatibleTextField(value = confirmation, onValueChange = { if (!submitting) confirmation = it.take(200) }, label = "确认密码", password = true)
                if (registering && confirmation.isNotEmpty() && confirmation != password) Text("两次输入的密码不一致", color = AppTheme.colors.errorRedColor, fontSize = 14.sp)
                if (error.isNotBlank()) Text(error, color = AppTheme.colors.errorRedColor, fontSize = 14.sp)
                Button(enabled = canSubmit, onClick = { if (registering) showReminder = true else submit() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.functionalTextColor),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    if (submitting) CircularProgressIndicator(Modifier.size(20.dp), color = AppTheme.colors.bgCardColor, strokeWidth = 2.dp)
                    else Text(if (registering) "注册并进入" else "登录")
                }
                TextButton(enabled = !submitting, onClick = {
                    registering = !registering
                    password = ""
                    confirmation = ""
                    error = ""
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (registering) "返回登录" else "创建账号")
                }
                TextButton(enabled = !submitting, onClick = {
                    if (!com.mychuannong.app.core.web.PublicWebsiteNavigator.open(context, "https://mychuannong.cn/contact.html")) {
                        error = "无法打开联系支持页面，请稍后重试"
                    }
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("联系支持") }
                if (onContinueAsGuest != null) TextButton(enabled = !submitting, onClick = onContinueAsGuest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (initialMode == "login") "暂不登录" else "返回")
                }
                TextButton(enabled = !submitting, onClick = { Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("隐私政策与用户协议") }
            }
        }
    }
    if (showReminder) AlertDialog(onDismissRequest = { showReminder = false }, title = { Text("请牢记密码") },
        text = { Text(AccountInputRules.PASSWORD_REMINDER) },
        confirmButton = { TextButton(onClick = { showReminder = false; submit() }) { Text("确认并继续") } },
        dismissButton = { TextButton(onClick = { showReminder = false }) { Text("返回检查") } })
}

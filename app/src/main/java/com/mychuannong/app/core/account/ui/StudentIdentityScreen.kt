package com.mychuannong.app.core.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import kotlinx.coroutines.launch

@Composable
fun StudentIdentityScreen(onBack: () -> Unit) {
    val state by CampusToolsSession.state.collectAsState()
    val user = (state as? AccountSessionState.SignedIn)?.user
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var verifyPhone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (verifyPhone) {
        androidx.activity.compose.BackHandler { verifyPhone = false }
        AccountGateScreen(CampusToolsSession::login, CampusToolsSession::register,
            onAuthenticated = { verifyPhone = false }, onContinueAsGuest = { verifyPhone = false }, initialMode = "verify")
        return
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor)
        .statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        com.mychuannong.app.core.theme.SecondaryPageHeader("学生资料", onBack)
        if (user?.studentId?.isNotEmpty() == true) {
            Text("姓名：${user.realName.orEmpty()}")
            Text("学号：${user.studentId}")
            Text("状态：${if (user.identityStatus == "VERIFIED") "已核验" else "待核验"}")
        } else {
            ImeCompatibleTextField(value = name, onValueChange = { if (!busy) name = it.filter { ch -> ch >= ' ' }.take(40) }, label = "姓名")
            ImeCompatibleTextField(value = studentId, onValueChange = {
                if (!busy) studentId = it.uppercase().filter { ch -> ch in 'A'..'Z' || ch in '0'..'9' }.take(20)
            }, label = "四川农业大学学号")
            if (user?.phoneVerified != true) TextButton(onClick = { verifyPhone = true }) { Text("先验证绑定手机号") }
            Button(onClick = { confirming = true }, enabled = !busy && user?.phoneVerified == true
                && name.trim().length in 2..40 && studentId.matches(Regex("[A-Z0-9]{6,20}")),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.functionalTextColor), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在提交…" else "提交学生资料")
            }
        }
        if (error.isNotEmpty()) Text(error, color = AppTheme.colors.errorRedColor)
        TextButton(onClick = { Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY) }) { Text("资料使用与更正规则") }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("确认姓名和学号") },
        text = { Text("一个学号只能绑定一个手机号账号。提交后进入核验，无法自行换绑；资料填写错误请通过意见反馈联系管理员。") },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("返回检查") } },
        confirmButton = { TextButton(onClick = {
            confirming = false; busy = true; error = ""
            scope.launch {
                try { CampusToolsSession.bindStudentIdentity(studentId, name) }
                catch (cause: Exception) {
                    if (cause is kotlinx.coroutines.CancellationException) throw cause
                    error = cause.message ?: "学生资料提交失败"
                } finally { busy = false }
            }
        }) { Text("确认并提交") } })
}

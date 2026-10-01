package com.mychuannong.app.core.account.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import kotlinx.coroutines.launch

class AccountCenterActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.mychuannong.app.core.account.AccountAccess.isSignedIn) {
            com.mychuannong.app.core.account.AccountAccess.openOrRequestLogin(
                this,
                android.content.Intent(this, AccountCenterActivity::class.java),
            )
            finish()
            return
        }
        setComposeContent {
            AccountCenterScreen(
                onBack = ::finish,
                onSignedOut = { Route.goHomeForcibly(this) },
            )
        }
    }
}

@Composable
private fun AccountCenterScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val state by CampusToolsSession.state.collectAsState()
    val user = (state as? AccountSessionState.SignedIn)?.user
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var nickname by remember(user?.id) { mutableStateOf(user?.nickname.orEmpty()) }
    var message by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var deletePassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = AppTheme.colors.primaryTextColor,
                )
            }
            Text(
                text = "账号与安全",
                color = AppTheme.colors.primaryTextColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (user == null) {
            Text(
                "账号已退出",
                color = AppTheme.colors.secondaryTextColor,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        Surface(
            color = AppTheme.colors.bgCardColor,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Text("我的川农账号", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                AccountRow("手机号", user.phoneMasked ?: "未提供")
                ImeCompatibleTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(30) },
                    label = "昵称",
                )
                Button(
                    enabled = nickname.isNotBlank() && nickname != user.nickname,
                    onClick = {
                        scope.launch {
                            runCatching { CampusToolsSession.updateNickname(nickname) }
                                .onSuccess { message = "昵称已更新" }
                                .onFailure { message = it.message ?: "更新失败" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.functionalTextColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存昵称")
                }
                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { confirmAction = "switch" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text("切换账号")
        }
        TextButton(
            onClick = { confirmAction = "logout" },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("退出登录", color = AppTheme.colors.secondaryTextColor)
        }
        TextButton(
            onClick = { confirmAction = "delete" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text("注销账号", color = AppTheme.colors.errorRedColor)
        }
    }

    when (confirmAction) {
        "logout", "switch" -> AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (confirmAction == "switch") "切换账号" else "退出登录") },
            text = {
                Text(
                    if (confirmAction == "switch") {
                        "退出当前账号后可登录其他我的川农账号。"
                    } else {
                        "确认退出我的川农账号？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        CampusToolsSession.logout()
                        onSignedOut()
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("取消") }
            },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("注销我的川农账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("账号注销后无法恢复。请输入当前账号密码确认。")
                    ImeCompatibleTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it.take(200) },
                        label = "账号密码",
                        password = true,
                    )
                    if (message.isNotBlank()) {
                        Text(message, color = AppTheme.colors.errorRedColor, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.length >= 8,
                    onClick = {
                        scope.launch {
                            runCatching { CampusToolsSession.deleteAccount(deletePassword) }
                                .onSuccess { onSignedOut() }
                                .onFailure { message = it.message ?: "注销失败" }
                        }
                    },
                ) { Text("确认注销", color = AppTheme.colors.errorRedColor) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("取消") }
            },
        )
        else -> Unit
    }
}

@Composable
private fun AccountRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = AppTheme.colors.secondaryTextColor,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.padding(4.dp))
        Text(value, color = AppTheme.colors.primaryTextColor)
    }
}

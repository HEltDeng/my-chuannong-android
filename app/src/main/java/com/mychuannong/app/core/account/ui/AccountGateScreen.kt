package com.mychuannong.app.core.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.account.accountRestrictionMessage
import com.mychuannong.app.R
import com.mychuannong.app.core.network.campustools.AuthUser
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.account.AccountInputRules
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bgPrimaryColor),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppTheme.colors.functionalTextColor)
    }
}

@Composable
fun AccountGateScreen(
    onLogin: suspend (phone: String, password: String) -> AuthUser,
    onRegister: suspend (phone: String, password: String, nickname: String) -> AuthUser,
    onAuthenticated: () -> Unit = {},
    onContinueAsGuest: (() -> Unit)? = null,
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current

    if (!com.mychuannong.app.core.network.ServiceEndpointPolicy.isAccountServiceConfigured()) {
        Column(
            Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("登录我的川农账号",fontSize=28.sp,fontWeight=FontWeight.Bold,color=AppTheme.colors.primaryTextColor)
            Text(com.mychuannong.app.core.network.ServiceEndpointPolicy.ACCOUNT_UNAVAILABLE,
                color=AppTheme.colors.secondaryTextColor)
            Button(onClick={ onContinueAsGuest?.invoke() ?: (context as? android.app.Activity)?.finish() },
                modifier=Modifier.fillMaxWidth()) { Text("返回") }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bgPrimaryColor)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.my_chuannong_brand_logo),
                contentDescription = "我的川农",
                modifier = Modifier
                    .size(72.dp)
                    .dropShadow(
                        shape = RoundedCornerShape(18.dp),
                        shadow = Shadow(radius = 10.dp, spread = 1.dp,
                            color = Color(0x387B9FCB), offset = DpOffset(0.dp, 4.dp)),
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (registerMode) "创建我的川农账号" else "登录我的川农账号",
                color = AppTheme.colors.primaryTextColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                color = AppTheme.colors.bgCardColor,
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(18.dp),
                ) {
                    if (registerMode) {
                        ImeCompatibleTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(30) },
                            label = "昵称",
                            placeholder = "例如：川农同学",
                        )
                    }
                    ImeCompatibleTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                        label = "手机号",
                        placeholder = "请输入 11 位手机号",
                        numeric = true,
                    )
                    ImeCompatibleTextField(
                        value = password,
                        onValueChange = { password = it.take(200) },
                        label = "密码",
                        placeholder = if (registerMode) "至少 8 位" else "请输入密码",
                        password = true,
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            color = AppTheme.colors.errorRedColor,
                            fontSize = 13.sp,
                        )
                    }
                    if (successMessage.isNotBlank()) {
                        Text(
                            text = successMessage,
                            color = AppTheme.colors.successGreenColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Button(
                        enabled = !submitting && AccountInputRules.canSubmit(
                            phone = phone,
                            password = password,
                            nickname = nickname,
                            registering = registerMode,
                        ),
                        onClick = {
                            errorMessage = ""
                            successMessage = ""
                            submitting = true
                            scope.launch {
                                runCatching {
                                    if (registerMode) onRegister(phone, password, nickname)
                                    else onLogin(phone, password)
                                }.onSuccess { user ->
                                    password = ""
                                    if (registerMode) {
                                        successMessage = "注册成功"
                                        delay(650L)
                                    } else {
                                        accountRestrictionMessage(
                                            accountStatus = user.accountStatus,
                                            expiresAtMillis = user.accountStatusExpiresAt,
                                        )?.let { message ->
                                            successMessage = message
                                            delay(1_400L)
                                        }
                                    }
                                    onAuthenticated()
                                }.onFailure {
                                    errorMessage = it.message ?: "操作失败，请检查网络后重试"
                                }
                                submitting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.functionalTextColor,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                color = AppTheme.colors.bgCardColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(if (registerMode) "注册并进入" else "登录")
                        }
                    }
                    TextButton(
                        onClick = {
                            password = ""
                            registerMode = !registerMode
                            errorMessage = ""
                            successMessage = ""
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            if (registerMode) "已有账号？直接登录" else "没有账号？创建账号",
                            color = AppTheme.colors.functionalTextColor,
                        )
                    }
                    if (onContinueAsGuest != null) {
                        TextButton(
                            onClick = onContinueAsGuest,
                            enabled = !submitting,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("暂不登录", color = AppTheme.colors.secondaryTextColor)
                        }
                    }
                    TextButton(
                        onClick = {
                            Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("查看完整隐私说明", color = AppTheme.colors.secondaryTextColor)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

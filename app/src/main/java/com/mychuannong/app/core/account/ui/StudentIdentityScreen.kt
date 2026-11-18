package com.mychuannong.app.core.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.theme.AppTheme

@Composable
fun StudentIdentityScreen(onBack: () -> Unit) {
    val state by CampusToolsSession.state.collectAsState()
    val user = (state as? AccountSessionState.SignedIn)?.user
    val academicRevision by StudentInfoManager.academicRevision.collectAsState()
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val school = remember(academicRevision, resumed) {
        if (StudentInfoManager.hasCompleteBinding) {
            listOf(StudentInfoManager.studentName, StudentInfoManager.studentId, StudentInfoManager.schoolGrade)
        } else null
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bgPrimaryColor)
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        com.mychuannong.app.core.theme.SecondaryPageHeader("学生资料", onBack)
        IdentitySection("学校认证") {
            if (school != null) {
                IdentityLine("姓名", school[0])
                IdentityLine("学号", school[1])
                if (school[2].isNotEmpty()) IdentityLine("年级", school[2])
                IdentityLine("状态", "已认证")
            } else {
                TextButton(onClick = { Route.goBindingUser(context) }) { Text("绑定学号") }
            }
        }
        IdentitySection("平台核验") {
            if (!user?.studentId.isNullOrEmpty()) {
                IdentityLine("姓名", user?.realName.orEmpty())
                IdentityLine("学号", user?.studentId.orEmpty())
                IdentityLine("状态", if (user?.identityStatus == "VERIFIED") "已核验" else "待核验")
            } else {
                IdentityLine("状态", "未核验")
            }
        }
        TextButton(onClick = { Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY) }) { Text("资料使用与更正规则") }
    }
}

@Composable
private fun IdentitySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.colors.bgCardColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
            content()
        }
    }
}

@Composable
private fun IdentityLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.weight(1f), color = AppTheme.colors.primaryTextColor)
        Text(value, color = AppTheme.colors.primaryTextColor)
    }
}

package com.mychuannong.app.profileSettings.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.update.AppUpdateCoordinator
import com.mychuannong.app.core.update.AppUpdatePolicyController
import com.mychuannong.app.core.update.AppUpdatePolicyState
import com.mychuannong.app.core.update.AppUpdatePrompt
import com.mychuannong.app.core.update.VersionCheckResult
import com.mychuannong.app.core.web.PublicWebsiteNavigator
import kotlinx.coroutines.launch

class AboutActivity : com.mychuannong.app.base.ComposeActivity() {
    companion object {
        const val EXTRA_AUTO_CHECK_UPDATE = "extra_auto_check_update"
    }

    override fun onResume() {
        super.onResume()
        AppUpdateCoordinator.resumePendingInstall(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoCheck = intent?.getBooleanExtra(EXTRA_AUTO_CHECK_UPDATE, false) ?: false
        setContent {
            AppSkinTheme {
                AboutScreen(activity = this, autoCheckUpdate = autoCheck)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun AboutScreen(activity: Activity? = null, autoCheckUpdate: Boolean = false) {
    val scope = rememberCoroutineScope()
    var checkMessage by remember { mutableStateOf("") }
    var availableUpdate by remember { mutableStateOf<VersionCheckResult.Available?>(null) }

    LaunchedEffect(autoCheckUpdate) {
        if (autoCheckUpdate) {
            checkMessage = "正在检查…"
            when (val state = AppUpdatePolicyController.refresh(forceRefresh = true)) {
                is AppUpdatePolicyState.NO_UPDATE -> checkMessage = "当前已是最新版本"
                is AppUpdatePolicyState.ERROR -> checkMessage = "暂时无法检查更新"
                is AppUpdatePolicyState.OPTIONAL_UPDATE -> {
                    checkMessage = ""
                    availableUpdate = state.update
                }
                is AppUpdatePolicyState.FORCE_UPDATE -> {
                    checkMessage = ""
                    availableUpdate = state.update
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                com.mychuannong.app.core.theme.SecondaryPageHeader("关于我的川农", { activity?.finish() })
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppTheme.colors.overviewPageBackgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Text(
                text = "我的川农",
                color = AppTheme.colors.primaryTextColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "面向四川农业大学同学的轻量校园生活工具，集中展示常用校园信息、课程服务与实用入口。",
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
            )
            Surface(
                color = AppTheme.colors.bgCardColor,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "当前版本",
                        color = AppTheme.colors.secondaryTextColor,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = AppTheme.colors.primaryTextColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (checkMessage.isNotBlank()) {
                        Text(
                            text = checkMessage,
                            color = AppTheme.colors.secondaryTextColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            Button(
                onClick = {
                    activity?.let {
                        PublicWebsiteNavigator.openHomeInBrowser(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                Text("访问我的川农官网")
            }
            Text(
                text = "产品介绍、最新消息和详细说明请前往官网查看。",
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    availableUpdate?.let { update ->
        AppUpdatePrompt(
            update = update,
            context = activity,
            onLater = { availableUpdate = null },
            onRefreshPolicy = {
                scope.launch {
                    val state = AppUpdatePolicyController.refresh(forceRefresh = true)
                    availableUpdate = when (state) {
                        is AppUpdatePolicyState.OPTIONAL_UPDATE -> state.update
                        is AppUpdatePolicyState.FORCE_UPDATE -> state.update
                        else -> null
                    }
                }
            },
            onPrimaryAction = {
                activity?.let { AppUpdateCoordinator.begin(it, update.version) }
            },
        )
    }
}

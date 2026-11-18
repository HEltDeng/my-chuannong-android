package com.mychuannong.app.profileSettings.ui.compose

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.network.campustools.*
import com.mychuannong.app.core.notifications.AnnouncementNotificationCoordinator
import com.mychuannong.app.core.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationFollowSettings(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by CampusToolsSession.state.collectAsState()
    val accountId = (accountState as? AccountSessionState.SignedIn)?.user?.id
    var follows by remember(accountId) { mutableStateOf<List<NotificationFollow>>(emptyList()) }
    var loading by remember(accountId) { mutableStateOf(false) }
    var busy by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(accountId, revision) {
        if (accountId == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            val result = authenticatedCall { it.notificationFollows() }
            if (result.accountId == accountId &&
                (CampusToolsSession.state.value as? AccountSessionState.SignedIn)?.user?.id == accountId
            ) follows = result.items
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            error = "暂时无法读取关注设置，请重试"
        } finally {
            loading = false
        }
    }

    Column(modifier.fillMaxSize().background(AppTheme.colors.overviewPageBackgroundColor)
        .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
        SecondaryPageHeader("通知设置", onBack)
        SecondaryCard {
            SecondaryRow("系统通知权限", "管理", onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(intent) }.onFailure {
                    error = "暂时无法打开系统设置"
                }
            })
        }
        Spacer(Modifier.height(16.dp))
        SecondaryCard {
            when {
                accountId == null -> {
                    TextButton(onClick = { Route.goAccountCenter(context) }) { Text("登录账号") }
                }
                loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                else -> follows.forEach { item ->
                    SecondaryRow(item.title, control = {
                        Switch(checked = item.following, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "关注${item.title}" },
                            colors = SwitchDefaults.colors(checkedTrackColor = AppTheme.colors.commonColor),
                            onCheckedChange = { following ->
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        val result = AnnouncementNotificationCoordinator.setFollowing(
                                            context, accountId, item.module, following,
                                        )
                                        follows = follows.map {
                                            if (it.module == item.module) it.copy(
                                                following = result.following, followedAt = result.followedAt,
                                            ) else it
                                        }
                                        if (result.following && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } catch (cause: CancellationException) {
                                        throw cause
                                    } catch (_: Exception) {
                                        error = "设置未保存，请检查网络或登录状态后重试"
                                    } finally {
                                        busy = false
                                    }
                                }
                            })
                    })
                }
            }
            error?.let {
                Text(it, color = AppTheme.colors.secondaryTextColor, modifier = Modifier.padding(top = 12.dp))
                TextButton(enabled = !busy && !loading, onClick = { revision++ }) { Text("重新读取") }
            }
        }
        Spacer(Modifier.height(112.dp))
    }
}

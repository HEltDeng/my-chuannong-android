package com.mychuannong.app.profileSettings.ui.compose

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.common.data.local.mmkv.UserInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.update.AppUpdateCoordinator
import com.mychuannong.app.core.update.AppUpdatePolicyController
import com.mychuannong.app.core.update.AppUpdatePolicyState
import com.mychuannong.app.core.update.AppUpdatePrompt
import com.mychuannong.app.core.update.VersionCheckResult
import com.mychuannong.app.core.web.PublicWebsiteNavigator
import com.mychuannong.app.profileSettings.ui.model.SettingItemUiModel
import kotlinx.coroutines.launch

private enum class ProfilePage { MAIN, PRIVACY, ACCOUNT_DATA, NOTIFICATIONS }

private data class ProfileBindingSnapshot(
    val studentId: String,
    val isBound: Boolean,
    val greeting: String,
)

private fun readProfileBindingSnapshot(): ProfileBindingSnapshot {
    val isBound = StudentInfoManager.hasCompleteBinding
    return ProfileBindingSnapshot(
        studentId = StudentInfoManager.studentId.takeIf { isBound }.orEmpty(),
        isBound = isBound,
        greeting = StudentInfoManager.greeting,
    )
}

@Composable
fun ProfileSettingsRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(ProfilePage.MAIN) }
    var bindingSnapshot by remember { mutableStateOf(readProfileBindingSnapshot()) }
    val learningRevision by com.mychuannong.app.school.SchoolSyncEngine.revision.collectAsState()
    androidx.compose.runtime.LaunchedEffect(learningRevision) { bindingSnapshot = readProfileBindingSnapshot() }
    var showCacheDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<VersionCheckResult?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val accountState by CampusToolsSession.state.collectAsState()
    val accountUser = (accountState as? AccountSessionState.SignedIn)?.user
    val studentIdLabel = remember(bindingSnapshot.studentId) {
        maskedStudentId(bindingSnapshot.studentId)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bindingSnapshot = readProfileBindingSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = page != ProfilePage.MAIN) { page = ProfilePage.MAIN }

    when (page) {
        ProfilePage.MAIN -> ProfileSettingsScreen(
            modifier = modifier,
            mainItems = createMainSettingItems(checkingUpdate).map { if(it is SettingItemUiModel.Option && it.id == "school-account" && bindingSnapshot.isBound) it.copy(title = "已绑定学号") else it },
            secondaryItems = createSecondarySettingItems(),
            studentIdLabel = studentIdLabel,
            displayName = bindingSnapshot.greeting,
            accountLabel = accountUser?.phoneMasked ?: "我的川农账号",
            avatarData = UserInfoManager.userAvatar.takeIf { it.isNotBlank() },
            isBound = bindingSnapshot.isBound,
            onWebsiteClick = {
                if (!PublicWebsiteNavigator.openHomeInBrowser(context)) {
                    updateMessage = "暂时无法打开官网"
                }
            },
            onItemClick = { item ->
                when (item.id) {
                    "platform-account" -> Route.goAccountCenter(context)
                    "school-account" -> Route.goBindingUser(context)
                    "webvpn" -> context.startActivity(
                        Intent(context, com.mychuannong.app.school.CampusNetworkActivity::class.java),
                    )
                    "reminders" -> Route.goCourseReminders(context)
                    "widgets" -> context.startActivity(Intent(context, com.mychuannong.app.feature.schedule.reminder.WidgetSetupActivity::class.java))
                    "notifications" -> page = ProfilePage.NOTIFICATIONS
                    "update" -> if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            when (val state = AppUpdatePolicyController.refresh(forceRefresh = true)) {
                                is AppUpdatePolicyState.NO_UPDATE -> {
                                    updateMessage = "当前已是最新版本"
                                    updateResult = null
                                }
                                is AppUpdatePolicyState.ERROR -> {
                                    updateMessage = "暂时无法检查更新，请稍后再试"
                                    updateResult = null
                                }
                                is AppUpdatePolicyState.OPTIONAL_UPDATE -> {
                                    updateResult = state.update
                                }
                                is AppUpdatePolicyState.FORCE_UPDATE -> {
                                    updateResult = state.update
                                }
                                else -> Unit
                            }
                            checkingUpdate = false
                        }
                    }
                    "privacy" -> page = ProfilePage.PRIVACY
                    "account-data" -> page = ProfilePage.ACCOUNT_DATA
                }
            },
        )

        ProfilePage.PRIVACY -> ProfileSubPage(
            modifier = modifier,
            title = "隐私与合规",
            items = createPrivacyItems(),
            onBack = { page = ProfilePage.MAIN },
            onItemClick = { item ->
                when (item.id) {
                    "about" -> Route.goAbout(context)
                    "privacy-policy" -> Route.goReleaseInformation(context, ReleaseDocumentType.PRIVACY)
                    "terms" -> if (!PublicWebsiteNavigator.open(
                            context,
                            PublicWebsiteNavigator.TERMS_URL,
                        )) updateMessage = "暂时无法打开用户协议"
                    "third-party" -> Route.goReleaseInformation(
                        context,
                        ReleaseDocumentType.THIRD_PARTY,
                    )
                    "personal-information" -> Route.goReleaseInformation(
                        context,
                        ReleaseDocumentType.PERSONAL_INFORMATION,
                    )
                }
            },
        )

        ProfilePage.NOTIFICATIONS -> ProfileSubPage(
            modifier = modifier, title = "通知设置",
            items = listOf(
                SettingItemUiModel.Option("app-notifications", "应用通知", Icons.Outlined.NotificationsActive),
                SettingItemUiModel.Option("announcements", "服务公告通知", Icons.Outlined.Info),
            ),
            onBack = { page = ProfilePage.MAIN },
            onItemClick = { item ->
                val target = if (item.id == "announcements" && android.os.Build.VERSION.SDK_INT >= 26)
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, "announcement_updates_v1")
                else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(target) }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
                        .onFailure { android.widget.Toast.makeText(context, "暂时无法打开系统设置", android.widget.Toast.LENGTH_SHORT).show() }
                }
            },
        )

        ProfilePage.ACCOUNT_DATA -> ProfileSubPage(
            modifier = modifier,
            title = "账号与数据管理",
            items = createAccountDataItems(),
            onBack = { page = ProfilePage.MAIN },
            onItemClick = { item ->
                when (item.id) {
                    "delete-account" -> Route.goAccountCenter(context)
                    "clear-cache" -> showCacheDialog = true
                }
            },
        )
    }

    if (showCacheDialog) {
        ConfirmDialog(
            title = "清除缓存",
            content = "确定清除实用工具产生的本地缓存吗？账号登录状态不会受到影响。",
            onDismiss = { showCacheDialog = false },
            onConfirm = {
                PlanetApplication.clearContentCache()
                showCacheDialog = false
            },
        )
    }

    (updateResult as? VersionCheckResult.Available)?.let { update ->
        AppUpdatePrompt(
            update = update,
            context = context,
            onLater = { updateResult = null },
            onRefreshPolicy = {
                scope.launch {
                    updateResult = when (
                        val state = AppUpdatePolicyController.refresh(forceRefresh = true)
                    ) {
                        is AppUpdatePolicyState.OPTIONAL_UPDATE -> state.update
                        is AppUpdatePolicyState.FORCE_UPDATE -> state.update
                        else -> null
                    }
                }
            },
            onPrimaryAction = {
                AppUpdateCoordinator.begin(context, update.version)
            },
        )
    }

    updateMessage?.let { message ->
        InformationDialog(message = message, onDismiss = { updateMessage = null })
    }
}

@Composable
private fun ProfileSettingsScreen(
    mainItems: List<SettingItemUiModel>,
    secondaryItems: List<SettingItemUiModel>,
    studentIdLabel: String,
    avatarData: Any?,
    isBound: Boolean,
    onWebsiteClick: () -> Unit,
    onItemClick: (SettingItemUiModel.Option) -> Unit,
    displayName: String = "川农同学",
    accountLabel: String = "我的川农账号",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.overviewPageBackgroundColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "我的",
            color = AppTheme.colors.primaryTextColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
        )
        Text(
            text = "账号与应用设置",
            color = AppTheme.colors.secondaryTextColor,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        ProfileHeader(
            studentIdLabel = studentIdLabel,
            displayName = displayName,
            accountLabel = accountLabel,
            avatarData = avatarData,
            isBound = isBound,
        )
        Spacer(modifier = Modifier.height(14.dp))
        WebsiteEntryCard(onClick = onWebsiteClick)
        Spacer(modifier = Modifier.height(14.dp))
        SettingsCard(items = mainItems, onItemClick = onItemClick)
        Spacer(modifier = Modifier.height(14.dp))
        SettingsCard(items = secondaryItems, onItemClick = onItemClick)
        Spacer(modifier = Modifier.height(112.dp))
    }
}

@Composable
private fun ProfileSubPage(
    title: String,
    items: List<SettingItemUiModel>,
    onBack: () -> Unit,
    onItemClick: (SettingItemUiModel.Option) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.overviewPageBackgroundColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        com.mychuannong.app.core.theme.SecondaryPageHeader(title, onBack)
        SettingsCard(items = items, onItemClick = onItemClick)
        Spacer(modifier = Modifier.height(112.dp))
    }
}

@Composable
private fun ProfileHeader(
    studentIdLabel: String,
    displayName: String,
    accountLabel: String,
    avatarData: Any?,
    isBound: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.bgTopBarColor,
        border = BorderStroke(1.dp, AppTheme.colors.outlineLowContrastColor),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarData)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .fallback(R.drawable.ic_default_avatar)
                    .build(),
                contentDescription = "用户头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = displayName,
                    color = AppTheme.colors.primaryTextColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = accountLabel,
                    color = AppTheme.colors.greyTextColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = studentIdLabel,
                    color = AppTheme.colors.greyTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = AppTheme.colors.bgButtonLowlightColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        text = if (isBound) "已绑定学号" else "未绑定学号",
                        color = AppTheme.colors.functionalTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsiteEntryCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.bgButtonColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = AppTheme.colors.textButtonColor,
                modifier = Modifier.size(26.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = "我的川农官网（最新消息）",
                    color = AppTheme.colors.textButtonColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "更新公告、功能介绍、使用说明与联系反馈",
                    color = AppTheme.colors.textButtonColor.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textButtonColor,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    items: List<SettingItemUiModel>,
    onItemClick: (SettingItemUiModel.Option) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.bgCardColor,
        border = BorderStroke(1.dp, AppTheme.colors.outlineLowContrastColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            items.forEach { item ->
                when (item) {
                    is SettingItemUiModel.Header -> SettingHeaderItem(item)
                    is SettingItemUiModel.Option -> SettingOptionItem(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingHeaderItem(item: SettingItemUiModel.Header) {
    Text(
        text = item.title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppTheme.colors.greyTextColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingOptionItem(
    item: SettingItemUiModel.Option,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        when {
            item.iconRes != null -> {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = AppTheme.colors.bgButtonLowlightColor,
                ) {
                    if (item.preserveOriginalColors) {
                        Image(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = AppTheme.colors.functionalTextColor,
                            modifier = Modifier.padding(
                                if (item.id == "platform-account") 4.dp else 8.dp,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
            item.icon != null -> {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = AppTheme.colors.bgButtonLowlightColor,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = AppTheme.colors.functionalTextColor,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                color = AppTheme.colors.primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AppTheme.colors.greyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (item.showProgress) {
            CircularProgressIndicator(
                color = AppTheme.colors.functionalTextColor,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        }
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppTheme.colors.iconSecondaryColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        containerColor = AppTheme.colors.bgCardColor,
        titleContentColor = AppTheme.colors.primaryTextColor,
        textContentColor = AppTheme.colors.greyTextColor,
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text = content, fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = AppTheme.colors.functionalTextColor, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppTheme.colors.greyTextColor, fontSize = 16.sp)
            }
        },
    )
}

@Composable
private fun InformationDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = AppTheme.colors.bgCardColor,
        onDismissRequest = onDismiss,
        title = { Text("检查更新", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { androidx.compose.material3.Button(
            onClick = onDismiss, shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.bgButtonLowlightColor,
                contentColor = AppTheme.colors.functionalTextColor),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) { Text("知道了") } },
    )
}

internal fun createMainSettingItems(checkingUpdate: Boolean): List<SettingItemUiModel> = listOf(
    SettingItemUiModel.Header("主要设置"),
    SettingItemUiModel.Option(
        id = "platform-account",
        title = "我的川农账号",
        iconRes = R.drawable.my_chuannong_brand_mark,
    ),
    SettingItemUiModel.Option(
        id = "school-account",
        title = "绑定学号",
        iconRes = R.drawable.ic_student_id_outline,
    ),
    SettingItemUiModel.Option(
        id = "webvpn",
        title = "校园网络环境绑定",
        icon = Icons.Outlined.Language,
    ),
    SettingItemUiModel.Option(
        id = "reminders",
        title = "上课提醒",
        iconRes = R.drawable.notification2,
    ),
    SettingItemUiModel.Option(
        id = "widgets",
        title = "桌面小组件",
        iconRes = R.drawable.ic_widget_grid,
    ),
    SettingItemUiModel.Option(
        id = "notifications",
        title = "通知设置",
        icon = Icons.Outlined.NotificationsActive,
    ),
    SettingItemUiModel.Option(
        id = "update",
        title = "检查更新",
        icon = Icons.Outlined.SystemUpdate,
        showProgress = checkingUpdate,
    ),
)

private fun createSecondarySettingItems(): List<SettingItemUiModel> = listOf(
    SettingItemUiModel.Option("privacy", "隐私与合规", Icons.Outlined.Security),
    SettingItemUiModel.Option("account-data", "账号与数据管理", Icons.Outlined.Storage),
)

private fun createPrivacyItems(): List<SettingItemUiModel> = listOf(
    SettingItemUiModel.Option("about", "关于我的川农", Icons.Outlined.Info),
    SettingItemUiModel.Option("privacy-policy", "隐私政策", Icons.Outlined.Security),
    SettingItemUiModel.Option("terms", "用户协议", Icons.Outlined.Info),
    SettingItemUiModel.Option("third-party", "第三方 SDK / 服务清单", Icons.Outlined.Info),
    SettingItemUiModel.Option("personal-information", "个人信息清单", Icons.Outlined.Info),
)

private fun createAccountDataItems(): List<SettingItemUiModel> = listOf(
    SettingItemUiModel.Option(
        "delete-account",
        "注销账号",
        Icons.Outlined.AccountCircle,
    ),
    SettingItemUiModel.Option(
        "clear-cache",
        "清除缓存",
        Icons.Outlined.DeleteOutline,
    ),
)

private fun maskedStudentId(studentId: String): String {
    if (studentId.isBlank()) return "尚未绑定学号"
    if (studentId.length <= 4) return "学号 ${"•".repeat(studentId.length)}"
    val visiblePrefixLength = if (studentId.length >= 8) 4 else 2
    val visibleSuffixLength = 2
    val hiddenLength = (studentId.length - visiblePrefixLength - visibleSuffixLength)
        .coerceAtLeast(2)
    return buildString {
        append("学号 ")
        append(studentId.take(visiblePrefixLength))
        append("•".repeat(hiddenLength))
        append(studentId.takeLast(visibleSuffixLength))
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileSettingsRoutePreview() {
    AppSkinTheme {
        ProfileSettingsScreen(
            mainItems = createMainSettingItems(checkingUpdate = false),
            secondaryItems = createSecondarySettingItems(),
            studentIdLabel = "尚未绑定学号",
            avatarData = null,
            isBound = false,
            onWebsiteClick = {},
            onItemClick = {},
        )
    }
}

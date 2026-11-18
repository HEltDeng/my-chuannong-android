package com.mychuannong.app.core

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.common.api.DrawerController
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.common.cache.CommonInfo
import com.mychuannong.app.common.pool.TabAnimationPool
import com.mychuannong.app.common.redux.action.UserAction
import com.mychuannong.app.common.redux.store.UserStore
import com.mychuannong.app.core.main.navigation.MainTabNavigator
import com.mychuannong.app.core.main.ui.MainScreen
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore
import com.mychuannong.app.core.notifications.AnnouncementNotificationCoordinator
import com.mychuannong.app.core.notifications.AnnouncementPopupDialog
import com.mychuannong.app.core.notifications.NotificationPermissionPreferences
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import com.mychuannong.app.core.privacy.PrivacyConsentManager.AppConsentState
import com.mychuannong.app.core.privacy.PrivacyConsentScreen
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.feature.lostfound.data.LostFoundRepository
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.update.AppUpdateCooldown
import com.mychuannong.app.core.update.AppUpdateCoordinator
import com.mychuannong.app.core.update.AppUpdatePolicyController
import com.mychuannong.app.core.update.AppUpdatePolicyState
import com.mychuannong.app.core.update.AppUpdatePrompt
import com.mychuannong.app.core.update.VersionCheckResult
import com.mychuannong.app.feature.common.ui.AnnouncementDetailActivity
import com.mychuannong.app.utils.event.AppEventBus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DrawerController {
    private val store by lazy { UserStore() }
    private val mainTabNavigator by lazy(LazyThreadSafetyMode.NONE) { MainTabNavigator() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("main_tab")) mainTabNavigator.select(intent.getIntExtra("main_tab", 0))
    }

    override fun onResume() {
        super.onResume()
        if (!com.mychuannong.app.core.security.SchoolAccessGate.enforce(this)) return
        AppUpdateCoordinator.resumePendingInstall(this)
        if (!PrivacyConsentManager.hasAppConsent(this)) return
        store.dispatch(UserAction.initilaize())  //初始化用户信息，对游客模式无影响
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { RemoteConfigurationStore.refreshIfStale(30_000L) }
            // Only the authenticated, opt-in feed can create notification effects.
            runCatching { AnnouncementNotificationCoordinator.refresh(this@MainActivity) }
        }
        lifecycleScope.launch {
            AppUpdatePolicyController.refresh(forceRefresh = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setCustomDensity(this, application, 412)
        super.onCreate(savedInstanceState)
        if (!com.mychuannong.app.core.security.SchoolAccessGate.enforce(this)) return
        if (intent.hasExtra("main_tab")) mainTabNavigator.select(intent.getIntExtra("main_tab", 0))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppEventBus.selectEvent.collect { event ->
                    mainTabNavigator.select(event.eventType)
                }
            }
        }
//        if (PlanetApplication.Companion.accessToken.isNullOrEmpty() && !PlanetApplication.Companion.is_tourist) {
//            Route.goLogin(this@MainActivity)
//            finish()
//            return
//       }
        ////  Route.goHome(this@MainActivity)
        CommonInfo.startTime = System.currentTimeMillis()
        enableEdgeToEdge()
        val start = System.currentTimeMillis()
        PlanetApplication.startTime = System.currentTimeMillis()
        setContent {
            AppSkinTheme {
                var privacyState by remember {
                    mutableStateOf(PrivacyConsentManager.appConsentState(this@MainActivity))
                }
                val announcementPopup by AnnouncementNotificationCoordinator.popup.collectAsState()
                when (privacyState) {
                    AppConsentState.UNDECIDED, AppConsentState.DECLINED -> PrivacyConsentScreen(
                        onAccept = {
                            PrivacyConsentManager.acceptAppConsent(this@MainActivity)
                            privacyState = AppConsentState.ACCEPTED
                            runConsentedLifecycleWork()
                        },
                        onDecline = {
                            PrivacyConsentManager.declineAppConsent(this@MainActivity)
                            finishAffinity()
                        },
                        onOpenPrivacy = {
                            Route.goReleaseInformation(
                                this@MainActivity,
                                ReleaseDocumentType.PRIVACY,
                            )
                        },
                        onOpenAgreement = {
                            Route.goReleaseInformation(
                                this@MainActivity,
                                ReleaseDocumentType.AGREEMENT,
                            )
                        },
                    )
                    AppConsentState.ACCEPTED -> {
                        val accountState by CampusToolsSession.state.collectAsState()
                        val updatePolicyState by AppUpdatePolicyController.state.collectAsState()
                        var suppressedOptionalVersion by remember { mutableStateOf<Int?>(null) }
                        LaunchedEffect(Unit) {
                            CampusToolsSession.restore()
                            if (AppUpdatePolicyController.state.value is AppUpdatePolicyState.IDLE) {
                                AppUpdatePolicyController.refresh(forceRefresh = true)
                            }
                        }
                        LaunchedEffect(accountState) {
                            val signedIn = accountState as? AccountSessionState.SignedIn
                            if (signedIn != null) {
                                LostFoundRepository.activateAccount(signedIn.user.id)
                            } else if (accountState is AccountSessionState.SignedOut) {
                                LostFoundRepository.activateGuest()
                            }
                        }
                        val forceUpdate = when (val state = updatePolicyState) {
                            is AppUpdatePolicyState.FORCE_UPDATE -> state.update
                            is AppUpdatePolicyState.CHECKING ->
                                (state.previous as? VersionCheckResult.Available)
                                    ?.takeIf { it.mandatory }
                            else -> null
                        }
                        val optionalUpdate = (updatePolicyState as? AppUpdatePolicyState.OPTIONAL_UPDATE)
                            ?.update
                            ?.takeIf {
                                suppressedOptionalVersion != it.version.latest_version_code &&
                                AppUpdateCooldown.isPromptAllowed(
                                    this@MainActivity,
                                    it.version.latest_version_code,
                                )
                        }
                        when {
                            forceUpdate != null -> {
                                AppUpdatePrompt(
                                    update = forceUpdate,
                                    context = this@MainActivity,
                                    onLater = {},
                                    onRefreshPolicy = {
                                        lifecycleScope.launch {
                                            AppUpdatePolicyController.refresh(forceRefresh = true)
                                        }
                                    },
                                    onPrimaryAction = {
                                        AppUpdateCoordinator.begin(
                                            this@MainActivity,
                                            forceUpdate.version,
                                        )
                                    },
                                )
                            }

                            else -> {
                                Surface(color = AppTheme.colors.bgPrimaryColor) {
                                    Column {
                                        Box(Modifier.weight(1f)) { MainScreen(navigator = mainTabNavigator) }
                                    }
                                }
                                optionalUpdate?.let { update ->
                                    AppUpdatePrompt(
                                        update = update,
                                        context = this@MainActivity,
                                        onLater = {
                                            AppUpdateCooldown.recordDismissed(
                                                this@MainActivity,
                                                update.version.latest_version_code,
                                            )
                                            suppressedOptionalVersion =
                                                update.version.latest_version_code
                                        },
                                        onRefreshPolicy = {
                                            lifecycleScope.launch {
                                                AppUpdatePolicyController.refresh(forceRefresh = true)
                                            }
                                        },
                                        onPrimaryAction = {
                                            AppUpdateCoordinator.begin(this@MainActivity, update.version)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                announcementPopup?.let { popup ->
                    AnnouncementPopupDialog(
                        announcement = popup,
                        onDismiss = {
                            AnnouncementNotificationCoordinator.dismissPopup(popup.id)
                        },
                        onOpenDetail = {
                            AnnouncementNotificationCoordinator.dismissPopup(popup.id)
                            startActivity(
                                AnnouncementDetailActivity.intent(
                                    context = this@MainActivity,
                                    title = popup.title,
                                    content = popup.content,
                                    publishedAt = popup.publishedAt,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (PrivacyConsentManager.hasAppConsent(this)) notifyLocalScheduleComponents()
        Log.d("MainActivity", "用时 ${System.currentTimeMillis() - start}")
        // 版本检查与自动更新共用 AppUpdateCoordinator；安装始终交给系统 PackageInstaller 确认。
    }

    override fun onDestroy() {
        super.onDestroy()
        TabAnimationPool.clear()
    }

    override fun onStart() {
        super.onStart()
        if (!com.mychuannong.app.core.security.SchoolAccessGate.isAllowed) return
        if (!PrivacyConsentManager.hasAppConsent(this)) return
        // 账号系统已取消，不再拉取自研后端的用户资料。
        // 但从老版本升级上来时，MMKV 中原本由自研后端写入的头像/昵称已被
        // migrateLegacyCacheIfNeeded 清除，这里用已绑定的学号/密码做一次静默 SSO
        // 重新登录 + getLoginUser，把 MOOC(网络教学平台) 的用户名/头像写回 UserInfoManager。
        // 若未绑定、或 UserInfoManager.account 已有值，action 内部会直接跳过，无网络开销。
        store.dispatch(UserAction.RefreshMoocProfileSilently)
    }

    private fun runConsentedLifecycleWork() {
        store.dispatch(UserAction.initilaize())
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { RemoteConfigurationStore.refreshIfStale(30_000L) }
            // Only the authenticated, opt-in feed can create notification effects.
            runCatching { AnnouncementNotificationCoordinator.refresh(this@MainActivity) }
        }
        store.dispatch(UserAction.RefreshMoocProfileSilently)
        notifyLocalScheduleComponents()
    }

    private fun notifyLocalScheduleComponents() {
        sendBroadcast(Intent(this, com.mychuannong.app.ScheduleSystemReceiver::class.java).apply {
            action = com.mychuannong.app.WidgetUpdateCoordinator.ACTION_REFRESH_SCHEDULE
        })
    }

    override fun openDrawer() {
    }

    private fun setCustomDensity(activity: Activity, application: Application, designWidthDp: Int) {
        val appDisplayMetrics = application.resources.displayMetrics
        val targetDensity = appDisplayMetrics.widthPixels / designWidthDp.toFloat()
        val targetDensityDpi = (targetDensity * 160).toInt()
        var nonCompatScaleDensity = appDisplayMetrics.scaledDensity

        application.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (newConfig.fontScale > 0) {
                    nonCompatScaleDensity = application.resources.displayMetrics.scaledDensity
                }
            }

            override fun onLowMemory() = Unit
        })

        val targetScaleDensity =
            targetDensity * (nonCompatScaleDensity / appDisplayMetrics.density)

        appDisplayMetrics.density = targetDensity
        appDisplayMetrics.densityDpi = targetDensityDpi
        appDisplayMetrics.scaledDensity = targetScaleDensity

        activity.resources.displayMetrics.density = targetDensity
        activity.resources.displayMetrics.densityDpi = targetDensityDpi
        activity.resources.displayMetrics.scaledDensity = targetScaleDensity
    }
}

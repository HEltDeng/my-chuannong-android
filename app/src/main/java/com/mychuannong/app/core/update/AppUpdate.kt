package com.mychuannong.app.core.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.network.campustools.CampusToolsApiClient
import com.mychuannong.app.core.network.campustools.RemoteAppVersion
import com.mychuannong.app.core.network.ServiceEndpointPolicy
import com.mychuannong.app.core.theme.AppTheme
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface VersionCheckResult {
    data object Latest : VersionCheckResult
    data class Available(
        val version: RemoteAppVersion,
        val mandatory: Boolean,
        val policyRevision: Long = 0,
    ) : VersionCheckResult
    data object Unavailable : VersionCheckResult
}

object VersionPolicy {
    fun evaluate(
        currentVersionCode: Int,
        updateAvailable: Boolean,
        latest: RemoteAppVersion?,
        mandatoryUpdate: Boolean = false,
        policyRevision: Long = 0,
    ): VersionCheckResult {
        if (latest == null || latest.latest_version_code < 1) return VersionCheckResult.Latest
        // Version ordering answers only whether a newer release exists. The
        // server's current response is authoritative for both updateEnabled
        // and mandatoryUpdate; local comparisons never create FORCE_UPDATE.
        if (!updateAvailable || latest.latest_version_code <= currentVersionCode) {
            return VersionCheckResult.Latest
        }
        return VersionCheckResult.Available(
            version = latest,
            mandatory = mandatoryUpdate,
            policyRevision = policyRevision,
        )
    }
}

object VersionSourcePolicy {
    fun choose(
        backend: VersionCheckResult?,
        website: VersionCheckResult?,
    ): VersionCheckResult = backend ?: website ?: VersionCheckResult.Unavailable

    fun fromWebsiteManifest(
        currentVersionCode: Int,
        manifest: OfficialReleaseManifest,
    ): VersionCheckResult? {
        val latest = manifest.toRemoteAppVersion() ?: return null
        return VersionPolicy.evaluate(
            currentVersionCode = currentVersionCode,
            updateAvailable = latest.latest_version_code > currentVersionCode,
            latest = latest,
            mandatoryUpdate = false,
        )
    }
}

/** Server-driven update gate. Every successful refresh replaces the old decision. */
sealed interface AppUpdatePolicyState {
    data object IDLE : AppUpdatePolicyState
    data class CHECKING(val previous: VersionCheckResult?) : AppUpdatePolicyState
    data class NO_UPDATE(val checkedAtMillis: Long) : AppUpdatePolicyState
    data class OPTIONAL_UPDATE(val update: VersionCheckResult.Available) : AppUpdatePolicyState
    data class FORCE_UPDATE(val update: VersionCheckResult.Available) : AppUpdatePolicyState
    data class ERROR(
        val previous: VersionCheckResult?,
        val checkedAtMillis: Long,
    ) : AppUpdatePolicyState
}

object AppUpdateStateMachine {
    fun resolve(
        result: VersionCheckResult,
        previous: VersionCheckResult?,
        checkedAtMillis: Long = System.currentTimeMillis(),
    ): AppUpdatePolicyState = when (result) {
        VersionCheckResult.Latest -> AppUpdatePolicyState.NO_UPDATE(checkedAtMillis)
        VersionCheckResult.Unavailable -> AppUpdatePolicyState.ERROR(
            previous = previous,
            checkedAtMillis = checkedAtMillis,
        )
        is VersionCheckResult.Available -> if (result.mandatory) {
            AppUpdatePolicyState.FORCE_UPDATE(result)
        } else {
            AppUpdatePolicyState.OPTIONAL_UPDATE(result)
        }
    }
}

object AppUpdatePolicyController {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow<AppUpdatePolicyState>(
        AppUpdatePolicyState.IDLE,
    )
    val state: StateFlow<AppUpdatePolicyState> = mutableState.asStateFlow()

    suspend fun refresh(forceRefresh: Boolean = true): AppUpdatePolicyState =
        refreshMutex.withLock {
            val previous = resolvedResult(mutableState.value)
            mutableState.value = AppUpdatePolicyState.CHECKING(previous)
            val next = AppUpdateStateMachine.resolve(
                AppUpdateChecker.check(forceRefresh),
                previous,
            )
            // A newer successful server response always replaces FORCE_UPDATE
            // in both directions. ERROR deliberately never re-locks from cache.
            mutableState.value = next
            next
        }

    private fun resolvedResult(state: AppUpdatePolicyState): VersionCheckResult? = when (state) {
        AppUpdatePolicyState.IDLE -> null
        is AppUpdatePolicyState.CHECKING -> state.previous
        is AppUpdatePolicyState.NO_UPDATE -> VersionCheckResult.Latest
        is AppUpdatePolicyState.OPTIONAL_UPDATE -> state.update
        is AppUpdatePolicyState.FORCE_UPDATE -> state.update
        is AppUpdatePolicyState.ERROR -> state.previous
    }
}

/**
 * Pure cooldown decision logic for the automatic update prompt.
 *
 * Kept Android-free so unit tests can cover the exact rules:
 * - a dismissed version is suppressed for [cooldownMillis];
 * - a newer version immediately resets the cooldown;
 * - manual checks are never subject to this policy (callers choose to bypass).
 */
data class UpdatePromptCooldownPolicy(
    val cooldownMillis: Long,
    private val nowMillis: Long,
) {
    fun shouldPrompt(
        dismissedVersionCode: Int,
        dismissedAtMillis: Long,
        candidateVersionCode: Int,
    ): Boolean {
        if (candidateVersionCode != dismissedVersionCode) return true
        return nowMillis - dismissedAtMillis >= cooldownMillis
    }
}

object AppUpdateChecker {
    suspend fun check(forceRefresh: Boolean = false): VersionCheckResult {
        val backendResult = if (ServiceEndpointPolicy.isCampusToolsConfigured()) {
            runCatching {
                val envelope = CampusToolsApiClient.unauthenticated.versionCheck(
                    BuildConfig.VERSION_CODE,
                    VersionRequestPolicy.cacheBust(forceRefresh),
                )
                val data = envelope.data.takeIf { envelope.ok } ?: return@runCatching null
                val latest = data.latestVersion?.takeIf {
                    it.latest_version_code > 0 && it.latest_version_name.isNotBlank()
                }
                VersionPolicy.evaluate(
                    BuildConfig.VERSION_CODE,
                    data.updateAvailable,
                    latest,
                    mandatoryUpdate = data.mandatoryUpdate,
                    policyRevision = data.policyRevision,
                )
                    .takeIf { isSafeResult(it) }
            }.getOrNull()
        } else {
            null
        }
        if (backendResult != null) return backendResult

        val websiteResult = runCatching {
            VersionSourcePolicy.fromWebsiteManifest(
                currentVersionCode = BuildConfig.VERSION_CODE,
                manifest = OfficialReleaseManifestClient.latest(forceRefresh),
            )?.takeIf(::isSafeResult)
        }.getOrNull()
        return VersionSourcePolicy.choose(backendResult, websiteResult)
    }

    private fun isSafeResult(result: VersionCheckResult): Boolean =
        result !is VersionCheckResult.Available ||
            !BuildConfig.RELEASE_USER_SURFACE ||
            (
                UpdateTargetPolicy.isSafeHttps(result.version.download_url) &&
                    UpdateArtifactPolicy.expectationFrom(result.version) != null
                )
}

/** Release builds only expose update actions backed by a public HTTPS target. */
object UpdateTargetPolicy {
    fun isPublicAddress(address: java.net.InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) {
            if (bytes[0] == 0 || bytes[0] >= 224 ||
                (bytes[0] == 100 && bytes[1] in 64..127) ||
                (bytes[0] == 198 && bytes[1] in 18..19)) return false
        } else if (bytes.size == 16) {
            if ((bytes[0] and 254) == 252) return false
        }
        return true
    }

    fun isSafeHttps(rawUrl: String?): Boolean {
        val value = rawUrl?.trim().orEmpty()
        if (value.isEmpty()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty().removePrefix("[").removeSuffix("]")
        if (uri.scheme?.lowercase() != "https" || host.isBlank()) return false
        if (uri.rawUserInfo != null || uri.port !in listOf(-1, 443) || uri.rawFragment != null) return false
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) {
            return runCatching { isPublicAddress(java.net.InetAddress.getByName(host)) }.getOrDefault(false)
        }
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".invalid")) {
            return false
        }
        if (
            host == "0.0.0.0" || host == "::1" || host.startsWith("127.") ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("169.254.") || isPrivate172(host)
        ) {
            return false
        }
        return true
    }

    private fun isPrivate172(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts[0] != "172") return false
        return parts[1].toIntOrNull() in 16..31
    }
}

/**
 * Local cooldown for the automatic cold-start update prompt.
 *
 * Manual checks and taps on the service-page update card always bypass the
 * cooldown. Only the automatic prompt records a dismissal, keyed by the
 * server version code, so a newly published version is never suppressed by an
 * old dismissal.
 */
object AppUpdateCooldown {
    private const val PREF_NAME = "app_update_prompt_cooldown_v1"
    private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    private const val KEY_DISMISSED_AT_MILLIS = "dismissed_at_millis"
    const val DEFAULT_COOLDOWN_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun isPromptAllowed(context: Context, versionCode: Int): Boolean {
        val prefs = prefs(context)
        val dismissedVersionCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1)
        val dismissedAt = prefs.getLong(KEY_DISMISSED_AT_MILLIS, 0L)
        return UpdatePromptCooldownPolicy(
            cooldownMillis = DEFAULT_COOLDOWN_MILLIS,
            nowMillis = System.currentTimeMillis(),
        ).shouldPrompt(
            dismissedVersionCode = dismissedVersionCode,
            dismissedAtMillis = dismissedAt,
            candidateVersionCode = versionCode,
        )
    }

    fun recordDismissed(context: Context, versionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_DISMISSED_VERSION_CODE, versionCode)
            .putLong(KEY_DISMISSED_AT_MILLIS, System.currentTimeMillis())
            .apply()
    }

    fun clearForVersion(context: Context, versionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_DISMISSED_VERSION_CODE, -1)
            .putLong(KEY_DISMISSED_AT_MILLIS, 0L)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

@Composable
fun AppUpdatePrompt(
    update: VersionCheckResult.Available,
    onLater: () -> Unit,
    onPrimaryAction: () -> Unit,
    onRefreshPolicy: (() -> Unit)? = null,
    context: Context? = null,
) {
    val appContext = context ?: LocalContext.current
    val releaseSurface = BuildConfig.RELEASE_USER_SURFACE
    val updateState by AppUpdateCoordinator.state.collectAsState()
    val activeState = updateState.takeIf { it.versionCode() == update.version.latest_version_code.toLong() }
    val isBusy = activeState is AppUpdateState.Downloading ||
        activeState is AppUpdateState.Verifying ||
        activeState is AppUpdateState.Installing
    val isMandatory = update.mandatory
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppUpdateCoordinator.resumePendingInstall(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AlertDialog(
        onDismissRequest = {
            if (isBusy) AppUpdateCoordinator.cancelDownload(appContext)
            if (!isMandatory) onLater()
        },
        containerColor = AppTheme.colors.bgCardColor,
        title = {
            Text(
                when (activeState) {
                    is AppUpdateState.Downloading,
                    is AppUpdateState.Verifying,
                    is AppUpdateState.Installing -> "正在安装更新"
                    is AppUpdateState.NeedInstallPermission -> "需要安装权限"
                    is AppUpdateState.Failed -> "更新失败"
                    else -> "发现新版本 ${update.version.latest_version_name}"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when (val current = activeState) {
                is AppUpdateState.Downloading -> {
                    val progress = current.totalBytes
                        ?.takeIf { it > 0L }
                        ?.let { (current.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
                    Column {
                        Text("正在下载更新…")
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = androidx.compose.ui.Modifier.padding(top = 12.dp),
                        )
                        Text(
                            text = if (current.totalBytes != null) {
                                "${formatUpdateBytes(current.downloadedBytes)} / ${formatUpdateBytes(current.totalBytes)}"
                            } else {
                                formatUpdateBytes(current.downloadedBytes)
                            },
                            modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
                        )
                    }
                }
                is AppUpdateState.Verifying -> Text("正在校验更新包…")
                is AppUpdateState.ReadyToInstall -> Text("更新包已准备完成，正在进入系统安装器…")
                is AppUpdateState.NeedInstallPermission -> Text(
                    "更新已下载完成。为了安装更新，请允许“我的川农”安装应用。",
                )
                is AppUpdateState.Installing -> Text("正在准备系统安装器…")
                is AppUpdateState.Failed -> Text(current.message)
                else -> Text(
                    buildString {
                        append(update.version.update_message.ifBlank { "包含体验与稳定性改进。" })
                        append(
                            "\n\n当前版本：V${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）" +
                                "\n最新版本：V${update.version.latest_version_name}（${update.version.latest_version_code}）",
                        )
                        update.version.apk_size?.let {
                            append("\n下载大小：${formatUpdateBytes(it)}")
                        }
                        if (!releaseSurface && update.mandatory) {
                            append("\n\n当前为本地规则演示，不会锁定或替换应用。")
                        }
                        append(
                            if (releaseSurface) "\n\n更新信息由我的川农服务提供。"
                            else "\n\n更新来源：我的川农本地后端。",
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (activeState) {
                        is AppUpdateState.NeedInstallPermission ->
                            AppUpdateCoordinator.openInstallPermissionSettings(appContext)
                        is AppUpdateState.Failed ->
                            AppUpdateCoordinator.begin(appContext, update.version)
                        is AppUpdateState.Downloading,
                        is AppUpdateState.Verifying,
                        is AppUpdateState.Installing -> Unit
                        else -> onPrimaryAction()
                    }
                },
                enabled = !isBusy,
            ) {
                Text(
                    when (activeState) {
                        is AppUpdateState.NeedInstallPermission -> "去开启"
                        is AppUpdateState.Failed -> "重新下载"
                        is AppUpdateState.Downloading,
                        is AppUpdateState.Verifying,
                        is AppUpdateState.Installing -> "处理中"
                        else -> if (releaseSurface) "立即更新" else "立即更新"
                    },
                )
            }
        },
        dismissButton = if (isMandatory) {
            {
                Row {
                    onRefreshPolicy?.let { refresh ->
                        TextButton(onClick = refresh) { Text("重新检查") }
                    }
                    TextButton(
                        onClick = { (appContext as? Activity)?.finishAffinity() },
                    ) { Text("退出应用") }
                }
            }
        } else {
            {
                TextButton(
                    onClick = {
                        if (isBusy) AppUpdateCoordinator.cancelDownload(appContext)
                        onLater()
                    },
                ) { Text(if (isBusy) "取消下载" else "稍后再说") }
            }
        },
    )
}

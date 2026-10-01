package com.mychuannong.app.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.network.campustools.RemoteAppVersion
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data class Downloading(
        val version: RemoteAppVersion,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : AppUpdateState

    data class Verifying(val version: RemoteAppVersion) : AppUpdateState

    data class ReadyToInstall(val version: RemoteAppVersion) : AppUpdateState

    data class NeedInstallPermission(val version: RemoteAppVersion) : AppUpdateState

    data class Installing(val version: RemoteAppVersion) : AppUpdateState

    data class Completed(val version: RemoteAppVersion) : AppUpdateState

    data class Failed(
        val version: RemoteAppVersion?,
        val message: String,
    ) : AppUpdateState
}

internal object UpdateDownloadPolicy {
    fun isSuccessfulHttpCode(code: Int): Boolean = code in 200..299

    fun shouldAppend(existingBytes: Long, responseCode: Int): Boolean =
        existingBytes > 0L && responseCode == 206

    fun isComplete(expectedBytes: Long?, downloadedBytes: Long): Boolean =
        expectedBytes == null || expectedBytes == downloadedBytes
}

/**
 * One download and install pipeline shared by automatic and manual update checks.
 * The scope is process-lived so an Activity recreation does not cancel an active download.
 */
object AppUpdateCoordinator {
    private const val UPDATE_DIR = "app-updates"
    private const val PREF_NAME = "app_update_install_v1"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_VERSION_NAME = "version_name"
    private const val KEY_DOWNLOAD_URL = "download_url"
    private const val KEY_SHA256 = "sha256"
    private const val KEY_SIZE = "size"
    private const val KEY_UPDATE_MESSAGE = "update_message"
    private const val KEY_APK_PATH = "apk_path"
    const val INSTALL_STATUS_ACTION = "com.mychuannong.app.action.APP_UPDATE_INSTALL_STATUS"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private var activeJob: Job? = null
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun begin(context: Context, remote: RemoteAppVersion) {
        val expected = UpdateArtifactPolicy.expectationFrom(remote)
            ?: run {
                mutableState.value = AppUpdateState.Failed(remote, "更新信息不完整，无法安全下载")
                return
            }
        if (expected.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
            mutableState.value = AppUpdateState.Failed(remote, "当前已经是更高或相同版本")
            return
        }
        val existing = activeJob
        if (existing?.isActive == true && state.value.versionCode() == expected.versionCode) return
        existing?.cancel()
        val appContext = context.applicationContext
        activeJob = scope.launch {
            try {
                downloadVerifyAndInstall(appContext, remote, expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = AppUpdateState.Failed(
                    remote,
                    error.message?.takeIf { it.isNotBlank() } ?: "更新下载失败，请稍后重试",
                )
            }
        }
    }

    fun cancelDownload(context: Context) {
        activeJob?.cancel()
        activeJob = null
        val directory = context.applicationContext.filesDir.resolve(UPDATE_DIR)
        directory.listFiles().orEmpty()
            .filter { it.name.endsWith(".partial") }
            .forEach { it.delete() }
        if (state.value is AppUpdateState.Downloading || state.value is AppUpdateState.Failed) {
            mutableState.value = AppUpdateState.Idle
        }
    }

    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** Called from Activity.onResume after the user returns from the unknown-sources settings. */
    fun resumePendingInstall(context: Context) {
        val pending = readPending(context) ?: return
        if (!pending.apkFile.isFile) {
            clearPending(context)
            mutableState.value = AppUpdateState.Failed(pending.version, "更新包已不存在，请重新下载")
            return
        }
        if (!canInstallPackages(context)) return
        val savedSessionId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt("session_id", -1)
        if (savedSessionId > 0 && context.packageManager.packageInstaller.getSessionInfo(savedSessionId) != null) {
            mutableState.value = AppUpdateState.Installing(pending.version)
            return
        }
        if (state.value !is AppUpdateState.Installing) {
            activeJob = scope.launch {
                runCatching { installVerified(context.applicationContext, pending.version, pending.apkFile) }
                    .onFailure { error ->
                        mutableState.value = AppUpdateState.Failed(
                            pending.version,
                            error.message?.takeIf { it.isNotBlank() } ?: "无法进入系统安装器",
                        )
                    }
            }
        }
    }

    internal fun handleInstallStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                val pending = readPending(context)
                clearPending(context)
                cleanupUpdateDirectory(context)
                if (pending != null) {
                    mutableState.value = AppUpdateState.Completed(pending.version)
                }
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val pendingIntent = intent.parcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                if (pendingIntent == null) {
                    mutableState.value = AppUpdateState.Failed(
                        readPending(context)?.version,
                        "系统安装器没有返回确认界面",
                    )
                    return
                }
                mutableState.value = AppUpdateState.Installing(readPending(context)?.version ?: return)
                runCatching {
                    pendingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(pendingIntent)
                }.onFailure {
                    mutableState.value = AppUpdateState.Failed(
                        readPending(context)?.version,
                        "无法打开系统安装器",
                    )
                }
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: "系统安装器拒绝了更新"
                val pending = readPending(context)
                clearPending(context)
                cleanupUpdateDirectory(context)
                mutableState.value = AppUpdateState.Failed(pending?.version, message)
            }
        }
    }

    private suspend fun downloadVerifyAndInstall(
        context: Context,
        remote: RemoteAppVersion,
        expected: UpdateArtifactExpectation,
    ) {
        val directory = context.filesDir.resolve(UPDATE_DIR).apply { mkdirs() }
        removeOtherVersions(directory, expected.versionCode)
        val partial = directory.resolve("update-${expected.versionCode}.apk.partial")
        val complete = directory.resolve("update-${expected.versionCode}.apk")
        if (complete.exists()) complete.delete()

        download(context, remote, expected, partial)
        mutableState.value = AppUpdateState.Verifying(remote)
        val actualHash = withContext(Dispatchers.IO) { UpdateArtifactVerifier.sha256(partial) }
        if (!actualHash.equals(expected.sha256, ignoreCase = true)) {
            partial.delete()
            error("更新包校验失败")
        }
        val archive = withContext(Dispatchers.IO) {
            UpdateArtifactVerifier.inspectArchive(context, partial)
        }
        UpdateArtifactPolicy.verifyMetadata(expected, archive, BuildConfig.VERSION_CODE.toLong())?.let {
            partial.delete()
            error(it)
        }
        if (expected.sizeBytes != null && partial.length() != expected.sizeBytes) {
            partial.delete()
            error("更新包大小校验失败")
        }
        if (!partial.renameTo(complete)) {
            partial.delete()
            error("无法保存更新包")
        }
        mutableState.value = AppUpdateState.ReadyToInstall(remote)
        savePending(context, remote, complete)
        installVerified(context, remote, complete)
    }

    private suspend fun download(
        context: Context,
        remote: RemoteAppVersion,
        expected: UpdateArtifactExpectation,
        partial: File,
    ) = withContext(Dispatchers.IO) {
        var existingBytes = partial.takeIf { it.isFile }?.length() ?: 0L
        if (expected.sizeBytes != null && existingBytes >= expected.sizeBytes) {
            if (existingBytes == expected.sizeBytes) {
                mutableState.value = AppUpdateState.Downloading(remote, existingBytes, expected.sizeBytes)
                return@withContext
            }
            partial.delete()
            existingBytes = 0L
        }
        val requestBuilder = Request.Builder()
            .url(expected.downloadUrl)
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/vnd.android.package-archive")
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")
        client.newCall(requestBuilder.build()).execute().use { response ->
            val append = UpdateDownloadPolicy.shouldAppend(existingBytes, response.code)
            if (!UpdateDownloadPolicy.isSuccessfulHttpCode(response.code) && !(existingBytes > 0L && response.code == 416)) {
                error("下载服务器返回 HTTP ${response.code}")
            }
            if (response.code == 416 && expected.sizeBytes == existingBytes) {
                mutableState.value = AppUpdateState.Downloading(remote, existingBytes, expected.sizeBytes)
                return@use
            }
            val startingBytes = if (append) existingBytes else 0L
            if (!append) {
                partial.delete()
                existingBytes = 0L
            }
            val body = response.body
            val contentLength = body.contentLength().takeIf { it > 0L }
            val totalBytes = expected.sizeBytes ?: contentLength?.let { it + startingBytes }
            var downloadedBytes = startingBytes
            mutableState.value = AppUpdateState.Downloading(remote, downloadedBytes, totalBytes)
            FileOutputStream(partial, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        mutableState.value = AppUpdateState.Downloading(
                            remote,
                            downloadedBytes,
                            totalBytes,
                        )
                    }
                }
                output.fd.sync()
            }
            if (!UpdateDownloadPolicy.isComplete(expected.sizeBytes, downloadedBytes)) {
                error("下载不完整：${downloadedBytes}/${expected.sizeBytes}")
            }
        }
    }

    private suspend fun installVerified(context: Context, remote: RemoteAppVersion, apkFile: File) {
        if (!canInstallPackages(context)) {
            mutableState.value = AppUpdateState.NeedInstallPermission(remote)
            return
        }
        mutableState.value = AppUpdateState.Installing(remote)
        withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply {
                    setAppPackageName(UpdateArtifactPolicy.EXPECTED_PACKAGE_NAME)
                    setSize(apkFile.length())
                }
            val installer = context.packageManager.packageInstaller
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                session.openWrite("base.apk", 0L, apkFile.length()).use { output ->
                    apkFile.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = INSTALL_STATUS_ACTION
                }
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                saveSessionId(context, sessionId)
                session.commit(pendingIntent.intentSender)
            } finally {
                session.close()
            }
        }
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    private data class PendingInstall(val version: RemoteAppVersion, val apkFile: File)

    private fun savePending(context: Context, version: RemoteAppVersion, file: File) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VERSION_CODE, version.latest_version_code)
            .putString(KEY_VERSION_NAME, version.latest_version_name)
            .putString(KEY_DOWNLOAD_URL, version.download_url)
            .putString(KEY_SHA256, version.apk_sha256)
            .putLong(KEY_SIZE, version.apk_size ?: 0L)
            .putString(KEY_UPDATE_MESSAGE, version.update_message)
            .putString(KEY_APK_PATH, file.absolutePath)
            .remove("session_id")
            .apply()
    }

    private fun saveSessionId(context: Context, sessionId: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt("session_id", sessionId).apply()
    }

    private fun readPending(context: Context): PendingInstall? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val code = prefs.getInt(KEY_VERSION_CODE, 0)
        val path = prefs.getString(KEY_APK_PATH, null)
        if (code < 1 || path.isNullOrBlank()) return null
        return PendingInstall(
            version = RemoteAppVersion(
                latest_version_code = code,
                minimum_version_code = 1,
                latest_version_name = prefs.getString(KEY_VERSION_NAME, "") ?: "",
                update_message = prefs.getString(KEY_UPDATE_MESSAGE, "") ?: "",
                download_url = prefs.getString(KEY_DOWNLOAD_URL, null),
                apk_sha256 = prefs.getString(KEY_SHA256, null),
                apk_size = prefs.getLong(KEY_SIZE, 0L).takeIf { it > 0L },
                // Installation recovery is artifact state, not update policy.
                // A historical local force bit must never re-lock the App.
                force_update = false,
            ),
            apkFile = File(path),
        )
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun removeOtherVersions(directory: File, currentCode: Long) {
        directory.listFiles().orEmpty()
            .filter { it.name.startsWith("update-") && !it.name.startsWith("update-$currentCode.") }
            .forEach { it.delete() }
    }

    private fun cleanupUpdateDirectory(context: Context) {
        context.filesDir.resolve(UPDATE_DIR).listFiles().orEmpty().forEach { it.delete() }
    }

}

internal fun AppUpdateState.versionCode(): Long? = when (this) {
    AppUpdateState.Idle -> null
    is AppUpdateState.Downloading -> version.latest_version_code.toLong()
    is AppUpdateState.Verifying -> version.latest_version_code.toLong()
    is AppUpdateState.ReadyToInstall -> version.latest_version_code.toLong()
    is AppUpdateState.NeedInstallPermission -> version.latest_version_code.toLong()
    is AppUpdateState.Installing -> version.latest_version_code.toLong()
    is AppUpdateState.Completed -> version.latest_version_code.toLong()
    is AppUpdateState.Failed -> version?.latest_version_code?.toLong()
}

internal fun formatUpdateBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppUpdateCoordinator.handleInstallStatus(context.applicationContext, intent)
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key)
    }

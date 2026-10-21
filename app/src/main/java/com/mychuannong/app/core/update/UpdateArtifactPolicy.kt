package com.mychuannong.app.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.network.campustools.RemoteAppVersion
import java.io.File
import java.security.MessageDigest

/** Metadata that must be present before an APK can be downloaded. */
data class UpdateArtifactExpectation(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long?,
)

data class UpdateArchiveMetadata(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val signingCertificateSha256: String,
)

/** Pure policy and APK metadata helpers kept separate from the download lifecycle. */
object UpdateArtifactPolicy {
    const val EXPECTED_PACKAGE_NAME = "com.mychuannong.app"
    const val OFFICIAL_SIGNING_CERTIFICATE_SHA256 =
        BuildConfig.OFFICIAL_SIGNING_CERTIFICATE_SHA256

    fun expectationFrom(remote: RemoteAppVersion): UpdateArtifactExpectation? {
        val name = remote.latest_version_name.trim()
        val url = remote.download_url?.trim().orEmpty()
        val hash = remote.apk_sha256?.trim()?.uppercase().orEmpty()
        if (
            name.isBlank() ||
            remote.latest_version_code < 1 ||
            (remote.apk_size != null && remote.apk_size !in 1..UpdateDownloadPolicy.MAX_APK_BYTES) ||
            !UpdateTargetPolicy.isSafeHttps(url) ||
            !hash.matches(Regex("[0-9A-F]{64}"))
        ) {
            return null
        }
        return UpdateArtifactExpectation(
            versionName = name,
            versionCode = remote.latest_version_code.toLong(),
            downloadUrl = url,
            sha256 = hash,
            sizeBytes = remote.apk_size?.takeIf { it > 0L },
        )
    }

    fun verifyMetadata(
        expectation: UpdateArtifactExpectation,
        actual: UpdateArchiveMetadata,
        currentVersionCode: Long,
    ): String? {
        if (actual.packageName != EXPECTED_PACKAGE_NAME) {
            return "更新包应用标识不匹配"
        }
        if (actual.versionCode != expectation.versionCode) {
            return "更新包版本号不匹配"
        }
        if (actual.versionName != expectation.versionName) {
            return "更新包版本名称不匹配"
        }
        if (actual.versionCode <= currentVersionCode) {
            return "更新包不是更高版本"
        }
        if (actual.signingCertificateSha256 != OFFICIAL_SIGNING_CERTIFICATE_SHA256) {
            return "更新包签名证书不匹配"
        }
        return null
    }
}

object UpdateArtifactVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    fun verifySha256(file: File, expected: String): Boolean =
        sha256(file).equals(expected.trim(), ignoreCase = true)

    fun inspectArchive(context: Context, file: File): UpdateArchiveMetadata {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("无法读取更新包信息")
        return packageInfo.toArchiveMetadata()
    }

    private fun PackageInfo.toArchiveMetadata(): UpdateArchiveMetadata {
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            signatures?.firstOrNull()
        } ?: error("无法读取更新包签名")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
        val certDigest = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02X".format(it) }
        return UpdateArchiveMetadata(
            packageName = packageName.orEmpty(),
            versionName = versionName,
            versionCode = versionCode,
            signingCertificateSha256 = certDigest,
        )
    }
}

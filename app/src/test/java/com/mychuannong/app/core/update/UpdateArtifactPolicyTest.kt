package com.mychuannong.app.core.update

import com.mychuannong.app.core.network.campustools.RemoteAppVersion
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateArtifactPolicyTest {
    private val validRemote = RemoteAppVersion(
        latest_version_code = 35,
        minimum_version_code = 34,
        latest_version_name = "1.0.3",
        update_message = "App 内更新",
        download_url = "https://downloads.example.test/app.apk",
        apk_sha256 = "A".repeat(64),
        apk_size = 123L,
    )

    @Test
    fun `incomplete remote metadata is rejected`() {
        assertNotNull(UpdateArtifactPolicy.expectationFrom(validRemote))
        assertNull(
            UpdateArtifactPolicy.expectationFrom(validRemote.copy(apk_sha256 = null)),
        )
        assertNull(
            UpdateArtifactPolicy.expectationFrom(validRemote.copy(download_url = "http://example.com/app.apk")),
        )
    }

    @Test
    fun `metadata policy rejects wrong package certificate and old version`() {
        val expectation = UpdateArtifactPolicy.expectationFrom(validRemote)!!
        val good = UpdateArchiveMetadata(
            packageName = UpdateArtifactPolicy.EXPECTED_PACKAGE_NAME,
            versionName = "1.0.3",
            versionCode = 35L,
            signingCertificateSha256 = UpdateArtifactPolicy.OFFICIAL_SIGNING_CERTIFICATE_SHA256,
        )
        assertNull(UpdateArtifactPolicy.verifyMetadata(expectation, good, currentVersionCode = 34L))
        assertEquals(
            "更新包应用标识不匹配",
            UpdateArtifactPolicy.verifyMetadata(expectation, good.copy(packageName = "other.app"), 34L),
        )
        assertEquals(
            "更新包版本名称不匹配",
            UpdateArtifactPolicy.verifyMetadata(expectation, good.copy(versionName = null), 34L),
        )
        assertEquals(
            "更新包签名证书不匹配",
            UpdateArtifactPolicy.verifyMetadata(expectation, good.copy(signingCertificateSha256 = "0"), 34L),
        )
        assertEquals(
            "更新包不是更高版本",
            UpdateArtifactPolicy.verifyMetadata(expectation, good, 35L),
        )
    }

    @Test
    fun `sha256 verification distinguishes matching and tampered files`() {
        val file = Files.createTempFile("mychuannong-update", ".apk").toFile()
        try {
            file.writeText("signed release candidate")
            val actual = UpdateArtifactVerifier.sha256(file)
            assertTrue(UpdateArtifactVerifier.verifySha256(file, actual.lowercase()))
            assertFalse(UpdateArtifactVerifier.verifySha256(file, "0".repeat(64)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `download policy handles success failure resume and interruption`() {
        assertTrue(UpdateDownloadPolicy.isSuccessfulHttpCode(200))
        assertTrue(UpdateDownloadPolicy.isSuccessfulHttpCode(206))
        assertFalse(UpdateDownloadPolicy.isSuccessfulHttpCode(503))
        assertTrue(UpdateDownloadPolicy.shouldAppend(68L, 206))
        assertFalse(UpdateDownloadPolicy.shouldAppend(68L, 200))
        assertTrue(UpdateDownloadPolicy.isComplete(100L, 100L))
        assertFalse(UpdateDownloadPolicy.isComplete(100L, 68L))
        assertTrue(UpdateDownloadPolicy.isComplete(null, 68L))
    }

    @Test
    fun `original school app certificate accepts updates and old product certificate does not`() {
        val expected = UpdateArtifactPolicy.expectationFrom(validRemote)!!
        val actual = UpdateArchiveMetadata("com.mychuannong.app", "1.0.3", 35,
            "A8F8EAC6FF810CAC0B21CBD59A08D5525E1ACA9C225F86F516FBD1172BECDF6D")
        assertNull(UpdateArtifactPolicy.verifyMetadata(expected, actual, 10))
        assertNotNull(UpdateArtifactPolicy.verifyMetadata(expected, actual.copy(signingCertificateSha256 =
            "2C3B934931C26719C1A55FA707760A41E18BA15B00561F97691FE1D452F4BF3D"), 10))
    }

    @Test
    fun `download rejects private targets and invalid sizes`() {
        for (url in listOf("https://[::1]/a", "https://[fc00::1]/a", "https://2130706433/a",
            "https://100.64.0.1/a", "https://224.0.0.1/a", "https://example.test:444/a", "https://example.test/a#x")) {
            assertFalse(url, UpdateTargetPolicy.isSafeHttps(url))
        }
        assertTrue(UpdateTargetPolicy.isSafeHttps("https://downloads.example.test/a"))
        for (size in listOf(0L, -1L, UpdateDownloadPolicy.MAX_APK_BYTES + 1)) {
            assertNull(UpdateArtifactPolicy.expectationFrom(validRemote.copy(apk_size = size)))
        }
    }

    @Test
    fun `streaming limit protects disk for chunked responses and resume`() {
        val saved = java.io.ByteArrayOutputStream()
        val payload = ByteArray(100_000)
        val input = java.io.ByteArrayInputStream(payload)
        try {
            UpdateDownloadPolicy.copyBounded(input, saved, 98, 100) {}
            org.junit.Assert.fail("overflow must stop the download")
        } catch (_: IllegalStateException) { }
        assertEquals(3, payload.size - input.available())
        assertEquals(0, saved.size())
        assertEquals(100L, UpdateDownloadPolicy.copyBounded(java.io.ByteArrayInputStream(byteArrayOf(1, 2)), saved, 98, 100) {})
        assertEquals(2, saved.size())
    }
}

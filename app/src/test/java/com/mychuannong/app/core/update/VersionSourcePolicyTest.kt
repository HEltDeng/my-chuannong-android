package com.mychuannong.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionSourcePolicyTest {
    private val manifest = OfficialReleaseManifest(
        versionName = "1.0.2",
        versionCode = 34,
        minimumVersionCode = 32,
        downloadUrl = "https://mychangliapp.cn/downloads/%E6%88%91%E7%9A%84%E9%95%BF%E7%90%86-V1.0.2.apk",
        apkSha256 = "65E842180BA9A706004F85D6856AEF308E4168135051B0224B2120BE3BD9E422",
        apkSize = 6_028_071L,
        updateMessage = "修复课程表定位。",
        publishedAt = "2026-09-05",
    )

    @Test
    fun websiteManifestProducesNumericUpdate() {
        val result = VersionSourcePolicy.fromWebsiteManifest(33, manifest)
        assertTrue(result is VersionCheckResult.Available)
        assertEquals(34, (result as VersionCheckResult.Available).version.latest_version_code)
        assertEquals(false, result.mandatory)
    }

    @Test
    fun websiteManifestCannotCreateAForcedUpdate() {
        val result = VersionSourcePolicy.fromWebsiteManifest(
            33,
            manifest.copy(forceUpdate = true, minimumVersionCode = 34),
        ) as VersionCheckResult.Available
        assertEquals(false, result.mandatory)
        assertEquals(false, result.version.force_update)
        assertEquals(1, result.version.minimum_version_code)
    }

    @Test
    fun backendSuccessWinsOverWebsiteFallback() {
        val backend = VersionCheckResult.Latest
        val website = VersionCheckResult.Available(manifest.toRemoteAppVersion()!!, mandatory = false)
        assertSame(backend, VersionSourcePolicy.choose(backend, website))
    }

    @Test
    fun backendFailureFallsBackToWebsiteAndBothFailuresAreUnavailable() {
        val website = VersionSourcePolicy.fromWebsiteManifest(33, manifest)
        assertEquals(website, VersionSourcePolicy.choose(null, website))
        assertEquals(VersionCheckResult.Unavailable, VersionSourcePolicy.choose(null, null))
    }

    @Test
    fun invalidWebsiteManifestIsRejected() {
        val invalid = manifest.copy(downloadUrl = "http://mychangliapp.cn/app.apk")
        assertNull(VersionSourcePolicy.fromWebsiteManifest(33, invalid))
    }

    @Test
    fun manualRefreshAddsCacheBustAndAutomaticChecksDoNot() {
        assertNull(VersionRequestPolicy.cacheBust(forceRefresh = false, nowMillis = 42L))
        assertEquals(42L, VersionRequestPolicy.cacheBust(forceRefresh = true, nowMillis = 42L))
    }
}

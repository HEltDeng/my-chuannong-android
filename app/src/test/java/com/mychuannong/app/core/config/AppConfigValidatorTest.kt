package com.mychuannong.app.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AppConfigValidatorTest {
    @Test
    fun builtInConfigPassesRegistryAndSafetyValidation() {
        AppConfigValidator.validate(
            envelope = BuiltInAppConfig.envelope(),
            appVersionCode = 2,
            allowBuiltInVersionZero = true,
        )

        assertTrue(FeatureRegistry.isRegisteredAction("schedule"))
        assertFalse(AppConfigValidator.isSafeOfficialUrl("https://mychangliapp.cn/features.html"))
    }

    @Test
    fun canonicalHashIsStableForEquivalentPayloads() {
        val first = payload(title = "成绩查询", order = 20)
        val second = payload(title = "成绩查询", order = 20)

        assertEquals(
            AppConfigValidator.canonicalConfigHash(first),
            AppConfigValidator.canonicalConfigHash(second),
        )
        assertEquals(
            AppConfigValidator.canonicalConfigHash(BuiltInAppConfig.envelope().config),
            AppConfigValidator.canonicalConfigHash(BuiltInAppConfig.envelope().config),
        )
    }

    @Test
    fun unsafeOrUnregisteredConfigIsRejected() {
        val unsafeUrl = payload(title = "外链", order = 20).copy(
            navigation = payload(title = "外链", order = 20).navigation.map {
                it.copy(actionId = "grade", url = "https://evil.example/")
            },
        )
        val unsafeEnvelope = envelope(unsafeUrl)

        val unsafeError = runCatching {
            AppConfigValidator.validate(unsafeEnvelope, appVersionCode = 2)
        }.exceptionOrNull()

        assertTrue(unsafeError is AppConfigValidationException)
        assertTrue(unsafeError?.message?.contains("官方 HTTPS") == true)

        val unknownAction = payload(title = "未知", order = 20).copy(
            navigation = payload(title = "未知", order = 20).navigation.map {
                it.copy(actionId = "not_registered")
            },
        )
        val unknownError = runCatching {
            AppConfigValidator.validate(envelope(unknownAction), appVersionCode = 2)
        }.exceptionOrNull()

        assertTrue(unknownError is AppConfigValidationException)
        assertTrue(unknownError?.message?.contains("未注册") == true)
    }

    @Test
    fun appVersionRangeAndExpiryAreRejected() {
        val config = payload(title = "版本限制", order = 20)
        val now = 1_700_000_000_000L

        val incompatible = envelope(
            config = config,
            minAppVersionCode = 3,
            issuedAt = now,
        )
        assertTrue(
            runCatching {
                AppConfigValidator.validate(incompatible, appVersionCode = 2, nowMs = now)
            }.exceptionOrNull() is AppConfigValidationException,
        )

        val expired = envelope(
            config = config,
            issuedAt = now - 73L * 60L * 60L * 1000L,
        )
        assertTrue(
            runCatching {
                AppConfigValidator.validate(expired, appVersionCode = 2, nowMs = now)
            }.exceptionOrNull() is AppConfigValidationException,
        )
    }

    private fun envelope(
        config: AppConfigPayload,
        minAppVersionCode: Int = 0,
        issuedAt: Long? = System.currentTimeMillis(),
    ): AppConfigEnvelope {
        return AppConfigEnvelope(
            schemaVersion = AppConfigValidator.SCHEMA_VERSION,
            configVersion = 1,
            minAppVersionCode = minAppVersionCode,
            issuedAt = issuedAt,
            configHash = AppConfigValidator.canonicalConfigHash(config),
            config = config,
        )
    }

    private fun payload(
        title: String,
        order: Int,
    ): AppConfigPayload {
        return BuiltInAppConfig.envelope().config.copy(
            navigation = listOf(
                AppConfigNavigationItem(
                    id = "schedule",
                    actionId = "schedule",
                    title = "课表",
                    iconKey = "calendar",
                    order = 10,
                    section = "learning",
                ),
                AppConfigNavigationItem(
                    id = "grade",
                    actionId = "grade",
                    title = title,
                    iconKey = "assessment",
                    order = order,
                    section = "learning",
                    url = null,
                ),
            ),
        )
    }
}

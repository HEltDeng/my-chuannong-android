package com.mychuannong.app.core.network.campustools

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Produces an app-scoped, one-way device identifier.
 *
 * The raw ANDROID_ID is never placed in a request, log, or persisted value.
 * Unsupported/invalid platform values intentionally return null instead of
 * falling back to installationId, which represents an install instance only.
 */
object DeviceIdentity {
    private const val APP_NAMESPACE = "com.mychuannong.app:device-scoped-v1:"
    private const val KNOWN_INVALID_ANDROID_ID = "9774d56d682e549c"

    fun deviceScopedId(context: Context): String? = deriveDeviceScopedId(
        Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ),
    )

    fun deriveDeviceScopedId(androidId: String?): String? {
        val normalized = androidId?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() && it != KNOWN_INVALID_ANDROID_ID }
            ?: return null
        return sha256Hex(APP_NAMESPACE + normalized)
    }

    private fun sha256Hex(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

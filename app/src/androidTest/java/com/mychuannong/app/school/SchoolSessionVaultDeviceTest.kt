package com.mychuannong.app.school

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Dedicated validation package only; synthetic values never contact a school server. */
@RunWith(AndroidJUnit4::class)
class SchoolSessionVaultDeviceTest {
    @Test fun encryptedSessionAndIdentityRoundTrip() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".validation"))
        // Never overwrite an existing authenticated test session.
        assertFalse(File(context.noBackupFilesDir,"sicau-identity.enc").exists())
        assertFalse(File(context.noBackupFilesDir,"sicau-session.enc").exists())
        try {
            val jar=SchoolCookieJar().apply{importAuthCookies(listOf("go-admin-tgt=synthetic-device-cookie; Path=/; Secure; HttpOnly"))}
            SchoolSessionVault.save(jar)
            SchoolSessionVault.saveIdentity("SYNTHETIC_DEVICE_OWNER")
            assertEquals("SYNTHETIC_DEVICE_OWNER",SchoolSessionVault.readIdentity())
            assertEquals("synthetic-device-cookie",SchoolSessionVault.read().loadForRequest(SicauAuthProtocol.ORIGIN.toHttpUrl()).single().value)
            assertFalse(String(File(context.noBackupFilesDir,"sicau-session.enc").readBytes()).contains("synthetic-device-cookie"))
            assertFalse(String(File(context.noBackupFilesDir,"sicau-identity.enc").readBytes()).contains("SYNTHETIC_DEVICE_OWNER"))
        } finally {
            SchoolSessionVault.clear();SchoolSessionVault.clearIdentity()
        }
    }
}

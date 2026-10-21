package com.mychuannong.app.school

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic data only, in the dedicated validation package. No school or platform calls. */
@RunWith(AndroidJUnit4::class)
class SicauJiaowuVaultDeviceTest {
    @Test fun androidCookieParsingAndKeystoreRoundTripRejectTampering() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".validation"))
        val target = File(context.noBackupFilesDir, "sicau-jiaowu.enc")
        assertFalse("Never overwrite a real session", target.exists())
        val authFile = File(context.noBackupFilesDir, "sicau-session.enc")
        val authBefore = authFile.takeIf { it.exists() }?.readBytes()
        val jar = SicauJiaowuCookieJar()
        var step = 0
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val body = when(step++) {
                0 -> {
                    jar.saveFromResponse(request.url, listOf(Cookie.parse(request.url,
                        "ASPSESSIONIDTEST=synthetic-vault-cookie; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly")!!))
                    "<input name=sign value=synthetic-sign><input name=hour_key value=synthetic-hour>"
                }
                1 -> "<script>location='student.asp'</script>"
                else -> "<table><tr><td>学号</td><td>20260001</td></tr></table>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body(body.toByteArray(java.nio.charset.Charset.forName("GBK")).toResponseBody()).build()
        }.build()
        try {
            val session = SicauJiaowuSession(jar,client,{})
            session.login("20260001","synthetic")
            SicauJiaowuSessionVault.save(session)
            val restored = requireNotNull(SicauJiaowuSessionVault.read())
            assertEquals("20260001",restored.first)
            assertEquals("synthetic-vault-cookie",restored.second.loadForRequest(SicauJiaowuProtocol.PERSONAL_INFO.toHttpUrl()).single().value)
            assertTrue(restored.second.loadForRequest("https://auth.sicau.edu.cn/".toHttpUrl()).isEmpty())
            val encrypted = target.readBytes()
            assertFalse(String(encrypted).contains("synthetic-vault-cookie"))
            assertFalse(String(encrypted).contains("20260001"))
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
            target.writeBytes(encrypted)
            assertNull(SicauJiaowuSessionVault.read())
            assertArrayEquals(authBefore,authFile.takeIf { it.exists() }?.readBytes())
        } finally { SicauJiaowuSessionVault.clear() }
    }
}

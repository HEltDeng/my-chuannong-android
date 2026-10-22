package com.mychuannong.app.school

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.SecondaryPageHeader
import com.mychuannong.app.feature.common.compose_ui.ServiceDynamicsSection
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI-only data; never writes the remote store or learning caches. */
@RunWith(AndroidJUnit4::class)
class ServiceDynamicsUiTest {
    // Prepare only the isolated validation install before Compose launches its host.
    @get:Rule(order = 0) val binding = object : org.junit.rules.ExternalResource() {
        override fun before() = kotlinx.coroutines.runBlocking {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName == "com.mychuannong.app.validation")
            com.mychuannong.app.core.privacy.PrivacyConsentManager.acceptAppConsent(context)
            val jar = SicauJiaowuCookieJar()
            val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
                val request = chain.request()
                val body = when(request.url.toString()) {
                    SicauJiaowuProtocol.LOGIN_PAGE -> {
                        jar.saveFromResponse(request.url, listOf(requireNotNull(okhttp3.Cookie.parse(request.url,
                            "ASPSESSIONIDTEST=synthetic; Domain=sicau.edu.cn; Path=/; Secure; HttpOnly"))))
                        "<input name=sign value=synthetic><input name=hour_key value=synthetic>"
                    }
                    SicauJiaowuProtocol.LOGIN_POST -> "<script>location='student.asp'</script>"
                    else -> "<table><tr><td>学号</td><td>20260001</td></tr></table>"
                }
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("synthetic").body(body.toByteArray(charset("GBK")).toResponseBody()).build()
            }.build()
            val session = SicauJiaowuSession(jar, client, {})
            session.login("20260001", "synthetic-password")
            SicauJiaowuAuth.activate(session)
            com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.acceptVerifiedIdentity("20260001")
            com.mychuannong.app.core.security.SchoolAccessGate.restoreBinding()
            Unit
        }
        override fun after() {
            com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.clear()
            SicauJiaowuSessionVault.clear()
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()
    @Test fun successfulEmptyAndNetworkFailureHaveDifferentMessages() {
        val error = mutableStateOf<String?>(null)
        compose.setContent { AppSkinTheme {
            ServiceDynamicsSection(emptyList(), {}, error=error.value)
        } }
        compose.onNodeWithText("服务动态").assertIsDisplayed()
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertIsDisplayed()
        compose.runOnIdle { error.value="暂时无法获取公告，请重试" }
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertDoesNotExist()
        compose.onNodeWithText("暂时无法获取公告，请重试").assertIsDisplayed()
    }
    @Test fun emptyStateIsVisibleAndHeaderBackWorks() {
        var back = false
        compose.setContent { AppSkinTheme {
            androidx.compose.foundation.layout.Column {
                SecondaryPageHeader("测试详情",{ back=true })
                ServiceDynamicsSection(emptyList(),{})
            }
        } }
        compose.onNodeWithText("服务动态").assertIsDisplayed()
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(true,back) }
    }
    @Test fun swipeClickAndShrinkingRefreshStayConsistent() {
        val entries=mutableStateOf(listOf(
            RemoteServiceUpdate("test-a","ANNOUNCEMENT",title="合成动态甲",summary="仅用于测试"),
            RemoteServiceUpdate("test-b","ANNOUNCEMENT",title="合成动态乙",summary="仅用于测试")))
        var clicked=""
        compose.setContent { AppSkinTheme { ServiceDynamicsSection(entries.value,{ clicked=it.id }) } }
        compose.onNodeWithText("合成动态甲").assertIsDisplayed()
        compose.onNodeWithTag("service-dynamics-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("合成动态乙").assertIsDisplayed()
        compose.onNodeWithText("合成动态乙").performClick()
        compose.runOnIdle { assertEquals("test-b",clicked);entries.value=entries.value.take(1) }
        compose.onNodeWithText("合成动态甲").assertIsDisplayed()
        compose.runOnIdle { entries.value=emptyList() }
        compose.onNodeWithText("暂无服务动态，下拉可刷新").assertIsDisplayed()
    }
}

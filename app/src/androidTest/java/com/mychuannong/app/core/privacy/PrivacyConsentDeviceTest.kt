package com.mychuannong.app.core.privacy

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.security.SchoolEntryActivity
import com.mychuannong.app.core.theme.AppSkinTheme
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Uses only the isolated validation package, without school or platform accounts. */
class PrivacyConsentDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<SchoolEntryActivity>? = null
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun resetIsolatedConsent() {
        check(context.packageName.endsWith(".validation"))
        context.getSharedPreferences("privacy_consent", 0).edit().clear().commit()
    }

    @After fun clearIsolatedConsent() {
        scenario?.close()
        scenario = null
        if (context.packageName.endsWith(".validation")) {
            context.getSharedPreferences("privacy_consent", 0).edit().clear().commit()
        }
    }

    @Test fun welcomeProvidesTwoDocumentLinksAndRequiresAnExplicitDecision() {
        var openedAgreement = false
        var openedPrivacy = false
        // This is the production activity allowed before consent. A generic
        // ComponentActivity would correctly be redirected by SchoolAccessGate.
        scenario = ActivityScenario.launch(SchoolEntryActivity::class.java).also { launched ->
            launched.onActivity { activity ->
                activity.setContent {
                    AppSkinTheme {
                        PrivacyConsentScreen(
                            onAccept = { PrivacyConsentManager.acceptAppConsent(context) },
                            onDecline = { PrivacyConsentManager.declineAppConsent(context) },
                            onOpenAgreement = { openedAgreement = true },
                            onOpenPrivacy = { openedPrivacy = true },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("用户协议及隐私保护").assertIsDisplayed()
        compose.onNodeWithText("用户协议").assertIsDisplayed().performClick()
        compose.onNodeWithText("隐私政策").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(openedAgreement)
            assertTrue(openedPrivacy)
            assertFalse(PrivacyConsentManager.hasAppConsent(context))
        }
        compose.onNodeWithText("不同意").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(PrivacyConsentManager.AppConsentState.DECLINED, PrivacyConsentManager.appConsentState(context))
            assertEquals(0L, PrivacyConsentManager.acceptedAt(context))
        }
        compose.onNodeWithText("同意").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(PrivacyConsentManager.hasAppConsent(context))
            assertTrue(PrivacyConsentManager.acceptedAt(context) > 0L)
        }
    }

    @Test fun allFourBundledDocumentsMatchTheActivePolicy() {
        val edition = context.assets.open("MyChuannongLegal.json").bufferedReader().use { JSONObject(it.readText()) }
        assertEquals(PrivacyConsentManager.POLICY_VERSION, edition.getString("version"))
        val documents = edition.getJSONObject("documents")
        for (key in listOf("privacy", "agreement", "collection", "thirdparties")) {
            val paragraphs = documents.getJSONArray(key)
            assertTrue("Bundled document is empty: $key", paragraphs.length() > 0)
            for (index in 0 until paragraphs.length()) {
                val paragraph = paragraphs.getJSONObject(index)
                assertTrue(paragraph.has("kind"))
                assertTrue(paragraph.getJSONArray("runs").length() > 0)
            }
        }
    }
}

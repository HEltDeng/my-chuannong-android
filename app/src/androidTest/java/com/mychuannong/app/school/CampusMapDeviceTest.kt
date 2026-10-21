package com.mychuannong.app.school

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.BuildConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run on a disposable emulator: verifies consent and upgrade behavior without illustration fallback. */
class CampusMapDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("campus_amap_consent", Context.MODE_PRIVATE)

    @Test fun decliningKeepsMapDisabledAfterRecreationUntilExplicitAcceptance() {
        assumeTrue(BuildConfig.AMAP_API_KEY.isNotBlank())
        prefs.edit().clear().commit()
        ActivityScenario.launch(CampusMapActivity::class.java).use { scenario ->
            compose.onNodeWithTag("campus-amap-decline").performClick()
            scenario.recreate()
            compose.onNodeWithTag("campus-map-unavailable").assertIsDisplayed()
            compose.onNodeWithTag("campus-amap-accept").assertDoesNotExist()
            compose.onNodeWithTag("campus-amap-scene").assertDoesNotExist()
            compose.onNodeWithTag("campus-map-scene").assertDoesNotExist()
            assertFalse(CampusAmapConsent.agreed(context))
            compose.onNodeWithTag("campus-amap-enable").performClick()
            compose.onNodeWithTag("campus-amap-accept").assertIsDisplayed()
            compose.onNodeWithTag("campus-amap-scene").assertDoesNotExist()
        }
    }
    @Test fun previousConsentOpensAmapEvenWhenOldIllustrationPreferenceWasSelected() {
        assumeTrue(BuildConfig.AMAP_API_KEY.isNotBlank())
        prefs.edit().clear().putBoolean("agreed_v1", true).putBoolean("online", false).commit()
        ActivityScenario.launch(CampusMapActivity::class.java).use {
            compose.onNodeWithTag("campus-amap-scene").assertExists()
            compose.onNodeWithTag("campus-amap-accept").assertDoesNotExist()
            compose.onNodeWithTag("campus-map-mode").assertDoesNotExist()
            compose.onNodeWithText("原图").assertDoesNotExist()
            compose.onNodeWithText("示意图").assertDoesNotExist()
        }
    }
}

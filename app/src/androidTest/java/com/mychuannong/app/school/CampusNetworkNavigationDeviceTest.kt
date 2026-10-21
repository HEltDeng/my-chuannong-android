package com.mychuannong.app.school

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Isolated activity lifecycle smoke test; only the public login-page connectivity probe may run. */
class CampusNetworkNavigationDeviceTest {
    @Test fun networkStatusActivityOpensRecreatesAndReturnsNormally() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".bindingtest"))
        ActivityScenario.launch(CampusNetworkActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            if (InstrumentationRegistry.getArguments().getString("bindingScreenshots") == "true") {
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.waitForIdle(500, 5_000)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "network-activity.png")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            scenario.onActivity { it.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}

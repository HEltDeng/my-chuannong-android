package com.mychuannong.app.school

import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore
import com.mychuannong.app.core.privacy.PrivacyConsentManager
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.ConcurrentLinkedQueue

/** Local public preview API only; never submits platform or school credentials. */
class ServiceRefreshDeviceTest {
    @Test fun recentAutomaticRefreshReusesCacheButExplicitRefreshStillRuns()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".perftest"))
        PrivacyConsentManager.acceptAppConsent(context)
        val events=ConcurrentLinkedQueue<Boolean>()
        val collector=launch(Dispatchers.Unconfined) {
            RemoteConfigurationStore.serviceUpdatesRefreshing.collect { events.add(it) }
        }
        try {
            val first=RemoteConfigurationStore.refreshServiceUpdates()
            assertEquals(first,RemoteConfigurationStore.serviceUpdates.value)
            events.clear()
            repeat(5) { assertEquals(first,RemoteConfigurationStore.refreshServiceUpdates(30_000L)) }
            assertFalse("Repeated tab entry must not start a fresh request",events.contains(true))
            RemoteConfigurationStore.refreshServiceUpdates()
            assertTrue("Manual refresh must remain immediate",events.contains(true))
            assertFalse(RemoteConfigurationStore.serviceUpdatesRefreshing.value)
        } finally { collector.cancelAndJoin() }
    }
}

package com.mychuannong.app.school

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CampusNetworkDetectorTest {
    @Test fun verifiedVpnTakesPriorityWithoutProbingDirect() = runBlocking {
        val detector = CampusNetworkDetector({ true }, { error("Direct must not be probed") })
        assertEquals(CampusNetworkStatus.VPN_CONNECTED, detector.check())
    }

    @Test fun missingVpnRequiresSuccessfulDirectProbeBeforeShowingCampusAccess() = runBlocking {
        val detector = CampusNetworkDetector({ false }, { true })
        assertEquals(CampusNetworkStatus.CAMPUS_DIRECT, detector.check())
    }

    @Test fun networkFailuresNeverDisplayConnected() = runBlocking {
        val detector = CampusNetworkDetector({ throw java.io.IOException() }, { false })
        assertEquals(CampusNetworkStatus.NOT_CONNECTED, detector.check())
    }

    @Test fun cancellationDoesNotFallBackToDirectOrPublishSuccess() = runBlocking {
        var directChecked = false
        val detector = CampusNetworkDetector({ throw CancellationException() }, { directChecked = true; true })
        try { detector.check(); fail("Cancellation must propagate") }
        catch (_: CancellationException) { assertFalse(directChecked) }
    }

    @Test fun laterFailedRecheckReplacesEarlierSuccess() = runBlocking {
        var reachable = true
        val detector = CampusNetworkDetector({ reachable }, { false })
        assertEquals(CampusNetworkStatus.VPN_CONNECTED, detector.check())
        reachable = false
        assertEquals(CampusNetworkStatus.NOT_CONNECTED, detector.check())
    }

    @Test fun vpnIsPreferredOnBothCampusAndExternalNetworks() {
        val vpn = requireNotNull(SicauJiaowuRoute.restore("/https/" + "c".repeat(64)))
        listOf(true, false).forEach { campus ->
            assertSame(vpn, AcademicRoutePreference.select(campus, vpnVerified = true, vpn))
        }
    }

    @Test fun savedRouteWithoutVerificationOnlyAllowsExplicitCampusDirect() {
        val vpn = requireNotNull(SicauJiaowuRoute.restore("/https/" + "d".repeat(64)))
        assertNull(AcademicRoutePreference.select(false, vpnVerified = false, vpn))
        assertSame(SicauJiaowuRoute.Direct, AcademicRoutePreference.select(true, vpnVerified = false, vpn))
    }

    @Test fun vanishedVerifiedVpnDoesNotSilentlyDowngradeToDirect() {
        assertNull(AcademicRoutePreference.select(true, vpnVerified = true, null))
        assertNull(AcademicRoutePreference.select(false, vpnVerified = true, null))
        assertNull(AcademicRoutePreference.select(true, vpnVerified = true, SicauJiaowuRoute.Direct))
    }
}

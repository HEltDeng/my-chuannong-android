package com.mychuannong.app.school

import kotlinx.coroutines.CancellationException

internal enum class CampusNetworkStatus { CHECKING, VPN_CONNECTED, CAMPUS_DIRECT, NOT_CONNECTED }

/** Reachability is proven against the academic login page, never inferred from Wi-Fi or a saved URL. */
internal class CampusNetworkDetector(
    private val vpnConnected: suspend () -> Boolean,
    private val directReachable: suspend () -> Boolean,
) {
    suspend fun check(): CampusNetworkStatus = when {
        attempt(vpnConnected) -> CampusNetworkStatus.VPN_CONNECTED
        attempt(directReachable) -> CampusNetworkStatus.CAMPUS_DIRECT
        else -> CampusNetworkStatus.NOT_CONNECTED
    }

    private suspend fun attempt(probe: suspend () -> Boolean): Boolean = try {
        probe()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }
}

/** VPN wins on either network; a missing/expired VPN never silently changes a chosen VPN route. */
internal object AcademicRoutePreference {
    fun select(campusSelected: Boolean, vpnVerified: Boolean, vpnRoute: SicauJiaowuRoute?): SicauJiaowuRoute? = when {
        vpnVerified -> vpnRoute?.takeIf { it.isWebVpn }
        campusSelected -> SicauJiaowuRoute.Direct
        else -> null
    }
}

package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.campustools.RemoteAppVersion
import com.mychuannong.app.core.update.VersionCheckResult
import com.mychuannong.app.core.update.VersionPolicy
import com.mychuannong.app.core.update.AppUpdatePolicyState
import com.mychuannong.app.core.update.AppUpdateStateMachine
import com.mychuannong.app.core.update.UpdateTargetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun `release update target requires public https`() {
        assertTrue(UpdateTargetPolicy.isSafeHttps("https://download.mychangliapp.cn/app.apk"))
        assertFalse(UpdateTargetPolicy.isSafeHttps(null))
        assertFalse(UpdateTargetPolicy.isSafeHttps("http://download.mychangliapp.cn/app.apk"))
        assertFalse(UpdateTargetPolicy.isSafeHttps("https://campus-tools.invalid/app.apk"))
        assertFalse(UpdateTargetPolicy.isSafeHttps("https://127.0.0.1/app.apk"))
        assertFalse(UpdateTargetPolicy.isSafeHttps("https://192.168.1.20/app.apk"))
    }
    private fun version(
        latest: Int,
        minimum: Int = 1,
        force: Boolean = false,
    ) = RemoteAppVersion(
        latest_version_code = latest,
        minimum_version_code = minimum,
        latest_version_name = "1.0.$latest",
        force_update = force,
    )

    @Test
    fun currentOrOlderServerVersionIsLatest() {
        assertEquals(
            VersionCheckResult.Latest,
            VersionPolicy.evaluate(10, updateAvailable = false, latest = version(10)),
        )
        assertEquals(
            VersionCheckResult.Latest,
            VersionPolicy.evaluate(10, updateAvailable = true, latest = version(9)),
        )
    }

    @Test
    fun serverCanDisableUpdateEvenWhenAReleaseNumberIsNewer() {
        val result = VersionPolicy.evaluate(10, updateAvailable = false, latest = version(11))
        assertEquals(VersionCheckResult.Latest, result)
    }

    @Test
    fun newerVersionIsAvailableWithoutForcingNormalUpgrade() {
        val result = VersionPolicy.evaluate(10, true, version(11)) as VersionCheckResult.Available
        assertEquals(11, result.version.latest_version_code)
        assertFalse(result.mandatory)
    }

    @Test
    fun onlyTheCurrentServerDecisionMarksUpgradeMandatory() {
        assertFalse(
            (VersionPolicy.evaluate(10, true, version(11, force = true)) as VersionCheckResult.Available).mandatory,
        )
        assertFalse(
            (VersionPolicy.evaluate(10, true, version(11, minimum = 11)) as VersionCheckResult.Available).mandatory,
        )
        assertTrue(
            (
                VersionPolicy.evaluate(
                    10,
                    true,
                    version(11),
                    mandatoryUpdate = true,
                    policyRevision = 21,
                ) as VersionCheckResult.Available
                ).mandatory,
        )
    }

    @Test
    fun forceDecisionCanTransitionInBothDirections() {
        val optional = VersionPolicy.evaluate(
            10, true, version(11), mandatoryUpdate = false, policyRevision = 20,
        ) as VersionCheckResult.Available
        val forced = VersionPolicy.evaluate(
            10, true, version(11), mandatoryUpdate = true, policyRevision = 21,
        ) as VersionCheckResult.Available
        val revoked = VersionPolicy.evaluate(
            10, true, version(11), mandatoryUpdate = false, policyRevision = 22,
        ) as VersionCheckResult.Available
        assertFalse(optional.mandatory)
        assertTrue(forced.mandatory)
        assertFalse(revoked.mandatory)
        assertEquals(22, revoked.policyRevision)
        assertTrue(AppUpdateStateMachine.resolve(forced, optional) is AppUpdatePolicyState.FORCE_UPDATE)
        assertTrue(AppUpdateStateMachine.resolve(revoked, forced) is AppUpdatePolicyState.OPTIONAL_UPDATE)
        assertTrue(
            AppUpdateStateMachine.resolve(VersionCheckResult.Latest, revoked) is
                AppUpdatePolicyState.NO_UPDATE,
        )
        // A failed refresh keeps the previous value only for diagnostics; the
        // ERROR state itself is never rendered as a cached force gate.
        assertTrue(
            AppUpdateStateMachine.resolve(VersionCheckResult.Unavailable, forced) is
                AppUpdatePolicyState.ERROR,
        )
    }
}

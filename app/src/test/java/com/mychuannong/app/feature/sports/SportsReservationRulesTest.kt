package com.mychuannong.app.feature.sports

import com.mychuannong.app.core.release.V1ReleasePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sports models remain in the backend contract, but V1 has no executable booking/payment UI. */
class SportsReservationRulesTest {
    @Test
    fun `real sports reservation is hard closed`() {
        assertTrue(V1ReleasePolicy.isHardClosed("sports_reservation"))
    }

    @Test
    fun `real payment is hard closed`() {
        assertTrue(V1ReleasePolicy.isHardClosed("real_payment"))
        assertFalse(V1ReleasePolicy.isHardClosed("lost_found"))
    }
}

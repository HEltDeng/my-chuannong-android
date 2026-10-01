package com.mychuannong.app.core.main.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainTabDestinationTest {

    @Test
    fun `formal navigation contains exactly three stable tabs`() {
        assertEquals(
            listOf(
                MainTabDestination.Overview,
                MainTabDestination.Feature,
                MainTabDestination.Profile
            ),
            MainTabDestination.ordered
        )
        assertEquals(listOf(0, 1, 2), MainTabDestination.ordered.map { it.index })
    }

    @Test
    fun `removed community index cannot resolve to a main tab`() {
        assertNull(MainTabDestination.fromIndex(3))
    }
}

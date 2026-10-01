package com.mychuannong.app.core

import com.mychuannong.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {

    @Test
    fun buildUsesVariantAppropriateApplicationId() {
        val expected = if (BuildConfig.DEBUG) {
            "com.mychuannong.app.debug"
        } else {
            "com.mychuannong.app"
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }
}

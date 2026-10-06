package com.mychuannong.app.core

import com.mychuannong.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {

    @Test
    fun buildUsesVariantAppropriateApplicationId() {
        val expected = when(BuildConfig.BUILD_TYPE) {
            "debug" -> "com.mychuannong.app.debug"
            "validation" -> "com.mychuannong.app.validation"
            else -> "com.mychuannong.app"
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }
}

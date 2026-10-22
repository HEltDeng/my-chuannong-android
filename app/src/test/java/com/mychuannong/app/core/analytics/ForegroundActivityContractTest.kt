package com.mychuannong.app.core.analytics

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mychuannong.app.core.network.campustools.ForegroundActivityRequest
import com.mychuannong.app.core.network.campustools.ForegroundActivityResult
import org.junit.Assert.*
import org.junit.Test

class ForegroundActivityContractTest {
    @Test fun wireContractContainsOnlyDisclosedDeviceFields() {
        val payload = ForegroundActivityRequest("synthetic-install", "a".repeat(64), "1.0.0", 10, 3,
            "Synthetic", "Test model", "15", 35)
        val json = Gson().toJsonTree(payload).asJsonObject
        assertEquals(setOf("installationId", "deviceScopedId", "versionName", "versionCode", "consentVersion",
            "manufacturer", "model", "androidVersion", "sdkInt"), json.keySet())
        assertEquals("Test model", json["model"].asString)
        // These wire fields must keep their names when release builds are obfuscated.
        for (type in listOf(ForegroundActivityRequest::class.java, ForegroundActivityResult::class.java)) {
            type.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach {
                assertNotNull(it.name, it.getAnnotation(SerializedName::class.java))
            }
        }
    }
}

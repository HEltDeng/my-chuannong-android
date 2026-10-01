package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.campustools.ApiEnvelope
import com.mychuannong.app.core.network.campustools.AuthData
import com.mychuannong.app.core.network.campustools.CreateReservationRequest
import com.mychuannong.app.core.network.campustools.DeleteAccountRequest
import com.mychuannong.app.core.network.campustools.DeviceAuthRequest
import com.mychuannong.app.core.network.campustools.FeedbackRequest
import com.mychuannong.app.core.network.campustools.LoginRequest
import com.mychuannong.app.core.network.campustools.ProfileRequest
import com.mychuannong.app.core.network.campustools.RegisterRequest
import com.mychuannong.app.core.network.campustools.ReportRequest
import com.mychuannong.app.core.network.campustools.ReservationProfileRequest
import com.mychuannong.app.core.network.campustools.ResourceComplaintRequest
import com.mychuannong.app.core.network.campustools.UpsertLostFoundRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusToolsAuthContractTest {
    private val gson = Gson()

    @Test
    fun deviceIdentityUsesOnlyTheOpaqueInstallationIdentifier() {
        val request = gson.toJsonTree(DeviceAuthRequest("installation-id")).asJsonObject
        assertEquals(setOf("installationId"), request.keySet())
        assertFalse(request.has("phone"))
        assertFalse(request.has("password"))
    }

    @Test
    fun v104IdentityMetadataIsOptionalForLegacyClientsAndExplicitForNewClients() {
        val request = gson.toJsonTree(
            DeviceAuthRequest(
                installationId = "installation-id",
                deviceScopedId = "a".repeat(64),
                versionName = "1.0.4",
                versionCode = 36,
            ),
        ).asJsonObject
        assertEquals(
            setOf("installationId", "deviceScopedId", "versionName", "versionCode"),
            request.keySet(),
        )
        assertTrue(request.get("deviceScopedId").asString.matches(Regex("[0-9a-f]{64}")))
        assertEquals(36, request.get("versionCode").asInt)
    }

    @Test
    fun registrationAndLoginRequestsUseBackendContractWithoutCredentialLoggingFields() {
        val register = gson.toJsonTree(
            RegisterRequest("19100000000", "local-test-password", "长理同学", "installation-id")
        ).asJsonObject
        assertEquals(setOf("phone", "password", "nickname", "installationId"), register.keySet())

        val login = gson.toJsonTree(
            LoginRequest("19100000000", "local-test-password", "installation-id")
        ).asJsonObject
        assertEquals(setOf("phone", "password", "installationId"), login.keySet())
        assertFalse(login.has("studentId"))
        assertFalse(login.has("schoolPassword"))
        assertFalse(login.has("cookie"))
    }

    @Test
    fun everyProductionRequestFieldHasAStableGsonName() {
        val requests = listOf(
            DeviceAuthRequest("installation-id") to setOf("installationId"),
            RegisterRequest("19100000000", "local-test-password", "长理同学", "installation-id") to
                setOf("phone", "password", "nickname", "installationId"),
            LoginRequest("19100000000", "local-test-password", "installation-id") to
                setOf("phone", "password", "installationId"),
            ProfileRequest("长理同学") to setOf("nickname"),
            DeleteAccountRequest("local-test-password") to setOf("password"),
            FeedbackRequest("OTHER", "测试标题", "测试内容", "1.0.0") to
                setOf("category", "title", "content", "appVersion"),
            ReservationProfileRequest("test-student-id", "测试用户") to
                setOf("studentId", "name", "consented"),
            CreateReservationRequest("venue-id", "2026-08-22", "10:00-11:00", 1) to
                setOf("venueId", "date", "timeSlot", "courtNumber"),
            ResourceComplaintRequest("OTHER", "测试说明") to setOf("reason", "detail"),
            UpsertLostFoundRequest(
                clientLocalId = "local-id",
                type = "LOST",
                itemName = "测试物品",
                category = "OTHER",
                location = "测试地点",
                eventDate = "2026-08-22",
                approximateTime = "10:00",
                imageBase64 = "test-image",
                status = "OPEN",
            ) to setOf(
                "clientLocalId", "type", "itemName", "category", "location", "eventDate",
                "approximateTime", "description", "claimNote", "imageBase64", "status",
            ),
            ReportRequest("测试原因") to setOf("reason"),
        )

        requests.forEach { (request, expectedNames) ->
            val json = gson.toJsonTree(request).asJsonObject
            assertEquals(request.javaClass.simpleName, expectedNames, json.keySet())

            request.javaClass.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    val annotation = field.getAnnotation(SerializedName::class.java)
                    assertNotNull("${request.javaClass.simpleName}.${field.name}", annotation)
                    assertEquals(field.name, annotation?.value)
                }
        }
    }

    @Test
    fun successfulAuthEnvelopeRestoresPhoneAccountAndOpaqueToken() {
        val envelopeType = object : TypeToken<ApiEnvelope<AuthData>>() {}.type
        val envelope: ApiEnvelope<AuthData> = gson.fromJson(
            """
            {"ok":true,"data":{"token":"opaque-local-token","user":{
              "id":"internal-user-id","role":"USER","accountType":"PHONE",
              "nickname":"长理同学","phoneMasked":"191****0000","accountStatus":"ACTIVE"
            }}}
            """.trimIndent(),
            envelopeType,
        )
        assertTrue(envelope.ok)
        val auth = requireNotNull(envelope.data)
        assertEquals("internal-user-id", auth.user.id)
        assertEquals("PHONE", auth.user.accountType)
        assertEquals("opaque-local-token", auth.token)
    }

    @Test
    fun authenticationResponseFieldsHaveStableGsonNames() {
        val responseTypes = listOf(
            ApiEnvelope::class.java,
            com.mychuannong.app.core.network.campustools.ApiErrorBody::class.java,
            AuthData::class.java,
            com.mychuannong.app.core.network.campustools.AuthUser::class.java,
        )

        responseTypes.forEach { type ->
            type.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    val annotation = field.getAnnotation(SerializedName::class.java)
                    assertNotNull("${type.simpleName}.${field.name}", annotation)
                    assertEquals(field.name, annotation?.value)
                }
        }
    }
}

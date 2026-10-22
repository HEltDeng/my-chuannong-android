package com.mychuannong.app.core.network.campustools

import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.config.AppConfigPayload
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.security.CredentialCipher
import com.mychuannong.app.core.network.ServiceEndpointPolicy
import com.mychuannong.app.core.network.ServiceUnavailableException
import com.mychuannong.app.core.network.UnavailableEndpointInterceptor
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.tencent.mmkv.MMKV
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ApiEnvelope<T>(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: ApiErrorBody? = null,
)

data class ApiErrorBody(
    @SerializedName("code") val code: String = "",
    @SerializedName("message") val message: String = "",
)
class CampusToolsApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String,
) : IllegalStateException(message)
data class DeviceAuthRequest(
    @SerializedName("installationId") val installationId: String,
    @SerializedName("deviceScopedId") val deviceScopedId: String? = null,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("versionCode") val versionCode: Int? = null,
)
data class ForegroundActivityRequest(
    @SerializedName("installationId") val installationId: String,
    @SerializedName("deviceScopedId") val deviceScopedId: String?,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("consentVersion") val consentVersion: Int,
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("model") val model: String,
    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("sdkInt") val sdkInt: Int,
)
data class ForegroundActivityResult(@SerializedName("accepted") val accepted: Boolean, @SerializedName("day") val day: String)
data class AuthData(
    @SerializedName("token") val token: String,
    @SerializedName("user") val user: AuthUser,
)
data class AuthUser(
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String,
    @SerializedName("accountType") val accountType: String = "DEVICE",
    @SerializedName("userCode") val userCode: String? = null,
    @SerializedName("nickname") val nickname: String = "川农同学",
    @SerializedName("phoneMasked") val phoneMasked: String? = null,
    @SerializedName("studentId") val studentId: String? = null,
    @SerializedName("realName") val realName: String? = null,
    @SerializedName("campusVerified") val campusVerified: Boolean = false,
    @SerializedName("campusVerifiedAt") val campusVerifiedAt: Long? = null,
    @SerializedName("reservationStudentId") val reservationStudentId: String? = null,
    @SerializedName("reservationName") val reservationName: String? = null,
    @SerializedName("reservationProfileConsentedAt") val reservationProfileConsentedAt: Long? = null,
    @SerializedName("reservationProfileVersion") val reservationProfileVersion: Int = 0,
    @SerializedName("reservationProfileUpdatedAt") val reservationProfileUpdatedAt: Long? = null,
    @SerializedName("accountStatus") val accountStatus: String = "ACTIVE",
    @SerializedName("accountStatusReason") val accountStatusReason: String = "",
    @SerializedName("accountStatusExpiresAt") val accountStatusExpiresAt: Long? = null,
)
data class RegisterRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("installationId") val installationId: String,
    @SerializedName("deviceScopedId") val deviceScopedId: String? = null,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("versionCode") val versionCode: Int? = null,
)
data class LoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String,
    @SerializedName("installationId") val installationId: String,
    @SerializedName("deviceScopedId") val deviceScopedId: String? = null,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("versionCode") val versionCode: Int? = null,
)
data class ProfileRequest(@SerializedName("nickname") val nickname: String)
data class DeleteAccountRequest(@SerializedName("password") val password: String)
data class DeleteAccountResult(val deleted: Boolean)
data class LogoutResult(val loggedOut: Boolean)
data class FeedbackRequest(
    @SerializedName("category") val category: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("appVersion") val appVersion: String,
)
data class FeedbackResult(
    val id: String,
    val status: String,
    val workflowStatus: String = status,
)
data class RemoteFeedbackEvent(
    val action: String,
    val oldStatus: String? = null,
    val newStatus: String? = null,
    val message: String = "",
    val createdAt: Long,
)
data class RemoteFeedback(
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val status: String,
    val publicReply: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val events: List<RemoteFeedbackEvent> = emptyList(),
)
data class ReservationProfileRequest(
    @SerializedName("studentId") val studentId: String,
    @SerializedName("name") val name: String,
    @SerializedName("consented") val consented: Boolean = true,
)
data class ReservationRules(
    val maxActivePerDay: Int = 2,
    val maxUserCancellationsPerDay: Int = 2,
    val cancelCutoffMinutes: Int = 30,
    val restrictionHoursAfterExcessCancel: Int = 24,
    val requireReservationStudentId: Boolean = false,
    val reservationProfileMode: String = if (requireReservationStudentId) "REQUIRED" else "OPTIONAL",
    val updatedAt: Long = 0,
)
data class ReservationTodayUsage(
    val activeReservations: Int = 0,
    val remainingReservations: Int = 0,
    val userCancellations: Int = 0,
    val remainingCancellations: Int = 0,
)
data class ReservationRestriction(
    val reservationBlocked: Boolean = false,
    val cancellationBlocked: Boolean = false,
    val reservationBlockedUntil: Long? = null,
    val cancellationBlockedUntil: Long? = null,
    val reason: String = "",
)
data class ReservationProfile(
    val studentId: String? = null,
    val name: String? = null,
    val consentedAt: Long? = null,
    val version: Int = 0,
    val updatedAt: Long? = null,
)
data class ReservationContext(
    val rules: ReservationRules = ReservationRules(),
    val today: ReservationTodayUsage = ReservationTodayUsage(),
    val restriction: ReservationRestriction = ReservationRestriction(),
    val profile: ReservationProfile = ReservationProfile(),
)

data class RemoteVenue(
    val id: String,
    val sport: String,
    val name: String,
    val unitLabel: String,
    val courtCount: Int,
    val priceYuan: Int,
    val timeSlots: List<String>,
)

data class RemoteCourtAvailability(val courtNumber: Int, val status: String)
data class RemoteSlotAvailability(
    val timeSlot: String,
    val courts: List<RemoteCourtAvailability>,
)
data class RemoteAvailability(
    val venueId: String,
    val date: String,
    val slots: List<RemoteSlotAvailability>,
)

data class CreateReservationRequest(
    @SerializedName("venueId") val venueId: String,
    @SerializedName("date") val date: String,
    @SerializedName("timeSlot") val timeSlot: String,
    @SerializedName("courtNumber") val courtNumber: Int,
)

data class RemoteReservation(
    val id: String,
    val sport: String,
    val venueId: String,
    val venueName: String,
    val unitLabel: String,
    val date: String,
    val timeSlot: String,
    val courtNumber: Int,
    val priceYuan: Int,
    val status: String,
    val paymentStatus: String,
    val refundStatus: String = "NOT_APPLICABLE",
    val verificationStatus: String = "NOT_REQUIRED",
    val cancelledAtMillis: Long? = null,
    val cancelledBy: String? = null,
    val cancelReason: String? = null,
    val reservationStudentIdSnapshot: String? = null,
    val reservationNameSnapshot: String? = null,
    val canCancel: Boolean = false,
    val cancelUnavailableReason: String = "",
    val nextCancellationCount: Int? = null,
    val remainingCancellationsAfterCancel: Int? = null,
    val createdAtMillis: Long,
)

data class RemoteResource(
    val id: String,
    val title: String,
    val summary: String = "",
    val category: String,
    val tags: List<String> = emptyList(),
    val audienceScope: String = "",
    val publisherLabel: String = "我的川农资料库",
    val sourceLabel: String = "",
    val usageNote: String = "",
    val resourceType: String = "EXTERNAL_LINK",
    val externalUrl: String,
    val coverUrl: String? = null,
    val isFree: Boolean = true,
    val futurePriceFen: Int? = null,
    val publishedAt: Long? = null,
    val isFavorite: Boolean = false,
    val viewCount: Int = 0,
    val lastViewedAt: Long? = null,
)
data class ResourceActionResult(
    val resourceId: String,
    val isFavorite: Boolean = false,
    val viewedAt: Long? = null,
)
data class ResourceComplaintRequest(
    @SerializedName("reason") val reason: String,
    @SerializedName("detail") val detail: String = "",
)
data class ResourceComplaintResult(val id: String, val status: String)

data class RemoteLostFoundPost(
    val id: String,
    val clientLocalId: String? = null,
    val type: String,
    val itemName: String,
    val category: String,
    val location: String,
    val eventDate: String,
    val approximateTime: String? = null,
    val description: String = "",
    val claimNote: String = "",
    val imageUrl: String? = null,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isMine: Boolean,
    val isFavorite: Boolean,
    val hasClaimRequest: Boolean,
)

data class UpsertLostFoundRequest(
    @SerializedName("clientLocalId") val clientLocalId: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("itemName") val itemName: String,
    @SerializedName("category") val category: String,
    @SerializedName("location") val location: String,
    @SerializedName("eventDate") val eventDate: String,
    @SerializedName("approximateTime") val approximateTime: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("claimNote") val claimNote: String = "",
    @SerializedName("imageBase64") val imageBase64: String? = null,
    @SerializedName("status") val status: String,
)

data class FavoriteResult(val postId: String, val isFavorite: Boolean)
data class DeleteResult(val deleted: Boolean, val id: String)
data class ClaimResult(val postId: String, val submitted: Boolean, val message: String)
data class ReportRequest(@SerializedName("reason") val reason: String)
data class ReportResult(val postId: String, val recorded: Boolean)
data class RemoteAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val publishedAt: Long,
    val notificationEnabled: Boolean = false,
    val popupEnabled: Boolean = false,
)
data class NotificationFollow(
    val module: String,
    val title: String,
    val following: Boolean,
    val followedAt: Long? = null,
)
data class NotificationFollows(val accountId: String, val items: List<NotificationFollow>)
data class NotificationFollowResult(
    val accountId: String, val module: String, val title: String,
    val following: Boolean, val followedAt: Long? = null,
)
data class NotificationFollowRequest(val following: Boolean)
data class FollowedNotice(
    val id: String, val module: String, val title: String, val content: String,
    val publishedAt: Long, val notificationEnabled: Boolean, val popupEnabled: Boolean,
)
data class NotificationFeed(val accountId: String, val items: List<FollowedNotice>)

data class RemoteFeatureFlag(
    val key: String,
    val enabled: Boolean,
    val updatedAt: Long? = null,
    val payload: RemoteFeaturePayload = RemoteFeaturePayload(),
)
data class RemoteFeaturePayload(
    val status: String = "OPEN",
    val maintenanceNote: String = "",
    val expectedRestoreAt: Long? = null,
)
data class RemoteAppVersion(
    val latest_version_code: Int,
    val minimum_version_code: Int,
    val latest_version_name: String,
    val update_message: String = "",
    val download_url: String? = null,
    val apk_sha256: String? = null,
    val apk_size: Long? = null,
    val package_name: String? = null,
    val signing_certificate_sha256: String? = null,
    val metadata_verified: Boolean = false,
    val force_update: Boolean = false,
    val published_at: Long? = null,
)
data class RemoteUpdatePolicy(
    val updateEnabled: Boolean = false,
    val forceUpdateEnabled: Boolean = false,
    val latestVersionCode: Int = 0,
    val minimumSupportedVersionCode: Int = 1,
    val policyRevision: Long = 0,
    val updatedAt: Long? = null,
)
data class VersionCheckResponse(
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val minimumVersionViolated: Boolean = false,
    val mandatoryUpdate: Boolean = false,
    val currentVersionCode: Int = 0,
    val latestVersion: RemoteAppVersion? = null,
    val policy: RemoteUpdatePolicy? = null,
    val policyRevision: Long = 0,
    val policyUpdatedAt: Long? = null,
    val requestTimestamp: Long? = null,
    val decision: String = "NONE",
)
data class PublicBootstrap(
    val flags: List<RemoteFeatureFlag> = emptyList(),
    val announcements: List<RemoteAnnouncement> = emptyList(),
    val version: RemoteAppVersion? = null,
    val updatePolicy: RemoteUpdatePolicy? = null,
    @SerializedName("schemaVersion") val schemaVersion: Int? = null,
    @SerializedName("configVersion") val configVersion: Long? = null,
    @SerializedName("minAppVersionCode") val minAppVersionCode: Int? = null,
    @SerializedName("maxAppVersionCode") val maxAppVersionCode: Int? = null,
    @SerializedName("issuedAt") val issuedAt: Long? = null,
    @SerializedName("expiresAt") val expiresAt: Long? = null,
    @SerializedName("configHash") val configHash: String? = null,
    @SerializedName("config") val config: AppConfigPayload? = null,
)

data class RemoteServiceUpdate(
    val id: String,
    val type: String,
    val priority: Int = 0,
    val title: String = "",
    val summary: String = "",
    val targetType: String = "",
    val targetId: String? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val forceUpdate: Boolean? = null,
    val minimumVersionCode: Int? = null,
    val moduleKey: String? = null,
    val recoverAt: Long? = null,
    val publishedAt: Long? = null,
    val dismissible: Boolean = false,
    val category: String? = null,
    val imageUrl: String? = null,
    val targetUrl: String? = null,
    val sortOrder: Int = 0,
)

interface CampusToolsApi {
    @GET suspend fun contentGet(@retrofit2.http.Url path: String): ApiEnvelope<com.google.gson.JsonElement>
    @POST suspend fun contentPost(@retrofit2.http.Url path: String, @Body body: com.google.gson.JsonObject): ApiEnvelope<com.google.gson.JsonElement>
    @PATCH suspend fun contentPatch(@retrofit2.http.Url path: String, @Body body: com.google.gson.JsonObject): ApiEnvelope<com.google.gson.JsonElement>
    @retrofit2.http.PUT suspend fun contentPut(@retrofit2.http.Url path: String, @Body body: com.google.gson.JsonObject): ApiEnvelope<com.google.gson.JsonElement>
    @DELETE suspend fun contentDelete(@retrofit2.http.Url path: String): ApiEnvelope<com.google.gson.JsonElement>

    @GET("notifications/follows")
    suspend fun notificationFollows(): ApiEnvelope<NotificationFollows>

    @retrofit2.http.PUT("notifications/follows/{module}")
    suspend fun setNotificationFollow(
        @Path("module") module: String,
        @Body request: NotificationFollowRequest,
    ): ApiEnvelope<NotificationFollowResult>

    @GET("notifications/feed")
    suspend fun notificationFeed(): ApiEnvelope<NotificationFeed>

    @POST("auth/device")
    suspend fun deviceAuth(@Body request: DeviceAuthRequest): ApiEnvelope<AuthData>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiEnvelope<AuthData>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<AuthData>

    @GET("auth/me")
    suspend fun me(): ApiEnvelope<AuthUser>

    @POST("activity/foreground")
    suspend fun foreground(@Body request: ForegroundActivityRequest): ApiEnvelope<ForegroundActivityResult>

    @PATCH("auth/profile")
    suspend fun updateProfile(@Body request: ProfileRequest): ApiEnvelope<AuthUser>

    @GET("auth/reservation-profile")
    suspend fun reservationProfile(): ApiEnvelope<ReservationContext>

    @retrofit2.http.PUT("auth/reservation-profile")
    suspend fun updateReservationProfile(
        @Body request: ReservationProfileRequest,
    ): ApiEnvelope<ReservationContext>

    @DELETE("auth/reservation-profile")
    suspend fun deleteReservationProfile(): ApiEnvelope<ReservationContext>

    @POST("auth/logout")
    suspend fun logout(@Body empty: Map<String, String> = emptyMap()): ApiEnvelope<LogoutResult>

    @HTTP(method = "DELETE", path = "auth/account", hasBody = true)
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest,
    ): ApiEnvelope<DeleteAccountResult>

    @GET("public/bootstrap")
    suspend fun publicBootstrap(
        @Query("schemaVersion") schemaVersion: Int? = null,
        @Query("appVersionCode") appVersionCode: Int? = null,
    ): ApiEnvelope<PublicBootstrap>

    @GET("public/version/android")
    suspend fun versionCheck(
        @Query("currentVersionCode") currentVersionCode: Int,
        @Query("cacheBust") cacheBust: Long? = null,
    ): ApiEnvelope<VersionCheckResponse>

    @GET("public/service-updates")
    suspend fun serviceUpdates(): ApiEnvelope<List<RemoteServiceUpdate>>

    @GET("sports/venues")
    suspend fun venues(): ApiEnvelope<List<RemoteVenue>>

    @GET("sports/context")
    suspend fun reservationContext(): ApiEnvelope<ReservationContext>

    @GET("sports/availability")
    suspend fun availability(
        @Query("venueId") venueId: String,
        @Query("date") date: String,
    ): ApiEnvelope<RemoteAvailability>

    @GET("sports/reservations")
    suspend fun reservations(): ApiEnvelope<List<RemoteReservation>>

    @POST("sports/reservations")
    suspend fun createReservation(
        @Body request: CreateReservationRequest,
    ): ApiEnvelope<RemoteReservation>

    @POST("sports/reservations/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String,
        @Body empty: Map<String, String> = emptyMap(),
    ): ApiEnvelope<RemoteReservation>

    @GET("lost-found")
    suspend fun lostFound(
        @Query("type") type: String? = null,
        @Query("q") query: String? = null,
        @Query("mine") mine: Boolean? = null,
    ): ApiEnvelope<List<RemoteLostFoundPost>>

    @POST("lost-found")
    suspend fun createLostFound(
        @Body request: UpsertLostFoundRequest,
    ): ApiEnvelope<RemoteLostFoundPost>

    @PATCH("lost-found/{id}")
    suspend fun updateLostFound(
        @Path("id") id: String,
        @Body request: UpsertLostFoundRequest,
    ): ApiEnvelope<RemoteLostFoundPost>

    @DELETE("lost-found/{id}")
    suspend fun deleteLostFound(@Path("id") id: String): ApiEnvelope<DeleteResult>

    @POST("lost-found/{id}/favorite")
    suspend fun toggleFavorite(
        @Path("id") id: String,
        @Body empty: Map<String, String> = emptyMap(),
    ): ApiEnvelope<FavoriteResult>

    @POST("lost-found/{id}/claim")
    suspend fun claim(
        @Path("id") id: String,
        @Body empty: Map<String, String> = emptyMap(),
    ): ApiEnvelope<ClaimResult>

    @POST("lost-found/{id}/report")
    suspend fun report(
        @Path("id") id: String,
        @Body request: ReportRequest,
    ): ApiEnvelope<ReportResult>

    @POST("feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): ApiEnvelope<FeedbackResult>

    @GET("feedback")
    suspend fun feedback(): ApiEnvelope<List<RemoteFeedback>>

    @GET("feedback/{id}")
    suspend fun feedback(@Path("id") id: String): ApiEnvelope<RemoteFeedback>

    @GET("library")
    suspend fun resources(
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
        @Query("favorites") favorites: Boolean? = null,
        @Query("recent") recent: Boolean? = null,
    ): ApiEnvelope<List<RemoteResource>>

    @GET("library/{id}")
    suspend fun resource(@Path("id") id: String): ApiEnvelope<RemoteResource>

    @POST("library/{id}/view")
    suspend fun recordResourceView(
        @Path("id") id: String,
        @Body empty: Map<String, String> = emptyMap(),
    ): ApiEnvelope<ResourceActionResult>

    @POST("library/{id}/favorite")
    suspend fun toggleResourceFavorite(
        @Path("id") id: String,
        @Body empty: Map<String, String> = emptyMap(),
    ): ApiEnvelope<ResourceActionResult>

    @POST("library/{id}/complaints")
    suspend fun complainResource(
        @Path("id") id: String,
        @Body request: ResourceComplaintRequest,
    ): ApiEnvelope<ResourceComplaintResult>
}

sealed interface AccountSessionState {
    data object Loading : AccountSessionState
    data object SignedOut : AccountSessionState
    data class SignedIn(
        val user: AuthUser,
        val offline: Boolean = false,
    ) : AccountSessionState
}

object CampusToolsSession {
    private const val STORE_ID = "campus_tools_session_v1"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_DEVICE_TOKEN = "device_access_token"
    private const val KEY_USER = "account_user"
    private val mutex = Mutex()
    private val store by lazy { MMKV.mmkvWithID(STORE_ID) }
    private val gson = Gson()
    private val mutableState = MutableStateFlow<AccountSessionState>(AccountSessionState.Loading)
    val state: StateFlow<AccountSessionState> = mutableState.asStateFlow()
    val currentUser: AuthUser?
        get() = cachedUser

    val installationId: String
        get() = store.decodeString(KEY_INSTALLATION_ID)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also {
                check(store.encode(KEY_INSTALLATION_ID, it))
            }

    var token: String?
        get() {
            val stored = store.decodeString(KEY_TOKEN)?.takeIf(String::isNotBlank) ?: return null
            if (CredentialCipher.isEncrypted(stored)) {
                return runCatching { CredentialCipher.decrypt(stored) }.getOrNull()
                    ?: run {
                        store.removeValueForKey(KEY_TOKEN)
                        null
                    }
            }
            val migrated = runCatching { CredentialCipher.encrypt(stored) }.getOrNull()
            return if (!migrated.isNullOrBlank() && store.encode(KEY_TOKEN, migrated)) {
                stored
            } else {
                store.removeValueForKey(KEY_TOKEN)
                null
            }
        }
        private set(value) {
            if (value.isNullOrBlank()) store.removeValueForKey(KEY_TOKEN)
            else {
                val encrypted = CredentialCipher.encrypt(value)
                check(store.encode(KEY_TOKEN, encrypted))
            }
        }

    private var deviceToken: String?
        get() {
            val stored = store.decodeString(KEY_DEVICE_TOKEN)?.takeIf(String::isNotBlank) ?: return null
            if (CredentialCipher.isEncrypted(stored)) {
                return runCatching { CredentialCipher.decrypt(stored) }.getOrNull()
                    ?: run {
                        store.removeValueForKey(KEY_DEVICE_TOKEN)
                        null
                    }
            }
            val migrated = runCatching { CredentialCipher.encrypt(stored) }.getOrNull()
            return if (!migrated.isNullOrBlank() && store.encode(KEY_DEVICE_TOKEN, migrated)) {
                stored
            } else {
                store.removeValueForKey(KEY_DEVICE_TOKEN)
                null
            }
        }
        set(value) {
            if (value.isNullOrBlank()) store.removeValueForKey(KEY_DEVICE_TOKEN)
            else {
                val encrypted = CredentialCipher.encrypt(value)
                check(store.encode(KEY_DEVICE_TOKEN, encrypted))
            }
        }

    internal val authorizationToken: String?
        get() = token ?: deviceToken

    private var cachedUser: AuthUser?
        get() = store.decodeString(KEY_USER)
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { gson.fromJson(it, AuthUser::class.java) }.getOrNull() }
        set(value) {
            if (value == null) store.removeValueForKey(KEY_USER)
            else check(store.encode(KEY_USER, gson.toJson(value)))
        }

    suspend fun restore() = mutex.withLock {
        val savedToken = token
        val savedUser = cachedUser?.takeIf {
            it.accountType == "PHONE" && it.accountStatus in setOf("ACTIVE", "LIMITED")
        }
        if (savedToken.isNullOrBlank()) {
            clearPhoneSession()
            ensureDeviceIdentityLocked()
            return@withLock
        }
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) {
            mutableState.value = savedUser?.let {
                AccountSessionState.SignedIn(it, offline = true)
            } ?: AccountSessionState.SignedOut
            return@withLock
        }
        if (savedUser != null) {
            mutableState.value = AccountSessionState.SignedIn(savedUser, offline = true)
        }
        try {
            val response = CampusToolsApiClient.authenticated.me()
            val user = response.data.takeIf { response.ok }
                ?: error(response.error?.message ?: "登录状态已失效")
            if (user.accountType != "PHONE" || user.accountStatus !in setOf("ACTIVE", "LIMITED")) {
                clearPhoneSession()
                ensureDeviceIdentityLocked()
                return@withLock
            }
            persistSignedIn(savedToken, user)
        } catch (cause: retrofit2.HttpException) {
            if (cause.code() == 401 || cause.code() == 403) {
                clearPhoneSession()
                ensureDeviceIdentityLocked()
            }
            else if (savedUser == null) mutableState.value = AccountSessionState.SignedOut
        } catch (_: Exception) {
            if (savedUser == null) mutableState.value = AccountSessionState.SignedOut
        }
    }

    suspend fun reportForeground(expectedAccount: String): Boolean = mutex.withLock {
        val context = PlanetApplication.appContext
        val user = currentUser
        if (!com.mychuannong.app.core.privacy.PrivacyConsentManager.hasAppConsent(context) ||
            user?.id != expectedAccount || user.accountType != "PHONE" || token.isNullOrBlank() ||
            !ServiceEndpointPolicy.isAccountServiceConfigured()) return@withLock false
        val response = CampusToolsApiClient.authenticated.foreground(ForegroundActivityRequest(
            installationId = installationId,
            deviceScopedId = deviceScopedId(),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            consentVersion = com.mychuannong.app.core.privacy.PrivacyConsentManager.CURRENT_APP_CONSENT_VERSION,
            manufacturer = android.os.Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }.take(80),
            model = android.os.Build.MODEL.orEmpty().ifBlank { "unknown" }.take(100),
            androidVersion = android.os.Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" }.take(40),
            sdkInt = android.os.Build.VERSION.SDK_INT,
        ))
        response.ok && response.data?.accepted == true
    }

    suspend fun register(
        phone: String,
        password: String,
        nickname: String,
    ): AuthUser = mutex.withLock {
        check(ServiceEndpointPolicy.isAccountServiceConfigured()) { ServiceEndpointPolicy.ACCOUNT_UNAVAILABLE }
        val response = try {
            CampusToolsApiClient.unauthenticated.register(
                RegisterRequest(
                    phone = phone.trim(),
                    password = password,
                    nickname = nickname.trim(),
                    installationId = installationId,
                    deviceScopedId = deviceScopedId(),
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            )
        } catch (cause: Throwable) {
            error(cause.apiMessage("注册失败，请检查网络后重试"))
        }
        val auth = response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "注册失败")
        persistSignedIn(auth.token, auth.user)
        auth.user
    }

    suspend fun login(phone: String, password: String): AuthUser = mutex.withLock {
        check(ServiceEndpointPolicy.isAccountServiceConfigured()) { ServiceEndpointPolicy.ACCOUNT_UNAVAILABLE }
        val response = try {
            CampusToolsApiClient.unauthenticated.login(
                LoginRequest(
                    phone = phone.trim(),
                    password = password,
                    installationId = installationId,
                    deviceScopedId = deviceScopedId(),
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            )
        } catch (cause: Throwable) {
            error(cause.apiMessage("登录失败，请检查手机号、密码和网络"))
        }
        val auth = response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "登录失败")
        persistSignedIn(auth.token, auth.user)
        auth.user
    }

    suspend fun updateNickname(nickname: String): AuthUser = mutex.withLock {
        val response = try {
            CampusToolsApiClient.authenticated.updateProfile(ProfileRequest(nickname.trim()))
        } catch (cause: Throwable) {
            error(cause.apiMessage("昵称更新失败"))
        }
        val user = response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "昵称更新失败")
        persistSignedIn(requireNotNull(token), user)
        user
    }

    suspend fun updateReservationProfile(
        studentId: String,
        name: String,
    ): ReservationContext = mutex.withLock {
        val response = try {
            CampusToolsApiClient.authenticated.updateReservationProfile(
                ReservationProfileRequest(
                    studentId = studentId.trim(),
                    name = name.trim(),
                )
            )
        } catch (cause: Throwable) {
            error(cause.apiMessage("预约资料保存失败"))
        }
        val context = response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "预约资料保存失败")
        cachedUser?.let { current ->
            val updated = current.copy(
                reservationStudentId = context.profile.studentId,
                reservationName = context.profile.name,
                reservationProfileConsentedAt = context.profile.consentedAt,
                reservationProfileVersion = context.profile.version,
                reservationProfileUpdatedAt = context.profile.updatedAt,
            )
            persistSignedIn(requireNotNull(token), updated)
        }
        context
    }

    suspend fun deleteReservationProfile(): ReservationContext = mutex.withLock {
        val response = try {
            CampusToolsApiClient.authenticated.deleteReservationProfile()
        } catch (cause: Throwable) {
            error(cause.apiMessage("预约资料删除失败"))
        }
        val context = response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "预约资料删除失败")
        cachedUser?.let { current ->
            persistSignedIn(
                requireNotNull(token),
                current.copy(
                    reservationStudentId = null,
                    reservationName = null,
                    reservationProfileConsentedAt = null,
                    reservationProfileVersion = 0,
                    reservationProfileUpdatedAt = context.profile.updatedAt,
                ),
            )
        }
        context
    }

    suspend fun logout() = mutex.withLock {
        runCatching { CampusToolsApiClient.authenticated.logout() }
        clearPhoneSession()
        ensureDeviceIdentityLocked()
    }

    suspend fun deleteAccount(password: String) = mutex.withLock {
        val response = try {
            CampusToolsApiClient.authenticated.deleteAccount(DeleteAccountRequest(password))
        } catch (cause: Throwable) {
            error(cause.apiMessage("账号注销失败"))
        }
        response.data.takeIf { response.ok }
            ?: error(response.error?.message ?: "账号注销失败")
        clearPhoneSession()
        ensureDeviceIdentityLocked()
    }

    fun requireToken(): String = authorizationToken ?: error("服务身份尚未建立")

    fun invalidate() {
        clearPhoneSession()
        deviceToken = null
    }

    private suspend fun persistSignedIn(accessToken: String, user: AuthUser) {
        // Platform accounts and the independently verified school identity have separate lifetimes.
        check(user.accountType == "PHONE") { "Only phone accounts can enter the visible account session" }
        if (cachedUser?.id != user.id) {
            com.mychuannong.app.core.notifications.AnnouncementNotificationCoordinator.clearPopup()
        }
        deviceToken = null
        token = accessToken
        cachedUser = user
        mutableState.value = AccountSessionState.SignedIn(user)
    }

    private fun clearPhoneSession() {
        com.mychuannong.app.core.notifications.AnnouncementNotificationCoordinator.clearPopup()
        token = null
        cachedUser = null
        mutableState.value = AccountSessionState.SignedOut
    }

    private suspend fun ensureDeviceIdentityLocked() {
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) return

        val savedDeviceToken = deviceToken
        if (!savedDeviceToken.isNullOrBlank()) {
            try {
                val response = CampusToolsApiClient.authenticated.me()
                val user = response.data.takeIf { response.ok }
                if (user?.accountType == "DEVICE" && user.accountStatus == "ACTIVE") return
                deviceToken = null
            } catch (cause: retrofit2.HttpException) {
                if (cause.code() == 401 || cause.code() == 403) deviceToken = null
                else return
            } catch (_: Exception) {
                return
            }
        }

        val response = runCatching {
            CampusToolsApiClient.unauthenticated.deviceAuth(
                DeviceAuthRequest(
                    installationId = installationId,
                    deviceScopedId = deviceScopedId(),
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            )
        }.getOrNull() ?: return
        val auth = response.data.takeIf { response.ok } ?: return
        if (auth.user.accountType == "DEVICE" && auth.user.accountStatus == "ACTIVE") {
            deviceToken = auth.token
        }
    }

    private fun deviceScopedId(): String? = runCatching {
        DeviceIdentity.deviceScopedId(PlanetApplication.appContext)
    }.getOrNull()
}

private fun Throwable.apiMessage(fallback: String): String {
    if (this is ServiceUnavailableException) return message
    val http = this as? retrofit2.HttpException ?: return fallback
    return runCatching {
        JsonParser.parseString(http.response()?.errorBody()?.string().orEmpty())
            .asJsonObject
            .getAsJsonObject("error")
            .get("message")
            .asString
            .takeIf(String::isNotBlank)
    }.getOrNull() ?: fallback
}

object CampusToolsApiClient {
    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.CAMPUS_TOOLS_API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val baseClient: OkHttpClient
        get() = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UnavailableEndpointInterceptor())
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request().newBuilder().header("X-Product-Id", "my-chuannong").build())
                if (response.header("X-Product-Id") != "my-chuannong" || response.header("X-Application-Id") != "com.mychuannong.app") {
                    response.close()
                    throw java.io.IOException("后端产品身份不匹配")
                }
                response
            }
            .build()

    val unauthenticated: CampusToolsApi by lazy {
        retrofit(baseClient).create(CampusToolsApi::class.java)
    }

    val authenticated: CampusToolsApi by lazy {
        val client = baseClient.newBuilder()
            .addInterceptor { chain ->
                val token = CampusToolsSession.authorizationToken
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .build()
        retrofit(client).create(CampusToolsApi::class.java)
    }

    fun contentImage(path: String): ByteArray {
        require(Regex("/uploads/[0-9a-f-]+\\.(png|jpg|webp)").matches(path))
        val root = BuildConfig.CAMPUS_TOOLS_API_BASE_URL.substringBefore("/api/v1/")
        return baseClient.newCall(okhttp3.Request.Builder().url(root + path).build()).execute().use { response ->
            check(response.isSuccessful) { "Image unavailable" }
            val body = response.body ?: error("Empty image")
            require(body.contentLength() in 0..(4L * 1024 * 1024))
            body.bytes()
        }
    }

    fun absoluteUrl(relative: String?): String? {
        if (relative.isNullOrBlank()) return null
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) return null
        val root = BuildConfig.CAMPUS_TOOLS_API_BASE_URL.substringBefore("/api/v1/")
        return root + relative
    }
}

suspend fun <T> authenticatedCall(block: suspend (CampusToolsApi) -> ApiEnvelope<T>): T {
    if (!ServiceEndpointPolicy.isCampusToolsConfigured()) {
        throw ServiceUnavailableException()
    }
    CampusToolsSession.requireToken()
    val response = try {
        block(CampusToolsApiClient.authenticated)
    } catch (cause: retrofit2.HttpException) {
        val error = runCatching {
            JsonParser.parseString(cause.response()?.errorBody()?.string().orEmpty())
                .asJsonObject.getAsJsonObject("error")
        }.getOrNull()
        val failure = CampusToolsApiException(
            statusCode = cause.code(),
            errorCode = error?.get("code")?.asString.orEmpty(),
            message = error?.get("message")?.asString?.takeIf(String::isNotBlank)
                ?: "校园工具服务暂时不可用",
        )
        if (cause.code() == 401 || failure.errorCode in setOf("ACCOUNT_UNAVAILABLE", "ADMIN_DISABLED")) {
            CampusToolsSession.invalidate()
        }
        if (failure.errorCode in setOf("FEATURE_MAINTENANCE", "FEATURE_CLOSED")) {
            runCatching { RemoteConfigurationStore.refreshIfStale(0L) }
        }
        throw failure
    }
    return response.data.takeIf { response.ok }
        ?: error(response.error?.message ?: "校园工具服务暂时不可用")
}

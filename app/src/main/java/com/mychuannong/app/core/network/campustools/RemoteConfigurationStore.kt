package com.mychuannong.app.core.network.campustools

import android.util.Log
import com.google.gson.Gson
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.config.AppConfigEnvelope
import com.mychuannong.app.core.config.AppConfigSource
import com.mychuannong.app.core.config.AppConfigState
import com.mychuannong.app.core.config.AppConfigValidator
import com.mychuannong.app.core.config.BuiltInAppConfig
import com.mychuannong.app.core.network.ServiceEndpointPolicy
import com.mychuannong.app.core.release.V1ReleasePolicy
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FeatureAvailability(
    val status: String = "OPEN",
    val message: String = "",
    val expectedRestoreAt: Long? = null,
) {
    val isOpen: Boolean get() = status == "OPEN"
}

fun resolveFeatureAvailability(flag: RemoteFeatureFlag?): FeatureAvailability {
    if (flag == null) return FeatureAvailability()
    val normalized = flag.payload.status.uppercase()
    val status = when {
        !flag.enabled -> "CLOSED"
        normalized in setOf("OPEN", "CLOSED", "MAINTENANCE") -> normalized
        else -> "OPEN"
    }
    return FeatureAvailability(
        status = status,
        message = flag.payload.maintenanceNote,
        expectedRestoreAt = flag.payload.expectedRestoreAt,
    )
}

object RemoteConfigurationStore {
    private const val TAG = "RemoteConfiguration"
    private const val STORE_ID = "campus_tools_public_config_v1"
    private const val KEY_BOOTSTRAP = "bootstrap"
    private const val KEY_SERVICE_UPDATES = "service_updates"
    private const val KEY_APP_CONFIG_CURRENT = "remote_config_current_v1"
    private const val KEY_APP_CONFIG_LAST_KNOWN_GOOD = "remote_config_last_known_good_v1"
    private const val KEY_APP_CONFIG_SOURCE = "remote_config_source_v1"
    private const val KEY_LAST_FETCH_AT = "remote_config_last_fetch_at_v1"
    private const val BUILT_IN_ASSET = "app-config/default-v1.json"
    private const val DEFAULT_REFRESH_INTERVAL_MS = 5L * 60L * 1000L

    private val gson = Gson()
    private val storage by lazy { MMKV.mmkvWithID(STORE_ID) }
    private val refreshMutex = Mutex()
    private val serviceUpdatesRefreshMutex = Mutex()
    @Volatile private var lastSuccessfulRefreshAt = 0L

    private val builtInAppConfig by lazy { loadBuiltInAppConfig() }
    private val mutableBootstrap = MutableStateFlow(load())
    private val mutableAppConfig = MutableStateFlow(loadAppConfig())
    private val mutableServiceUpdates = MutableStateFlow(loadServiceUpdates())
    private val mutableServiceUpdatesRefreshing = MutableStateFlow(false)

    val bootstrap: StateFlow<PublicBootstrap?> = mutableBootstrap.asStateFlow()
    val appConfig: StateFlow<AppConfigState> = mutableAppConfig.asStateFlow()
    val serviceUpdates: StateFlow<List<RemoteServiceUpdate>> = mutableServiceUpdates.asStateFlow()
    val serviceUpdatesRefreshing: StateFlow<Boolean> = mutableServiceUpdatesRefreshing.asStateFlow()

    init {
        lastSuccessfulRefreshAt = storage.decodeLong(KEY_LAST_FETCH_AT, 0L)
    }

    suspend fun refresh(): PublicBootstrap? = refreshIfStale(0L)

    suspend fun refreshIfStale(
        minIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    ): PublicBootstrap? {
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) return mutableBootstrap.value
        val now = System.currentTimeMillis()
        if (now - lastSuccessfulRefreshAt < minIntervalMs) return mutableBootstrap.value

        return refreshMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (lockedNow - lastSuccessfulRefreshAt < minIntervalMs) {
                return@withLock mutableBootstrap.value
            }

            val response = try {
                CampusToolsApiClient.unauthenticated.publicBootstrap(
                    schemaVersion = AppConfigValidator.SCHEMA_VERSION,
                    appVersionCode = BuildConfig.VERSION_CODE,
                )
            } catch (error: Throwable) {
                // Remote config must never make the app unusable. Keep the best local state and
                // let the next foreground refresh retry the server.
                Log.w(TAG, "Remote configuration refresh failed (${error.javaClass.simpleName})")
                return@withLock mutableBootstrap.value
            }

            val value = response.data.takeIf { response.ok } ?: return@withLock mutableBootstrap.value
            check(storage.encode(KEY_BOOTSTRAP, gson.toJson(value)))
            mutableBootstrap.value = value

            value.toAppConfigEnvelope()?.let { envelope ->
                acceptServerConfig(envelope, lockedNow)
            }

            lastSuccessfulRefreshAt = lockedNow
            check(storage.encode(KEY_LAST_FETCH_AT, lockedNow))
            Log.d(
                TAG,
                "Public configuration refreshed: " +
                    "configVersion=${mutableAppConfig.value.envelope.configVersion}, " +
                    "source=${mutableAppConfig.value.source}, flags=" +
                    value.flags.joinToString { flag ->
                        "${flag.key}=${resolveFeatureAvailability(flag).status}"
                    },
            )
            value
        }
    }

    fun feature(key: String): FeatureAvailability {
        if (V1ReleasePolicy.isHardClosed(key)) {
            return FeatureAvailability(
                status = "CLOSED",
                message = V1ReleasePolicy.CLOSED_MESSAGE,
            )
        }
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) {
            return FeatureAvailability(
                status = "CLOSED",
                message = "服务暂未开放",
            )
        }
        val flag = mutableBootstrap.value?.flags?.firstOrNull { it.key == key }
        return resolveFeatureAvailability(flag)
    }

    suspend fun refreshServiceUpdates(): List<RemoteServiceUpdate> {
        if (!ServiceEndpointPolicy.isCampusToolsConfigured()) return mutableServiceUpdates.value
        if (!serviceUpdatesRefreshMutex.tryLock()) return mutableServiceUpdates.value
        mutableServiceUpdatesRefreshing.value = true
        return try {
            val response = try {
                CampusToolsApiClient.unauthenticated.serviceUpdates()
            } catch (error: Throwable) {
                Log.w(TAG, "Service updates refresh failed (${error.javaClass.simpleName})")
                throw error
            }
            val value = response.data.takeIf { response.ok } ?: return mutableServiceUpdates.value
            check(storage.encode(KEY_SERVICE_UPDATES, gson.toJson(value)))
            mutableServiceUpdates.value = value
            value
        } finally {
            mutableServiceUpdatesRefreshing.value = false
            serviceUpdatesRefreshMutex.unlock()
        }
    }

    private fun acceptServerConfig(
        envelope: AppConfigEnvelope,
        loadedAt: Long,
    ) {
        try {
            AppConfigValidator.validate(
                envelope = envelope,
                appVersionCode = BuildConfig.VERSION_CODE,
                nowMs = loadedAt,
            )

            val current = mutableAppConfig.value
            val currentEnvelope = current.envelope
            if (envelope.configVersion < currentEnvelope.configVersion) {
                throw IllegalArgumentException(
                    "server configVersion ${envelope.configVersion} is older than " +
                        "local ${currentEnvelope.configVersion}",
                )
            }
            if (
                envelope.configVersion == currentEnvelope.configVersion &&
                envelope.configHash.isNotBlank() &&
                !envelope.configHash.equals(currentEnvelope.configHash, ignoreCase = true)
            ) {
                throw IllegalArgumentException("same configVersion has a different configHash")
            }

            val encoded = gson.toJson(envelope)
            check(storage.encode(KEY_APP_CONFIG_CURRENT, encoded))
            check(storage.encode(KEY_APP_CONFIG_LAST_KNOWN_GOOD, encoded))
            check(storage.encode(KEY_APP_CONFIG_SOURCE, AppConfigSource.SERVER.name))

            val knownGoodVersion = maxOf(
                envelope.configVersion,
                current.lastKnownGoodVersion,
            )
            mutableAppConfig.value = AppConfigState(
                envelope = envelope,
                source = AppConfigSource.SERVER,
                loadedAt = loadedAt,
                lastKnownGoodVersion = knownGoodVersion,
            )
        } catch (error: Throwable) {
            // Validation failures, incompatible versions, expired payloads, and unsafe URLs are
            // rejected as a unit. The current cache/LKG/default remains visible to the UI.
            Log.w(TAG, "Remote app config rejected (${error.message})")
        }
    }

    private fun loadAppConfig(): AppConfigState {
        val now = System.currentTimeMillis()
        val lastKnownGood = readAppConfig(KEY_APP_CONFIG_LAST_KNOWN_GOOD)
        val cached = readAppConfig(KEY_APP_CONFIG_CURRENT)

        val selected = when {
            cached != null && isValidCachedConfig(cached, now) -> cached to AppConfigSource.CACHE
            lastKnownGood != null && isValidCachedConfig(lastKnownGood, now) ->
                lastKnownGood to AppConfigSource.CACHE
            else -> builtInAppConfig to AppConfigSource.BUILT_IN
        }
        val knownGoodVersion = maxOf(
            selected.first.configVersion,
            lastKnownGood?.configVersion ?: 0L,
        )
        return AppConfigState(
            envelope = selected.first,
            source = selected.second,
            loadedAt = now,
            lastKnownGoodVersion = knownGoodVersion,
        )
    }

    private fun isValidCachedConfig(
        envelope: AppConfigEnvelope,
        nowMs: Long,
    ): Boolean = runCatching {
        AppConfigValidator.validate(
            envelope = envelope,
            appVersionCode = BuildConfig.VERSION_CODE,
            nowMs = nowMs,
        )
    }.isSuccess

    private fun readAppConfig(key: String): AppConfigEnvelope? = runCatching {
        val encoded = storage.decodeString(key)
        if (encoded.isNullOrBlank()) null
        else gson.fromJson(encoded, AppConfigEnvelope::class.java)
    }.getOrNull()

    private fun loadBuiltInAppConfig(): AppConfigEnvelope {
        val candidate = runCatching {
            PlanetApplication.appContext.assets.open(BUILT_IN_ASSET).bufferedReader(Charsets.UTF_8).use {
                gson.fromJson(it, AppConfigEnvelope::class.java)
            }
        }.getOrElse {
            BuiltInAppConfig.envelope()
        }
        return runCatching {
            AppConfigValidator.validate(
                envelope = candidate,
                appVersionCode = BuildConfig.VERSION_CODE,
                allowBuiltInVersionZero = true,
            )
            candidate
        }.getOrElse {
            Log.w(TAG, "Built-in app config invalid; using code default (${it.message})")
            BuiltInAppConfig.envelope()
        }
    }

    private fun load(): PublicBootstrap? = runCatching {
        gson.fromJson(storage.decodeString(KEY_BOOTSTRAP), PublicBootstrap::class.java)
    }.getOrNull()

    private fun loadServiceUpdates(): List<RemoteServiceUpdate> = runCatching {
        val encoded = storage.decodeString(KEY_SERVICE_UPDATES)
        if (encoded.isNullOrBlank()) {
            emptyList()
        } else {
            gson.fromJson(
                encoded,
                Array<RemoteServiceUpdate>::class.java,
            ).toList()
        }
    }.getOrDefault(emptyList())

    private fun PublicBootstrap.toAppConfigEnvelope(): AppConfigEnvelope? {
        val payload = config ?: return null
        val version = configVersion ?: return null
        val schema = schemaVersion ?: return null
        return AppConfigEnvelope(
            schemaVersion = schema,
            configVersion = version,
            minAppVersionCode = minAppVersionCode ?: 0,
            maxAppVersionCode = maxAppVersionCode,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            configHash = configHash.orEmpty(),
            config = payload,
        )
    }
}

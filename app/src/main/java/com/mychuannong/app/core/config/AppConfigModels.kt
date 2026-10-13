package com.mychuannong.app.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.security.MessageDigest

data class AppConfigEnvelope(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("configVersion") val configVersion: Long = 0,
    @SerializedName("minAppVersionCode") val minAppVersionCode: Int = 0,
    @SerializedName("maxAppVersionCode") val maxAppVersionCode: Int? = null,
    @SerializedName("issuedAt") val issuedAt: Long? = null,
    @SerializedName("expiresAt") val expiresAt: Long? = null,
    @SerializedName("configHash") val configHash: String = "",
    @SerializedName("config") val config: AppConfigPayload = AppConfigPayload(),
)

data class AppConfigPayload(
    @SerializedName("navigation") val navigation: List<AppConfigNavigationItem> = emptyList(),
    @SerializedName("cards") val cards: List<AppConfigCard> = emptyList(),
    @SerializedName("remotePages") val remotePages: List<AppConfigLink> = emptyList(),
    @SerializedName("resources") val resources: List<AppConfigLink> = emptyList(),
)

data class AppConfigNavigationItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("actionId") val actionId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("iconKey") val iconKey: String = "",
    @SerializedName("order") val order: Int = 0,
    @SerializedName("section") val section: String = "learning",
    @SerializedName("visible") val visible: Boolean = true,
    @SerializedName("featureKey") val featureKey: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class AppConfigCard(
    @SerializedName("id") val id: String = "",
    @SerializedName("cardType") val cardType: String = "notice",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("order") val order: Int = 0,
    @SerializedName("visible") val visible: Boolean = true,
    @SerializedName("actionId") val actionId: String = "",
    @SerializedName("url") val url: String? = null,
)

data class AppConfigLink(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("url") val url: String? = null,
    @SerializedName("visible") val visible: Boolean = true,
    @SerializedName("order") val order: Int = 0,
)

enum class AppConfigSource {
    SERVER,
    CACHE,
    BUILT_IN,
}

data class AppConfigState(
    val envelope: AppConfigEnvelope,
    val source: AppConfigSource,
    val loadedAt: Long,
    val lastKnownGoodVersion: Long,
)

object FeatureRegistry {
    val nativeActionIds: Set<String> = setOf(
        "schedule",
        "grade",
        "classroom",
        "homework",
        "electric",
        "exam",
        "calendar",
        "cet",
        "ncre",
        "lost_found",
        "account",
        "document",
        "mandarin",
        "official_web",
    )

    val iconKeys: Set<String> = setOf(
        "calendar",
        "assessment",
        "computer",
        "meeting_room",
        "assignment",
        "electric",
        "event_note",
        "language",
        "find_in_page",
        "wallet",
        "folder",
        "record_voice_over",
        "globe",
    )

    val sections: Set<String> = setOf("learning", "life", "primary", "secondary")
    val configurableFeatureKeys: Set<String> = setOf("lost_found", "resource_library")

    fun isRegisteredAction(actionId: String): Boolean = actionId in nativeActionIds
}

class AppConfigValidationException(message: String) : IllegalArgumentException(message)

object AppConfigValidator {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 256 * 1024
    private const val MAX_NAVIGATION = 100
    private const val MAX_CARDS = 50
    private const val MAX_REMOTE_PAGES = 30
    private const val MAX_RESOURCES = 200
    private const val MAX_ID_LENGTH = 64
    private const val MAX_TITLE_LENGTH = 40
    private const val MAX_DESCRIPTION_LENGTH = 500
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_CACHE_AGE_MS = 72L * 60L * 60L * 1000L
    private const val OFFICIAL_HOST = "" // No product website has been configured.

    fun validate(
        envelope: AppConfigEnvelope,
        appVersionCode: Int,
        nowMs: Long = System.currentTimeMillis(),
        allowBuiltInVersionZero: Boolean = false,
    ) {
        if (envelope.schemaVersion != SCHEMA_VERSION) {
            invalid("schemaVersion 不受支持")
        }
        if (envelope.configVersion < 0L || (!allowBuiltInVersionZero && envelope.configVersion == 0L)) {
            invalid("configVersion 无效")
        }
        if (envelope.minAppVersionCode < 0 ||
            envelope.maxAppVersionCode != null &&
            envelope.maxAppVersionCode < envelope.minAppVersionCode
        ) {
            invalid("配置兼容版本范围无效")
        }
        if (appVersionCode < envelope.minAppVersionCode ||
            envelope.maxAppVersionCode?.let { appVersionCode > it } == true
        ) {
            invalid("配置与当前 App 版本不兼容")
        }
        if (envelope.expiresAt != null && nowMs > envelope.expiresAt) {
            invalid("配置已过期")
        }
        if (envelope.configVersion > 0L && nowMs - (envelope.issuedAt ?: nowMs) > MAX_CACHE_AGE_MS) {
            invalid("配置超过本地有效期")
        }

        val config = envelope.config
        if (config.navigation.size > MAX_NAVIGATION) invalid("navigation 数量超出限制")
        if (config.cards.size > MAX_CARDS) invalid("cards 数量超出限制")
        if (config.remotePages.size > MAX_REMOTE_PAGES) invalid("remotePages 数量超出限制")
        if (config.resources.size > MAX_RESOURCES) invalid("resources 数量超出限制")

        val navigationIds = HashSet<String>()
        config.navigation.forEachIndexed { index, item ->
            requireText(item.id, "navigation[$index].id", MAX_ID_LENGTH)
            if (!navigationIds.add(item.id)) invalid("navigation[$index].id 重复")
            requireText(item.actionId, "navigation[$index].actionId", MAX_ID_LENGTH)
            if (!FeatureRegistry.isRegisteredAction(item.actionId)) {
                invalid("navigation[$index].actionId 未注册")
            }
            requireText(item.title, "navigation[$index].title", MAX_TITLE_LENGTH)
            requireText(item.iconKey, "navigation[$index].iconKey", MAX_ID_LENGTH)
            if (item.iconKey !in FeatureRegistry.iconKeys) invalid("navigation[$index].iconKey 未注册")
            if (item.section !in FeatureRegistry.sections) invalid("navigation[$index].section 未注册")
            item.featureKey?.let {
                requireText(it, "navigation[$index].featureKey", MAX_ID_LENGTH)
                if (it !in FeatureRegistry.configurableFeatureKeys) {
                    invalid("navigation[$index].featureKey 不允许从配置开启")
                }
            }
            validateUrl(item.url, "navigation[$index].url")
        }
        if (config.navigation.none { it.id == "schedule" }) {
            invalid("navigation 必须保留课表入口")
        }

        val cardIds = HashSet<String>()
        config.cards.forEachIndexed { index, item ->
            requireText(item.id, "cards[$index].id", MAX_ID_LENGTH)
            if (!cardIds.add(item.id)) invalid("cards[$index].id 重复")
            if (item.cardType !in setOf("notice", "link", "announcement", "remote_page")) {
                invalid("cards[$index].cardType 未注册")
            }
            requireText(item.title, "cards[$index].title", MAX_TITLE_LENGTH, required = false)
            requireText(item.description, "cards[$index].description", MAX_DESCRIPTION_LENGTH, required = false)
            validateUrl(item.url, "cards[$index].url")
        }
        validateLinks(config.remotePages, "remotePages")
        validateLinks(config.resources, "resources")

        if (envelope.configVersion > 0L) {
            val expectedHash = canonicalConfigHash(config)
            if (!expectedHash.equals(envelope.configHash, ignoreCase = true)) {
                invalid("configHash 不匹配")
            }
        }
    }

    fun canonicalConfigHash(config: AppConfigPayload): String {
        // Backend canonical JSON retains explicit null fields; Gson's default serializer omits
        // them, which would produce a different hash for an otherwise identical payload.
        val gson = GsonBuilder().serializeNulls().create()
        val tree = gson.toJsonTree(config)
        val canonical = canonicalJson(tree).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical)
            .joinToString("") { "%02X".format(it) }
    }

    fun canonicalJson(element: JsonElement): String = when {
        element is JsonNull || element.isJsonNull -> "null"
        element is JsonObject -> element.entrySet()
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") {
                JsonPrimitive(it.key).toString() + ":" + canonicalJson(it.value)
            }
        element is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::canonicalJson)
        element is JsonPrimitive -> element.toString()
        else -> element.toString()
    }

    fun isSafeOfficialUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(OFFICIAL_HOST, ignoreCase = true) &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.path.startsWith("/") &&
            !uri.path.startsWith("//")
    }

    private fun validateLinks(items: List<AppConfigLink>, field: String) {
        val ids = HashSet<String>()
        items.forEachIndexed { index, item ->
            requireText(item.id, "$field[$index].id", MAX_ID_LENGTH)
            if (!ids.add(item.id)) invalid("$field[$index].id 重复")
            requireText(item.title, "$field[$index].title", MAX_TITLE_LENGTH)
            requireText(item.description, "$field[$index].description", MAX_DESCRIPTION_LENGTH, required = false)
            validateUrl(item.url, "$field[$index].url")
        }
    }

    private fun validateUrl(value: String?, field: String) {
        if (value == null) return
        if (value.length > MAX_URL_LENGTH || !isSafeOfficialUrl(value)) {
            invalid("$field 只能使用官方 HTTPS 域名路径")
        }
    }

    private fun requireText(value: String, field: String, maximum: Int, required: Boolean = true) {
        if (value.length > maximum || required && value.isBlank()) {
            invalid("$field 内容无效")
        }
    }

    private fun invalid(message: String): Nothing = throw AppConfigValidationException(message)
}

object BuiltInAppConfig {
    fun envelope(): AppConfigEnvelope = AppConfigEnvelope(
        schemaVersion = AppConfigValidator.SCHEMA_VERSION,
        configVersion = 0L,
        minAppVersionCode = 0,
        configHash = "",
        config = AppConfigPayload(
            navigation = listOf(
                item("schedule", "schedule", "课表", "calendar", 10, "learning"),
                item("grade", "grade", "成绩查询", "assessment", 20, "learning"),
                item("electric", "electric", "电费查询", "electric", 50, "life"),
                item("exam", "exam", "考试安排", "event_note", 60, "learning"),
                item("cet", "cet", "四六级", "language", 70, "learning"),
                item("lost_found", "lost_found", "失物招领", "find_in_page", 80, "life", featureKey = "lost_found"),
                item("document", "document", "资料库", "folder", 90, "learning", featureKey = "resource_library"),
                item("mandarin", "mandarin", "普通话测试", "record_voice_over", 100, "learning"),
                item("ncre", "ncre", "计算机考试", "computer", 110, "learning"),
            ),
            resources = emptyList(),
        ),
    )

    private fun item(
        id: String,
        actionId: String,
        title: String,
        iconKey: String,
        order: Int,
        section: String,
        featureKey: String? = null,
    ) = AppConfigNavigationItem(
        id = id,
        actionId = actionId,
        title = title,
        iconKey = iconKey,
        order = order,
        section = section,
        featureKey = featureKey,
    )

    private fun link(
        id: String,
        title: String,
        description: String,
        url: String,
        order: Int,
    ) = AppConfigLink(
        id = id,
        title = title,
        description = description,
        url = url,
        order = order,
    )
}

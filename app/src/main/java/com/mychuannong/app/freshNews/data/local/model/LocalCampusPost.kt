package com.mychuannong.app.freshNews.data.local.model

data class LocalCampusPost(
    val id: String,
    val title: String,
    val content: String,
    val tag: String,
    val createdAtMillis: Long,
    val isFavorite: Boolean = false,
    val linkedActivityId: String? = null,
    val lostFound: LocalLostFoundInfo? = null,
)

enum class LostFoundCategory(val label: String) {
    CARD("证件 / 校园卡"), ELECTRONIC("电子设备"), KEYS("钥匙"),
    STATIONERY("书籍 / 文具"), DAILY("衣物 / 日用品"), UMBRELLA("雨伞"), OTHER("其他")
}

enum class LostFoundType(val label: String) {
    LOST("寻物"),
    FOUND("招领"),
}

enum class LostFoundStatus(val label: String) {
    SEARCHING("寻找中"),
    WAITING("等待认领"),
    CLAIMED("已认领"),
    FOUND("已找回"),
    RETURNED("已归还"),
}

/** Extra data carried by one campus-channel post; null keeps older normal posts compatible. */
data class LocalLostFoundInfo(
    val itemName: String,
    val category: LostFoundCategory,
    val location: String,
    val foundDate: String,
    val approximateTime: String? = null,
    val description: String = "",
    val claimNote: String = "",
    val imagePath: String? = null,
    /**
     * Nullable for JSON compatibility: records written before the independent lost-and-found
     * module did not contain this field and are therefore interpreted as FOUND.
     */
    val type: LostFoundType? = null,
    val status: LostFoundStatus = LostFoundStatus.WAITING,
    val isLocalPublisher: Boolean = true,
    val hasLocalClaimRequest: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** True only while a local write still needs acknowledgement from the production API. */
    val pendingSync: Boolean = false,
) {
    val effectiveType: LostFoundType
        get() = type ?: LostFoundType.FOUND
}

data class LocalCampusDraft(
    val title: String = "",
    val content: String = "",
    val tag: String = LocalCampusPostRules.DEFAULT_TAG,
    val updatedAtMillis: Long = 0L
) {
    val hasContent: Boolean
        get() = title.isNotBlank() || content.isNotBlank()
}

enum class LocalCampusValidationError {
    TITLE_TOO_LONG,
    CONTENT_REQUIRED,
    CONTENT_TOO_LONG,
    INVALID_TAG
}

/**
 * Pure Kotlin rules shared by the UI, local store and JVM tests.
 *
 * Keeping validation here prevents the local MVP from persisting values that the UI cannot
 * faithfully display. No account or network state participates in these rules.
 */
object LocalCampusPostRules {
    const val MAX_TITLE_LENGTH = 40
    const val MAX_CONTENT_LENGTH = 1_000
    const val DEFAULT_TAG = "校园动态"

    val tags: List<String> = listOf(
        DEFAULT_TAG,
        "校园活动",
        "搭子社",
        "失物招领",
        "学习交流",
        "生活服务"
    )

    fun validate(
        title: String,
        content: String,
        tag: String
    ): LocalCampusValidationError? = when {
        title.length > MAX_TITLE_LENGTH -> LocalCampusValidationError.TITLE_TOO_LONG
        content.isBlank() -> LocalCampusValidationError.CONTENT_REQUIRED
        content.length > MAX_CONTENT_LENGTH -> LocalCampusValidationError.CONTENT_TOO_LONG
        tag !in tags -> LocalCampusValidationError.INVALID_TAG
        else -> null
    }

    fun normalizedTag(tag: String): String = when (tag) {
        "校园生活", "求助互助" -> "生活服务"
        "经验分享" -> "学习交流"
        else -> tag.takeIf(tags::contains) ?: DEFAULT_TAG
    }

    fun createPost(
        id: String,
        title: String,
        content: String,
        tag: String,
        createdAtMillis: Long,
        linkedActivityId: String? = null,
    ): LocalCampusPost {
        val error = validate(title, content, tag)
        require(error == null) { "Invalid local campus post: $error" }
        require(id.isNotBlank()) { "Local campus post id must not be blank" }
        require(createdAtMillis > 0L) { "Local campus post time must be positive" }

        return LocalCampusPost(
            id = id,
            title = title.trim(),
            content = content.trim(),
            tag = tag,
            createdAtMillis = createdAtMillis,
            linkedActivityId = linkedActivityId?.takeIf(String::isNotBlank)
        )
    }
}

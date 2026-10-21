package com.mychuannong.app.feature.resources

import java.net.URI
import java.util.Locale

enum class ResourceCategory(val displayName: String) {
    EXAM_MATERIALS("考试资料"),
    LIBRARY("图书馆"),
    ACADEMIC_AFFAIRS("教务信息"),
    STUDENT_SERVICES("学生服务")
}

data class ResourceLink(
    val id: String,
    val title: String,
    val description: String,
    val category: ResourceCategory,
    val sourceLabel: String,
    val url: String,
    val keywords: Set<String> = emptySet()
)

/**
 * Curated links verified against their public pages on 2026-07-21.
 *
 * Only exact entries from this catalog can be opened by the resource-library UI.
 */
object ResourceCatalog {
    val resources: List<ResourceLink> = emptyList()

    val categories: List<ResourceCategory> = ResourceCategory.entries

    private val allowedHosts = setOf(
        "github.com",
        "www.sicau.edu.cn",
        "lib.sicau.edu.cn"
    )

    init {
        check(resources.map(ResourceLink::id).distinct().size == resources.size) {
            "Resource ids must be unique"
        }
        check(resources.all { isAllowedRemoteUrl(it.url) }) {
            "Resource catalog contains a URL outside the verified host allowlist"
        }
    }

    fun contains(resource: ResourceLink): Boolean =
        resource in resources && isAllowedRemoteUrl(resource.url)

    fun filter(
        query: String,
        category: ResourceCategory?
    ): List<ResourceLink> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return resources.filter { resource ->
            val matchesCategory = category == null || resource.category == category
            val matchesQuery = normalizedQuery.isEmpty() || resource.searchText()
                .lowercase(Locale.ROOT)
                .contains(normalizedQuery)
            matchesCategory && matchesQuery
        }
    }

    private fun ResourceLink.searchText(): String = buildString {
        append(title)
        append(' ')
        append(description)
        append(' ')
        append(category.displayName)
        append(' ')
        append(sourceLabel)
        append(' ')
        append(keywords.joinToString(" "))
    }

    private fun isAllowedRemoteUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT)
        uri.scheme.equals("https", ignoreCase = true) &&
            host != null &&
            host in allowedHosts
    }.getOrDefault(false)
}

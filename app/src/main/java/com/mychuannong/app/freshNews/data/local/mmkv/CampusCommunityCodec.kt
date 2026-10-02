package com.mychuannong.app.freshNews.data.local.mmkv

import com.mychuannong.app.freshNews.data.local.model.LocalCampusDraft
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPost
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Android-free JSON codec so persistence compatibility can be covered by regular JVM tests. */
class CampusCommunityCodec(
    private val gson: Gson = Gson()
) {
    private val postListType = object : TypeToken<List<LocalCampusPost>>() {}.type

    fun encodePosts(posts: List<LocalCampusPost>): String = gson.toJson(posts)

    fun decodePosts(json: String?): List<LocalCampusPost> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<LocalCampusPost>>(json, postListType)
                .orEmpty()
                .map { it.copy(tag = LocalCampusPostRules.normalizedTag(it.tag)) }
                .filter(::isValidStoredPost)
                .sortedByDescending(LocalCampusPost::createdAtMillis)
                .distinctBy(LocalCampusPost::id)
        }.getOrDefault(emptyList())
    }

    fun encodeDraft(draft: LocalCampusDraft): String = gson.toJson(draft)

    fun decodeDraft(json: String?): LocalCampusDraft {
        if (json.isNullOrBlank()) return LocalCampusDraft()
        return runCatching {
            val decoded = gson.fromJson(json, LocalCampusDraft::class.java)
            LocalCampusDraft(
                title = decoded.title.take(LocalCampusPostRules.MAX_TITLE_LENGTH),
                content = decoded.content.take(LocalCampusPostRules.MAX_CONTENT_LENGTH),
                tag = LocalCampusPostRules.normalizedTag(decoded.tag),
                updatedAtMillis = decoded.updatedAtMillis.coerceAtLeast(0L)
            )
        }.getOrDefault(LocalCampusDraft())
    }

    private fun isValidStoredPost(post: LocalCampusPost): Boolean =
        post.id.isNotBlank() &&
            post.createdAtMillis > 0L &&
            LocalCampusPostRules.validate(post.title, post.content, post.tag) == null
}

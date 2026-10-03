package com.mychuannong.app.core.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mychuannong.app.core.network.campustools.ApiEnvelope
import com.mychuannong.app.core.network.campustools.PublicBootstrap
import com.mychuannong.app.core.network.campustools.RemoteLostFoundPost
import com.mychuannong.app.core.network.campustools.RemoteResource
import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusToolsProductionContractTest {
    private val gson = Gson()

    @Test
    fun releaseDtosParseLostFoundAndResourceProductionShapes() {
        val lostType = object : TypeToken<ApiEnvelope<List<RemoteLostFoundPost>>>() {}.type
        val lost: ApiEnvelope<List<RemoteLostFoundPost>> = gson.fromJson(
            """{"ok":true,"data":[{"id":"lost-1","clientLocalId":"local-1","type":"LOST","itemName":"校园卡","category":"CARD","location":"图书馆","eventDate":"2026-08-22","approximateTime":"10:00","description":"测试","claimNote":"","imageUrl":null,"status":"FOUND","createdAtMillis":1,"updatedAtMillis":2,"isMine":true,"isFavorite":false,"hasClaimRequest":false}]}""",
            lostType,
        )
        val post = requireNotNull(lost.data).single()
        assertEquals("校园卡", post.itemName)
        assertEquals("FOUND", post.status)
        assertTrue(post.isMine)

        val resourceType = object : TypeToken<ApiEnvelope<List<RemoteResource>>>() {}.type
        val resources: ApiEnvelope<List<RemoteResource>> = gson.fromJson(
            """{"ok":true,"data":[{"id":"resource-1","title":"资料标题","summary":"摘要","category":"学习资料","tags":["V1"],"audienceScope":"全体同学","publisherLabel":"我的川农资料库","sourceLabel":"公开来源","usageNote":"仅供学习","resourceType":"EXTERNAL_LINK","externalUrl":"https://example.com/resource","coverUrl":null,"isFree":true,"futurePriceFen":null,"publishedAt":3,"isFavorite":false,"viewCount":4,"lastViewedAt":null}]}""",
            resourceType,
        )
        val resource = requireNotNull(resources.data).single()
        assertEquals("资料标题", resource.title)
        assertEquals("https://example.com/resource", resource.externalUrl)
        assertEquals(4, resource.viewCount)
    }

    @Test
    fun releaseDtosParseAnnouncementBootstrapAndServiceUpdates() {
        val bootstrapType = object : TypeToken<ApiEnvelope<PublicBootstrap>>() {}.type
        val bootstrap: ApiEnvelope<PublicBootstrap> = gson.fromJson(
            """{"ok":true,"data":{"flags":[{"key":"lost_found","enabled":true,"updatedAt":1,"payload":{"status":"OPEN","maintenanceNote":"","expectedRestoreAt":null}}],"announcements":[{"id":"announcement-1","title":"生产公告","content":"联调内容","publishedAt":2}],"version":{"latest_version_code":32,"minimum_version_code":32,"latest_version_name":"1.0.0","update_message":"","download_url":null,"apk_sha256":null,"apk_size":null,"force_update":false,"published_at":3}}}""",
            bootstrapType,
        )
        val data = requireNotNull(bootstrap.data)
        assertEquals("生产公告", data.announcements.single().title)
        assertTrue(data.flags.single().enabled)
        assertEquals(32, requireNotNull(data.version).latest_version_code)

        val updatesType = object : TypeToken<ApiEnvelope<List<RemoteServiceUpdate>>>() {}.type
        val updates: ApiEnvelope<List<RemoteServiceUpdate>> = gson.fromJson(
            """{"ok":true,"data":[{"id":"announcement-1","type":"ANNOUNCEMENT","priority":10,"title":"生产公告","summary":"联调内容","targetType":"ANNOUNCEMENT","targetId":"announcement-1","publishedAt":2,"dismissible":true}]}""",
            updatesType,
        )
        val update = requireNotNull(updates.data).single()
        assertEquals("ANNOUNCEMENT", update.type)
        assertEquals("announcement-1", update.targetId)
        assertTrue(update.dismissible)
    }

    @Test
    fun releaseRulesKeepProductionResponseDtosAndResourceCacheAvoidsAnonymousTypeToken() {
        val projectRoot = if (File("app/proguard-rules.pro").isFile) File(".") else File("..")
        val rules = File(projectRoot, "app/proguard-rules.pro").readText()
        assertTrue(rules.contains("-keep class com.mychuannong.app.core.network.campustools.ApiEnvelope"))
        assertTrue(rules.contains("-keep class com.mychuannong.app.core.network.campustools.Remote**"))
        assertTrue(rules.contains("-keep class com.mychuannong.app.core.network.campustools.PublicBootstrap"))

        val repository = File(
            projectRoot,
            "app/src/main/java/com/mychuannong/app/feature/resources/ResourceLibraryRepository.kt",
        ).readText()
        assertTrue(repository.contains("Array<RemoteResource>::class.java"))
        assertFalse(repository.contains("TypeToken<List<RemoteResource>>"))
    }
}

package com.mychuannong.app.feature.common.compose_ui

import com.google.gson.Gson
import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import org.junit.Assert.*
import org.junit.Test

class ServiceAnnouncementFeedTest {
    @Test fun publishedFeedPreservesOrderImagesLinksAndDetails() {
        val feed = Gson().fromJson("""[
            {"id":"update-11","type":"UPDATE","title":"升级"},
            {"id":"announcement-a","type":"ANNOUNCEMENT","title":"图书馆通知","summary":"正文一","imageUrl":"/uploads/a.png","targetType":"ANNOUNCEMENT","publishedAt":1792600000000},
            {"id":"closed","type":"CLOSED","title":"维护"},
            {"id":"announcement-b","type":"ANNOUNCEMENT","title":"活动通知","summary":"正文二","targetType":"URL","targetUrl":"https://www.sicau.edu.cn/"}
        ]""", Array<RemoteServiceUpdate>::class.java).toList()
        val cards = filterServiceUpdatesForClient(feed, 10).filter { it.type == "ANNOUNCEMENT" }
        assertEquals(listOf("announcement-a", "announcement-b"), cards.map { it.id })
        assertEquals("正文一", cards[0].summary)
        assertEquals("/uploads/a.png", cards[0].imageUrl)
        assertEquals(1792600000000L, cards[0].publishedAt)
        assertEquals("https://www.sicau.edu.cn/", cards[1].targetUrl)
    }
    @Test fun successfulEmptyFeedIsEmptyInsteadOfSyntheticOfflineCard() {
        assertTrue(filterServiceUpdatesForClient(emptyList(), 10).isEmpty())
        assertTrue(filterServiceUpdatesForClient(listOf(RemoteServiceUpdate(id="u",type="UPDATE",title="升级")), 10).isEmpty())
    }
}

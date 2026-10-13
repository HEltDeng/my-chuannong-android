package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SicauTranscriptNavigationTest {
    private val source = JiaowuPage.GRADES.url
    @Test fun confirmedContentEndpointsUseExistingDirectAndVpnRouting() {
        val route = SicauJiaowuRoute.fromResourcePage("https://webvpn.sicau.edu.cn/https/" + "a".repeat(32) + "/web/web/web/index.asp")!!
        for(url in listOf(SicauTranscriptNavigation.TRANSCRIPT_URL, SicauTranscriptNavigation.RANKING_URL)) {
            assertEquals(url, SicauTranscriptNavigation.resolve(url, source, SicauJiaowuRoute.Direct, "synthetic-a"))
            assertEquals(url, SicauTranscriptNavigation.resolve(route.url(url), source, route, "synthetic-a"))
            assertTrue(route.url(url).startsWith("https://webvpn.sicau.edu.cn/https/" + "a".repeat(32) + "/xuesheng/chengji/chengji/"))
        }
    }
    @Test fun recognizesBracketedNavigationLabelsShownBySchool() {
        val links = SicauTranscriptNavigation.links("<a href='report.asp'>【个人成绩单】</a><a href='rank.asp'>【有效必修加权排名】</a><a href='other.asp'>【全部成绩加权排名】</a>", source, SicauJiaowuRoute.Direct, "synthetic-a")
        assertEquals(listOf("个人成绩单", "有效必修加权排名"), links.map { it.title })
    }
    @Test fun followsOnlyActualLabeledLinksAndPreservesQueries() {
        val links = SicauTranscriptNavigation.links("<a href='report.asp?term=2'>个人成绩单</a><a href='rank.asp'>有效必修加权排名</a><a href='other.asp'>其他学生</a>", source, SicauJiaowuRoute.Direct, "synthetic-a")
        assertEquals(2, links.size)
        assertTrue(links.first().url.endsWith("/report.asp?term=2"))
    }
    @Test fun rejectsForeignOriginsMutationsAndOtherStudentParameters() {
        listOf("https://evil.test/xuesheng/report.asp", "javascript:alert(1)", "file:///private", "report.asp?xh=synthetic-b", "save.asp", "/admin/report.asp").forEach {
            assertNull(it, SicauTranscriptNavigation.resolve(it, source, SicauJiaowuRoute.Direct, "synthetic-a"))
        }
    }
    @Test fun discoveredVpnLinksStayOnTheConfirmedProxy() {
        val route = SicauJiaowuRoute.fromResourcePage("https://webvpn.sicau.edu.cn/https/" + "a".repeat(32) + "/web/web/web/index.asp")!!
        val url = SicauTranscriptNavigation.resolve("report.asp", source, route, "synthetic-a")!!
        assertEquals(SicauJiaowuProtocol.ORIGIN + "/xuesheng/chengji/chengji/report.asp", url)
        assertTrue(route.url(url).startsWith("https://webvpn.sicau.edu.cn/https/"))
        assertNull(SicauTranscriptNavigation.resolve("https://webvpn.sicau.edu.cn/https/" + "b".repeat(32) + "/xuesheng/report.asp", source, route, "synthetic-a"))
    }
}

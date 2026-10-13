package com.mychuannong.app.school

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SicauTranscriptSessionTest {
    private val owner = "synthetic-a"
    private val reportPath = "/xuesheng/chengji/chengji/bchengjike.asp"
    private val rankPath = "/xuesheng/chengji/chengji/zytongbf.asp"
    private fun profile(id: String) = "<table><tr><td>学号</td><td>$id</td></tr></table>"
    private suspend fun read(badRanking: Boolean = false, changeOwnerAtEnd: Boolean = false): Pair<SchoolTranscript, List<String>> {
        val jar = SicauJiaowuCookieJar()
        val events = mutableListOf<String>()
        var sawRank = false
        val requests = mutableListOf<String>()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.encodedPath
            val text = when(request.url.encodedPath) {
                "/web/web/web/index.asp" -> {
                    jar.saveFromResponse(request.url, listOf(Cookie.parse(request.url, "ASPSESSIONIDSYNTHETIC=test-only; Secure; Path=/")!!))
                    "<input name=sign value=test><input name=hour_key value=test>"
                }
                "/jiaoshi/bangong/check.asp" -> "<script>location='main.asp'</script>"
                "/xuesheng/dangan/banji/bjiben.asp" -> profile(if(sawRank && changeOwnerAtEnd) "synthetic-b" else owner)
                "/xuesheng/chengji/chengji/sear_ch_all.asp" -> "<p>合成导航页：无内容链接</p>"
                reportPath -> "<h1>个人成绩单</h1><table><tr><th>课程名称</th><th>成绩</th><th>学分</th></tr><tr><td>合成课</td><td>免修</td><td>02.00</td></tr></table><p>有效学分：02.00</p>"
                rankPath -> { sawRank = true; if(badRanking) "<h1>维护中</h1>" else "<h1>有效必修加权排名</h1><p>本人排名：03</p>" }
                else -> error("Unexpected route")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type", "text/html; charset=utf-8").body(text.toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, events::add)
        session.login(owner, "test-only-password")
        val result = session.transcript(owner)
        assertTrue(events.none { it.contains(owner) || it.contains("test-only") || it.contains("免修") || it.contains("合成课") })
        assertTrue(requests.contains(reportPath)); assertTrue(requests.contains(rankPath))
        return result to requests
    }
    @Test fun followsAuthenticatedLinksAndReturnsExactPersonalResults() = runBlocking {
        val report = read().first
        assertEquals("02.00", report.courses.single().credit)
        assertEquals("免修", report.courses.single().grade)
        assertEquals("03", report.ranking.single().value)
        assertEquals(SyncPhase.DATA, report.rankingPhase)
    }
    @Test fun confirmedContentEndpointsDoNotDependOnMenuLinkDiscovery() = runBlocking {
        val requests = read().second
        assertFalse(requests.contains("/xuesheng/chengji/chengji/sear_ch_all.asp"))
        assertTrue(requests.indexOf(reportPath) < requests.indexOf(rankPath))
        assertEquals(1, requests.count { it == reportPath })
        assertEquals(1, requests.count { it == rankPath })
    }
    @Test fun rankingFailureIsExplicitAndDoesNotEraseValidTranscript() = runBlocking {
        val report = read(badRanking = true).first
        assertEquals(1, report.courses.size)
        assertTrue(report.ranking.isEmpty())
        assertEquals(SyncPhase.PARSER_PENDING, report.rankingPhase)
    }
    @Test fun changedIdentityBeforeCompletionRejectsAllData() = runBlocking {
        try { read(changeOwnerAtEnd = true); fail("must reject") }
        catch(e: JiaowuException) { assertEquals(JiaowuFailure.IDENTITY_MISMATCH, e.reason) }
    }
}

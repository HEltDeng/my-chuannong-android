package com.mychuannong.app.school

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Synthetic in-process transport; does not claim real school/network acceptance. */
class SicauJiaowuSessionTest {
    @Test fun orderedLoginUsesFreshFieldsSameJarAndProfileProof() = runBlocking {
        val jar = SicauJiaowuCookieJar()
        var step = 0
        val trace = mutableListOf<String>()
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            val req = chain.request()
            val index = step++ % 3
            val round = (step - 1) / 3
            val body: String
            when (index) {
                0 -> {
                    assertEquals(SicauJiaowuProtocol.LOGIN_PAGE, req.url.toString())
                    assertTrue(jar.loadForRequest(req.url).isEmpty())
                    // The synthetic transport emulates response cookie handling; real OkHttp uses BridgeInterceptor.
                    jar.saveFromResponse(req.url, listOf(Cookie.parse(req.url, "ASPSESSIONIDTEST=synthetic-$round; Path=/; Secure")!!))
                    body = "<input name=sign value=dynamic-$round><input name=hour_key value=hour-$round>"
                }
                1 -> {
                    assertEquals(SicauJiaowuProtocol.LOGIN_POST, req.url.toString())
                    assertEquals("POST", req.method)
                    assertEquals(SicauJiaowuProtocol.LOGIN_PAGE, req.header("Referer"))
                    assertEquals(SicauJiaowuProtocol.ORIGIN, req.header("Origin"))
                    assertEquals("synthetic-$round", jar.loadForRequest(req.url).single().value)
                    val buffer = Buffer(); req.body!!.writeTo(buffer)
                    assertEquals("user=20260001&pwd=synthetic&lb=S&submit=&sign=dynamic-$round&hour_key=hour-$round", buffer.readUtf8())
                    body = ""
                }
                else -> {
                    assertEquals(SicauJiaowuProtocol.PERSONAL_INFO, req.url.toString())
                    assertEquals("synthetic-$round", jar.loadForRequest(req.url).single().value)
                    body = "<table><tr><td>学号</td><td>20260001</td></tr></table>"
                }
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(if(index == 1) 302 else 200)
                .message("synthetic").body(body.toByteArray(java.nio.charset.Charset.forName("GBK")).toResponseBody()).build()
        }.build()
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
        val session = SicauJiaowuSession(jar,client,trace::add)
        repeat(2) { assertEquals("20260001",session.login("20260001","synthetic").studentId) }
        assertEquals(6,step)
        assertEquals("20260001",session.verifiedId)
        assertTrue(trace.contains("identityMatch=true"))
        assertTrue(trace.none { it.contains("20260001") || it.contains("synthetic") || it.contains("dynamic-") || it.contains("hour-") })
    }
    @Test fun candidateWithoutMatchingProfileNeverBecomesVerified() = runBlocking {
        for (mismatch in listOf(false,true)) {
            val jar = SicauJiaowuCookieJar()
            var step = 0
            val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
                val request=chain.request()
                val body = when(step++) {
                    0 -> { jar.saveFromResponse(request.url,listOf(Cookie.parse(request.url,"ASPSESSIONIDTEST=synthetic; Path=/")!!)); "<input name=sign value=a><input name=hour_key value=b>" }
                    1 -> "<script>location='page.asp'</script>"
                    else -> if(mismatch) "<table><tr><td>学号</td><td>20260002</td></tr></table>" else "登录超时"
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                    .body(body.toByteArray(java.nio.charset.Charset.forName("GBK")).toResponseBody()).build()
            }.build()
            val session = SicauJiaowuSession(jar,client,{})
            try { session.login("20260001","synthetic");fail("Must not verify") }
            catch(e:JiaowuException) { assertEquals(if(mismatch) JiaowuFailure.IDENTITY_MISMATCH else JiaowuFailure.SESSION_EXPIRED,e.reason) }
            assertNull(session.verifiedId);assertFalse(jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        }
    }
}

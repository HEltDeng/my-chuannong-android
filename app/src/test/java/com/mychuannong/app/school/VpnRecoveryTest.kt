package com.mychuannong.app.school

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class VpnRecoveryTest {
    private val origin=SicauJiaowuRoute.VPN_ORIGIN
    private val realm=origin+"/https/"+"a".repeat(64)
    private val login=realm+"/wui/#/?appid=webvpn&service="+URLEncoder.encode(origin+"/login?cas_login=true","UTF-8")
    @Test fun vpnUsesExistingUnifiedSessionThenRechecksPortalWithoutPassword()=runBlocking {
        var vpnCookie=""; var tickets=0; var requests=0
        val cookies=object : SchoolSsoCookies {
            override suspend fun get(url: String)=if(url.startsWith(realm)) "sso=synthetic" else vpnCookie
            override suspend fun accept(url: String, cookies: List<String>) {
                if(cookies.isNotEmpty()) { assertTrue(url.startsWith(origin+"/login")); vpnCookie="vpn=renewed" }
            }
        }
        val client=OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val request=chain.request(); requests++; assertEquals("GET",request.method); assertNull(request.body)
            val response=Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
            val body=when {
                request.url.encodedPath.endsWith("/api/v1/cas/login") -> {
                    tickets++; assertEquals("sso=synthetic",request.header("Cookie"))
                    """{"code":200,"data":{"st":"synthetic-ticket","user":{"id":"id","number":"20260001"},"users":[]}}"""
                }
                request.url.encodedPath=="/login" -> {
                    assertEquals("synthetic-ticket:id",request.url.queryParameter("ticket"))
                    response.code(302).header("Location","/").header("Set-Cookie","vpn=renewed; Path=/; Secure")
                    ""
                }
                vpnCookie.isBlank() -> { response.code(302).header("Location",login); "" }
                else -> { assertEquals("vpn=renewed",request.header("Cookie")); "<html>portal</html>" }
            }
            response.body(body.toResponseBody()).build()
        }.build()
        assertTrue(SchoolSsoBroker(cookies,client).restoreVpn("20260001",realm))
        assertEquals(1,tickets); assertEquals(4,requests)
    }
    @Test fun foreignRedirectNeverReceivesSchoolCookies()=runBlocking {
        var requests=0
        val cookies=object : SchoolSsoCookies {
            override suspend fun get(url: String):String { assertTrue(url.startsWith(origin)); return "vpn=synthetic" }
            override suspend fun accept(url:String,cookies:List<String>) {}
        }
        val client=OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("synthetic")
                .header("Location","https://unrelated.invalid/login").body("".toResponseBody()).build()
        }.build()
        assertFalse(SchoolSsoBroker(cookies,client).restoreVpn("20260001",realm))
        assertEquals(1,requests)
    }
}

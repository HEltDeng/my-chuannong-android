package com.mychuannong.app.school

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class SchoolSsoBrokerTest {
    private val realm = "https://webvpn.sicau.edu.cn/https/" + "a".repeat(64)
    private val owner = "20260001"
    private fun login(service: String, app: String = "synthetic-app") =
        "https://auth.sicau.edu.cn/wui/#/?appid=$app&service=" + URLEncoder.encode(service, "UTF-8")
    private fun success(account: String = owner, users: String = """[{"id":"synthetic-id","number":"$account"}]""") =
        """{"code":200,"data":{"st":"synthetic-ticket","user":{"id":"synthetic-id","number":"$account"},"users":$users}}"""
    private val noCookies = object : SchoolSsoCookies {
        override suspend fun get(url: String) = ""
        override suspend fun accept(url: String, cookies: List<String>) = Unit
    }
    private fun client(reply: (Request) -> Pair<Int, String>, redirect: String? = null) =
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            val (code, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
                .apply { if(redirect != null) header("Location", redirect) }
                .body(body.toResponseBody()).build()
        }.build()

    @Test fun existingVpnRealmIssuesDifferentServiceTicketsWithoutDirectTgtOrPassword() = runBlocking {
        val visited = mutableListOf<String>()
        val cookies = object : SchoolSsoCookies {
            override suspend fun get(url: String) = if(url.startsWith("$realm/")) "vpn=synthetic-vpn" else ""
            override suspend fun accept(url: String, cookies: List<String>) { assertTrue(url.startsWith(realm)) }
        }
        val broker = SchoolSsoBroker(cookies, client({ request ->
            assertEquals("GET", request.method)
            assertNull(request.body)
            assertEquals("vpn=synthetic-vpn", request.header("Cookie"))
            assertEquals("$realm/wui/", request.header("Referer"))
            assertEquals("$realm/api/v1/cas/login", request.url.newBuilder().query(null).build().toString())
            visited.add(request.url.queryParameter("appid")!!)
            200 to success()
        }))
        for((app, service) in listOf("student" to "https://xsgz.sicau.edu.cn/callback?next=%2Fmobile",
            "ywtb" to "https://ywtb.sicau.edu.cn/cas/login/user")) {
            val result = broker.callback(requireNotNull(SchoolSsoRequest.parse(login(service, app))), owner, realm)!!.toHttpUrl()
            assertEquals(service.toHttpUrl().host, result.host)
            assertEquals("synthetic-ticket:synthetic-id", result.queryParameter("ticket"))
            assertEquals("synthetic-id", result.queryParameter("userId"))
            assertEquals(service.toHttpUrl().queryParameter("next"), result.queryParameter("next"))
        }
        assertEquals(listOf("student", "ywtb"), visited)
    }

    @Test fun ownerMismatchOrUnavailableOwnerRequiresOfficialUi() {
        val request = SchoolSsoRequest.parse(login("https://xsgz.sicau.edu.cn/callback"))!!
        listOf(success("20260002"), success(users = """[{"id":"delegate","number":"20260002"}]"""),
            success(users = """[{"id":"delegate","number":"20260001"}]"""),
            success(users = """[{"id":"synthetic-id","number":"20260002"}]"""),
            success(users = """[{"id":"synthetic-id","number":"20260001"},{"id":"synthetic-id","number":"20260001"}]"""),
            """{"code":401,"msg":"login expired"}""", """{"code":200,"data":{}}""").forEach {
            assertNull(request.callback(JsonParser.parseString(it).asJsonObject, owner))
        }
        assertNull(request.callback(JsonParser.parseString(success()).asJsonObject, ""))
    }

    @Test fun webOnlyLoginRequiresThePrimarySchoolIdentity() = runBlocking {
        val cookies = object : SchoolSsoCookies by noCookies {
            override suspend fun get(url: String) = if (url.startsWith("$realm/")) "vpn=synthetic" else ""
        }
        var reply = success()
        val broker = SchoolSsoBroker(cookies, client({ request ->
            assertEquals("GET", request.method)
            assertEquals("$realm/api/v1/cas/login?appid=sicau-cas", request.url.toString())
            200 to reply
        }))
        assertEquals(owner, broker.browserOwner(realm, null))
        try {
            broker.browserOwner(realm, "different-owner")
            fail("Mismatched school account")
        } catch (e: JiaowuException) { assertEquals(JiaowuFailure.IDENTITY_MISMATCH, e.reason) }
        reply = """{"code":200,"data":{"st":"synthetic","users":[{"id":"delegate","number":"20260001"}]}}"""
        assertNull(broker.browserOwner(realm, null))
    }

    @Test fun portalUsesOnlyVerifiedOwnerWhenSchoolAlsoListsDelegatedIdentities() {
        val service = "https://ywtb.sicau.edu.cn/#/pages/personmsg/personmsg"
        val request = SchoolSsoRequest.parse(login(service, "ywtb"))!!
        val response = success(users = """[{"id":"delegate","number":"20260002"},{"id":"synthetic-id","number":"20260001"}]""")
        val callback = request.callback(JsonParser.parseString(response).asJsonObject, owner)!!.toHttpUrl()
        assertEquals("synthetic-ticket:synthetic-id", callback.queryParameter("ticket"))
        assertEquals("synthetic-id", callback.queryParameter("userId"))
        assertEquals("/pages/personmsg/personmsg", callback.fragment)
    }

    @Test fun academicUnifiedCallbackAcceptsOnlyTheSchoolOwnedEntry() {
        val service = "https://jiaowu.sicau.edu.cn/jiaoshi/aspsso/caslogin.asp"
        val request = requireNotNull(SchoolSsoRequest.parse(login(service, "academic")))
        val callback = requireNotNull(request.callback(JsonParser.parseString(success()).asJsonObject, owner)).toHttpUrl()
        assertEquals("jiaowu.sicau.edu.cn", callback.host)
        assertEquals("/jiaoshi/aspsso/caslogin.asp", callback.encodedPath)
        assertEquals("synthetic-ticket:synthetic-id", callback.queryParameter("ticket"))
        val route = requireNotNull(SicauJiaowuRoute.fromResourcePage("$realm/jiaoshi/aspsso/caslogin.asp"))
        val proxied = requireNotNull(SchoolSsoNavigation.routeAcademicCallback(callback.toString(), route)).toHttpUrl()
        assertEquals("webvpn.sicau.edu.cn", proxied.host)
        assertEquals(route.prefix + callback.encodedPath, proxied.encodedPath)
        assertEquals(callback.queryParameter("ticket"), proxied.queryParameter("ticket"))
        assertNull(SchoolSsoNavigation.routeAcademicCallback(callback.toString(), null))
        assertNull(request.callback(JsonParser.parseString(success()).asJsonObject, "another-owner"))
        for (unsafe in listOf("$service?redirect=elsewhere", "https://jiaowu.sicau.edu.cn/other", "http://jiaowu.sicau.edu.cn/jiaoshi/aspsso/caslogin.asp"))
            assertNull(SchoolSsoRequest.parse(login(unsafe, "academic")))
    }

    @Test fun portalAndUnifiedAuthenticationKeepTheSameBusinessOriginDespiteCachedVpnRoutes() {
        val gateway = realm + "/"
        assertEquals(SicauAdapter.AUTH, SchoolWebPolicy.initialEntry("https://ywtb.sicau.edu.cn/", gateway))
        assertEquals(SicauAdapter.AUTH, SchoolWebPolicy.initialEntry(SicauAdapter.AUTH, gateway))
        val deepPage = "https://ywtb.sicau.edu.cn/#/pages/personmsg/personmsg"
        assertEquals(deepPage, SchoolWebPolicy.initialEntry(deepPage, gateway))
        val student = "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex"
        assertEquals(student, SchoolWebPolicy.initialEntry(student, gateway))
        assertEquals(gateway, SchoolWebPolicy.initialEntry("https://jiaowu.sicau.edu.cn/", gateway))
    }

    @Test fun preservesOfficialCallbackButRejectsForeignAndSpecialFlows() {
        val valid = login("https://ywtb.sicau.edu.cn/cas/login/user")
        assertNotNull(SchoolSsoRequest.parse(valid + "&cascallback=choose"))
        listOf(valid + "&cascallback=err", valid + "&uuid=challenge", valid + "&appid=another",
            valid.replace("/wui/", "/payment/"), valid.replace("#/?", "#/h5?"),
            login("https://evil.test/callback"), login("http://xsgz.sicau.edu.cn/callback"),
            login("https://user@xsgz.sicau.edu.cn/callback"), login("https://xsgz.sicau.edu.cn:444/callback"),
            login("https://xsgz.sicau.edu.cn/callback?ticket=old"),
            valid.replace("auth.sicau.edu.cn", "auth.sicau.edu.cn.evil.test")).forEach {
            assertNull(it, SchoolSsoRequest.parse(it))
        }
    }

    @Test fun discoveryUsesOfficialVpnRedirectWithoutSendingOrImportingCookies() = runBlocking {
        val redirect = login("https://webvpn.sicau.edu.cn/login?cas_login=true")
            .replace(SicauAuthProtocol.ORIGIN, realm)
        val broker = SchoolSsoBroker(noCookies, client({ request ->
            assertEquals(SicauJiaowuRoute.VPN_ORIGIN + "/", request.url.toString())
            assertNull(request.header("Cookie"))
            302 to ""
        }, redirect))
        assertEquals(realm, broker.discoverProxyRealm())
        assertNull(SchoolSsoRequest.vpnAuthRealm(redirect.replace("cas_login%3Dtrue", "cas_login%3Dfalse")))
        assertNull(SchoolSsoRequest.proxyRealm(realm.replace("webvpn.sicau.edu.cn", "evil.test")))
    }

    @Test fun expiredProxySessionDoesNotBecomeAnAuthorizedCallback() = runBlocking {
        val cookies = object : SchoolSsoCookies by noCookies {
            override suspend fun get(url: String) = if(url.startsWith("$realm/")) "vpn=synthetic-expired" else ""
        }
        val request = SchoolSsoRequest.parse(login("https://ywtb.sicau.edu.cn/cas/login/user"))!!
        for(status in listOf(200, 302, 401, 403)) {
            val broker = SchoolSsoBroker(cookies, client({ status to """{"code":401}""" }, "https://evil.test/"))
            assertNull(broker.callback(request, owner, realm))
        }
    }
}

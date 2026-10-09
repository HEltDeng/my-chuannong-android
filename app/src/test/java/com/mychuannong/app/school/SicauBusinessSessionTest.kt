package com.mychuannong.app.school
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
class SicauBusinessSessionTest {
 @Test fun verifiedSessionFetchesGbkDataWithSameJarAndSafeTrace()=runBlocking {
  val jar=SicauJiaowuCookieJar();val events=mutableListOf<String>();var stage=0
  val client=SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
   val req=chain.request();val step=stage++
   val body=when(step){
    0->{jar.saveFromResponse(req.url,listOf(Cookie.parse(req.url,"ASPSESSIONIDSYNTHETIC=secret-test; Secure; Path=/")!!));"<input name=sign value=fresh><input name=hour_key value=fresh-hour>"}
    1->"<script>location='main.asp'</script>"
    2->"<table><tr><td>学号</td><td>synthetic-a</td></tr></table>"
    3->{assertEquals(JiaowuPage.COURSES.url,req.url.toString());assertTrue(jar.hasSession(req.url.toString()));assertEquals(SicauJiaowuProtocol.PERSONAL_INFO,req.header("Referer"));"<h1>四川农业大学2026-2027-1学期</h1><table><tr><th>课程名称</th><th>周次</th><th>教室</th><th>上课时间</th></tr><tr><td>合成课</td><td>1-16</td><td>A</td><td>3-3,3-4</td></tr></table>"}
    else->"登录超时"
   }
   Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("synthetic").header("Content-Type","text/html; charset=gbk").body(body.toByteArray(java.nio.charset.Charset.forName("GBK")).toResponseBody()).build()
  }.build()
  val session=SicauJiaowuSession(jar,client,events::add);session.login("synthetic-a","synthetic-password")
  val batch=SicauBusinessParser.courses(session.fetchBusiness(JiaowuPage.COURSES),session.verifiedId!!)
  assertEquals("合成课",batch.courses.single().courseName)
  try{session.fetchBusiness(JiaowuPage.COURSES);fail("must expire")}catch(e:JiaowuException){assertEquals(JiaowuFailure.SESSION_EXPIRED,e.reason)}
  assertTrue(events.none {it.contains("synthetic-a") || it.contains("secret-test") || it.contains("fresh-hour") || it.contains("合成课")})
 }
 @Test fun cookieAloneCannotReadBusinessPages()=runBlocking {
  val session=SicauJiaowuSession(trace={})
  try{session.fetchBusiness(JiaowuPage.COURSES);fail("must reject")}catch(e:JiaowuException){assertEquals(JiaowuFailure.SESSION_EXPIRED,e.reason)}
 }
}

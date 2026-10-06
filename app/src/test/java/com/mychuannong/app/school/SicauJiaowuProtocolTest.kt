package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class SicauJiaowuProtocolTest {
    private fun failure(expected: JiaowuFailure, action: () -> Unit) {
        try { action(); fail("Expected $expected") }
        catch (e: JiaowuException) { assertEquals(expected, e.reason) }
    }
    @Test fun dynamicFieldsUseDomRegardlessOfOrderQuotesWhitespace() {
        for (html in listOf(
            "<input name=\"sign\" value=\"abc\"><input name=\"hour_key\" value=\"123\">",
            "<input value=\"abc\" name=\"sign\"><input value=\"123\" name=\"hour_key\">",
            "<input name='sign' value='abc'><input name='hour_key' value='123'>",
            "<input value = 'abc' type='hidden'   name = 'sign'><input id='x' value='123' name = 'hour_key'>"
        )) {
            val result = SicauJiaowuProtocol.loginFields(html)
            assertEquals("abc", result.sign); assertEquals("123", result.hourKey)
        }
    }
    @Test fun missingEmptyOrAmbiguousFieldsHaveNoFallback() {
        for (html in listOf("<input name=sign value=abc>", "<input name=hour_key value=123>",
            "<input name=sign value=''><input name=hour_key value=123>",
            "<input name=sign value=a><input name=sign value=b><input name=hour_key value=123>"))
            failure(JiaowuFailure.LOGIN_PAGE_PARSE_FAILED) { SicauJiaowuProtocol.loginFields(html) }
    }
    @Test fun redirectsAreCandidatesOnly() {
        assertEquals(JiaowuLoginClassification.REDIRECT_SUCCESS, SicauJiaowuProtocol.classifyLogin(302, ""))
        for (script in listOf("location='page.asp'", "window.location='page.asp'", "location.href='page.asp'", "window.navigate('page.asp')"))
            assertEquals(JiaowuLoginClassification.SCRIPT_SUCCESS, SicauJiaowuProtocol.classifyLogin(200, "<script>$script</script>"))
    }
    @Test fun explicitFailuresOverrideRedirectAndUnknown200FailsClosed() {
        failure(JiaowuFailure.CREDENTIAL_REJECTED) { SicauJiaowuProtocol.classifyLogin(200, "alert('用户名或密码错误！');location='index.asp'") }
        failure(JiaowuFailure.SCHOOL_VALIDATION_REJECTED) { SicauJiaowuProtocol.classifyLogin(200, "alert('验证失败，请通过正常链接重新登录！')") }
        failure(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN) { SicauJiaowuProtocol.classifyLogin(200, "欢迎") }
        failure(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN) { SicauJiaowuProtocol.classifyLogin(200, "alert('未知错误');location='index.asp'") }
        failure(JiaowuFailure.CREDENTIAL_REJECTED) { SicauJiaowuProtocol.classifyLogin(302, "密码错误") }
    }
    @Test fun gbkBytesDecodeBeforeClassificationAndExpiryDetection() {
        val gbk = Charset.forName("GBK")
        fun decoded(s: String) = SicauJiaowuProtocol.decode(s.toByteArray(gbk), "text/html")
        failure(JiaowuFailure.CREDENTIAL_REJECTED) { SicauJiaowuProtocol.classifyLogin(200, decoded("用户名或密码错误！")) }
        failure(JiaowuFailure.SCHOOL_VALIDATION_REJECTED) { SicauJiaowuProtocol.classifyLogin(200, decoded("验证失败，请通过正常链接重新登录！")) }
        failure(JiaowuFailure.SESSION_EXPIRED) { SicauJiaowuProtocol.personalInfo(decoded("登录超时")) }
        assertEquals("登录超时", SicauJiaowuProtocol.decode("登录超时".toByteArray(), "text/html; charset=UTF-8"))
    }
    private fun profile(id: String) = """
        <table class='tablebody'><tr class='g_column'>
        <td>姓名</td><td>专业</td><td>学生学号</td><td>学院</td><td>年级</td><td>班级</td><td>校区</td></tr>
        <tr class='g_body_2'><td>测试学生</td><td>测试专业</td><td>$id</td><td>测试学院</td><td>2026</td><td>测试班级</td><td>测试校区</td></tr></table>
    """.trimIndent()
    @Test fun profileHeaderMappingAndStrictIdentity() {
        assertEquals("20260001", SicauJiaowuProtocol.verifyIdentity(profile("20260001"), "20260001").studentId)
        failure(JiaowuFailure.IDENTITY_MISMATCH) { SicauJiaowuProtocol.verifyIdentity(profile("20260002"), "20260001") }
        assertEquals("20260001", SicauJiaowuProtocol.personalInfo("<table><tr><th>学号：</th><td>20260001</td></tr></table>").studentId)
    }
    @Test fun expiredLoginFormsMissingIdsAndAmbiguousOwnersNeverParseSuccessfully() {
        for (message in listOf("登录超时", "账号验证失败", "请重新登录", "<input type=password>"))
            failure(JiaowuFailure.SESSION_EXPIRED) { SicauJiaowuProtocol.personalInfo(message + profile("20260001")) }
        failure(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED) { SicauJiaowuProtocol.personalInfo("<a href='?studentId=20260001'>20260001</a>") }
        failure(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED) { SicauJiaowuProtocol.personalInfo(profile("20260001") + profile("20260002")) }
        failure(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED) { SicauJiaowuProtocol.personalInfo(profile("20260001").replace("</table>", "<tr class=g_body_2><td>20260002</td></tr></table>")) }
    }
}

package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SicauLoginResponseSignalsTest {
    @Test fun observedSchoolLockoutIsDifferentFromAnUnclassifiedAlert() {
        val signals = SicauLoginResponseSignals.inspect("<script>alert('对不起，今天密码【含验证码】输入错误超过5次，疑似非法操作已被系统锁定，请明天再试！');history.back()</script>")
        assertTrue(signals.containsAll(setOf(SicauLoginSignal.ACCOUNT_LOCKED,
            SicauLoginSignal.TOO_MANY_ATTEMPTS, SicauLoginSignal.SECURITY_WARNING,
            SicauLoginSignal.CREDENTIAL_ERROR, SicauLoginSignal.HAS_ALERT)))
        assertEquals(setOf(SicauLoginSignal.HAS_ALERT), SicauLoginResponseSignals.inspect("<script>alert('未知学校提示')</script>"))
    }

    @Test fun javascriptUnicodeEscapesAreRecognizedWithoutExecutingCode() {
        val escaped = "账号已被系统锁定".map { "\\u" + it.code.toString(16).padStart(4, '0') }.joinToString("")
        val signals = SicauLoginResponseSignals.inspect("<script>window.alert(\"$escaped\")</script>")
        assertTrue(signals.contains(SicauLoginSignal.ACCOUNT_LOCKED))
        assertTrue(signals.contains(SicauLoginSignal.HAS_ALERT))
    }

    @Test fun challengeAndSessionRejectionAreSeparateEvidence() {
        val sms = SicauLoginResponseSignals.inspect("<script>alert('请通过验证码重新登录教务系统并及时修改密码')</script>")
        assertTrue(sms.containsAll(setOf(SicauLoginSignal.SMS_REQUIRED, SicauLoginSignal.PASSWORD_NOTICE)))
        assertFalse(sms.contains(SicauLoginSignal.CREDENTIAL_ERROR))
        assertFalse(sms.contains(SicauLoginSignal.ACCOUNT_LOCKED))
        assertTrue(SicauLoginResponseSignals.inspect("验证失败，请通过正常链接重新登录").contains(SicauLoginSignal.SESSION_VALIDATION))
        assertTrue(SicauLoginResponseSignals.inspect("请输入图形验证码").contains(SicauLoginSignal.CAPTCHA_REQUIRED))
    }

    @Test fun diagnosticsContainOnlyEnumsEvenIfServerEchoesSensitiveValues() {
        val html = "<input name=user value='synthetic-student'><input type=password value='synthetic-secret'>" +
            "<script>alert('synthetic-student synthetic-secret synthetic-cookie');location='/?ticket=synthetic-ticket'</script>"
        val result = SicauLoginResponseSignals.inspect(html).joinToString(",") { it.name }
        assertTrue(Regex("[A-Z_,]+").matches(result))
        assertFalse(result.contains("synthetic"))
        assertTrue(result.contains("HAS_LOGIN_FORM"))
        assertTrue(result.contains("HAS_REDIRECT"))
    }

    @Test fun evidenceDoesNotTurnUnknownResponseIntoAuthenticationSuccess() {
        val html = "<script>alert('欢迎回来');location='home.asp'</script>"
        assertTrue(SicauLoginResponseSignals.inspect(html).contains(SicauLoginSignal.HAS_REDIRECT))
        try { SicauJiaowuProtocol.classifyLogin(200, html); fail("Still requires verified authentication") }
        catch (e: JiaowuException) { assertEquals(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN, e.reason) }
        assertEquals(setOf(SicauLoginSignal.EMPTY_RESPONSE), SicauLoginResponseSignals.inspect("  "))
    }
}

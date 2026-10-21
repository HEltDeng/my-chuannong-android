package com.mychuannong.app.school

import org.jsoup.Jsoup

/** Debug evidence only. These flags never grant access or replace identity verification. */
internal enum class SicauLoginSignal {
    ACCOUNT_LOCKED, TOO_MANY_ATTEMPTS, SECURITY_WARNING, ACCESS_DENIED,
    CREDENTIAL_ERROR, SESSION_VALIDATION, SMS_REQUIRED, PASSWORD_NOTICE,
    CAPTCHA_REQUIRED, HAS_ALERT, HAS_REDIRECT, HAS_LOGIN_FORM, EMPTY_RESPONSE
}

/** Emits only fixed enum names, never a response, credential, identifier, URL or cookie. */
internal object SicauLoginResponseSignals {
    private val alerts = Regex("""\b(?:window\s*\.\s*)?alert\s*\(\s*("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""", RegexOption.IGNORE_CASE)
    private val escaped = Regex("""\\(?:u([0-9a-fA-F]{4})|x([0-9a-fA-F]{2})|([\s\S]))""")
    private fun literal(value: String) = escaped.replace(value.substring(1, value.length - 1)) {
        when {
            it.groupValues[1].isNotEmpty() -> it.groupValues[1].toInt(16).toChar().toString()
            it.groupValues[2].isNotEmpty() -> it.groupValues[2].toInt(16).toChar().toString()
            it.groupValues[3] in listOf("n", "r", "t") -> " "
            else -> it.groupValues[3]
        }
    }

    fun inspect(html: String): Set<SicauLoginSignal> {
        if (html.isBlank()) return setOf(SicauLoginSignal.EMPTY_RESPONSE)
        val document = Jsoup.parse(html)
        val messages = alerts.findAll(html).take(16).map { literal(it.groupValues[1]) }.toList()
        // These are page-content hints; finding an alert literal does not prove its branch ran.
        val text = (listOf(document.body().text()) + messages).joinToString(" ")
        fun has(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
        return buildSet {
            if (has("(?:账号|账户|系统).{0,16}锁定|已被.{0,8}锁定|账户.{0,8}冻结")) add(SicauLoginSignal.ACCOUNT_LOCKED)
            if (has("(?:次数|操作|登录|登陆|访问).{0,16}(?:过多|频繁)|(?:错误|失败).{0,16}(?:超过|超出)|too many requests")) add(SicauLoginSignal.TOO_MANY_ATTEMPTS)
            if (has("风控|疑似非法|非法操作|异常登录|异常登陆|风险验证|安全验证|访问异常|异常访问")) add(SicauLoginSignal.SECURITY_WARNING)
            if (has("禁止访问|拒绝访问|access denied|forbidden")) add(SicauLoginSignal.ACCESS_DENIED)
            if (has("(?:密码|口令|验证码).{0,24}(?:错误|不正确|有误)|用户名或密码|账号不存在|学号不存在")) add(SicauLoginSignal.CREDENTIAL_ERROR)
            if (has("验证失败|正常链接|会话.{0,12}(?:失效|过期)")) add(SicauLoginSignal.SESSION_VALIDATION)
            if (has("短信验证|手机验证码|通过验证码|验证码.{0,24}(?:重新登录|重新登陆|修改密码)")) add(SicauLoginSignal.SMS_REQUIRED)
            if (has("(?:修改|更改|重置).{0,8}密码|密码.{0,12}(?:过期|泄露|泄漏|简单)|弱密码")) add(SicauLoginSignal.PASSWORD_NOTICE)
            if (has("图形验证码|图片验证码|滑动验证|人机验证|captcha")) add(SicauLoginSignal.CAPTCHA_REQUIRED)
            if (messages.isNotEmpty()) add(SicauLoginSignal.HAS_ALERT)
            if (Regex("""(?:window\.)?location(?:\.href)?\s*=|window\.navigate\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(html)) add(SicauLoginSignal.HAS_REDIRECT)
            if (document.select("input[type=password], input[name=hour_key]").isNotEmpty()) add(SicauLoginSignal.HAS_LOGIN_FORM)
        }
    }
}

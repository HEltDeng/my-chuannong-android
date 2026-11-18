package com.mychuannong.app.school

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.jsoup.Jsoup
import java.nio.charset.Charset

internal enum class JiaowuFailure(val userMessage: String) {
    AUTO_LOGIN_NOT_ENABLED("学校会话需要验证，上次数据已保留"),
    AUTO_LOGIN_STOPPED("学校会话恢复未完成，上次数据已保留"),
    AUTO_LOGIN_COOLDOWN("教务自动恢复暂未成功，请稍后下拉重试；上次数据已保留"),
    CAPTCHA_REQUIRED("学校要求验证码或二次验证，请打开学校页面完成验证；上次数据已保留"),
    WEBVPN_REQUIRED("请先完成校园 VPN 绑定，再继续登录；本地缓存仍可查看"),
    NETWORK_UNREACHABLE("暂时无法连接学校教务，请检查网络后重试"),
    LOGIN_PAGE_HTTP_ERROR("学校教务登录页暂时无法访问"),
    LOGIN_PAGE_PARSE_FAILED("学校教务登录页验证字段读取失败，请重试"),
    SESSION_COOKIE_MISSING("学校教务会话未建立，请重新登录"),
    SESSION_UNAVAILABLE("登录信息暂未恢复，请重试；仍不可用时请重新登录教务系统"),
    CREDENTIAL_REJECTED("学校教务未接受学号或密码，请核对后重试"),
    SCHOOL_VALIDATION_REJECTED("学校教务会话验证失败，请重新登录"),
    SMS_VERIFICATION_REQUIRED("学校要求短信验证，请打开学校页面完成验证后继续绑定"),
    PASSWORD_CHANGE_REQUIRED("学校要求处理密码安全提示，请打开学校页面完成后继续绑定"),
    LOGIN_RESPONSE_UNKNOWN("学校返回的登录提示暂未识别，请打开学校页面核对并继续验证"),
    PERSONAL_INFO_HTTP_ERROR("学校本人信息暂时无法访问"),
    SESSION_EXPIRED("学校教务登录已过期，请重新认证"),
    PERSONAL_INFO_PARSE_FAILED("学校本人学号读取失败，尚未完成绑定"),
    IDENTITY_MISMATCH("学校返回的学号与输入不一致，未完成绑定"),
    BUSINESS_HTTP_ERROR("教务数据页请求失败，保留本机缓存"),
    BUSINESS_STRUCTURE("教务数据表格式无法确认，保留本机缓存"),
    BUSINESS_REDIRECT("教务数据页发生跳转，请重新进入学校教务"),
    TERM_UNCONFIRMED("学校课表学期无法确认，保留本机缓存"),
    COURSE_MAPPING("学校课程时间或教室无法对应，保留本机缓存"),
    LOCAL_STORAGE_FAILED("无法保存本机教务会话，请重试")
}

internal class JiaowuException(val reason: JiaowuFailure) : Exception(reason.name)
/** Discard invalid online credentials; the authenticated binding receipt remains valid. */
internal val JiaowuFailure.invalidatesSession: Boolean
    get() = this == JiaowuFailure.SESSION_EXPIRED || this == JiaowuFailure.IDENTITY_MISMATCH
internal val JiaowuFailure.requiresLogin: Boolean
    get() = invalidatesSession || this == JiaowuFailure.SESSION_COOKIE_MISSING || this == JiaowuFailure.SESSION_UNAVAILABLE
internal val JiaowuFailure.supportsSchoolVerification: Boolean
    get() = this in setOf(JiaowuFailure.SMS_VERIFICATION_REQUIRED, JiaowuFailure.PASSWORD_CHANGE_REQUIRED,
        JiaowuFailure.LOGIN_RESPONSE_UNKNOWN, JiaowuFailure.SCHOOL_VALIDATION_REJECTED, JiaowuFailure.CAPTCHA_REQUIRED)
internal enum class JiaowuLoginClassification { REDIRECT_SUCCESS, SCRIPT_SUCCESS }
internal class JiaowuLoginFields(val sign: String, val hourKey: String, val action: String = "",
    val charset: Charset = Charset.forName("GBK"))
// No data-class toString: personal identity and dynamic fields must never enter diagnostics.
internal class JiaowuPersonalInfo(val studentId: String, val name: String? = null, val grade: String? = null, val admissionYear: Int? = null)

/** Independently implemented from the school's HTTP contract. No fallback tokens or inferred owner. */
internal object SicauJiaowuProtocol {
    const val ORIGIN = "https://jiaowu.sicau.edu.cn"
    const val LOGIN_PAGE = "$ORIGIN/web/web/web/index.asp"
    const val LOGIN_POST = "$ORIGIN/jiaoshi/bangong/check.asp"
    const val PERSONAL_INFO = "$ORIGIN/xuesheng/dangan/banji/bjiben.asp"
    private val gbk = Charset.forName("GBK")

    private fun charset(label: String?): Charset? = when (label?.trim()?.lowercase()) {
        "utf-8", "utf8" -> Charsets.UTF_8
        "gb2312", "gbk", "x-gbk", "gb_2312-80" -> gbk
        else -> null
    }
    private fun documentCharset(html: String, contentType: String?): Charset {
        val header = runCatching { contentType?.toMediaTypeOrNull()?.charset()?.name() }.getOrNull()
        charset(header)?.let { return it }
        val doc = Jsoup.parse(html)
        for (meta in doc.select("meta")) {
            val label = meta.attr("charset").ifBlank {
                Regex("charset\\s*=\\s*['\"]?([^\\s;'\"]+)", RegexOption.IGNORE_CASE)
                    .find(meta.attr("content"))?.groupValues?.get(1).orEmpty()
            }
            charset(label)?.let { return it }
        }
        return gbk
    }

    fun decode(bytes: ByteArray, contentType: String?): String {
        val encoding = documentCharset(String(bytes.take(1024).toByteArray(), Charsets.ISO_8859_1), contentType)
        return String(bytes, encoding).removePrefix("\uFEFF")
    }

    fun loginFields(html: String, contentType: String? = null,
        presence: (Boolean, Boolean) -> Unit = { _, _ -> }): JiaowuLoginFields {
        val document = Jsoup.parse(html)
        if (document.select("input").any { input ->
                val marker = listOf(input.attr("name"), input.id(), input.attr("autocomplete"), input.attr("placeholder")).joinToString(" ")
                input.attr("type") != "hidden" && Regex("captcha|verifycode|checkcode|vcode|one-time-code|验证码", RegexOption.IGNORE_CASE).containsMatchIn(marker)
            }) throw JiaowuException(JiaowuFailure.CAPTCHA_REQUIRED)
        fun field(name: String): String? = document.select("input[name=$name]").singleOrNull()
            ?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }
        val sign = field("sign"); val hour = field("hour_key")
        presence(sign != null, hour != null)
        if (sign == null || hour == null) throw JiaowuException(JiaowuFailure.LOGIN_PAGE_PARSE_FAILED)
        val form = document.select("input[name=hour_key]").first()?.closest("form")
        val encoding = form?.attr("accept-charset")?.split(Regex("\\s+"))?.firstNotNullOfOrNull(::charset)
            ?: documentCharset(html, contentType)
        return JiaowuLoginFields(sign, hour, form?.attr("action").orEmpty(), encoding)
    }

    private fun schoolRequirement(text: String): JiaowuFailure? = when {
        Regex("短信验证|手机验证码|通过验证码|验证码.{0,24}(?:重新登录|重新登陆|修改密码)").containsMatchIn(text) ->
            JiaowuFailure.SMS_VERIFICATION_REQUIRED
        Regex("(?:必须|强制|需要|请立即).{0,16}(?:修改|更改|重置).{0,8}密码|密码.{0,12}(?:过期|泄露|泄漏|过于简单)|弱密码").containsMatchIn(text) ->
            JiaowuFailure.PASSWORD_CHANGE_REQUIRED
        else -> null
    }

    fun classifyLogin(status: Int, html: String): JiaowuLoginClassification {
        // Reject explicit failures before considering redirects, including scripts that go back to login.
        if (Regex("验证失败|正常链接").containsMatchIn(html))
            throw JiaowuException(JiaowuFailure.SCHOOL_VALIDATION_REJECTED)
        schoolRequirement(html)?.let { throw JiaowuException(it) }
        if (Regex("密码错误|用户名或密码|账号不存在|学号不存在").containsMatchIn(html))
            throw JiaowuException(JiaowuFailure.CREDENTIAL_REJECTED)
        if (Regex("alert\\s*\\(|登录失败", RegexOption.IGNORE_CASE).containsMatchIn(html))
            throw JiaowuException(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN)
        if (status in 300..399) return JiaowuLoginClassification.REDIRECT_SUCCESS
        if (status == 200 && Regex("(?:window\\.)?location(?:\\.href)?\\s*=|window\\.navigate\\s*\\(",
                RegexOption.IGNORE_CASE).containsMatchIn(html)) return JiaowuLoginClassification.SCRIPT_SUCCESS
        throw JiaowuException(JiaowuFailure.LOGIN_RESPONSE_UNKNOWN)
    }

    fun personalInfo(html: String): JiaowuPersonalInfo {
        val doc = Jsoup.parse(html)
        // An identity table never overrides an outstanding school security requirement.
        schoolRequirement(doc.body().text())?.let { throw JiaowuException(it) }
        // The authenticated profile contains this help text even when its identity table is valid.
        // Exclude only that observed instruction; actual expiry notices/forms still fail closed.
        val sessionSignals = html.replace("提交后重新登录便可正常访问教务系统", "")
        if (Regex("登录超时|账号验证失败|重新登录").containsMatchIn(sessionSignals) ||
            doc.select("input[type=password], input[name=hour_key]").isNotEmpty())
            throw JiaowuException(JiaowuFailure.SESSION_EXPIRED)
        fun label(value: String) = value.replace(Regex("[\\s\u00a0：:]"), "")
        val ids = mutableListOf<String>()
        val names = mutableListOf<String>()
        val grades = mutableListOf<String>()
        val years = mutableListOf<Int>()
        fun collect(key: String, value: String) {
            if (label(key) in setOf("学号", "学生学号")) ids += value.trim()
            if (label(key) in setOf("姓名", "学生姓名")) names += value.trim()
            if (label(key) in setOf("年级","所在年级","入学年份","入学年级")) {
                val text=value.trim()
                if (text in setOf("大一","大二","大三","大四","大五","大六","研一","研二","研三","研四")) grades += text
                Regex("^(20[0-9]{2})(?:级|年)?$").matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()?.let { years += it }
            }
        }
        for (table in doc.select("table.tablebody")) {
            val headers = table.select("tr.g_column")
            val rows = table.select("tr.g_body_2")
            if (headers.size != 1 || rows.size != 1) {
                if (headers.isNotEmpty() || rows.isNotEmpty())
                    throw JiaowuException(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED)
                continue
            }
            val keys = headers.single().children().filter { it.tagName() in setOf("td", "th") }
            val values = rows.single().children().filter { it.tagName() in setOf("td", "th") }
            if (keys.size != values.size) throw JiaowuException(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED)
            keys.indices.forEach { collect(keys[it].text(), values[it].text()) }
        }
        // Legacy field/value tables only; never search arbitrary page text, links or input values.
        for (row in doc.select("tr")) {
            if (row.hasClass("g_column") || row.hasClass("g_body_2")) continue
            val cells = row.children().filter { it.tagName() in setOf("td", "th") }
            for (i in 0 until cells.size - 1 step 2) collect(cells[i].text(), cells[i + 1].text())
        }
        val id = ids.distinct().singleOrNull()?.takeIf { Regex("[0-9A-Za-z_-]{4,32}").matches(it) }
            ?: throw JiaowuException(JiaowuFailure.PERSONAL_INFO_PARSE_FAILED)
        return JiaowuPersonalInfo(id, names.distinct().singleOrNull()?.takeIf { it.length in 2..40 }, grades.distinct().singleOrNull(), years.distinct().singleOrNull())
    }

    fun verifyIdentity(html: String, entered: String): JiaowuPersonalInfo = personalInfo(html).also {
        if (it.studentId != entered) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
    }
}

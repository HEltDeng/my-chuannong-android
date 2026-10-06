package com.mychuannong.app.school

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.jsoup.Jsoup
import java.nio.charset.Charset

internal enum class JiaowuFailure(val userMessage: String) {
    NETWORK_UNREACHABLE("暂时无法连接学校教务，请检查网络后重试"),
    LOGIN_PAGE_HTTP_ERROR("学校教务登录页暂时无法访问"),
    LOGIN_PAGE_PARSE_FAILED("学校教务登录页验证字段读取失败，请重试"),
    SESSION_COOKIE_MISSING("学校教务会话未建立，请重新登录"),
    CREDENTIAL_REJECTED("学校教务未接受学号或密码，请核对后重试"),
    SCHOOL_VALIDATION_REJECTED("学校教务会话验证失败，请重新登录"),
    LOGIN_RESPONSE_UNKNOWN("学校教务登录结果未确认，请重试"),
    PERSONAL_INFO_HTTP_ERROR("学校本人信息暂时无法访问"),
    SESSION_EXPIRED("学校教务登录已过期，请重新认证"),
    PERSONAL_INFO_PARSE_FAILED("学校本人学号读取失败，尚未完成绑定"),
    IDENTITY_MISMATCH("学校返回的学号与输入不一致，未完成绑定"),
    LOCAL_STORAGE_FAILED("无法保存本机教务会话，请重试")
}

internal class JiaowuException(val reason: JiaowuFailure) : Exception(reason.name)
internal enum class JiaowuLoginClassification { REDIRECT_SUCCESS, SCRIPT_SUCCESS }
internal class JiaowuLoginFields(val sign: String, val hourKey: String)
// No data-class toString: personal identity and dynamic fields must never enter diagnostics.
internal class JiaowuPersonalInfo(val studentId: String)

/** Independently implemented from the school's HTTP contract. No fallback tokens or inferred owner. */
internal object SicauJiaowuProtocol {
    const val ORIGIN = "https://jiaowu.sicau.edu.cn"
    const val LOGIN_PAGE = "$ORIGIN/web/web/web/index.asp"
    const val LOGIN_POST = "$ORIGIN/jiaoshi/bangong/check.asp"
    const val PERSONAL_INFO = "$ORIGIN/xuesheng/dangan/banji/bjiben.asp"
    private val gbk = Charset.forName("GBK")

    fun decode(bytes: ByteArray, contentType: String?): String {
        val charset = runCatching { contentType?.toMediaTypeOrNull()?.charset(gbk) }.getOrNull() ?: gbk
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    fun loginFields(html: String, presence: (Boolean, Boolean) -> Unit = { _, _ -> }): JiaowuLoginFields {
        val document = Jsoup.parse(html)
        fun field(name: String): String? = document.select("input[name=$name]").singleOrNull()
            ?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }
        val sign = field("sign"); val hour = field("hour_key")
        presence(sign != null, hour != null)
        if (sign == null || hour == null) throw JiaowuException(JiaowuFailure.LOGIN_PAGE_PARSE_FAILED)
        return JiaowuLoginFields(sign, hour)
    }

    fun classifyLogin(status: Int, html: String): JiaowuLoginClassification {
        // Reject explicit failures before considering redirects, including scripts that go back to login.
        if (Regex("验证失败|正常链接").containsMatchIn(html))
            throw JiaowuException(JiaowuFailure.SCHOOL_VALIDATION_REJECTED)
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
        if (Regex("登录超时|账号验证失败|重新登录").containsMatchIn(html) ||
            doc.select("input[type=password], input[name=hour_key]").isNotEmpty())
            throw JiaowuException(JiaowuFailure.SESSION_EXPIRED)
        fun label(value: String) = value.replace(Regex("[\\s\u00a0：:]"), "")
        val ids = mutableListOf<String>()
        fun collect(key: String, value: String) {
            if (label(key) in setOf("学号", "学生学号")) ids += value.trim()
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
        return JiaowuPersonalInfo(id)
    }

    fun verifyIdentity(html: String, entered: String): JiaowuPersonalInfo = personalInfo(html).also {
        if (it.studentId != entered) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
    }
}

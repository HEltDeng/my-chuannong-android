package com.mychuannong.app.school

import com.mychuannong.app.SafeUrl
import java.net.URI

enum class SchoolServiceAuth { NONE, UNIFIED, ACADEMIC }

/** Add services here with their official destination and authentication realm. */
data class SchoolService(val title: String, val url: String, val auth: SchoolServiceAuth) {
    init { require(title.isNotBlank() && SafeUrl.school(url)) }
}

object SchoolServices {
    val entries = linkedMapOf(
        "comprehensive_score" to SchoolService("综测成绩", "https://xsgz.sicau.edu.cn/mobile/scoreLocus/list", SchoolServiceAuth.UNIFIED),
        "weekly_attendance" to SchoolService("周点名", "https://xsgz.sicau.edu.cn/mobile/signTask", SchoolServiceAuth.UNIFIED),
        "holiday_destination" to SchoolService("节假日去向", "https://xsgz.sicau.edu.cn/mobile/leaveSchoolRegister", SchoolServiceAuth.UNIFIED),
        "student-affairs" to SchoolService("学工系统", "https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex", SchoolServiceAuth.UNIFIED),
        "academic" to SchoolService("教务系统", SicauJiaowuProtocol.PERSONAL_INFO, SchoolServiceAuth.ACADEMIC),
        "ywtb" to SchoolService("一网通办", "https://ywtb.sicau.edu.cn/", SchoolServiceAuth.UNIFIED),
        "auth" to SchoolService("统一认证", SicauAdapter.AUTH, SchoolServiceAuth.UNIFIED),
        "payments" to SchoolService("校园缴费", "https://op.sicau.edu.cn/CasLogin.aspx", SchoolServiceAuth.UNIFIED),
    )

    fun forUrl(url: String): SchoolService? = entries.values.firstOrNull { it.url == url }
        ?: entries["academic"].takeIf { url == SicauJiaowuProtocol.LOGIN_PAGE }
}

/** URL-only navigation state. Never stores tickets, credentials or WebView history. */
internal class SchoolServiceJourney(val service: SchoolService) {
    var reachedTarget = false
        private set
    private var returns = 0

    fun reset() { reachedTarget = false; returns = 0 }
    fun authenticationRequired() { if(reachedTarget) reset() }

    fun isAuthentication(url: String): Boolean = SchoolWebPolicy.isAuthentication(url) ||
        url.substringBefore('?').substringBefore('#') == SicauJiaowuProtocol.LOGIN_PAGE

    fun isNeutralLanding(url: String): Boolean {
        if (!SafeUrl.school(url) || isAuthentication(url)) return false
        val current = URI(url.substringBefore('#'))
        val target = URI(service.url.substringBefore('#'))
        if (current.host != target.host) return false
        return current.path in setOf("", "/", "/index.html") || SchoolWebPolicy.isStudentHall(url)
    }

    fun isTarget(url: String): Boolean {
        if (!SafeUrl.school(url) || isAuthentication(url)) return false
        val current = URI(url.substringBefore('#'))
        val target = URI(service.url.substringBefore('#'))
        // A configured official authentication entry may redirect to its own business destination.
        if (SchoolWebPolicy.isAuthentication(service.url)) {
            val callbackHost = SchoolSsoRequest.parse(service.url)?.service?.let { URI(it.substringBefore('#')).host } ?: target.host
            return current.host == callbackHost
        }
        if (current.host != target.host) return false
        if (target.path in setOf("", "/")) return true
        return current.path.trimEnd('/') == target.path.trimEnd('/') &&
            (target.rawQuery == null || current.rawQuery == target.rawQuery) &&
            (!service.url.contains('#') || url.substringAfter('#', "") == service.url.substringAfter('#'))
    }

    enum class Result { READY, RETURN_TO_TARGET, WAIT, FAILED }
    fun finished(url: String): Result {
        if (reachedTarget) return Result.READY
        if (isTarget(url)) { reachedTarget = true; return Result.READY }
        val academicLanding = service.auth == SchoolServiceAuth.ACADEMIC && SafeUrl.school(url) &&
            !isAuthentication(url) && URI(url.substringBefore('#')).host == URI(service.url.substringBefore('#')).host
        if (isNeutralLanding(url) || academicLanding) {
            if (returns++ == 0) return Result.RETURN_TO_TARGET
            return Result.FAILED
        }
        return Result.WAIT
    }

    fun exitsToServices(url: String): Boolean = reachedTarget && isNeutralLanding(url) && !isTarget(url)
}

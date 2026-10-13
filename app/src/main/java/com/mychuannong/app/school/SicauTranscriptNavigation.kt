package com.mychuannong.app.school

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class TranscriptLink(val title: String, val url: String)
/** Follow only actual read-only academic links, preserving the selected direct/WebVPN route. */
internal object SicauTranscriptNavigation {
    // Content-page links supplied by the user from the authenticated school UI.
    const val TRANSCRIPT_URL = "https://jiaowu.sicau.edu.cn/xuesheng/chengji/chengji/bchengjike.asp"
    const val RANKING_URL = "https://jiaowu.sicau.edu.cn/xuesheng/chengji/chengji/zytongbf.asp"
    private val titles = setOf("成绩信息", "成绩查询", "个人成绩单", "有效必修加权排名")
    fun links(html: String, source: String, route: SicauJiaowuRoute, account: String): List<TranscriptLink> {
        val doc = SicauTranscriptParser.document(html)
        return doc.select("a[href],frame[src],iframe[src]").mapNotNull { element ->
            val frame = element.tagName() in setOf("frame", "iframe")
            val title = element.text().replace(Regex("\\s+"), "")
                .removeSurrounding("【", "】").removeSurrounding("[", "]")
            if(!frame && title !in titles) return@mapNotNull null
            val raw = element.attr(if(frame) "src" else "href")
            val url = resolve(raw, source, route, account) ?: return@mapNotNull null
            TranscriptLink(if(frame) "成绩查询" else title, url)
        }.distinctBy { it.url }
    }
    fun resolve(raw: String, source: String, route: SicauJiaowuRoute, account: String): String? {
        val base = route.url(source).toHttpUrlOrNull() ?: return null
        val u = base.resolve(raw) ?: return null
        // School HTML sometimes uses absolute direct links even within the WebVPN page.
        val direct = if(u.host == "jiaowu.sicau.edu.cn" && u.scheme == "https" && u.port == 443 && u.username.isEmpty() && u.password.isEmpty()) u
        else if(route.isWebVpn && route.allows(u)) (SicauJiaowuProtocol.ORIGIN + u.encodedPath.removePrefix(route.prefix) + (u.encodedQuery?.let { "?$it" } ?: "")).toHttpUrlOrNull()
        else null
        if(direct == null || !direct.encodedPath.startsWith("/xuesheng/") ||
            Regex("(?i)(logout|delete|update|save|check|tuichu)").containsMatchIn(direct.encodedPath)) return null
        for(name in direct.queryParameterNames) {
            if(name.lowercase() in setOf("xh", "xuehao", "studentid", "student_id", "user", "userid") && direct.queryParameterValues(name).any { it != account }) return null
        }
        return direct.newBuilder().fragment(null).build().toString()
    }
}

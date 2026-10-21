package com.mychuannong.app.school

import com.mychuannong.app.SafeUrl
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Only replay a server-issued cookie at its original HTTPS origin and path. */
internal object SchoolWebCookiePolicy {
    fun location(url: String): String? {
        if (!SafeUrl.school(url)) return null
        return url.toHttpUrlOrNull()?.newBuilder()?.query(null)?.fragment(null)?.build()?.toString()
    }

    fun valid(url: String, raw: String, now: Long = System.currentTimeMillis()): Cookie? {
        val target = location(url)?.toHttpUrlOrNull() ?: return null
        // CookieManager cannot restore the top-level partition key; do not de-partition it.
        if (raw.contains(Regex(";\\s*Partitioned(?:;|$)", RegexOption.IGNORE_CASE))) return null
        val domains = Regex(";\\s*domain=([^;]*)", RegexOption.IGNORE_CASE).findAll(raw)
            .map { it.groupValues[1].trim().trimStart('.').lowercase() }
        if (domains.any { it !in setOf(target.host, "sicau.edu.cn") }) return null
        return Cookie.parse(target, raw)?.takeIf {
            it.domain in setOf(target.host, "sicau.edu.cn") && it.matches(target) && it.expiresAt > now
        }
    }

    fun key(cookie: Cookie) = listOf(cookie.name, cookie.domain, cookie.path).joinToString("\n")

    fun durable(url: String, raw: String, now: Long = System.currentTimeMillis()): String? {
        val cookie = valid(url, raw, now) ?: return null
        if (!Regex(";\\s*max-age=", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return raw
        val expiry = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(cookie.expiresAt).atZone(ZoneOffset.UTC))
        return raw.replace(Regex(";\\s*(max-age|expires)=[^;]*", RegexOption.IGNORE_CASE), "") + "; Expires=$expiry"
    }
}

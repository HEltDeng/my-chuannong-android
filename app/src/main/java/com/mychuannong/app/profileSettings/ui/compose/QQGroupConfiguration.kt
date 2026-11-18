package com.mychuannong.app.profileSettings.ui.compose

import com.google.gson.JsonObject
import java.net.URI
import java.net.URLDecoder

/** Accept only the official QQ links configured for the same group. */
internal data class QQGroupConfiguration(val enabled: Boolean, val joinUrl: String?) {
    companion object {
        fun from(value: JsonObject): QQGroupConfiguration {
            val enabled = runCatching { value.get("enabled")?.asBoolean == true }.getOrDefault(false)
            val group = runCatching { value.get("groupNumber")?.asString.orEmpty() }.getOrDefault("")
            val url = runCatching { value.get("joinUrl")?.asString.orEmpty() }.getOrDefault("")
            return QQGroupConfiguration(enabled, url.takeIf { enabled && validJoinUrl(it, group) })
        }

        fun validJoinUrl(raw: String, group: String): Boolean {
            if (!group.matches(Regex("[1-9][0-9]{4,11}")) || raw.length !in 1..2048 || raw.any { it.isWhitespace() || it.isISOControl() }) return false
            return runCatching {
                val uri = URI(raw)
                if (uri.userInfo != null || uri.fragment != null || uri.port !in setOf(-1, 443)) return false
                when {
                    uri.scheme == "https" && uri.host in setOf("qun.qq.com", "qm.qq.com") ->
                        !uri.path.isNullOrEmpty() && uri.path != "/"
                    uri.scheme == "mqqapi" && uri.host == "card" && uri.port == -1 && uri.path == "/show_pslcard" -> {
                        val entries = uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map {
                            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                        }
                        val keys = entries.map { it.first }
                        val values = entries.toMap()
                        keys.size == keys.distinct().size && keys.all { it in setOf("src_type", "version", "uin", "card_type", "source", "auth", "key") } &&
                            values["uin"] == group && values["card_type"] == "group"
                    }
                    else -> false
                }
            }.getOrDefault(false)
        }
    }
}

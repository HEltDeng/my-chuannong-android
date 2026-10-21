package com.mychuannong.app.school

import java.text.Normalizer

internal enum class CampusMap(val id: String, val title: String) {
    YAAN("yaan", "雅安校区"),
    CHENGDU("chengdu", "成都校区"),
    DUJIANGYAN("dujiangyan", "都江堰校区"),
}

internal enum class PlaceCategory(val title: String, val color: Long, val keywords: String) {
    ALL("全部", 0xFF237B62, "全部"),
    TEACHING("教学楼", 0xFFE4AE62, "教学 教室 上课 自习"),
    DORM("宿舍", 0xFF70AD98, "宿舍 公寓 寝室 住宿"),
    DINING("食堂", 0xFFDA8C7E, "食堂 餐厅 吃饭 餐饮"),
    LIBRARY("图书馆", 0xFF7AA9CE, "图书馆 借书 阅读 自习"),
    SPORTS("体育场馆", 0xFF8DA9B0, "体育 运动 球场 健身"),
    SERVICE("生活服务", 0xFFAC9AC2, "生活 服务"),
    GATE("校门", 0xFF9BADA3, "校门 出入口 大门"),
}

internal data class CampusPlace(
    val id: String, val campus: CampusMap, val name: String, val shortName: String,
    val category: PlaceCategory, val aliases: List<String> = emptyList(),
    val geo: CampusGeoPoi? = null,
)

internal object CampusPlaces {
    fun visible(campus: CampusMap, category: PlaceCategory, query: String, catalogue: List<CampusPlace>): List<CampusPlace> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return catalogue.filter { it.campus == campus && (category == PlaceCategory.ALL || it.category == category) }
        // Search always covers every campus and category. Number boundaries avoid 1号 matching 11号.
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        return catalogue.filter { place ->
            val haystack = normalize(listOf(place.name, place.shortName, place.campus.title,
                place.category.keywords, *place.aliases.toTypedArray()).joinToString(" "))
            tokens.all { token ->
                val start=if(token.first().isDigit()) "(?<![0-9])" else ""
                val end=if(token.last().isDigit()) "(?![0-9])" else ""
                Regex(start + Regex.escape(token) + end).containsMatchIn(haystack)
            }
        }.sortedWith(compareBy<CampusPlace> { it.campus != campus }.thenBy { !normalize(it.name).startsWith(normalized) })
    }
    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase().trim().replace(Regex("\\s+"), " ")
        .replace(Regex("[一二三四五六七八九]?十[一二三四五六七八九]?|[一二三四五六七八九](?=[号栋舍教公综宿])")) { match ->
            val digits="零一二三四五六七八九"
            val parts=match.value.split('十')
            if(parts.size==2) ((if(parts[0].isEmpty()) 1 else digits.indexOf(parts[0][0]))*10+
                (if(parts[1].isEmpty()) 0 else digits.indexOf(parts[1][0]))).toString()
            else digits.indexOf(match.value[0]).toString()
        }
}

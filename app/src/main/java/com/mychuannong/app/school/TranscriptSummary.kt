package com.mychuannong.app.school

/** Shared display order for the home card and transcript summary. Keep school values unchanged. */
internal fun SchoolTranscript?.scoreSummaryFields(): List<Pair<String, String>> {
    fun summaryValue(vararg names: String): String = names.firstNotNullOfOrNull { name ->
        this?.summary?.firstOrNull { it.name == name }?.value
    } ?: "--"

    val majorRanking = listOf("专业排名", "有效必修加权排名", "本人排名", "排名", "名次")
        .firstNotNullOfOrNull { name -> this?.ranking?.firstOrNull { it.name == name }?.value }
        ?: "--"

    return listOf(
        "加权平均成绩" to summaryValue("加权平均成绩"),
        "专业排名" to majorRanking,
        "平均成绩绩点" to summaryValue("平均成绩绩点", "平均绩点"),
        "有效学分" to summaryValue("有效学分"),
    )
}

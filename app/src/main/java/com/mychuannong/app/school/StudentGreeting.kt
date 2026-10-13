package com.mychuannong.app.school

internal object StudentGreeting {
    private val compound = setOf("欧阳", "司马", "上官", "诸葛", "夏侯", "司徒", "司空", "皇甫", "尉迟", "公孙", "慕容", "令狐", "长孙", "宇文", "东方", "南宫", "独孤", "端木", "轩辕", "钟离", "闾丘", "闻人", "澹台", "公羊", "申屠", "太史", "仲孙", "万俟")
    fun from(name: String?, bound: Boolean): String {
        val value = name?.trim().orEmpty()
        if (!bound || value.isBlank()) return "川农同学"
        val surname = if (value.take(2) in compound) value.take(2)
            else String(Character.toChars(value.codePointAt(0)))
        return surname + "同学"
    }
}

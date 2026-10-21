package com.mychuannong.app.overview.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.school.LearningCacheKeys

/** First-frame copy only; Room remains authoritative. Store raw weeks, never yesterday's UI. */
internal object OverviewCourseCache {
    private val store by lazy { LearningCacheKeys.store("overview_courses_v1") }
    private val gson = Gson()
    @Volatile var generation: Long = 0
        private set
    private fun key(owner: String, term: String) = LearningCacheKeys.owner(owner) + ":" + term

    @Synchronized fun read(owner: String, term: String): List<TimeTableMySubject>? {
        if (owner.isBlank()) return null
        return runCatching {
            val json = store.decodeString(key(owner, term)) ?: return null
            decode(json, owner, term)
        }.getOrNull()
    }

    internal fun decode(json: String, owner: String, term: String): List<TimeTableMySubject>? = runCatching {
        if (owner.isBlank()) return null
        val rows: List<TimeTableMySubject> = gson.fromJson(json,
            object : TypeToken<List<TimeTableMySubject>>() {}.type)
        rows.takeIf { rows.all { it.term == term &&
            (it.studentId == owner || (it.studentId.isBlank() && it.isCustom)) } }
    }.getOrNull()

    @Synchronized fun save(owner: String, term: String, rows: List<TimeTableMySubject>, expectedGeneration: Long) {
        if (owner.isBlank() || expectedGeneration != generation) return
        store.encode(key(owner, term), gson.toJson(rows))
    }

    @Synchronized fun clear() { generation++; store.clearAll() }
}

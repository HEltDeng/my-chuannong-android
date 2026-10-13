package com.mychuannong.app.feature.common.data.local.mmkv

import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.school.LearningCacheKeys

class ExamArrangementCache {

    private val mmkv get() = LearningCacheKeys.store("learning_exams_v2")
    private val gson = Gson()

    init {
        // Legacy records have no owner. Never assign them to whoever signs in next.
        MMKV.mmkvWithID("content_cache").removeValueForKey("exams")
    }

    private fun accountKey(): String? = StudentInfoManager.studentId.takeIf(String::isNotBlank)
        ?.let { "exams_" + LearningCacheKeys.owner(it) }

    fun saveExamArrangement(exams: List<ExamArrange>) {
        val key = requireNotNull(accountKey()) { "A verified school account is required" }
        check(mmkv.encode(key, gson.toJson(exams)))
    }

    fun getExamArrangement(): List<ExamArrange>? {
        val key = accountKey() ?: return null
        val json = mmkv.decodeString(key) ?: return null
        val type = object : TypeToken<List<ExamArrange>>() {}.type
        return try {
            val exams: List<ExamArrange>? = gson.fromJson(json, type)
            if (exams == null) return null
            val valid = exams.filter { it.isCacheCompatible() }
            if (valid.size != exams.size) {
                mmkv.removeValueForKey(key)
                return null
            }
            valid
        } catch (e: Exception) {
            mmkv.removeValueForKey(key)
            null
        }
    }

    private fun ExamArrange.isCacheCompatible(): Boolean = runCatching {
        courseNameval.isNotBlank() && examTime.isNotBlank()
    }.getOrDefault(false)

    fun clearCache() {
        mmkv.clearAll()
        MMKV.mmkvWithID("content_cache").removeValueForKey("exams")
    }
}

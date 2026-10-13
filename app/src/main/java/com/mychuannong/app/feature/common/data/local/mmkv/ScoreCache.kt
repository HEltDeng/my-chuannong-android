package com.mychuannong.app.feature.common.data.local.mmkv

import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV

object ScoreCache {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)

    private val mmkv by lazy {
        com.mychuannong.app.school.LearningCacheKeys.store("learning_scores_v2").also { encrypted ->
            val legacy=MMKV.mmkvWithID("content_cache")
            legacy.allKeys().orEmpty().filter { it.startsWith("transcript_v1_") }.forEach { key ->
                if(!encrypted.containsKey(key)) check(encrypted.encode(key,legacy.decodeString(key)))
                legacy.removeValueForKey(key)
            }
        }
    }
    private val gson = Gson()

    fun saveGrades(grades: List<Grade>) {
        val normalized = grades.mapNotNull(::normalizeGrade)
        if (normalized.isEmpty()) {
            mmkv.removeValueForKey(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)))
            return
        }
        mmkv.encode(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)), gson.toJson(normalized))
    }

    fun saveGradesDetailByUrl(url: String, details: String) {
        mmkv.encode(transcriptKey(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)+url, details)
    }

    fun getGradesDetailByUrl(url: String): String {
        return mmkv.getString(transcriptKey(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)+url, "") ?: ""
    }

    fun getGrades(): List<Grade>? {
        val json = mmkv.decodeString(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId))) ?: return null
        val type = object : TypeToken<List<Grade>>() {}.type
        return try {
            val grades: List<Grade>? = gson.fromJson(json, type)
            if (grades == null) {
                mmkv.removeValueForKey(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)))
                return null
            }
            val normalized = grades.mapNotNull(::normalizeGrade)
            if (normalized.isEmpty()) {
                mmkv.removeValueForKey(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)))
                return null
            }
            if (normalized.size != grades.size) {
                mmkv.encode(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)), gson.toJson(normalized))
            }
            normalized
        } catch (e: Exception) {
            mmkv.removeValueForKey(("grades_" + com.mychuannong.app.school.LearningCacheKeys.owner(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)))
            null
        }
    }

    private fun normalizeGrade(grade: Grade): Grade? {
        val normalizedName = grade.name.trim()
        val normalizedGrade = grade.grade.trim()
        if (normalizedName.isBlank() || normalizedGrade.isBlank()) return null

        val normalizedItem = grade.item.trim().ifBlank { "未知学期" }
        val normalizedId = grade.id.trim().ifBlank { "${normalizedItem}_${normalizedName}" }

        return grade.copy(
            id = normalizedId,
            item = normalizedItem,
            name = normalizedName,
            grade = normalizedGrade,
            score = grade.score.trim(),
            point = grade.point.trim(),
            timeR = grade.timeR.trim(),
            flag = grade.flag.trim(),
            upperReItem = grade.upperReItem.trim(),
            method = grade.method.trim(),
            property = grade.property.trim(),
            attribute = grade.attribute.trim(),
            reItem = grade.reItem.trim()
        )
    }

    private fun transcriptKey(account: String): String {
        require(account.isNotBlank())
        return "transcript_v1_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun getTranscript(account: String): com.mychuannong.app.school.SchoolTranscript? {
        if(account.isBlank() || account != com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId) return null
        val legacy=MMKV.mmkvWithID("content_cache")
        if(!mmkv.containsKey(transcriptKey(account))) legacy.decodeString(transcriptKey(account))?.let { old ->
            if(mmkv.encode(transcriptKey(account),old)) legacy.removeValueForKey(transcriptKey(account))
        }
        return runCatching { gson.fromJson(mmkv.decodeString(transcriptKey(account)), com.mychuannong.app.school.SchoolTranscript::class.java) }.getOrNull()
    }
    internal fun saveTranscript(account: String, report: com.mychuannong.app.school.SchoolTranscript) {
        check(account.isNotBlank() && account == com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId)
        check(mmkv.encode(transcriptKey(account), gson.toJson(report.copy(courses = report.courses.distinct()))))
        revision.value++
    }
    fun clearCache() {
        mmkv.clearAll()
        MMKV.mmkvWithID("content_cache").clearAll()
        revision.value++
    }
}

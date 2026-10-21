package com.mychuannong.app.core.privacy

import com.mychuannong.app.school.core.RetrofitUtils as SchoolRetrofitUtils
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.security.SchoolSessionState
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local-only account and school-data operations exposed from Settings. */
object AccountDataManager {
    suspend fun clearSchoolBinding() {
        // Revoke immediately, before waiting for an in-flight academic synchronization.
        com.mychuannong.app.core.security.SchoolAccessGate.revokeBinding()
        com.mychuannong.app.school.SicauNativeAuth.clear()
        com.mychuannong.app.school.SicauJiaowuAuth.clear()
        com.mychuannong.app.school.SicauWebVpn.forgetRoute()
        kotlinx.coroutines.withContext(Dispatchers.IO) { com.mychuannong.app.school.SchoolSyncEngine.clear() }
        StudentInfoManager.clear()
        SchoolSessionState.invalidate()
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { SchoolRetrofitUtils.ClearClient("moocClient") }
            runCatching { SchoolRetrofitUtils.ClearClient("EducationClient") }
        }
        MMKV.mmkvWithID("education_cache").clearAll()
        MMKV.mmkvWithID("sicau_cookie_jar", MMKV.MULTI_PROCESS_MODE).clearAll()
        MMKV.mmkvWithID("MoocCookiejar").clearAll()
        MMKV.mmkvWithID("import_cache").clearAll()
        // SicauNativeAuth.clear owns asynchronous browser clearing and its revision barrier.
        com.mychuannong.app.school.SicauNativeAuth.awaitCleared()
        MMKV.mmkvWithID("TimeTableAppWidget").clearAll()
    }

    suspend fun clearLocalLearningData() = withContext(Dispatchers.IO) {
        com.mychuannong.app.school.SchoolSyncEngine.clearLearning()
        MMKV.mmkvWithID("content_cache").clearAll()
        MMKV.mmkvWithID("mooc_local_cache").clearAll()
        MMKV.mmkvWithID("TimeTableAppWidget").clearAll()
    }
}

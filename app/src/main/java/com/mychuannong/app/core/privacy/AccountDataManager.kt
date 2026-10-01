package com.mychuannong.app.core.privacy

import android.webkit.CookieManager
import com.mychuannong.app.school.core.RetrofitUtils as SchoolRetrofitUtils
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.security.SchoolSessionState
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Local-only account and school-data operations exposed from Settings. */
object AccountDataManager {
    fun clearSchoolBinding() {
        runBlocking(Dispatchers.IO) { com.mychuannong.app.school.SchoolSyncEngine.clear() }
        StudentInfoManager.clear()
        SchoolSessionState.invalidate()
        runBlocking(Dispatchers.IO) {
            runCatching { SchoolRetrofitUtils.ClearClient("moocClient") }
            runCatching { SchoolRetrofitUtils.ClearClient("EducationClient") }
        }
        MMKV.mmkvWithID("education_cache").clearAll()
        MMKV.mmkvWithID("sicau_cookie_jar", MMKV.MULTI_PROCESS_MODE).clearAll()
        MMKV.mmkvWithID("MoocCookiejar").clearAll()
        MMKV.mmkvWithID("import_cache").clearAll()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        PlanetApplication.clearSchoolDataCacheAll()
    }

    fun clearLocalLearningData() {
        MMKV.mmkvWithID("content_cache").clearAll()
        MMKV.mmkvWithID("mooc_local_cache").clearAll()
        MMKV.mmkvWithID("TimeTableAppWidget").clearAll()
        PlanetApplication.clearSchoolDataCacheAll()
    }
}

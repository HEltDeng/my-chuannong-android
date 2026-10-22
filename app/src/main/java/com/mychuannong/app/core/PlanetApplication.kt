package com.mychuannong.app.core

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.security.SchoolSessionState
import com.mychuannong.app.core.network.campustools.DeviceIdentity
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.mooc.viewmodel.MoocViewModel
import com.mychuannong.app.skin.SkinManager
import com.mychuannong.app.skin.data.cache.SkinCache
import com.mychuannong.app.utils.StartupTimeTracker
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlanetApplication : Application(), ViewModelStoreOwner {
    companion object {
        private const val TIME_TABLE_APP_WIDGET = "TimeTableAppWidget"
        private const val CACHE_SCHEMA_VERSION_KEY = "cache_schema_version"
        private const val CURRENT_CACHE_SCHEMA_VERSION = 5

        var accessToken: String?
            get() = MMKV.defaultMMKV().getString("token", null)
            set(value) {
                MMKV.defaultMMKV().putString("token", value)
            }

        var isExpired: Boolean
            get() = MMKV.defaultMMKV().getBoolean("is_expired", true)
            set(value) {
                MMKV.defaultMMKV().putBoolean("is_expired", value)
            }

        var startTime: Long = 0
        var deviceId: String = ""
        lateinit var appContext: Context

        /** Legacy helper calls are redirected to the owner's configured community service. */
        val UserIp: String
            get() = BuildConfig.COMMUNITY_USER_BASE_URL.trimEnd('/')

        fun clearCacheAll() {
            SchoolSessionState.invalidate()
            CoroutineScope(Dispatchers.IO).launch {
                accessToken = ""
                MMKV.mmkvWithID("education_cache").clearAll()
                MMKV.mmkvWithID("content_cache").clearAll()
                MMKV.mmkvWithID(TIME_TABLE_APP_WIDGET).clearAll()
                CoursesDataBase.getDatabase(appContext).courseDao().clearAllCourses();com.mychuannong.app.overview.data.OverviewCourseCache.clear()
            }
        }
        fun clearSchoolDataCacheAll(){
            CoroutineScope(Dispatchers.IO).launch {
                MMKV.mmkvWithID("content_cache").clearAll()
                MMKV.mmkvWithID(TIME_TABLE_APP_WIDGET).clearAll()
                CoursesDataBase.getDatabase(appContext).courseDao().clearAllCourses();com.mychuannong.app.overview.data.OverviewCourseCache.clear()
            }
        }

        fun clearContentCache() {
            CoroutineScope(Dispatchers.IO).launch {
                com.mychuannong.app.school.SchoolSyncEngine.clearLearning()
            }
        }

        fun clearLocalCache() {
            CoroutineScope(Dispatchers.IO).launch {
                MMKV.mmkvWithID("import_cache").clearAll()
                clearContentCache()
            }
        }

        fun getSystemDeviceId(): String {
            return DeviceIdentity.deviceScopedId(appContext) ?: "unknown_device"
        }
    }

    private val _appViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _appViewModelStore

    private val moocViewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MoocViewModel(this@PlanetApplication) as T
        }
    }

    val moocViewModel: MoocViewModel by lazy {
        ViewModelProvider(this, moocViewModelFactory)[MoocViewModel::class.java]
    }


    override fun onCreate() {

        super.onCreate()


        initMMKV()
        migrateLegacyCacheIfNeeded()
        prepareTransientCsustDataGetStores()
        if (BuildConfig.DEBUG) {
            StartupTimeTracker.initialize(applicationContext as Application)
        }
        appContext = applicationContext
        com.mychuannong.app.core.analytics.AccountActivityReporter.install(this)
        com.mychuannong.app.core.security.SchoolAccessGate.install(this)
        com.mychuannong.app.school.SicauWebVpn.observeNetwork(this)
        com.mychuannong.app.school.SicauAcademicData.initialize(this)
        com.mychuannong.app.feature.schedule.reminder.ReminderRecoveryWorker.install(this)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(appContext) }
        }
        setSkin()
        saveDefaultSkin()
    }

    private fun setSkin() {
        val skinPath = SkinCache.getAssetsName()
        if (skinPath != "skin_default") {
            SkinManager.setSkin(skinPath)
        }
    }

    private fun initMMKV() {
        MMKV.initialize(this@PlanetApplication)
    }

    /**
     * CSUSTDataGet 1.1.4 hard-codes plaintext MMKV stores for credentials and cookies.
     * Delete any previous files, then open those IDs read-only before the dependency can access
     * them. Its CookieJar keeps the active session in memory, but cannot persist it to disk.
     */
    private fun prepareTransientCsustDataGetStores() {
        resetAndOpenReadOnly("education_cache", MMKV.SINGLE_PROCESS_MODE)
        resetAndOpenReadOnly("sicau_cookie_jar", MMKV.MULTI_PROCESS_MODE)
    }

    private fun resetAndOpenReadOnly(storeId: String, mode: Int) {
        MMKV.mmkvWithID(storeId, mode).apply {
            clearAll()
            sync()
            close()
        }
        MMKV.mmkvWithID(storeId, mode or MMKV.READ_ONLY_MODE)
    }

    private fun migrateLegacyCacheIfNeeded() {
        val kv = MMKV.defaultMMKV() ?: return
        val version = kv.decodeInt(CACHE_SCHEMA_VERSION_KEY, 0)
        if (version >= CURRENT_CACHE_SCHEMA_VERSION) return

        runCatching {
            if (version < 3) {
                MMKV.mmkvWithID("education_cache", MMKV.SINGLE_PROCESS_MODE).clearAll()
                MMKV.mmkvWithID("content_cache").removeValueForKey("grades")
                MMKV.mmkvWithID("content_cache").removeValueForKey("exams")
                MMKV.mmkvWithID("MoocCookiejar").clearAll()
            }
            if (version < 4) {
                MMKV.mmkvWithID("import_cache").apply {
                    removeValueForKey("user_account") // UserInfoManager.account（MOOC userName）
                    removeValueForKey("user_avatar")  // UserInfoManager.userAvatar
                    removeValueForKey("user_id")      // UserInfoManager.userId
                    removeValueForKey("user_email")   // UserInfoManager.userEmail
                }
            }
            if (version < 5) {
                // CSUSTDataGet 1.1.4 stores these values as plaintext. Credentials now live
                // only in StudentInfoManager; the dependency cache is treated as transient.
                MMKV.mmkvWithID("education_cache", MMKV.SINGLE_PROCESS_MODE)
                    .removeValueForKey("student_password")
                MMKV.mmkvWithID("sicau_cookie_jar", MMKV.MULTI_PROCESS_MODE).clearAll()
                MMKV.mmkvWithID("import_cache").removeValueForKey("user_id")
                kv.removeValueForKey("token")
            }
        }

        kv.encode(CACHE_SCHEMA_VERSION_KEY, CURRENT_CACHE_SCHEMA_VERSION)
    }
    private fun saveDefaultSkin(){
        SkinCache.saveSkinDownloaded("skin_default")
        SkinCache.saveSkinDownloaded("skin_dark.apk")
    }
}


//@SuppressLint("StaticFieldLeak")
//object CrashHandler : Thread.UncaughtExceptionHandler {
//
//    private lateinit var myContext: Context
//    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
//
//    fun init(context: Context) {
//        this.myContext = context.applicationContext
// //       defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
//        Thread.setDefaultUncaughtExceptionHandler(this)
//    }
//
//    override fun uncaughtException(t: Thread, e: Throwable) {
//        try {
//            // 记录崩溃日志 可加入后端
//            Log.e("CrashHandler", "App crashed: ", e)
//
//            val intent = myContext.packageManager.getLaunchIntentForPackage(myContext.packageName)
//            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
//
//            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
//                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
//            } else {
//                PendingIntent.FLAG_ONE_SHOT
//            }
//
//            val pendingIntent = PendingIntent.getActivity(
//                myContext,
//                0,
//                intent,
//                flags
//            )
//
//            val alarmManager = myContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pendingIntent)
//        } catch (e: Exception) {
//            Log.e("CrashHandler", "Error in crash handler", e)
//        } finally {
//            Process.killProcess(Process.myPid())
//            exitProcess(1)
//        }
//    }
//}

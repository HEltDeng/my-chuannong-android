package com.mychuannong.app.core.task


import android.content.Context
import com.dcelysia.cp_common.start_up.AndroidStartup
import com.dcelysia.cp_common.start_up.Startup

class CrashReportStartup : AndroidStartup<Unit>() {

    override fun create(context: Context): Unit {
        // “我的川农”不复用上游项目的 Bugly 项目。接入自有统计前保持 no-op，
        // 避免把二次开发版本的崩溃和设备信息发送给原项目账号。
    }

    override fun dependencies(): List<Class<out Startup<*>>>? = null

    override fun callCreateOnMainThread(): Boolean = false

    override fun waitOnMainThread(): Boolean = false
}

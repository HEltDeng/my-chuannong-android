package com.mychuannong.app.core.task

import android.content.Context
import com.dcelysia.cp_common.start_up.AndroidStartup
import com.dcelysia.cp_common.start_up.Startup

class PreRequestStartup : AndroidStartup<Unit>() {

    override fun create(context: Context) = Unit

    override fun dependencies(): List<Class<out Startup<*>>>? =
        listOf(MMKVStartup::class.java)

    override fun callCreateOnMainThread(): Boolean = false

    override fun waitOnMainThread(): Boolean = false
}

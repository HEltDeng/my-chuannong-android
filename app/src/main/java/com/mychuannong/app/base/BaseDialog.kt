package com.mychuannong.app.base

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.mychuannong.app.R
import com.mychuannong.app.core.PlanetApplication

abstract class BaseDialog(context: Context?) : Dialog(
    context ?: PlanetApplication.appContext, R.style.CustomDialog
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutId())
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        init()
    }

    protected abstract fun init()
    protected abstract fun layoutId(): Int
}
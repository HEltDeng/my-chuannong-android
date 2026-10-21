package com.mychuannong.app.core.notifications

import android.content.Context
import android.os.Build

object NotificationPermissionPreferences {
    private const val STORE = "notification_permission_v1"
    private const val KEY_EDUCATION_SHOWN = "education_shown"

    fun shouldOfferEducation(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                .getBoolean(KEY_EDUCATION_SHOWN, false)

    fun markEducationShown(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EDUCATION_SHOWN, true)
            .apply()
    }
}

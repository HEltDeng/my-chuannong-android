package com.mychuannong.app.overview.data.local

import com.tencent.mmkv.MMKV

/** Local-only display preferences for personal overview data. */
object OverviewPrivacyPreferences {
    private const val STORE_ID = "overview_privacy_preferences_v1"
    private const val KEY_SHOW_GPA = "show_gpa"

    private val storage by lazy { MMKV.mmkvWithID(STORE_ID) }

    fun isGpaVisible(): Boolean = storage.decodeBool(KEY_SHOW_GPA, true)

    fun setGpaVisible(visible: Boolean): Boolean = storage.encode(KEY_SHOW_GPA, visible)
}

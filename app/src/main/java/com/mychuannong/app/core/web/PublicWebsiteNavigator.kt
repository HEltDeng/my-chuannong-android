package com.mychuannong.app.core.web

import android.content.Context
import android.content.Intent
import android.net.Uri

object PublicWebsiteNavigator {
    const val HOME_URL = "https://mychuannong.cn/"
    const val DOWNLOAD_URL = "https://mychuannong.cn/download.html"
    const val PRIVACY_URL = "https://mychuannong.cn/privacy.html"
    const val TERMS_URL = "https://mychuannong.cn/terms.html"

    fun openHomeInBrowser(context: Context): Boolean = open(context, HOME_URL)

    fun open(context: Context, url: String): Boolean {
        if (!isOfficialWebsiteUrl(url)) return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        }.getOrDefault(false)
    }
}

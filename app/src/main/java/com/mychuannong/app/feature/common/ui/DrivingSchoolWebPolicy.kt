package com.mychuannong.app.feature.common.ui

import java.net.URI

/** Only the bundled driving-school document and its own assets are available to this WebView. */
object DrivingSchoolWebPolicy {
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val ASSET_ROOT = "/assets/driving-school/"
    const val HOME_URL = "https://appassets.androidplatform.net/assets/driving-school/index.html?host=android"

    private val allowedPaths = setOf(
        "index.html", "online.css", "online.js",
    ).map { ASSET_ROOT + it }.toSet()

    fun allowsNavigation(url: String): Boolean = url.substringBefore('#') == HOME_URL

    fun allowsAssetRequest(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.rawAuthority != ASSET_HOST || uri.rawPath !in allowedPaths) return false
        return uri.rawQuery == null || (uri.rawPath == ASSET_ROOT + "index.html" && uri.rawQuery == "host=android")
    }
}

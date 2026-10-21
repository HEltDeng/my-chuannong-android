package com.mychuannong.app.feature.common.ui

import java.net.URI

enum class CampusService(val route: String) {
    Language("language"), Exam("exam"), Gym("gym");

    companion object {
        fun fromRoute(route: String?): CampusService? = entries.find { it.route == route }
    }
}

/** The service pages can load only their bundled assets, without accessing other app data. */
object CampusServicesWebPolicy {
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val ASSET_ROOT = "/assets/campus-services/"
    const val DOCUMENT_URL = "https://appassets.androidplatform.net/assets/campus-services/index.html?host=android"
    private val allowedPaths = setOf("index.html", "online.css", "online.js")
        .map { ASSET_ROOT + it }.toSet()

    fun homeUrl(service: CampusService): String = "$DOCUMENT_URL#/${service.route}"

    fun allowsNavigation(url: String): Boolean = url.substringBefore('#') == DOCUMENT_URL

    fun allowsAssetRequest(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.rawAuthority != ASSET_HOST || uri.rawPath !in allowedPaths) return false
        return uri.rawQuery == null || (uri.rawPath == ASSET_ROOT + "index.html" && uri.rawQuery == "host=android")
    }
}

package com.mychuannong.app.feature.lostfound.ui

import java.net.URI

/** Only this feature's bundled files may be loaded by its offline WebView. */
internal object LostFoundWebPolicy {
    const val HOME = "https://appassets.androidplatform.net/assets/lost-found/index.html"
    const val EXIT = "https://appassets.androidplatform.net/assets/lost-found/exit"
    private fun localUri(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf {
        it.scheme == "https" && it.rawAuthority == "appassets.androidplatform.net"
    }
    fun allowsAsset(url: String): Boolean {
        val uri = localUri(url) ?: return false
        val path = uri.rawPath ?: return false
        return path.startsWith("/assets/lost-found/") &&
            path.matches(Regex("[A-Za-z0-9_./-]+")) &&
            path.split('/').none { it == "." || it == ".." } && !path.contains("//")
    }
    fun allowsDocument(url: String): Boolean = localUri(url)?.rawPath == "/assets/lost-found/index.html"
    fun isExit(url: String): Boolean = url == EXIT
}

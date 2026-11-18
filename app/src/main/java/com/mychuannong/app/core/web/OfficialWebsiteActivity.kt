package com.mychuannong.app.core.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.net.URI

internal fun isOfficialWebsiteUrl(rawUrl: String): Boolean = runCatching {
    val uri = URI(rawUrl)
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host?.lowercase() in setOf("mychuannong.cn", "www.mychuannong.cn") &&
        uri.userInfo == null && uri.port in setOf(-1, 443) &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath.startsWith("/") && !uri.rawPath.startsWith("//"))
}.getOrDefault(false)

/** Compatibility route for existing callers; navigation remains in the user's browser. */
class OfficialWebsiteActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_URL = "official_website_url"
        fun createIntent(context: Context, url: String = PublicWebsiteNavigator.HOME_URL): Intent =
            Intent(context, OfficialWebsiteActivity::class.java).putExtra(EXTRA_URL, url)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val url = intent.getStringExtra(EXTRA_URL) ?: PublicWebsiteNavigator.HOME_URL
        if (!PublicWebsiteNavigator.open(this, url)) Toast.makeText(this, "暂时无法打开官网", Toast.LENGTH_SHORT).show()
        finish()
    }
}

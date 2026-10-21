package com.mychuannong.app.core.web
import android.content.Context
object PublicWebsiteNavigator {
 const val HOME_URL=""
 const val DOWNLOAD_URL=""
 const val PRIVACY_URL=""
 const val TERMS_URL=""
 fun openHomeInBrowser(context:Context):Boolean = open(context,"")
 fun open(context:Context,url:String):Boolean { context.startActivity(OfficialWebsiteActivity.createIntent(context));return true }
}

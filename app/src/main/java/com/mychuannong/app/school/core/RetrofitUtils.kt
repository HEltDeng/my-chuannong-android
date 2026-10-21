package com.mychuannong.app.school.core
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.CookieJar
/** Compatibility contract: no legacy network client, credentials, logging or school endpoints. */
object RetrofitUtils {
 val totalCookieJar = LocalJar()
 fun ClearClient(name:String) { totalCookieJar.clear() }
 class LocalJar:CookieJar {
  override fun loadForRequest(url:HttpUrl):List<Cookie> = emptyList()
  override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) { }
  fun clear() { }
 }
}

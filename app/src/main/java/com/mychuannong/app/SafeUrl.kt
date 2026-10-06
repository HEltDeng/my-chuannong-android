package com.mychuannong.app
import java.net.URI
object SafeUrl {
 private val hosts=setOf("www.sicau.edu.cn","news.sicau.edu.cn","jiaowu.sicau.edu.cn","auth.sicau.edu.cn","ecology.sicau.edu.cn","ywtb.sicau.edu.cn","op.sicau.edu.cn","lib.sicau.edu.cn","rsc.sicau.edu.cn","xcb.sicau.edu.cn")
 // SICAU's successful portal redirect contains a literal pipe in the hash route.
 // A fragment is never sent to the server and cannot change the trusted origin.
 // Validate the network URL, preserving the original fragment for the school SPA.
 private fun networkUri(url:String):URI {
  require(url.none { it.code < 32 || it.code == 127 } && !url.contains('\\'))
  return URI(url.substringBefore('#'))
 }
 fun https(url:String)=runCatching { val u=networkUri(url);u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&(u.port==-1||u.port==443) }.getOrDefault(false)
 fun school(url:String)=https(url)&&runCatching { networkUri(url).host.lowercase() in hosts }.getOrDefault(false)
}

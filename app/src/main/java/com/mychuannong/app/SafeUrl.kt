package com.mychuannong.app
import java.net.URI
object SafeUrl {
 private val hosts=setOf("www.sicau.edu.cn","jiaowu.sicau.edu.cn","auth.sicau.edu.cn","ecology.sicau.edu.cn","ywtb.sicau.edu.cn","op.sicau.edu.cn","lib.sicau.edu.cn","rsc.sicau.edu.cn","xcb.sicau.edu.cn")
 fun https(url:String)=runCatching { val u=URI(url);u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&(u.port==-1||u.port==443) }.getOrDefault(false)
 fun school(url:String)=https(url)&&runCatching { URI(url).host.lowercase() in hosts }.getOrDefault(false)
}

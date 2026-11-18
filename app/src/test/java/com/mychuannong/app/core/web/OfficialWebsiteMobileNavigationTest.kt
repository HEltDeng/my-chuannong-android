package com.mychuannong.app.core.web
import com.mychuannong.app.SafeUrl
import org.junit.Test
import org.junit.Assert.*
class OfficialWebsiteMobileNavigationTest {
 @Test fun productWebsiteUsesItsOwnHttpsDomain() {
  assertEquals("https://mychuannong.cn/",PublicWebsiteNavigator.HOME_URL)
  assertTrue(isOfficialWebsiteUrl(PublicWebsiteNavigator.HOME_URL))
  assertTrue(isOfficialWebsiteUrl("https://www.mychuannong.cn/privacy.html"))
  listOf("http://mychuannong.cn/", "https://mychuannong.cn.evil.test/", "https://mychuannong.cn@evil.test/", "https://mychuannong.cn:8443/").forEach { assertFalse(it, isOfficialWebsiteUrl(it)) }
  assertFalse(isOfficialWebsiteUrl("https://www.sicau.edu.cn/"))
 }
 @Test fun schoolHostsCannotBeSpoofed() {
  listOf("https://jiaowu.sicau.edu.cn/","https://auth.sicau.edu.cn/wui/").forEach{assertTrue(SafeUrl.school(it))}
  listOf("http://jiaowu.sicau.edu.cn/","https://jiaowu.sicau.edu.cn.evil.test/","https://jiaowu.sicau.edu.cn@evil.test/","https://evil.test/?next=jiaowu.sicau.edu.cn","javascript:alert(1)","file:///sdcard/session","https://bd.bdfairy.cn/","https://jiaowu.sicau.edu.cn:8443/").forEach{assertFalse(it,SafeUrl.school(it))}
 }
}

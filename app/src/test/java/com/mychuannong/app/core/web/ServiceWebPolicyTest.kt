package com.mychuannong.app.core.web

import com.mychuannong.app.school.SchoolWebPolicy
import org.junit.Assert.*
import org.junit.Test

class ServiceWebPolicyTest {
    @Test fun `business navigation skips login callback and repeated page`() {
        val pages = listOf("https://bd.bdfairy.cn/room", "https://example.com/login", "https://example.com/callback", "https://bd.bdfairy.cn/order")
        assertEquals(0, ServiceWebPolicy.previousBusinessIndex(pages, 3))
        assertNull(ServiceWebPolicy.previousBusinessIndex(pages, 0))
        assertNull(ServiceWebPolicy.previousBusinessIndex(emptyList(), -1))
    }

    @Test fun `payment schemes cannot be confused with web or local file URLs`() {
        listOf("weixin://wap/pay?x=1", "alipays://platformapi/startapp", "alipay://pay").forEach {
            assertTrue(it, ServiceWebPolicy.paymentScheme(it))
            assertFalse(it, ServiceWebPolicy.httpUrl(it))
        }
        listOf("javascript:alert(1)", "file:///sdcard/pay", "content://pay", "https://weixin.evil.test", "intent://pay").forEach {
            assertFalse(it, ServiceWebPolicy.paymentScheme(it))
        }
        assertFalse(ServiceWebPolicy.httpUrl("https://trusted.test@evil.test"))
        assertTrue(ServiceWebPolicy.externalScheme("tel:12345"))
        assertTrue(ServiceWebPolicy.externalScheme("campusapp://login"))
        listOf("file:///sdcard/private", "javascript:alert(1)", "content://private", "intent://pay", "data:text/html,test").forEach {
            assertFalse(ServiceWebPolicy.externalScheme(it))
        }
    }

    @Test fun `new services retain original CSS and page structure`() {
        listOf("https://www.sicau.edu.cn/", "https://xsgz.sicau.edu.cn/", "https://bd.bdfairy.cn/", "https://ncre.neea.edu.cn").forEach {
            assertTrue(ServiceWebPolicy.httpUrl(it))
            assertNull(ServiceWebPolicy.adaptation(it))
        }
        assertNull(SchoolWebPolicy.adaptation("https://www.sicau.edu.cn/"))
    }
}

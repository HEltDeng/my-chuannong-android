package com.mychuannong.app.school

import com.mychuannong.app.SafeUrl
import org.junit.Assert.*
import org.junit.Test

class SchoolSsoRedirectTest {
    @Test fun portalHashCallbackDoesNotInvalidateTrustedOrigin() {
        // Synthetic value only; never use a captured school token in fixtures.
        val url="https://ywtb.sicau.edu.cn/#/pages/login/login?sicau=1&token=example|sample&service=portal"
        assertTrue(SafeUrl.school(url))
        assertTrue(SafeUrl.https(url))
        assertTrue(SchoolWebPolicy.isAuthentication(url))
        assertNull(SchoolWebPolicy.adaptation(url))
        assertEquals(0, SchoolWebPolicy.previousBusinessIndex(listOf("https://ywtb.sicau.edu.cn/",url,"https://ywtb.sicau.edu.cn/#/pages/Index/Index"),2))
    }
    @Test fun fragmentsCannotOverrideOriginRestrictions() {
        listOf(
            "https://evil.test/#https://ywtb.sicau.edu.cn/|sample",
            "https://ywtb.sicau.edu.cn.evil.test/#sample|value",
            "https://ywtb.sicau.edu.cn@evil.test/#sample|value",
            "https://evil.test@ ywtb.sicau.edu.cn/#sample|value",
            "http://ywtb.sicau.edu.cn/#sample|value",
            "https://ywtb.sicau.edu.cn:8443/#sample|value",
            "https://ywtb.sicau.edu.cn\\@evil.test/#sample|value",
            "https://ywtb.sicau.edu.cn/\n#sample|value"
        ).forEach { assertFalse(it,SafeUrl.school(it)) }
    }
}

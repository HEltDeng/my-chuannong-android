package com.mychuannong.app.core.web

import org.junit.Assert.*
import org.junit.Test

class ElectricAuthorizationPolicyTest {
    @Test fun onlyOfficialAuthorizationEndpointsCanTriggerAutomaticFallback() {
        assertTrue(ElectricAuthorizationPolicy.wechatAuthorization("https://open.weixin.qq.com/connect/oauth2/authorize?state=example#wechat_redirect"))
        assertTrue(ElectricAuthorizationPolicy.wechatAuthorization("https://open.weixin.qq.com/connect/qrconnect"))
        listOf("http://open.weixin.qq.com/connect/oauth2/authorize",
            "https://open.weixin.qq.com.evil.test/connect/oauth2/authorize",
            "https://open.weixin.qq.com@evil.test/connect/oauth2/authorize",
            "https://open.weixin.qq.com:444/connect/oauth2/authorize",
            "https://open.weixin.qq.com/other", "weixin://wap/pay").forEach {
            assertFalse(it,ElectricAuthorizationPolicy.wechatAuthorization(it))
        }
    }
    @Test fun providerRecognitionDoesNotTrustSuffixesOrUserInfo() {
        assertFalse(ElectricAuthorizationPolicy.provider("http://bd.bdfairy.cn/selectservice"))
        assertTrue(ElectricAuthorizationPolicy.provider("https://bd.bdfairy.cn/room"))
        assertFalse(ElectricAuthorizationPolicy.provider("https://bd.bdfairy.cn.evil.test/"))
        assertFalse(ElectricAuthorizationPolicy.provider("https://bd.bdfairy.cn@evil.test/"))
    }
}

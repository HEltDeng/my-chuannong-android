package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class SchoolServiceJourneyTest {
    private val module=SchoolServices.entries.getValue("weekly_attendance")
    private val hall=SchoolServices.entries.getValue("student-affairs").url
    @Test fun validSessionOpensExactTargetWithoutHome() {
        val flow=SchoolServiceJourney(module)
        assertEquals(SchoolServiceJourney.Result.READY,flow.finished(module.url))
        assertTrue(flow.reachedTarget)
    }
    @Test fun automaticAndManualAuthenticationBothResumeModuleFromLanding() {
        listOf(false,true).forEach { authSeen ->
            val flow=SchoolServiceJourney(module)
            if(authSeen) assertEquals(SchoolServiceJourney.Result.WAIT,flow.finished(SicauAdapter.AUTH))
            assertEquals(SchoolServiceJourney.Result.RETURN_TO_TARGET,flow.finished(hall))
            assertFalse(flow.reachedTarget)
            assertEquals(SchoolServiceJourney.Result.READY,flow.finished(module.url))
        }
    }
    @Test fun repeatedLandingStopsInsteadOfLoopingOrDisplayingHome() {
        val flow=SchoolServiceJourney(module)
        assertEquals(SchoolServiceJourney.Result.RETURN_TO_TARGET,flow.finished(hall))
        assertEquals(SchoolServiceJourney.Result.FAILED,flow.finished(hall))
        assertFalse(flow.reachedTarget)
    }
    @Test fun expiredBusinessSessionCanRecoverAndReturnAgain() {
        val flow=SchoolServiceJourney(module)
        flow.finished(module.url);flow.authenticationRequired()
        assertFalse(flow.reachedTarget)
        assertEquals(SchoolServiceJourney.Result.RETURN_TO_TARGET,flow.finished(hall))
        assertEquals(SchoolServiceJourney.Result.READY,flow.finished(module.url))
    }
    @Test fun returnToHallAfterTargetExitsButModuleDetailsRemainUsable() {
        val flow=SchoolServiceJourney(module)
        assertFalse(flow.exitsToServices(hall))
        flow.finished(module.url)
        assertTrue(flow.exitsToServices(hall))
        assertFalse(flow.exitsToServices("https://xsgz.sicau.edu.cn/mobile/signTask/detail"))
        assertFalse(flow.exitsToServices(SicauAdapter.AUTH))
    }
    @Test fun academicUsesProtectedTargetAndNeverTreatsLoginAsReady() {
        val service=SchoolServices.entries.getValue("academic")
        val flow=SchoolServiceJourney(service)
        assertEquals(SicauJiaowuProtocol.PERSONAL_INFO,service.url)
        assertEquals(SchoolServiceJourney.Result.WAIT,flow.finished(SicauJiaowuProtocol.LOGIN_PAGE))
        assertEquals(SchoolServiceJourney.Result.RETURN_TO_TARGET,flow.finished("https://jiaowu.sicau.edu.cn/xuesheng/index.asp"))
        assertEquals(SchoolServiceJourney.Result.READY,flow.finished(service.url))
        assertFalse(flow.exitsToServices("https://jiaowu.sicau.edu.cn/xuesheng/other.asp"))
    }
    @Test fun officialPaymentAndUnifiedCallbackMustReachTheirBusinessOrigin() {
        val payment=SchoolServiceJourney(SchoolServices.entries.getValue("payments"))
        assertEquals(SchoolServiceJourney.Result.WAIT,payment.finished(payment.service.url))
        assertEquals(SchoolServiceJourney.Result.WAIT,payment.finished(SicauAdapter.AUTH))
        assertEquals(SchoolServiceJourney.Result.READY,payment.finished("https://op.sicau.edu.cn/orders.aspx"))
        val unified=SchoolServiceJourney(SchoolServices.entries.getValue("auth"))
        assertEquals(SchoolServiceJourney.Result.WAIT,unified.finished(SicauAdapter.AUTH))
        assertEquals(SchoolServiceJourney.Result.READY,unified.finished("https://ywtb.sicau.edu.cn/"))
    }
    @Test fun untrustedAndUnrelatedPagesNeverCompleteOrRedirect() {
        listOf("http://xsgz.sicau.edu.cn/", "https://xsgz.sicau.edu.cn.evil.invalid/", "https://auth.sicau.edu.cn/wui/",
            "https://op.sicau.edu.cn/", "https://xsgz.sicau.edu.cn/mobile/other").forEach {
            assertEquals(SchoolServiceJourney.Result.WAIT,SchoolServiceJourney(module).finished(it))
        }
    }
    @Test fun futureServicesNeedOnlyNameTargetAndRealm() {
        val custom=SchoolService("学校新服务","https://xsgz.sicau.edu.cn/mobile/example?tab=mine#/details",SchoolServiceAuth.UNIFIED)
        val flow=SchoolServiceJourney(custom)
        assertFalse(flow.isTarget("https://xsgz.sicau.edu.cn/mobile/example?tab=other#/details"))
        assertFalse(flow.isTarget("https://xsgz.sicau.edu.cn/mobile/example?tab=mine#/list"))
        assertEquals(SchoolServiceJourney.Result.RETURN_TO_TARGET,flow.finished(hall))
        assertEquals(SchoolServiceJourney.Result.READY,flow.finished(custom.url))
    }
    @Test fun officialPortalFragmentMayContainLiteralPipes() {
        val flow=SchoolServiceJourney(SchoolServices.entries.getValue("ywtb"))
        assertEquals(SchoolServiceJourney.Result.READY,flow.finished("https://ywtb.sicau.edu.cn/#/index|student"))
    }
}

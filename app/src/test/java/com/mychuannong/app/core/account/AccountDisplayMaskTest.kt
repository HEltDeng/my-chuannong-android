package com.mychuannong.app.core.account
import org.junit.Assert.assertEquals
import org.junit.Test
class AccountDisplayMaskTest {
    @Test fun phoneUsesThreeAndFour() {
        assertEquals("155****1234", AccountDisplayMask.phone("15500001234"))
        assertEquals("155****1234", AccountDisplayMask.phone("155****1234"))
        assertEquals("未绑定", AccountDisplayMask.phone(null))
    }
    @Test fun studentUsesFourAndTwoWithoutLeakingShortIds() {
        assertEquals("2099****42", AccountDisplayMask.student("2099000042"))
        assertEquals("*****", AccountDisplayMask.student("12345"))
        assertEquals("未绑定", AccountDisplayMask.student(""))
    }
}

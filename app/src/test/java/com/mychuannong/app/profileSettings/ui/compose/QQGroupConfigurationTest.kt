package com.mychuannong.app.profileSettings.ui.compose

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class QQGroupConfigurationTest {
    @Test fun acceptsOnlyOfficialLinksForTheConfiguredGroup() {
        assertTrue(QQGroupConfiguration.validJoinUrl("https://qm.qq.com/q/example", "123456"))
        assertTrue(QQGroupConfiguration.validJoinUrl("mqqapi://card/show_pslcard?uin=123456&card_type=group", "123456"))
        listOf(
            "https://qm.qq.com.evil.test/q/example",
            "https://qm.qq.com@evil.test/q/example",
            "http://qm.qq.com/q/example",
            "mqqapi://card/show_pslcard?uin=654321&card_type=group",
            "mqqapi://card/show_pslcard?uin=123456&uin=654321&card_type=group",
        ).forEach { assertFalse(it, QQGroupConfiguration.validJoinUrl(it, "123456")) }
    }

    @Test fun disabledGroupNeverProvidesALink() {
        val config = QQGroupConfiguration.from(JsonParser.parseString("""{"enabled":false,"groupNumber":"123456","joinUrl":"https://qm.qq.com/q/example"}""").asJsonObject)
        assertFalse(config.enabled)
        assertNull(config.joinUrl)
    }
}

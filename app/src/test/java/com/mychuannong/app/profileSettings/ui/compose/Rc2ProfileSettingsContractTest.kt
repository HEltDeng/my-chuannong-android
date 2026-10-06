package com.mychuannong.app.profileSettings.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import com.mychuannong.app.R
import com.mychuannong.app.profileSettings.ui.model.SettingItemUiModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Rc2ProfileSettingsContractTest {
    private fun appFile(relative: String): File =
        if (File("src/main").isDirectory) File(relative) else File("app", relative)

    @Test
    fun `brand screens use a raster drawable copied from the existing brand asset`() {
        val accountGate = appFile(
            "src/main/java/com/mychuannong/app/core/account/ui/AccountGateScreen.kt",
        ).readText()
        val privacy = appFile(
            "src/main/java/com/mychuannong/app/core/privacy/PrivacyConsentScreen.kt",
        ).readText()
        assertTrue(accountGate.contains("painterResource(R.drawable.my_chuannong_brand_logo)"))
        assertTrue(privacy.contains("painterResource(R.drawable.my_chuannong_brand_logo)"))
        assertFalse(accountGate.contains("painterResource(R.mipmap.my_chuannong_icon)"))
        assertFalse(privacy.contains("painterResource(R.mipmap.my_chuannong_icon)"))
        assertFalse(privacy.contains("暂不同意不会反复弹窗，之后可主动重新选择。"))
        assertTrue(privacy.contains("dropShadow("))
        assertTrue(privacy.contains("height(40.dp)"))

        val brandLogo = appFile("src/main/res/drawable-nodpi/my_chuannong_brand_logo.png")
        assertTrue(brandLogo.readBytes().take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
        assertTrue(appFile("src/main/res/drawable-nodpi/my_chuannong_brand_logo_rounded.png").isFile)
    }

    @Test
    fun `main profile entries keep platform and school identity distinct`() {
        val items = createMainSettingItems(checkingUpdate = false)
            .filterIsInstance<SettingItemUiModel.Option>()

        assertEquals(
            listOf("platform-account", "school-account", "reminders", "widgets", "notifications", "update"),
            items.map { it.id },
        )

        val platform = items.first { it.id == "platform-account" }
        assertEquals(R.drawable.my_chuannong_brand_mark, platform.iconRes)
        assertFalse(platform.preserveOriginalColors)
        assertNull(platform.icon)

        val school = items.first { it.id == "school-account" }
        assertEquals(R.drawable.ic_student_id_outline, school.iconRes)
        assertFalse(school.preserveOriginalColors)
        assertNull(school.icon)

        val reminders = items.first { it.id == "reminders" }
        assertEquals(R.drawable.notification2, reminders.iconRes)
        assertNull(reminders.icon)
        assertEquals(R.drawable.ic_widget_grid, items.first { it.id == "widgets" }.iconRes)
        assertNull(items.first { it.id == "notifications" }.subtitle)
        assertNull(items.first { it.id == "update" }.subtitle)
        assertTrue(createMainSettingItems(checkingUpdate = true)
            .filterIsInstance<SettingItemUiModel.Option>()
            .first { it.id == "update" }
            .showProgress)
    }
}

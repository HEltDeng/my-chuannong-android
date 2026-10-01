package com.mychuannong.app.core.release

import com.mychuannong.app.feature.common.compose_ui.FunctionDestination
import com.mychuannong.app.core.web.isOfficialWebsiteUrl
import com.mychuannong.app.overview.ui.compose.isScoreMetricId
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V1SourceBoundaryTest {
    private fun appPath(relative: String): File =
        if (File("src/main").isDirectory) File(relative) else File("app", relative)

    private fun appFile(relative: String): File = appPath(relative).also {
        check(it.exists()) { "Missing app file: $relative (cwd=${System.getProperty("user.dir")})" }
    }

    @Test
    fun `authorized campus dependency is fixed while excluded binaries stay absent`() {
        val csustDataGet = appFile("libs/CSUSTDataGet-1.1.4-no-sensitive-logs.aar")
        assertFalse(appPath("libs/Lite3DMap.jar").exists())

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(csustDataGet.readBytes())
            .joinToString("") { "%02X".format(it) }
        assertEquals(
            "9B03523FA04E9C8B3F5CAAAD49DC282B17EB2AC82E69A49E89764B21ABF71C29",
            sha256,
        )

        val sourceRoot = appFile("src/main/java")
        val source = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }
        assertTrue(source.contains("com.mychuannong.app.school"))
        assertFalse(source.contains("com.amap.api"))

        val build = appFile("build.gradle.kts").readText()
        assertTrue(build.contains("CSUSTDataGet-1.1.4-no-sensitive-logs.aar"))
        assertFalse(build.contains("Lite3DMap.jar"))
    }

    @Test
    fun `closed feature activities permissions and deep links are absent`() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        listOf(
            "SportsReservationActivity",
            "CampusMapActivity",
            "SportsReservationActivity",
        ).forEach { assertFalse(manifest.contains(it)) }
        listOf(
            "BindingUserActivity",
            "ElectronicActivity",
            "MoocActivity",
            "ScoreInquiryActivity",
            "ExamArrangementActivity",
        ).forEach { assertTrue(manifest.contains(it)) }
        listOf(
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "ACCESS_WIFI_STATE",
            "CHANGE_WIFI_STATE",
            "android.permission.CAMERA",
            "com.amap.api.v2.apikey",
        ).forEach { assertFalse(manifest.contains(it)) }
        assertFalse(manifest.contains("<data"))
        assertTrue(manifest.contains("LostFoundActivity"))
        assertTrue(appPath("src/main/res/layout/activity_binding_user.xml").exists())

        val networkConfig = appFile("src/main/res/xml/netconfig.xml").readText()
        assertTrue(networkConfig.contains("<base-config cleartextTrafficPermitted=\"false\">"))
        assertTrue(networkConfig.contains("xk.csust.edu.cn"))
        assertTrue(networkConfig.contains("pt.csust.edu.cn"))
        assertFalse(networkConfig.contains("includeSubdomains=\"true\""))
    }

    @Test
    fun `own account remains separate from school credentials`() {
        val accountCenter = appFile(
            "src/main/java/com/mychangli/app/core/account/ui/AccountCenterActivity.kt",
        ).readText()
        listOf(
            "reservationProfile()",
            "updateReservationProfile(",
            "deleteReservationProfile()",
            "预约识别学号",
            "教务绑定",
        ).forEach { assertFalse(accountCenter.contains(it)) }

        val accountGate = appFile(
            "src/main/java/com/mychangli/app/core/account/ui/AccountGateScreen.kt",
        ).readText()
        assertTrue(accountGate.contains("查看完整隐私说明"))
        assertFalse(accountGate.contains("学校账号相互独立"))

        val bindingCopy = appFile("src/main/res/values/strings.xml").readText()
        assertTrue(bindingCopy.contains("绑定学校账号后，可以查看你的课程、成绩等校园信息。"))
        assertTrue(bindingCopy.contains("你的账号信息和成绩数据不会被我们获取或保存，仅用于提供校园查询服务。更多说明请查看隐私与合规。"))
        assertTrue(bindingCopy.contains("查看隐私政策与个人信息清单"))
        assertTrue(bindingCopy.contains("遇到问题？尝试网页登录"))
        assertFalse(bindingCopy.contains("连接对应学校服务"))
    }

    @Test
    fun `release identity endpoint and privacy gate are explicit`() {
        val build = appFile("build.gradle.kts").readText()
        assertTrue(build.contains("applicationId = \"com.mychuannong.app\""))
        assertTrue(build.contains("versionCode = 37"))
        assertTrue(build.contains("versionName = \"1.0.5\""))
        assertTrue(build.contains("https://api.mychangliapp.cn/api/v1/"))
        assertTrue(build.contains("https://api.mychangliapp.cn/api/v1/legacy-disabled/"))
        assertTrue(build.contains("Release endpoints must not contain placeholder or loopback hosts"))

        val main = appFile("src/main/java/com/mychangli/app/core/MainActivity.kt").readText()
        assertTrue(main.contains("PrivacyConsentScreen"))
        assertTrue(main.contains("PrivacyDeclinedScreen"))

        val campusTools = appFile(
            "src/main/java/com/mychangli/app/core/network/campustools/CampusToolsApi.kt",
        ).readText()
        assertTrue(campusTools.contains("deviceAuth(\n                DeviceAuthRequest("))
        assertTrue(campusTools.contains("deviceScopedId = deviceScopedId()"))
        assertTrue(campusTools.contains("get() = token ?: deviceToken"))
        assertTrue(campusTools.contains("Only phone accounts can enter the visible account session"))

        val legacyNetwork = appFile(
            "src/main/java/com/mychangli/app/utils/RetrofitUtils.kt",
        ).readText()
        assertTrue(legacyNetwork.contains("LegacyServiceGuardInterceptor()"))
    }

    @Test
    fun `release user surface removes local demo components and draft labels`() {
        val releaseManifest = appFile("src/release/AndroidManifest.xml").readText()
        listOf(
            "BuddyClubActivity",
            "CampusActivityPlaceholderActivity",
            "LocalCampusPublishActivity",
            "LocalCampusPostDetailActivity",
        ).forEach { activity ->
            assertTrue(releaseManifest.contains(activity))
        }
        assertEquals(4, Regex("tools:node=\"remove\"").findAll(releaseManifest).count())

        val releaseStrings = appFile("src/main/res/values/strings.xml").readText()
        assertTrue(releaseStrings.contains("<string name=\"app_name\">我的川农</string>"))
        assertTrue(releaseStrings.contains("<string name=\"release_document_title_privacy\">隐私政策</string>"))
        assertFalse(releaseStrings.contains("发布前审阅稿"))

        val debugStrings = appFile("src/debug/res/values/strings.xml").readText()
        assertTrue(debugStrings.contains("<string name=\"app_name\">我的川农 QA</string>"))
    }

    @Test
    fun `profile hierarchy uses a restricted in app website and concise first level entries`() {
        val profile = appFile(
            "src/main/java/com/mychangli/app/profileSettings/ui/compose/ProfileSettingsRoute.kt",
        ).readText()
        listOf(
            "我的川农官网（最新消息）",
            "我的川农账号",
            "绑定学号",
            "上课提醒 & 桌面小组件",
            "检查更新",
            "隐私与合规",
            "账号与数据管理",
        ).forEach { assertTrue(profile.contains(it)) }
        assertTrue(profile.contains("上课提醒 & 桌面小组件"))
        assertFalse(profile.contains("通过官网获取新版本"))

        val websiteNavigator = appFile(
            "src/main/java/com/mychangli/app/core/web/PublicWebsiteNavigator.kt",
        ).readText()
        listOf(
            "https://mychangliapp.cn",
            "https://mychangliapp.cn/download.html",
            "https://mychangliapp.cn/privacy.html",
            "https://mychangliapp.cn/terms.html",
            "CustomTabsIntent.Builder()",
        ).forEach { assertTrue(websiteNavigator.contains(it)) }
        assertTrue(profile.contains("PublicWebsiteNavigator.openHomeInBrowser(context)"))
        val about = appFile(
            "src/main/java/com/mychangli/app/profileSettings/ui/AboutActivity.kt",
        ).readText()
        assertTrue(about.contains("PublicWebsiteNavigator.openHomeInBrowser(it)"))
        assertTrue(websiteNavigator.contains("fun openHomeInBrowser"))
        assertTrue(websiteNavigator.contains("Intent(Intent.ACTION_VIEW, uri)"))
        assertTrue(websiteNavigator.contains("Intent.CATEGORY_BROWSABLE"))
        assertFalse(websiteNavigator.contains("OfficialWebsiteActivity.createIntent"))

        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("core.web.OfficialWebsiteActivity"))
    }

    @Test
    fun `in app website allowlist accepts only the official https origin`() {
        assertTrue(isOfficialWebsiteUrl("https://mychangliapp.cn"))
        assertTrue(isOfficialWebsiteUrl("https://mychangliapp.cn/privacy.html?from=app"))
        assertFalse(isOfficialWebsiteUrl("http://mychangliapp.cn"))
        assertFalse(isOfficialWebsiteUrl("https://www.mychangliapp.cn"))
        assertFalse(isOfficialWebsiteUrl("https://mychangliapp.cn.example.com"))
        assertFalse(isOfficialWebsiteUrl("https://user@mychangliapp.cn"))
        assertFalse(isOfficialWebsiteUrl("https://mychangliapp.cn:8443"))
    }

    @Test
    fun `service dynamics uses one second continuous cadence`() {
        val dynamics = appFile(
            "src/main/java/com/mychangli/app/feature/common/compose_ui/ServiceDynamicsSection.kt",
        ).readText()
        assertTrue(dynamics.contains("AUTO_ADVANCE_MILLIS = 1_000L"))
        assertTrue(dynamics.contains("（点击查看详情）"))
        assertTrue(dynamics.contains("（下拉刷新）"))
        assertTrue(dynamics.contains("fontSize = 11.sp"))
        assertTrue(dynamics.contains("color = colors.greyTextColor"))
        assertFalse(dynamics.contains("AUTO_ADVANCE_MILLIS = 7_000L"))
    }

    @Test
    fun `service tab reentry and pull gesture refresh cached updates without parallel requests`() {
        val main = appFile("src/main/java/com/mychangli/app/core/main/ui/MainScreen.kt").readText()
        val feature = appFile(
            "src/main/java/com/mychangli/app/feature/common/ui/FeatureFragment.kt",
        ).readText()
        val store = appFile(
            "src/main/java/com/mychangli/app/core/network/campustools/RemoteConfigurationStore.kt",
        ).readText()

        assertTrue(main.contains("isActive = pagerState.settledPage == page"))
        assertTrue(feature.contains("LaunchedEffect(isActive)"))
        assertTrue(feature.contains("PullToRefreshBox("))
        assertTrue(feature.contains("RemoteConfigurationStore.refreshServiceUpdates()"))
        assertTrue(store.contains("serviceUpdatesRefreshMutex.tryLock()"))
        assertTrue(store.contains("KEY_SERVICE_UPDATES"))
    }

    @Test
    fun `bottom navigation removes rim gap and uses fine line icons`() {
        val bottomBar = appFile(
            "src/main/java/com/mychangli/app/core/main/ui/MainBottomBar.kt",
        ).readText()
        assertTrue(bottomBar.contains("edgeGlowColor = Color.Transparent"))
        assertTrue(bottomBar.contains("tabBarContentPadding = PaddingValues(0.dp)"))
        val floatingBar = appFile(
            "src/main/java/com/mychangli/app/widget/view/FloatingTabBar.kt",
        ).readText()
        assertTrue(floatingBar.contains("verticalInset = 0.dp"))

        listOf("ic_home.xml", "ic_unselect_home.xml").forEach { name ->
            val icon = appFile("src/main/res/drawable/$name").readText()
            assertTrue(icon.contains("android:strokeWidth=\"1.6\""))
            assertTrue(icon.contains("android:fillColor=\"#00000000\""))
        }
        listOf("ic_home.xml", "ic_service.xml", "ic_profile.xml").forEach { name ->
            assertTrue(appFile("src/main/res/drawable/$name").readText().contains("#FF000000"))
        }
        listOf("ic_unselect_home.xml", "ic_unselect_service.xml", "ic_unselect_profile.xml").forEach { name ->
            assertTrue(appFile("src/main/res/drawable/$name").readText().contains("#FF7A7A7A"))
        }
    }

    @Test
    fun `overview recognizes the production score metric id`() {
        assertTrue(isScoreMetricId(FunctionDestination.ScoreInquiry.name))
        assertTrue(isScoreMetricId("score"))
        assertFalse(isScoreMetricId("not-a-score-metric"))
    }

    @Test
    fun `overview score card uses a privacy visibility control instead of the grade badge`() {
        val overview = appFile(
            "src/main/java/com/mychangli/app/overview/ui/compose/OverviewScreen.kt",
        ).readText()
        assertTrue(overview.contains("Icons.Outlined.Visibility"))
        assertTrue(overview.contains("Icons.Outlined.VisibilityOff"))
        assertTrue(overview.contains("平均分 ****"))
        assertTrue(overview.contains("\"****\" else metric.value"))
        assertTrue(overview.contains("隐藏成绩"))
        assertTrue(overview.contains("显示成绩"))
        assertTrue(overview.contains("overview_score_visibility_icon"))
        assertTrue(overview.contains("overview_score_value_visibility"))
        assertTrue(overview.contains("FunctionDestination.ScoreInquiry.name"))
        assertTrue(overview.contains("val isScoreMetric = isScoreMetricId(metric.id)"))
        assertFalse(overview.contains("成绩已隐藏"))

        val headerStart = overview.indexOf("Row(verticalAlignment = Alignment.CenterVertically)")
        val headerEnd = overview.indexOf("verticalArrangement = Arrangement.Center", headerStart)
        val header = overview.substring(headerStart, headerEnd)
        assertEquals(1, Regex("IconButton\\(").findAll(header).count())
        assertTrue(header.indexOf("IconButton(") < header.indexOf("text = metric.title"))
        val scoreControl = header.substring(
            header.indexOf("if (isScoreMetric)"),
            header.indexOf("} else {"),
        )
        assertFalse(scoreControl.contains(".background("))
        assertFalse(scoreControl.contains("metric.iconRes"))

        val privacyPreference = appFile(
            "src/main/java/com/mychangli/app/overview/data/local/OverviewPrivacyPreferences.kt",
        ).readText()
        assertTrue(privacyPreference.contains("overview_privacy_preferences_v1"))
        assertTrue(privacyPreference.contains("show_gpa"))
        assertTrue(privacyPreference.contains("decodeBool(KEY_SHOW_GPA, true)"))
        assertTrue(privacyPreference.contains("encode(KEY_SHOW_GPA, visible)"))
        assertTrue(overview.contains("OverviewPrivacyPreferences.isGpaVisible()"))
        assertTrue(overview.contains("OverviewPrivacyPreferences.setGpaVisible(visible)"))
        assertFalse(overview.contains("rememberSaveable { mutableStateOf(true) }"))
    }

    @Test
    fun `three main tabs reuse the overview page background color`() {
        val overview = appFile(
            "src/main/java/com/mychangli/app/overview/ui/compose/OverviewScreen.kt",
        ).readText()
        val feature = appFile(
            "src/main/java/com/mychangli/app/feature/common/ui/FeatureFragment.kt",
        ).readText()
        val profile = appFile(
            "src/main/java/com/mychangli/app/profileSettings/ui/compose/ProfileSettingsRoute.kt",
        ).readText()

        assertTrue(overview.contains("background(colors.overviewPageBackgroundColor)"))
        assertTrue(feature.contains("background(AppTheme.colors.overviewPageBackgroundColor)"))
        assertTrue(profile.contains("background(AppTheme.colors.overviewPageBackgroundColor)"))
    }

    @Test
    fun `account entry and about surfaces stay concise while registration enters automatically`() {
        val accountGate = appFile(
            "src/main/java/com/mychangli/app/core/account/ui/AccountGateScreen.kt",
        ).readText()
        listOf(
            "登录我的川农账号",
            "没有账号？创建账号",
            "注册成功",
            "delay(650L)",
            "onAuthenticated()",
            "查看完整隐私说明",
        ).forEach { assertTrue(accountGate.contains(it)) }
        assertFalse(accountGate.contains("服务说明"))

        val about = appFile(
            "src/main/java/com/mychangli/app/profileSettings/ui/AboutActivity.kt",
        ).readText()
        listOf(
            "面向四川农业大学同学的轻量校园生活工具",
            "当前版本",
            "访问我的川农官网",
            "产品介绍、最新消息和详细说明请前往官网查看",
        ).forEach { assertTrue(about.contains(it)) }
        assertFalse(about.contains("尽量"))
        assertFalse(about.contains("FeatureItem("))
        assertFalse(about.contains("BulletPoint("))

        val releaseInformation = appFile(
            "src/main/java/com/mychangli/app/core/privacy/v1/ReleaseInformationV1Activity.kt",
        ).readText()
        assertTrue(releaseInformation.contains("if (type == ReleaseDocumentType.PRIVACY)"))
    }
}

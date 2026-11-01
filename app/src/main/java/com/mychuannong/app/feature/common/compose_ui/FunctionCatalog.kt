package com.mychuannong.app.feature.common.compose_ui

import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ShoppingBag
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mychuannong.app.R
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore
import com.mychuannong.app.core.network.campustools.serviceFeatureKey
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.account.AccountAccess
import com.mychuannong.app.core.config.AppConfigNavigationItem
import com.mychuannong.app.core.config.AppConfigPayload
import com.mychuannong.app.core.config.AppConfigValidator
import com.mychuannong.app.core.web.OfficialWebsiteActivity
import com.mychuannong.app.feature.common.ui.CampusService
import com.mychuannong.app.feature.common.ui.CampusServicesActivity
import com.mychuannong.app.widget.view.CustomToast

@Immutable
data class FunctionShortcut(
    val id: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val tintColor: Color,
    val destination: FunctionDestination,
    @StringRes val statusRes: Int? = null,
    val titleOverride: String? = null,
    val url: String? = null,
)

enum class FunctionDestination {
    Timetable,
    ScoreInquiry,
    Electronic,
    Errand,
    LogisticsRepair,
    Secondhand,
    DrivingSchool,
    IeltsToefl,
    CivilPostgrad,
    Gym,
    ExamArrangement,
    Calendar,
    Cet,
    ComputerExam,
    ComprehensiveScore,
    WeeklyAttendance,
    HolidayDestination,
    LostFound,
    AccountBook,
    ResourceLibrary,
    Mandarin,
    OfficialWebsite,
    ComingSoon,
}

/**
 * 首页正在按这些稳定 ID 查找快捷入口，因此这里保留原有顺序、ID 与跳转。
 */
fun primaryFunctionShortcuts(config: AppConfigPayload? = null): List<FunctionShortcut> =
    config?.let {
        dynamicShortcuts(
            config = it,
            allowedActionIds = setOf(
                "schedule",
                "grade",
                "electric",
                "exam",
                "calendar",
            ),
        )
    } ?: v1VisibleFunctionCatalog().selectById(
        "schedule",
        "grade",
        "electric",
        "exam",
        "calendar",
    )

/**
 * 保留旧接口，避免已有调用方因服务页重新分区而失效。
 */
fun secondaryFunctionShortcuts(config: AppConfigPayload? = null): List<FunctionShortcut> =
    config?.let {
        dynamicShortcuts(
            config = it,
            allowedActionIds = setOf("cet", "lost_found", "document", "mandarin"),
        )
    } ?: v1VisibleFunctionCatalog().selectById(
        "cet",
        "lost_found",
        "document",
        "mandarin",
    )

fun learningFunctionShortcuts(config: AppConfigPayload? = null): List<FunctionShortcut> =
    config?.let {
        val effectiveConfig = withMissingBuiltIns(it, setOf("ncre", "comprehensive_score"))
        dynamicShortcuts(
            config = effectiveConfig,
            allowedActionIds = setOf(
                "schedule",
                "grade",
                "exam",
                "document",
                "cet",
                "mandarin",
                "ncre",
                "comprehensive_score",
            ),
            section = "learning",
        )
    } ?: v1VisibleFunctionCatalog().selectById(
        "schedule",
        "grade",
        "exam",
        "document",
        "cet",
        "mandarin",
        "ncre",
        "comprehensive_score",
    )

fun offCampusFunctionShortcuts(config: AppConfigPayload? = null): List<FunctionShortcut> {
    val ids = setOf("driving_school", "ielts_toefl", "civil_postgrad", "gym")
    return dynamicShortcuts(withMissingBuiltIns(config ?: com.mychuannong.app.core.config.BuiltInAppConfig.envelope().config, ids), ids, "off_campus")
}

fun sportsFunctionShortcuts(): List<FunctionShortcut> =
    emptyList()

fun lifeFunctionShortcuts(config: AppConfigPayload? = null): List<FunctionShortcut> =
    config?.let {
        val effective = withMissingBuiltIns(it, setOf("secondhand", "errand", "logistics_repair", "weekly_attendance", "holiday_destination"))
        dynamicShortcuts(
            config = effective,
            allowedActionIds = setOf("electric", "calendar", "lost_found", "secondhand", "errand", "logistics_repair", "weekly_attendance", "holiday_destination"),
            section = "life",
        )
    } ?: v1VisibleFunctionCatalog().selectById(
        "electric",
        "calendar",
        "lost_found",
        "errand",
        "secondhand",
        "weekly_attendance",
        "holiday_destination",
        "logistics_repair",
    )

fun FunctionShortcut.toFunctionItemData(context: Context): FunctionItemData =
    FunctionItemData(
        id = id,
        title = titleOverride ?: context.getString(titleRes),
        icon = icon,
        tintColor = tintColor,
        statusLabel = statusRes?.let(context::getString),
        onClick = { openFunctionShortcut(context, this) },
    )

fun openFunctionShortcut(context: Context, shortcut: FunctionShortcut) {
    serviceFeatureKey(shortcut.id)?.let { if (!RemoteConfigurationStore.feature(it).isOpen) return }
    if (shortcut.destination == FunctionDestination.Electronic) {
        Route.goElectronic(context)
        return
    }
    if (shortcut.destination == FunctionDestination.ComputerExam) {
        Route.goComputerExam(context)
        return
    }
    if (shortcut.destination == FunctionDestination.Errand) {
        openFunctionShortcut(context, FunctionDestination.Errand)
        return
    }
    if (shortcut.destination in setOf(FunctionDestination.ComprehensiveScore, FunctionDestination.WeeklyAttendance, FunctionDestination.HolidayDestination)) {
        openFunctionShortcut(context, shortcut.destination)
        return
    }
    if (shortcut.destination in setOf(FunctionDestination.ResourceLibrary, FunctionDestination.LostFound, FunctionDestination.Secondhand, FunctionDestination.DrivingSchool, FunctionDestination.LogisticsRepair,
            FunctionDestination.IeltsToefl, FunctionDestination.CivilPostgrad, FunctionDestination.Gym)) {
        openFunctionShortcut(context, shortcut.destination)
        return
    }
    val url = shortcut.url
    if (!url.isNullOrBlank() && AppConfigValidator.isSafeOfficialUrl(url)) {
        context.startActivity(OfficialWebsiteActivity.createIntent(context, url))
        return
    }
    openFunctionShortcut(context, shortcut.destination)
}

fun openFunctionShortcut(context: Context, destination: FunctionDestination) {
    functionCatalog().firstOrNull { it.destination == destination }?.id?.let(::serviceFeatureKey)?.let {
        if (!RemoteConfigurationStore.feature(it).isOpen) return
    }
    when (destination) {
        FunctionDestination.ComingSoon -> CustomToast.showMessage(context, "内容即将上线")
        FunctionDestination.Timetable -> Route.goTimetable(context)
        FunctionDestination.ScoreInquiry -> Route.goScoreInquiry(context)
        FunctionDestination.Secondhand -> AccountAccess.openOrRequestLogin(context, android.content.Intent(context, com.mychuannong.app.feature.common.ui.SecondhandActivity::class.java))
        FunctionDestination.DrivingSchool -> AccountAccess.openOrRequestLogin(context, android.content.Intent(context, com.mychuannong.app.feature.common.ui.DrivingSchoolActivity::class.java))
        FunctionDestination.IeltsToefl -> AccountAccess.openOrRequestLogin(context, CampusServicesActivity.createIntent(context, CampusService.Language))
        FunctionDestination.CivilPostgrad -> AccountAccess.openOrRequestLogin(context, CampusServicesActivity.createIntent(context, CampusService.Exam))
        FunctionDestination.Gym -> AccountAccess.openOrRequestLogin(context, CampusServicesActivity.createIntent(context, CampusService.Gym))
        FunctionDestination.Errand -> AccountAccess.openOrRequestLogin(context, android.content.Intent(context, com.mychuannong.app.feature.common.ui.ErrandActivity::class.java))
        FunctionDestination.LogisticsRepair -> context.startActivity(android.content.Intent(context, com.mychuannong.app.feature.common.ui.LogisticsRepairActivity::class.java))
        FunctionDestination.Electronic -> Route.goElectronic(context)
        FunctionDestination.ExamArrangement -> Route.goExamArrangement(context)
        FunctionDestination.Calendar -> Route.goCalendar(context)
        FunctionDestination.Cet -> Route.goCet(context)
        FunctionDestination.ComprehensiveScore -> com.mychuannong.app.school.StudentAffairsServices.open(context, "comprehensive_score")
        FunctionDestination.WeeklyAttendance -> com.mychuannong.app.school.StudentAffairsServices.open(context, "weekly_attendance")
        FunctionDestination.HolidayDestination -> com.mychuannong.app.school.StudentAffairsServices.open(context, "holiday_destination")
        FunctionDestination.ComputerExam -> Route.goComputerExam(context)
        FunctionDestination.LostFound -> Route.goLostFound(context)
        FunctionDestination.AccountBook -> Route.goAccountBook(context)
        FunctionDestination.ResourceLibrary -> Route.goResourceLibrary(context)
        FunctionDestination.Mandarin -> Route.goMande(context)
        FunctionDestination.OfficialWebsite -> context.startActivity(
            OfficialWebsiteActivity.createIntent(context, "https://unconfigured.invalid/"),
        )
    }
}

private fun withMissingBuiltIns(config: AppConfigPayload, ids: Set<String>): AppConfigPayload {
    val existing = config.navigation.map { it.actionId }.toSet()
    val additions = com.mychuannong.app.core.config.BuiltInAppConfig.envelope().config.navigation
        .filter { it.actionId in ids && it.actionId !in existing }
    return config.copy(navigation = config.navigation + additions)
}

private fun functionCatalog(): List<FunctionShortcut> = listOf(
    FunctionShortcut("logistics_repair", R.string.feature_function_logistics_repair, Icons.Outlined.Build, FunctionColors.Map, FunctionDestination.LogisticsRepair),
    FunctionShortcut("secondhand", R.string.feature_function_secondhand, Icons.Outlined.ShoppingBag, FunctionColors.Map, FunctionDestination.Secondhand),
    FunctionShortcut("driving_school", R.string.feature_function_driving_school, Icons.Outlined.DirectionsCar, FunctionColors.Schedule, FunctionDestination.DrivingSchool),
    FunctionShortcut("ielts_toefl", R.string.feature_function_ielts_toefl, Icons.Outlined.Abc, FunctionColors.CET, FunctionDestination.IeltsToefl),
    FunctionShortcut("civil_postgrad", R.string.feature_function_civil_postgrad, Icons.Outlined.MenuBook, FunctionColors.Grade, FunctionDestination.CivilPostgrad),
    FunctionShortcut("gym", R.string.feature_function_gym, Icons.Outlined.FitnessCenter, FunctionColors.Map, FunctionDestination.Gym),

    FunctionShortcut("comprehensive_score", R.string.feature_function_comprehensive_score, Icons.Outlined.Assessment, FunctionColors.Grade, FunctionDestination.ComprehensiveScore),
    FunctionShortcut("weekly_attendance", R.string.feature_function_weekly_attendance, Icons.Outlined.HowToReg, FunctionColors.Map, FunctionDestination.WeeklyAttendance),
    FunctionShortcut("holiday_destination", R.string.feature_function_holiday_destination, Icons.Outlined.Luggage, FunctionColors.Electric, FunctionDestination.HolidayDestination),
    FunctionShortcut(
        id = "errand",
        titleRes = R.string.feature_function_errand,
        icon = Icons.Outlined.LocalShipping,
        tintColor = FunctionColors.Map,
        destination = FunctionDestination.Errand,
    ),
    FunctionShortcut(
        id = "schedule",
        titleRes = R.string.feature_function_course_schedule,
        icon = Icons.Outlined.CalendarMonth,
        tintColor = FunctionColors.Schedule,
        destination = FunctionDestination.Timetable,
    ),
    FunctionShortcut(
        id = "grade",
        titleRes = R.string.feature_function_grade_query,
        icon = Icons.Outlined.Assessment,
        tintColor = FunctionColors.Grade,
        destination = FunctionDestination.ScoreInquiry,
    ),
    FunctionShortcut(
        id = "electric",
        titleRes = R.string.feature_function_electricity_query,
        icon = Icons.Outlined.ElectricBolt,
        tintColor = FunctionColors.Electric,
        destination = FunctionDestination.Electronic,
    ),
    FunctionShortcut(
        id = "exam",
        titleRes = R.string.feature_function_exam_schedule,
        icon = Icons.Outlined.EventNote,
        tintColor = FunctionColors.Exam,
        destination = FunctionDestination.ExamArrangement,
    ),
    FunctionShortcut(
        id = "calendar",
        titleRes = R.string.feature_function_school_calendar,
        icon = Icons.Outlined.MenuBook,
        tintColor = FunctionColors.Calendar,
        destination = FunctionDestination.Calendar,
    ),
    FunctionShortcut(
        id = "cet",
        titleRes = R.string.feature_function_cet_exam,
        icon = Icons.Outlined.Abc,
        tintColor = FunctionColors.CET,
        destination = FunctionDestination.Cet,
    ),
    FunctionShortcut(
        id = "ncre",
        titleRes = R.string.feature_function_computer_exam,
        icon = Icons.Outlined.Computer,
        tintColor = FunctionColors.CET,
        destination = FunctionDestination.ComputerExam,
    ),
    FunctionShortcut(
        id = "lost_found",
        titleRes = R.string.feature_function_lost_found,
        icon = Icons.Outlined.FindInPage,
        tintColor = FunctionColors.LostFound,
        destination = FunctionDestination.LostFound,
    ),
    FunctionShortcut(
        id = "account",
        titleRes = R.string.feature_function_account_book,
        icon = Icons.Outlined.AccountBalanceWallet,
        tintColor = FunctionColors.Account,
        destination = FunctionDestination.AccountBook,
    ),
    FunctionShortcut(
        id = "document",
        titleRes = R.string.feature_function_document_library,
        icon = Icons.Outlined.FolderOpen,
        tintColor = FunctionColors.Document,
        destination = FunctionDestination.ResourceLibrary,
    ),
    FunctionShortcut(
        id = "mandarin",
        titleRes = R.string.feature_function_mandarin_test,
        icon = Icons.Outlined.RecordVoiceOver,
        tintColor = FunctionColors.Mandarin,
        destination = FunctionDestination.Mandarin,
    ),
)

/**
 * 校历仍保留路由和实现，V1 仅隐藏已失效旧数据源对应的用户入口。
 */
private fun v1VisibleFunctionCatalog(): List<FunctionShortcut> =
    functionCatalog().filterNot { it.id == "calendar" }

private fun dynamicShortcuts(
    config: AppConfigPayload,
    allowedActionIds: Set<String>,
    section: String? = null,
): List<FunctionShortcut> {
    val staticByActionId = functionCatalog().associateBy(FunctionShortcut::id)
    return config.navigation
        .asSequence()
        .filter { it.visible && it.actionId in allowedActionIds }
        .filter { section == null || it.section == section }
        .sortedWith(compareBy<AppConfigNavigationItem> { it.order }.thenBy { it.id })
        .mapNotNull { item ->
            val base = staticByActionId[item.actionId] ?: officialWebsiteShortcut(item)
            base?.copy(
                id = item.actionId,
                titleOverride = item.title,
                url = item.url,
                icon = if (item.actionId == "cet") Icons.Outlined.Abc else iconFor(item.iconKey) ?: base.icon,
            )
        }
        .toList()
}

private fun officialWebsiteShortcut(item: AppConfigNavigationItem): FunctionShortcut? =
    if (item.actionId == "official_web") {
        FunctionShortcut(
            id = item.actionId,
            titleRes = R.string.app_name,
            icon = iconFor(item.iconKey) ?: Icons.Outlined.Language,
            tintColor = FunctionColors.Document,
            destination = FunctionDestination.OfficialWebsite,
        )
    } else {
        null
    }

private fun iconFor(iconKey: String): ImageVector? = when (iconKey) {
    "build" -> Icons.Outlined.Build
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "directions_car" -> Icons.Outlined.DirectionsCar
    "fitness_center" -> Icons.Outlined.FitnessCenter
    "menu_book" -> Icons.Outlined.MenuBook
    "abc" -> Icons.Outlined.Abc
    "how_to_reg" -> Icons.Outlined.HowToReg
    "luggage" -> Icons.Outlined.Luggage
    "local_shipping" -> Icons.Outlined.LocalShipping
    "computer" -> Icons.Outlined.Computer
    "calendar" -> Icons.Outlined.CalendarMonth
    "assessment" -> Icons.Outlined.Assessment
    "meeting_room" -> Icons.Outlined.MeetingRoom
    "assignment" -> Icons.Outlined.Assignment
    "electric" -> Icons.Outlined.ElectricBolt
    "event_note" -> Icons.Outlined.EventNote
    "language" -> Icons.Outlined.Language
    "find_in_page" -> Icons.Outlined.FindInPage
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "folder" -> Icons.Outlined.FolderOpen
    "record_voice_over" -> Icons.Outlined.RecordVoiceOver
    "globe" -> Icons.Outlined.Language
    else -> null
}

private fun List<FunctionShortcut>.selectById(vararg ids: String): List<FunctionShortcut> {
    val shortcutsById = associateBy(FunctionShortcut::id)
    return ids.mapNotNull(shortcutsById::get)
}

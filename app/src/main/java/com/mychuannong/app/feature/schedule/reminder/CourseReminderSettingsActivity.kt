package com.mychuannong.app.feature.schedule.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.*
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.ScheduleCourse
import kotlinx.coroutines.launch

class CourseReminderSettingsActivity : ComposeActivity() {
    private var notificationGranted by mutableStateOf(false)
    private var exactGranted by mutableStateOf<Boolean?>(null)
    private var channelEnabled by mutableStateOf<Boolean?>(null)
    private var refreshKey by mutableIntStateOf(0)
    private var openingSettings = false
    private var lastAction = 0L
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        openingSettings = false
        refreshSystemState()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent {
            CourseReminderSettingsScreen(::finish, notificationGranted, exactGranted, channelEnabled, refreshKey,
                ::notificationAction, ::channelAction, ::exactAction, ::refreshSystemState,
                { startActivity(Intent(this, CourseReminderCoursesActivity::class.java)) })
        }
    }
    override fun onResume() {
        super.onResume()
        openingSettings = false
        refreshSystemState()
    }
    private fun refreshSystemState() {
        notificationGranted = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        exactGranted = runCatching { Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrNull()
        ReminderChannels.ensure(this)
        channelEnabled = runCatching { ReminderChannels.enabled(this) }.getOrNull()
        refreshKey++
    }
    private fun beginAction(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (openingSettings || now - lastAction < 600) return false
        lastAction = now
        openingSettings = true
        return true
    }
    private fun launchSettings(intent: Intent) {
        if (!beginAction()) return
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .onFailure { openingSettings = false; Toast.makeText(this, "暂时无法打开系统设置", Toast.LENGTH_SHORT).show() }
        }
    }
    private fun notificationAction() {
        val asked = getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            if (!beginAction()) return
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else launchSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    private fun channelAction() {
        if (Build.VERSION.SDK_INT >= 26) launchSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderChannels.currentId()))
        else notificationAction()
    }
    private fun exactAction() {
        if (Build.VERSION.SDK_INT >= 31) launchSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        else Toast.makeText(this, "此系统无需单独授权", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CourseReminderSettingsScreen(
    onBack: () -> Unit, notificationGranted: Boolean, exactGranted: Boolean?, channelEnabled: Boolean?, refreshKey: Int,
    notificationAction: () -> Unit, channelAction: () -> Unit, exactAction: () -> Unit, refreshSystem: () -> Unit,
    openCourses: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("main") }
    var enabled by remember { mutableStateOf(CourseReminderPreferences.enabled) }
    var lead by remember { mutableIntStateOf(CourseReminderPreferences.leadMinutes) }
    var details by remember { mutableStateOf(CourseReminderPreferences.showDetails) }
    var silent by remember { mutableStateOf(CourseReminderPreferences.silent) }
    var snooze by remember { mutableStateOf(CourseReminderPreferences.snoozeEnabled) }
    var courses by remember { mutableStateOf<List<ScheduleCourse>>(emptyList()) }
    var entries by remember { mutableStateOf<List<ScheduledReminder>>(emptyList()) }
    fun reloadSchedule() { scope.launch {
        CourseReminderScheduler.reschedule(context)
        entries = CourseReminderScheduler.effectiveEntries(context)
    } }
    LaunchedEffect(refreshKey) {
        enabled = CourseReminderPreferences.enabled
        lead = CourseReminderPreferences.leadMinutes
        details = CourseReminderPreferences.showDetails
        silent = CourseReminderPreferences.silent
        snooze = CourseReminderPreferences.snoozeEnabled
        courses = CourseScheduleRepository.loadCourses(context)
        CourseReminderScheduler.reschedule(context)
        entries = CourseReminderScheduler.effectiveEntries(context)
    }
    fun back() { if (page == "main") onBack() else page = if(page == "scheduled") "status" else "main" }
    BackHandler { back() }
    val title = when(page) { "lead" -> "提前时间"; "mode" -> "提醒方式"; "status" -> "提醒状态"; "scheduled" -> "已安排提醒"; else -> "上课提醒" }
    val state = if (!enabled) "已关闭" else if(notificationGranted && channelEnabled == true && courses.isNotEmpty()) "正常" else "待设置"
    // Separate saved list states prevent a long child list moving the parent's header off-screen.
    key(page) {
        LazyColumn(Modifier.fillMaxSize().background(AppTheme.colors.overviewPageBackgroundColor)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ReminderHeader(title, ::back) }
            when(page) {
                "main" -> {
                    item { SecondaryCard {
                        PreferenceSwitchRow("上课提醒", enabled) { enabled = !enabled; CourseReminderPreferences.enabled = enabled; reloadSchedule() }
                        SecondaryRow("提前时间", "${lead}分钟", { page = "lead" })
                        PreferenceSwitchRow("显示教师和教室", details) { details = !details; CourseReminderPreferences.showDetails = details }
                    } }
                    item { SecondaryCard {
                        SecondaryRow("课程提醒", onClick = openCourses)
                        SecondaryRow("提醒方式", if(silent) "静默" else "普通", { page = "mode" })
                        SecondaryRow("提醒状态", state, { refreshSystem(); page = "status" })
                    } }
                }
                "lead" -> item { SecondaryCard { Column(Modifier.selectableGroup()) {
                    listOf(5,10,15,20,30).forEach { minutes ->
                        ReminderRadioRow("${minutes}分钟", lead == minutes) {
                            if(lead != minutes) { lead = minutes; CourseReminderPreferences.leadMinutes = minutes; reloadSchedule() }
                        }
                    }
                } } }
                "mode" -> item { SecondaryCard {
                    Column(Modifier.selectableGroup()) {
                        listOf(false, true).forEach { value -> ReminderRadioRow(if(value) "静默提醒" else "普通提醒", silent == value) {
                            if(silent != value) { silent = value; CourseReminderPreferences.silent = value; refreshSystem() }
                        } }
                    }
                    PreferenceSwitchRow("允许稍后提醒", snooze) {
                        snooze = !snooze; CourseReminderPreferences.snoozeEnabled = snooze; ReminderSnooze.restore(context)
                    }
                    SecondaryRow("系统渠道设置", onClick = channelAction)
                } }
                "status" -> item { SecondaryCard {
                    PreferenceSwitchRow("通知权限", notificationGranted, notificationAction)
                    if(channelEnabled == null) SecondaryRow("通知渠道", "读取失败", channelAction)
                    else PreferenceSwitchRow("通知渠道", channelEnabled, channelAction)
                    if(Build.VERSION.SDK_INT < 31) SecondaryRow("精确提醒", "无需额外授权")
                    else if(exactGranted == null) SecondaryRow("精确提醒", "读取失败", exactAction)
                    else PreferenceSwitchRow("精确提醒", exactGranted, exactAction)
                    SecondaryRow("本地课程", "${courses.size}节")
                    SecondaryRow("已安排提醒", "${entries.size}项", { entries = CourseReminderScheduler.effectiveEntries(context); page = "scheduled" })
                } }
                "scheduled" -> {
                    if(entries.isEmpty()) item { SecondaryCard { SecondaryRow("暂无已安排提醒") } }
                    items(entries, key = { it.code }) { entry -> SecondaryCard {
                        val time = java.time.Instant.ofEpochMilli(entry.at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                        SecondaryRow(entry.name, time)
                    } }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReminderRadioRow(title: String, selected: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).selectable(selected, role = Role.RadioButton, onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = AppTheme.colors.primaryTextColor)
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AppTheme.colors.commonColor, unselectedColor = AppTheme.colors.controlDividerColor))
    }
}

@Composable
private fun PreferenceSwitchRow(title: String, checked: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).toggleable(checked, role = Role.Switch, onValueChange = { action() }), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = AppTheme.colors.primaryTextColor)
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedTrackColor = AppTheme.colors.commonColor, checkedThumbColor = AppTheme.colors.bgCardColor,
            uncheckedTrackColor = AppTheme.colors.bgLightGrayColor, uncheckedThumbColor = AppTheme.colors.secondaryTextColor,
            uncheckedBorderColor = AppTheme.colors.controlDividerColor))
    }
}

@Composable
internal fun ReminderHeader(title: String, onBack: () -> Unit) {
    SecondaryPageHeader(title, onBack)
}

@Composable
internal fun ReminderCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun ReminderNavigationCard(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.padding(horizontal = 5.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
                Text(subtitle, color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, "进入", tint = AppTheme.colors.secondaryTextColor)
        }
    }
}

@Composable
internal fun ReminderSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.primaryTextColor)
            Text(subtitle, color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp)
        }
        BrandSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

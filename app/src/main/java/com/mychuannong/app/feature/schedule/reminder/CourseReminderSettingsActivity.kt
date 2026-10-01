package com.mychuannong.app.feature.schedule.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.WidgetUpdateCoordinator
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.ScheduleCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CourseReminderSettingsActivity : ComposeActivity() {
    private var notificationPermissionGranted by mutableStateOf(false)
    private var exactAlarmGranted by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted || hasNotificationPermission()
        if (notificationPermissionGranted) {
            CourseReminderPreferences.enabled = true
            lifecycleScope.launch(Dispatchers.IO) {
                CourseReminderScheduler.reschedule(this@CourseReminderSettingsActivity)
                WidgetUpdateCoordinator.updateAll(this@CourseReminderSettingsActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        setComposeContent {
            CourseReminderSettingsScreen(
                onBack = ::finish,
                notificationGranted = notificationPermissionGranted,
                exactGranted = exactAlarmGranted,
                requestNotification = ::requestNotificationPermission,
                openNotificationSettings = ::openNotificationSettings,
                openExactAlarmSettings = ::openExactAlarmSettings,
                openCourseSettings = {
                    startActivity(Intent(this, CourseReminderCoursesActivity::class.java))
                },
                openWidgets = {
                    startActivity(Intent(this, WidgetSetupActivity::class.java))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
        }
    }
}

@Composable
private fun CourseReminderSettingsScreen(
    onBack: () -> Unit,
    notificationGranted: Boolean,
    exactGranted: Boolean,
    requestNotification: () -> Unit,
    openNotificationSettings: () -> Unit,
    openExactAlarmSettings: () -> Unit,
    openCourseSettings: () -> Unit,
    openWidgets: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(CourseReminderPreferences.enabled) }
    var lead by remember { mutableStateOf(CourseReminderPreferences.leadMinutes) }
    var showDetails by remember { mutableStateOf(CourseReminderPreferences.showDetails) }
    var courses by remember { mutableStateOf<List<ScheduleCourse>>(emptyList()) }
    var explainPermission by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun reschedule(showMessage: Boolean = true) {
        scope.launch {
            CourseReminderScheduler.reschedule(context)
            WidgetUpdateCoordinator.updateAll(context)
            if (showMessage) message = "已按当前本地课表重新安排提醒"
        }
    }

    LaunchedEffect(Unit) { courses = CourseScheduleRepository.loadCourses(context) }
    LaunchedEffect(notificationGranted) {
        if (notificationGranted && CourseReminderPreferences.enabled) enabled = true
    }
    val disabledCount = courses.count(CourseReminderPreferences::isDisabled)
    val enabledCount = courses.size - disabledCount

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ReminderHeader("上课提醒", onBack) }
        item {
            ReminderCard {
                ReminderSwitch("上课提醒", "仅根据本机课表发送系统通知", enabled) { checked ->
                    if (checked && !notificationGranted) explainPermission = true else {
                        enabled = checked
                        CourseReminderPreferences.enabled = checked
                        reschedule(false)
                    }
                }
            }
        }
        item {
            ReminderCard {
                Text("提前时间", fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(5, 10, 15, 20, 30)) { minutes ->
                        FilterChip(selected = lead == minutes, onClick = {
                            lead = minutes
                            CourseReminderPreferences.leadMinutes = minutes
                            reschedule(false)
                        }, label = { Text("$minutes 分") })
                    }
                }
                ReminderSwitch("显示教师和教室", "关闭后通知只显示课程名称与时间", showDetails) {
                    showDetails = it
                    CourseReminderPreferences.showDetails = it
                }
            }
        }
        item {
            ReminderNavigationCard(
                title = "课程提醒",
                subtitle = when {
                    courses.isEmpty() -> "暂无本地课程，导入后可逐节设置"
                    disabledCount == 0 -> "所有课程均已开启提醒 · 共 ${courses.size} 节"
                    else -> "已开启 $enabledCount 节 · 已关闭 $disabledCount 节"
                },
                onClick = openCourseSettings,
            )
        }
        item {
            ReminderNavigationCard(
                title = "桌面小组件",
                subtitle = "在桌面查看下一节课和今日课表",
                icon = { Icon(Icons.Rounded.Widgets, null, tint = AppTheme.colors.functionalTextColor) },
                onClick = openWidgets,
            )
        }
        item {
            ReminderCard {
                Text("提醒状态", fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
                Text(if (notificationGranted) "通知权限：已允许" else "通知权限：未允许，提醒无法启用", color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp)
                if (!notificationGranted) TextButton(onClick = openNotificationSettings) { Text("打开通知设置") }
                Text(if (exactGranted) "精确提醒：可用" else "精确提醒：不可用，提醒可能略有延迟", color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp)
                if (!exactGranted) TextButton(onClick = openExactAlarmSettings) { Text("管理精确提醒权限") }
            }
        }
        item {
            OutlinedButton(onClick = { reschedule() }, modifier = Modifier.fillMaxWidth()) { Text("重新安排提醒") }
            if (message.isNotBlank()) Text(message, color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }

    if (explainPermission) {
        AlertDialog(
            onDismissRequest = { explainPermission = false },
            title = { Text("允许上课提醒通知？") },
            text = { Text("开启后，我的川农会按本机课表在课程开始前发送通知。课程数据不会上传到服务器，你也可以随时在系统设置中关闭通知。") },
            confirmButton = { Button(onClick = { explainPermission = false; requestNotification() }) { Text("继续") } },
            dismissButton = { TextButton(onClick = { explainPermission = false }) { Text("暂不开启") } },
        )
    }
}

@Composable
internal fun ReminderHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = AppTheme.colors.primaryTextColor) }
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
    }
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
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

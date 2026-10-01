package com.mychuannong.app.feature.schedule.reminder

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.CourseTimePreferences
import com.mychuannong.app.feature.schedule.ScheduleCourse
import kotlinx.coroutines.launch

class CourseReminderCoursesActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent { CourseReminderCoursesScreen(::finish) }
    }
}

@Composable
private fun CourseReminderCoursesScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<ScheduleCourse>>(emptyList()) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var revision by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(Unit) { courses = CourseScheduleRepository.loadCourses(context) }

    fun toggle(course: ScheduleCourse, enabled: Boolean) {
        CourseReminderPreferences.setCourseEnabled(course, enabled)
        revision++
        scope.launch { CourseReminderScheduler.reschedule(context) }
    }

    val filtered = courses.filter {
        query.text.isBlank() || it.name.contains(query.text.trim(), ignoreCase = true)
    }
    val active = filtered.filterNot(CourseReminderPreferences::isDisabled)
    val disabled = filtered.filter(CourseReminderPreferences::isDisabled)
    @Suppress("UNUSED_VARIABLE") val observeRevision = revision

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ReminderHeader("管理课程提醒", onBack) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索课程名称") },
                placeholder = { Text("输入关键词") },
            )
        }
        if (courses.isEmpty()) {
            item {
                ReminderCard {
                    Text("还没有本地课表", fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
                    Text("请先导入或手动添加课程，再为每一节课程设置提醒。", color = AppTheme.colors.secondaryTextColor)
                }
            }
        } else if (filtered.isEmpty()) {
            item { Text("没有找到匹配的课程", color = AppTheme.colors.secondaryTextColor) }
        } else {
            (1..7).forEach { weekday ->
                val dayCourses = active.filter { it.weekday == weekday }
                    .sortedWith(compareBy<ScheduleCourse> { it.startSection }.thenBy { it.name })
                if (dayCourses.isNotEmpty()) {
                    item(key = "day-$weekday") {
                        CourseGroup(
                            title = "${weekdayName(weekday)}（${dayCourses.size} 节）",
                            expanded = expanded[weekday] == true,
                            onToggleGroup = { expanded[weekday] = !(expanded[weekday] ?: false) },
                            courses = dayCourses,
                            onToggle = ::toggle,
                        )
                    }
                }
            }
            if (disabled.isNotEmpty()) {
                item(key = "disabled") {
                    CourseGroup(
                        title = "已关闭提醒（${disabled.size} 节）",
                        expanded = expanded[-1] == true,
                        onToggleGroup = { expanded[-1] = !(expanded[-1] ?: false) },
                        courses = disabled.sortedWith(compareBy<ScheduleCourse> { it.weekday }.thenBy { it.startSection }),
                        onToggle = ::toggle,
                        showWeekday = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseGroup(
    title: String,
    expanded: Boolean,
    onToggleGroup: () -> Unit,
    courses: List<ScheduleCourse>,
    onToggle: (ScheduleCourse, Boolean) -> Unit,
    showWeekday: Boolean = false,
) {
    ReminderCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleGroup).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起" else "展开", tint = AppTheme.colors.secondaryTextColor)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                courses.forEach { course ->
                    val enabled = !CourseReminderPreferences.isDisabled(course)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(course.name, color = AppTheme.colors.primaryTextColor, fontWeight = FontWeight.Medium)
                            val period = CourseTimePreferences.periods.getOrNull(course.startSection - 1)
                            val time = period?.let { "%02d:%02d".format(it.start.hour, it.start.minute) } ?: "第${course.startSection}节"
                            val prefix = if (showWeekday) "${weekdayName(course.weekday)} · " else ""
                            Text("$prefix$time · ${course.room.ifBlank { "地点待定" }}", color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp)
                            if (!enabled) Text("已关闭提醒", color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp)
                        }
                        Switch(checked = enabled, onCheckedChange = { onToggle(course, it) })
                    }
                }
            }
        }
    }
}

private fun weekdayName(day: Int) = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    .getOrElse(day - 1) { "星期$day" }

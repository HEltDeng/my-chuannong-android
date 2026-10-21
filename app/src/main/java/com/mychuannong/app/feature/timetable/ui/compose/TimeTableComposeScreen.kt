package com.mychuannong.app.feature.timetable.ui.compose

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.absoluteValue

internal val kCourseCardColors = listOf(
    Pair(Color(0xFFCDE8FF), Color(0xFF8EC9F5)),
    Pair(Color(0xFFB8F0CE), Color(0xFF6BDAA0)),
    Pair(Color(0xFFD8D0FF), Color(0xFFA896F5)),
    Pair(Color(0xFFFFE7B0), Color(0xFFFFCA60)),
    Pair(Color(0xFFFFCDD8), Color(0xFFFF9AB8)),
    Pair(Color(0xFFA8EFEC), Color(0xFF5DD5D0)),
    Pair(Color(0xFFFFD8B8), Color(0xFFFFAA72)),
    Pair(Color(0xFFCEF0A8), Color(0xFF8ED460)),
)
internal val kCourseCardTextColor = Color(0xFF1C2B3A)

internal fun courseCardColorPair(courseTitle: String): Pair<Color, Color> =
    kCourseCardColors[courseTitle.hashCode().absoluteValue % kCourseCardColors.size]

private fun generatedCourseColorPair(index: Int): Pair<Color, Color> {
    val hue = ((index * 37) % 360).toFloat()
    val top = Color.hsl(hue, saturation = 0.52f, lightness = 0.84f)
    val bottom = Color.hsl(hue, saturation = 0.62f, lightness = 0.70f)
    return top to bottom
}

private fun buildUniqueCourseColorMap(courses: List<TimeTableCourseUi>): Map<String, Pair<Color, Color>> {
    val titles = courses
        .map { it.title.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    return titles.mapIndexed { index, title ->
        val colors = if (index < kCourseCardColors.size) {
            kCourseCardColors[index]
        } else {
            generatedCourseColorPair(index)
        }
        title to colors
    }.toMap()
}

private const val TotalSections = 10
private const val TotalColumns = 7
private const val TotalWeeks = 20

enum class WeekBadgeState {
    Preview,
    NotStarted,
    Current,
    NotCurrent,
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

@Immutable
private data class TimetableSizeSpec(
    val timeColumnWidth: Dp = 48.dp,
    val cellHeight: Dp = 64.dp,
)

@Composable
fun TimeTableComposeScreen(
    modifier: Modifier = Modifier,
    termText: String,
    rawTerm: String = termText,
    emptyMessage:String = "",
    cacheMessage:String = "",
    weekText: String,
    displayWeek: Int,
    currentWeek: Int,
    termStarted: Boolean,
    courses: List<TimeTableCourseUi>,
    remark: List<String> = emptyList(),
    dateHeaderProvider: (Int) -> Pair<String, List<TimeTableDayHeaderUi>>,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    onBack: () -> Unit = {},
    onTermClick: () -> Unit,
    onWeekClick: () -> Unit,
    onWeekChange: (Int) -> Unit,
    onEmptySlotClick: (dayOfWeek: Int, startSection: Int) -> Unit,
    onCourseClick: (TimeTableCourseUi) -> Unit,
    onOverlapCoursesClick: (List<TimeTableCourseUi>) -> Unit,
    onCourseLongClick: (TimeTableCourseUi) -> Unit,
) {
    val totalWeeks=com.mychuannong.app.common.cache.CommonInfo.teachingWeeks(rawTerm)
    val colors = AppTheme.colors
    val sizeSpec = remember { TimetableSizeSpec() }
    val pagerState = rememberPagerState(
        initialPage = (displayWeek - 1).coerceIn(0, totalWeeks - 1),
        pageCount = { totalWeeks },
    )

    LaunchedEffect(displayWeek) {
        val targetPage = (displayWeek - 1).coerceIn(0, totalWeeks - 1)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != targetPage) {
            if (abs(pagerState.currentPage - targetPage) > 2) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    val coursesByWeek = remember(courses,totalWeeks) {
        (1..totalWeeks).associateWith { week ->
            courses.filter { week in it.weeks && it.sectionSpan > 0 && it.startSection > 0 }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .map { it + 1 }
            .distinctUntilChanged()
            .collect { week ->
                if (week != displayWeek) onWeekChange(week)
            }
    }

    val settledWeek = remember { derivedStateOf { pagerState.settledPage + 1 } }
    val effectiveWeek = settledWeek.value
    val weekBadgeState = remember(termStarted, currentWeek, effectiveWeek) {
        when {
            !termStarted && effectiveWeek == 1 && currentWeek == 1 -> WeekBadgeState.Preview
            !termStarted -> WeekBadgeState.NotStarted
            effectiveWeek == currentWeek -> WeekBadgeState.Current
            else -> WeekBadgeState.NotCurrent
        }
    }
    val (monthText, dayHeaders) = dateHeaderProvider(settledWeek.value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.bgTopBarColor.copy(alpha = 0.95f),
                        colors.bgPrimaryColor,
                        colors.bgPrimaryColor,
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TimeTableTopBar(
            isRefreshing = isRefreshing,
            onRefreshClick = onRefreshClick,
            onBack = onBack,
        )
        TermWeekBar(
            remark = remark,
            termText = termText,
            rawTerm = rawTerm,
            weekText = "第${effectiveWeek}周",
            weekBadgeState = weekBadgeState,
            onTermClick = onTermClick,
            onWeekClick = onWeekClick,
            onTodayClick = { onWeekChange(currentWeek) },
        )
        if (com.mychuannong.app.common.cache.CommonInfo.getTermStartDate(rawTerm) == null) {
            Text("该学期暂无校历日期", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = colors.secondaryTextColor)
        }
        if(cacheMessage.isNotBlank()) Text(cacheMessage,Modifier.padding(horizontal=16.dp,vertical=2.dp),fontSize=11.sp,color=colors.secondaryTextColor)
        if(emptyMessage.isNotBlank()) Text(emptyMessage,Modifier.padding(horizontal=16.dp,vertical=2.dp),fontSize=11.sp,color=colors.secondaryTextColor)
        DateHeaderRow(
            monthText = monthText,
            dayHeaders = dayHeaders,
            timeColumnWidth = sizeSpec.timeColumnWidth,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp,
        ) { pageIndex ->
            val week = pageIndex + 1
            val pageCourses = coursesByWeek[week].orEmpty()
            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue
            val motion = (1f - pageOffset.coerceIn(0f, 1f))
            val cardScale = lerpFloat(0.93f, 1f, motion)
            val cardAlpha = lerpFloat(0.72f, 1f, motion)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.bgCardColor.copy(alpha = 0.88f))
                    .padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            ) {
                val scrollState = rememberScrollState()
                TimeAxisColumn(
                    modifier = Modifier
                        .width(sizeSpec.timeColumnWidth)
                        .verticalScroll(scrollState),
                    cellHeight = sizeSpec.cellHeight,
                )

                TimeTableGrid(
                    modifier = Modifier.weight(1f),
                    cellHeight = sizeSpec.cellHeight,
                    scrollState = scrollState,
                    visibleCourses = pageCourses,
                    onEmptySlotClick = onEmptySlotClick,
                    onCourseClick = onCourseClick,
                    onOverlapCoursesClick = onOverlapCoursesClick,
                    onCourseLongClick = onCourseLongClick,
                )
            }
        }
    }
}

@Composable
private fun TimeTableTopBar(
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    onBack: () -> Unit = {},
) {
    val refreshColor = Color(0xFF4F7FED)
    Box(Modifier.padding(horizontal=16.dp)) {
      com.mychuannong.app.core.theme.SecondaryPageHeader("课程表",onBack) {
        TextButton(onClick = onRefreshClick, enabled = !isRefreshing) {
            if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=refreshColor)
            else Icon(painterResource(R.drawable.coursetable_ic_refresh),contentDescription=null,Modifier.size(22.dp),tint=refreshColor)
            Spacer(Modifier.width(5.dp))
            Text("刷新",color=refreshColor)
        }
      }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TermWeekBar(
    termText: String,
    rawTerm: String,
    weekText: String,
    weekBadgeState: WeekBadgeState,
    remark: List<String> = emptyList(),
    onTermClick: () -> Unit,
    onWeekClick: () -> Unit,
    onTodayClick: () -> Unit,
) {
    val colors = AppTheme.colors
    var showRemarkDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val editYear = remember { mutableStateOf(false) }
    val yearText = remember { mutableStateOf(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.admissionYear?.toString().orEmpty()) }
    val badgeContainerColor = when (weekBadgeState) {
        WeekBadgeState.Preview -> colors.textHeighLightColor.copy(alpha = 0.14f)
        WeekBadgeState.NotStarted -> colors.textHeighLightColor.copy(alpha = 0.14f)
        WeekBadgeState.Current -> colors.successGreenColor.copy(alpha = 0.16f)
        WeekBadgeState.NotCurrent -> colors.secondaryTextColor.copy(alpha = 0.12f)
    }
    val badgeTextColor = when (weekBadgeState) {
        WeekBadgeState.Preview -> colors.textHeighLightColor
        WeekBadgeState.NotStarted -> colors.textHeighLightColor
        WeekBadgeState.Current -> colors.successGreenColor
        WeekBadgeState.NotCurrent -> colors.secondaryTextColor
    }
    val badgeText = if (!com.mychuannong.app.school.SicauAcademicData.isTeachingDate(rawTerm)) "非行课期" else when (weekBadgeState) {
        WeekBadgeState.Preview -> "第1周预览"
        WeekBadgeState.NotStarted -> "未开学"
        WeekBadgeState.Current -> if (com.mychuannong.app.common.cache.CommonInfo.getTermStartDate(rawTerm) == null) "无校历" else "本周"
        WeekBadgeState.NotCurrent -> "本周"
    }
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 1.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TimetableCapsule(
            text = termText,
            modifier = Modifier.padding(end = 6.dp),
            leadingIcon = R.drawable.coursetable_ic_calendar,
            onClick = onTermClick,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TimetableCapsule(
                text = weekText,
                trailingIcon = R.drawable.coursetable_ic_extend,
                onClick = onWeekClick,
            )
            IconButton(
                onClick = {
                    if (remark.isNotEmpty()) {
                        showRemarkDialog.value = true
                    } else {
                        if(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.hasCompleteBinding) editYear.value=true else Toast.makeText(context,"尚未绑定学校账号",Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "备注",
                    tint = colors.secondaryTextColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Surface(
                modifier = Modifier.heightIn(min = 48.dp).clickable {
                    if(com.mychuannong.app.school.SicauAcademicData.isTeachingDate(rawTerm)) onTodayClick()
                    else Toast.makeText(context,"所选学期当前不在行课期",Toast.LENGTH_SHORT).show()
                },
                shape = CircleShape,
                color = badgeContainerColor,
            ) {
                Text(
                    text = badgeText,
                    maxLines = 1,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    if(editYear.value) AlertDialog(onDismissRequest={editYear.value=false},title={Text("校正入学年份")},text={
        Column { Text("按本人学籍填写，用于计算所选学期简称。留空显示学年简称。")
            androidx.compose.material3.OutlinedTextField(value=yearText.value,onValueChange={yearText.value=it.filter(Char::isDigit).take(4)},singleLine=true,label={Text("入学年份")}) }
    },confirmButton={TextButton(enabled=yearText.value.isBlank() || yearText.value.toIntOrNull() in 1900..2200,onClick={
        com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.setAdmissionYearForVerifiedAccount(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId,yearText.value.toIntOrNull());editYear.value=false
    }){Text("保存")}},dismissButton={TextButton(onClick={editYear.value=false}){Text("取消")}})
    if (showRemarkDialog.value) {
        RemarkDialog(
            remark = remark,
            onDismiss = { showRemarkDialog.value = false },
            onEditYear = { showRemarkDialog.value=false;editYear.value=true },
        )
    }

    }

}
@Composable
private fun TimetableCapsule(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null,
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.dp, colors.outlineLowContrastColor.copy(alpha = 0.5f), CircleShape)
            .combinedClickable(onClick = onClick),
        color = colors.bgCardColor.copy(alpha = 0.92f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    modifier = Modifier.size(24.dp, 24.dp),
                    painter = painterResource(id = leadingIcon),
                    contentDescription = null,
                    tint = colors.iconSecondaryColor,
                )
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = colors.primaryTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (trailingIcon != null) {
                Icon(
                    painter = painterResource(id = trailingIcon),
                    contentDescription = null,
                    tint = colors.iconSecondaryColor,
                )
            }
        }
    }
}

@Composable
private fun DateHeaderRow(
    monthText: String,
    dayHeaders: List<TimeTableDayHeaderUi>,
    timeColumnWidth: Dp,
) {
    val colors = AppTheme.colors
    val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 340
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCardColor.copy(alpha = 0.35f))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.bgSecondaryColor.copy(alpha = 0.8f),
        ) {
            Text(
                text = if(compact && monthText.contains("/"))monthText.removeSuffix("月") else monthText,
                color = colors.primaryTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(timeColumnWidth)
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        dayHeaders.forEach { header ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if(compact)header.weekdayLabel.removePrefix("周") else header.weekdayLabel,
                    maxLines=1,
                    color = if (header.isToday) colors.textHeighLightColor else colors.primaryTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if(compact)header.dayOfMonthLabel.removeSuffix("日") else header.dayOfMonthLabel,
                    maxLines=1,
                    color = if (header.isToday) colors.textHeighLightColor else colors.greyTextColor,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun TimeAxisColumn(
    modifier: Modifier,
    cellHeight: Dp,
) {
    val colors = AppTheme.colors
    val times = run {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("H:mm")
        com.mychuannong.app.feature.schedule.CourseTimePreferences.periods.map {
            "${it.start.format(formatter)}\n${it.end.format(formatter)}"
        }
    }

    Column(modifier = modifier) {
        repeat(TotalSections) { index ->
            Box(
                modifier = Modifier
                    .height(cellHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .drawBehind {
                        drawLine(
                            color = colors.dividerColor.copy(alpha = 0.08f),
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = 1f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = times.getOrNull(index) ?: "—",
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = colors.primaryTextColor.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun TimeTableGrid(
    modifier: Modifier,
    cellHeight: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    visibleCourses: List<TimeTableCourseUi>,
    onEmptySlotClick: (dayOfWeek: Int, startSection: Int) -> Unit,
    onCourseClick: (TimeTableCourseUi) -> Unit,
    onOverlapCoursesClick: (List<TimeTableCourseUi>) -> Unit,
    onCourseLongClick: (TimeTableCourseUi) -> Unit,
) {
    val colors = AppTheme.colors
    val totalHeight = cellHeight * TotalSections
    val courseColorMap = remember(visibleCourses) {
        buildUniqueCourseColorMap(visibleCourses)
    }
    val cellCoursesMap = remember(visibleCourses) {
        val result = mutableMapOf<Pair<Int, Int>, MutableList<TimeTableCourseUi>>()
        visibleCourses.forEach { course ->
            val endSection = (course.startSection + course.sectionSpan - 1).coerceAtMost(TotalSections)
            for (section in course.startSection..endSection) {
                val key = course.dayOfWeek to section
                result.getOrPut(key) { mutableListOf() }.add(course)
            }
        }
        result
    }
    val courseOverlapMap = remember(visibleCourses, cellCoursesMap) {
        val result = mutableMapOf<Int, List<TimeTableCourseUi>>()
        visibleCourses.forEach { course ->
            val endSection = (course.startSection + course.sectionSpan - 1).coerceAtMost(TotalSections)
            val overlaps = linkedSetOf<TimeTableCourseUi>()
            for (section in course.startSection..endSection) {
                cellCoursesMap[course.dayOfWeek to section].orEmpty().forEach { overlaps.add(it) }
            }
            result[course.id] = overlaps.toList()
        }
        result
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        val boardWidth = maxWidth
        val columnWidth = boardWidth / TotalColumns

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .padding(end = 2.dp),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val lineColor = colors.dividerColor.copy(alpha = 0.1f)
                val h = size.height
                val w = size.width
                val rowHeightPx = cellHeight.toPx()
                val colWidthPx = w / TotalColumns

                repeat(TotalSections + 1) { row ->
                    val y = row * rowHeightPx
                    drawLine(
                        lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(w, y),
                        strokeWidth = 0.8f,
                    )
                }

                repeat(TotalColumns + 1) { col ->
                    val x = col * colWidthPx
                    drawLine(
                        lineColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, h),
                        strokeWidth = 0.8f,
                    )
                }
            }

            Column(modifier = Modifier.matchParentSize()) {
                repeat(TotalSections) { section ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(TotalColumns) { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight)
                                    .combinedClickable(
                                        onClick = {
                                            val hitCourses =
                                                cellCoursesMap[day + 1 to section + 1].orEmpty()
                                            when (hitCourses.size) {
                                                0 -> onEmptySlotClick(day + 1, section + 1)
                                                1 -> onCourseClick(hitCourses.first())
                                                else -> onOverlapCoursesClick(hitCourses)
                                            }
                                        },
                                        onLongClick = {
                                            val hitCourses =
                                                cellCoursesMap[day + 1 to section + 1].orEmpty()
                                            if (hitCourses.isNotEmpty()) {
                                                onCourseLongClick(hitCourses.first())
                                            }
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            visibleCourses.forEach { course ->
                val x = columnWidth * (course.dayOfWeek - 1)
                val y = cellHeight * (course.startSection - 1)
                val cardHeight = cellHeight * course.sectionSpan

                TimeTableCourseCard(
                    course = course,
                    courseColors = courseColorMap[course.title] ?: courseCardColorPair(course.title),
                    modifier = Modifier
                        .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                        .width(columnWidth)
                        .height(cardHeight)
                        .padding(horizontal = 1.5.dp, vertical = 1.5.dp),
                    onClick = {
                        val overlaps = courseOverlapMap[course.id].orEmpty()
                        when (overlaps.size) {
                            0, 1 -> onCourseClick(course)
                            else -> onOverlapCoursesClick(overlaps)
                        }
                    },
                    onLongClick = {
                        val overlaps = courseOverlapMap[course.id].orEmpty()
                        onCourseLongClick(overlaps.firstOrNull() ?: course)
                    },
                )
            }
        }
    }
}

@Composable
private fun TimeTableCourseCard(
    course: TimeTableCourseUi,
    courseColors: Pair<Color, Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (topColor, bottomColor) = courseColors
    val cardBrush = remember(topColor, bottomColor) {
        Brush.verticalGradient(listOf(topColor, bottomColor))
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(start = 2.dp, end = 2.dp, top = 13.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = course.title,
                color = kCourseCardTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 3,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (course.room.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.room,
                    color = kCourseCardTextColor.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (course.teacher.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = course.teacher,
                    color = kCourseCardTextColor.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
private fun RemarkDialog(
    remark: List<String>,
    onDismiss: () -> Unit,
    onEditYear: () -> Unit,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { if(com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.hasCompleteBinding)TextButton(onClick=onEditYear){Text("校正入学年份")} },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "我知道了", color = colors.commonColor)
            }
        },
        title = {
            Text(text = "备注", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                remark.forEachIndexed { index, item ->
                    Text(
                        text = "${index + 1}. $item",
                        color = colors.primaryTextColor,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        containerColor = colors.bgCardColor,
        titleContentColor = colors.primaryTextColor,
        textContentColor = colors.secondaryTextColor,
    )
}

@Preview(showBackground = true)
@Composable
private fun TimeTableScreenPreview() {
    AppSkinTheme {
        TimeTableComposeScreen(
            termText = "大一下",
            weekText = "第5周",
            displayWeek = 5,
            currentWeek = 5,
            termStarted = true,
            isRefreshing = false,
            courses = listOf(
                TimeTableCourseUi(1, "高等数学", "张老师", "明理楼A201", 1, 1, 2, setOf(1, 2, 3, 4, 5), false),
                TimeTableCourseUi(2, "数据结构", "李老师", "综合楼C305", 3, 5, 2, setOf(5, 6, 7), true),
                TimeTableCourseUi(3, "英语", "王老师", "外语楼202", 5, 9, 2, setOf(5), false),
            ),
            dateHeaderProvider = {
                "3月" to listOf(
                    TimeTableDayHeaderUi("周一", "03日", false),
                    TimeTableDayHeaderUi("周二", "04日", false),
                    TimeTableDayHeaderUi("周三", "05日", true),
                    TimeTableDayHeaderUi("周四", "06日", false),
                    TimeTableDayHeaderUi("周五", "07日", false),
                    TimeTableDayHeaderUi("周六", "08日", false),
                    TimeTableDayHeaderUi("周日", "09日", false),
                )
            },
            onRefreshClick = {},
            onTermClick = {},
            onWeekClick = {},
            onWeekChange = {},
            onEmptySlotClick = { _, _ -> },
            onCourseClick = {},
            onOverlapCoursesClick = {},
            onCourseLongClick = {},
        )
    }
}

package com.mychuannong.app.feature.calendar.ui.compose

import android.os.Bundle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.calendar.data.remote.dto.SemesterCalendarDetail
import com.mychuannong.app.feature.calendar.data.remote.dto.SemesterCalendarNote
import com.mychuannong.app.feature.calendar.data.remote.dto.SemesterCalendarWeekRange
import com.mychuannong.app.feature.calendar.viewmodel.SemesterCalendarDetailViewModel
import kotlinx.coroutines.launch

/**
 * 校历详情页 Activity（Compose 壳）。
 *
 * 入参（Intent extras）：
 *  - [EXTRA_SEMESTER_CODE] 必填，如 `"2025-2026-2"`
 *  - [EXTRA_TITLE] / [EXTRA_SUBTITLE] 选填，网络未就绪时作为标题兜底
 *
 * 兼容性说明：所有日期运算使用 [java.util.Calendar]，**不依赖 java.time.\***，
 * 以适配项目 `minSdk = 24`（未开启 core library desugaring）。
 */
class SemesterCalendarDetailActivity : ComposeActivity() {

    companion object {
        const val EXTRA_SEMESTER_CODE = "semester_code"
        const val EXTRA_TITLE = "semester_title"
        const val EXTRA_SUBTITLE = "semester_subtitle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val semesterCode = intent.getStringExtra(EXTRA_SEMESTER_CODE).orEmpty()
        val fallbackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val fallbackSubtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
        setComposeContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = AppTheme.colors.bgPrimaryColor
            ) {
                SemesterCalendarDetailScreen(
                    semesterCode = semesterCode,
                    fallbackTitle = fallbackTitle,
                    fallbackSubtitle = fallbackSubtitle,
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * 详情页主 Screen。负责：Top Bar、三态渲染（加载/错误/内容）、跳到今天 FAB。
 */
@Composable
fun SemesterCalendarDetailScreen(
    semesterCode: String,
    fallbackTitle: String = "",
    fallbackSubtitle: String = "",
    onBack: () -> Unit,
    viewModel: SemesterCalendarDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(semesterCode) { viewModel.load(semesterCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()

    ) {
        DetailTopBar(
            title = state.detail?.title?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.ifBlank { "学期校历" },
            subtitle = state.detail?.subtitle?.takeIf { it.isNotBlank() } ?: fallbackSubtitle,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            sourceResource = state.detail?.sourceResource.orEmpty()
        )

        if(state.errorMessage.isNotBlank()) Text(state.errorMessage,Modifier.padding(12.dp),fontSize=12.sp)
        Box(modifier = Modifier.fillMaxSize()) {
            val detail = state.detail
            when {
                state.isLoading && detail == null -> CenterLoading()
                detail == null -> CenterTip(state.errorMessage.ifBlank { "暂无校历数据" })
                else -> CalendarDetailContent(detail = detail)
            }
        }
    }
}

@Composable
private fun CenterLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppTheme.colors.commonColor)
    }
}

@Composable
private fun CenterTip(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = AppTheme.colors.greyTextColor, fontSize = 14.sp)
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    sourceResource: String
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    com.mychuannong.app.school.SchoolPageTopBar(subtitle.ifBlank { "学期校历" },onBack,onRefresh,listOf(
        "在浏览器中打开" to {com.mychuannong.app.school.openOfficialBrowser(context,com.mychuannong.app.school.SicauAcademicData.SOURCE)},
        "查看官方来源" to { context.startActivity(android.content.Intent(context,com.mychuannong.app.SchoolWebActivity::class.java).putExtra("url",com.mychuannong.app.school.SicauAcademicData.SOURCE).putExtra("title","官方校历来源")) },
        "查看官方原图" to { context.startActivity(android.content.Intent(context,com.mychuannong.app.SchoolWebActivity::class.java).putExtra("url",sourceResource.ifBlank { com.mychuannong.app.school.SicauAcademicData.current().sourceResources.first().url }).putExtra("title","官方校历原图")) }
    ))
}

/**
 * 校历主体内容（表格风格）：
 *  - 顶部学期概览紧凑条
 *  - 表格（表头 + N 行 + 蓝色教学周 overlay，整体是 LazyColumn 的 1 个 item）
 *  - 浮动按钮：学期内时显示"第 X 周"；学期外则显示"回到开学周"
 *
 * 进入页面自动滚到当前教学周（若不在学期范围内则滚到开学周作为兜底）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalendarDetailContent(detail: SemesterCalendarDetail) {
    val weeks=remember(detail){buildWeekRows(detail)}
    val start=parseIsoDateMs(detail.semesterStart)!!
    val today=todayAtMidnightMs()
    val weekIndex=remember(detail){buildTeachingWeekIndex(weeks,start)}
    val membershipStart=parseIsoDateMs(detail.events.firstOrNull{it.type=="opening"}?.start?:detail.semesterStart)!!
    val current=if(today>=membershipStart && today<=parseIsoDateMs(detail.calendarEnd)!!)weeks.indexOfFirst{today in (it.startMs!!..it.endMs!!)} else -1
    val beginning=weeks.indexOfFirst{start in (it.startMs!!..it.endMs!!)}.coerceAtLeast(0)
    val target=if(current>=0)current else beginning
    val listState=rememberLazyListState()
    val stickyOffset=with(LocalDensity.current){(-60).dp.roundToPx()}
    val scope=rememberCoroutineScope()
    val c=AppTheme.colors
    var selected by remember{mutableStateOf<DayCellData?>(null)}
    LaunchedEffect(detail.semesterCode){if(current>=0)listState.scrollToItem(target+2,stickyOffset)}
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state=listState,contentPadding=PaddingValues(start=12.dp,end=12.dp,top=10.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            item { SemesterInfoCard(detail) }
            stickyHeader {
                Row(Modifier.fillMaxWidth().background(c.bgPrimaryColor).padding(vertical=10.dp)) {
                    Text("周",Modifier.width(36.dp),fontSize=12.sp)
                    listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=12.sp)}
                }
            }
            items(weeks.size){i->
                val row=weeks[i]
                val w=weekIndex[row.row]?.takeIf{it<=detail.teachingWeeks}
                Row(Modifier.fillMaxWidth().background(if(i==current)c.titleTopColor.copy(alpha=.08f) else c.bgCardColor),verticalAlignment=Alignment.CenterVertically) {
                    Text(w?.toString()?:"—",Modifier.width(36.dp),fontSize=12.sp,color=c.titleTopColor)
                    row.days.forEach { day ->
                        val iso=day?.let{java.time.Instant.ofEpochMilli(it.dateMs).atZone(com.mychuannong.app.school.SicauAcademicData.zone).toLocalDate().toString()}.orEmpty()
                        val events=detail.events.filter{iso>=it.start && (it.type=="vacation_start" || iso<=it.end)}
                        val marker=when { events.any{it.type=="practice"}->"实践";events.any{it.type=="vacation" || it.type=="vacation_start"}->"假期";events.isNotEmpty()->"事件";else->"" }
                        Column(Modifier.weight(1f).height(62.dp).border(.5.dp,c.greyTextColor.copy(alpha=.15f)).background(if(current>=0 && day?.dateMs==today)c.titleTopColor.copy(alpha=.20f) else androidx.compose.ui.graphics.Color.Transparent).clickable{selected=day}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(if(day==null)"" else if(day.dayOfMonth==1 || java.time.Instant.ofEpochMilli(day.dateMs).atZone(com.mychuannong.app.school.SicauAcademicData.zone).dayOfWeek.value==1 || i==0) "${day.month}/${day.dayOfMonth}" else day.dayOfMonth.toString(),fontSize=13.sp,fontWeight=if(current>=0 && day?.dateMs==today)FontWeight.Bold else FontWeight.Normal,color=c.primaryTextColor)
                            Text(marker,fontSize=9.sp,color=if(marker=="实践")c.titleTopColor else c.greyTextColor)
                        }
                    }
                }
            }
            items(detail.events.size){i->val ev=detail.events[i];Text("${ev.start} ${if(ev.end!=ev.start)"至 ${ev.end} " else ""}${ev.name}",fontSize=13.sp,modifier=Modifier.padding(vertical=4.dp))}
            item { Text("来源：四川农业大学 · 核对 ${com.mychuannong.app.school.SicauAcademicData.current().checkedAt.take(10)}",fontSize=11.sp,color=c.greyTextColor) }
        }
        Button(onClick={scope.launch{listState.animateScrollToItem(target+2,stickyOffset)}},modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),colors=ButtonDefaults.buttonColors(containerColor=c.titleTopColor)) {
            Text(if(current>=0) "今天 / ${weekIndex[weeks[current].row]?.takeIf{it<=detail.teachingWeeks}?.let{"第${it}周"}?:"假期"}" else "开学周")
        }
    }
    selected?.let { day ->
        val date=java.time.Instant.ofEpochMilli(day.dateMs).atZone(com.mychuannong.app.school.SicauAcademicData.zone).toLocalDate()
        val week=com.mychuannong.app.feature.schedule.CourseScheduleEngine.teachingWeek(java.time.LocalDate.parse(detail.semesterStart),date)
        val events=detail.events.filter{date.toString()>=it.start && (it.type=="vacation_start" || date.toString()<=it.end)}
        AlertDialog(onDismissRequest={selected=null},title={Text(date.toString())},text={Text((if(week in 1..detail.teachingWeeks)"第${week}教学周" else "非教学周")+"\n"+events.joinToString("\n"){if(it.type=="vacation_start")"${it.name}（结束日期未公布）" else it.name}.ifBlank{"暂无校历事件"})},confirmButton={TextButton(onClick={selected=null}){Text("关闭")}})
    }
}

/**
 * 渲染完整表格：表头 + N 行 + 顶层蓝色折线。
 *
 * 表格整体宽度固定为 [TABLE_TOTAL_WIDTH]，超过屏幕宽时由外层 `horizontalScroll` 承担
 * "整张表格横滑"的体验（所有列的日期/备注随之一起滑动，视觉一致）。
 *
 * @param currentRowIdx 今天所在周的下标（0-based），用于整行高亮；`-1` 表示不在学期内
 */
@Composable
private fun CalendarTable(
    weeks: List<WeekRow>,
    monthLabels: Map<Int, String?>,
    teachingWeekByRow: Map<Int, Int>,
    noteMap: Map<Int, List<IndexedNote>>,
    rangeByRow: Map<Int, SemesterCalendarWeekRange>,
    semesterStartMs: Long?,
    semesterEndMs: Long?,
    todayMs: Long,
    currentRowIdx: Int,
    noteOrdinals: Map<Int, Int>
) {
    val horizontalScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
            .background(AppTheme.colors.bgPrimaryColor)
    ) {
        // 底层：表头 + 所有周行
        Column(modifier = Modifier.width(TABLE_TOTAL_WIDTH)) {
            WeekdayHeaderRow()
            weeks.forEachIndexed { idx, row ->
                WeekRowCard(
                    row = row,
                    teachingWeekIndex = teachingWeekByRow[row.row],
                    monthLabel = monthLabels[row.row],
                    semesterStartMs = semesterStartMs,
                    semesterEndMs = semesterEndMs,
                    todayMs = todayMs,
                    isCurrentRow = idx == currentRowIdx,
                    notes = noteMap[row.row].orEmpty(),
                    range = rangeByRow[row.row],
                    noteOrdinals = noteOrdinals
                )
            }
        }

        // 上层：蓝色教学周折线（fillMaxSize 会撑到底层 Column 的宽高，坐标一致）
        TeachingPeriodOverlay(
            weeks = weeks,
            rangeByRow = rangeByRow,
            semesterStartMs = semesterStartMs,
            semesterEndMs = semesterEndMs,
            modifier = Modifier
                .width(TABLE_TOTAL_WIDTH)
                .height(HEADER_ROW_HEIGHT + TABLE_ROW_HEIGHT * weeks.size)
        )
    }
}

/**
 * 构建月份合并列：每周显示"其第一天所在的月"；与上一周同月则置 null（实现合并单元格效果）。
 */
private fun buildMonthLabels(weeks: List<WeekRow>): Map<Int, String?> {
    val result = HashMap<Int, String?>(weeks.size)
    var lastMonth = -1
    for (row in weeks) {
        val month = row.days.firstNotNullOfOrNull { it?.month } ?: continue
        result[row.row] = if (month != lastMonth) monthCn(month) else null
        lastMonth = month
    }
    return result
}

private val MONTH_CN_LABELS = arrayOf(
    "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月"
)

private fun monthCn(month: Int): String =
    MONTH_CN_LABELS.getOrNull(month - 1) ?: "$month 月"

/**
 * 为所有 `needNumber=true` 的 notes **按 `row` 升序** 分配 1-based 编号。
 *
 * 键是 note 在原 `notes` 列表中的全局 index，不用 `(row, content)` 拼接是为了避免
 * "同周同文"两条备注被当成同一条而丢失编号。
 *
 * 排序的理由：服务端返回的 `notes` 数组顺序无保证，按 row 升序编号后，
 * 屏幕上从上到下看到的圆圈数字才会是 ①②③... 递增。
 */
private fun buildNoteOrdinals(notes: List<SemesterCalendarNote>): Map<Int, Int> {
    val result = HashMap<Int, Int>()
    var ordinal = 0
    notes.withIndex()
        .filter { it.value.needNumber }
        .sortedBy { it.value.row }
        .forEach { (idx, _) ->
            ordinal += 1
            result[idx] = ordinal
        }
    return result
}

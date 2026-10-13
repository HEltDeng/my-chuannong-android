package com.mychuannong.app.overview.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.common.data.local.mmkv.UserInfoManager
import com.mychuannong.app.common.data.local.room.database.UserDataBase
import com.mychuannong.app.feature.common.compose_ui.FunctionDestination
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.calendar.data.repository.SemesterCalendarRepository
import com.mychuannong.app.overview.ui.model.OverviewCourseUiModel
import com.mychuannong.app.overview.ui.model.OverviewExamUiModel
import com.mychuannong.app.overview.ui.model.OverviewMetricUiModel
import com.mychuannong.app.overview.ui.model.OverviewUiState
import com.mychuannong.app.utils.NetworkUtil
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.scoreSummaryFields
import com.mychuannong.app.school.education.data.remote.EducationHelper
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.mychuannong.app.school.education.data.remote.services.ExamArrangeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OverviewRepository(
    context: Context
) {

    private val appContext = context.applicationContext
    private val courseDao by lazy { CoursesDataBase.getDatabase(appContext).courseDao() }
    private val userDao by lazy { UserDataBase.getInstance(appContext).itemDao() }
    private val examCache by lazy { ExamArrangementCache() }
    suspend fun loadLocalState(): OverviewUiState = withContext(Dispatchers.IO) {
        buildState(
            courses = readLocalCourses(),
            grades = ScoreCache.getGrades().orEmpty(),
            exams = examCache.getExamArrangement().orEmpty().toUiExams()
        )
    }

    suspend fun refreshState(): OverviewUiState = withContext(Dispatchers.IO) {
        com.mychuannong.app.school.SchoolSyncEngine.sync(false)
        loadLocalState().copy(isSilentSyncing=false)
    }

    private suspend fun buildState(
        courses: List<TimeTableMySubject>,
        grades: List<Grade>,
        exams: List<OverviewExamUiModel>
    ): OverviewUiState = withContext(Dispatchers.IO) {
        val studentId = StudentInfoManager.studentId
        val transcript = ScoreCache.getTranscript(studentId)
        val currentTerm = getCurrentTerm()
        val syncItems = com.mychuannong.app.school.SchoolSyncEngine.items.value
        val currentWeek = getCurrentWeek(currentTerm)
        val currentUser = UserInfoManager.userId.takeIf { it > 0 }?.let { userDao.getUserById(it) }

        val isShowingTomorrow = java.time.LocalTime.now(com.mychuannong.app.school.SicauAcademicData.zone) >= com.mychuannong.app.feature.schedule.CourseTimePreferences.periods.last().end
        val targetWeekday: Int
        val targetWeek: Int
        val targetCalendar: Calendar

        if (isShowingTomorrow) {
            targetCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).apply { add(Calendar.DAY_OF_MONTH, 1) }
            targetWeekday = targetCalendar.let {
                when (it.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> 7
                    else -> it.get(Calendar.DAY_OF_WEEK) - 1
                }
            }
            targetWeek = if (targetWeekday == 1) currentWeek + 1 else currentWeek
        } else {
            targetCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            targetWeekday = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).let {
                when (it.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> 7
                    else -> it.get(Calendar.DAY_OF_WEEK) - 1
                }
            }
            targetWeek = currentWeek
        }

        val displayCourses = courses.toDayCourses(targetWeekday, targetWeek)
        OverviewUiState(
            isRefreshing = false,
            isSilentSyncing = false,
            isBoundStudent = StudentInfoManager.hasCompleteBinding,
            isOnline = hasNetwork(),
            accountName = StudentInfoManager.greeting,
            avatarUrl = currentUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: UserInfoManager.userAvatar,
            studentId = studentId,
            dateText = buildDateText(currentTerm, targetWeek, targetCalendar, isShowingTomorrow),
            currentTerm = currentTerm,
            currentWeek = currentWeek,
            dataSourceLabel = if (courses.isEmpty() && transcript == null && grades.isEmpty()) "尚未同步" else "当前为本地缓存数据",
            metrics = buildMetrics(grades, transcript),
            todayCourses = displayCourses,
            todayCourseMessage = when {
                displayCourses.isNotEmpty() -> ""
                courses.isNotEmpty() -> if(isShowingTomorrow) "明日没有课程 · 点击查看完整课表" else "今日没有课程 · 点击查看完整课表"
                studentId.isBlank() -> "先绑定学号 · 点击进入课表"
                syncItems["courses"]?.phase == com.mychuannong.app.school.SyncPhase.LOADING -> "正在获取课表…"
                else -> "暂无本学期课表缓存 · 点击进入课表获取数据"
            },
            isShowingTomorrow = isShowingTomorrow,
            pendingHomeworks = emptyList(),
            pendingHomeworkMessage = when {
                studentId.isBlank() -> "先绑定学号"
                else -> "尚未同步 · 学校解析待适配"
            },
            pendingTests = emptyList(),
            pendingTestMessage = when {
                studentId.isBlank() -> "先绑定学号"
                else -> "尚未同步 · 学校解析待适配"
            },
            upcomingExams = exams.take(3),
            examMessage = when {
                studentId.isBlank() -> "先绑定学号"
                exams.isNotEmpty() -> ""
                else -> syncItems["exams"]?.message ?: "尚未同步"
            }
        )
    }

    private suspend fun readLocalCourses(): List<TimeTableMySubject> {
        val studentId = StudentInfoManager.studentId
        val studentPassword = StudentInfoManager.studentPassword
        // Cached courses remain available even when the business session expires.
        return withContext(Dispatchers.IO) {
            com.mychuannong.app.feature.schedule.CourseScheduleRepository.readRows(courseDao,getCurrentTerm(),studentId)
        }
    }

    private fun buildMetrics(
        grades: List<Grade>,
        transcript: com.mychuannong.app.school.SchoolTranscript?,
    ): List<OverviewMetricUiModel> {
        return listOf(
            OverviewMetricUiModel(
                id = FunctionDestination.ScoreInquiry.name,
                title = "平均绩点",
                value = transcript?.summary?.firstOrNull { it.name in setOf("平均绩点", "平均成绩绩点") }?.value ?: "--",
                subtitle = transcript?.summary?.filter { it.name in setOf("算术平均成绩", "加权平均成绩") }
                    ?.joinToString(" · ") { "${it.name}：${it.value}" }?.takeIf { it.isNotBlank() }
                    ?: if (grades.isEmpty()) "暂无成绩缓存 · 点击获取成绩" else "点击查看课程成绩",
                secondarySubtitle = transcript?.let { com.mychuannong.app.school.LearningCacheStatus.text(true, it.updatedAt) }.orEmpty(),
                summaryFields = transcript.scoreSummaryFields(),
                iconRes = R.drawable.ic_grade,
                accentColor = Color(0xFF237B62)
            )
        )
    }
    private fun formatMetricNumber(value: Double): String {
        return String.format(Locale.CHINA, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun preprocessGrades(rawData: List<Grade>): List<Grade> {
        return rawData
            .groupBy { it.name }
            .map { (_, grades) ->
                val retakeGrade = grades.find { grade ->
                    grade.upperReItem.isNotBlank() ||
                        grade.property.contains("重修", ignoreCase = true) ||
                        grade.property.contains("补考", ignoreCase = true)
                }

                retakeGrade ?: grades.maxByOrNull { it.grade.toDoubleOrNull() ?: 0.0 }!!
            }
    }

    private fun hasNetwork(): Boolean = NetworkUtil.getNetworkType(appContext) != NetworkUtil.NetworkType.None

    private fun buildDateText(term: String, week: Int, targetCalendar: Calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")), isShowingTomorrow: Boolean = false): String {
        val weekday = listOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")[targetCalendar.get(Calendar.DAY_OF_WEEK)]
        val prefix = if (isShowingTomorrow) "明日" else ""
        val termLabel=com.mychuannong.app.feature.timetable.ui.AcademicTermDisplayFormatter.format(term,StudentInfoManager.admissionYear)
        val day=java.time.Instant.ofEpochMilli(targetCalendar.timeInMillis).atZone(com.mychuannong.app.school.SicauAcademicData.zone).toLocalDate()
        val weekLabel=if(com.mychuannong.app.school.SicauAcademicData.isTeachingDate(term,day)) "第${week}周" else "非行课期"
        return "${prefix}${targetCalendar.get(Calendar.MONTH) + 1}月${targetCalendar.get(Calendar.DAY_OF_MONTH)}日 $weekday · $termLabel · $weekLabel"
    }

    private fun getCurrentTerm(): String =
        com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()

    private fun getCurrentWeek(term: String): Int =
        com.mychuannong.app.common.cache.CommonInfo.getCurrentWeekInt(term)

    private fun getCurrentWeekForDate(term: String, calendar: Calendar): Int {
        val startTime = com.mychuannong.app.common.cache.CommonInfo.getTermStartDate(term) ?: return 1
        return runCatching {
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.parse(startTime.take(10)) ?: return 1
            val targetDate = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            (((targetDate.time - startDate.time) / TimeUnit.DAYS.toMillis(1)) / 7 + 1).toInt().coerceAtLeast(1)
        }.getOrDefault(1)
    }

    private fun parseWeeks(weekJson: String): WeekJsonInfo {
        val pattern = Pattern.compile("(\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*)\\((周|单周|双周)?\\)?\\[(\\d{2})(?:-(\\d{2}))?(?:-(\\d{2}))?(?:-(\\d{2}))?节\\]")
        val matcher = pattern.matcher(weekJson)
        if (!matcher.find()) return WeekJsonInfo(emptyList(), 0, 0)
        val weeksRange = matcher.group(1)
        val weekType = matcher.group(2)
        val startClass = matcher.group(3)?.toIntOrNull() ?: 0
        val endClass = listOfNotNull(
            matcher.group(4)?.toIntOrNull(),
            matcher.group(5)?.toIntOrNull(),
            matcher.group(6)?.toIntOrNull()
        ).lastOrNull() ?: startClass
        val weeks = weeksRange?.split(",")?.flatMap { part ->
            if (part.contains("-")) {
                val (start, end) = part.split("-").map { it.toInt() }
                when (weekType) {
                    "单周" -> (start..end).filter { it % 2 != 0 }
                    "双周" -> (start..end).filter { it % 2 == 0 }
                    else -> (start..end).toList()
                }
            } else {
                listOf(part.toInt())
            }
        }.orEmpty()
        return WeekJsonInfo(weeks, startClass, endClass - startClass + 1)
    }

    private fun List<ExamArrange>.toUiExams(): List<OverviewExamUiModel> =
        mapNotNull { exam ->
            if (exam.courseNameval.isBlank() || exam.examTime.isBlank()) null else {
                OverviewExamUiModel(
                    id = "${exam.courseNameval}_${exam.examTime}",
                    courseName = exam.courseNameval,
                    examTime = exam.examTime,
                    location = listOf(exam.campus, exam.examRoomval).filter { it.isNotBlank() }.joinToString(" · "),
                    badge = if (exam.examTime.contains("明天")) "明天" else "考试"
                )
            }
        }

    private fun List<TimeTableMySubject>.toDayCourses(targetWeekday: Int, currentWeek: Int): List<OverviewCourseUiModel> {
        val colors = listOf(Color(0xFF5E87F6), Color(0xFF45B979), Color(0xFFF08D3C), Color(0xFFB974F0))
        return filter { it.weekday == targetWeekday && (it.weeks.isNullOrEmpty() || currentWeek in it.weeks.orEmpty()) }
            .sortedBy { it.start }
            .mapIndexed { index, course ->
                OverviewCourseUiModel(
                    id = "${course.courseName}_${course.start}_${course.weekday}_$index",
                    courseName = course.courseName,
                    classroom = course.classroom.orEmpty().ifBlank { "教室待定" },
                    teacher = course.teacher.ifBlank { "教师待定" },
                    timeText = buildCourseTimeText(course.start, course.step),
                    accentLabel = if (course.isCustom) "自定义" else "校园课表",
                    accentColor = colors[index % colors.size]
                )
            }
    }

    private fun buildCourseTimeText(startSection: Int, sectionSpan: Int): String {
        val periods = com.mychuannong.app.feature.schedule.CourseTimePreferences.periods
        val start = periods.getOrNull(startSection - 1)?.start
        val end = periods.getOrNull(startSection + sectionSpan - 2)?.end
        return if (start != null && end != null) "$start–$end" else "${startSection}-${startSection + sectionSpan - 1}节"
    }

    private data class WeekJsonInfo(
        val weeks: List<Int>,
        val start: Int,
        val step: Int
    )

}

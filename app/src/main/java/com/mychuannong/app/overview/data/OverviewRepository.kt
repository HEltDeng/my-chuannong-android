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
        coroutineScope {
            val currentTerm = getCurrentTerm()
            // 刷新前触发一次校历预取（非阻塞 fire-and-forget，失败静默）
            SemesterCalendarRepository.prefetchDetailIfMissing(currentTerm)
            val courseDeferred = async(Dispatchers.IO) { fetchCourses(currentTerm) }
            val gradesDeferred = async(Dispatchers.IO) { fetchGrades() }
            val examsDeferred = async(Dispatchers.IO) { fetchExams(currentTerm) }

            val courses = courseDeferred.await() ?: readLocalCourses()
            val grades = gradesDeferred.await() ?: ScoreCache.getGrades().orEmpty()
            val exams = examsDeferred.await() ?: examCache.getExamArrangement().orEmpty().toUiExams()

            buildState(
                courses = courses,
                grades = grades,
                exams = exams
            ).copy(isSilentSyncing = false)
        }
    }

    private suspend fun buildState(
        courses: List<TimeTableMySubject>,
        grades: List<Grade>,
        exams: List<OverviewExamUiModel>
    ): OverviewUiState = withContext(Dispatchers.IO) {
        val studentId = StudentInfoManager.studentId
        val studentPassword = StudentInfoManager.studentPassword
        val currentTerm = getCurrentTerm()
        val syncItems = com.mychuannong.app.school.SchoolSyncEngine.items.value
        val currentWeek = getCurrentWeek(currentTerm)
        val currentUser = UserInfoManager.userId.takeIf { it > 0 }?.let { userDao.getUserById(it) }

        val isShowingTomorrow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21
        val targetWeekday: Int
        val targetWeek: Int
        val targetCalendar: Calendar

        if (isShowingTomorrow) {
            targetCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
            targetWeekday = targetCalendar.let {
                when (it.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> 7
                    else -> it.get(Calendar.DAY_OF_WEEK) - 1
                }
            }
            targetWeek = if (targetWeekday == 1) currentWeek + 1 else currentWeek
        } else {
            targetCalendar = Calendar.getInstance()
            targetWeekday = Calendar.getInstance().let {
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
            isSilentSyncing = hasNetwork(),
            isBoundStudent = studentId.isNotBlank() && studentPassword.isNotBlank(),
            isOnline = hasNetwork(),
            accountName = currentUser?.account?.takeIf { it.isNotBlank() } ?: UserInfoManager.account.ifBlank { "我的川农" },
            avatarUrl = currentUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: UserInfoManager.userAvatar,
            studentId = studentId,
            dateText = buildDateText(currentTerm, targetWeek, targetCalendar, isShowingTomorrow),
            currentTerm = currentTerm,
            currentWeek = currentWeek,
            dataSourceLabel = if (courses.isEmpty() && grades.isEmpty()) "尚未同步" else "本机缓存",
            metrics = buildMetrics(grades),
            todayCourses = displayCourses,
            todayCourseMessage = when {
                studentId.isBlank() || studentPassword.isBlank() -> syncItems["courses"]?.message ?: "先绑定学号"
                displayCourses.isNotEmpty() -> ""
                else -> "尚未同步 · 学校解析待适配"
            },
            isShowingTomorrow = isShowingTomorrow,
            pendingHomeworks = emptyList(),
            pendingHomeworkMessage = when {
                studentId.isBlank() || studentPassword.isBlank() -> syncItems["courses"]?.message ?: "先绑定学号"
                else -> "尚未同步 · 学校解析待适配"
            },
            pendingTests = emptyList(),
            pendingTestMessage = when {
                studentId.isBlank() || studentPassword.isBlank() -> syncItems["courses"]?.message ?: "先绑定学号"
                else -> "尚未同步 · 学校解析待适配"
            },
            upcomingExams = exams.take(3),
            examMessage = when {
                studentId.isBlank() || studentPassword.isBlank() -> syncItems["courses"]?.message ?: "先绑定学号"
                exams.isNotEmpty() -> ""
                else -> "尚未同步 · 学校解析待适配"
            }
        )
    }

    private suspend fun readLocalCourses(): List<TimeTableMySubject> {
        val studentId = StudentInfoManager.studentId
        val studentPassword = StudentInfoManager.studentPassword
        if (studentId.isBlank() || studentPassword.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            courseDao.getCoursesByTerm(getCurrentTerm(), studentId)
        }
    }

    private suspend fun fetchGrades(): List<Grade>? {
        val result = runCatching { EducationHelper.getCourseGrades() }.getOrNull() ?: return null
        if (result.code != "200") return null
        val grades = result.data?.map {
            Grade(
                id = it.courseID,
                item = it.semester,
                name = it.courseName,
                grade = it.grade.toString(),
                flag = it.gradeIdentifier,
                score = it.credit.toString(),
                timeR = it.totalHours.toString(),
                point = it.gradePoint.toString(),
                upperReItem = it.retakeSemester,
                method = it.assessmentMethod,
                property = it.examNature,
                attribute = it.courseAttribute,
                reItem = it.groupName,
                pscjUrl = it.gradeDetailUrl
            )
        }.orEmpty()
        val processedGrades = grades
        if (processedGrades.isNotEmpty()) ScoreCache.saveGrades(processedGrades)
        return processedGrades
    }

    private suspend fun fetchCourses(term: String): List<TimeTableMySubject>? {
        return when (val result = runCatching { EducationHelper.getCourseScheduleByTerm("", term) }.getOrNull()) {
            is Resource.Success -> {
                val subjects = result.data.courses.map {
                    val weekInfo = parseWeeks(it.weeks)
                    TimeTableMySubject(
                        courseName = it.courseName,
                        classroom = it.classroom,
                        teacher = it.teacher,
                        weeks = weekInfo.weeks,
                        start = weekInfo.start,
                        step = weekInfo.step,
                        weekday = it.weekday.toInt(),
                        term = term,
                        studentId = StudentInfoManager.studentId
                    )
                }.distinctBy {
                    "${it.courseName}${it.classroom}${it.teacher}${it.start}${it.step}${it.weekday}${it.term}"
                }
                val studentId = StudentInfoManager.studentId
                val studentPassword = StudentInfoManager.studentPassword
                if (subjects.isNotEmpty() && studentId.isNotBlank() && studentPassword.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        courseDao.deleteNetworkCoursesByTerm(term, studentId)
                        courseDao.insertCourses(subjects.toMutableList())
                    }
                    com.mychuannong.app.WidgetUpdateCoordinator.updateAll(com.mychuannong.app.core.PlanetApplication.appContext)
                    com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(com.mychuannong.app.core.PlanetApplication.appContext)
                }
                if (studentId.isBlank() || studentPassword.isBlank()) {
                    subjects
                } else {
                    courseDao.getCoursesByTerm(term, studentId)
                }
            }

            else -> null
        }
    }

    private suspend fun fetchExams(term: String): List<OverviewExamUiModel>? {
        val examResponse = runCatching { ExamArrangeService.getExamArrange(term) }.getOrNull() ?: return null
        if (examResponse !is Resource.Success) return null
        val exams = examResponse.data
        if (exams.isNotEmpty()) {
            examCache.saveExamArrangement(exams)
        }
        return exams.toUiExams()
    }

    private fun buildMetrics(
        grades: List<Grade>,
    ): List<OverviewMetricUiModel> {
        val processedGrades = grades
        val totalCredits = processedGrades.sumOf { it.score.toDoubleOrNull() ?: 0.0 }
        val gpa = if (totalCredits > 0) {
            processedGrades.sumOf { (it.score.toDoubleOrNull() ?: 0.0) * (it.point.toDoubleOrNull() ?: 0.0) } / totalCredits
        } else {
            0.0
        }
        val averageScore = if (totalCredits > 0) {
            processedGrades.sumOf { (it.grade.toDoubleOrNull() ?: 0.0) * (it.score.toDoubleOrNull() ?: 0.0) } / totalCredits
        } else {
            0.0
        }
        return listOf(
            OverviewMetricUiModel(
                id = FunctionDestination.ScoreInquiry.name,
                title = "平均绩点",
                value = "--",
                subtitle = if (grades.isEmpty()) "尚未同步" else "统计口径待确认 · 可查看单科成绩",
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

    private fun buildDateText(term: String, week: Int, targetCalendar: Calendar = Calendar.getInstance(), isShowingTomorrow: Boolean = false): String {
        val weekday = listOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")[targetCalendar.get(Calendar.DAY_OF_WEEK)]
        val prefix = if (isShowingTomorrow) "明日" else ""
        return "${prefix}${targetCalendar.get(Calendar.MONTH) + 1}月${targetCalendar.get(Calendar.DAY_OF_MONTH)}日 $weekday  ·  学年学期待确认 · 教学周待确认"
    }

    private fun getCurrentTerm(): String =
        com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()

    private fun getCurrentWeek(term: String): Int =
        com.mychuannong.app.common.cache.CommonInfo.getCurrentWeekInt(term)

    private fun getCurrentWeekForDate(term: String, calendar: Calendar): Int {
        val startTime = com.mychuannong.app.common.cache.CommonInfo.getTermStartDate(term) ?: return 1
        return runCatching {
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(startTime.take(10)) ?: return 1
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
        return "${startSection}-${startSection + sectionSpan - 1}节 · 作息待确认"
    }

    private data class WeekJsonInfo(
        val weeks: List<Int>,
        val start: Int,
        val step: Int
    )

}

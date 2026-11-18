package com.mychuannong.app.feature.timetable.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.common.cache.CommonInfo
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.network.ApiResponse
import com.mychuannong.app.core.security.SchoolSessionCoordinator
import com.mychuannong.app.core.security.SchoolSessionResult
import com.mychuannong.app.core.security.SchoolSessionState
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import com.mychuannong.app.feature.common.data.remote.dto.Course
import com.mychuannong.app.feature.timetable.ui.entity.TimeTableUiState
import com.mychuannong.app.school.core.Resource
import com.mychuannong.app.school.education.data.remote.EducationHelper
import com.mychuannong.app.school.education.data.remote.model.CourseScheduleData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.regex.Pattern

class TimeTableViewModel : ViewModel() {

    companion object {
        private const val TAG = "TimeTableViewModel"
    }

    private val mmkv by lazy { MMKV.defaultMMKV() }
    private val studentId get() = StudentInfoManager.studentId
    private val studentPassword get() = StudentInfoManager.studentPassword
    private val dataBase by lazy { CoursesDataBase.getDatabase(PlanetApplication.appContext) }
    private val courseDao by lazy { dataBase.courseDao() }

    // UI State
    private val _uiState = MutableLiveData<TimeTableUiState>()
    val uiState: LiveData<TimeTableUiState> = _uiState

    private val courseRequests = CourseRefreshCoordinator<List<TimeTableMySubject>>(
        viewModelScope,
        needsAuthentication = { com.mychuannong.app.school.SchoolAuthState.phase.value == com.mychuannong.app.school.SchoolAuthPhase.SESSION_EXPIRED },
        onTimeout = { com.mychuannong.app.school.schoolDiagnostic("timetable=timeout") },
    )
    val coursesResponse = courseRequests.response
    val isRefreshing = courseRequests.refreshing
    internal val refreshFailures = courseRequests.failures

    // Add Course Response
    private val _addCourseResponse = MutableLiveData<ApiResponse<Unit>>()
    val addCourseResponse: LiveData<ApiResponse<Unit>> = _addCourseResponse

    // Delete Course Response
    private val _deleteCourseResponse = MutableLiveData<ApiResponse<Unit>>()
    val deleteCourseResponse: LiveData<ApiResponse<Unit>> = _deleteCourseResponse

    // Current Display Week
    private val _curDisplayWeek = MutableLiveData<Int>()
    val curDisplayWeek: LiveData<Int> = _curDisplayWeek

    /**
     * 当前所选学期的开学日期（`yyyy-MM-dd HH:mm:ss`）。
     *
     * 用于驱动课表日期表头按**校历**锚定，而非"当前自然周"兜底。
     * - [selectTerm] 切换学期时先同步填入缓存命中值，
     *   再异步调 [CommonInfo.fetchTermStartDate]（内部走网络库 [EducationHelper.getSemesterStartDate]）
     *   拉取最新开学日并落盘，到账后更新本 State，UI 即可 recompose 出正确的日期头。
     */
    private val _termStartDate = MutableStateFlow<String?>(null)
    val termStartDate: StateFlow<String?> = _termStartDate

    private var freshTerm:String?=null
    private var visibleOwner=studentId
    init {
        val initState = TimeTableUiState(term = getCurrentTerm())
        _uiState.value = initState
        _curDisplayWeek.value = 1
        viewModelScope.launch {
            StudentInfoManager.academicRevision.collect {
                if(visibleOwner!=studentId)loadCourses(_uiState.value?.term ?: getCurrentTerm())
            }
        }
        viewModelScope.launch {
            com.mychuannong.app.school.SchoolSyncEngine.revision.collect {
                val term=_uiState.value?.term ?: getCurrentTerm()
                reloadFromCache(term)
            }
        }
    }

    internal suspend fun reloadFromCache(term:String,
        dao:com.mychuannong.app.feature.common.data.local.room.dao.CourseDao=courseDao,
        account:String=studentId) {
        val rows=withContext(Dispatchers.IO) { com.mychuannong.app.feature.schedule.CourseScheduleRepository.readRows(dao,term,account) }
        withContext(Dispatchers.Main) { if(account==studentId && term==_uiState.value?.term) updateUiState(rows.toMutableList(),term,loadRemarkFromLocal(term)) }
    }

    fun initFirstLaunch() { /* Room migrations preserve preview upgrade data. */ }

    fun getCurrentTerm(): String = CommonInfo.getCurrentTerm()

    fun loadCourses(term: String, forceRefresh: Boolean = false) {
        val owner=studentId
        val identityRevision=StudentInfoManager.academicRevision.value
        if(visibleOwner!=owner) {
            visibleOwner=owner;freshTerm=null
            _uiState.value=TimeTableUiState(term=term)
        }
        com.mychuannong.app.school.schoolDiagnostic(if (forceRefresh) "timetable=manual_start" else "timetable=load_start")
        courseRequests.start(manual = forceRefresh) {
                suspend fun currentIdentity(): Boolean {
                    if (!isCurrent()) return false
                    if (owner != studentId || identityRevision != StudentInfoManager.academicRevision.value) {
                        complete(ApiResponse.Error("学校信息已变化，请重试"))
                        return false
                    }
                    return true
                }
                val courses=withContext(Dispatchers.IO) { normalizeCourses(com.mychuannong.app.feature.schedule.CourseScheduleRepository.readRows(courseDao,term,owner)) }
                if (!currentIdentity()) return@start
                com.mychuannong.app.school.schoolDiagnostic("timetable=cache_read")
                freshTerm=null
                updateUiState(courses.toMutableList(),term,loadRemarkFromLocal(term))
                if (!forceRefresh) complete(ApiResponse.Success(courses))
                if (owner.isBlank()) {
                    if (forceRefresh) complete(ApiResponse.Error("请先绑定学校账号"), requiresAuthentication = true)
                    return@start
                }
                if (!forceRefresh && (courses.isNotEmpty() || com.mychuannong.app.school.SchoolSyncEngine.courseUpdatedAt(term)>0)) return@start
                loading()
                // Reaching this point means the selected term has no usable cache or the
                // user explicitly asked for a refresh. A prior failed binding sync must not
                // suppress this request for another thirty minutes.
                com.mychuannong.app.school.schoolDiagnostic("timetable=sync_start")
                com.mychuannong.app.school.SchoolSyncEngine.syncCourses()
                if (!currentIdentity()) return@start
                com.mychuannong.app.school.schoolDiagnostic("timetable=sync_finished")
                val status=com.mychuannong.app.school.SchoolSyncEngine.items.value.getValue("courses")
                val rows=withContext(Dispatchers.IO){com.mychuannong.app.feature.schedule.CourseScheduleRepository.readRows(courseDao,term,owner)}
                if (!currentIdentity()) return@start
                freshTerm=term.takeIf { status.phase in setOf(com.mychuannong.app.school.SyncPhase.DATA,com.mychuannong.app.school.SyncPhase.EMPTY) && com.mychuannong.app.school.SchoolSyncEngine.syncedTerm()==term }
                updateUiState(rows.toMutableList(),term,loadRemarkFromLocal(term))
                val failure = if (status.phase in setOf(com.mychuannong.app.school.SyncPhase.DATA, com.mychuannong.app.school.SyncPhase.EMPTY) &&
                    com.mychuannong.app.school.SchoolSyncEngine.syncedTerm() != term) "学校返回的课表不属于当前选择的学期" else status.message
                complete(if(freshTerm!=null || !forceRefresh && rows.isNotEmpty())ApiResponse.Success(rows) else ApiResponse.Error(failure))
        }
    }
    private fun distinctSubjects(subjects: MutableList<TimeTableMySubject>): MutableList<TimeTableMySubject> {
        return subjects.distinctBy {
            "${it.courseName}${it.teacher}${it.weeks}${it.classroom}${it.start}${it.step}${it.term}${it.weekday}"
        }.toMutableList()
    }

    private fun normalizeCourses(courses: List<TimeTableMySubject>): List<TimeTableMySubject> {
        return courses.distinctBy {
            "${it.courseName}${it.teacher}${it.weeks}${it.classroom}${it.start}${it.step}${it.term}${it.weekday}"
        }
    }

    fun addCourse(course: TimeTableMySubject) {
        viewModelScope.launch {
            _addCourseResponse.value = ApiResponse.Loading()

            try {
                val insertedId = withContext(Dispatchers.IO) {
                    courseDao.insertCourse(course)
                }

                if (insertedId == -1L) {
                    _addCourseResponse.value = ApiResponse.Error("该课程已存在，无法重复添加")
                    return@launch
                }

                val insertedCourse = course.copy(id = insertedId.toInt())

                val currentState = _uiState.value ?: TimeTableUiState()
                val updatedSubjects = currentState.subjects.toMutableList().apply {
                    add(insertedCourse)
                }

                updateUiState(updatedSubjects, currentState.term)
                _addCourseResponse.value = ApiResponse.Success(Unit)
                refreshScheduleOutputs()

            } catch (e: Exception) {
                Log.e(TAG, "Error adding course", e)
                _addCourseResponse.value = ApiResponse.Error("添加课程失败: ${e.message}")
            }
        }
    }

    fun deleteCourse(courseId: Int, term: String) {
        viewModelScope.launch {
            _deleteCourseResponse.value = ApiResponse.Loading()

            try {
                val deletedRows = withContext(Dispatchers.IO) {
                    courseDao.deleteCustomCourseById(courseId)
                }

                if (deletedRows <= 0) {
                    _deleteCourseResponse.value = ApiResponse.Error("删除失败，课程不存在或非自定义课程")
                    return@launch
                }

                val currentState = _uiState.value ?: TimeTableUiState()
                val updatedSubjects = currentState.subjects.filterNot {
                    it.id == courseId && it.isCustom
                }.toMutableList()

                updateUiState(updatedSubjects, term)
                _deleteCourseResponse.value = ApiResponse.Success(Unit)
                refreshScheduleOutputs()

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting course", e)
                _deleteCourseResponse.value = ApiResponse.Error("删除课程失败: ${e.message}")
            }
        }
    }

    fun selectWeek(weekInfo: String) {
        val weekNumber = extractWeekNumber(weekInfo)
        _curDisplayWeek.value = weekNumber
        val currentState = _uiState.value ?: TimeTableUiState()
        _uiState.value = currentState.copy(weekInfo = weekInfo)
    }

    fun selectTerm(term: String) {
        loadCourses(term)
        ensureTermStartDate(term)
    }

/**
     * 确保所选学期的"校历开学日期"已落盘并暴露给 UI。
     *
     * 流程：
     *  1. 同步先填入缓存命中值（命中即按校历渲染，未命中暂时为 null）；
     *  2. 调用 [CommonInfo.fetchTermStartDate]（内部走网络库 [EducationHelper.getSemesterStartDate]）
     *     拉取开学日并落盘，到账后更新 [_termStartDate]，触发日期表头按校历重新渲染；
     *     失败时保留既有值（不把 null 覆盖回已命中的缓存值）。
     */
    fun ensureTermStartDate(term: String) {
        _termStartDate.value = CommonInfo.getTermStartDate(term)
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = CommonInfo.fetchTermStartDate(term)
            if (fetched != null && fetched != _termStartDate.value) {
                _termStartDate.value = fetched
            }
        }
    }

    private fun updateUiState(subjects: MutableList<TimeTableMySubject>, term: String, remark: List<String> = emptyList()) {
        val currentWeekInfo = _uiState.value?.weekInfo ?: "第1周"
        _uiState.value = TimeTableUiState(
            subjects = subjects,
            term = term,
            weekInfo = currentWeekInfo,
            lastUpdate = com.mychuannong.app.school.SchoolSyncEngine.courseUpdatedAt(term),
            isCached = freshTerm!=term,
            remark = remark,
        )
    }

    private fun extractWeekNumber(weekString: String): Int {
        val regex = Regex("\\d+")
        val matchResult = regex.find(weekString)
        return matchResult?.value?.toInt() ?: 1
    }


    private fun saveRemarkToLocal(term: String, remark: List<String>) {
        com.mychuannong.app.school.SchoolSyncEngine.saveImportNotes(studentId,term,Gson().toJson(remark))
    }

    private fun loadRemarkFromLocal(term: String): List<String> {
        val schoolNotes=com.mychuannong.app.school.SchoolSyncEngine.courseNotes(studentId,term)
        if(schoolNotes.isNotEmpty()) return schoolNotes
        val json = com.mychuannong.app.school.SchoolSyncEngine.importNotes(studentId,term)
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseWeeks(weekJson: String): WeekJsonInfo {
        val pattern = Pattern.compile(
            "(\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*)\\((周|单周|双周)?\\)?\\[(\\d{2})(?:-(\\d{2}))?(?:-(\\d{2}))?(?:-(\\d{2}))?节\\]"
        )
        val matcher = pattern.matcher(weekJson)

        if (matcher.find()) {
            val weeksRange = matcher.group(1)
            val weekType = matcher.group(2)
            val startClass = matcher.group(3)?.toIntOrNull() ?: 0
            val endClass = listOfNotNull(
                matcher.group(4)?.toIntOrNull(),
                matcher.group(5)?.toIntOrNull(),
                matcher.group(6)?.toIntOrNull(),
            ).lastOrNull() ?: startClass

            val step = endClass - startClass + 1

            val weeks = try {
                weeksRange?.let { range ->
                    range.split(",").flatMap { part ->
                        if (part.contains("-")) {
                            val (weekStart, weekEnd) = part.split("-").map { it.toInt() }
                            when (weekType) {
                                "单周" -> (weekStart..weekEnd).filter { it % 2 != 0 }
                                "双周" -> (weekStart..weekEnd).filter { it % 2 == 0 }
                                else -> (weekStart..weekEnd).toList()
                            }
                        } else {
                            listOf(part.toInt())
                        }
                    }
                } ?: listOf()
            } catch (e: Exception) {
                listOf()
            }

            return WeekJsonInfo(weeks, startClass, step)
        }

        return WeekJsonInfo(listOf(), 0, 0)
    }

    private fun generateSubjects(
        courses: List<Course>,
        newTerm: String
    ): MutableList<TimeTableMySubject> {
        return courses.map {
            val weekInfo = parseWeeks(it.weeks)
            TimeTableMySubject(
                courseName = it.courseName,
                classroom = it.classroom,
                teacher = it.teacher,
                weeks = weekInfo.weeks,
                start = weekInfo.start,
                step = weekInfo.step,
                weekday = it.weekday.toInt(),
                term = newTerm,
                studentId = studentId
            )
        }.toMutableList()
    }

    private fun toLocalCourse(
        courses: List<com.mychuannong.app.school.education.data.remote.model.Course>
    ): List<Course> {
        return courses.map {
            Course(
                it.classroom,
                it.courseName,
                it.teacher,
                it.weekday,
                it.weeks
            )
        }
    }

    fun getCurWeek(term: String): Int =
        com.mychuannong.app.feature.schedule.CourseScheduleRepository.termStart(term)
            ?.let {
                com.mychuannong.app.feature.schedule.CourseScheduleEngine.teachingWeek(
                    it,
                    java.time.LocalDate.now(ZoneId.of("Asia/Shanghai")),
                )
            }
            ?.coerceAtLeast(1)
            ?: CommonInfo.getCurrentWeekInt(term)

    private fun refreshScheduleOutputs() {
        com.mychuannong.app.WidgetUpdateCoordinator.updateAll(PlanetApplication.appContext)
        viewModelScope.launch(Dispatchers.IO) {
            com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler.reschedule(PlanetApplication.appContext)
        }
    }

    fun hasTermStarted(term: String): Boolean = CommonInfo.hasTermStarted(term)

    data class WeekJsonInfo(val weeks: List<Int>, val start: Int, val step: Int)
}

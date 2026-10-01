package com.mychuannong.app.common.cache

import com.mychuannong.app.feature.calendar.data.local.SemesterCalendarCache
import com.mychuannong.app.feature.calendar.data.repository.SemesterCalendarRepository
import com.mychuannong.app.school.education.data.remote.EducationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 学期与周次相关工具。
 *
 * 重构说明：
 *  - 原先的硬编码 [termMap] 仅作为**网络数据未就绪时的兜底**；
 *  - 所有业务统一通过 [getTermStartDate] 获取开学日期，内部按
 *    **内存/本地缓存（由 [SemesterCalendarRepository] 回写） → 内置 termMap** 顺序查找；
 *  - 学期切换规则、当前周计算也统一收敛到本 object，避免全仓库 5+ 份副本不一致。
 */
object CommonInfo {

    // ---------------- 常量 ----------------

    /** 与 termMap 保持一致的开学日期格式。 */
    const val TERM_DATE_TIME_FORMAT: String = "yyyy-MM-dd HH:mm:ss"

    /** 仅日期部分格式。 */
    const val TERM_DATE_FORMAT: String = "yyyy-MM-dd"

    /** 周次选择器容量，不代表川农已确认的学期长度。 */
    const val MAX_TEACHING_WEEK: Int = 20

    private const val SHANGHAI_TZ_ID = "Asia/Shanghai"
    private const val UPCOMING_TERM_PREVIEW_DAYS = 7L
    private val shanghaiTz: TimeZone get() = TimeZone.getTimeZone(SHANGHAI_TZ_ID)
    private val shanghaiZone: ZoneId get() = ZoneId.of(SHANGHAI_TZ_ID)

    // ---------------- 内置 termMap（兜底） ----------------

    /**
     * 内置兜底学期开学日期（格式：[TERM_DATE_TIME_FORMAT]）。
     *
     * 仅在服务端尚未返回数据、本地也无缓存时作为 fallback；
     * 后续可随服务端上线的学期逐步移除。
     */
    val termMap: Map<String, String> = emptyMap()

    // ---------------- 当前学期（统一入口） ----------------

    /**
     * 兼容字段：App 冷启动时间戳（由 [com.mychuannong.app.core.MainActivity] 写入）。
     *
     * 项目内无读点，保留避免破坏历史调用；未来建议统一走
     * [com.mychuannong.app.core.PlanetApplication.startTime]。
     */
    @Deprecated(
        "使用 PlanetApplication.startTime 代替",
        ReplaceWith("com.mychuannong.app.core.PlanetApplication.startTime")
    )
    var startTime: Long = 0L

    /**
     * 计算当前学期代码（统一规则，全 App 共用）。
     *
     * 优先按**已知的开学日期**判断：合并内置 [termMap] 与已缓存的学期列表，
     * 选取"开学日不晚于现在、且仍在教学周区间（[MAX_TEACHING_WEEK] 周内）"的
     * 最近一个学期；若下一学期开学日在短预览窗口内，则提前选择下一学期的第 1 周。
     * 没有任何已知学期时才回退到基于月份的兜底规则。
     *
     * 月份兜底规则（基于上海时区）：
     *  - 7-12 月 → `当年-下一年-1`（秋季）
     *  - 2-6 月 → `上一年-当年-2`（春季）
     *  - 1 月   → `上一年-当年-1`（上学年延续）
     *
     * @return 形如 `"2025-2026-1"` 的学期代码
     */
    fun getCurrentTerm(): String {
        val today = LocalDate.now(shanghaiZone)
        resolveCurrentTermByDate(today, mergedTermStarts())?.let { return it }
        return fallbackTerm(today)
    }

    /**
     * Deterministic form of [getCurrentTerm] for date-boundary tests and callers that already
     * have the term-start mapping. It deliberately does not read the device cache or the network.
     */
    fun getCurrentTerm(today: LocalDate): String {
        resolveCurrentTermByDate(today, termMap)?.let { return it }
        return fallbackTerm(today)
    }

    private fun fallbackTerm(today: LocalDate): String {
        val year = today.year
        val month = today.monthValue
        return when {
            month >= 7 -> "$year-${year + 1}-1"
            month >= 2 -> "${year - 1}-${year}-2"
            else -> "${year - 1}-${year}-1"
        }
    }

    /**
     * 基于已知开学日期推断"当前正在进行的学期"。
     *
     * 选取规则：
     *  1. 合并内置 [termMap] 与已缓存学期列表（缓存优先覆盖）；
     *  2. 若最近的未来学期在预览窗口内，优先返回该学期；
     *  3. 否则过滤出"开学日不晚于现在"的候选，再取"教学周仍在 [1, [MAX_TEACHING_WEEK]] 区间内"者，
     *     没有任何学期仍处于教学周时，退而求其次保留最近一个已开学学期，
     *     以便用户仍能看到上一学期的课表，而不是直接跳到下一学期；
     *  4. 取开学日最近的一个返回。
     *
     * @return 学期代码，或 null（无任何已知学期已开学，由调用方回退到月份规则）
     */
    private fun mergedTermStarts(): Map<String, String> = HashMap<String, String>(termMap.size + 4).apply {
        putAll(termMap)
        SemesterCalendarRepository.getListSync()?.forEach { item ->
            val start = SemesterCalendarRepository.getTermStartDate(item.semesterCode)
            if (!start.isNullOrBlank()) put(item.semesterCode, start)
        }
    }

    private fun resolveCurrentTermByDate(
        today: LocalDate,
        terms: Map<String, String>,
    ): String? {
        val datedTerms = terms.mapNotNull { (term, rawStart) ->
            parseTermDate(rawStart)?.let { term to it }
        }
        val started = datedTerms
            .asSequence()
            .filter { (_, startDate) -> !startDate.isAfter(today) }
            .sortedByDescending { (_, startDate) -> startDate }
            .toList()

        // 在新学期开学前的短预览窗口内，默认进入 upcoming term 的第 1 周。这样 9 月
        // 5/6 日仍显示 9 月 7 日至 13 日，而不会让上学期的最后一周占据默认页面。
        datedTerms.asSequence()
            .filter { (_, startDate) -> startDate.isAfter(today) }
            .sortedBy { (_, startDate) -> startDate }
            .firstOrNull()
            ?.takeIf { (_, startDate) ->
                ChronoUnit.DAYS.between(today, startDate) in 1..UPCOMING_TERM_PREVIEW_DAYS
            }
            ?.let { return it.first }

        if (started.isEmpty()) return null

        // 优先返回仍处于教学周内的学期；若全部已超过教学周上限，
        // 则保留最近一个已开学学期，避免提前跳到下一学期。
        return started.firstOrNull { (_, startDate) ->
            teachingWeek(startDate, today) in 1..MAX_TEACHING_WEEK
        }?.first ?: started.first().first
    }

    // ---------------- 开学日期（统一入口） ----------------

    /**
     * 统一获取学期开学日期（[TERM_DATE_TIME_FORMAT] 格式）。
     *
     * 查找顺序：内存/本地缓存 → 内置 [termMap]；均未命中返回 null。
     *
     * 本方法**不会触发网络请求**（保持同步行为，适合 Widget、ViewModel init 等场景调用）；
     * 如需拉取最新开学日，调用挂起的 [fetchTermStartDate]。
     *
     * @param term 学期代码，如 `"2024-2025-1"`
     * @return 开学日期字符串，或 null（未命中）
     */
    fun getTermStartDate(term: String): String? {
        if (term.isBlank()) return null
        SemesterCalendarRepository.getTermStartDate(term)?.let { return it }
        return termMap[term]
    }

    /**
     * 通过网络库 [EducationHelper.getSemesterStartDate] 拉取学期开学日，
     * 归一化后回写本地缓存（[SemesterCalendarCache.saveTermStartDate]）并返回。
     *
     * 向 [SemesterCalendarRepository] 内存层同样回写一份 detail（仅含 [SemesterCalendarDetail.semesterStart]），
     * 以便同步读 [getTermStartDate] 立即命中。
     *
     * 在 IO 上执行；失败返回 null，不抛异常。
     *
     * @return 开学日（`yyyy-MM-dd HH:mm:ss`），或 null
     */
    suspend fun fetchTermStartDate(term: String): String? {
        if (term.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val dateStr = EducationHelper.getSemesterStartDate(term) ?: return@withContext null

                val parser = SimpleDateFormat(TERM_DATE_FORMAT, Locale.CHINA).apply {
                    isLenient = false
                    timeZone = shanghaiTz
                }
                val parsed = parser.parse(dateStr) ?: return@withContext null
                val nextDay = Calendar.getInstance(shanghaiTz).apply {
                    time = parsed
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                val formatted = SimpleDateFormat(TERM_DATE_TIME_FORMAT, Locale.CHINA).apply {
                    timeZone = shanghaiTz
                }.format(nextDay.time)

                SemesterCalendarCache.saveTermStartDate(term, formatted)
                formatted
            }.getOrNull()
        }
    }

    // ---------------- 当前周（文本 & 数值统一入口） ----------------

    /**
     * 获取当前是开学第几周（文本形式）。
     *
     * @param currentTerm 当前学期代码
     * @return `"第 N 周"` / `"未开学"` / `"未知"` / `"计算错误"`
     */
    fun getCurrentWeek(currentTerm: String): String {
        val startTimeStr = getTermStartDate(currentTerm) ?: return "未知"
        val weekNumber = computeWeekNumber(startTimeStr, LocalDate.now(shanghaiZone)) ?: return "计算错误"
        if (weekNumber <= 0) return "未开学"
        return "第 $weekNumber 周"
    }

    /**
     * 获取当前是开学第几周（数值形式）。
     *
     * 与 [getCurrentWeek] 共用底层算法，结果被 clamp 到 `1..[maxWeek]` 区间。
     * 未命中学期时返回 1（与历史业务保持兼容，避免上游崩溃）。
     *
     * @param term 学期代码
     * @param maxWeek 上限，默认 [MAX_TEACHING_WEEK]
     */
    fun getCurrentWeekInt(term: String, maxWeek: Int = MAX_TEACHING_WEEK): Int {
        val startTimeStr = getTermStartDate(term) ?: return 1
        val week = computeWeekNumber(startTimeStr, LocalDate.now(shanghaiZone)) ?: return 1
        return week.coerceIn(1, maxWeek)
    }

    /** Deterministic date-boundary form of [getCurrentWeekInt]. */
    fun getCurrentWeekInt(term: String, today: LocalDate, maxWeek: Int = MAX_TEACHING_WEEK): Int {
        val startTimeStr = termMap[term] ?: return 1
        val week = computeWeekNumber(startTimeStr, today) ?: return 1
        return week.coerceIn(1, maxWeek)
    }

    /**
     * 判断学期是否已经开学（今天 >= 开学日）。未命中学期按"已开学"处理。
     */
    fun hasTermStarted(term: String): Boolean {
        val startTimeStr = getTermStartDate(term) ?: return true
        return runCatching {
            val startDate = parseTermDate(startTimeStr) ?: return true
            !LocalDate.now(shanghaiZone).isBefore(startDate)
        }.getOrDefault(true)
    }

    /** Deterministic date-boundary form of [hasTermStarted]. */
    fun hasTermStarted(term: String, today: LocalDate): Boolean {
        val startTimeStr = termMap[term] ?: return true
        return runCatching {
            val startDate = parseTermDate(startTimeStr) ?: return true
            !today.isBefore(startDate)
        }.getOrDefault(true)
    }

    /**
     * 自动获取当前学期的周数（给 Widget / 启动路径快速调用）。
     *
     * 性能说明：[TERM_DATE_TIME_FORMAT] 格式可直接按字典序比较，
     * 无需 SimpleDateFormat.parse，避免 Widget 主线程频繁解析。
     */
    fun getCurrentWeekAuto(): String {
        val today = LocalDate.now(shanghaiZone)
        val best = resolveCurrentTermByDate(today, mergedTermStarts()) ?: return "未开学"

        return getCurrentWeek(best)
    }

    // ---------------- 内部工具 ----------------

    /**
     * 根据开学日期字符串计算"今天是第几周（1-based）"。
     * 开学日之前返回 0；解析失败返回 null。
     */
    private fun computeWeekNumber(startTimeStr: String, today: LocalDate): Int? {
        val startDate = parseTermDate(startTimeStr) ?: return null
        return teachingWeek(startDate, today)
    }

    /**
     * 解析 [TERM_DATE_TIME_FORMAT] 或仅日期部分（前 10 字符）为 [LocalDate]。
     */
    private fun parseTermDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

    private fun teachingWeek(termStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(termStart, date)
        return if (days < 0) 0 else (days / 7L + 1L).toInt()
    }
}

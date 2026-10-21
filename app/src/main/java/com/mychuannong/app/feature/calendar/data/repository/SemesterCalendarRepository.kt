package com.mychuannong.app.feature.calendar.data.repository
import com.mychuannong.app.feature.calendar.data.remote.dto.*
import com.mychuannong.app.school.SicauAcademicData

/** The verified public bundle is atomic; personal course caches are never touched here. */
object SemesterCalendarRepository {
    fun getListSync():List<SemesterCalendarListItem> = SicauAcademicData.current().terms.map { SemesterCalendarListItem(it.semesterCode,it.title,it.subtitle) }
    fun getDetailSync(semesterCode:String):SemesterCalendarDetail? = SicauAcademicData.term(semesterCode)
    fun getTermStartDate(semesterCode:String):String? = getDetailSync(semesterCode)?.semesterStart?.let { "$it 00:00:00" }
    suspend fun getList(forceRefresh:Boolean=false):Result<List<SemesterCalendarListItem>> {
        val result=SicauAcademicData.refresh(forceRefresh)
        return if(forceRefresh && result.isFailure) Result.failure(result.exceptionOrNull()!!) else Result.success(getListSync())
    }
    suspend fun getDetail(semesterCode:String,forceRefresh:Boolean=false):Result<SemesterCalendarDetail> {
        val result=SicauAcademicData.refresh(forceRefresh)
        if(forceRefresh && result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        return getDetailSync(semesterCode)?.let{Result.success(it)} ?: Result.failure(IllegalStateException("该学期尚无已验证校历"))
    }
    fun prefetchDetailIfMissing(semesterCode:String) = Unit
    fun clearMemoryCache() = Unit
}

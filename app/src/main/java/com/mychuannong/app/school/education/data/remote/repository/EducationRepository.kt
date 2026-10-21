package com.mychuannong.app.school.education.data.remote.repository
import com.mychuannong.app.school.SicauAdapter
import com.mychuannong.app.school.education.data.remote.model.*
class EducationRepository private constructor() {
 companion object { val instance by lazy { EducationRepository() } }
 suspend fun getAvailableClassrooms(campus:Campus,week:Int,dayOfWeek:DayOfWeek,section:Int):List<String> = throw IllegalStateException(SicauAdapter.pending())
}

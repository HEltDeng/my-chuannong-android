package com.mychuannong.app.school

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.feature.common.data.local.room.database.CoursesDataBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableImportStorageTest {
    @Test fun replacementIsScopedAndInvalidImportPreservesPersistentCache() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // A separate disposable DB, never the installed user's course_database or identity.
        val name="synthetic-import-${java.util.UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CoursesDataBase::class.java,name).build()
        var db=open()
        try {
            val term="2026-2027-1"
            val course=TimetableImportRules.Draft("合成测试课","合成教师","合成教室","3","5","6","1-16(单周)","").course(term,"synthetic-a")
            val custom=course.copy(courseName="手动课",isCustom=true)
            val other=course.copy(studentId="synthetic-b")
            val older=course.copy(term="2025-2026-2")
            db.courseDao().insertCourses(mutableListOf(custom,other,older))
            SchoolSyncEngine.saveImportedRows(db,term,"synthetic-a",listOf(course,course.copy(weeks=listOf(2,4))))
            val rows=db.courseDao().getCoursesByTerm(term,"synthetic-a")
            assertEquals(2,rows.size)
            assertEquals(listOf(1,2,3,4,5,7,9,11,13,15),rows.single { !it.isCustom }.weeks)
            assertEquals(1,db.courseDao().getCoursesByTerm(term,"synthetic-b").size)
            assertEquals(1,db.courseDao().getCoursesByTerm("2025-2026-2","synthetic-a").size)
            assertTrue(runCatching { SchoolSyncEngine.saveImportedRows(db,term,"synthetic-a",listOf(course.copy(weeks=emptyList()))) }.isFailure)
            assertEquals(rows,db.courseDao().getCoursesByTerm(term,"synthetic-a"))
            db.close();db=open()
            assertEquals(rows,db.courseDao().getCoursesByTerm(term,"synthetic-a"))
        } finally { db.close();context.deleteDatabase(name) }
    }
}

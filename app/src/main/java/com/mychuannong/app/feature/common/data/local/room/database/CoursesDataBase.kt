package com.mychuannong.app.feature.common.data.local.room.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mychuannong.app.feature.common.data.local.entity.TimeTableMySubject
import com.mychuannong.app.feature.common.data.local.room.converter.WeeksTypeConverter
import com.mychuannong.app.feature.common.data.local.room.dao.CourseDao

@Database(entities = [TimeTableMySubject::class], version = 12, exportSchema = true)
@TypeConverters(WeeksTypeConverter::class)
abstract class CoursesDataBase : RoomDatabase() {
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var INSTANCE: CoursesDataBase? = null
        fun getDatabase(context: Context) = INSTANCE ?: synchronized(this) {
            com.mychuannong.app.school.EncryptedCourseStorage.migrate(context.applicationContext)
            val instance = Room.databaseBuilder(
                context.applicationContext,
                CoursesDataBase::class.java,
                "course_database_encrypted"
            )
                .openHelperFactory(com.mychuannong.app.school.EncryptedCourseStorage.factory())
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                .build()
            INSTANCE = instance
            instance
        }

        internal val MIGRATION_11_12 = object : Migration(11,12) {
            override fun migrate(db:SupportSQLiteDatabase) {
                // All pre-existing rows, including reviewed imports, remain protected local rows.
                db.execSQL("ALTER TABLE courses ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `courses_new` (
                        `courseName` TEXT NOT NULL,
                        `classroom` TEXT,
                        `teacher` TEXT NOT NULL,
                        `weeks` TEXT,
                        `start` INTEGER NOT NULL,
                        `step` INTEGER NOT NULL,
                        `weekday` INTEGER NOT NULL,
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `term` TEXT NOT NULL,
                        `studentId` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `courses_new` (
                        `courseName`, `classroom`, `teacher`, `weeks`, `start`, `step`,
                        `weekday`, `id`, `isCustom`, `term`, `studentId`
                    )
                    SELECT `courseName`, `classroom`, `teacher`, `weeks`, `start`, `step`,
                        `weekday`, `id`, `isCustom`, `term`, `studentId`
                    FROM `courses`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `courses`")
                db.execSQL("ALTER TABLE `courses_new` RENAME TO `courses`")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_courses_courseName_classroom_teacher_start_step_weekday_term_studentId`
                    ON `courses` (`courseName`, `classroom`, `teacher`, `start`, `step`, `weekday`, `term`, `studentId`)
                    """.trimIndent()
                )
            }
        }
    }
}

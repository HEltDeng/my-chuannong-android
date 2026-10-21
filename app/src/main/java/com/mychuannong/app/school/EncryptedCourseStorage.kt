package com.mychuannong.app.school

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Copy/verify first; the old database is removed only after the encrypted copy is durable. */
internal object EncryptedCourseStorage {
    fun factory() : SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        net.zetetic.database.Logger.setTarget(net.zetetic.database.NoopTarget())
        return SupportOpenHelperFactory(LearningCacheKeys.secret("courses").toByteArray())
    }
    @Synchronized fun migrate(context:Context, oldName:String="course_database", newName:String="course_database_encrypted") {
        System.loadLibrary("sqlcipher")
        net.zetetic.database.Logger.setTarget(net.zetetic.database.NoopTarget())
        val old=context.getDatabasePath(oldName)
        if(!old.exists())return
        val target=context.getDatabasePath(newName)
        val pass=LearningCacheKeys.secret("courses")
        if(!target.exists()) {
            val pending=context.getDatabasePath("$newName.pending")
            context.deleteDatabase("$newName.pending")
            SQLiteDatabase.openDatabase(old.path,"",null,SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,null).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)",null).use{it.moveToFirst()}
                db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?",arrayOf(pending.path,pass))
                db.rawQuery("SELECT sqlcipher_export('encrypted')",null).use{it.moveToFirst()}
                db.execSQL("PRAGMA encrypted.user_version = ${db.version}")
                db.execSQL("DETACH DATABASE encrypted")
            }
            SQLiteDatabase.openDatabase(pending.path,pass,null,SQLiteDatabase.OPEN_READONLY,null).use { db ->
                db.rawQuery("PRAGMA integrity_check",null).use { check(it.moveToFirst() && it.getString(0)=="ok") }
            }
            check(pending.renameTo(target))
        }
        SQLiteDatabase.openDatabase(target.path,pass,null,SQLiteDatabase.OPEN_READONLY,null).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master",null).use { check(it.moveToFirst()) }
        }
        check(context.deleteDatabase(oldName))
    }
}

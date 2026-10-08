package com.egrmeister.roomresetmini.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        RoomTemplateEntity::class,
        TemplateTaskEntity::class,
        ResetSessionEntity::class,
        SessionTaskEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val VERSION = 1
        private const val NAME = "roomreset.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No fallbackToDestructiveMigration(): user data must survive app updates.
                .build()
    }
}

/**
 * Schema history lives in app/schemas (exported by Room, committed to Git).
 * Every future version bump adds an explicit Migration here, for example:
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE reset_sessions ADD COLUMN note TEXT")
 *       }
 *   }
 *
 * Version 1 is the first release, so there is nothing to migrate yet.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}

package com.karelisio.exit.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SmokeEntity::class, CravingEntity::class, PlanEventEntity::class, ConfigEntity::class, CheckInEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class ExitDatabase : RoomDatabase() {
    abstract fun smokes(): SmokeDao
    abstract fun cravings(): CravingDao
    abstract fun planEvents(): PlanEventDao
    abstract fun config(): ConfigDao
    abstract fun checkIns(): CheckInDao

    companion object {
        fun create(context: Context): ExitDatabase =
            Room.databaseBuilder(context, ExitDatabase::class.java, "exit.db").build()
    }
}

package com.karelisio.exit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SmokeEntity::class, CravingEntity::class, PlanEventEntity::class, ConfigEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ExitDatabase : RoomDatabase() {
    abstract fun smokes(): SmokeDao
    abstract fun cravings(): CravingDao
    abstract fun planEvents(): PlanEventDao
    abstract fun config(): ConfigDao

    companion object {
        fun create(context: Context): ExitDatabase =
            Room.databaseBuilder(context, ExitDatabase::class.java, "exit.db").build()
    }
}

package com.karelisio.exit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SmokeDao {
    @Query("SELECT * FROM smokes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SmokeEntity>>

    @Query("SELECT * FROM smokes ORDER BY timestamp DESC")
    suspend fun all(): List<SmokeEntity>

    @Query("SELECT * FROM smokes ORDER BY timestamp DESC LIMIT 1")
    suspend fun last(): SmokeEntity?

    @Query("SELECT * FROM smokes WHERE timestamp >= :from ORDER BY timestamp DESC")
    suspend fun since(from: Long): List<SmokeEntity>

    @Query("SELECT COUNT(*) FROM smokes WHERE timestamp >= :from AND timestamp < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Insert
    suspend fun insert(smoke: SmokeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(smokes: List<SmokeEntity>)

    @Update
    suspend fun update(smoke: SmokeEntity)

    @Delete
    suspend fun delete(smoke: SmokeEntity)

    @Query("DELETE FROM smokes")
    suspend fun clear()
}

@Dao
interface CravingDao {
    @Query("SELECT * FROM cravings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CravingEntity>>

    @Query("SELECT * FROM cravings ORDER BY timestamp DESC")
    suspend fun all(): List<CravingEntity>

    @Insert
    suspend fun insert(craving: CravingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cravings: List<CravingEntity>)

    @Query("SELECT * FROM cravings ORDER BY timestamp DESC LIMIT 1")
    suspend fun last(): CravingEntity?

    @Update
    suspend fun update(craving: CravingEntity)

    @Delete
    suspend fun delete(craving: CravingEntity)

    @Query("DELETE FROM cravings")
    suspend fun clear()
}

@Dao
interface PlanEventDao {
    @Query("SELECT * FROM plan_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PlanEventEntity>>

    @Query("SELECT * FROM plan_events ORDER BY timestamp DESC")
    suspend fun all(): List<PlanEventEntity>

    @Insert
    suspend fun insert(event: PlanEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PlanEventEntity>)

    @Query("DELETE FROM plan_events")
    suspend fun clear()
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE id = 0")
    fun observe(): Flow<ConfigEntity?>

    @Query("SELECT * FROM config WHERE id = 0")
    suspend fun get(): ConfigEntity?

    @Upsert
    suspend fun upsert(config: ConfigEntity)
}

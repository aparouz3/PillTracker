package com.pilltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history ORDER BY dateKey ASC")
    suspend fun getAllOnce(): List<PriceHistory>

    @Query("SELECT * FROM price_history WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(dateKey: String): PriceHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PriceHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PriceHistory>)

    @Query("DELETE FROM price_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM price_history ORDER BY dateKey DESC LIMIT 30")
    suspend fun getRecent(): List<PriceHistory>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_entries ORDER BY dayKey, time ASC")
    suspend fun getAllOnce(): List<ScheduleEntry>

    @Query("SELECT * FROM schedule_entries WHERE dayKey = :dayKey ORDER BY time ASC")
    suspend fun getByDay(dayKey: String): List<ScheduleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScheduleEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ScheduleEntry>)

    @Query("DELETE FROM schedule_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM schedule_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

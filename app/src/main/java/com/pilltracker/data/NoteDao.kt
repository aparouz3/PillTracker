package com.pilltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoteDao {
    @Query("SELECT * FROM daily_notes WHERE year = :year AND month = :month AND day = :day LIMIT 1")
    suspend fun getNoteForDate(year: Int, month: Int, day: Int): DailyNote?

    @Query("SELECT * FROM daily_notes ORDER BY timestamp DESC")
    suspend fun getAllNotesOnce(): List<DailyNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: DailyNote): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DailyNote>)

    @Query("DELETE FROM daily_notes")
    suspend fun deleteAll()

    @Query("DELETE FROM daily_notes WHERE year = :year AND month = :month AND day = :day")
    suspend fun deleteForDate(year: Int, month: Int, day: Int)
}
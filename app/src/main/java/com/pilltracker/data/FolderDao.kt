package com.pilltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE year = :year AND month = :month AND day = :day ORDER BY name COLLATE NOCASE")
    fun getFoldersForDate(year: Int, month: Int, day: Int): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE year = :year AND month = :month AND day = :day ORDER BY name COLLATE NOCASE")
    suspend fun getFoldersForDateOnce(year: Int, month: Int, day: Int): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder): Long

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<Folder>)

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Folder?

    @Query("SELECT * FROM folders ORDER BY year, month, day, name COLLATE NOCASE")
    suspend fun getAllFoldersOnce(): List<Folder>
}
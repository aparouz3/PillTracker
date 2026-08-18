package com.pilltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "daily_notes")
data class DailyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "year") val year: Int,     // Persian year
    @ColumnInfo(name = "month") val month: Int,   // Persian month (1-12)
    @ColumnInfo(name = "day") val day: Int,       // Persian day
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
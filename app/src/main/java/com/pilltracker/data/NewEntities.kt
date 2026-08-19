package com.pilltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_history")
data class PriceHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,          // Persian date "2585-5-28"
    val price: Long,              // price in Toman
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedule_entries")
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,           // saturday..friday
    val time: String,
    val subject: String,
    val teacher: String
)

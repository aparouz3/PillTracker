package com.pilltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "year") val year: Int,     // Persian year of the day
    @ColumnInfo(name = "month") val month: Int,   // Persian month
    @ColumnInfo(name = "day") val day: Int        // Persian day
)
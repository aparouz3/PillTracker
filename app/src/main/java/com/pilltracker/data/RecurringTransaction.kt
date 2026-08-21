package com.pilltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "type") val type: TransactionType,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "interval_days") val intervalDays: Int = 30,
    @ColumnInfo(name = "anchor_year") val anchorYear: Int,
    @ColumnInfo(name = "anchor_month") val anchorMonth: Int,
    @ColumnInfo(name = "anchor_day") val anchorDay: Int,
    @ColumnInfo(name = "active") val active: Boolean = true,
    @ColumnInfo(name = "next_year") val nextYear: Int,
    @ColumnInfo(name = "next_month") val nextMonth: Int,
    @ColumnInfo(name = "next_day") val nextDay: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

package com.pilltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "type") val type: TransactionType,
    @ColumnInfo(name = "year") val year: Int,     // Persian year
    @ColumnInfo(name = "month") val month: Int,   // Persian month (1-12)
    @ColumnInfo(name = "day") val day: Int,       // Persian day
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

enum class TransactionType {
    EXPENSE, INCOME
}
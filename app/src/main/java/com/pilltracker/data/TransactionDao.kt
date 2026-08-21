package com.pilltracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE year = :year AND month = :month AND day = :day ORDER BY timestamp DESC")
    fun getTransactionsForDate(year: Int, month: Int, day: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE year = :year AND month = :month ORDER BY day DESC, timestamp DESC")
    fun getTransactionsForMonth(year: Int, month: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsOnce(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND year = :year AND month = :month AND day = :day")
    fun getDailyTotal(type: TransactionType, year: Int, month: Int, day: Int): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND year = :year AND month = :month AND day = :day")
    suspend fun getDailyTotalOnce(type: TransactionType, year: Int, month: Int, day: Int): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND year = :year AND month = :month")
    fun getMonthlyTotal(type: TransactionType, year: Int, month: Int): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND year = :year")
    fun getYearlyTotal(type: TransactionType, year: Int): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND timestamp >= :startTs AND timestamp < :endTs")
    fun getTotalBetween(type: TransactionType, startTs: Long, endTs: Long): Flow<Long>

    // Sum by Persian date range (inclusive): (sy,sm,sd) .. (ey,em,ed)
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND (
            (year > :sy) OR
            (year = :sy AND month > :sm) OR
            (year = :sy AND month = :sm AND day >= :sd)
        ) AND (
            (year < :ey) OR
            (year = :ey AND month < :em) OR
            (year = :ey AND month = :em AND day <= :ed)
        )
    """)
    fun getTotalInPersianRange(
        type: TransactionType,
        sy: Int, sm: Int, sd: Int,
        ey: Int, em: Int, ed: Int
    ): Flow<Long>

    // All expenses in a Persian date range, sorted by amount descending (biggest first)
    @Query("""
        SELECT * FROM transactions
        WHERE type = 'EXPENSE' AND (
            (year > :sy) OR
            (year = :sy AND month > :sm) OR
            (year = :sy AND month = :sm AND day >= :sd)
        ) AND (
            (year < :ey) OR
            (year = :ey AND month < :em) OR
            (year = :ey AND month = :em AND day <= :ed)
        )
        ORDER BY amount DESC, timestamp DESC
    """)
    fun getExpensesInPersianRange(
        sy: Int, sm: Int, sd: Int,
        ey: Int, em: Int, ed: Int
    ): Flow<List<Transaction>>

    // All transactions (expense or income) in a Persian date range, sorted by amount descending
    @Query("""
        SELECT * FROM transactions
        WHERE type = :type AND (
            (year > :sy) OR
            (year = :sy AND month > :sm) OR
            (year = :sy AND month = :sm AND day >= :sd)
        ) AND (
            (year < :ey) OR
            (year = :ey AND month < :em) OR
            (year = :ey AND month = :em AND day <= :ed)
        )
        ORDER BY amount DESC, timestamp DESC
    """)
    fun getTransactionsInPersianRange(
        type: TransactionType,
        sy: Int, sm: Int, sd: Int,
        ey: Int, em: Int, ed: Int
    ): Flow<List<Transaction>>

    @Update
    suspend fun update(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE title LIKE :q OR category_id IN (SELECT id FROM categories WHERE name LIKE :q) ORDER BY timestamp DESC LIMIT 100")
    suspend fun searchOnce(q: String): List<Transaction>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Category (folder) queries ----

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE category_id = :categoryId AND type = 'EXPENSE'")
    fun getExpenseTotalByCategory(categoryId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE category_id = :categoryId AND type = 'INCOME'")
    fun getIncomeTotalByCategory(categoryId: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    // ---- Daily folder queries ----

    @Query("SELECT * FROM transactions WHERE year = :year AND month = :month AND day = :day AND folder_id = :folderId ORDER BY timestamp DESC")
    fun getTransactionsForFolder(year: Int, month: Int, day: Int, folderId: Long): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE folder_id = :folderId AND type = 'EXPENSE'")
    fun getExpenseTotalByFolder(folderId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE folder_id = :folderId AND type = 'INCOME'")
    fun getIncomeTotalByFolder(folderId: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE folder_id = :folderId")
    suspend fun countByFolder(folderId: Long): Int
}
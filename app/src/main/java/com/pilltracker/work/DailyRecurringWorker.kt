package com.pilltracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pilltracker.PillTrackerApp
import com.pilltracker.data.Transaction
import com.pilltracker.util.PersianCalendar
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily worker that checks all active recurring transactions and generates
 * transactions for any missed due dates, then updates nextDate.
 */
class DailyRecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyRecurringWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_recurring",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as PillTrackerApp
        val db = app.database
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val todayInt = today.year * 10000 + today.month * 100 + today.day

        val all = db.recurringDao().getAllActive()
        for (r in all) {
            if (!r.active) continue
            val nextInt = r.nextYear * 10000 + r.nextMonth * 100 + r.nextDay

            // If next date is today or earlier, generate the transaction(s)
            if (nextInt <= todayInt) {
                var currentNextInt = nextInt
                var currentYear = r.nextYear
                var currentMonth = r.nextMonth
                var currentDay = r.nextDay

                // Generate transactions for all missed dates up to today (cap at 90 days)
                var safety = 0
                while (currentNextInt <= todayInt && safety < 90) {
                    // Insert the transaction for this date
                    db.transactionDao().insert(
                        Transaction(
                            title = r.title,
                            amount = r.amount,
                            type = r.type,
                            year = currentYear,
                            month = currentMonth,
                            day = currentDay,
                            categoryId = r.categoryId
                        )
                    )
                    // Advance by interval
                    val next = PersianCalendar.addDays(currentYear, currentMonth, currentDay, r.intervalDays)
                    currentYear = next.year
                    currentMonth = next.month
                    currentDay = next.day
                    currentNextInt = currentYear * 10000 + currentMonth * 100 + currentDay
                    safety++
                }

                // Update next date in the recurring record
                db.recurringDao().update(r.copy(
                    nextYear = currentYear,
                    nextMonth = currentMonth,
                    nextDay = currentDay
                ))
            }
        }
        return Result.success()
    }
}
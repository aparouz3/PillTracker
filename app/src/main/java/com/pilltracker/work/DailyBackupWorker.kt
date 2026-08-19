package com.pilltracker.work

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pilltracker.PillTrackerApp
import com.pilltracker.util.BackupUtils
import com.pilltracker.util.PersianCalendar
import java.io.File
import java.util.Calendar

/**
 * Daily automatic backup worker.
 * Saves a JSON backup of all transactions + notes to the public Downloads folder
 * (survives app uninstall). Keeps the latest 30 backups, deletes older ones.
 */
class DailyBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = (applicationContext as PillTrackerApp).database
            val transactions = db.transactionDao().getAllTransactionsOnce()
            val notes = db.noteDao().getAllNotesOnce()
            val categories = db.categoryDao().getAllCategoriesOnce()
            val folders = db.folderDao().getAllFoldersOnce()
            val foods = db.foodDao().getAllFoodsOnce()
            val schedule = db.scheduleDao().getAllOnce()
            val priceHistory = db.priceHistoryDao().getAllOnce()

            val now = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
            )
            val filename = "pilltracker_backup_${today.year}_${today.month}_${today.day}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(filename, transactions, notes, categories, folders, foods, schedule, priceHistory)
            } else {
                saveViaLegacy(filename, transactions, notes, categories, folders, foods, schedule, priceHistory)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun saveViaMediaStore(
        filename: String,
        transactions: List<com.pilltracker.data.Transaction>,
        notes: List<com.pilltracker.data.DailyNote>,
        categories: List<com.pilltracker.data.Category>,
        folders: List<com.pilltracker.data.Folder>,
        foods: List<com.pilltracker.data.Food> = emptyList(),
        schedule: List<com.pilltracker.data.ScheduleEntry> = emptyList(),
        priceHistory: List<com.pilltracker.data.PriceHistory> = emptyList()
    ) {
        val resolver = applicationContext.contentResolver
        val folder = Environment.DIRECTORY_DOWNLOADS + "/PillTrackerBackups"

        // NOTE: no backups are ever deleted — all daily backups are kept forever,
        // so no expense/income data is ever lost.

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            BackupUtils.writeToStream(transactions, notes, categories, folders, foods, schedule, priceHistory, out)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveViaLegacy(
        filename: String,
        transactions: List<com.pilltracker.data.Transaction>,
        notes: List<com.pilltracker.data.DailyNote>,
        categories: List<com.pilltracker.data.Category>,
        folders: List<com.pilltracker.data.Folder>,
        foods: List<com.pilltracker.data.Food> = emptyList(),
        schedule: List<com.pilltracker.data.ScheduleEntry> = emptyList(),
        priceHistory: List<com.pilltracker.data.PriceHistory> = emptyList()
    ) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PillTrackerBackups"
        )
        if (!dir.exists()) dir.mkdirs()

        // NOTE: no backups are ever deleted — all daily backups are kept forever.

        val file = File(dir, filename)
        file.outputStream().use { out ->
            BackupUtils.writeToStream(transactions, notes, categories, folders, foods, schedule, priceHistory, out)
        }
    }
}
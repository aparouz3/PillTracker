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

            val now = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
            )
            val filename = "pilltracker_backup_${today.year}_${today.month}_${today.day}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(filename, transactions, notes)
            } else {
                saveViaLegacy(filename, transactions, notes)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun saveViaMediaStore(
        filename: String,
        transactions: List<com.pilltracker.data.Transaction>,
        notes: List<com.pilltracker.data.DailyNote>
    ) {
        val resolver = applicationContext.contentResolver
        val folder = Environment.DIRECTORY_DOWNLOADS + "/PillTrackerBackups"

        // Delete old backups older than 30 days (keep the latest 30)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("$folder/"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                count++
                if (count > 30) {
                    val id = cursor.getLong(0)
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns._ID} = ?",
                        arrayOf(id.toString())
                    )
                }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            BackupUtils.writeToStream(transactions, notes, out)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveViaLegacy(
        filename: String,
        transactions: List<com.pilltracker.data.Transaction>,
        notes: List<com.pilltracker.data.DailyNote>
    ) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PillTrackerBackups"
        )
        if (!dir.exists()) dir.mkdirs()

        // Keep the latest 30
        val files = dir.listFiles { f -> f.name.startsWith("pilltracker_backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        for (f in files.drop(30)) f.delete()

        val file = File(dir, filename)
        file.outputStream().use { out ->
            BackupUtils.writeToStream(transactions, notes, out)
        }
    }
}
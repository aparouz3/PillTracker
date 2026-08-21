package com.pilltracker

import android.app.Application
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pilltracker.data.PillTrackerDatabase
import com.pilltracker.work.CrownsNotifier
import com.pilltracker.work.DailyBackupWorker
import com.pilltracker.work.DailyCrownsWorker
import com.pilltracker.work.DailyRecurringWorker
import com.pilltracker.work.PriceUpdateScheduler
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

class PillTrackerApp : Application() {

    val database: PillTrackerDatabase by lazy {
        PillTrackerDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        scheduleDailyBackup()
        CrownsNotifier.ensureChannel(this)
        DailyCrownsWorker.schedule(this)
        DailyRecurringWorker.schedule(this)
        PriceUpdateScheduler.scheduleDaily(this)
    }

    /**
     * Schedules the automatic daily backup (runs once a day while the app is installed).
     */
    private fun scheduleDailyBackup() {
        val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Saves any uncaught exception to a file so the next launch can show it.
     */
    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("Time: ${System.currentTimeMillis()}")
                pw.println("Thread: ${thread.name}")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
                throwable.printStackTrace(pw)
                pw.flush()

                val dir = File(getExternalFilesDir(null) ?: filesDir, "crashes")
                dir.mkdirs()
                val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
                FileWriter(file).use { it.write(sw.toString()) }

                // Also keep a copy at a predictable location
                val copy = File(dir, "last_crash.txt")
                FileWriter(copy).use { it.write(sw.toString()) }
            } catch (e: Exception) {
                // ignore
            }
            // Let the default handler finish (show "app stopped" dialog)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
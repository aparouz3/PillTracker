package com.pilltracker

import android.app.Application
import android.os.Build
import android.os.Environment
import com.pilltracker.data.PillTrackerDatabase
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter

class PillTrackerApp : Application() {

    val database: PillTrackerDatabase by lazy {
        PillTrackerDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
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
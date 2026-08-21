package com.pilltracker.work

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pilltracker.PillTrackerApp
import com.pilltracker.util.PersianCalendar
import com.pilltracker.data.ScheduleEntry
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily worker that shows crowns notifications (food, price, schedule) + tomorrow-note check.
 * Replaces the old AlarmManager-based CrownsNotificationReceiver.
 */
class DailyCrownsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "crowns_daily"
        private const val KEY_LAST_NOTE_NOTIF = "last_note_notif_date"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCrownsWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_crowns",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as PillTrackerApp
        val db = app.database
        val prefs = applicationContext.getSharedPreferences("crowns_prefs", Context.MODE_PRIVATE)
        CrownsNotifier.ensureChannel(applicationContext)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var id = 1000

        // 1) Food of the day
        if (prefs.getBoolean(CrownsNotifier.KEY_FOOD, true)) {
            val food = db.foodDao().getRandomFoodOnce()
            if (food != null) {
                postNotification(nm, id++, "🍽 غذای امروز", "پیشنهاد امروز: ${food.name}")
            }
        }

        // 2) Tara price
        if (prefs.getBoolean(CrownsNotifier.KEY_PRICE, true)) {
            val price = fetchPrice()
            if (price != null) {
                postNotification(nm, id++, "🪙 قیمت تارا", "قیمت امروز تارا: $price تومان")
            }
        }

        // 3) Today's classes
        if (prefs.getBoolean(CrownsNotifier.KEY_SCHEDULE, true)) {
            val cal = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            val dayKey = arrayOf(
                "saturday", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday"
            )[PersianCalendar.getPersianWeekDayIndex(today.year, today.month, today.day)]
            val classes = db.scheduleDao().getByDay(dayKey)
            if (classes.isNotEmpty()) {
                val summary = classes.joinToString(" • ") { "${it.time} ${it.subject}" }
                postNotification(nm, id++, "📚 برنامه کلاسی امروز", summary)
            }
        }

        // 4) Tomorrow note notification (only once per day)
        val todayCal = Calendar.getInstance()
        val todayPersian = PersianCalendar.gregorianToPersian(
            todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH)
        )
        val todayKey = "${todayPersian.year}-${todayPersian.month}-${todayPersian.day}"
        val lastShown = prefs.getString(KEY_LAST_NOTE_NOTIF, "")
        if (lastShown != todayKey) {
            val tomorrow = PersianCalendar.addDays(todayPersian.year, todayPersian.month, todayPersian.day, 1)
            val note = db.noteDao().getNoteForDate(tomorrow.year, tomorrow.month, tomorrow.day)
            if (note != null && note.text.isNotBlank()) {
                prefs.edit().putString(KEY_LAST_NOTE_NOTIF, todayKey).apply()
                val intent = Intent(applicationContext, com.pilltracker.ui.MainActivity::class.java)
                val pi = android.app.PendingIntent.getActivity(
                    applicationContext, 2100, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("📝 یادداشت فردا")
                    .setContentText(note.text.take(60))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm.notify(2100, notification)
            }
        }

        // Schedule the recurring transaction worker
        val recurringRequest = PeriodicWorkRequestBuilder<DailyRecurringWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "daily_recurring",
            ExistingPeriodicWorkPolicy.KEEP,
            recurringRequest
        )

        return Result.success()
    }

    private fun fetchPrice(): String? {
        return try {
            val url = java.net.URL("https://www.iranjib.ir/showgroup/45/%D9%82%DB%8C%D9%85%D8%AA-%D8%AE%D9%88%D8%AF%D8%B1%D9%88-%D8%AA%D9%88%D9%84%DB%8C%D8%AF-%D8%AF%D8%A7%D8%AE%D9%84/")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val regex = Regex(">تارا[^<]*</a></td>\\s*<td[^>]*>\\s*<span[^>]*class=\"lastprice\"[^>]*>([۰-۹0-9,]+)</span>")
            val match = regex.find(html)
            val raw = match?.groupValues?.get(1) ?: run {
                val alt = Regex(">تارا[^<]*</a></td>.*?class=\"lastprice\">([۰-۹0-9,]+)<", RegexOption.DOT_MATCHES_ALL)
                alt.find(html)?.groupValues?.get(1)
            }
            raw?.let { toAscii(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun toAscii(s: String): String = s
        .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
        .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')

    private fun postNotification(nm: NotificationManager, id: Int, title: String, text: String) {
        val intent = Intent(applicationContext, com.pilltracker.ui.CrownsActivity::class.java)
        val pi = android.app.PendingIntent.getActivity(
            applicationContext, id, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }
}
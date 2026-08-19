package com.pilltracker.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Schedules and shows daily 9AM crown notifications (food, price, schedule).
 * Each crown can be toggled on/off from the Crowns menu via SharedPreferences.
 */
object CrownsNotifier {

    const val PREFS = "crowns_prefs"
    const val KEY_FOOD = "notif_food"
    const val KEY_PRICE = "notif_price"
    const val KEY_SCHEDULE = "notif_schedule"
    const val KEY_LAST_FOOD_DATE = "last_food_date"
    const val KEY_LAST_FOOD_NAME = "last_food_name"
    const val KEY_TODAY_FOOD = "today_food" // food locked for today
    const val KEY_TODAY_FOOD_DATE = "today_food_date"

    const val CHANNEL_ID = "crowns_daily"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFoodNotifEnabled(c: Context) = prefs(c).getBoolean(KEY_FOOD, true)
    fun isPriceNotifEnabled(c: Context) = prefs(c).getBoolean(KEY_PRICE, true)
    fun isScheduleNotifEnabled(c: Context) = prefs(c).getBoolean(KEY_SCHEDULE, true)

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CrownsNotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 900, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "کرون‌های روزانه",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "یادآوری روزانه غذای پیشنهادی، قیمت تارا و برنامه کلاسی"
        }
        nm.createNotificationChannel(channel)
    }
}

class CrownsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PillTrackerApp
        val db = app.database
        CrownsNotifier.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var id = 1000

        // 1) Food of the day
        if (CrownsNotifier.isFoodNotifEnabled(context)) {
            CoroutineScope(Dispatchers.IO).launch {
                val food = db.foodDao().getRandomFoodOnce()
                if (food != null) {
                    post(
                        context, nm, id++,
                        "🍽 غذای امروز",
                        "پیشنهاد امروز: ${food.name}",
                        CrownsNotifier.CHANNEL_ID
                    )
                }
            }
        }

        // 2) Tara price
        if (CrownsNotifier.isPriceNotifEnabled(context)) {
            CoroutineScope(Dispatchers.IO).launch {
                val price = fetchPrice(context)
                if (price != null) {
                    post(
                        context, nm, id++,
                        "🪙 قیمت تارا",
                        "قیمت امروز تارا: $price تومان",
                        CrownsNotifier.CHANNEL_ID
                    )
                }
            }
        }

        // 3) Today's classes
        if (CrownsNotifier.isScheduleNotifEnabled(context)) {
            CoroutineScope(Dispatchers.IO).launch {
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
                    post(
                        context, nm, id++,
                        "📚 برنامه کلاسی امروز",
                        summary,
                        CrownsNotifier.CHANNEL_ID
                    )
                }
            }
        }
    }

    private fun fetchPrice(context: Context): String? {
        return try {
            val url = "https://www.iranjib.ir/showgroup/45/%D9%82%DB%8C%D9%85%D8%AA-%D8%AE%D9%88%D8%AF%D8%B1%D9%88-%D8%AA%D9%88%D9%84%DB%8C%D8%AF-%D8%AF%D8%A7%D8%AE%D9%84/"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val regex = Regex(
                ">تارا[^<]*</a></td>\\s*<td[^>]*>\\s*<span[^>]*class=\"lastprice\"[^>]*>([۰-۹0-9,]+)</span>"
            )
            val match = regex.find(html)
            val raw = match?.groupValues?.get(1) ?: run {
                val alt = Regex(">تارا[^<]*</a></td>.*?class=\"lastprice\">([۰-۹0-9,]+)<", RegexOption.DOT_MATCHES_ALL)
                alt.find(html)?.groupValues?.get(1)
            }
            raw?.let { toAsciiDigits(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun toAsciiDigits(s: String): String = s
        .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
        .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')

    private fun post(context: Context, nm: NotificationManager, id: Int, title: String, text: String, channel: String) {
        val intent = Intent(context, com.pilltracker.ui.CrownsActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
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

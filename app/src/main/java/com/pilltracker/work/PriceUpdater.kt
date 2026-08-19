package com.pilltracker.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pilltracker.PillTrackerApp
import com.pilltracker.data.PriceHistory
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fetches the Tara price and stores it in price_history.
 * Scheduled to run at 9:00, 12:00 and 18:00 every day.
 */
object PriceUpdateScheduler {

    const val ACTION = "com.pilltracker.UPDATE_TARA_PRICE"

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PriceUpdateReceiver::class.java).setAction(ACTION)
        val hours = intArrayOf(9, 12, 18)
        for ((index, hour) in hours.withIndex()) {
            val pending = PendingIntent.getBroadcast(
                context, 700 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
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
    }
}

class PriceUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PriceUpdateScheduler.ACTION) return
        val app = context.applicationContext as PillTrackerApp
        val db = app.database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val price = fetchPrice()
                if (price != null) {
                    val cal = Calendar.getInstance()
                    val today = PersianCalendar.gregorianToPersian(
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
                    )
                    val dateKey = "${today.year}-${today.month}-${today.day}"
                    db.priceHistoryDao().insert(
                        PriceHistory(dateKey = dateKey, price = price)
                    )
                }
            } catch (e: Exception) {
                // silent
            }
        }
    }

    private fun fetchPrice(): Long? {
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
            raw?.let { toAsciiDigits(it).replace(",", "").toLongOrNull() }
        } catch (e: Exception) {
            null
        }
    }

    private fun toAsciiDigits(s: String): String = s
        .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
        .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')
}

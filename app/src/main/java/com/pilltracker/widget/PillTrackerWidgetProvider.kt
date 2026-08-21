package com.pilltracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PillTrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PillTrackerWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, com.pilltracker.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)
            manager.updateAppWidget(widgetId, views)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as PillTrackerApp
                    val db = app.database
                    val cal = Calendar.getInstance()
                    val today = PersianCalendar.gregorianToPersian(
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
                    )
                    val income = db.transactionDao().getDailyTotalOnce(TransactionType.INCOME, today.year, today.month, today.day)
                    val expense = db.transactionDao().getDailyTotalOnce(TransactionType.EXPENSE, today.year, today.month, today.day)
                    val balance = income - expense

                    val updated = RemoteViews(context.packageName, R.layout.widget_layout)
                    updated.setTextViewText(R.id.widgetIncome, "درآمد: ${FormatUtils.formatAmount(income)}")
                    updated.setTextViewText(R.id.widgetExpense, "هزینه: ${FormatUtils.formatAmount(expense)}")
                    updated.setTextViewText(R.id.widgetBalance, "مانده: ${FormatUtils.formatAmount(balance)}")
                    updated.setTextViewText(R.id.widgetDate, "${today.day} ${PersianCalendar.getPersianMonthName(today.month)}")
                    updated.setOnClickPendingIntent(R.id.widgetRoot, pi)
                    manager.updateAppWidget(widgetId, updated)
                } catch (e: Exception) {
                    // widget update failure — keep last state
                }
            }
        }
    }
}
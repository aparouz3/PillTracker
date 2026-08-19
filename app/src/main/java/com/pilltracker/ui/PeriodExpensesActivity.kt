package com.pilltracker.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Shows top expenses/incomes for a period (week/month/year) with prev/next navigation.
 * Opened from the "بیشترین تراکنش‌ها" menu page via EXTRA_PERIOD.
 */
class PeriodExpensesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERIOD = "period" // "week" | "month" | "year"
    }

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase

    private var period = "week"
    private var offset = 0 // 0 = current, -1 = previous, 1 = next

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_period_expenses)

        db = (application as PillTrackerApp).database
        period = intent.getStringExtra(EXTRA_PERIOD) ?: "week"

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.prevPeriodBtn).setOnClickListener { offset--; load() }
        findViewById<ImageButton>(R.id.nextPeriodBtn).setOnClickListener { offset++; load() }

        load()
    }

    private fun load() {
        when (period) {
            "month" -> loadMonth()
            "year" -> loadYear()
            else -> loadWeek()
        }
    }

    private fun loadWeek() {
        val weekStartTs = PersianCalendar.getWeekStartTimestamp() + offset * 7L * 24 * 60 * 60 * 1000L
        val weekStartCal = Calendar.getInstance().apply { timeInMillis = weekStartTs }
        val saturday = PersianCalendar.gregorianToPersian(
            weekStartCal.get(Calendar.YEAR), weekStartCal.get(Calendar.MONTH) + 1, weekStartCal.get(Calendar.DAY_OF_MONTH)
        )
        val friday = PersianCalendar.addDays(saturday.year, saturday.month, saturday.day, 6)

        findViewById<TextView>(R.id.periodTitle).text = if (offset == 0) {
            "این هفته (شنبه ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)})"
        } else {
            "هفته ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)} $saturday.year"
        }

        loadSection(
            R.id.expenseList, R.id.expenseEmpty, TransactionType.EXPENSE,
            saturday.year, saturday.month, saturday.day, friday.year, friday.month, friday.day
        )
        loadSection(
            R.id.incomeList, R.id.incomeEmpty, TransactionType.INCOME,
            saturday.year, saturday.month, saturday.day, friday.year, friday.month, friday.day
        )
    }

    private fun loadMonth() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        var month = today.month + offset
        var year = today.year
        while (month < 1) { month += 12; year-- }
        while (month > 12) { month -= 12; year++ }

        findViewById<TextView>(R.id.periodTitle).text = if (offset == 0) {
            "این ماه (${PersianCalendar.getPersianMonthName(month)} $year)"
        } else {
            "ماه ${PersianCalendar.getPersianMonthName(month)} $year"
        }

        val lastDay = PersianCalendar.getPersianMonthDays(year, month)
        loadSection(R.id.expenseList, R.id.expenseEmpty, TransactionType.EXPENSE, year, month, 1, year, month, lastDay)
        loadSection(R.id.incomeList, R.id.incomeEmpty, TransactionType.INCOME, year, month, 1, year, month, lastDay)
    }

    private fun loadYear() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        val year = today.year + offset

        findViewById<TextView>(R.id.periodTitle).text = if (offset == 0) {
            "امسال ($year)"
        } else {
            "سال $year"
        }

        loadSection(R.id.expenseList, R.id.expenseEmpty, TransactionType.EXPENSE, year, 1, 1, year, 12, 31)
        loadSection(R.id.incomeList, R.id.incomeEmpty, TransactionType.INCOME, year, 1, 1, year, 12, 31)
    }

    private fun loadSection(
        listId: Int, emptyId: Int, type: TransactionType,
        sy: Int, sm: Int, sd: Int, ey: Int, em: Int, ed: Int
    ) {
        val listContainer = findViewById<LinearLayout>(listId)
        val emptyView = findViewById<TextView>(emptyId)
        lifecycleScope.launch {
            val items = db.transactionDao()
                .getTransactionsInPersianRange(type, sy, sm, sd, ey, em, ed)
                .first()
                .sortedByDescending { it.amount }

            listContainer.removeAllViews()
            if (items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                return@launch
            }
            emptyView.visibility = View.GONE
            for ((index, t) in items.withIndex()) {
                listContainer.addView(buildRankRow(index + 1, t))
            }
        }
    }

    private fun buildRankRow(rank: Int, t: Transaction): View {
        val row = layoutInflater.inflate(R.layout.item_expense_rank, null)

        row.findViewById<TextView>(R.id.rankText).text = rank.toString()

        val title = row.findViewById<TextView>(R.id.expenseTitle)
        title.text = t.title

        val amount = row.findViewById<TextView>(R.id.expenseAmount)
        amount.text = FormatUtils.formatAmount(t.amount)
        amount.setTextColor(
            ContextCompat.getColor(
                this,
                if (t.type == TransactionType.INCOME) R.color.income_green else R.color.expense_red
            )
        )
        return row
    }
}

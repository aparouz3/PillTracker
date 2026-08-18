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

class TopExpensesActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase

    // Section state holders
    private val sections = mutableMapOf<String, SectionState>()

    // Navigation offsets (0 = current, -1 = previous, 1 = next)
    private var weekOffset = 0
    private var monthOffset = 0
    private var yearOffset = 0

    /** Preview shows this many items; 'show more' reveals the rest. */
    private val previewCount = 3

    private class SectionState(val container: LinearLayout, val emptyView: TextView) {
        var items: List<Transaction> = emptyList()
        var expanded: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_expenses)

        db = (application as PillTrackerApp).database

        sections["weekExpense"] = SectionState(findViewById(R.id.weekExpenseList), findViewById(R.id.weekExpenseEmpty))
        sections["weekIncome"] = SectionState(findViewById(R.id.weekIncomeList), findViewById(R.id.weekIncomeEmpty))
        sections["monthExpense"] = SectionState(findViewById(R.id.monthExpenseList), findViewById(R.id.monthExpenseEmpty))
        sections["monthIncome"] = SectionState(findViewById(R.id.monthIncomeList), findViewById(R.id.monthIncomeEmpty))
        sections["yearExpense"] = SectionState(findViewById(R.id.yearExpenseList), findViewById(R.id.yearExpenseEmpty))
        sections["yearIncome"] = SectionState(findViewById(R.id.yearIncomeList), findViewById(R.id.yearIncomeEmpty))

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.prevWeekBtn).setOnClickListener { weekOffset--; loadWeek() }
        findViewById<ImageButton>(R.id.nextWeekBtn).setOnClickListener { weekOffset++; loadWeek() }
        findViewById<ImageButton>(R.id.prevMonthBtn).setOnClickListener { monthOffset--; loadMonth() }
        findViewById<ImageButton>(R.id.nextMonthBtn).setOnClickListener { monthOffset++; loadMonth() }
        findViewById<ImageButton>(R.id.prevYearBtn).setOnClickListener { yearOffset--; loadYear() }
        findViewById<ImageButton>(R.id.nextYearBtn).setOnClickListener { yearOffset++; loadYear() }

        loadAll()
    }

    private fun loadAll() {
        loadWeek()
        loadMonth()
        loadYear()
    }

    private fun loadWeek() {
        val weekStartTs = PersianCalendar.getWeekStartTimestamp() + weekOffset * 7L * 24 * 60 * 60 * 1000L
        val weekStartCal = Calendar.getInstance().apply { timeInMillis = weekStartTs }
        val saturday = PersianCalendar.gregorianToPersian(
            weekStartCal.get(Calendar.YEAR), weekStartCal.get(Calendar.MONTH) + 1, weekStartCal.get(Calendar.DAY_OF_MONTH)
        )
        val friday = PersianCalendar.addDays(saturday.year, saturday.month, saturday.day, 6)

        findViewById<TextView>(R.id.weekTitle).text = if (weekOffset == 0) {
            "این هفته (شنبه ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)})"
        } else {
            "هفته ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)} $saturday.year"
        }

        loadSection("weekExpense", TransactionType.EXPENSE,
            saturday.year, saturday.month, saturday.day, friday.year, friday.month, friday.day)
        loadSection("weekIncome", TransactionType.INCOME,
            saturday.year, saturday.month, saturday.day, friday.year, friday.month, friday.day)
    }

    private fun loadMonth() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        var month = today.month + monthOffset
        var year = today.year
        while (month < 1) { month += 12; year-- }
        while (month > 12) { month -= 12; year++ }

        findViewById<TextView>(R.id.monthTitle).text = if (monthOffset == 0) {
            "این ماه (${PersianCalendar.getPersianMonthName(month)} $year)"
        } else {
            "ماه ${PersianCalendar.getPersianMonthName(month)} $year"
        }

        val lastDay = PersianCalendar.getPersianMonthDays(year, month)
        loadSection("monthExpense", TransactionType.EXPENSE, year, month, 1, year, month, lastDay)
        loadSection("monthIncome", TransactionType.INCOME, year, month, 1, year, month, lastDay)
    }

    private fun loadYear() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        val year = today.year + yearOffset

        findViewById<TextView>(R.id.yearTitle).text = if (yearOffset == 0) {
            "امسال ($year)"
        } else {
            "سال $year"
        }

        loadSection("yearExpense", TransactionType.EXPENSE, year, 1, 1, year, 12, 31)
        loadSection("yearIncome", TransactionType.INCOME, year, 1, 1, year, 12, 31)
    }

    private fun loadSection(key: String, type: TransactionType, sy: Int, sm: Int, sd: Int, ey: Int, em: Int, ed: Int) {
        val section = sections[key] ?: return
        lifecycleScope.launch {
            section.items = db.transactionDao().getTransactionsInPersianRange(type, sy, sm, sd, ey, em, ed).first()
            section.expanded = false // collapse back on period change
            renderSection(section)
        }
    }

    private fun renderSection(section: SectionState) {
        val container = section.container
        container.removeAllViews()

        if (section.items.isEmpty()) {
            section.emptyView.visibility = View.VISIBLE
            return
        }
        section.emptyView.visibility = View.GONE

        // Preview mode: only first N items + "show more" button
        val visible = if (section.expanded) section.items else section.items.take(previewCount)
        for ((index, t) in visible.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_expense_rank, container, false)

            val rank = row.findViewById<TextView>(R.id.rankText)
            rank.text = (index + 1).toString()

            val title = row.findViewById<TextView>(R.id.expenseTitle)
            title.text = t.title

            val date = row.findViewById<TextView>(R.id.expenseDate)
            date.text = "${t.day} ${PersianCalendar.getPersianMonthName(t.month)} $t.year"

            val amount = row.findViewById<TextView>(R.id.expenseAmount)
            amount.text = FormatUtils.formatAmount(t.amount)

            container.addView(row)
        }

        // "Show more" button when there are more items than the preview
        if (!section.expanded && section.items.size > previewCount) {
            val more = TextView(this)
            more.text = "نمایش بیشتر (${section.items.size - previewCount} تراکنش دیگر)"
            more.setTextColor(ContextCompat.getColor(this, R.color.primary))
            more.textSize = 13f
            more.gravity = android.view.Gravity.CENTER
            more.textStyle = android.graphics.Typeface.BOLD
            more.isClickable = true
            more.isFocusable = true
            val pad = (8 * resources.displayMetrics.density).toInt()
            more.setPadding(pad, pad, pad, pad)
            more.setBackgroundResource(android.R.drawable.list_selector_background)
            more.setOnClickListener {
                section.expanded = true
                renderSection(section)
            }
            container.addView(more)
        }
    }
}
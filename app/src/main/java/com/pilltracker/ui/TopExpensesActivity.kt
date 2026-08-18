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

    private lateinit var weekTitle: TextView
    private lateinit var weekExpenseList: LinearLayout
    private lateinit var weekExpenseEmpty: TextView
    private lateinit var weekIncomeList: LinearLayout
    private lateinit var weekIncomeEmpty: TextView
    private lateinit var monthTitle: TextView
    private lateinit var monthExpenseList: LinearLayout
    private lateinit var monthExpenseEmpty: TextView
    private lateinit var monthIncomeList: LinearLayout
    private lateinit var monthIncomeEmpty: TextView
    private lateinit var yearTitle: TextView
    private lateinit var yearExpenseList: LinearLayout
    private lateinit var yearExpenseEmpty: TextView
    private lateinit var yearIncomeList: LinearLayout
    private lateinit var yearIncomeEmpty: TextView

    // Navigation offsets (0 = current, -1 = previous, 1 = next)
    private var weekOffset = 0
    private var monthOffset = 0
    private var yearOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_expenses)

        db = (application as PillTrackerApp).database

        weekTitle = findViewById(R.id.weekTitle)
        weekExpenseList = findViewById(R.id.weekExpenseList)
        weekExpenseEmpty = findViewById(R.id.weekExpenseEmpty)
        weekIncomeList = findViewById(R.id.weekIncomeList)
        weekIncomeEmpty = findViewById(R.id.weekIncomeEmpty)
        monthTitle = findViewById(R.id.monthTitle)
        monthExpenseList = findViewById(R.id.monthExpenseList)
        monthExpenseEmpty = findViewById(R.id.monthExpenseEmpty)
        monthIncomeList = findViewById(R.id.monthIncomeList)
        monthIncomeEmpty = findViewById(R.id.monthIncomeEmpty)
        yearTitle = findViewById(R.id.yearTitle)
        yearExpenseList = findViewById(R.id.yearExpenseList)
        yearExpenseEmpty = findViewById(R.id.yearExpenseEmpty)
        yearIncomeList = findViewById(R.id.yearIncomeList)
        yearIncomeEmpty = findViewById(R.id.yearIncomeEmpty)

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

        weekTitle.text = if (weekOffset == 0) {
            "این هفته (شنبه ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)})"
        } else {
            "هفته ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)} $saturday.year"
        }

        lifecycleScope.launch {
            val expenses = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.EXPENSE,
                saturday.year, saturday.month, saturday.day,
                friday.year, friday.month, friday.day
            ).first()
            fillList(weekExpenseList, weekExpenseEmpty, expenses)
        }
        lifecycleScope.launch {
            val incomes = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.INCOME,
                saturday.year, saturday.month, saturday.day,
                friday.year, friday.month, friday.day
            ).first()
            fillList(weekIncomeList, weekIncomeEmpty, incomes)
        }
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

        monthTitle.text = if (monthOffset == 0) {
            "این ماه (${PersianCalendar.getPersianMonthName(month)} $year)"
        } else {
            "ماه ${PersianCalendar.getPersianMonthName(month)} $year"
        }

        lifecycleScope.launch {
            val expenses = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.EXPENSE,
                year, month, 1,
                year, month, PersianCalendar.getPersianMonthDays(year, month)
            ).first()
            fillList(monthExpenseList, monthExpenseEmpty, expenses)
        }
        lifecycleScope.launch {
            val incomes = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.INCOME,
                year, month, 1,
                year, month, PersianCalendar.getPersianMonthDays(year, month)
            ).first()
            fillList(monthIncomeList, monthIncomeEmpty, incomes)
        }
    }

    private fun loadYear() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        val year = today.year + yearOffset

        yearTitle.text = if (yearOffset == 0) {
            "امسال ($year)"
        } else {
            "سال $year"
        }

        lifecycleScope.launch {
            val expenses = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.EXPENSE,
                year, 1, 1,
                year, 12, 31
            ).first()
            fillList(yearExpenseList, yearExpenseEmpty, expenses)
        }
        lifecycleScope.launch {
            val incomes = db.transactionDao().getTransactionsInPersianRange(
                TransactionType.INCOME,
                year, 1, 1,
                year, 12, 31
            ).first()
            fillList(yearIncomeList, yearIncomeEmpty, incomes)
        }
    }

    private fun fillList(container: LinearLayout, emptyView: TextView, items: List<Transaction>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        // Sorted biggest -> smallest by the query; show top 5
        val top = items.take(5)
        for ((index, t) in top.withIndex()) {
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
        if (items.size > 5) {
            val more = TextView(this)
            more.text = "… و ${items.size - 5} تراکنش دیگر"
            more.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            more.textSize = 12f
            more.gravity = android.view.Gravity.CENTER
            val pad = (8 * resources.displayMetrics.density).toInt()
            more.setPadding(pad, pad, pad, pad)
            container.addView(more)
        }
    }
}
package com.pilltracker.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class StatsActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase

    private lateinit var weekTitle: TextView
    private lateinit var weekIncome: TextView
    private lateinit var weekExpense: TextView
    private lateinit var weekBalance: TextView
    private lateinit var monthTitle: TextView
    private lateinit var monthIncome: TextView
    private lateinit var monthExpense: TextView
    private lateinit var monthBalance: TextView
    private lateinit var yearTitle: TextView
    private lateinit var yearIncome: TextView
    private lateinit var yearExpense: TextView
    private lateinit var yearBalance: TextView

    // Navigation offsets (0 = current, -1 = previous, 1 = next)
    private var weekOffset = 0
    private var monthOffset = 0
    private var yearOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        db = (application as PillTrackerApp).database

        weekTitle = findViewById(R.id.weekTitle)
        weekIncome = findViewById(R.id.weekIncome)
        weekExpense = findViewById(R.id.weekExpense)
        weekBalance = findViewById(R.id.weekBalance)
        monthTitle = findViewById(R.id.monthTitle)
        monthIncome = findViewById(R.id.monthIncome)
        monthExpense = findViewById(R.id.monthExpense)
        monthBalance = findViewById(R.id.monthBalance)
        yearTitle = findViewById(R.id.yearTitle)
        yearIncome = findViewById(R.id.yearIncome)
        yearExpense = findViewById(R.id.yearExpense)
        yearBalance = findViewById(R.id.yearBalance)

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
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )

        // Week start (Saturday) of the current week + offset weeks
        val weekStartTs = PersianCalendar.getWeekStartTimestamp() + weekOffset * 7L * 24 * 60 * 60 * 1000L
        val weekStartCal = Calendar.getInstance().apply { timeInMillis = weekStartTs }
        val saturday = PersianCalendar.gregorianToPersian(
            weekStartCal.get(Calendar.YEAR), weekStartCal.get(Calendar.MONTH) + 1, weekStartCal.get(Calendar.DAY_OF_MONTH)
        )
        val friday = PersianCalendar.addDays(saturday.year, saturday.month, saturday.day, 6)

        val isCurrent = weekOffset == 0
        weekTitle.text = if (isCurrent) {
            "جمع این هفته (شنبه ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)})"
        } else {
            "هفته ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)} $saturday.year"
        }

        lifecycleScope.launch {
            val income = db.transactionDao().getTotalInPersianRange(
                TransactionType.INCOME,
                saturday.year, saturday.month, saturday.day,
                friday.year, friday.month, friday.day
            ).first()
            val expense = db.transactionDao().getTotalInPersianRange(
                TransactionType.EXPENSE,
                saturday.year, saturday.month, saturday.day,
                friday.year, friday.month, friday.day
            ).first()
            weekIncome.text = "درآمد: ${FormatUtils.formatAmount(income)} تومان"
            weekExpense.text = "هزینه: ${FormatUtils.formatAmount(expense)} تومان"
            weekBalance.text = "مانده: ${FormatUtils.formatAmount(income - expense)} تومان"
        }
    }

    private fun loadMonth() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )

        // Month arithmetic in the Persian calendar (offset months, handling year rollover)
        var month = today.month + monthOffset
        var year = today.year
        while (month < 1) { month += 12; year-- }
        while (month > 12) { month -= 12; year++ }

        val isCurrent = monthOffset == 0
        monthTitle.text = if (isCurrent) {
            "جمع ${PersianCalendar.getPersianMonthName(month)} (این ماه)"
        } else {
            "جمع ${PersianCalendar.getPersianMonthName(month)} $year"
        }

        lifecycleScope.launch {
            val income = db.transactionDao().getMonthlyTotal(TransactionType.INCOME, year, month).first()
            val expense = db.transactionDao().getMonthlyTotal(TransactionType.EXPENSE, year, month).first()
            monthIncome.text = "درآمد: ${FormatUtils.formatAmount(income)} تومان"
            monthExpense.text = "هزینه: ${FormatUtils.formatAmount(expense)} تومان"
            monthBalance.text = "مانده: ${FormatUtils.formatAmount(income - expense)} تومان"
        }
    }

    private fun loadYear() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )

        val year = today.year + yearOffset

        val isCurrent = yearOffset == 0
        yearTitle.text = if (isCurrent) {
            "جمع سال $year (امسال)"
        } else {
            "جمع سال $year"
        }

        lifecycleScope.launch {
            val income = db.transactionDao().getYearlyTotal(TransactionType.INCOME, year).first()
            val expense = db.transactionDao().getYearlyTotal(TransactionType.EXPENSE, year).first()
            yearIncome.text = "درآمد: ${FormatUtils.formatAmount(income)} تومان"
            yearExpense.text = "هزینه: ${FormatUtils.formatAmount(expense)} تومان"
            yearBalance.text = "مانده: ${FormatUtils.formatAmount(income - expense)} تومان"
        }
    }
}
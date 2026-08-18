package com.pilltracker.ui

import android.os.Bundle
import android.view.View
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

        loadStats()
    }

    private fun loadStats() {
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )

        val weekStart = PersianCalendar.getWeekStartTimestamp()
        val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000L

        weekTitle.text = "جمع این هفته (از شنبه ${PersianCalendar.getPersianMonthName(today.month)})"

        lifecycleScope.launch {
            val wIncome = db.transactionDao().getTotalBetween(TransactionType.INCOME, weekStart, weekEnd).first()
            val wExpense = db.transactionDao().getTotalBetween(TransactionType.EXPENSE, weekStart, weekEnd).first()
            weekIncome.text = "درآمد: ${FormatUtils.formatAmount(wIncome)} تومان"
            weekExpense.text = "هزینه: ${FormatUtils.formatAmount(wExpense)} تومان"
            weekBalance.text = "مانده: ${FormatUtils.formatAmount(wIncome - wExpense)} تومان"
        }

        monthTitle.text = "جمع ${PersianCalendar.getPersianMonthName(today.month)} $today.year"

        lifecycleScope.launch {
            val mIncome = db.transactionDao().getMonthlyTotal(TransactionType.INCOME, today.year, today.month).first()
            val mExpense = db.transactionDao().getMonthlyTotal(TransactionType.EXPENSE, today.year, today.month).first()
            monthIncome.text = "درآمد: ${FormatUtils.formatAmount(mIncome)} تومان"
            monthExpense.text = "هزینه: ${FormatUtils.formatAmount(mExpense)} تومان"
            monthBalance.text = "مانده: ${FormatUtils.formatAmount(mIncome - mExpense)} تومان"
        }

        yearTitle.text = "جمع سال $today.year"

        lifecycleScope.launch {
            val yIncome = db.transactionDao().getYearlyTotal(TransactionType.INCOME, today.year).first()
            val yExpense = db.transactionDao().getYearlyTotal(TransactionType.EXPENSE, today.year).first()
            yearIncome.text = "درآمد: ${FormatUtils.formatAmount(yIncome)} تومان"
            yearExpense.text = "هزینه: ${FormatUtils.formatAmount(yExpense)} تومان"
            yearBalance.text = "مانده: ${FormatUtils.formatAmount(yIncome - yExpense)} تومان"
        }
    }
}
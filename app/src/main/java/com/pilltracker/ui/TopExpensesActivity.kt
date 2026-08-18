package com.pilltracker.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Transaction
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class TopExpensesActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase

    private lateinit var weekTitle: TextView
    private lateinit var weekList: LinearLayout
    private lateinit var weekEmpty: TextView
    private lateinit var monthTitle: TextView
    private lateinit var monthList: LinearLayout
    private lateinit var monthEmpty: TextView
    private lateinit var yearTitle: TextView
    private lateinit var yearList: LinearLayout
    private lateinit var yearEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_expenses)

        db = (application as PillTrackerApp).database

        weekTitle = findViewById(R.id.weekTitle)
        weekList = findViewById(R.id.weekList)
        weekEmpty = findViewById(R.id.weekEmpty)
        monthTitle = findViewById(R.id.monthTitle)
        monthList = findViewById(R.id.monthList)
        monthEmpty = findViewById(R.id.monthEmpty)
        yearTitle = findViewById(R.id.yearTitle)
        yearList = findViewById(R.id.yearList)
        yearEmpty = findViewById(R.id.yearEmpty)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        loadAll()
    }

    private fun loadAll() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )

        // ---- Week: Saturday .. Friday ----
        val weekStartTs = PersianCalendar.getWeekStartTimestamp()
        val weekStartCal = Calendar.getInstance().apply { timeInMillis = weekStartTs }
        val saturday = PersianCalendar.gregorianToPersian(
            weekStartCal.get(Calendar.YEAR), weekStartCal.get(Calendar.MONTH) + 1, weekStartCal.get(Calendar.DAY_OF_MONTH)
        )
        val friday = PersianCalendar.addDays(saturday.year, saturday.month, saturday.day, 6)
        weekTitle.text = "این هفته (شنبه ${saturday.day} ${PersianCalendar.getPersianMonthName(saturday.month)})"

        // ---- Month ----
        monthTitle.text = "این ماه (${PersianCalendar.getPersianMonthName(today.month)} $today.year)"

        // ---- Year ----
        yearTitle.text = "امسال ($today.year)"

        lifecycleScope.launch {
            val weekExpenses = db.transactionDao().getExpensesInPersianRange(
                saturday.year, saturday.month, saturday.day,
                friday.year, friday.month, friday.day
            ).first()
            fillList(weekList, weekEmpty, weekExpenses)
        }

        lifecycleScope.launch {
            val monthExpenses = db.transactionDao().getExpensesInPersianRange(
                today.year, today.month, 1,
                today.year, today.month, PersianCalendar.getPersianMonthDays(today.year, today.month)
            ).first()
            fillList(monthList, monthEmpty, monthExpenses)
        }

        lifecycleScope.launch {
            val yearExpenses = db.transactionDao().getExpensesInPersianRange(
                today.year, 1, 1,
                today.year, 12, 31
            ).first()
            fillList(yearList, yearEmpty, yearExpenses)
        }
    }

    private fun fillList(container: LinearLayout, emptyView: TextView, expenses: List<Transaction>) {
        container.removeAllViews()
        if (expenses.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        // Sorted biggest -> smallest by the query; show top 10
        val top = expenses.take(10)
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
        if (expenses.size > 10) {
            val more = TextView(this)
            more.text = "… و ${expenses.size - 10} هزینه دیگر"
            more.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            more.textSize = 12f
            more.gravity = android.view.Gravity.CENTER
            val pad = (8 * resources.displayMetrics.density).toInt()
            more.setPadding(pad, pad, pad, pad)
            container.addView(more)
        }
    }
}
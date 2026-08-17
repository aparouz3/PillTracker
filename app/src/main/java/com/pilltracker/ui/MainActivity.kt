package com.pilltracker.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var currentDay: Int = 0

    // UI
    private lateinit var dateHeader: TextView
    private lateinit var prevDayBtn: MaterialButton
    private lateinit var nextDayBtn: MaterialButton
    private lateinit var todayBtn: MaterialButton
    private lateinit var calendarGrid: ViewGroup
    private lateinit var monthYearText: TextView
    private lateinit var prevMonthBtn: MaterialButton
    private lateinit var nextMonthBtn: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryIncome: TextView
    private lateinit var summaryExpense: TextView
    private lateinit var summaryBalance: TextView
    private lateinit var emptyText: TextView
    private lateinit var fabExpense: FloatingActionButton
    private lateinit var fabIncome: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = (application as PillTrackerApp).database

        // Set today's Persian date
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        currentYear = today.year
        currentMonth = today.month
        currentDay = today.day

        initViews()
        setupListeners()
        loadData()
    }

    private fun initViews() {
        dateHeader = findViewById(R.id.dateHeader)
        prevDayBtn = findViewById(R.id.prevDayBtn)
        nextDayBtn = findViewById(R.id.nextDayBtn)
        todayBtn = findViewById(R.id.todayBtn)
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        prevMonthBtn = findViewById(R.id.prevMonthBtn)
        nextMonthBtn = findViewById(R.id.nextMonthBtn)
        recyclerView = findViewById(R.id.recyclerView)
        summaryIncome = findViewById(R.id.summaryIncome)
        summaryExpense = findViewById(R.id.summaryExpense)
        summaryBalance = findViewById(R.id.summaryBalance)
        emptyText = findViewById(R.id.emptyText)
        fabExpense = findViewById(R.id.fabExpense)
        fabIncome = findViewById(R.id.fabIncome)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(transactions) { transaction ->
            showDeleteDialog(transaction)
        }
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        prevDayBtn.setOnClickListener {
            currentDay--
            if (currentDay < 1) {
                currentMonth--
                if (currentMonth < 1) {
                    currentYear--
                    currentMonth = 12
                }
                currentDay = PersianCalendar.getPersianMonthDays(currentYear, currentMonth)
            }
            loadData()
        }

        nextDayBtn.setOnClickListener {
            val maxDays = PersianCalendar.getPersianMonthDays(currentYear, currentMonth)
            currentDay++
            if (currentDay > maxDays) {
                currentDay = 1
                currentMonth++
                if (currentMonth > 12) {
                    currentYear++
                    currentMonth = 1
                }
            }
            loadData()
        }

        todayBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            currentYear = today.year
            currentMonth = today.month
            currentDay = today.day
            loadData()
        }

        prevMonthBtn.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentYear--
                currentMonth = 12
            }
            currentDay = 1
            loadData()
        }

        nextMonthBtn.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentYear++
                currentMonth = 1
            }
            currentDay = 1
            loadData()
        }

        fabExpense.setOnClickListener {
            showAddDialog(TransactionType.EXPENSE)
        }

        fabIncome.setOnClickListener {
            showAddDialog(TransactionType.INCOME)
        }
    }

    private fun loadData() {
        updateHeader()
        buildCalendar()
        loadTransactions()
        loadSummary()
    }

    private fun updateHeader() {
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val isToday = currentYear == today.year && currentMonth == today.month && currentDay == today.day
        val dayName = PersianCalendar.getPersianWeekDayName(cal.get(Calendar.DAY_OF_WEEK))
        dateHeader.text = if (isToday) {
            "امروز — $currentDay ${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"
        } else {
            "$dayName $currentDay ${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"
        }
        monthYearText.text = "${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"
    }

    private fun buildCalendar() {
        calendarGrid.removeAllViews()
        val daysInMonth = PersianCalendar.getPersianMonthDays(currentYear, currentMonth)

        // First day of month: convert to Gregorian, find day of week
        val greg = PersianCalendar.persianToGregorian(currentYear, currentMonth, 1)
        val cal = Calendar.getInstance()
        cal.set(greg.first, greg.second - 1, greg.third)
        var startDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 4) % 7 // 0=Saturday .. 6=Friday

        // Today's day
        val todayCal = Calendar.getInstance()
        val todayPersian = PersianCalendar.gregorianToPersian(
            todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH)
        )

        // Weekday headers
        val weekDays = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")
        for (dayName in weekDays) {
            val tv = layoutInflater.inflate(R.layout.item_calendar_day, calendarGrid, false) as TextView
            tv.text = dayName
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tv.textSize = 11f
            calendarGrid.addView(tv)
        }

        // Empty cells before first day
        for (i in 0 until startDayOfWeek) {
            val tv = layoutInflater.inflate(R.layout.item_calendar_day, calendarGrid, false) as TextView
            tv.visibility = View.INVISIBLE
            calendarGrid.addView(tv)
        }

        // Day cells
        for (day in 1..daysInMonth) {
            val tv = layoutInflater.inflate(R.layout.item_calendar_day, calendarGrid, false) as TextView
            tv.text = day.toString()
            tv.setTextColor(ContextCompat.getColor(this, R.color.calendar_day))

            val isToday = day == todayPersian.day && currentMonth == todayPersian.month && currentYear == todayPersian.year
            val isSelected = day == currentDay

            if (isToday) {
                tv.setBackgroundResource(R.drawable.calendar_today_bg)
            }
            if (isSelected) {
                tv.setBackgroundResource(R.drawable.calendar_selected_bg)
                if (isToday) {
                    tv.setTextColor(ContextCompat.getColor(this, R.color.primary))
                } else {
                    tv.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                }
            }

            tv.setOnClickListener { v ->
                currentDay = day
                loadData()
            }
            calendarGrid.addView(tv)
        }
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            db.transactionDao().getTransactionsForDate(currentYear, currentMonth, currentDay)
                .collectLatest { list ->
                    transactions.clear()
                    transactions.addAll(list)
                    adapter.notifyDataSetChanged()
                    emptyText.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    private fun loadSummary() {
        lifecycleScope.launch {
            db.transactionDao().getDailyTotal(TransactionType.INCOME, currentYear, currentMonth, currentDay)
                .collectLatest { income ->
                    summaryIncome.text = "درآمد: ${FormatUtils.formatAmount(income)}"
                }
        }
        lifecycleScope.launch {
            db.transactionDao().getDailyTotal(TransactionType.EXPENSE, currentYear, currentMonth, currentDay)
                .collectLatest { expense ->
                    summaryExpense.text = "هزینه: ${FormatUtils.formatAmount(expense)}"
                }
        }
        lifecycleScope.launch {
            val income = db.transactionDao().getDailyTotal(TransactionType.INCOME, currentYear, currentMonth, currentDay)
            val expense = db.transactionDao().getDailyTotal(TransactionType.EXPENSE, currentYear, currentMonth, currentDay)
            // Combine both
            kotlinx.coroutines.coroutineScope {
                val i = income
                val e = expense
            }
        }
    }

    private fun showAddDialog(type: TransactionType) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.titleInput)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (type == TransactionType.EXPENSE) "افزودن هزینه" else "افزودن درآمد")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ -> }
            .setNegativeButton("لغو", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text?.toString()?.trim() ?: ""
            val amountStr = amountInput.text?.toString()?.trim() ?: ""

            if (title.isEmpty()) {
                titleInput.error = "عنوان را وارد کنید"
                return@setOnClickListener
            }
            val amount = amountStr.toLongOrNull()
            if (amount == null || amount <= 0) {
                amountInput.error = "مبلغ معتبر وارد کنید"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                db.transactionDao().insert(
                    Transaction(
                        title = title,
                        amount = amount,
                        type = type,
                        year = currentYear,
                        month = currentMonth,
                        day = currentDay
                    )
                )
            }
            dialog.dismiss()
        }
    }

    private fun showDeleteDialog(transaction: Transaction) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف تراکنش")
            .setMessage("آیا از حذف «${transaction.title}» مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    db.transactionDao().delete(transaction)
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    // ---- Adapter ----
    class TransactionAdapter(
        private val items: List<Transaction>,
        private val onDelete: (Transaction) -> Unit
    ) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.transactionTitle)
            val amountText: TextView = view.findViewById(R.id.transactionAmount)
            val typeIcon: View = view.findViewById(R.id.typeIcon)
            val deleteBtn: View = view.findViewById(R.id.deleteBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.titleText.text = item.title
            holder.amountText.text = FormatUtils.formatAmountWithUnit(item.amount)
            holder.amountText.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (item.type == TransactionType.INCOME) R.color.income_green else R.color.expense_red
                )
            )
            holder.typeIcon.setBackgroundResource(
                if (item.type == TransactionType.INCOME) R.drawable.ic_income_dot else R.drawable.ic_expense_dot
            )
            holder.deleteBtn.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount() = items.size
    }
}
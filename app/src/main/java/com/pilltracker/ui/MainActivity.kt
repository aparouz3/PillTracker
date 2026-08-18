package com.pilltracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var currentDay: Int = 0

    // UI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var dateHeader: TextView
    private lateinit var prevDayBtn: ImageButton
    private lateinit var nextDayBtn: ImageButton
    private lateinit var dayStrip: LinearLayout
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
        checkAndShowCrashReport()
    }

    /**
     * If a crash log exists from a previous run, show it so the user can report it.
     */
    private fun checkAndShowCrashReport() {
        try {
            val dir = File(getExternalFilesDir(null) ?: filesDir, "crashes")
            val file = File(dir, "last_crash.txt")
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    file.delete()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("گزارش خطا (کرش قبلی)")
                        .setMessage(content)
                        .setPositiveButton("متوجه شدم", null)
                        .setCancelable(false)
                        .show()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        menuButton = findViewById(R.id.menuButton)
        dateHeader = findViewById(R.id.dateHeader)
        prevDayBtn = findViewById(R.id.prevDayBtn)
        nextDayBtn = findViewById(R.id.nextDayBtn)
        dayStrip = findViewById(R.id.dayStrip)
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

        // Navigation drawer
        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navCalendar -> {
                    drawerLayout.closeDrawers()
                    openCalendar()
                }
                R.id.navStats -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, StatsActivity::class.java))
                }
            }
            true
        }
    }

    private fun setupListeners() {
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(findViewById<NavigationView>(R.id.navView))
        }

        prevDayBtn.setOnClickListener {
            val prev = PersianCalendar.addDays(currentYear, currentMonth, currentDay, -1)
            currentYear = prev.year
            currentMonth = prev.month
            currentDay = prev.day
            loadData()
        }

        nextDayBtn.setOnClickListener {
            val next = PersianCalendar.addDays(currentYear, currentMonth, currentDay, 1)
            currentYear = next.year
            currentMonth = next.month
            currentDay = next.day
            loadData()
        }

        fabExpense.setOnClickListener {
            showAddDialog(TransactionType.EXPENSE)
        }

        fabIncome.setOnClickListener {
            showAddDialog(TransactionType.INCOME)
        }
    }

    private fun openCalendar() {
        val intent = Intent(this, CalendarActivity::class.java)
        calendarLauncher.launch(intent)
    }

    private val calendarLauncher = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        .let { registerForActivityResult(it) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    currentYear = data.getIntExtra("year", currentYear)
                    currentMonth = data.getIntExtra("month", currentMonth)
                    currentDay = data.getIntExtra("day", currentDay)
                    loadData()
                }
            }
        } }

    private fun loadData() {
        updateHeader()
        buildDayStrip()
        loadTransactions()
        loadSummary()
    }

    private fun updateHeader() {
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val isToday = currentYear == today.year && currentMonth == today.month && currentDay == today.day
        val dayName = PersianCalendar.getPersianWeekDayNameForDate(currentYear, currentMonth, currentDay)
        dateHeader.text = if (isToday) {
            "امروز — $currentDay ${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"
        } else {
            "$dayName $currentDay ${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"
        }
    }

    /**
     * Horizontal strip of recent days (7 days ending at the selected day).
     */
    private fun buildDayStrip() {
        dayStrip.removeAllViews()

        val todayCal = Calendar.getInstance()
        val todayPersian = PersianCalendar.gregorianToPersian(
            todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH)
        )

        // 7 days: from (selected-6) to (selected)
        for (offset in -6..0) {
            val d = PersianCalendar.addDays(currentYear, currentMonth, currentDay, offset)
            val cell = layoutInflater.inflate(R.layout.item_day_strip, dayStrip, false)

            val weekday = cell.findViewById<TextView>(R.id.dayStripWeekday)
            val number = cell.findViewById<TextView>(R.id.dayStripNumber)

            weekday.text = PersianCalendar.getPersianWeekDayNameForDate(d.year, d.month, d.day)
            number.text = d.day.toString()

            val isSelected = d.year == currentYear && d.month == currentMonth && d.day == currentDay
            val isToday = d.year == todayPersian.year && d.month == todayPersian.month && d.day == todayPersian.day

            if (isSelected) {
                number.setBackgroundResource(R.drawable.calendar_selected_bg)
                number.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            } else if (isToday) {
                number.setBackgroundResource(R.drawable.calendar_today_bg)
                number.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                number.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            }

            cell.setOnClickListener {
                currentYear = d.year
                currentMonth = d.month
                currentDay = d.day
                loadData()
            }
            dayStrip.addView(cell)
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
            val income = db.transactionDao().getDailyTotal(TransactionType.INCOME, currentYear, currentMonth, currentDay).first()
            val expense = db.transactionDao().getDailyTotal(TransactionType.EXPENSE, currentYear, currentMonth, currentDay).first()
            summaryIncome.text = "درآمد: ${FormatUtils.formatAmount(income)}"
            summaryExpense.text = "هزینه: ${FormatUtils.formatAmount(expense)}"
            summaryBalance.text = "مانده: ${FormatUtils.formatAmount(income - expense)}"
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
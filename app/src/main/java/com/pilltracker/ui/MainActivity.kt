package com.pilltracker.ui

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
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
import com.pilltracker.util.BackupUtils
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import com.pilltracker.work.CrownsNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var adapter: DayAdapter
    private val dayItems = mutableListOf<DayItem>()

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var currentDay: Int = 0
    private var transactionsJob: Job? = null
    private var categoryNameMap: Map<Long, String> = emptyMap()

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
        requestNotificationPermission()
        checkTomorrowNoteNotification()
        checkPinLock()
    }

    // ---- App lock (PIN) ----

    private val pinPrefs: android.content.SharedPreferences
        get() = getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private fun isPinSet(): Boolean = pinPrefs.getString("pin", "").isNullOrEmpty().not()

    private fun checkPinLock() {
        val pin = pinPrefs.getString("pin", "")
        if (pin.isNullOrEmpty()) return
        // Show lock: biometric first if available, else PIN dialog
        showBiometricOrPinLock(pin)
    }

    private fun showBiometricOrPinLock(expectedPin: String) {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (canAuth == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = androidx.biometric.BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        // unlocked via fingerprint — nothing to do
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Negative button ("ورود با پین") or cancel/failure — fall back to PIN dialog
                        showPinLockDialog(expectedPin)
                    }

                    override fun onAuthenticationFailed() {
                        // let the prompt retry
                    }
                }
            )
            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("قفل برنامه")
                .setSubtitle("اثر انگشت را لمس کنید")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("ورود با پین")
                .build()
            try {
                prompt.authenticate(promptInfo)
            } catch (e: Exception) {
                showPinLockDialog(expectedPin)
            }
        } else {
            showPinLockDialog(expectedPin)
        }
    }

    private fun showPinLockDialog(expectedPin: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, null)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pinInput)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("🔒 قفل برنامه")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("ورود") { _, _ ->
                val entered = pinInput.text?.toString()?.trim().orEmpty()
                if (entered == expectedPin) {
                    // unlocked — dismiss
                } else {
                    pinInput.error = "پین اشتباه است"
                    showPinLockDialog(expectedPin)
                }
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val entered = pinInput.text?.toString()?.trim().orEmpty()
            if (entered == expectedPin) {
                dialog.dismiss()
            } else {
                pinInput.error = "پین اشتباه است"
            }
        }
    }

    private fun showPinSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, null)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pinInput)
        val existingPin = pinPrefs.getString("pin", "")
        val isSet = !existingPin.isNullOrEmpty()

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isSet) "تغییر پین قفل" else "تنظیم پین قفل")
            .setMessage(if (isSet) "پین فعلی را وارد کنید" else "یک پین ۴ رقمی وارد کنید")
            .setView(dialogView)
            .setPositiveButton(if (isSet) "تأیید" else "فعال‌سازی") { _, _ ->
                val entered = pinInput.text?.toString()?.trim().orEmpty()
                if (isSet) {
                    if (entered == existingPin) {
                        showNewPinDialog()
                    } else {
                        Toast.makeText(this, "پین فعلی اشتباه است", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (entered.length >= 4) {
                        pinPrefs.edit().putString("pin", entered).apply()
                        Toast.makeText(this, "قفل فعال شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "پین باید حداقل ۴ رقم باشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton(if (isSet) "غیرفعال‌سازی" else null) { _, _ ->
                if (isSet) {
                    pinPrefs.edit().remove("pin").apply()
                    Toast.makeText(this, "قفل غیرفعال شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showNewPinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, null)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pinInput)
        MaterialAlertDialogBuilder(this)
            .setTitle("پین جدید")
            .setMessage("یک پین جدید وارد کنید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val entered = pinInput.text?.toString()?.trim().orEmpty()
                if (entered.length >= 4) {
                    pinPrefs.edit().putString("pin", entered).apply()
                    Toast.makeText(this, "پین تغییر کرد", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "پین باید حداقل ۴ رقم باشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---- Search ----

    private val searchLauncher = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        .let { registerForActivityResult(it) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null && data.hasExtra("year")) {
                    currentYear = data.getIntExtra("year", currentYear)
                    currentMonth = data.getIntExtra("month", currentMonth)
                    currentDay = data.getIntExtra("day", currentDay)
                    loadData()
                }
            }
        } }

    // ---- CSV / Excel export ----

    private val csvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) writeExport(uri, "csv")
    }

    private val xlsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.ms-excel")) { uri ->
        if (uri != null) writeExport(uri, "xls")
    }

    private fun exportCsv() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        csvLauncher.launch("pilltracker_${today.year}_${today.month}_${today.day}.csv")
    }

    private fun exportXls() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        xlsLauncher.launch("pilltracker_${today.year}_${today.month}_${today.day}.xls")
    }

    private fun writeExport(uri: Uri, format: String) {
        lifecycleScope.launch {
            try {
                val all = db.transactionDao().getAllTransactionsOnce()
                val categories = db.categoryDao().getAllCategoriesOnce()
                val bytes = if (format == "csv") {
                    com.pilltracker.util.CsvExportHelper.buildCsv(all, categories)
                } else {
                    com.pilltracker.util.CsvExportHelper.buildXls(all, categories)
                }
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this@MainActivity, "خروجی ذخیره شد (${all.size} تراکنش)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطا در ذخیره: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * When the app is opened, checks whether tomorrow has a daily note.
     * If yes, shows a notification (only once per day). No notification otherwise.
     */
    private fun checkTomorrowNoteNotification() {
        lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance()
                val today = PersianCalendar.gregorianToPersian(
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
                )
                val todayKey = "${today.year}-${today.month}-${today.day}"
                val prefs = CrownsNotifier.prefs(this@MainActivity)
                val lastShown = prefs.getString(CrownsNotifier.KEY_LAST_NOTE_NOTIF, "")
                if (lastShown == todayKey) return@launch // already notified today

                val tomorrow = PersianCalendar.addDays(today.year, today.month, today.day, 1)
                val note = db.noteDao().getNoteForDate(tomorrow.year, tomorrow.month, tomorrow.day)
                if (note == null || note.text.isBlank()) return@launch

                prefs.edit().putString(CrownsNotifier.KEY_LAST_NOTE_NOTIF, todayKey).apply()
                showTomorrowNoteNotification(note.text)
            } catch (e: Exception) {
                // silent
            }
        }
    }

    private fun showTomorrowNoteNotification(noteText: String) {
        CrownsNotifier.ensureChannel(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 2100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CrownsNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📝 یادداشت فردا")
            .setContentText(noteText.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(noteText))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2100, notification)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
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
        adapter = DayAdapter(
            dayItems,
            onDelete = { transaction -> showDeleteDialog(transaction) },
            onEdit = { transaction -> showEditDialog(transaction) },
            onDeleteFolder = { folder -> showDeleteFolderDialog(folder) },
            onFolderClick = { folder -> showAddDialogToFolder(folder) },
            categoryNames = { categoryNameMap }
        )
        // Load category names for display in the list
        lifecycleScope.launch {
            categoryNameMap = db.categoryDao().getAllCategoriesOnce().associate { it.id to it.name }
            adapter.notifyDataSetChanged()
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
                R.id.navTopExpenses -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, TopExpensesActivity::class.java))
                }
                R.id.navCategories -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, CategoriesActivity::class.java))
                }
                R.id.navCrowns -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, CrownsActivity::class.java))
                }
                R.id.navRecurring -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, RecurringActivity::class.java))
                }
                R.id.navSearch -> {
                    drawerLayout.closeDrawers()
                    searchLauncher.launch(Intent(this, SearchActivity::class.java))
                }
                R.id.navExportCsv -> {
                    drawerLayout.closeDrawers()
                    exportCsv()
                }
                R.id.navExportXls -> {
                    drawerLayout.closeDrawers()
                    exportXls()
                }
                R.id.navPinLock -> {
                    drawerLayout.closeDrawers()
                    showPinSetupDialog()
                }
                R.id.navBackup -> {
                    drawerLayout.closeDrawers()
                    exportBackup()
                }
                R.id.navRestore -> {
                    drawerLayout.closeDrawers()
                    importBackup()
                }
            }
            true
        }
    }

    // ---- Backup / Restore ----

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            writeBackup(uri)
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreBackup(uri)
        }
    }

    private fun exportBackup() {
        val now = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        val filename = "pilltracker_backup_${today.year}_${today.month}_${today.day}.json"
        backupLauncher.launch(filename)
    }

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val all = db.transactionDao().getAllTransactionsOnce()
                val notes = db.noteDao().getAllNotesOnce()
                val categories = db.categoryDao().getAllCategoriesOnce()
                val folders = db.folderDao().getAllFoldersOnce()
                val foods = db.foodDao().getAllFoodsOnce()
                val schedule = db.scheduleDao().getAllOnce()
                val priceHistory = db.priceHistoryDao().getAllOnce()
                val recurring = db.recurringDao().getAll()
                contentResolver.openOutputStream(uri)?.use { out ->
                    BackupUtils.writeToStream(all, notes, categories, folders, foods, schedule, priceHistory, recurring, out)
                }
                Toast.makeText(this@MainActivity, "بکاپ ذخیره شد (${all.size} تراکنش، ${notes.size} یادداشت، ${categories.size} کتگوری، ${folders.size} پوشه، ${foods.size} غذا، ${schedule.size} کلاس، ${priceHistory.size} قیمت، ${recurring.size} تکراری)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطا در بکاپ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importBackup() {
        restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
    }

    private fun restoreBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw Exception("فایل خالی است")
                val data = BackupUtils.parseBackup(text)
                if (data.transactions.isEmpty() && data.notes.isEmpty() && data.categories.isEmpty() && data.folders.isEmpty()) {
                    Toast.makeText(this@MainActivity, "فایل بکاپ معتبر نیست", Toast.LENGTH_LONG).show()
                    return@launch
                }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("بازیابی داده")
                    .setMessage("${data.transactions.size} تراکنش، ${data.notes.size} یادداشت، ${data.categories.size} کتگوری، ${data.folders.size} پوشه، ${data.foods.size} غذا، ${data.schedule.size} کلاس، ${data.priceHistory.size} قیمت و ${data.recurring.size} تکراری در فایل پیدا شد. داده فعلی با این داده جایگزین می‌شود. ادامه می‌دهید؟")
                    .setPositiveButton("بله") { _, _ ->
                        lifecycleScope.launch {
                            db.transactionDao().deleteAll()
                            db.transactionDao().insertAll(data.transactions)
                            db.noteDao().deleteAll()
                            db.noteDao().insertAll(data.notes)
                            db.categoryDao().getAllCategoriesOnce().forEach { db.categoryDao().delete(it.id) }
                            db.categoryDao().insertAll(data.categories)
                            db.folderDao().deleteAll()
                            db.folderDao().insertAll(data.folders)
                            db.foodDao().getAllFoodsOnce().forEach { db.foodDao().delete(it.id) }
                            db.foodDao().insertAll(data.foods)
                            db.scheduleDao().deleteAll()
                            db.scheduleDao().insertAll(data.schedule)
                            db.priceHistoryDao().deleteAll()
                            db.priceHistoryDao().insertAll(data.priceHistory)
                            db.recurringDao().deleteAll()
                            db.recurringDao().insertAll(data.recurring)
                            Toast.makeText(this@MainActivity, "بازیابی انجام شد (${data.transactions.size} تراکنش، ${data.notes.size} یادداشت، ${data.categories.size} کتگوری، ${data.folders.size} پوشه، ${data.foods.size} غذا، ${data.schedule.size} کلاس، ${data.priceHistory.size} قیمت، ${data.recurring.size} تکراری)", Toast.LENGTH_LONG).show()
                            loadData()
                            loadNote()
                        }
                    }
                    .setNegativeButton("خیر", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطا در بازیابی: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

        // Add daily folder button
        findViewById<ImageButton>(R.id.addFolderBtn).setOnClickListener {
            showAddFolderDialog()
        }

        // Daily note card
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.noteCard).setOnClickListener {
            showNoteDialog()
        }
        findViewById<ImageButton>(R.id.noteButton).setOnClickListener {
            showNoteDialog()
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
        loadNote()
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

        // 7 days: from (selected-3) to (selected+3) — selected day in the middle
        for (offset in -3..3) {
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
        transactionsJob?.cancel()
        transactionsJob = lifecycleScope.launch {
            // Load daily folders and transactions together, then build a grouped list:
            // folder header (with its own totals) followed by its transactions,
            // then transactions without a folder at the end.
            val folders = db.folderDao().getFoldersForDateOnce(currentYear, currentMonth, currentDay)
            val list = db.transactionDao().getTransactionsForDate(currentYear, currentMonth, currentDay).first()

            dayItems.clear()
            val byFolder = list.groupBy { it.folderId }
            for (folder in folders) {
                val txs = byFolder[folder.id].orEmpty()
                val exp = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val inc = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                dayItems.add(DayItem.FolderHeader(folder, exp, inc))
                dayItems.addAll(txs.map { DayItem.Tx(it) })
            }
            // Transactions without any folder — separated with a thin dashed line
            val unfoldered = byFolder[null].orEmpty()
            if (unfoldered.isNotEmpty() && folders.isNotEmpty()) {
                dayItems.add(DayItem.Separator)
            }
            unfoldered.forEach { dayItems.add(DayItem.Tx(it)) }

            adapter.notifyDataSetChanged()
            recyclerView.requestLayout()
            emptyText.visibility = if (dayItems.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showAddFolderDialog() {
        val input = TextInputEditText(this)
        input.hint = "نام پوشه (مثلاً: خرید، قسط)"
        input.setPadding(24, 24, 24, 24)
        MaterialAlertDialogBuilder(this)
            .setTitle("پوشه جدید برای این روز")
            .setView(input)
            .setPositiveButton("ساخت") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.folderDao().insert(
                            com.pilltracker.data.Folder(
                                name = name,
                                year = currentYear,
                                month = currentMonth,
                                day = currentDay
                            )
                        )
                        loadTransactions()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showDeleteFolderDialog(folder: com.pilltracker.data.Folder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف پوشه «${folder.name}»")
            .setMessage("تراکنش‌های داخل این پوشه حذف نمی‌شوند؛ فقط از پوشه خارج می‌شوند.")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    db.folderDao().delete(folder.id)
                    // Unlink transactions from this folder
                    val all = db.transactionDao().getAllTransactionsOnce()
                    for (t in all) {
                        if (t.folderId == folder.id) {
                            db.transactionDao().update(t.copy(folderId = null))
                        }
                    }
                    loadTransactions()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    /**
     * Formats the amount field with comma separators while typing (e.g. 1,500,000).
     */
    private fun attachAmountFormatting(input: TextInputEditText) {
        input.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().replace(",", "").filter { it.isDigit() }
                val formatted = formatWithCommas(digits)
                if (formatted != s.toString()) {
                    input.setText(formatted)
                    input.setSelection(formatted.length)
                }
                isFormatting = false
            }
        })
    }

    private fun formatWithCommas(digits: String): String {
        if (digits.isEmpty()) return ""
        val sb = StringBuilder()
        var count = 0
        for (i in digits.length - 1 downTo 0) {
            sb.append(digits[i])
            count++
            if (count % 3 == 0 && i > 0) sb.append(',')
        }
        return sb.reverse().toString()
    }

    private fun showAddDialog(type: TransactionType) {
        showAddDialog(type, null)
    }

    private fun showAddDialogToFolder(folder: com.pilltracker.data.Folder) {
        showAddDialog(TransactionType.EXPENSE, folder)
    }

    private fun showAddDialog(type: TransactionType, preselectedFolder: com.pilltracker.data.Folder?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.titleInput)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        attachAmountFormatting(amountInput)
        val folderDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.folderDropdown)
        val categoryDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.categoryDropdown)
        val recurringSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.recurringSwitch)
        val recurringIntervalLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.recurringIntervalLayout)
        val recurringIntervalDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.recurringIntervalDropdown)

        // Recurring interval selector (shown when switch is on)
        val intervalOptions = arrayOf("ماهانه (هر ۳۰ روز)", "هفتگی (هر ۷ روز)", "هر ۲ هفته (۱۴ روز)", "سالیانه (۳۶۵ روز)")
        val intervalValues = intArrayOf(30, 7, 14, 365)
        var selectedIntervalDays = 30
        recurringSwitch.setOnCheckedChangeListener { _, checked ->
            recurringIntervalLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }
        recurringIntervalDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, intervalOptions)
        )
        recurringIntervalDropdown.setText(intervalOptions[0], false)
        recurringIntervalDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedIntervalDays = intervalValues[position]
        }

        // Load daily folders (for the selected day) into the dropdown
        val folderNames = mutableListOf<String>("— بدون پوشه —")
        val folderIds = mutableListOf<Long?>(null)
        var selectedFolderId: Long? = preselectedFolder?.id
        lifecycleScope.launch {
            val folders = db.folderDao().getFoldersForDateOnce(currentYear, currentMonth, currentDay)
            var preselectedName = ""
            for (f in folders) {
                folderNames.add(f.name)
                folderIds.add(f.id)
                if (f.id == preselectedFolder?.id) preselectedName = f.name
            }
            folderDropdown.setAdapter(
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    folderNames
                )
            )
            folderDropdown.setText(preselectedName, false)
        }
        folderDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedFolderId = folderIds[position]
        }

        // Load categories into the dropdown (first item = no category)
        val categoryNames = mutableListOf<String>("— بدون کتگوری —")
        val categoryIds = mutableListOf<Long?>(null)
        var selectedCategoryId: Long? = null
        lifecycleScope.launch {
            val cats = db.categoryDao().getAllCategoriesOnce()
            for (c in cats) {
                categoryNames.add(c.name)
                categoryIds.add(c.id)
            }
            categoryDropdown.setAdapter(
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    categoryNames
                )
            )
            categoryDropdown.setText("", false)
        }
        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categoryIds[position]
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (type == TransactionType.EXPENSE) "افزودن هزینه" else "افزودن درآمد")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ -> }
            .setNegativeButton("لغو", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text?.toString()?.trim() ?: ""
            val amountStr = amountInput.text?.toString()?.trim()?.replace(",", "") ?: ""

            if (title.isEmpty()) {
                titleInput.error = "عنوان را وارد کنید"
                return@setOnClickListener
            }
            val amount = amountStr.toLongOrNull()
            if (amount == null || amount <= 0) {
                amountInput.error = "مبلغ معتبر وارد کنید"
                return@setOnClickListener
            }

            val isRecurring = recurringSwitch.isChecked
            lifecycleScope.launch {
                if (isRecurring) {
                    db.recurringDao().insert(
                        com.pilltracker.data.RecurringTransaction(
                            title = title,
                            amount = amount,
                            type = type,
                            categoryId = selectedCategoryId,
                            intervalDays = selectedIntervalDays,
                            anchorYear = currentYear,
                            anchorMonth = currentMonth,
                            anchorDay = currentDay,
                            nextYear = currentYear,
                            nextMonth = currentMonth,
                            nextDay = currentDay
                        )
                    )
                    Toast.makeText(this@MainActivity, "تراکنش تکراری فعال شد — از فردا به بعد هر بازه ثبت می‌شود", Toast.LENGTH_LONG).show()
                } else {
                    db.transactionDao().insert(
                        Transaction(
                            title = title,
                            amount = amount,
                            type = type,
                            year = currentYear,
                            month = currentMonth,
                            day = currentDay,
                            categoryId = selectedCategoryId,
                            folderId = selectedFolderId
                        )
                    )
                    loadTransactions()
                    loadSummary()
                }
            }
            dialog.dismiss()
        }
    }

    private fun showEditDialog(transaction: Transaction) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.titleInput)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val folderDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.folderDropdown)
        val categoryDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.categoryDropdown)
        val expenseBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.typeExpenseBtn)
        val incomeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.typeIncomeBtn)

        titleInput.setText(transaction.title)
        amountInput.setText(formatWithCommas(transaction.amount.toString()))
        attachAmountFormatting(amountInput)

        // Load daily folders, preselect the transaction's current folder
        val folderNames = mutableListOf<String>("— بدون پوشه —")
        val folderIds = mutableListOf<Long?>(null)
        var selectedFolderId: Long? = transaction.folderId
        lifecycleScope.launch {
            val folders = db.folderDao().getFoldersForDateOnce(currentYear, currentMonth, currentDay)
            var selectedName = ""
            for (f in folders) {
                folderNames.add(f.name)
                folderIds.add(f.id)
                if (f.id == transaction.folderId) selectedName = f.name
            }
            folderDropdown.setAdapter(
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    folderNames
                )
            )
            folderDropdown.setText(selectedName, false)
        }
        folderDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedFolderId = folderIds[position]
        }

        // Load categories, preselect the transaction's current folder
        val categoryNames = mutableListOf<String>("— بدون کتگوری —")
        val categoryIds = mutableListOf<Long?>(null)
        var selectedCategoryId: Long? = transaction.categoryId
        lifecycleScope.launch {
            val cats = db.categoryDao().getAllCategoriesOnce()
            var selectedName = ""
            for (c in cats) {
                categoryNames.add(c.name)
                categoryIds.add(c.id)
                if (c.id == transaction.categoryId) selectedName = c.name
            }
            categoryDropdown.setAdapter(
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    categoryNames
                )
            )
            categoryDropdown.setText(selectedName, false)
        }
        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categoryIds[position]
        }

        // Type selector: highlight the current type, tap to switch
        var selectedType = transaction.type
        fun updateTypeButtons() {
            val isExpense = selectedType == TransactionType.EXPENSE
            expenseBtn.isChecked = isExpense
            incomeBtn.isChecked = !isExpense
            expenseBtn.setBackgroundColor(
                ContextCompat.getColor(this, if (isExpense) R.color.expense_red else android.R.color.transparent)
            )
            incomeBtn.setBackgroundColor(
                ContextCompat.getColor(this, if (!isExpense) R.color.income_green else android.R.color.transparent)
            )
            expenseBtn.setTextColor(
                ContextCompat.getColor(this, if (isExpense) R.color.on_primary else R.color.expense_red)
            )
            incomeBtn.setTextColor(
                ContextCompat.getColor(this, if (!isExpense) R.color.on_primary else R.color.income_green)
            )
        }
        updateTypeButtons()
        expenseBtn.setOnClickListener {
            selectedType = TransactionType.EXPENSE
            updateTypeButtons()
        }
        incomeBtn.setOnClickListener {
            selectedType = TransactionType.INCOME
            updateTypeButtons()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("ویرایش تراکنش")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ -> }
            .setNegativeButton("لغو", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text?.toString()?.trim() ?: ""
            val amountStr = amountInput.text?.toString()?.trim()?.replace(",", "") ?: ""

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
                db.transactionDao().update(
                    transaction.copy(
                        title = title,
                        amount = amount,
                        type = selectedType,
                        categoryId = selectedCategoryId,
                        folderId = selectedFolderId
                    )
                )
                loadTransactions()
                loadSummary()
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
                    loadTransactions()
                    loadSummary()
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    // ---- Daily Note ----

    private fun loadNote() {
        lifecycleScope.launch {
            val note = db.noteDao().getNoteForDate(currentYear, currentMonth, currentDay)
            val noteText = findViewById<TextView>(R.id.noteText)
            if (note != null && note.text.isNotBlank()) {
                noteText.text = note.text
                noteText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface))
            } else {
                noteText.text = "یادداشتی برای این روز ثبت نشده"
                noteText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            }
        }
    }

    private fun showNoteDialog() {
        lifecycleScope.launch {
            val existing = db.noteDao().getNoteForDate(currentYear, currentMonth, currentDay)
            val noteEdit = TextInputEditText(this@MainActivity)
            noteEdit.setText(existing?.text ?: "")
            noteEdit.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            noteEdit.setMinLines(4)
            noteEdit.setMaxLines(8)
            val pad = (12 * resources.displayMetrics.density).toInt()
            noteEdit.setPadding(pad, pad, pad, pad)

            val dateStr = "${currentDay} ${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("یادداشت روزانه — $dateStr")
                .setView(noteEdit)
                .setPositiveButton("ذخیره") { _, _ ->
                    val text = noteEdit.text?.toString()?.trim() ?: ""
                    lifecycleScope.launch {
                        if (text.isEmpty()) {
                            db.noteDao().deleteForDate(currentYear, currentMonth, currentDay)
                        } else {
                            db.noteDao().insert(
                                com.pilltracker.data.DailyNote(
                                    year = currentYear,
                                    month = currentMonth,
                                    day = currentDay,
                                    text = text
                                )
                            )
                        }
                        loadNote()
                    }
                }
                .setNegativeButton("انصراف", null)
                .setNeutralButton("حذف یادداشت") { _, _ ->
                    lifecycleScope.launch {
                        db.noteDao().deleteForDate(currentYear, currentMonth, currentDay)
                        loadNote()
                    }
                }
                .show()
        }
    }

    // ---- Day items ----
    sealed class DayItem {
        data class FolderHeader(
            val folder: com.pilltracker.data.Folder,
            val expenseTotal: Long,
            val incomeTotal: Long
        ) : DayItem()

        data class Tx(val transaction: Transaction) : DayItem()

        object Separator : DayItem()
    }

    // ---- Adapter ----
    class DayAdapter(
        private val items: List<DayItem>,
        private val onDelete: (Transaction) -> Unit,
        private val onEdit: (Transaction) -> Unit,
        private val onDeleteFolder: (com.pilltracker.data.Folder) -> Unit,
        private val onFolderClick: (com.pilltracker.data.Folder) -> Unit,
        private val categoryNames: () -> Map<Long, String>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_FOLDER = 0
            private const val TYPE_TX = 1
            private const val TYPE_SEPARATOR = 2
        }

        inner class FolderHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.folderTitle)
            val expenseTotal: TextView = view.findViewById(R.id.folderExpenseTotal)
            val incomeTotal: TextView = view.findViewById(R.id.folderIncomeTotal)
            val deleteBtn: ImageButton = view.findViewById(R.id.deleteFolderBtn)
        }

        inner class TxHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.transactionTitle)
            val amountText: TextView = view.findViewById(R.id.transactionAmount)
            val typeIcon: View = view.findViewById(R.id.typeIcon)
            val categoryText: TextView = view.findViewById(R.id.transactionCategory)
            val editBtn: View = view.findViewById(R.id.editBtn)
            val deleteBtn: View = view.findViewById(R.id.deleteBtn)
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DayItem.FolderHeader -> TYPE_FOLDER
                is DayItem.Separator -> TYPE_SEPARATOR
                else -> TYPE_TX
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_FOLDER -> FolderHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_folder_header, parent, false)
                )
                TYPE_SEPARATOR -> object : RecyclerView.ViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_separator, parent, false)
                ) {}
                else -> TxHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_transaction, parent, false)
                )
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DayItem.FolderHeader -> {
                    val h = holder as FolderHolder
                    h.titleText.text = "📁 ${item.folder.name}"
                    h.expenseTotal.text = if (item.expenseTotal > 0) "هزینه: ${FormatUtils.formatAmount(item.expenseTotal)}" else ""
                    h.incomeTotal.text = if (item.incomeTotal > 0) "درآمد: ${FormatUtils.formatAmount(item.incomeTotal)}" else ""
                    h.deleteBtn.setOnClickListener { onDeleteFolder(item.folder) }
                    // Tap the folder name to add a transaction inside this folder
                    h.itemView.setOnClickListener { onFolderClick(item.folder) }
                    h.titleText.setOnClickListener { onFolderClick(item.folder) }
                }
                is DayItem.Tx -> {
                    val h = holder as TxHolder
                    val tx = item.transaction
                    h.titleText.text = tx.title
                    h.amountText.text = FormatUtils.formatAmountWithUnit(tx.amount)
                    h.amountText.setTextColor(
                        ContextCompat.getColor(
                            h.itemView.context,
                            if (tx.type == TransactionType.INCOME) R.color.income_green else R.color.expense_red
                        )
                    )
                    h.typeIcon.setBackgroundResource(
                        if (tx.type == TransactionType.INCOME) R.drawable.ic_income_dot else R.drawable.ic_expense_dot
                    )
                    if (tx.categoryId != null) {
                        val name = categoryNames()[tx.categoryId]
                        if (!name.isNullOrEmpty()) {
                            h.categoryText.text = "🏷 $name"
                            h.categoryText.visibility = View.VISIBLE
                        } else {
                            h.categoryText.visibility = View.GONE
                        }
                    } else {
                        h.categoryText.visibility = View.GONE
                    }
                    h.editBtn.setOnClickListener { onEdit(tx) }
                    h.deleteBtn.setOnClickListener { onDelete(tx) }
                }
                is DayItem.Separator -> {
                    // Separator is just a view, nothing to bind
                }
            }
        }
    }
}
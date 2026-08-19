package com.pilltracker.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Food
import com.pilltracker.data.PriceHistory
import com.pilltracker.data.ScheduleEntry
import com.pilltracker.util.PersianCalendar
import com.pilltracker.work.CrownsNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CrownsActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase

    // Gold price views
    private lateinit var goldProgress: ProgressBar
    private lateinit var goldPriceText: TextView
    private lateinit var goldUpdateTime: TextView
    private lateinit var priceChart: LineChartView

    // Food views
    private lateinit var foodSuggestionText: TextView
    private lateinit var refreshFoodBtn: Button

    // Schedule
    private lateinit var scheduleTodayLabel: TextView
    private lateinit var scheduleListContainer: ViewGroup
    private lateinit var scheduleEmptyText: TextView
    private lateinit var editScheduleBtn: Button

    private var dayOffset = 0 // 0 = today, -1 = yesterday, +1 = tomorrow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crowns)

        db = (application as PillTrackerApp).database

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        goldProgress = findViewById(R.id.goldProgress)
        goldPriceText = findViewById(R.id.goldPriceText)
        goldUpdateTime = findViewById(R.id.goldUpdateTime)
        priceChart = findViewById(R.id.priceChart)

        foodSuggestionText = findViewById(R.id.foodSuggestionText)
        refreshFoodBtn = findViewById(R.id.refreshFoodBtn)

        scheduleTodayLabel = findViewById(R.id.scheduleTodayLabel)
        scheduleListContainer = findViewById(R.id.scheduleListContainer)
        scheduleEmptyText = findViewById(R.id.scheduleEmptyText)
        editScheduleBtn = findViewById(R.id.editScheduleBtn)

        // Seed default schedule from assets on first run (only if table is empty)
        seedScheduleIfNeeded()

        // Load gold price
        loadGoldPrice()

        // Load food suggestion (same random food all day, changable via dice)
        loadFoodSuggestion()
        refreshFoodBtn.setOnClickListener { changeFoodSuggestion() }
        findViewById<View>(R.id.foodCard).setOnClickListener { showFoodListDialog() }

        // Load class schedule
        findViewById<View>(R.id.prevDayBtn).setOnClickListener { dayOffset--; loadSchedule() }
        findViewById<View>(R.id.nextDayBtn).setOnClickListener { dayOffset++; loadSchedule() }
        editScheduleBtn.setOnClickListener { showEditScheduleDialog() }
        loadSchedule()

        // Notification toggles
        setupNotifSwitches()
    }

    // ==================== Tara Price + Chart ====================

    private fun loadGoldPrice() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { goldProgress.visibility = View.VISIBLE }
                val price = fetchGoldPrice()
                if (price != null) {
                    // Store in history (one entry per day)
                    val cal = Calendar.getInstance()
                    val today = PersianCalendar.gregorianToPersian(
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
                    )
                    val dateKey = "${today.year}-${today.month}-${today.day}"
                    val existing = db.priceHistoryDao().getByDate(dateKey)
                    if (existing == null) {
                        db.priceHistoryDao().insert(PriceHistory(dateKey = dateKey, price = price))
                    }
                }
                val history = db.priceHistoryDao().getRecent()
                withContext(Dispatchers.Main) {
                    goldProgress.visibility = View.GONE
                    if (price != null) {
                        goldPriceText.text = "قیمت تارا: ${formatPrice(price)} تومان"
                        val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        goldUpdateTime.text = "آخرین به‌روزرسانی: $now"
                    } else {
                        goldPriceText.text = "قیمت در دسترس نیست"
                        // Still show chart from stored history
                        val last = history.firstOrNull()
                        if (last != null) {
                            goldPriceText.text = "قیمت تارا: ${formatPrice(last.price)} تومان (آفلاین)"
                        }
                    }
                    priceChart.setData(history)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    goldProgress.visibility = View.GONE
                    goldPriceText.text = "خطا در دریافت قیمت"
                }
            }
        }
    }

    private fun fetchGoldPrice(): Long? {
        return try {
            val url = "https://www.iranjib.ir/showgroup/45/%D9%82%DB%8C%D9%85%D8%AA-%D8%AE%D9%88%D8%AF%D8%B1%D9%88-%D8%AA%D9%88%D9%84%DB%8C%D8%AF-%D8%AF%D8%A7%D8%AE%D9%84/"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            // Site spells it تارا (with ت). Price is in the first row: name cell then
            // <td><span class="lastprice">۱,۹۶۲,۰۰۰,۰۰۰</span></td>
            val regex = Regex(
                ">تارا[^<]*</a></td>\\s*<td[^>]*>\\s*<span[^>]*class=\"lastprice\"[^>]*>([۰-۹0-9,]+)</span>"
            )
            val match = regex.find(html)
            val raw = match?.groupValues?.get(1) ?: run {
                val altRegex = Regex(">تارا[^<]*</a></td>.*?class=\"lastprice\">([۰-۹0-9,]+)<", RegexOption.DOT_MATCHES_ALL)
                altRegex.find(html)?.groupValues?.get(1)
            }
            raw?.let { toAsciiDigits(it).replace(",", "").toLongOrNull() }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatPrice(price: Long): String {
        val s = price.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count % 3 == 0 && i > 0) sb.append(',')
        }
        return sb.reverse().toString()
    }

    private fun toAsciiDigits(s: String): String = s
        .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
        .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')

    // ==================== Daily Food (locked per day) ====================

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return "${today.year}-${today.month}-${today.day}"
    }

    private fun loadFoodSuggestion() {
        lifecycleScope.launch {
            val prefs = CrownsNotifier.prefs(this@CrownsActivity)
            val today = todayKey()
            val lockedDate = prefs.getString(CrownsNotifier.KEY_TODAY_FOOD_DATE, "")
            if (lockedDate == today) {
                val lockedName = prefs.getString(CrownsNotifier.KEY_TODAY_FOOD, "")
                if (!lockedName.isNullOrBlank()) {
                    foodSuggestionText.text = lockedName
                    return@launch
                }
            }
            // Pick a random food and lock it for today
            val food = db.foodDao().getRandomFoodOnce()
            if (food != null) {
                prefs.edit()
                    .putString(CrownsNotifier.KEY_TODAY_FOOD_DATE, today)
                    .putString(CrownsNotifier.KEY_TODAY_FOOD, food.name)
                    .apply()
                foodSuggestionText.text = food.name
            } else {
                foodSuggestionText.text = "لیست غذاها خالیه — روی کارت بزن تا اضافه کنی"
            }
        }
    }

    private fun changeFoodSuggestion() {
        lifecycleScope.launch {
            val food = db.foodDao().getRandomFoodOnce()
            if (food != null) {
                val prefs = CrownsNotifier.prefs(this@CrownsActivity)
                prefs.edit()
                    .putString(CrownsNotifier.KEY_TODAY_FOOD_DATE, todayKey())
                    .putString(CrownsNotifier.KEY_TODAY_FOOD, food.name)
                    .apply()
                foodSuggestionText.text = food.name
            } else {
                foodSuggestionText.text = "لیست غذاها خالیه — روی کارت بزن تا اضافه کنی"
            }
        }
    }

    private fun showFoodListDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_food_list, null)
        val foodInput = dialogView.findViewById<TextInputEditText>(R.id.foodInput)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.foodListRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🍽 لیست غذاها")
            .setView(dialogView)
            .setPositiveButton("افزودن") { _, _ ->
                val name = foodInput.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.foodDao().insert(Food(name = name))
                        loadFoodSuggestion()
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()

        val adapter = FoodListAdapter(emptyList()) { food ->
            lifecycleScope.launch {
                db.foodDao().delete(food.id)
                refreshFoodList(recyclerView)
            }
        }
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            val foods = db.foodDao().getAllFoodsOnce()
            adapter.updateList(foods)
        }
    }

    private fun refreshFoodList(recyclerView: RecyclerView) {
        lifecycleScope.launch {
            val foods = db.foodDao().getAllFoodsOnce()
            (recyclerView.adapter as? FoodListAdapter)?.updateList(foods)
        }
    }

    class FoodListAdapter(
        private var foods: List<Food>,
        private val onDelete: (Food) -> Unit
    ) : RecyclerView.Adapter<FoodListAdapter.FoodViewHolder>() {

        fun updateList(newList: List<Food>) {
            foods = newList
            notifyDataSetChanged()
        }

        class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.foodNameText)
            val deleteBtn: ImageButton = view.findViewById(R.id.deleteFoodBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_food, parent, false)
            return FoodViewHolder(view)
        }

        override fun getItemCount() = foods.size

        override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
            val food = foods[position]
            holder.nameText.text = food.name
            holder.deleteBtn.setOnClickListener { onDelete(food) }
        }
    }

    // ==================== Class Schedule (from DB) ====================

    private fun seedScheduleIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = db.scheduleDao().getAllOnce()
            if (existing.isNotEmpty()) return@launch
            try {
                val inputStream = assets.open("weekly_schedule.json")
                val jsonStr = inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(jsonStr)
                val entries = mutableListOf<ScheduleEntry>()
                for (i in 0 until arr.length()) {
                    val day = arr.getJSONObject(i)
                    val dayKey = dayNameToKey(day.optString("day")) ?: continue
                    val classes = day.optJSONArray("classes") ?: continue
                    for (j in 0 until classes.length()) {
                        val cls = classes.getJSONObject(j)
                        entries.add(
                            ScheduleEntry(
                                dayKey = dayKey,
                                time = cls.optString("time"),
                                subject = cls.optString("subject"),
                                teacher = cls.optString("teacher")
                            )
                        )
                    }
                }
                db.scheduleDao().insertAll(entries)
            } catch (e: Exception) {
                // ignore — user can edit schedule manually
            }
        }
    }

    private fun dayNameToKey(name: String): String? = when (name) {
        "شنبه" -> "saturday"
        "یکشنبه" -> "sunday"
        "دوشنبه" -> "monday"
        "سه‌شنبه", "سه شنبه" -> "tuesday"
        "چهارشنبه" -> "wednesday"
        "پنج‌شنبه", "پنج شنبه" -> "thursday"
        "جمعه" -> "friday"
        else -> null
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            val cal = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            // 0=Saturday ... 6=Friday
            val todayWeekday = PersianCalendar.getPersianWeekDayIndex(today.year, today.month, today.day)
            val selectedWeekday = ((todayWeekday + dayOffset) % 7 + 7) % 7
            val selectedDate = PersianCalendar.addDays(today.year, today.month, today.day, dayOffset)
            val dayName = PersianCalendar.getPersianWeekDayNameForDate(selectedDate.year, selectedDate.month, selectedDate.day)

            val dayKeys = arrayOf(
                "saturday", "sunday", "monday", "tuesday",
                "wednesday", "thursday", "friday"
            )
            val classes = db.scheduleDao().getByDay(dayKeys[selectedWeekday])

            withContext(Dispatchers.Main) {
                scheduleTodayLabel.text = if (dayOffset == 0) {
                    "امروز ($dayName ${selectedDate.day} ${PersianCalendar.getPersianMonthName(selectedDate.month)})"
                } else {
                    "$dayName ${selectedDate.day} ${PersianCalendar.getPersianMonthName(selectedDate.month)}"
                }

                scheduleListContainer.removeAllViews()
                if (classes.isEmpty()) {
                    scheduleEmptyText.visibility = View.VISIBLE
                } else {
                    scheduleEmptyText.visibility = View.GONE
                    for (cls in classes) {
                        addScheduleRow(cls.time, cls.subject, cls.teacher)
                    }
                }
            }
        }
    }

    private fun addScheduleRow(time: String, subject: String, teacher: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }

        val timeView = TextView(this).apply {
            text = time
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.primary_variant, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subjectView = TextView(this).apply {
            text = subject
            textSize = 14f
            setTextColor(resources.getColor(R.color.on_surface, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        val teacherView = TextView(this).apply {
            text = if (teacher.isNotEmpty()) "👨‍🏫 $teacher" else ""
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        container.addView(timeView)
        container.addView(subjectView)
        container.addView(teacherView)
        scheduleListContainer.addView(container)
    }

    private fun showEditScheduleDialog() {
        val dayKeys = arrayOf(
            "saturday", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday"
        )
        val dayNames = arrayOf(
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
        )
        val selected = intArrayOf(0)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_schedule, null)
        val daySpinner = dialogView.findViewById<TextView>(R.id.scheduleDayName)
        val timeInput = dialogView.findViewById<EditText>(R.id.scheduleTimeInput)
        val subjectInput = dialogView.findViewById<EditText>(R.id.scheduleSubjectInput)
        val teacherInput = dialogView.findViewById<EditText>(R.id.scheduleTeacherInput)
        val classesContainer = dialogView.findViewById<LinearLayout>(R.id.scheduleClassesContainer)

        fun refreshDay() {
            daySpinner.text = dayNames[selected[0]]
            lifecycleScope.launch {
                val classes = db.scheduleDao().getByDay(dayKeys[selected[0]])
                classesContainer.removeAllViews()
                for (cls in classes) {
                    val row = LinearLayout(this@CrownsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 4, 0, 4)
                    }
                    val info = TextView(this@CrownsActivity).apply {
                        text = "${cls.time} — ${cls.subject}" +
                            (if (cls.teacher.isNotEmpty()) " (${cls.teacher})" else "")
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val deleteBtn = ImageButton(this@CrownsActivity).apply {
                        setImageResource(android.R.drawable.ic_menu_delete)
                        background = null
                        setOnClickListener {
                            lifecycleScope.launch {
                                db.scheduleDao().deleteById(cls.id)
                                refreshDay()
                            }
                        }
                    }
                    row.addView(info)
                    row.addView(deleteBtn)
                    classesContainer.addView(row)
                }
            }
        }

        // Use the dialog's own day navigation
        val prevBtn = dialogView.findViewById<ImageButton>(R.id.schedulePrevDay)
        val nextBtn = dialogView.findViewById<ImageButton>(R.id.scheduleNextDay)
        prevBtn.setOnClickListener {
            selected[0] = (selected[0] + 6) % 7
            refreshDay()
        }
        nextBtn.setOnClickListener {
            selected[0] = (selected[0] + 1) % 7
            refreshDay()
        }

        refreshDay()

        AlertDialog.Builder(this)
            .setTitle("✏️ تغییر برنامه کلاسی")
            .setView(dialogView)
            .setPositiveButton("افزودن کلاس") { _, _ ->
                val time = timeInput.text?.toString()?.trim().orEmpty()
                val subject = subjectInput.text?.toString()?.trim().orEmpty()
                if (subject.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.scheduleDao().insert(
                            ScheduleEntry(
                                dayKey = dayKeys[selected[0]],
                                time = time,
                                subject = subject,
                                teacher = teacherInput.text?.toString()?.trim().orEmpty()
                            )
                        )
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    // ==================== Notification Toggles ====================

    private fun setupNotifSwitches() {
        val prefs = CrownsNotifier.prefs(this)

        findViewById<SwitchMaterial>(R.id.foodNotifSwitch).apply {
            isChecked = prefs.getBoolean(CrownsNotifier.KEY_FOOD, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(CrownsNotifier.KEY_FOOD, checked).apply()
            }
        }
        findViewById<SwitchMaterial>(R.id.priceNotifSwitch).apply {
            isChecked = prefs.getBoolean(CrownsNotifier.KEY_PRICE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(CrownsNotifier.KEY_PRICE, checked).apply()
            }
        }
        findViewById<SwitchMaterial>(R.id.scheduleNotifSwitch).apply {
            isChecked = prefs.getBoolean(CrownsNotifier.KEY_SCHEDULE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(CrownsNotifier.KEY_SCHEDULE, checked).apply()
            }
        }
    }
}

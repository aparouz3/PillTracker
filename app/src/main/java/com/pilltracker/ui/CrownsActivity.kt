package com.pilltracker.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Food
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    // Food views
    private lateinit var foodSuggestionText: TextView
    private lateinit var refreshFoodBtn: Button

    // Schedule
    private lateinit var scheduleTodayLabel: TextView
    private lateinit var scheduleListContainer: ViewGroup
    private lateinit var scheduleEmptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crowns)

        db = (application as PillTrackerApp).database

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        goldProgress = findViewById(R.id.goldProgress)
        goldPriceText = findViewById(R.id.goldPriceText)
        goldUpdateTime = findViewById(R.id.goldUpdateTime)

        foodSuggestionText = findViewById(R.id.foodSuggestionText)
        refreshFoodBtn = findViewById(R.id.refreshFoodBtn)

        scheduleTodayLabel = findViewById(R.id.scheduleTodayLabel)
        scheduleListContainer = findViewById(R.id.scheduleListContainer)
        scheduleEmptyText = findViewById(R.id.scheduleEmptyText)

        // Load gold price
        loadGoldPrice()

        // Load food suggestion
        loadFoodSuggestion()
        refreshFoodBtn.setOnClickListener { loadFoodSuggestion() }
        findViewById<View>(R.id.foodCard).setOnClickListener { showFoodListDialog() }

        // Load class schedule
        loadSchedule()
    }

    // ---- Gold Price ----
    private fun loadGoldPrice() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { goldProgress.visibility = View.VISIBLE }
                val price = fetchGoldPrice()
                withContext(Dispatchers.Main) {
                    goldProgress.visibility = View.GONE
                    if (price != null) {
                        goldPriceText.text = price
                        val now = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.US).format(Date())
                        goldUpdateTime.text = "آخرین به‌روزرسانی: $now"
                    } else {
                        goldPriceText.text = "قیمت در دسترس نیست"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    goldProgress.visibility = View.GONE
                    goldPriceText.text = "خطا در دریافت قیمت"
                }
            }
        }
    }

    private fun fetchGoldPrice(): String? {
        return try {
            val url = "https://www.iranjib.ir/showgroup/45/%D9%82%DB%8C%D9%85%D8%AA-%D8%AE%D9%88%D8%AF%D8%B1%D9%88-%D8%AA%D9%88%D9%84%DB%8C%D8%AF-%D8%AF%D8%A7%D8%AE%D9%84/"
            val html = URL(url).readText()
            // Look for "تارا" and "دستی" + "V1" / "نسخه 1" patterns
            val regex = Regex("تارا[^<]*دستی[^<]*V1[^<]*</td>\\s*<td[^>]*>([^<]+)</td>", RegexOption.IGNORE_CASE)
            val match = regex.find(html)
            if (match != null) {
                "تارا دستی V1: ${match.groupValues[1].trim()}"
            } else {
                // Fallback: try to find price near "تارا"
                val altRegex = Regex("تارا[^<]*</td>\\s*<td[^>]*>([^<]+)</td>\\s*<td[^>]*>([^<]+)</td>", RegexOption.IGNORE_CASE)
                val altMatch = altRegex.find(html)
                if (altMatch != null) {
                    "تارا: ${altMatch.groupValues[1].trim()} / ${altMatch.groupValues[2].trim()}"
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Food Suggestion ----
    private fun loadFoodSuggestion() {
        lifecycleScope.launch {
            val food = db.foodDao().getRandomFoodOnce()
            foodSuggestionText.text = food?.name ?: "لیست غذاها خالیه — روی کارت بزن تا اضافه کنی"
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

        // Load initial list
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

    // ---- Class Schedule ----
    private fun loadSchedule() {
        lifecycleScope.launch {
            // Determine today's Persian day-of-week
            val cal = Calendar.getInstance()
            val today = PersianCalendar.gregorianToPersian(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            // PersianCalendar week starts on Saturday=1 ... Friday=7
            val dayOfWeek = PersianCalendar.getPersianWeekDayIndex(today.year, today.month, today.day)
            val dayName = PersianCalendar.getPersianWeekDayNameForDate(today.year, today.month, today.day)

            // Read schedule JSON from assets
            val json = readScheduleJson()
            val dayClasses = json?.optJSONArray(getDayKey(dayOfWeek)) ?: JSONArray()

            withContext(Dispatchers.Main) {
                scheduleTodayLabel.text = "امروز ($dayName ${today.day} ${PersianCalendar.getPersianMonthName(today.month)})"

                scheduleListContainer.removeAllViews()
                if (dayClasses.length() == 0) {
                    scheduleEmptyText.visibility = View.VISIBLE
                } else {
                    scheduleEmptyText.visibility = View.GONE
                    for (i in 0 until dayClasses.length()) {
                        val cls = dayClasses.getJSONObject(i)
                        addScheduleRow(cls.optString("time"), cls.optString("subject"), cls.optString("teacher"))
                    }
                }
            }
        }
    }

    private fun addScheduleRow(time: String, subject: String, teacher: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_day_strip, null)
        // Reuse a simple layout: we create custom views instead
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

    private fun getDayKey(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "saturday"
            2 -> "sunday"
            3 -> "monday"
            4 -> "tuesday"
            5 -> "wednesday"
            6 -> "thursday"
            7 -> "friday"
            else -> "saturday"
        }
    }

    private fun readScheduleJson(): JSONObject? {
        return try {
            val inputStream = assets.open("weekly_schedule.json")
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            // The JSON is an array; convert to object keyed by day name
            val arr = JSONArray(jsonStr)
            val obj = JSONObject()
            for (i in 0 until arr.length()) {
                val day = arr.getJSONObject(i)
                val key = when (day.optString("day")) {
                    "شنبه" -> "saturday"
                    "یکشنبه" -> "sunday"
                    "دوشنبه" -> "monday"
                    "سه‌شنبه", "سه شنبه" -> "tuesday"
                    "چهارشنبه" -> "wednesday"
                    "پنج‌شنبه", "پنج شنبه" -> "thursday"
                    "جمعه" -> "friday"
                    else -> continue
                }
                obj.put(key, day.optJSONArray("classes") ?: JSONArray())
            }
            obj
        } catch (e: Exception) {
            null
        }
    }
}
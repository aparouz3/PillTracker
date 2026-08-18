package com.pilltracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.pilltracker.R
import com.pilltracker.util.PersianCalendar
import java.util.Calendar

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarGrid: GridLayout
    private lateinit var monthYearText: TextView
    private lateinit var prevMonthBtn: MaterialButton
    private lateinit var nextMonthBtn: MaterialButton

    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        prevMonthBtn = findViewById(R.id.prevMonthBtn)
        nextMonthBtn = findViewById(R.id.nextMonthBtn)

        val cal = Calendar.getInstance()
        val today = PersianCalendar.gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        currentYear = today.year
        currentMonth = today.month

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        prevMonthBtn.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentYear--
                currentMonth = 12
            }
            buildCalendar()
        }

        nextMonthBtn.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentYear++
                currentMonth = 1
            }
            buildCalendar()
        }

        buildCalendar()
    }

    private fun buildCalendar() {
        calendarGrid.removeAllViews()
        val daysInMonth = PersianCalendar.getPersianMonthDays(currentYear, currentMonth)

        val greg = PersianCalendar.persianToGregorian(currentYear, currentMonth, 1)
        val cal = Calendar.getInstance()
        cal.set(greg.first, greg.second - 1, greg.third)
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) % 7 // 0=Saturday .. 6=Friday

        val todayCal = Calendar.getInstance()
        val todayPersian = PersianCalendar.gregorianToPersian(
            todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH)
        )

        monthYearText.text = "${PersianCalendar.getPersianMonthName(currentMonth)} $currentYear"

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
            if (isToday) {
                tv.setBackgroundResource(R.drawable.calendar_today_bg)
            }

            tv.setOnClickListener { v ->
                val result = Intent()
                result.putExtra("year", currentYear)
                result.putExtra("month", currentMonth)
                result.putExtra("day", day)
                setResult(RESULT_OK, result)
                finish()
            }
            calendarGrid.addView(tv)
        }
    }
}
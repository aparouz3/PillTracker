package com.pilltracker.util

/**
 * Persian (Jalali/Solar Hijri) calendar converter.
 * Converts between Gregorian and Persian dates.
 */
object PersianCalendar {

    private val persianMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val persianWeekDays = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    fun getPersianMonthName(month: Int): String = persianMonthNames.getOrElse(month - 1) { "?" }

    fun getPersianWeekDayName(dayOfWeek: Int): String {
        // Java: 1=Monday ... 7=Sunday → Persian: 0=Saturday ... 6=Friday
        val index = (dayOfWeek + 4) % 7
        return persianWeekDays[index]
    }

    data class PersianDate(
        val year: Int,
        val month: Int,
        val day: Int
    ) {
        val monthName: String get() = getPersianMonthName(month)
        val dayName: String get() = ""

        fun toDisplayString(): String = "$day $monthName $year"
    }

    /**
     * Convert Gregorian to Persian date.
     * Algorithm based on the Persian calendar.
     */
    fun gregorianToPersian(gYear: Int, gMonth: Int, gDay: Int): PersianDate {
        val array = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val leap: Boolean
        var gd: Int
        var gm: Int
        var gy: Int
        var jm: Int
        var jy: Int
        var jd: Int

        gy = gYear - 1600
        gm = gMonth - 1
        gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += array[i]
        }
        if (gm > 1 && (gy % 4 == 0 && gy % 100 != 0 || gy % 400 == 0)) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = (jDayNo / 12053).toInt()
        jDayNo %= 12053
        jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        jm = 0
        while (jm < 11 && jDayNo >= if (jm < 6) 31 else 30) {
            jDayNo -= if (jm < 6) 31 else 30
            jm++
        }
        jm++
        jd = jDayNo + 1

        return PersianDate(jy, jm, jd)
    }

    /**
     * Convert Persian to Gregorian date.
     */
    fun persianToGregorian(pYear: Int, pMonth: Int, pDay: Int): Triple<Int, Int, Int> {
        var gy: Int
        var gm: Int
        var gd: Int
        var jy: Int

        jy = pYear - 979
        var jMonth = pMonth - 1
        var jDay = pDay - 1

        var jDayNo = 365 * jy + (jy / 33) * 8 + (jy % 33 + 3) / 4
        for (i in 0 until jMonth) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jDay

        var gDayNo = jDayNo + 79
        gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo = gDayNo % 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        gm = 0
        val monthDays = if (leap) {
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        } else {
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        }
        for (i in monthDays.indices) {
            if (gDayNo < monthDays[i]) {
                gm = i + 1
                break
            }
            gDayNo -= monthDays[i]
        }
        gd = gDayNo + 1
        return Triple(gy, gm, gd)
    }

    /**
     * Get the number of days in a Persian month.
     */
    fun getPersianMonthDays(year: Int, month: Int): Int {
        if (month <= 6) return 31
        if (month <= 11) return 30
        // Month 12 (Esfand) - can be 29 or 30
        return if (isPersianLeapYear(year)) 30 else 29
    }

    /**
     * Check if a Persian year is a leap year.
     */
    fun isPersianLeapYear(year: Int): Boolean {
        val base = year - 979
        val remainder = (base % 33) + 3
        return remainder >= 33 || remainder >= 4
    }
}
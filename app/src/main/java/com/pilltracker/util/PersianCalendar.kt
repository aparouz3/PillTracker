package com.pilltracker.util

/**
 * Persian (Jalali/Solar Hijri) calendar converter.
 * Uses the standard Birashk algorithm (jalaali-js compatible).
 * Valid for Persian years -61 to 3177 (roughly 561 to 3798 Gregorian).
 */
object PersianCalendar {

    private val persianMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val persianWeekDays = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    fun getPersianMonthName(month: Int): String = persianMonthNames.getOrElse(month - 1) { "?" }

    fun getPersianWeekDayName(dayOfWeek: Int): String {
        // Java: 1=Sunday ... 7=Saturday → Persian: 0=Saturday ... 6=Friday
        val index = dayOfWeek % 7
        return persianWeekDays[index]
    }

    data class PersianDate(
        val year: Int,
        val month: Int,
        val day: Int
    ) {
        val monthName: String get() = getPersianMonthName(month)
        fun toDisplayString(): String = "$day $monthName $year"
    }

    // ---- helpers (integer division/modulo matching JS semantics) ----
    private fun div(a: Int, b: Int): Int = a / b

    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

    private fun jalCal(jy: Int): IntArray {
        // returns [leap, gy, march]
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0

        require(jy >= breaks[0] && jy < breaks[breaks.size - 1]) { "Invalid Jalaali year $jy" }

        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ = leapJ + div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp

        // Find the number of leap years from AD 621 to the beginning of the current year
        leapJ = leapJ + div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) {
            leapJ += 1
        }

        // And the same in the Gregorian calendar (until the year gy)
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150

        // Determine the Gregorian date of Farvardin the 1st
        val march = 20 + leapJ - leapG

        // Find how many years have passed since the last leap year
        if (jump - n < 6) {
            n = n - jump + div(jump + 4, 33) * 33
        }
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) {
            leap = 4
        }
        return intArrayOf(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): IntArray {
        val j = 4 * jdn + 139361631 + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return intArrayOf(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r[1], 3, r[2]) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): IntArray {
        val gy = d2g(jdn)[0]
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r[2])
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                val jm = div(k, 31) + 1
                val jd = mod(k, 31) + 1
                return intArrayOf(jy, jm, jd)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            k = if (r[0] == 1) k + 1 else k
        }
        val jm = 7 + div(k, 30)
        val jd = mod(k, 30) + 1
        return intArrayOf(jy, jm, jd)
    }

    /**
     * Convert Gregorian to Persian date.
     */
    fun gregorianToPersian(gYear: Int, gMonth: Int, gDay: Int): PersianDate {
        val j = d2j(g2d(gYear, gMonth, gDay))
        return PersianDate(j[0], j[1], j[2])
    }

    /**
     * Convert Persian to Gregorian date.
     */
    fun persianToGregorian(pYear: Int, pMonth: Int, pDay: Int): Triple<Int, Int, Int> {
        val g = d2g(j2d(pYear, pMonth, pDay))
        return Triple(g[0], g[1], g[2])
    }

    /**
     * Get the number of days in a Persian month.
     */
    fun getPersianMonthDays(year: Int, month: Int): Int {
        if (month <= 6) return 31
        if (month <= 11) return 30
        // Month 12 (Esfand) - 29 or 30 depending on leap year
        return if (isPersianLeapYear(year)) 30 else 29
    }

    /**
     * Check if a Persian year is a leap year.
     */
    fun isPersianLeapYear(year: Int): Boolean {
        return jalCal(year)[0] == 0
    }

    /**
     * Add/subtract days to a Persian date and return the resulting Persian date.
     */
    fun addDays(year: Int, month: Int, day: Int, days: Int): PersianDate {
        val g = persianToGregorian(year, month, day)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(g.first, g.second - 1, g.third)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return gregorianToPersian(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Persian weekday name for a specific Persian date.
     */
    fun getPersianWeekDayNameForDate(year: Int, month: Int, day: Int): String {
        val g = persianToGregorian(year, month, day)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(g.first, g.second - 1, g.third)
        return getPersianWeekDayName(cal.get(Calendar.DAY_OF_WEEK))
    }

    /**
     * Gregorian timestamp (millis) at 00:00 of the given Persian date.
     */
    fun persianDateToTimestamp(year: Int, month: Int, day: Int): Long {
        val g = persianToGregorian(year, month, day)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(g.first, g.second - 1, g.third)
        return cal.timeInMillis
    }

    /**
     * Start of the current Persian week (Saturday 00:00) in millis.
     */
    fun getWeekStartTimestamp(): Long {
        val now = Calendar.getInstance()
        val dow = now.get(Calendar.DAY_OF_WEEK) // 1=Sunday .. 7=Saturday
        val persianIndex = dow % 7 // 0=Saturday .. 6=Friday
        val cal = now.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -persianIndex)
        return cal.timeInMillis
    }
}
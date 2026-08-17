package com.pilltracker.util

import android.content.Context
import java.text.NumberFormat
import java.util.Locale

object FormatUtils {
    private val numberFormat = NumberFormat.getInstance(Locale.US)

    fun formatAmount(amount: Long): String {
        return numberFormat.format(amount)
    }

    fun formatAmountWithUnit(amount: Long): String {
        return "${formatAmount(amount)} تومان"
    }
}
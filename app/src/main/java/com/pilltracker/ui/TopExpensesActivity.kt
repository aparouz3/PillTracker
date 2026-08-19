package com.pilltracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.pilltracker.R

/**
 * Menu page for "بیشترین تراکنش‌ها": links to weekly / monthly / yearly pages.
 */
class TopExpensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_expenses)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<View>(R.id.weeklyCard).setOnClickListener { open("week") }
        findViewById<View>(R.id.monthlyCard).setOnClickListener { open("month") }
        findViewById<View>(R.id.yearlyCard).setOnClickListener { open("year") }
    }

    private fun open(period: String) {
        startActivity(
            Intent(this, PeriodExpensesActivity::class.java)
                .putExtra(PeriodExpensesActivity.EXTRA_PERIOD, period)
        )
    }
}

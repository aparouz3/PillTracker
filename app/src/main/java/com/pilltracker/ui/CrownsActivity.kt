package com.pilltracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pilltracker.R

/**
 * «کرون‌ها» — daily update feed.
 * List of daily updates will be populated here (placeholder for now).
 */
class CrownsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crowns)

        findViewById<android.widget.ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }
    }
}

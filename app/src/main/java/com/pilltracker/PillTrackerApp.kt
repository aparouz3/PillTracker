package com.pilltracker

import android.app.Application
import com.pilltracker.data.PillTrackerDatabase

class PillTrackerApp : Application() {
    val database: PillTrackerDatabase by lazy {
        PillTrackerDatabase.getDatabase(this)
    }
}
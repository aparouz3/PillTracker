package com.pilltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transaction::class], version = 1, exportSchema = false)
abstract class PillTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: PillTrackerDatabase? = null

        fun getDatabase(context: Context): PillTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PillTrackerDatabase::class.java,
                    "pilltracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
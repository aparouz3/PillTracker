package com.pilltracker.util

import com.pilltracker.data.Category
import com.pilltracker.data.DailyNote
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Shared backup logic: builds/parses the JSON backup format.
 * Format v3: { version, exportedAt, transactions: [...], notes: [...], categories: [...] }
 */
object BackupUtils {

    fun buildBackupJson(
        transactions: List<Transaction>,
        notes: List<DailyNote>,
        categories: List<Category>
    ): String {
        return JSONObject().apply {
            put("version", 3)
            put("exportedAt", System.currentTimeMillis())
            put("transactions", JSONArray().apply {
                for (t in transactions) {
                    put(
                        JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("amount", t.amount)
                            put("type", t.type.name)
                            put("year", t.year)
                            put("month", t.month)
                            put("day", t.day)
                            if (t.categoryId != null) put("categoryId", t.categoryId)
                            put("timestamp", t.timestamp)
                        }
                    )
                }
            })
            put("notes", JSONArray().apply {
                for (n in notes) {
                    put(
                        JSONObject().apply {
                            put("id", n.id)
                            put("year", n.year)
                            put("month", n.month)
                            put("day", n.day)
                            put("text", n.text)
                            put("timestamp", n.timestamp)
                        }
                    )
                }
            })
            put("categories", JSONArray().apply {
                for (c in categories) {
                    put(
                        JSONObject().apply {
                            put("id", c.id)
                            put("name", c.name)
                        }
                    )
                }
            })
        }.toString(2)
    }

    fun writeToStream(
        transactions: List<Transaction>,
        notes: List<DailyNote>,
        categories: List<Category>,
        out: OutputStream
    ) {
        out.write(buildBackupJson(transactions, notes, categories).toByteArray(Charsets.UTF_8))
    }

    data class BackupData(
        val transactions: List<Transaction>,
        val notes: List<DailyNote>,
        val categories: List<Category>
    )

    fun parseBackup(text: String): BackupData {
        val json = JSONObject(text)
        val tArr = json.getJSONArray("transactions")
        val transactions = mutableListOf<Transaction>()
        for (i in 0 until tArr.length()) {
            val o = tArr.getJSONObject(i)
            transactions.add(
                Transaction(
                    id = o.optLong("id", 0),
                    title = o.optString("title", ""),
                    amount = o.optLong("amount", 0),
                    type = if (o.optString("type") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                    year = o.optInt("year", 0),
                    month = o.optInt("month", 0),
                    day = o.optInt("day", 0),
                    categoryId = if (o.has("categoryId")) o.optLong("categoryId") else null,
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        val notes = mutableListOf<DailyNote>()
        if (json.has("notes")) {
            val nArr = json.getJSONArray("notes")
            for (i in 0 until nArr.length()) {
                val o = nArr.getJSONObject(i)
                notes.add(
                    DailyNote(
                        id = o.optLong("id", 0),
                        year = o.optInt("year", 0),
                        month = o.optInt("month", 0),
                        day = o.optInt("day", 0),
                        text = o.optString("text", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        val categories = mutableListOf<Category>()
        if (json.has("categories")) {
            val cArr = json.getJSONArray("categories")
            for (i in 0 until cArr.length()) {
                val o = cArr.getJSONObject(i)
                categories.add(
                    Category(
                        id = o.optLong("id", 0),
                        name = o.optString("name", "")
                    )
                )
            }
        }
        return BackupData(transactions, notes, categories)
    }
}
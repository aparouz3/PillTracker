package com.pilltracker.util

import com.pilltracker.data.Category
import com.pilltracker.data.DailyNote
import com.pilltracker.data.Food
import com.pilltracker.data.PriceHistory
import com.pilltracker.data.RecurringTransaction
import com.pilltracker.data.ScheduleEntry
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Shared backup logic: builds/parses the JSON backup format.
 * Format v6: { version, exportedAt, transactions: [...], notes: [...], categories: [...],
 *             folders: [...], foods: [...], schedule: [...], priceHistory: [...],
 *             recurring: [...] }
 */
object BackupUtils {

    fun buildBackupJson(
        transactions: List<Transaction>,
        notes: List<DailyNote>,
        categories: List<Category>,
        folders: List<com.pilltracker.data.Folder>,
        foods: List<Food> = emptyList(),
        schedule: List<ScheduleEntry> = emptyList(),
        priceHistory: List<PriceHistory> = emptyList(),
        recurring: List<RecurringTransaction> = emptyList()
    ): String {
        return JSONObject().apply {
            put("version", 6)
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
                            if (t.folderId != null) put("folderId", t.folderId)
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
            put("folders", JSONArray().apply {
                for (f in folders) {
                    put(
                        JSONObject().apply {
                            put("id", f.id)
                            put("name", f.name)
                            put("year", f.year)
                            put("month", f.month)
                            put("day", f.day)
                        }
                    )
                }
            })
            put("foods", JSONArray().apply {
                for (f in foods) {
                    put(
                        JSONObject().apply {
                            put("id", f.id)
                            put("name", f.name)
                        }
                    )
                }
            })
            put("schedule", JSONArray().apply {
                for (s in schedule) {
                    put(
                        JSONObject().apply {
                            put("id", s.id)
                            put("dayKey", s.dayKey)
                            put("time", s.time)
                            put("subject", s.subject)
                            put("teacher", s.teacher)
                        }
                    )
                }
            })
            put("priceHistory", JSONArray().apply {
                for (p in priceHistory) {
                    put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("dateKey", p.dateKey)
                            put("price", p.price)
                            put("timestamp", p.timestamp)
                        }
                    )
                }
            })
            put("recurring", JSONArray().apply {
                for (r in recurring) {
                    put(
                        JSONObject().apply {
                            put("id", r.id)
                            put("title", r.title)
                            put("amount", r.amount)
                            put("type", r.type.name)
                            if (r.categoryId != null) put("categoryId", r.categoryId)
                            put("intervalDays", r.intervalDays)
                            put("anchorYear", r.anchorYear)
                            put("anchorMonth", r.anchorMonth)
                            put("anchorDay", r.anchorDay)
                            put("active", r.active)
                            put("nextYear", r.nextYear)
                            put("nextMonth", r.nextMonth)
                            put("nextDay", r.nextDay)
                            put("createdAt", r.createdAt)
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
        folders: List<com.pilltracker.data.Folder>,
        foods: List<Food> = emptyList(),
        schedule: List<ScheduleEntry> = emptyList(),
        priceHistory: List<PriceHistory> = emptyList(),
        recurring: List<RecurringTransaction> = emptyList(),
        out: OutputStream
    ) {
        out.write(
            buildBackupJson(transactions, notes, categories, folders, foods, schedule, priceHistory, recurring)
                .toByteArray(Charsets.UTF_8)
        )
    }

    data class BackupData(
        val transactions: List<Transaction>,
        val notes: List<DailyNote>,
        val categories: List<Category>,
        val folders: List<com.pilltracker.data.Folder>,
        val foods: List<Food> = emptyList(),
        val schedule: List<ScheduleEntry> = emptyList(),
        val priceHistory: List<PriceHistory> = emptyList(),
        val recurring: List<RecurringTransaction> = emptyList()
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
                    folderId = if (o.has("folderId")) o.optLong("folderId") else null,
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
        val folders = mutableListOf<com.pilltracker.data.Folder>()
        if (json.has("folders")) {
            val fArr = json.getJSONArray("folders")
            for (i in 0 until fArr.length()) {
                val o = fArr.getJSONObject(i)
                folders.add(
                    com.pilltracker.data.Folder(
                        id = o.optLong("id", 0),
                        name = o.optString("name", ""),
                        year = o.optInt("year", 0),
                        month = o.optInt("month", 0),
                        day = o.optInt("day", 0)
                    )
                )
            }
        }
        val foods = mutableListOf<Food>()
        if (json.has("foods")) {
            val fArr = json.getJSONArray("foods")
            for (i in 0 until fArr.length()) {
                val o = fArr.getJSONObject(i)
                foods.add(
                    Food(
                        id = o.optLong("id", 0),
                        name = o.optString("name", "")
                    )
                )
            }
        }
        val schedule = mutableListOf<ScheduleEntry>()
        if (json.has("schedule")) {
            val sArr = json.getJSONArray("schedule")
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                schedule.add(
                    ScheduleEntry(
                        id = o.optLong("id", 0),
                        dayKey = o.optString("dayKey", ""),
                        time = o.optString("time", ""),
                        subject = o.optString("subject", ""),
                        teacher = o.optString("teacher", "")
                    )
                )
            }
        }
        val priceHistory = mutableListOf<PriceHistory>()
        if (json.has("priceHistory")) {
            val pArr = json.getJSONArray("priceHistory")
            for (i in 0 until pArr.length()) {
                val o = pArr.getJSONObject(i)
                priceHistory.add(
                    PriceHistory(
                        id = o.optLong("id", 0),
                        dateKey = o.optString("dateKey", ""),
                        price = o.optLong("price", 0),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        val recurring = mutableListOf<RecurringTransaction>()
        if (json.has("recurring")) {
            val rArr = json.getJSONArray("recurring")
            for (i in 0 until rArr.length()) {
                val o = rArr.getJSONObject(i)
                recurring.add(
                    RecurringTransaction(
                        id = o.optLong("id", 0),
                        title = o.optString("title", ""),
                        amount = o.optLong("amount", 0),
                        type = if (o.optString("type") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                        categoryId = if (o.has("categoryId")) o.optLong("categoryId") else null,
                        intervalDays = o.optInt("intervalDays", 30),
                        anchorYear = o.optInt("anchorYear", 0),
                        anchorMonth = o.optInt("anchorMonth", 0),
                        anchorDay = o.optInt("anchorDay", 0),
                        active = o.optBoolean("active", true),
                        nextYear = o.optInt("nextYear", 0),
                        nextMonth = o.optInt("nextMonth", 0),
                        nextDay = o.optInt("nextDay", 0),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        return BackupData(transactions, notes, categories, folders, foods, schedule, priceHistory, recurring)
    }
}

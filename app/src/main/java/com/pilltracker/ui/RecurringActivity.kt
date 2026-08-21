package com.pilltracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.RecurringTransaction
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.launch

class RecurringActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val items = mutableListOf<RecurringTransaction>()
    private val categoryNames = mutableMapOf<Long, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recurring)

        db = (application as PillTrackerApp).database
        recyclerView = findViewById(R.id.recurringList)
        emptyText = findViewById(R.id.recurringEmptyText)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RecurringAdapter()

        lifecycleScope.launch {
            categoryNames.putAll(db.categoryDao().getAllCategoriesOnce().associate { it.id to it.name })
            load()
        }
    }

    private fun load() {
        lifecycleScope.launch {
            items.clear()
            items.addAll(db.recurringDao().getAll())
            recyclerView.adapter?.notifyDataSetChanged()
            emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    inner class RecurringAdapter : RecyclerView.Adapter<RecurringAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.recurringTitle)
            val amount: TextView = view.findViewById(R.id.recurringAmount)
            val meta: TextView = view.findViewById(R.id.recurringMeta)
            val deleteBtn: ImageButton = view.findViewById(R.id.deleteRecurringBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_recurring, parent, false)
            )
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val r = items[position]
            holder.title.text = if (r.active) r.title else "${r.title} (غیرفعال)"
            holder.amount.text = FormatUtils.formatAmountWithUnit(r.amount)
            holder.amount.setTextColor(
                resources.getColor(
                    if (r.type == TransactionType.INCOME) R.color.income_green else R.color.expense_red,
                    null
                )
            )
            val intervalStr = if (r.intervalDays == 30) "ماهانه" else "هر ${r.intervalDays} روز"
            val cat = r.categoryId?.let { categoryNames[it] }
            val next = "${r.nextDay} ${PersianCalendar.getPersianMonthName(r.nextMonth)} ${r.nextYear}"
            holder.meta.text = "$intervalStr • بعدی: $next" + (if (cat != null) " • 🏷 $cat" else "")

            holder.deleteBtn.setOnClickListener {
                MaterialAlertDialogBuilder(this@RecurringActivity)
                    .setTitle("حذف تراکنش تکراری")
                    .setMessage("«${r.title}» دیگر به صورت خودکار ثبت نمی‌شود.")
                    .setPositiveButton("حذف") { _, _ ->
                        lifecycleScope.launch {
                            db.recurringDao().delete(r)
                            load()
                        }
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
        }
    }
}
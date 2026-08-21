package com.pilltracker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pilltracker.PillTrackerApp
import com.pilltracker.R
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType
import com.pilltracker.util.FormatUtils
import com.pilltracker.util.PersianCalendar
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val results = mutableListOf<Transaction>()
    private val categoryNames = mutableMapOf<Long, String>()
    private var resultJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        db = (application as PillTrackerApp).database
        searchInput = findViewById(R.id.searchInput)
        recyclerView = findViewById(R.id.searchResults)
        emptyText = findViewById(R.id.searchEmptyText)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ResultAdapter()

        lifecycleScope.launch {
            categoryNames.putAll(db.categoryDao().getAllCategoriesOnce().associate { it.id to it.name })
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runSearch(s?.toString()?.trim().orEmpty())
            }
        })
    }

    private fun runSearch(query: String) {
        resultJob?.cancel()
        if (query.isEmpty()) {
            results.clear()
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "عبارت جستجو را وارد کنید"
            return
        }
        resultJob = lifecycleScope.launch {
            results.clear()
            results.addAll(db.transactionDao().searchOnce("%$query%"))
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
            emptyText.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            if (results.isEmpty()) emptyText.text = "نتیجه‌ای پیدا نشد"
        }
    }

    inner class ResultAdapter : RecyclerView.Adapter<ResultAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.searchResultTitle)
            val amount: TextView = view.findViewById(R.id.searchResultAmount)
            val meta: TextView = view.findViewById(R.id.searchResultMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
            )
        }

        override fun getItemCount() = results.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tx = results[position]
            holder.title.text = tx.title
            holder.amount.text = FormatUtils.formatAmountWithUnit(tx.amount)
            holder.amount.setTextColor(
                resources.getColor(
                    if (tx.type == TransactionType.INCOME) R.color.income_green else R.color.expense_red,
                    null
                )
            )
            val date = "${tx.day} ${PersianCalendar.getPersianMonthName(tx.month)} ${tx.year}"
            val cat = tx.categoryId?.let { categoryNames[it] }
            holder.meta.text = if (cat != null) "$date — 🏷 $cat" else date

            holder.itemView.setOnClickListener {
                // Return to MainActivity with the date of this transaction
                setResult(RESULT_OK, android.content.Intent().apply {
                    putExtra("year", tx.year)
                    putExtra("month", tx.month)
                    putExtra("day", tx.day)
                })
                finish()
            }
        }
    }
}
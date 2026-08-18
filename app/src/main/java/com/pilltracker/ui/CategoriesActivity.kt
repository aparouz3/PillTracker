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
import com.pilltracker.data.Category
import com.pilltracker.util.FormatUtils
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategoriesActivity : AppCompatActivity() {

    private lateinit var db: com.pilltracker.data.PillTrackerDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val categories = mutableListOf<Category>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        db = (application as PillTrackerApp).database

        recyclerView = findViewById(R.id.categoryList)
        emptyText = findViewById(R.id.emptyText)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.addCategoryBtn).setOnClickListener { showAddCategoryDialog() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = CategoryAdapter()

        loadCategories()
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            categories.clear()
            categories.addAll(db.categoryDao().getAllCategoriesOnce())
            render()
        }
    }

    private fun render() {
        emptyText.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun showAddCategoryDialog() {
        val input = TextInputEditText(this)
        input.hint = "نام پوشه (مثلاً: خوراکی، حمل‌ونقل، قسط)"
        input.setPadding(24, 24, 24, 24)
        MaterialAlertDialogBuilder(this)
            .setTitle("پوشه جدید")
            .setView(input)
            .setPositiveButton("ساخت") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.categoryDao().insert(Category(name = name))
                        loadCategories()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun deleteCategory(category: Category) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف پوشه «${category.name}»")
            .setMessage("تراکنش‌های داخل این پوشه حذف نمی‌شوند؛ فقط از پوشه خارج می‌شوند.")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    db.categoryDao().delete(category.id)
                    // Unlink transactions from this category
                    val all = db.transactionDao().getAllTransactionsOnce()
                    for (t in all) {
                        if (t.categoryId == category.id) {
                            db.transactionDao().update(t.copy(categoryId = null))
                        }
                    }
                    loadCategories()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    inner class CategoryAdapter : RecyclerView.Adapter<CategoryAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.categoryName)
            val count: TextView = view.findViewById(R.id.categoryCount)
            val expenseTotal: TextView = view.findViewById(R.id.categoryExpenseTotal)
            val incomeTotal: TextView = view.findViewById(R.id.categoryIncomeTotal)
            val deleteBtn: ImageButton = view.findViewById(R.id.deleteCategoryBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            )
        }

        override fun getItemCount() = categories.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val category = categories[position]
            holder.name.text = category.name

            holder.deleteBtn.setOnClickListener { deleteCategory(category) }

            holder.itemView.setOnClickListener {
                // Optional: show transactions of this category (future)
            }

            lifecycleScope.launch {
                val expense = db.transactionDao().getExpenseTotalByCategory(category.id).first()
                val income = db.transactionDao().getIncomeTotalByCategory(category.id).first()
                val count = db.transactionDao().countByCategory(category.id)
                holder.expenseTotal.text = "هزینه: ${FormatUtils.formatAmount(expense)}"
                holder.incomeTotal.text = if (income > 0) "درآمد: ${FormatUtils.formatAmount(income)}" else ""
                holder.count.text = "$count تراکنش"
            }
        }
    }
}
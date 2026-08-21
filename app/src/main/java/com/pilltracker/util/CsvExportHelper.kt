package com.pilltracker.util

import com.pilltracker.data.Category
import com.pilltracker.data.Transaction
import com.pilltracker.data.TransactionType

/**
 * CSV and Excel (HTML-based .xls) export helpers.
 * CSV uses UTF-8 BOM so Persian characters display correctly in Excel.
 */
object CsvExportHelper {

    private fun escapeCsv(field: String): String {
        val needsQuotes = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains(';')
        return if (needsQuotes) "\"${field.replace("\"", "\"\"")}\"" else field
    }

    fun buildCsv(transactions: List<Transaction>, categories: List<Category>): ByteArray {
        val catNames = categories.associate { it.id to it.name }
        val sb = StringBuilder()
        // UTF-8 BOM for Excel Persian support
        sb.append('\uFEFF')
        sb.append("عنوان,مبلغ (تومان),نوع,سال,ماه,روز,کتگوری\n")
        for (t in transactions.sortedByDescending { it.timestamp }) {
            sb.append(escapeCsv(t.title)).append(',')
                .append(t.amount).append(',')
                .append(if (t.type == TransactionType.INCOME) "درآمد" else "هزینه").append(',')
                .append(t.year).append(',')
                .append(t.month).append(',')
                .append(t.day).append(',')
                .append(escapeCsv(t.categoryId?.let { catNames[it] } ?: "")).append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun buildXls(transactions: List<Transaction>, categories: List<Category>): ByteArray {
        val catNames = categories.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("<html><head><meta charset=\"utf-8\"></head><body>")
        sb.append("<table border='1'><tr><th>عنوان</th><th>مبلغ (تومان)</th><th>نوع</th><th>تاریخ شمسی</th><th>کتگوری</th></tr>")
        for (t in transactions.sortedByDescending { it.timestamp }) {
            val date = "${t.year}/${t.month}/${t.day}"
            sb.append("<tr><td>").append(escapeHtml(t.title)).append("</td><td>").append(t.amount)
                .append("</td><td>").append(if (t.type == TransactionType.INCOME) "درآمد" else "هزینه")
                .append("</td><td>").append(date).append("</td><td>")
                .append(escapeHtml(t.categoryId?.let { catNames[it] } ?: "")).append("</td></tr>")
        }
        sb.append("</table></body></html>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
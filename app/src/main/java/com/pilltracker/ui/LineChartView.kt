package com.pilltracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.pilltracker.data.PriceHistory

/**
 * Simple line chart for the Tara price history.
 * Draws a broken line (خط شکسته) connecting daily prices.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C4DFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C4DFF")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 10f * resources.displayMetrics.density
    }

    private var prices: List<PriceHistory> = emptyList()

    fun setData(data: List<PriceHistory>) {
        prices = data.sortedBy { it.dateKey }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (prices.size < 2) {
            // Not enough data: draw a hint
            canvas.drawText(
                "دیتای کافی برای نمودار نیست (حداقل ۲ روز)",
                width / 2f - 140f * resources.displayMetrics.density,
                height / 2f,
                textPaint
            )
            return
        }

        val pad = 12f * resources.displayMetrics.density
        val maxPrice = prices.maxOf { it.price }.toFloat()
        val minPrice = prices.minOf { it.price }.toFloat()
        val range = (maxPrice - minPrice).coerceAtLeast(1f)

        val chartW = width - pad * 2
        val chartH = height - pad * 2

        val stepX = if (prices.size > 1) chartW / (prices.size - 1) else chartW

        val points = prices.mapIndexed { i, p ->
            val x = pad + i * stepX
            val y = pad + chartH - ((p.price - minPrice) / range) * chartH
            x to y
        }

        // Grid lines (3 horizontal)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        for (g in 0..2) {
            val gy = pad + chartH * g / 2f
            canvas.drawLine(pad, gy, width - pad, gy, gridPaint)
        }

        // Price labels on first & last
        canvas.drawText(formatCompact(minPrice), pad, height - 2f, textPaint)
        canvas.drawText(formatCompact(maxPrice), pad, pad + 8f, textPaint)

        // Line
        val path = Path()
        path.moveTo(points[0].first, points[0].second)
        for ((x, y) in points.drop(1)) path.lineTo(x, y)
        canvas.drawPath(path, linePaint)

        // Points
        val r = 4f * resources.displayMetrics.density
        for ((x, y) in points) canvas.drawCircle(x, y, r, pointPaint)
    }

    private fun formatCompact(value: Float): String {
        val v = value.toLong()
        return if (v >= 1_000_000_000) "%.1fB".format(v / 1_000_000_000.0)
        else if (v >= 1_000_000) "%.1fM".format(v / 1_000_000.0)
        else if (v >= 1_000) "%.0fK".format(v / 1_000.0)
        else v.toString()
    }
}

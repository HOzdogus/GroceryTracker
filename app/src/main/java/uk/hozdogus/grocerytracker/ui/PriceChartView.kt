package uk.hozdogus.grocerytracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import uk.hozdogus.grocerytracker.R
import uk.hozdogus.grocerytracker.data.PricePoint
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Step chart of an item's price history. Prices hold until the next reading, so the line
 * steps rather than slopes. A dashed line marks the target price, if set.
 */
class PriceChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<PricePoint> = emptyList()
    private var target: Int? = null

    private val density = resources.displayMetrics.density
    private val lineColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0)
    private val textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
    private val gridColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor
        strokeWidth = 1f * density
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.price_down)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val path = Path()

    fun setData(points: List<PricePoint>, target: Int?) {
        this.points = points
        this.target = target
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            val msg = "No price history yet"
            canvas.drawText(msg, (width - textPaint.measureText(msg)) / 2, height / 2f, textPaint)
            return
        }

        val prices = points.map { it.price } + listOfNotNull(target)
        var lo = prices.min()
        var hi = prices.max()
        if (hi == lo) {
            lo -= 10
            hi += 10
        }
        val loLabel = formatPence(lo.coerceAtLeast(0))
        val hiLabel = formatPence(hi)

        val labelWidth = maxOf(textPaint.measureText(loLabel), textPaint.measureText(hiLabel))
        val left = paddingLeft + labelWidth + 8 * density
        val right = width - paddingRight - 8 * density
        val top = paddingTop + textPaint.textSize
        val bottom = height - paddingBottom - textPaint.textSize - 8 * density

        // Price axis labels and grid lines at the top and bottom of the range.
        canvas.drawLine(left, top, right, top, gridPaint)
        canvas.drawLine(left, bottom, right, bottom, gridPaint)
        canvas.drawText(hiLabel, paddingLeft.toFloat(), top + textPaint.textSize / 3, textPaint)
        canvas.drawText(loLabel, paddingLeft.toFloat(), bottom + textPaint.textSize / 3, textPaint)

        val t0 = points.first().checkedAt
        val t1 = maxOf(points.last().checkedAt, t0 + 1)
        fun x(t: Long) = if (points.size == 1) right else left + (right - left) * (t - t0) / (t1 - t0).toFloat()
        fun y(p: Int) = bottom - (bottom - top) * (p - lo) / (hi - lo).toFloat()

        target?.let { canvas.drawLine(left, y(it), right, y(it), targetPaint) }

        path.reset()
        var prevY = y(points.first().price)
        path.moveTo(if (points.size == 1) left else x(t0), prevY)
        for (pt in points.drop(1)) {
            path.lineTo(x(pt.checkedAt), prevY)
            prevY = y(pt.price)
            path.lineTo(x(pt.checkedAt), prevY)
        }
        path.lineTo(right, prevY)
        canvas.drawPath(path, linePaint)
        for (pt in points) canvas.drawCircle(x(pt.checkedAt), y(pt.price), 3.5f * density, dotPaint)

        // Date labels under the start and end of the line.
        val startLabel = DATE.format(localDate(t0))
        val endLabel = DATE.format(localDate(points.last().checkedAt))
        val baseline = height - paddingBottom.toFloat()
        canvas.drawText(startLabel, left, baseline, textPaint)
        if (endLabel != startLabel) {
            canvas.drawText(endLabel, right - textPaint.measureText(endLabel), baseline, textPaint)
        }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    }
}

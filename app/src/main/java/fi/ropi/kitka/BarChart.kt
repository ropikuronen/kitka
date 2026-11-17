package fi.ropi.kitka

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View

/**
 * Plain vertical bar chart drawn on a Canvas.
 * Values on top of bars, two-line labels under them, optional dashed average line.
 */
class BarChart(ctx: Context) : View(ctx) {

    var values: List<Float> = emptyList()
    var labelsTop: List<String> = emptyList()
    var labelsBottom: List<String> = emptyList()
    var highlight: Int = -1
    var average: Float? = null
    var averageLabel: String = ""
    var fixedMax: Float? = null
    var valueText: (Float) -> String = { Math.round(it).toString() }
    /** Bars with this value are drawn as "no data" (a small dash, no number). */
    var missing: Set<Int> = emptySet()

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, context.dp(190))
    }

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val ctx = context
        val top = ctx.dpf(22f)
        val bottomPad = ctx.dpf(38f)
        val chartH = height - top - bottomPad
        val baseY = top + chartH
        val n = values.size
        val slot = width.toFloat() / n
        val barW = slot * 0.46f
        val max = (fixedMax ?: maxOf(values.maxOrNull() ?: 0f, average ?: 0f)).coerceAtLeast(1f)

        // baseline
        line.color = Pal.LINE
        line.strokeWidth = ctx.dpf(1f)
        line.pathEffect = null
        c.drawLine(0f, baseY, width.toFloat(), baseY, line)

        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val isHi = i == highlight
            if (i in missing) {
                bar.color = Pal.LINE
                c.drawRect(cx - barW / 2, baseY - ctx.dpf(2f), cx + barW / 2, baseY, bar)
            } else {
                val v = values[i]
                val h = (v / max) * chartH
                bar.color = if (isHi) Pal.INK else Pal.DIM
                c.drawRoundRect(cx - barW / 2, baseY - h, cx + barW / 2, baseY, ctx.dpf(3f), ctx.dpf(3f), bar)
                text.color = if (isHi) Pal.INK else Pal.MUTED
                text.textSize = ctx.dpf(11f)
                text.typeface = Fonts.MED
                c.drawText(valueText(v), cx, baseY - h - ctx.dpf(6f), text)
            }
            text.color = if (isHi) Pal.INK else Pal.MUTED
            text.textSize = ctx.dpf(12f)
            text.typeface = Fonts.MED
            labelsTop.getOrNull(i)?.let { c.drawText(it, cx, baseY + ctx.dpf(16f), text) }
            text.typeface = Fonts.REG
            text.textSize = ctx.dpf(11f)
            labelsBottom.getOrNull(i)?.let { c.drawText(it, cx, baseY + ctx.dpf(31f), text) }
        }

        val avg = average
        if (avg != null && avg > 0f) {
            val y = baseY - (avg / max) * chartH
            line.color = Pal.MUTED
            line.strokeWidth = ctx.dpf(1.5f)
            line.pathEffect = DashPathEffect(floatArrayOf(ctx.dpf(6f), ctx.dpf(5f)), 0f)
            c.drawLine(0f, y, width.toFloat(), y, line)
            if (averageLabel.isNotEmpty()) {
                text.color = Pal.MUTED
                text.textSize = ctx.dpf(11f)
                text.typeface = Fonts.MED
                text.textAlign = Paint.Align.RIGHT
                c.drawText(averageLabel, width.toFloat(), (y - ctx.dpf(5f)).coerceAtLeast(ctx.dpf(11f)), text)
                text.textAlign = Paint.Align.CENTER
            }
        }
    }
}

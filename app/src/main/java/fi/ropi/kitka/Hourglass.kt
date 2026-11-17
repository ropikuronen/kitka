package fi.ropi.kitka

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Hourglass that never empties: sand sits top and bottom, grains keep falling. */
class Hourglass(ctx: Context) : View(ctx) {

    private val glass = Path()
    private val inner = Path()
    private val pile = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Pal.INK; strokeCap = Paint.Cap.ROUND }
    private val sand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.SAND }
    private val start = System.currentTimeMillis()

    override fun onDraw(c: Canvas) {
        // Drawn in a 74 x 112 box, scaled to the view.
        val s = minOf(width / 74f, height / 112f)
        c.save()
        c.translate((width - 74 * s) / 2, (height - 112 * s) / 2)
        c.scale(s, s)

        inner.reset()
        inner.moveTo(14f, 6f)
        inner.cubicTo(14f, 32f, 34f, 40f, 34f, 56f)
        inner.cubicTo(34f, 72f, 14f, 82f, 14f, 106f)
        inner.lineTo(60f, 106f)
        inner.cubicTo(60f, 82f, 40f, 72f, 40f, 56f)
        inner.cubicTo(40f, 40f, 60f, 32f, 60f, 6f)
        inner.close()

        c.save()
        c.clipPath(inner)
        c.drawRect(0f, 30f, 74f, 57f, sand)
        pile.reset()
        pile.moveTo(0f, 106f); pile.lineTo(0f, 93f)
        pile.cubicTo(20f, 91f, 31f, 76f, 37f, 74f)
        pile.cubicTo(43f, 76f, 54f, 91f, 74f, 93f)
        pile.lineTo(74f, 106f); pile.close()
        c.drawPath(pile, sand)
        val t = (System.currentTimeMillis() - start) % 900L
        for (k in 0 until 3) {
            val phase = ((t + k * 300L) % 900L) / 900f
            c.drawCircle(37f, 56f + 19f * phase, 1.4f, sand)
        }
        c.restore()

        stroke.strokeWidth = 3f
        c.drawLine(8f, 4f, 66f, 4f, stroke)
        c.drawLine(8f, 108f, 66f, 108f, stroke)
        stroke.strokeWidth = 2f
        glass.reset()
        glass.moveTo(14f, 6f); glass.cubicTo(14f, 32f, 34f, 40f, 34f, 56f); glass.cubicTo(34f, 72f, 14f, 82f, 14f, 106f)
        glass.moveTo(60f, 6f); glass.cubicTo(60f, 32f, 40f, 40f, 40f, 56f); glass.cubicTo(40f, 72f, 60f, 82f, 60f, 106f)
        c.drawPath(glass, stroke)
        c.restore()
        postInvalidateOnAnimation()
    }
}

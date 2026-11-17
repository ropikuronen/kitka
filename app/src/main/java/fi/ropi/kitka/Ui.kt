package fi.ropi.kitka

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Black and white. Grey for secondary things. White is the only "accent". */
object Pal {
    val BG = Color.rgb(5, 5, 5)
    val CARD = Color.rgb(14, 14, 14)
    val BORDER = Color.rgb(31, 31, 31)
    val LINE = Color.rgb(38, 38, 38)
    val INK = Color.rgb(255, 255, 255)
    val SOFT = Color.rgb(208, 208, 208)
    val MUTED = Color.rgb(138, 138, 138)
    val DIM = Color.rgb(74, 74, 74)
    val SAND = Color.rgb(217, 217, 217)
}

object Fonts {
    val REG: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

val EN: Locale = Locale.ENGLISH

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.dpf(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

@Suppress("DEPRECATION")
fun Activity.paintWindow() {
    window.decorView.setBackgroundColor(Pal.BG)
    window.statusBarColor = Pal.BG
    window.navigationBarColor = Pal.BG
}

fun Context.tv(text: CharSequence, size: Float, color: Int = Pal.INK, font: Typeface = Fonts.REG): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = font
        setLineSpacing(0f, 1.2f)
    }

fun Context.big(text: String, size: Float = 30f): TextView =
    tv(text, size, Pal.INK, Fonts.BOLD).apply { letterSpacing = -0.02f }

fun Context.label(text: String): TextView =
    tv(text, 13f, Pal.MUTED, Fonts.MED).apply { setPadding(0, dp(24), 0, dp(8)) }

fun Context.caps(text: String, color: Int = Pal.MUTED): TextView =
    tv(text.uppercase(EN), 12f, color, Fonts.BOLD).apply { letterSpacing = 0.12f }

fun Context.body(text: String, color: Int = Pal.SOFT, size: Float = 15f): TextView =
    tv(text, size, color).apply { setLineSpacing(0f, 1.35f) }

fun Context.small(text: String): TextView =
    tv(text, 13f, Pal.MUTED).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(0f, 1.3f) }

fun Context.space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(h)) }

fun rounded(fill: Int, radius: Float, stroke: Int? = null, strokeW: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeW, stroke)
    }

/** Dark rounded block. */
fun Context.card(top: Int = 10, pad: Int = 18): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        background = rounded(Pal.CARD, dpf(18f), Pal.BORDER, dp(1))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }
    }

/** White pill = main action. Outline pill = other action. */
fun Context.button(text: String, primary: Boolean, onClick: () -> Unit): TextView =
    tv(text, 17f, if (primary) Pal.BG else Pal.INK, Fonts.MED).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = if (primary) rounded(Pal.INK, dpf(30f)) else rounded(Color.TRANSPARENT, dpf(30f), Pal.DIM, dp(1))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

/** Selectable option row: white when picked. */
fun Context.option(text: String, picked: Boolean, onClick: () -> Unit): TextView =
    tv(text, 16f, if (picked) Pal.BG else Pal.INK).apply {
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = if (picked) rounded(Pal.INK, dpf(16f)) else rounded(Color.TRANSPARENT, dpf(16f), Color.rgb(51, 51, 51), dp(1))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
    }

fun Context.underlineLink(text: String, onClick: () -> Unit): TextView =
    tv(text, 14f, Pal.MUTED).apply {
        paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        setPadding(0, dp(12), 0, dp(10))
        isClickable = true
        setOnClickListener { onClick() }
    }

fun Context.metric(value: String, label: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(value, 26f, Pal.INK, Fonts.BOLD))
        addView(tv(label, 13f, Pal.MUTED))
    }

fun Context.row(vararg views: View): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) addView(v, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

fun Context.kv(left: String, right: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(tv(left, 15f, Pal.MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(tv(right, 15f, Pal.INK, Fonts.MED))
    }

fun Context.divider(): View =
    View(this).apply {
        setBackgroundColor(Pal.BORDER)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
    }

/** Thin horizontal bar. */
fun Context.bar(fraction: Float, height: Int = 4, fill: Int = Pal.INK): LinearLayout {
    val f = fraction.coerceIn(0f, 1f)
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(Pal.LINE, dpf(height / 2f))
        addView(View(context).apply { background = rounded(fill, dpf(height / 2f)) },
            LinearLayout.LayoutParams(0, dp(height), f.coerceAtLeast(0.001f)))
        addView(View(context), LinearLayout.LayoutParams(0, dp(height), (1f - f).coerceAtLeast(0.001f)))
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(height))
    }
}

fun Context.dot(on: Boolean, leaving: Boolean = false): View =
    View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when {
                leaving -> { setColor(Pal.DIM); setStroke(dp(2), Pal.DIM) }
                on -> { setColor(Pal.INK); setStroke(dp(2), Pal.INK) }
                else -> { setColor(Color.TRANSPARENT); setStroke(dp(2), Pal.DIM) }
            }
        }
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
    }

/** Number + title row that expands to text + source link. */
fun Context.expandable(title: String, sub: String, text: String, cite: String?, url: String?, open: (String) -> Unit): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15))
            isClickable = true
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(ctx.tv(title, 15f, Pal.INK, Fonts.MED))
        texts.addView(ctx.tv(sub, 13f, Pal.MUTED))
        head.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val chev = ctx.tv("›", 22f, Pal.MUTED)
        head.addView(chev)
        addView(head)
        val detail = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(16))
            visibility = View.GONE
            addView(ctx.body(text, Pal.SOFT, 14f))
            if (cite != null && url != null) addView(ctx.underlineLink(cite) { open(url) })
        }
        addView(detail)
        head.setOnClickListener {
            val show = detail.visibility != View.VISIBLE
            detail.visibility = if (show) View.VISIBLE else View.GONE
            chev.animate().rotation(if (show) 90f else 0f).setDuration(180).start()
        }
    }
}

fun fmtMin(min: Long): String = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

fun fmtHours(min: Long): String {
    val h = min / 60.0
    return if (h < 10) String.format(EN, "%.1f h", h) else "${Math.round(h)} h"
}

fun fmtLeft(untilMs: Long): String {
    val ms = (untilMs - System.currentTimeMillis()).coerceAtLeast(0)
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

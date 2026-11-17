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

/** Black and white. Grey for secondary things. No accent colour. */
object Pal {
    val BG = Color.rgb(0, 0, 0)
    val SURFACE = Color.rgb(17, 17, 17)
    val LINE = Color.rgb(38, 38, 38)
    val INK = Color.rgb(255, 255, 255)
    val MUTED = Color.rgb(140, 140, 140)
    val DIM = Color.rgb(80, 80, 80)
}

object Fonts {
    val REG: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BIG: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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

fun Context.tv(text: String, size: Float, color: Int = Pal.INK, font: Typeface = Fonts.REG): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = font
        setLineSpacing(0f, 1.2f)
    }

/** Page title at the top of each tab. */
fun Context.pageTitle(text: String): TextView =
    tv(text, 30f, Pal.INK, Fonts.MED).apply {
        letterSpacing = -0.01f
        setPadding(0, dp(20), 0, dp(4))
    }

/** Small grey heading above a group. */
fun Context.label(text: String): TextView =
    tv(text, 13f, Pal.MUTED, Fonts.MED).apply { setPadding(0, dp(28), 0, dp(8)) }

fun Context.body(text: String, color: Int = Pal.MUTED): TextView =
    tv(text, 15f, color).apply { setPadding(0, dp(6), 0, 0) }

fun Context.small(text: String): TextView =
    tv(text, 13f, Pal.MUTED).apply { setPadding(0, dp(8), 0, 0) }

/** Rounded dark block that groups related rows. */
fun Context.card(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            setColor(Pal.SURFACE)
            cornerRadius = dpf(14f)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
    }

/** White filled = main action. Grey outline = other action. */
fun Context.button(text: String, primary: Boolean, onClick: () -> Unit): TextView =
    tv(text, 16f, if (primary) Pal.BG else Pal.INK, Fonts.MED).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = GradientDrawable().apply {
            cornerRadius = dpf(28f)
            if (primary) {
                setColor(Pal.INK)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Pal.DIM)
            }
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

/** Number over a label, for rows of 2 or 3. */
fun Context.metric(value: String, label: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(value, 26f, Pal.INK, Fonts.BIG))
        addView(tv(label, 13f, Pal.MUTED))
    }

fun Context.metricRow(vararg m: LinearLayout): LinearLayout =
    card().apply {
        orientation = LinearLayout.HORIZONTAL
        for (x in m) addView(x, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

/** Label left, value right. */
fun Context.kv(left: String, right: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(tv(left, 15f, Pal.MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(tv(right, 15f, Pal.INK, Fonts.MED))
    }

/** One app: name and minutes, thin bar under it. */
fun Context.appBar(name: String, right: String, fraction: Float): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(9), 0, dp(9))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ctx.tv(name, 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(ctx.tv(right, 15f, Pal.MUTED))
        })
        val f = fraction.coerceIn(0.01f, 1f)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(View(ctx).apply {
                background = GradientDrawable().apply { setColor(Pal.INK); cornerRadius = dpf(2f) }
            }, LinearLayout.LayoutParams(0, dp(4), f))
            addView(View(ctx), LinearLayout.LayoutParams(0, dp(4), (1f - f).coerceAtLeast(0.0001f)))
        })
    }
}

fun Context.divider(): View =
    View(this).apply {
        setBackgroundColor(Pal.LINE)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
    }

fun fmtMin(min: Long): String =
    if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

fun fmtHours(min: Long): String {
    val h = min / 60.0
    return if (h < 10) String.format(EN, "%.1f h", h) else "${Math.round(h)} h"
}

fun fmtLeft(untilMs: Long): String {
    val ms = (untilMs - System.currentTimeMillis()).coerceAtLeast(0)
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    return if (h > 0) "${h} h ${m} min" else "$m min"
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

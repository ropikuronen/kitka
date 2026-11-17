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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Look: forged iron and old leather. Warm near-black, parchment text,
 * steel for neutral data, one crimson (the helmet plume) for actions and "today".
 * No gradients, no glow, no emoji.
 */
object Pal {
    val BG = Color.rgb(22, 19, 15)
    val SURFACE = Color.rgb(33, 28, 22)
    val LINE = Color.rgb(60, 52, 42)
    val INK = Color.rgb(238, 228, 208)
    val MUTED = Color.rgb(156, 144, 125)
    val STEEL = Color.rgb(112, 112, 108)
    val CRIMSON = Color.rgb(178, 58, 50)
    val BRASS = Color.rgb(201, 162, 91)
}

object Fonts {
    val HEAD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val NUM: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val BODY: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val BODY_MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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

fun Context.tv(text: String, size: Float, color: Int = Pal.INK, font: Typeface = Fonts.BODY): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = font
        setLineSpacing(0f, 1.15f)
    }

fun Context.helm(sizeDp: Int): ImageView =
    ImageView(this).apply {
        setImageResource(R.drawable.ic_helm)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** A framed block. Everything on the main screen lives in one of these. */
fun Context.panel(title: String? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(Pal.SURFACE)
            setStroke(dp(1), Pal.LINE)
            cornerRadius = dpf(6f)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) }
        if (title != null) {
            addView(tv(title, 19f, Pal.INK, Fonts.HEAD).apply { setPadding(0, 0, 0, dp(10)) })
        }
    }

fun Context.note(text: String): TextView =
    tv(text, 13f, Pal.MUTED).apply { setPadding(0, dp(10), 0, 0) }

/**
 * Buttons look like buttons: full width, filled or framed, with a clear label.
 * Primary = crimson fill. Secondary = framed.
 */
fun Context.button(text: String, primary: Boolean, onClick: () -> Unit): TextView =
    tv(text, if (primary) 18f else 16f, Pal.INK, Fonts.BODY_MED).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.02f
        val v = if (primary) dp(17) else dp(14)
        setPadding(dp(16), v, dp(16), v)
        background = GradientDrawable().apply {
            cornerRadius = dpf(5f)
            if (primary) {
                setColor(Pal.CRIMSON)
            } else {
                setColor(Pal.BG)
                setStroke(dp(1), Pal.MUTED)
            }
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

/** Big number with a small label under it. Used three in a row. */
fun Context.stat(value: String, label: String, color: Int = Pal.INK): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(value, 28f, color, Fonts.NUM))
        addView(tv(label, 12f, Pal.MUTED, Fonts.BODY_MED).apply { letterSpacing = 0.06f })
    }

fun Context.statRow(vararg stats: LinearLayout): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, 0)
        for (s in stats) addView(s, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

/** One app: name, minutes, and a thin bar showing its share. */
fun Context.appBar(name: String, right: String, fraction: Float, accent: Boolean): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(6))
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ctx.tv(name, 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(ctx.tv(right, 14f, Pal.MUTED, Fonts.BODY_MED))
        }
        addView(top)
        val f = fraction.coerceIn(0.01f, 1f)
        val track = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        track.addView(
            View(ctx).apply { setBackgroundColor(if (accent) Pal.CRIMSON else Pal.STEEL) },
            LinearLayout.LayoutParams(0, dp(5), f)
        )
        track.addView(
            View(ctx).apply { setBackgroundColor(Pal.LINE) },
            LinearLayout.LayoutParams(0, dp(5), (1f - f).coerceAtLeast(0.0001f))
        )
        addView(track)
    }
}

/** Label left, value right. */
fun Context.kv(left: String, right: String, rightColor: Int = Pal.INK): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(7), 0, dp(7))
        addView(tv(left, 15f, Pal.MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(tv(right, 17f, rightColor, Fonts.NUM))
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
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

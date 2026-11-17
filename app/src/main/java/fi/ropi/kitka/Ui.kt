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

/**
 * Ulkoasu. Tarkoituksella pelkistetty: lämmin musta, paperinvalkoinen teksti,
 * yksi korostusväri. Ei gradientteja, ei kortteja, ei koristeita.
 */
object Pal {
    val BG = Color.rgb(20, 19, 17)
    val INK = Color.rgb(237, 232, 223)
    val MUTED = Color.rgb(140, 135, 126)
    val RULE = Color.rgb(54, 52, 48)
    val AMBER = Color.rgb(232, 163, 61)
}

object Fonts {
    val HEAD: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val BODY: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val NUM: Typeface = Typeface.MONOSPACE
}

val FI: Locale = Locale.forLanguageTag("fi-FI")

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

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

fun Context.sectionLabel(text: String): TextView =
    tv(text.uppercase(FI), 12f, Pal.MUTED, Fonts.HEAD).apply {
        letterSpacing = 0.12f
        setPadding(0, dp(30), 0, dp(10))
    }

fun Context.rule(spaced: Boolean = true): View =
    View(this).apply {
        setBackgroundColor(Pal.RULE)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            if (spaced) {
                topMargin = dp(14)
                bottomMargin = dp(6)
            }
        }
    }

/** Nappi. Ensisijainen = täytetty meripihka. Toissijainen = ohut reunus. */
fun Context.action(text: String, primary: Boolean, onClick: () -> Unit): TextView =
    tv(text, if (primary) 20f else 16f, if (primary) Pal.BG else Pal.INK, Fonts.HEAD).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.03f
        val v = if (primary) dp(20) else dp(14)
        setPadding(dp(16), v, dp(16), v)
        background = GradientDrawable().apply {
            cornerRadius = dp(3).toFloat()
            if (primary) {
                setColor(Pal.AMBER)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Pal.INK)
            }
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

/** Rivi: nimi vasemmalla, luvut oikealla monospacena. */
fun Context.row(left: String, right: String, color: Int = Pal.INK): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(tv(left, 15f, color), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(
            tv(right, 14f, color, Fonts.NUM).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) }
        )
    }

/** Päivärivi vaakapalkilla. Palkki on tavallinen View, ei tekstimerkkejä. */
fun Context.barRow(day: String, fraction: Float, right: String, today: Boolean): LinearLayout {
    val ctx = this
    val f = fraction.coerceIn(0f, 1f)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(
            ctx.tv(day, 13f, if (today) Pal.INK else Pal.MUTED, Fonts.NUM),
            LinearLayout.LayoutParams(dp(78), WRAP)
        )
        val track = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        track.addView(
            View(ctx).apply { setBackgroundColor(if (today) Pal.AMBER else Pal.MUTED) },
            LinearLayout.LayoutParams(0, dp(8), f)
        )
        track.addView(View(ctx), LinearLayout.LayoutParams(0, dp(8), 1f - f))
        addView(track, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(
            ctx.tv(right, 13f, if (today) Pal.INK else Pal.MUTED, Fonts.NUM).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(dp(118), WRAP).apply { leftMargin = dp(10) }
        )
    }
}

fun fmtLeft(untilMs: Long): String {
    val ms = (untilMs - System.currentTimeMillis()).coerceAtLeast(0)
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    return "$h h $m min"
}

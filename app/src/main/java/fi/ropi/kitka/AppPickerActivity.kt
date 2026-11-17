package fi.ropi.kitka

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Pick guarded apps. Adding works at once, removing waits 24 h. */
class AppPickerActivity : Activity() {

    private lateinit var list: LinearLayout
    private var apps: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        col.addView(button("Back", primary = false) { finish() }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; width = WRAP }
            setPadding(dp(18), dp(10), dp(18), dp(10))
        })
        col.addView(tv("Guarded apps", 28f, Pal.INK, Fonts.HEAD).apply { setPadding(dp(2), dp(18), 0, 0) })
        col.addView(
            note(
                "Tap an app to guard it. Guarding starts at once. " +
                    "Un-guarding takes 24 hours, so you cannot loosen the rules on an impulse. " +
                    "Tap again during those 24 hours to cancel."
            ).apply { setPadding(dp(2), dp(8), 0, dp(4)) }
        )
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(col)
        })

        list.addView(note("Loading apps."))
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val found = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != packageName }
            runOnUiThread {
                apps = found
                renderList()
            }
        }.start()
    }

    private fun renderList() {
        list.removeAllViews()
        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val byName = compareBy<Pair<String, String>> { it.second.lowercase(EN) }
        val guarded = apps.filter { it.first in targets }.sortedWith(byName)
        val others = apps.filter { it.first !in targets }.sortedWith(byName)

        val g = panel("Guarded (${guarded.size})")
        if (guarded.isEmpty()) g.addView(note("Nothing guarded."))
        for ((i, a) in guarded.withIndex()) {
            if (i > 0) g.addView(divider())
            g.addView(appRow(a.first, a.second, on = true, pendingUntil = pending[a.first]))
        }
        list.addView(g)

        val o = panel("Other apps")
        for ((i, a) in others.withIndex()) {
            if (i > 0) o.addView(divider())
            o.addView(appRow(a.first, a.second, on = false, pendingUntil = null))
        }
        list.addView(o)
    }

    private fun appRow(pkg: String, label: String, on: Boolean, pendingUntil: Long?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(tv(label, 16f, if (on) Pal.INK else Pal.MUTED))
        if (pendingUntil != null) {
            text.addView(tv("Un-guarded in ${fmtLeft(pendingUntil)}. Tap to cancel.", 13f, Pal.BRASS))
        }
        row.addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))

        // Checkbox: crimson filled square = guarded, empty frame = not.
        val box = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(3f)
                when {
                    pendingUntil != null -> { setColor(Pal.BG); setStroke(dp(2), Pal.BRASS) }
                    on -> { setColor(Pal.CRIMSON); setStroke(dp(2), Pal.CRIMSON) }
                    else -> { setColor(Pal.BG); setStroke(dp(2), Pal.STEEL) }
                }
            }
        }
        row.addView(box, LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(12) })

        row.setOnClickListener {
            when {
                !on -> Targets.add(this, pkg)
                pendingUntil != null -> Targets.cancelRemove(this, pkg)
                else -> Targets.requestRemove(this, pkg)
            }
            renderList()
        }
        return row
    }
}

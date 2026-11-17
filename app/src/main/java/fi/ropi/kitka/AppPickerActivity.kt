package fi.ropi.kitka

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Kohdesovellusten valinta. Lisäys heti, poisto 24 h viiveellä. */
class AppPickerActivity : Activity() {

    private lateinit var list: LinearLayout
    private var apps: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(48))
        }
        col.addView(tv("Kohdesovellukset", 26f, Pal.INK, Fonts.HEAD))
        col.addView(
            tv(
                "Napauta lisätäksesi tai poistaaksesi. Lisäys toimii heti. Poisto astuu voimaan " +
                    "vasta 24 tunnin päästä, jotta sääntöjä ei voi löysätä hetken mielijohteesta.",
                14f, Pal.MUTED
            ).apply { setPadding(0, dp(8), 0, dp(16)) }
        )
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(col)
        })

        list.addView(tv("Haetaan sovelluksia.", 14f, Pal.MUTED))
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
        val sorted = apps.sortedWith(
            compareBy<Pair<String, String>>({ it.first !in targets }, { it.second.lowercase(FI) })
        )
        for ((pkg, label) in sorted) {
            val on = pkg in targets
            val pend = pending[pkg]
            val status = when {
                on && pend != null -> "poistuu ${fmtLeft(pend)}"
                on -> "päällä"
                else -> ""
            }
            val r = row(label, status, if (on) Pal.INK else Pal.MUTED)
            if (pend != null) (r.getChildAt(1) as TextView).setTextColor(Pal.AMBER)
            r.setPadding(0, dp(13), 0, dp(13))
            r.isClickable = true
            r.setOnClickListener {
                when {
                    !on -> Targets.add(this, pkg)
                    pend != null -> Targets.cancelRemove(this, pkg)
                    else -> Targets.requestRemove(this, pkg)
                }
                renderList()
            }
            list.addView(r)
            list.addView(rule(spaced = false))
        }
    }
}

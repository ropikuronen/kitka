package fi.ropi.kitka

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private class Day(
        val date: LocalDate,
        val minutes: Map<String, Long>,
        val counts: Map<String, Map<String, Int>>,
    )

    private class Stats(
        val days: List<Day>,
        val targets: Set<String>,
        val labels: Map<String, String>,
        val pending: Map<String, Long>,
    )

    private val dayLong = DateTimeFormatter.ofPattern("EEEE d.M.yyyy", FI)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d.M.", FI)

    private lateinit var col: LinearLayout
    private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(48))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val g = ++gen
        col.removeAllViews()
        col.addView(tv(getString(R.string.app_name), 30f, Pal.INK, Fonts.HEAD))
        col.addView(tv(LocalDate.now().format(dayLong), 14f, Pal.MUTED).apply { setPadding(0, dp(2), 0, 0) })

        val a11y = isServiceEnabled()
        val usage = Usage.hasPermission(this)
        if (!a11y || !usage) setup(a11y, usage)

        val loading = tv("Lasketaan.", 14f, Pal.MUTED).apply { setPadding(0, dp(24), 0, 0) }
        col.addView(loading)
        Thread {
            val data = compute()
            runOnUiThread {
                if (g == gen && !isFinishing) {
                    col.removeView(loading)
                    show(data, usage)
                }
            }
        }.start()
    }

    private fun setup(a11y: Boolean, usage: Boolean) {
        col.addView(sectionLabel("Käyttöönotto"))
        if (!a11y) {
            col.addView(tv("1. Laita palvelu päälle: Esteettömyys, sitten asennetut sovellukset, sitten tämä sovellus.", 15f))
            col.addView(action("Avaa esteettömyysasetukset", primary = true) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            col.addView(
                tv(
                    "Jos Android sanoo \"rajoitettu asetus\": avaa sovelluksen tiedot, paina oikean " +
                        "yläkulman kolmea pistettä ja valitse Salli rajoitetut asetukset. Palaa sitten kohtaan 1. " +
                        "Tämä johtuu siitä, että appi ei tule Play Kaupasta.",
                    14f, Pal.MUTED
                ).apply { setPadding(0, dp(14), 0, 0) }
            )
            col.addView(action("Sovelluksen tiedot", primary = false) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        }
        if (!usage) {
            col.addView(
                tv("${if (a11y) "1" else "2"}. Salli käyttötilastot, niin näet minuutit.", 15f)
                    .apply { setPadding(0, dp(20), 0, 0) }
            )
            col.addView(action("Avaa käyttötilastot", primary = a11y) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }
    }

    private fun compute(): Stats {
        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val today = LocalDate.now()
        val counts = Db.get(this).counts(today.minusDays(6))
        val loggedPkgs = counts.values.flatMap { it.keys }.toSet()
        val days = (0..6).map { i ->
            val d = today.minusDays(i.toLong())
            val (s, e) = Usage.dayBounds(d)
            Day(d, Usage.foregroundMs(this, s, e, targets + loggedPkgs), counts[d.toString()] ?: emptyMap())
        }
        val labels = HashMap<String, String>()
        for (p in targets + loggedPkgs) labelOf(p)?.let { labels[p] = it }
        return Stats(days, targets, labels, pending)
    }

    private fun show(s: Stats, usage: Boolean) {
        fun count(d: Day, type: String) = d.counts.values.sumOf { it[type] ?: 0 }
        val today = s.days[0]

        // Tänään
        col.addView(sectionLabel("Tänään"))
        val totalMin = today.minutes.values.sum() / 60_000
        if (usage) {
            col.addView(tv("$totalMin min", 46f, Pal.INK, Fonts.NUM))
        } else {
            col.addView(tv("Minuutit näkyvät, kun käyttötilastot on sallittu.", 14f, Pal.MUTED))
        }
        val attempts = count(today, Ev.ATTEMPT)
        val gates = attempts + count(today, Ev.RECHECK)
        val closes = count(today, Ev.CLOSE)
        val pct = if (gates > 0) closes * 100 / gates else 0
        col.addView(tv("$attempts avausyritystä, $closes suljettu ($pct %)", 15f).apply { setPadding(0, dp(10), 0, 0) })
        col.addView(tv("Jatkoit ${count(today, Ev.CONTINUE)} kertaa. Noin ${count(today, Ev.SCROLL)} pyyhkäisyä.", 15f, Pal.MUTED))

        val pkgs = (today.minutes.filterValues { it >= 60_000 }.keys + today.counts.keys)
            .distinct()
            .sortedByDescending { today.minutes[it] ?: 0L }
        if (pkgs.isNotEmpty()) {
            col.addView(rule())
            for (p in pkgs) {
                val m = (today.minutes[p] ?: 0L) / 60_000
                val a = today.counts[p]?.get(Ev.ATTEMPT) ?: 0
                val c = today.counts[p]?.get(Ev.CLOSE) ?: 0
                col.addView(row(s.labels[p] ?: p, "$m min  $a av  $c sulj"))
            }
        }

        // 7 päivää
        col.addView(sectionLabel("7 päivää"))
        val maxMin = s.days.maxOf { it.minutes.values.sum() / 60_000 }.coerceAtLeast(1)
        for ((i, d) in s.days.withIndex()) {
            val m = d.minutes.values.sum() / 60_000
            col.addView(
                barRow(
                    d.date.format(dayShort),
                    m.toFloat() / maxMin,
                    "$m min ${count(d, Ev.ATTEMPT)} av",
                    i == 0
                )
            )
        }

        // Kohteet
        col.addView(sectionLabel("Kohteet"))
        val installed = s.targets.mapNotNull { s.labels[it] }.sorted()
        col.addView(tv(if (installed.isEmpty()) "Ei asennettuja kohdesovelluksia." else installed.joinToString(", "), 15f))
        for ((p, t) in s.pending) {
            col.addView(
                tv("${s.labels[p] ?: p} poistuu listalta ${fmtLeft(t)} päästä.", 14f, Pal.AMBER)
                    .apply { setPadding(0, dp(8), 0, 0) }
            )
        }
        col.addView(action("Muokkaa kohteita", primary = false) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        })

        col.addView(
            tv(
                "Tämä appi ei estä sinua poistamasta sitä tai sammuttamasta palvelua. " +
                    "Se tekee jokaisesta avauksesta valinnan, ja näyttää mitä valitsit.",
                13f, Pal.MUTED
            ).apply { setPadding(0, dp(36), 0, 0) }
        )
    }

    private fun labelOf(pkg: String): String? = try {
        @Suppress("DEPRECATION")
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, KitkaService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}

package fi.ropi.kitka

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    companion object {
        const val APK_URL = "https://github.com/ropikuronen/kitka/releases/latest/download/app.apk"
    }

    private class Day(
        val date: LocalDate,
        val minutes: Map<String, Long>,
        val counts: Map<String, Map<String, Int>>,
    ) {
        val totalMin: Long get() = minutes.values.sum() / 60_000
        fun count(type: String) = counts.values.sumOf { it[type] ?: 0 }
        val gates: Int get() = count(Ev.ATTEMPT) + count(Ev.RECHECK)
    }

    private class Stats(
        val days: List<Day>,          // index 0 = today, up to 7 days back
        val targets: Set<String>,
        val labels: Map<String, String>,
        val pending: Map<String, Long>,
        val monthMin: Long,
        val yearMin: Long,
        val since: Long,
    )

    private val dateLong = DateTimeFormatter.ofPattern("EEEE, d MMMM", EN)
    private val dayAbbr = DateTimeFormatter.ofPattern("EEE", EN)
    private val dayNum = DateTimeFormatter.ofPattern("d.M.", EN)
    private val sinceFmt = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm", EN)

    private lateinit var col: LinearLayout
    private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
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
        header()

        val a11y = isServiceEnabled()
        val usage = Usage.hasPermission(this)
        if (!a11y || !usage) setup(a11y, usage)

        checkUpdate(g)

        val loading = note("Counting.")
        col.addView(loading)
        Thread {
            val data = compute()
            runOnUiThread {
                if (g == gen && !isFinishing) {
                    col.removeView(loading)
                    show(data, a11y, usage)
                }
            }
        }.start()
    }

    private fun header() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, 0, dp(4))
        }
        row.addView(helm(46))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        text.addView(tv(getString(R.string.app_name), 28f, Pal.INK, Fonts.HEAD))
        text.addView(tv(LocalDate.now().format(dateLong), 14f, Pal.MUTED))
        row.addView(text)
        col.addView(row)
    }

    private fun setup(a11y: Boolean, usage: Boolean) {
        val p = panel("Setup")
        var step = 1
        if (!a11y) {
            p.addView(tv("${step++}. Turn on the gate", 16f, Pal.INK, Fonts.BODY_MED))
            p.addView(note("Accessibility, then Installed apps, then ScrollSlayer, then On."))
            p.addView(button("Open Accessibility settings", primary = true) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            p.addView(
                note(
                    "If Android says \"Restricted setting\": open App info, tap the three dots " +
                        "top right, choose Allow restricted settings, then try again."
                )
            )
            p.addView(button("Open App info", primary = false) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        }
        if (!usage) {
            p.addView(
                tv("$step. Allow usage access", 16f, Pal.INK, Fonts.BODY_MED)
                    .apply { if (!a11y) setPadding(0, dp(22), 0, 0) }
            )
            p.addView(note("Needed for minutes. Android already records how long each app is open; this lets ScrollSlayer read it."))
            p.addView(button("Open Usage access", primary = a11y) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }
        col.addView(p)
    }

    private fun compute(): Stats {
        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val today = LocalDate.now()
        val counts = Db.get(this).counts(today.minusDays(7))
        val logged = counts.values.flatMap { it.keys }.toSet()
        val all = targets + logged
        val days = (0..7).map { i ->
            val d = today.minusDays(i.toLong())
            val (s, e) = Usage.dayBounds(d)
            Day(d, Usage.foregroundMs(this, s, e, all), counts[d.toString()] ?: emptyMap())
        }
        val z = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(z).toInstant().toEpochMilli()
        val yearStart = today.withDayOfYear(1).atStartOfDay(z).toInstant().toEpochMilli()
        val monthMin = Usage.aggregateMs(this, monthStart, now, targets) / 60_000
        val yearMin = Usage.aggregateMs(this, yearStart, now, targets) / 60_000
        val labels = HashMap<String, String>()
        for (p in all) labelOf(p)?.let { labels[p] = it }
        return Stats(days, targets, labels, pending, monthMin, yearMin, KitkaService.since(this))
    }

    private fun show(s: Stats, a11y: Boolean, usage: Boolean) {
        val today = s.days[0]
        val installed = s.targets.filter { it in s.labels }

        if (a11y) {
            col.addView(
                note("Gate is on. Guarding ${installed.size} apps.").apply { setPadding(dp(2), dp(4), 0, 0) }
            )
        }

        // TODAY
        val t = panel("Today")
        if (usage) {
            t.addView(tv(fmtMin(today.totalMin), 44f, Pal.INK, Fonts.NUM))
            t.addView(tv("on guarded apps", 14f, Pal.MUTED))
        }
        t.addView(
            statRow(
                stat(today.count(Ev.ATTEMPT).toString(), "OPENS"),
                stat(today.count(Ev.CLOSE).toString(), "CLOSED", Pal.CRIMSON),
                stat(today.count(Ev.CONTINUE).toString(), "CONTINUED"),
            ).apply { setPadding(0, dp(18), 0, 0) }
        )
        val gates = today.gates
        t.addView(
            note(
                if (gates > 0) {
                    "You closed ${today.count(Ev.CLOSE)} of $gates gates (${today.count(Ev.CLOSE) * 100 / gates} %). " +
                        "About ${today.count(Ev.SCROLL)} swipes after continuing."
                } else {
                    "No gates yet today. Open one of your guarded apps to test it."
                }
            )
        )

        val apps = (today.minutes.filterValues { it >= 60_000 }.keys + today.counts.keys)
            .distinct()
            .sortedByDescending { today.minutes[it] ?: 0L }
        if (apps.isNotEmpty()) {
            t.addView(divider().apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
            })
            val maxApp = apps.maxOf { today.minutes[it] ?: 0L }.coerceAtLeast(1L)
            for ((i, p) in apps.withIndex()) {
                val ms = today.minutes[p] ?: 0L
                val o = today.counts[p]?.get(Ev.ATTEMPT) ?: 0
                t.addView(
                    appBar(
                        s.labels[p] ?: p,
                        "${fmtMin(ms / 60_000)}   $o ${if (o == 1) "open" else "opens"}",
                        ms.toFloat() / maxApp,
                        accent = i == 0
                    )
                )
            }
        }
        if (s.since > 0) {
            val at = Instant.ofEpochMilli(s.since).atZone(ZoneId.systemDefault()).format(sinceFmt)
            t.addView(note("Opens are counted from when you turned the gate on ($at). Minutes come from Android's own records, so they go back further."))
        }
        col.addView(t)

        // LAST 7 DAYS
        if (usage) {
            val week = s.days.take(7).reversed()          // oldest .. today
            val full = s.days.drop(1).take(7)              // last 7 complete days
            val avg = full.sumOf { it.totalMin } / 7f
            val w = panel("Last 7 days")
            w.addView(BarChart(this).apply {
                values = week.map { it.totalMin.toFloat() }
                labelsTop = week.map { it.date.format(dayAbbr) }
                labelsBottom = week.map { it.date.format(dayNum) }
                highlight = week.size - 1
                average = avg
                averageLabel = "avg ${Math.round(avg)} min"
                valueText = { Math.round(it).toString() }
            })
            w.addView(tv("Minutes per day on guarded apps. Dashed line is your average over the last 7 full days.", 13f, Pal.MUTED)
                .apply { setPadding(0, dp(6), 0, dp(4)) })
            w.addView(kv("This week", fmtMin(week.sumOf { it.totalMin })))
            w.addView(kv("Daily average", fmtMin(Math.round(avg).toLong())))
            col.addView(w)

            // AT THIS PACE
            val b = panel("At this pace")
            b.addView(kv("Per month", fmtHours(Math.round(avg * 30).toLong()), Pal.INK))
            b.addView(kv("Per year", fmtHours(Math.round(avg * 365).toLong()), Pal.CRIMSON))
            val yearMin = avg * 365
            b.addView(kv("Full 24 h days per year", String.format(EN, "%.1f", yearMin / 1440)))
            b.addView(kv("Waking days per year (16 h)", String.format(EN, "%.1f", yearMin / 960)))
            b.addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) })
            b.addView(kv("This month so far", "≈ " + fmtHours(s.monthMin)))
            b.addView(kv("This year so far", "≈ " + fmtHours(s.yearMin)))
            b.addView(
                note(
                    "Pace = your 7-day average × days. It is arithmetic, not a forecast. " +
                        "\"So far\" comes from Android's long-term records, which are coarser, so treat it as approximate."
                )
            )
            col.addView(b)
        }

        // GATE RESULTS
        if (s.since > 0) {
            val sinceDay = Instant.ofEpochMilli(s.since).atZone(ZoneId.systemDefault()).toLocalDate()
            val week = s.days.take(7).reversed()
            val r = panel("Gate results")
            r.addView(BarChart(this).apply {
                values = week.map { d -> if (d.gates > 0) d.count(Ev.CLOSE) * 100f / d.gates else 0f }
                labelsTop = week.map { it.date.format(dayAbbr) }
                labelsBottom = week.map { it.date.format(dayNum) }
                highlight = week.size - 1
                fixedMax = 100f
                missing = week.indices.filter { week[it].date.isBefore(sinceDay) || week[it].gates == 0 }.toSet()
                valueText = { "${Math.round(it)}%" }
            })
            r.addView(tv("Share of gates you closed each day. Grey dash = no gates that day.", 13f, Pal.MUTED)
                .apply { setPadding(0, dp(6), 0, 0) })
            col.addView(r)
        }

        // GUARDED APPS
        val a = panel("Guarded apps")
        a.addView(tv(if (installed.isEmpty()) "None installed." else installed.map { s.labels[it] }.sortedBy { it }.joinToString(", "), 15f))
        for ((p, until) in s.pending) {
            a.addView(note("${s.labels[p] ?: p} stops being guarded in ${fmtLeft(until)}.").apply { setTextColor(Pal.BRASS) })
        }
        a.addView(button("Edit guarded apps", primary = false) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        })
        col.addView(a)

        col.addView(
            note(
                "ScrollSlayer cannot stop you from turning it off or uninstalling it. " +
                    "It turns every open into a choice and shows you what you chose."
            ).apply { setPadding(dp(4), dp(22), dp(4), 0) }
        )
        col.addView(note("Build ${currentBuild()}").apply { setPadding(dp(4), dp(10), 0, 0) })
    }

    /** Asks GitHub for the newest build. If it is newer, shows an Update button at the top. */
    private fun checkUpdate(g: Int) {
        Thread {
            val latest = try {
                val c = java.net.URL("https://api.github.com/repos/ropikuronen/kitka/releases/latest")
                    .openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(body).optString("name").removePrefix("Build ").trim().toIntOrNull()
            } catch (e: Exception) {
                null
            }
            val mine = currentBuild()
            if (latest != null && latest > mine) {
                runOnUiThread {
                    if (g != gen || isFinishing) return@runOnUiThread
                    val p = panel("Update available")
                    p.addView(tv("Build $latest is out. You have build $mine.", 15f, Pal.MUTED))
                    p.addView(button("Download build $latest", primary = true) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
                    })
                    p.addView(note("Your browser downloads it. Open the file and install over this one. Your data stays."))
                    col.addView(p, 1)
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun currentBuild(): Int =
        packageManager.getPackageInfo(packageName, 0).versionCode

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

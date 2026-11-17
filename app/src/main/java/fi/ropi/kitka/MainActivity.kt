package fi.ropi.kitka

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Four tabs along the bottom: Today, Trends, Science, Apps. */
class MainActivity : Activity() {

    companion object {
        const val APK_URL = "https://github.com/ropikuronen/kitka/releases/latest/download/app.apk"
        private const val TODAY = 0
        private const val TRENDS = 1
        private const val SCIENCE = 2
        private const val APPS = 3
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

    private class Experiment(
        val beforeAvg: Float?, val beforeDays: Int,
        val afterAvg: Float?, val afterDays: Int,
        val totalGates: Int, val totalClosed: Int,
    )

    private class Stats(
        val days: List<Day>,          // index 0 = today, up to 7 days back
        val targets: Set<String>,
        val labels: Map<String, String>,
        val monthMin: Long,
        val yearMin: Long,
        val since: Long,
        val exp: Experiment,
    ) {
        /** Average of the last 7 complete days. */
        val avg7: Float get() = days.drop(1).take(7).sumOf { it.totalMin } / 7f
    }

    private val dayAbbr = DateTimeFormatter.ofPattern("EEE", EN)
    private val dayNum = DateTimeFormatter.ofPattern("d.M.", EN)
    private val dateLong = DateTimeFormatter.ofPattern("EEEE d MMMM", EN)

    private lateinit var scroll: ScrollView
    private lateinit var col: LinearLayout
    private val tabViews = ArrayList<Pair<ImageView, TextView>>()

    private var tab = TODAY
    private var stats: Stats? = null
    private var apps: List<Pair<String, String>>? = null
    private var latestBuild: Int? = null
    private var a11y = false
    private var usage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        tab = savedInstanceState?.getInt("tab") ?: TODAY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(col)
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(divider())
        root.addView(bottomNav())
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        a11y = isServiceEnabled()
        usage = Usage.hasPermission(this)
        render()
        Thread {
            val s = compute()
            runOnUiThread {
                if (!isFinishing) {
                    stats = s
                    render()
                }
            }
        }.start()
        checkUpdate()
    }

    // ---------- navigation ----------

    private fun bottomNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Pal.BG)
            setPadding(0, dp(8), 0, dp(10))
        }
        val items = listOf(
            R.drawable.tab_today to "Today",
            R.drawable.tab_trends to "Trends",
            R.drawable.tab_science to "Science",
            R.drawable.tab_apps to "Apps",
        )
        tabViews.clear()
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(2))
                isClickable = true
                setOnClickListener {
                    if (tab != i) {
                        tab = i
                        render()
                        scroll.scrollTo(0, 0)
                    }
                }
            }
            val icon = ImageView(this).apply { setImageResource(item.first) }
            cell.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
            val label = tv(item.second, 12f, Pal.DIM, Fonts.MED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }
            cell.addView(label)
            bar.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            tabViews.add(icon to label)
        }
        return bar
    }

    private fun render() {
        for ((i, t) in tabViews.withIndex()) {
            val c = if (i == tab) Pal.INK else Pal.DIM
            t.first.setColorFilter(c, PorterDuff.Mode.SRC_IN)
            t.second.setTextColor(c)
        }
        col.removeAllViews()
        val s = stats
        if (s == null) {
            col.addView(pageTitle(" "))
            return
        }
        when (tab) {
            TODAY -> renderToday(s)
            TRENDS -> renderTrends(s)
            SCIENCE -> renderScience(s)
            APPS -> renderApps()
        }
    }

    // ---------- Today ----------

    private fun renderToday(s: Stats) {
        col.addView(pageTitle("Today"))
        col.addView(tv(LocalDate.now().format(dateLong), 15f, Pal.MUTED))

        if (!a11y || !usage) setup()
        latestBuild?.let { updateCard(it) }

        val today = s.days[0]
        if (usage) {
            col.addView(tv(fmtMin(today.totalMin), 56f, Pal.INK, Fonts.BIG).apply {
                letterSpacing = -0.02f
                setPadding(0, dp(28), 0, 0)
            })
            col.addView(tv("on selected apps", 15f, Pal.MUTED))
            if (s.avg7 >= 1f) col.addView(tv("Your daily average: ${Math.round(s.avg7)} min", 15f, Pal.MUTED))
        }

        col.addView(
            metricRow(
                metric(today.count(Ev.ATTEMPT).toString(), "Opens"),
                metric(today.count(Ev.CLOSE).toString(), "Closed"),
                metric(today.count(Ev.CONTINUE).toString(), "Opened anyway"),
            ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(24) }
        )

        val apps = (today.minutes.filterValues { it >= 60_000 }.keys + today.counts.keys)
            .distinct()
            .sortedByDescending { today.minutes[it] ?: 0L }
        if (apps.isNotEmpty()) {
            col.addView(label("By app"))
            val c = card()
            val maxApp = apps.maxOf { today.minutes[it] ?: 0L }.coerceAtLeast(1L)
            for (pkg in apps) {
                val ms = today.minutes[pkg] ?: 0L
                c.addView(appBar(s.labels[pkg] ?: pkg, fmtMin(ms / 60_000), ms.toFloat() / maxApp))
            }
            col.addView(c)
        }

        if (a11y && today.gates == 0) {
            col.addView(small("Open one of your selected apps to see the pause.").apply { setPadding(0, dp(16), 0, 0) })
        }
    }

    private fun setup() {
        val c = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) }
        c.addView(tv("Finish setup", 18f, Pal.INK, Fonts.MED))
        var step = 1
        if (!a11y) {
            c.addView(body("${step++}. Turn on the pause. Accessibility > Installed apps > ${getString(R.string.app_name)} > On."))
            c.addView(button("Open Accessibility", primary = true) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            c.addView(small("If it says \"Restricted setting\": App info > ⋮ > Allow restricted settings. Then try again."))
            c.addView(button("Open App info", primary = false) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        }
        if (!usage) {
            c.addView(body("$step. Allow usage access, so the app can show your minutes.").apply {
                if (!a11y) setPadding(0, dp(20), 0, 0)
            })
            c.addView(button("Open Usage access", primary = a11y) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }
        col.addView(c)
    }

    private fun updateCard(latest: Int) {
        val c = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) }
        c.addView(tv("Update available", 18f, Pal.INK, Fonts.MED))
        c.addView(body("Build $latest is ready. You have build ${currentBuild()}."))
        c.addView(button("Download", primary = true) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
        })
        c.addView(small("Install it over this one. Your data stays."))
        col.addView(c)
    }

    // ---------- Trends ----------

    private fun renderTrends(s: Stats) {
        col.addView(pageTitle("Trends"))
        if (!usage) {
            col.addView(body("Allow usage access on the Today tab to see your minutes."))
            return
        }
        val week = s.days.take(7).reversed()   // oldest .. today
        val avg = s.avg7

        col.addView(label("Minutes per day"))
        val c = card()
        c.addView(BarChart(this).apply {
            values = week.map { it.totalMin.toFloat() }
            labelsTop = week.map { it.date.format(dayAbbr) }
            labelsBottom = week.map { it.date.format(dayNum) }
            highlight = week.size - 1
            average = avg
            averageLabel = "avg ${Math.round(avg)}"
            valueText = { Math.round(it).toString() }
        })
        col.addView(c)
        col.addView(
            metricRow(
                metric(fmtHours(week.sumOf { it.totalMin }), "Last 7 days"),
                metric("${Math.round(avg)} min", "Daily average"),
            )
        )

        val yearMin = avg * 365
        col.addView(label("At this pace"))
        col.addView(
            metricRow(
                metric(fmtHours(Math.round(avg * 30).toLong()), "Per month"),
                metric(fmtHours(Math.round(yearMin).toLong()), "Per year"),
            )
        )
        col.addView(
            small(
                "That is ${String.format(EN, "%.0f", yearMin / 960)} waking days a year (16 h each). " +
                    "Your daily average × days, not a forecast."
            )
        )
        val so = card()
        so.addView(kv("This month so far", "≈ " + fmtHours(s.monthMin)))
        so.addView(divider())
        so.addView(kv("This year so far", "≈ " + fmtHours(s.yearMin)))
        col.addView(so)
        col.addView(small("From Android's long-term records. Less exact than the daily numbers."))

        if (s.since > 0) {
            val sinceDay = Instant.ofEpochMilli(s.since).atZone(ZoneId.systemDefault()).toLocalDate()
            col.addView(label("Pauses you closed"))
            val g = card()
            g.addView(BarChart(this).apply {
                values = week.map { d -> if (d.gates > 0) d.count(Ev.CLOSE) * 100f / d.gates else 0f }
                labelsTop = week.map { it.date.format(dayAbbr) }
                labelsBottom = week.map { it.date.format(dayNum) }
                highlight = week.size - 1
                fixedMax = 100f
                missing = week.indices.filter { week[it].date.isBefore(sinceDay) || week[it].gates == 0 }.toSet()
                valueText = { "${Math.round(it)}%" }
            })
            col.addView(g)
            col.addView(small("Share of pauses where you chose Close. Higher is better."))
        }
    }

    // ---------- Science ----------

    private fun renderScience(s: Stats) {
        col.addView(pageTitle("Science"))
        col.addView(tv("Everything in this app is here because a study found it works. Nothing else is added.", 15f, Pal.MUTED))

        // Your own experiment.
        col.addView(label("Your experiment"))
        val e = s.exp
        val c = card()
        c.addView(kv("Before the pause", e.beforeAvg?.let { "${Math.round(it)} min / day" } ?: "No data"))
        c.addView(divider())
        c.addView(kv("With the pause", e.afterAvg?.let { "${Math.round(it)} min / day" } ?: "Collecting"))
        val before = e.beforeAvg
        val after = e.afterAvg
        if (before != null && after != null && before > 0f) {
            val change = Math.round((after - before) / before * 100)
            c.addView(tv(if (change <= 0) "−${-change} %" else "+$change %", 48f, Pal.INK, Fonts.BIG).apply {
                setPadding(0, dp(16), 0, 0)
            })
            c.addView(tv("change in daily minutes", 14f, Pal.MUTED))
        }
        c.addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) })
        val rate = if (e.totalGates > 0) "${e.totalClosed * 100 / e.totalGates} %" else "None yet"
        c.addView(kv("Pauses you closed", rate))
        col.addView(c)
        col.addView(
            small(
                when {
                    s.since == 0L -> "Starts when you turn on the pause."
                    e.afterDays < 7 -> "Day ${e.afterDays} of 7 with the pause. One week before and one week after " +
                        "is the minimum to compare. Before: ${e.beforeDays} days."
                    else -> "${e.beforeDays} days before, ${e.afterDays} days with the pause. " +
                        "This is a test on one person, so it tells you about you, not about people in general."
                }
            )
        )
        col.addView(small("For comparison: in the one sec study, people closed 36 % of pauses."))

        // Why each part exists.
        col.addView(label("Why it works"))
        evidence(
            "A pause before the app opens",
            "280 people, 6 weeks. People closed the app 36 % of the times the pause appeared, " +
                "and tried to open it 37 % less by week 6. Together: 57 % fewer opens.",
            "Grüning et al. 2023, PNAS",
            "https://doi.org/10.1073/pnas.2213114120"
        )
        evidence(
            "Close is the big button",
            "In the same study the option to close was the strongest part. The waiting time helped too, but less.",
            "Grüning et al. 2023, PNAS",
            "https://doi.org/10.1073/pnas.2213114120"
        )
        evidence(
            "No motivational messages",
            "A message asking you to reflect had no effect in the same study, so the pause has none.",
            "Grüning et al. 2023, PNAS",
            "https://doi.org/10.1073/pnas.2213114120"
        )
        evidence(
            "Removing an app takes 24 hours",
            "Letting people set limits on their future use clearly reduced use. Self-control problems " +
                "explained about 31 % of social media use. So the rules are set in advance and can't be loosened on the spot.",
            "Allcott, Gentzkow & Song 2022, AER",
            "https://doi.org/10.1257/aer.20210867"
        )
        evidence(
            "Numbers alone don't do it",
            "112 students. The group that only tracked their screen time did not use their phone less. " +
                "The numbers here are for checking if the pause works, not the cure.",
            "Zimmermann & Sobolev 2023",
            "https://doi.org/10.1089/cyber.2022.0027"
        )

        // Optional extra with evidence.
        col.addView(label("Add this yourself"))
        val gs = card()
        gs.addView(tv("Grayscale", 16f, Pal.INK, Fonts.MED))
        gs.addView(
            body(
                "Same 112-student study: grayscale cut screen time right away, more than time limits did. " +
                    "Android can't let an app switch it on, so do it in settings. On Samsung: " +
                    "Accessibility > Vision enhancements > Colour adjustment > Greyscale."
            )
        )
        gs.addView(source("Zimmermann & Sobolev 2023", "https://doi.org/10.1089/cyber.2022.0027"))
        gs.addView(button("Open Accessibility", primary = false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(gs)

        // Honest limits.
        col.addView(label("Not proven"))
        val np = card()
        np.addView(body("Most studies last a few weeks. Nobody has shown that a tool like this makes you stop for good.", Pal.INK))
        np.addView(
            body(
                "People who switched the pause off went back to their old use quickly. " +
                    "It works while it is on."
            ).apply { setPadding(0, dp(12), 0, 0) }
        )
        np.addView(source("Haliburton et al. 2024, CHI", "https://doi.org/10.1145/3613904.3642370"))
        col.addView(np)
    }

    private fun evidence(title: String, finding: String, cite: String, url: String) {
        val c = card()
        c.addView(tv(title, 16f, Pal.INK, Fonts.MED))
        c.addView(body(finding))
        c.addView(source(cite, url))
        col.addView(c)
    }

    private fun source(cite: String, url: String): TextView =
        tv(cite, 13f, Pal.MUTED).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(0, dp(10), 0, dp(2))
            isClickable = true
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }

    // ---------- Apps ----------

    private fun renderApps() {
        col.addView(pageTitle("Apps"))
        col.addView(
            tv(
                "The pause shows up when you open a selected app. Adding works right away. " +
                    "Removing takes 24 hours. Tap again within that time to cancel.",
                15f, Pal.MUTED
            )
        )
        val list = apps
        if (list == null) {
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
                    if (tab == APPS) render()
                }
            }.start()
            return
        }

        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val byName = compareBy<Pair<String, String>> { it.second.lowercase(EN) }
        val selected = list.filter { it.first in targets }.sortedWith(byName)
        val others = list.filter { it.first !in targets }.sortedWith(byName)

        col.addView(label("Selected"))
        val sc = card()
        if (selected.isEmpty()) sc.addView(body("None."))
        for ((i, a) in selected.withIndex()) {
            if (i > 0) sc.addView(divider())
            sc.addView(appRow(a.first, a.second, on = true, pendingUntil = pending[a.first]))
        }
        col.addView(sc)

        col.addView(label("Other apps"))
        val oc = card()
        for ((i, a) in others.withIndex()) {
            if (i > 0) oc.addView(divider())
            oc.addView(appRow(a.first, a.second, on = false, pendingUntil = null))
        }
        col.addView(oc)

        col.addView(
            small("This app can't stop you from turning it off or deleting it. It only makes each open a choice.")
                .apply { setPadding(0, dp(24), 0, 0) }
        )
        col.addView(small("Build ${currentBuild()}"))
    }

    private fun appRow(pkg: String, label: String, on: Boolean, pendingUntil: Long?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(tv(label, 16f, if (on) Pal.INK else Pal.MUTED))
        if (pendingUntil != null) {
            text.addView(tv("Removed in ${fmtLeft(pendingUntil)}. Tap to cancel.", 13f, Pal.MUTED))
        }
        row.addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))

        // Round check: filled white = selected, outline = not, dashed-looking grey = leaving.
        val box = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                when {
                    pendingUntil != null -> { setColor(Pal.DIM); setStroke(dp(2), Pal.DIM) }
                    on -> { setColor(Pal.INK); setStroke(dp(2), Pal.INK) }
                    else -> { setColor(Pal.BG); setStroke(dp(2), Pal.DIM) }
                }
            }
        }
        row.addView(box, LinearLayout.LayoutParams(dp(22), dp(22)).apply { leftMargin = dp(12) })

        row.setOnClickListener {
            when {
                !on -> Targets.add(this, pkg)
                pendingUntil != null -> Targets.cancelRemove(this, pkg)
                else -> Targets.requestRemove(this, pkg)
            }
            val y = scroll.scrollY
            render()
            scroll.post { scroll.scrollTo(0, y) }
        }
        return row
    }

    // ---------- data ----------

    private fun compute(): Stats {
        val targets = Targets.active(this)
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
        val since = KitkaService.since(this)
        return Stats(days, targets, labels, monthMin, yearMin, since, experiment(days, since))
    }

    /**
     * Before/after comparison. Android keeps detailed history for only about a week,
     * so daily totals are saved here as soon as a day is complete.
     */
    private fun experiment(days: List<Day>, since: Long): Experiment {
        val all = Db.get(this).counts(LocalDate.of(2000, 1, 1)).values.flatMap { it.values }
        val totalGates = all.sumOf { (it[Ev.ATTEMPT] ?: 0) + (it[Ev.RECHECK] ?: 0) }
        val totalClosed = all.sumOf { it[Ev.CLOSE] ?: 0 }
        if (since == 0L || !usage) return Experiment(null, 0, null, 0, totalGates, totalClosed)

        val p = getSharedPreferences("kitka", Context.MODE_PRIVATE)
        val sinceDay = Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).toLocalDate()
        val complete = days.drop(1)  // yesterday and older

        // Baseline: complete days before the pause was turned on. Saved once.
        if (!p.contains("baseline_days")) {
            val before = complete.filter { it.date.isBefore(sinceDay) }
            if (before.isNotEmpty()) {
                p.edit()
                    .putFloat("baseline_avg", before.sumOf { it.totalMin } / before.size.toFloat())
                    .putInt("baseline_days", before.size)
                    .apply()
            }
        }

        // After: complete days after the day it was turned on. Accumulated over time.
        val saved = HashMap<String, Long>()
        for (s in p.getStringSet("after_days", emptySet()) ?: emptySet()) {
            val parts = s.split('|')
            if (parts.size == 2) parts[1].toLongOrNull()?.let { saved[parts[0]] = it }
        }
        var changed = false
        for (d in complete) {
            if (d.date.isAfter(sinceDay) && d.date.toString() !in saved) {
                saved[d.date.toString()] = d.totalMin
                changed = true
            }
        }
        if (changed) p.edit().putStringSet("after_days", saved.map { "${it.key}|${it.value}" }.toHashSet()).apply()

        val bDays = p.getInt("baseline_days", 0)
        return Experiment(
            beforeAvg = if (bDays > 0) p.getFloat("baseline_avg", 0f) else null,
            beforeDays = bDays,
            afterAvg = if (saved.isNotEmpty()) saved.values.sum() / saved.size.toFloat() else null,
            afterDays = saved.size,
            totalGates = totalGates,
            totalClosed = totalClosed,
        )
    }

    /** Asks GitHub for the newest build. If newer, Today shows an update card. */
    private fun checkUpdate() {
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
            if (latest != null && latest > currentBuild()) {
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    latestBuild = latest
                    if (tab == TODAY) render()
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

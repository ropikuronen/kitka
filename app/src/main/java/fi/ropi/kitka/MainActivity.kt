package fi.ropi.kitka

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One screen, four tabs along the bottom: Today, Week, Pace, Guarded.
 * Data is computed once per resume and each tab renders from that.
 */
class MainActivity : Activity() {

    companion object {
        const val APK_URL = "https://github.com/ropikuronen/kitka/releases/latest/download/app.apk"
        private const val TODAY = 0
        private const val WEEK = 1
        private const val PACE = 2
        private const val GUARDED = 3
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

    private lateinit var scroll: ScrollView
    private lateinit var col: LinearLayout
    private val tabViews = ArrayList<Triple<LinearLayout, ImageView, android.widget.TextView>>()

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
        root.addView(topBar())
        root.addView(View(this).apply { setBackgroundColor(Pal.LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)))

        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(col)
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(Pal.LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)))
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

    // ---------- chrome ----------

    private fun topBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Pal.SURFACE)
            addView(helm(44))
            val t = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            t.addView(tv(getString(R.string.app_name), 24f, Pal.INK, Fonts.HEAD))
            t.addView(tv(LocalDate.now().format(dateLong), 13f, Pal.MUTED))
            addView(t)
        }

    private fun bottomNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Pal.SURFACE)
            setPadding(dp(4), dp(8), dp(4), dp(10))
        }
        val items = listOf(
            R.drawable.tab_gate to "TODAY",
            R.drawable.tab_week to "WEEK",
            R.drawable.tab_pace to "PACE",
            R.drawable.tab_guard to "GUARDED",
        )
        tabViews.clear()
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(4))
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
            cell.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
            val label = tv(item.second, 11f, Pal.MUTED, Fonts.BODY_MED).apply {
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            }
            cell.addView(label)
            bar.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            tabViews.add(Triple(cell, icon, label))
        }
        return bar
    }

    private fun paintTabs() {
        for ((i, t) in tabViews.withIndex()) {
            val on = i == tab
            t.second.setColorFilter(if (on) Pal.CRIMSON else Pal.MUTED, PorterDuff.Mode.SRC_IN)
            t.third.setTextColor(if (on) Pal.INK else Pal.MUTED)
            t.first.background = if (on) GradientDrawable().apply {
                setColor(Pal.BG)
                cornerRadius = dpf(8f)
            } else null
        }
    }

    private fun render() {
        paintTabs()
        col.removeAllViews()
        val s = stats
        if (s == null) {
            col.addView(note("Counting.").apply { setPadding(0, dp(24), 0, 0) })
            return
        }
        when (tab) {
            TODAY -> renderToday(s)
            WEEK -> renderWeek(s)
            PACE -> renderPace(s)
            GUARDED -> renderGuarded(s)
        }
    }

    // ---------- TODAY ----------

    private fun renderToday(s: Stats) {
        if (!a11y || !usage) setup()
        latestBuild?.let { updatePanel(it) }

        val today = s.days[0]

        // Hero: today's minutes.
        val hero = panel().apply { gravity = Gravity.CENTER_HORIZONTAL }
        if (usage) {
            hero.addView(tv(fmtMin(today.totalMin), 52f, Pal.INK, Fonts.NUM).apply { gravity = Gravity.CENTER })
            hero.addView(tv("on guarded apps today", 15f, Pal.MUTED).apply { gravity = Gravity.CENTER })
        } else {
            hero.addView(tv("Allow usage access to see minutes.", 15f, Pal.MUTED))
        }
        hero.addView(
            tv(if (a11y) "Gate is on" else "Gate is off", 13f, if (a11y) Pal.BRASS else Pal.CRIMSON, Fonts.BODY_MED)
                .apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0); letterSpacing = 0.05f }
        )
        col.addView(hero)

        col.addView(
            tileRow(
                tile(today.count(Ev.ATTEMPT).toString(), "OPENS"),
                tile(today.count(Ev.CLOSE).toString(), "CLOSED", Pal.CRIMSON),
                tile(today.count(Ev.CONTINUE).toString(), "CONTINUED"),
            )
        )
        val gates = today.gates
        col.addView(
            note(
                if (gates > 0) {
                    "You closed ${today.count(Ev.CLOSE)} of $gates gates (${today.count(Ev.CLOSE) * 100 / gates} %). " +
                        "About ${today.count(Ev.SCROLL)} swipes after continuing."
                } else {
                    "No gates yet today. Open a guarded app to test it."
                }
            ).apply { setPadding(dp(2), dp(10), dp(2), 0) }
        )

        val apps = (today.minutes.filterValues { it >= 60_000 }.keys + today.counts.keys)
            .distinct()
            .sortedByDescending { today.minutes[it] ?: 0L }
        if (apps.isNotEmpty()) {
            val p = panel("By app")
            val maxApp = apps.maxOf { today.minutes[it] ?: 0L }.coerceAtLeast(1L)
            for ((i, pkg) in apps.withIndex()) {
                val ms = today.minutes[pkg] ?: 0L
                val o = today.counts[pkg]?.get(Ev.ATTEMPT) ?: 0
                p.addView(
                    appBar(
                        s.labels[pkg] ?: pkg,
                        "${fmtMin(ms / 60_000)}   $o ${if (o == 1) "open" else "opens"}",
                        ms.toFloat() / maxApp,
                        accent = i == 0
                    )
                )
            }
            col.addView(p)
        }

        if (s.since > 0) {
            val at = Instant.ofEpochMilli(s.since).atZone(ZoneId.systemDefault()).format(sinceFmt)
            col.addView(
                note("Opens are counted since the gate was turned on ($at). Minutes come from Android's own records and go back further.")
                    .apply { setPadding(dp(2), dp(14), dp(2), 0) }
            )
        }
    }

    private fun setup() {
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
            p.addView(note("Android already records how long each app is open. This lets ScrollSlayer read it."))
            p.addView(button("Open Usage access", primary = a11y) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }
        col.addView(p)
    }

    private fun updatePanel(latest: Int) {
        val p = panel("Update available")
        p.addView(tv("Build $latest is out. You have build ${currentBuild()}.", 15f, Pal.MUTED))
        p.addView(button("Download build $latest", primary = true) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
        })
        p.addView(note("Your browser downloads it. Open the file and install over this one. Your data stays."))
        col.addView(p)
    }

    // ---------- WEEK ----------

    private fun renderWeek(s: Stats) {
        if (!usage) {
            col.addView(panel("Week").apply { addView(note("Allow usage access to see minutes.")) })
            return
        }
        val week = s.days.take(7).reversed()          // oldest .. today
        val full = s.days.drop(1).take(7)              // last 7 complete days
        val avg = full.sumOf { it.totalMin } / 7f

        val w = panel("Minutes per day")
        w.addView(BarChart(this).apply {
            values = week.map { it.totalMin.toFloat() }
            labelsTop = week.map { it.date.format(dayAbbr) }
            labelsBottom = week.map { it.date.format(dayNum) }
            highlight = week.size - 1
            average = avg
            averageLabel = "avg ${Math.round(avg)} min"
            valueText = { Math.round(it).toString() }
        })
        w.addView(note("Guarded apps only. Dashed line is your average over the last 7 full days."))
        col.addView(w)

        col.addView(
            tileRow(
                tile(fmtHours(week.sumOf { it.totalMin }), "THIS WEEK"),
                tile("${Math.round(avg)}", "MIN / DAY AVG"),
            )
        )

        if (s.since > 0) {
            val sinceDay = Instant.ofEpochMilli(s.since).atZone(ZoneId.systemDefault()).toLocalDate()
            val r = panel("Gates closed")
            r.addView(BarChart(this).apply {
                values = week.map { d -> if (d.gates > 0) d.count(Ev.CLOSE) * 100f / d.gates else 0f }
                labelsTop = week.map { it.date.format(dayAbbr) }
                labelsBottom = week.map { it.date.format(dayNum) }
                highlight = week.size - 1
                fixedMax = 100f
                missing = week.indices.filter { week[it].date.isBefore(sinceDay) || week[it].gates == 0 }.toSet()
                valueText = { "${Math.round(it)}%" }
            })
            r.addView(note("Share of gates you closed each day. This is the number that shows whether it works for you. Grey dash = no gates that day."))
            col.addView(r)
        }
    }

    // ---------- PACE ----------

    private fun renderPace(s: Stats) {
        if (!usage) {
            col.addView(panel("Pace").apply { addView(note("Allow usage access to see minutes.")) })
            return
        }
        val avg = s.days.drop(1).take(7).sumOf { it.totalMin } / 7f
        val yearMin = avg * 365

        val hero = panel().apply { gravity = Gravity.CENTER_HORIZONTAL }
        hero.addView(tv(fmtHours(Math.round(yearMin).toLong()), 52f, Pal.CRIMSON, Fonts.NUM).apply { gravity = Gravity.CENTER })
        hero.addView(tv("per year at your current pace", 15f, Pal.MUTED).apply { gravity = Gravity.CENTER })
        col.addView(hero)

        col.addView(
            tileRow(
                tile(String.format(EN, "%.1f", yearMin / 960), "WAKING DAYS"),
                tile(String.format(EN, "%.1f", yearMin / 1440), "FULL DAYS"),
                tile(fmtHours(Math.round(avg * 30).toLong()), "PER MONTH"),
            )
        )

        val p = panel("So far")
        p.addView(kv("This month", "≈ " + fmtHours(s.monthMin)))
        p.addView(divider())
        p.addView(kv("This year", "≈ " + fmtHours(s.yearMin)))
        col.addView(p)

        col.addView(
            note(
                "Pace = your average over the last 7 full days (${Math.round(avg)} min a day) × days. " +
                    "Arithmetic, not a forecast. Waking day = 16 h. " +
                    "\"So far\" uses Android's long-term records, which are coarser, so treat them as approximate."
            ).apply { setPadding(dp(2), dp(14), dp(2), 0) }
        )
    }

    // ---------- GUARDED ----------

    private fun renderGuarded(s: Stats) {
        col.addView(
            note(
                "Tap an app to guard it. Guarding starts at once. Un-guarding takes 24 hours, " +
                    "so you cannot loosen the rules on an impulse. Tap again within those 24 hours to cancel."
            ).apply { setPadding(dp(2), dp(14), dp(2), 0) }
        )
        val list = apps
        if (list == null) {
            col.addView(note("Loading apps."))
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
                    if (tab == GUARDED) render()
                }
            }.start()
            return
        }

        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val byName = compareBy<Pair<String, String>> { it.second.lowercase(EN) }
        val guarded = list.filter { it.first in targets }.sortedWith(byName)
        val others = list.filter { it.first !in targets }.sortedWith(byName)

        val g = panel("Guarded (${guarded.size})")
        if (guarded.isEmpty()) g.addView(note("Nothing guarded."))
        for ((i, a) in guarded.withIndex()) {
            if (i > 0) g.addView(divider())
            g.addView(appRow(a.first, a.second, on = true, pendingUntil = pending[a.first]))
        }
        col.addView(g)

        val o = panel("Other apps")
        for ((i, a) in others.withIndex()) {
            if (i > 0) o.addView(divider())
            o.addView(appRow(a.first, a.second, on = false, pendingUntil = null))
        }
        col.addView(o)

        col.addView(
            note(
                "ScrollSlayer cannot stop you from turning it off or uninstalling it. " +
                    "It turns every open into a choice and shows you what you chose."
            ).apply { setPadding(dp(2), dp(18), dp(2), 0) }
        )
        col.addView(note("Build ${currentBuild()}").apply { setPadding(dp(2), dp(8), 0, 0) })
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

        // Checkbox: crimson square = guarded, empty frame = not, brass frame = leaving.
        val box = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(4f)
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
            val y = scroll.scrollY
            render()
            scroll.post { scroll.scrollTo(0, y) }
        }
        return row
    }

    // ---------- data ----------

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

    /** Asks GitHub for the newest build. If newer, Today shows an Update panel. */
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

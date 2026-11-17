package fi.ropi.kitka

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    companion object {
        const val APK_URL = "https://github.com/ropikuronen/kitka/releases/latest/download/app.apk"
        const val TODAY = 0; const val HISTORY = 1; const val SCIENCE = 2; const val RULES = 3
        const val URGE = 4; const val CREATOR = 5
    }

    private lateinit var scroll: ScrollView
    private lateinit var col: LinearLayout
    private lateinit var overlayLayer: FrameLayout
    private val tabViews = ArrayList<Pair<ImageView, TextView>>()
    private val handler = Handler(Looper.getMainLooper())

    private var page = TODAY
    private var stats: Stats? = null
    private var latestBuild: Int? = null
    private var tourStep = -1
    private val tourTargets = HashMap<String, View>()

    // Urge timer
    private var urgeState = 0 // 0 idle, 1 running, 2 done
    private var urgeLeft = 90

    // Rules: show all apps
    private var showAllApps = false
    private var appQuery = ""
    private var allApps: List<Pair<String, String>>? = null

    private val dayAbbr = DateTimeFormatter.ofPattern("EEE", EN)
    private val dayNum = DateTimeFormatter.ofPattern("d.M.", EN)
    private val headDate = DateTimeFormatter.ofPattern("EEE d MMM", EN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        if (!Store.onboarded(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        page = savedInstanceState?.getInt("page") ?: TODAY
        handleIntent(intent)

        val root = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        scroll = ScrollView(this).apply { addView(col) }
        main.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        main.addView(divider())
        main.addView(bottomNav())
        root.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))
        overlayLayer = FrameLayout(this)
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        if (!Store.tourDone(this)) tourStep = 0
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        if (::col.isInitialized) render()
    }

    private fun handleIntent(i: Intent?) {
        when (i?.getStringExtra("open")) {
            "urge" -> page = URGE
            "proof" -> page = TODAY
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    override fun onResume() {
        super.onResume()
        if (!::col.isInitialized) return
        Proof.schedule(this)
        render()
        Thread {
            val s = Stats.compute(this)
            runOnUiThread { if (!isFinishing) { stats = s; render() } }
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
        val items = listOf(R.drawable.tab_today to "Today", R.drawable.tab_history to "History",
            R.drawable.tab_science to "Science", R.drawable.tab_rules to "Rules")
        tabViews.clear()
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(4))
                isClickable = true
                setOnClickListener { endTour(); open(i) }
            }
            val icon = ImageView(this).apply { setImageResource(item.first) }
            cell.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
            val label = tv(item.second, 12f, Pal.DIM, Fonts.MED).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
            cell.addView(label)
            bar.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            tabViews.add(icon to label)
            tourTargets["tab$i"] = cell
        }
        return bar
    }

    private fun open(p: Int) {
        page = p
        render()
        scroll.scrollTo(0, 0)
    }

    private fun render() {
        for ((i, t) in tabViews.withIndex()) {
            val c = if (i == page) Pal.INK else Pal.DIM
            t.first.setColorFilter(c, PorterDuff.Mode.SRC_IN)
            t.second.setTextColor(c)
        }
        col.removeAllViews()
        tourTargets.keys.removeAll { !it.startsWith("tab") }
        val s = stats
        when (page) {
            TODAY -> if (s != null) renderToday(s) else header()
            HISTORY -> if (s != null) renderHistory(s) else header()
            SCIENCE -> if (s != null) renderScience(s) else header()
            RULES -> renderRules()
            URGE -> renderUrge()
            CREATOR -> renderCreator()
        }
        renderTour()
    }

    private fun header() {
        val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        h.addView(caps("FinalApp", Pal.INK).apply { letterSpacing = 0.25f }, LinearLayout.LayoutParams(0, WRAP, 1f))
        h.addView(tv(LocalDate.now().format(headDate), 12f, Pal.MUTED))
        col.addView(h)
    }

    private fun pageTitle(t: String) = col.addView(big(t).apply { setPadding(0, dp(16), 0, 0) })

    private fun backTo(target: Int, label: String) {
        col.addView(tv("‹  $label", 15f, Pal.MUTED).apply {
            setPadding(0, dp(14), dp(20), dp(6))
            isClickable = true
            setOnClickListener { open(target) }
        }, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    // ---------- Today ----------

    private fun renderToday(s: Stats) {
        header()
        setupCard()
        latestBuild?.let { updateCard(it) }

        val t = s.today
        val a = card(top = 16)
        a.addView(tv("Today", 13f, Pal.MUTED, Fonts.MED))
        if (Perms.usage(this)) {
            a.addView(big(fmtMin(t.totalMin), 54f))
            a.addView(bar(t.totalMin.toFloat() / s.limit, 6).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) })
            val over = t.totalMin - s.limit
            a.addView(tv(if (over > 0) "$over min over your ${s.limit} min limit." else "${-over} min left of your ${s.limit} min limit.", 14f, Pal.SOFT).apply { setPadding(0, dp(8), 0, 0) })
        } else a.addView(body("Allow usage access to see your minutes."))
        col.addView(a); tourTargets["A"] = a

        // Urge entry
        val u = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Pal.INK, dpf(18f))
            isClickable = true
            setOnClickListener { endTour(); open(URGE) }
        }
        val circle = FrameLayout(this).apply { background = rounded(Pal.BG, dpf(22f)) }
        circle.addView(ImageView(this).apply { setImageResource(R.drawable.ic_wave) }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        u.addView(circle, LinearLayout.LayoutParams(dp(44), dp(44)))
        val ut = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        ut.addView(tv("Want to scroll right now?", 18f, Pal.BG, Fonts.BOLD))
        ut.addView(tv("Tap here when the urge hits.", 14f, Color.rgb(68, 68, 68)))
        u.addView(ut, LinearLayout.LayoutParams(0, WRAP, 1f))
        u.addView(tv("›", 26f, Pal.BG, Fonts.BOLD))
        col.addView(u, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        tourTargets["U"] = u

        // From the creator
        val c = card().apply { background = rounded(Pal.CARD, dpf(18f), Color.rgb(51, 51, 51), dp(1)); isClickable = true; setOnClickListener { endTour(); open(CREATOR) } }
        c.addView(caps("From the creator"))
        c.addView(tv("It's okay to feel uncomfortable. It's just a feeling. It will not harm you. Unmask it.", 18f, Pal.INK, Fonts.MED).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(tv("Read all advice", 14f, Pal.SOFT).apply { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG; setPadding(0, dp(8), 0, 0) })
        col.addView(c); tourTargets["C"] = c

        // Hourglass
        if (s.avg7 >= 1f) {
            val h = card()
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(Hourglass(this), LinearLayout.LayoutParams(dp(58), dp(88)))
            val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, 0) }
            ht.addView(big(String.format(EN, "%.1f years", s.yearsBy80), 36f))
            ht.addView(tv("of waking life by age 80, at your current pace.", 14f, Pal.SOFT).apply { setPadding(0, dp(4), 0, 0) })
            r.addView(ht, LinearLayout.LayoutParams(0, WRAP, 1f))
            h.addView(r)
            col.addView(h); tourTargets["B"] = h
        }

        // This week
        val w = card()
        w.addView(tv("This week", 13f, Pal.MUTED, Fonts.MED))
        val won = s.wonBackMin
        val wonText = when { won == null -> "–"; won >= 0 -> "+${fmtMin(won)}"; else -> "−${fmtMin(-won)}" }
        val rate = if (s.weekGates > 0) "${s.weekClosed * 100 / s.weekGates} %" else "–"
        w.addView(row(metric(wonText, "won back"), metric(rate, "pauses closed")).apply { setPadding(0, dp(8), 0, 0) })
        if (won == null) w.addView(small("Shown after your first full day with FinalApp."))
        col.addView(w); tourTargets["W"] = w

        // By app
        val apps = (t.minutes.filterValues { it >= 60_000 }.keys + t.counts.keys).distinct().sortedByDescending { t.minutes[it] ?: 0L }
        if (apps.isNotEmpty()) {
            val b = card()
            b.addView(tv("By app", 13f, Pal.MUTED, Fonts.MED))
            val maxMs = apps.maxOf { t.minutes[it] ?: 0L }.coerceAtLeast(1L)
            for (p in apps) {
                val ms = t.minutes[p] ?: 0L
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
                r.addView(tv(s.labels[p] ?: p, 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
                r.addView(tv(fmtMin(ms / 60_000), 15f, Pal.MUTED))
                b.addView(r)
                b.addView(bar(ms.toFloat() / maxMs))
            }
            col.addView(b)
        }
    }

    private fun setupCard() {
        val missing = ArrayList<Pair<String, () -> Unit>>()
        if (!Perms.accessibility(this)) missing.add("Turn on the pause (Accessibility)" to { Perms.openAccessibility(this) })
        if (!Perms.usage(this)) missing.add("Allow usage access" to { Perms.openUsage(this) })
        if (missing.isEmpty()) return
        val c = card(top = 16).apply { background = rounded(Pal.CARD, dpf(18f), Pal.INK, dp(1)) }
        c.addView(tv("The pause is not running", 17f, Pal.INK, Fonts.BOLD))
        c.addView(small("If it was switched off, your Daily Proof shows it."))
        for ((label, act) in missing) c.addView(button(label, primary = true) { act() })
        c.addView(underlineLink("Says \"Restricted setting\"? Open App info") { Perms.openAppInfo(this) })
        col.addView(c)
    }

    private fun updateCard(latest: Int) {
        val c = card(top = 16)
        c.addView(tv("Update available", 17f, Pal.INK, Fonts.BOLD))
        c.addView(body("Build $latest is ready. You have build ${currentBuild()}.", Pal.MUTED, 14f))
        c.addView(button("Download", primary = true) { Perms.openUrl(this, APK_URL) })
        c.addView(small("Install it over this one. Your data stays."))
        col.addView(c)
    }

    // ---------- History ----------

    private fun renderHistory(s: Stats) {
        header()
        pageTitle("History")
        val t = s.today
        col.addView(card(top = 16).apply {
            addView(row(metric("${t.count(Ev.ATTEMPT)}", "Opens today"), metric("${t.count(Ev.CLOSE)}", "Closed"), metric("${t.count(Ev.SCROLL)}", "Swipes")))
        })

        col.addView(label("Minutes per day"))
        val week = s.days.take(7).reversed()
        col.addView(card(top = 0).apply {
            addView(BarChart(this@MainActivity).apply {
                values = week.map { it.totalMin.toFloat() }
                labelsTop = week.map { it.date.format(dayAbbr) }
                labelsBottom = week.map { it.date.format(dayNum) }
                highlight = week.size - 1
                average = s.avg7
                averageLabel = "avg ${Math.round(s.avg7)}"
            })
        })

        col.addView(label("When you scroll (last 7 days)"))
        val hc = card(top = 0)
        val maxH = (s.hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val hours = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        for (h in 0 until 24) {
            val v = s.hours[h]
            val cell = FrameLayout(this)
            val barV = View(this).apply { background = rounded(if (v * 4 >= maxH * 3) Pal.INK else Pal.DIM, dpf(2f)) }
            cell.addView(barV, FrameLayout.LayoutParams(MATCH, (dp(80) * v / maxH).toInt().coerceAtLeast(dp(3)), Gravity.BOTTOM))
            hours.addView(cell, LinearLayout.LayoutParams(0, dp(80), 1f).apply { leftMargin = dp(1); rightMargin = dp(1) })
        }
        hc.addView(hours)
        hc.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (l in listOf("00", "06", "12", "18", "24")) addView(tv(l, 11f, Pal.MUTED).apply { gravity = if (l == "24") Gravity.END else Gravity.START }, LinearLayout.LayoutParams(0, WRAP, 1f))
            setPadding(0, dp(6), 0, 0)
        })
        val worst = (0 until 24).maxByOrNull { s.hours[it] } ?: 0
        if (s.hours[worst] > 0) hc.addView(small("Your worst hour: ${String.format(EN, "%02d", worst)} to ${String.format(EN, "%02d", (worst + 1) % 24)}."))
        col.addView(hc)

        val now = LocalDate.now()
        col.addView(label(now.month.getDisplayName(java.time.format.TextStyle.FULL, EN)))
        val cal = card(top = 0, pad = 14)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in listOf("M", "T", "W", "T", "F", "S", "S")) head.addView(tv(d, 11f, Pal.DIM).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, WRAP, 1f))
        cal.addView(head)
        val first = now.withDayOfMonth(1)
        val offset = first.dayOfWeek.value - 1
        val days = now.lengthOfMonth()
        val info = small("Lighter = more scrolling. Tap a day.")
        var cell = 0
        var r: LinearLayout? = null
        val total = ((offset + days + 6) / 7) * 7
        while (cell < total) {
            if (cell % 7 == 0) { r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }; cal.addView(r) }
            val dayN = cell - offset + 1
            val v: View = if (dayN in 1..days) {
                val date = first.withDayOfMonth(dayN)
                val m = s.history[date]
                val bg = when { date.isAfter(now) || m == null -> Color.rgb(17, 17, 17); m < 40 -> Color.rgb(31, 31, 31); m < 90 -> Pal.DIM; m < 140 -> Pal.MUTED; else -> Pal.INK }
                tv("$dayN", 12f, if (m != null && m >= 140) Pal.BG else if (date.isAfter(now)) Pal.DIM else Pal.SOFT).apply {
                    gravity = Gravity.CENTER
                    background = if (date == now) rounded(bg, dpf(6f), Pal.INK, dp(2)) else rounded(bg, dpf(6f))
                    isClickable = true
                    setOnClickListener { info.text = "${date.format(DateTimeFormatter.ofPattern("EEE d MMM", EN))}: " + (if (m != null) fmtMin(m) else "no data") }
                }
            } else View(this)
            r!!.addView(v, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            cell++
        }
        cal.addView(info)
        col.addView(cal)
    }

    // ---------- Science ----------

    private fun renderScience(s: Stats) {
        header()
        pageTitle("Science")
        val e = card(top = 16)
        e.addView(tv("Your 7-day test", 13f, Pal.MUTED, Fonts.MED))
        val b = s.beforeAvg; val a = s.afterAvg
        if (b != null && a != null && b > 0f) {
            val change = Math.round((a - b) / b * 100)
            e.addView(big(if (change <= 0) "−${-change} %" else "+$change %", 54f))
            e.addView(tv("${Math.round(b)} → ${Math.round(a)} min a day", 14f, Pal.SOFT))
        } else {
            e.addView(big("Collecting", 34f))
            e.addView(tv(if (b != null) "Before: ${Math.round(b)} min a day" else "Your before number is saved on day 1.", 14f, Pal.SOFT))
        }
        val days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        for (i in 0 until 7) days.addView(View(this).apply { background = rounded(if (i < s.afterDays) Pal.INK else Pal.LINE, dpf(3f)) },
            LinearLayout.LayoutParams(0, dp(6), 1f).apply { if (i > 0) leftMargin = dp(4) })
        e.addView(days)
        e.addView(small(if (s.afterDays >= 7) "${s.afterDays} days with FinalApp. A test on one person: it tells you about you." else "Day ${s.afterDays} of 7."))
        col.addView(e)

        val p = card().apply { background = rounded(Pal.INK, dpf(18f)) }
        p.addView(caps("Our promise", Pal.BG))
        p.addView(tv("Do exactly what FinalApp says for 7 days. If your scrolling doesn't drop, delete it and call the science useless.", 17f, Pal.BG, Fonts.MED).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(p)

        col.addView(label("Why every rule is here"))
        col.addView(studyList(Content.WHY_IT_WORKS))
    }

    private fun studyList(list: List<Content.Study>): LinearLayout {
        val c = card(top = 0, pad = 0)
        for ((i, st) in list.withIndex()) {
            if (i > 0) c.addView(divider())
            c.addView(expandable(st.title, st.number, st.text, st.cite, st.url) { Perms.openUrl(this, it) })
        }
        return c
    }

    // ---------- Rules ----------

    private fun renderRules() {
        header()
        pageTitle("Rules")
        col.addView(tv("Stricter right away. Easier only after 24 hours.", 15f, Pal.MUTED).apply { setPadding(0, dp(4), 0, 0) })

        col.addView(label("Apps"))
        val list = card(top = 0, pad = 0).apply { setPadding(dp(16), 0, dp(16), 0) }
        if (showAllApps) {
            val search = EditText(this).apply {
                hint = "Search apps"; setHintTextColor(Pal.DIM); setTextColor(Pal.INK); textSize = 16f; setSingleLine()
                setText(appQuery); setSelection(appQuery.length)
                background = rounded(Pal.BG, dpf(12f), Color.rgb(51, 51, 51), dp(1))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { appQuery = s?.toString() ?: ""; appRows(list) }
                })
            }
            col.addView(search, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) })
        }
        col.addView(list)
        appRows(list)

        col.addView(label("How it holds you"))
        val rc = card(top = 0, pad = 0).apply { setPadding(dp(16), 0, dp(16), 0) }
        for ((i, r) in Rules.ALL.withIndex()) {
            if (i > 0) rc.addView(divider())
            val cur = Rules.get(this, r)
            val pend = Rules.pending(this, r)
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                isClickable = true
                setOnClickListener { editRule(r) }
            }
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(tv(r.name, 16f))
            t.addView(tv(r.sub, 13f, Pal.MUTED))
            if (pend != null) t.addView(tv("Changes to ${Rules.format(r, pend.first)} in ${fmtLeft(pend.second)}. Tap to cancel.", 13f, Pal.SOFT))
            if (r == Rules.GRAY && !Perms.grayscale(this)) t.addView(tv("Setup needed. Tap for the command.", 13f, Pal.SOFT))
            rowV.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            rowV.addView(tv(Rules.format(r, cur), 15f, Pal.INK, android.graphics.Typeface.MONOSPACE).apply { setPadding(dp(12), 0, 0, 0) })
            rc.addView(rowV)
        }
        rc.addView(divider())
        rc.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, dp(14))
            val t = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            t.addView(tv("Daily Proof", 16f))
            t.addView(tv("Every evening. Sent to: you.", 13f, Pal.MUTED))
            addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(tv("21:00", 15f, Pal.INK, android.graphics.Typeface.MONOSPACE))
        })
        col.addView(rc)

        col.addView(small("100% free. Forever. We don't collect any data.").apply { setPadding(0, dp(18), 0, 0) })
        val sup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.TRANSPARENT, dpf(18f), Color.rgb(51, 51, 51), dp(1))
            isClickable = true
            setOnClickListener { Perms.openUrl(this@MainActivity, Content.SUPPORT_URL) }
        }
        sup.addView(ImageView(this).apply { setImageResource(R.drawable.ic_coffee) }, LinearLayout.LayoutParams(dp(24), dp(24)))
        val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        st.addView(tv("Support the creator", 16f, Pal.INK, Fonts.MED))
        st.addView(tv("FinalApp stays free. If it helps you, buy me a coffee.", 13f, Pal.MUTED))
        sup.addView(st, LinearLayout.LayoutParams(0, WRAP, 1f))
        sup.addView(tv("↗", 18f, Pal.MUTED))
        col.addView(sup, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        col.addView(small("Build ${currentBuild()}"))
    }

    private fun appRows(list: LinearLayout) {
        list.removeAllViews()
        val targets = Targets.active(this)
        val pending = Targets.pendingRemovals(this)
        val apps = allApps ?: Perms.installedApps(this).also { allApps = it }
        val q = appQuery.trim().lowercase(EN)
        val shown = if (showAllApps) apps.filter { q.isEmpty() || it.second.lowercase(EN).contains(q) }.sortedBy { it.first !in targets }
                    else apps.filter { it.first in targets }
        for ((pkg, name) in shown) {
            val on = pkg in targets
            val pend = pending[pkg]
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                isClickable = true
                setOnClickListener {
                    when {
                        !on -> Targets.add(this@MainActivity, pkg)
                        pend != null -> Targets.cancelRemove(this@MainActivity, pkg)
                        else -> Targets.requestRemove(this@MainActivity, pkg)
                    }
                    appRows(list)
                }
            }
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(tv(name, 16f, if (on) Pal.INK else Pal.MUTED))
            if (pend != null) t.addView(tv("Leaves in ${fmtLeft(pend)}. Tap to cancel.", 13f, Pal.MUTED))
            r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(dot(on, leaving = pend != null))
            list.addView(r)
            list.addView(divider())
        }
        list.addView(tv(if (showAllApps) "Show selected only" else "More…", 16f, Pal.SOFT).apply {
            setPadding(0, dp(15), 0, dp(15))
            isClickable = true
            setOnClickListener { showAllApps = !showAllApps; appQuery = ""; render() }
        })
    }

    private fun editRule(r: Rules.Rule) {
        val pend = Rules.pending(this, r)
        if (pend != null) { Rules.cancelPending(this, r); render(); return }
        if (r == Rules.GRAY && !Perms.grayscale(this)) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Grayscale setup")
                .setMessage("Android only lets a computer give this permission. Turn on USB debugging, plug in the phone and run:\n\n${Grayscale.ADB}")
                .setPositiveButton("Copy command") { _, _ -> Perms.copy(this, Grayscale.ADB) }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        val labels = r.options.map { Rules.format(r, it) }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(r.name)
            .setItems(labels) { _, which ->
                val applied = Rules.set(this, r, r.options[which])
                if (!applied) AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("Easier rules wait 24 hours. It changes then, unless you cancel it.")
                    .setPositiveButton("OK", null).show()
                render()
            }
            .show()
    }

    // ---------- Urge ----------

    private val urgeTick = object : Runnable {
        override fun run() {
            if (urgeState != 1) return
            urgeLeft--
            if (urgeLeft <= 0) { urgeState = 2; urgeLeft = 0 }
            if (page == URGE) render()
            if (urgeState == 1) handler.postDelayed(this, 1000)
        }
    }

    private fun renderUrge() {
        backTo(TODAY, "Today")
        col.addView(big("Urge"))
        val steps = listOf("Start the timer. Put the phone down.", "Don't fight the urge. Notice where you feel it.", "Watch it rise, peak and pass.")
        for ((i, st) in steps.withIndex()) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            r.addView(tv("${i + 1}", 16f, Pal.MUTED, Fonts.BOLD), LinearLayout.LayoutParams(dp(24), WRAP))
            r.addView(tv(st, 16f), LinearLayout.LayoutParams(0, WRAP, 1f))
            col.addView(r)
        }
        when (urgeState) {
            0 -> col.addView(tv("Start 90 seconds", 24f, Pal.BG, Fonts.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
                background = rounded(Pal.INK, dpf(18f))
                isClickable = true
                setOnClickListener { urgeState = 1; urgeLeft = 90; handler.removeCallbacks(urgeTick); handler.postDelayed(urgeTick, 1000); render() }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })
            1 -> col.addView(card(top = 18).apply {
                addView(big("${urgeLeft / 60}:${String.format(EN, "%02d", urgeLeft % 60)}", 64f))
                addView(bar((90 - urgeLeft) / 90f).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
                addView(body("Don't fight it. Notice where you feel it. Watch it rise, peak and pass.", Pal.INK, 17f).apply { setPadding(0, dp(16), 0, 0) })
            })
            else -> col.addView(card(top = 18).apply {
                background = rounded(Pal.INK, dpf(18f))
                addView(tv("It passed.", 28f, Pal.BG, Fonts.BOLD))
                addView(tv("90 seconds, and you didn't open anything.", 16f, Pal.BG).apply { setPadding(0, dp(6), 0, 0) })
                addView(tv("Done", 15f, Pal.INK, Fonts.MED).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(22), dp(12), dp(22), dp(12))
                    background = rounded(Pal.BG, dpf(22f))
                    isClickable = true
                    setOnClickListener { urgeState = 0; urgeLeft = 90; render() }
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
            })
        }
        col.addView(small("Urge surfing. Smokers who learned it smoked less over the next week. Not tested on phones yet."))
        col.addView(underlineLink("Bowen & Marlatt 2009") { Perms.openUrl(this, "https://doi.org/10.1037/a0017127") })

        col.addView(label("Why you reach for it"))
        col.addView(studyList(Content.WHY_YOU_REACH))
        col.addView(label("What you get back"))
        col.addView(studyList(Content.WHAT_YOU_GET))
    }

    // ---------- From the creator ----------

    private fun renderCreator() {
        backTo(TODAY, "Today")
        col.addView(big("From the creator"))
        val c = card(top = 16, pad = 20)
        c.addView(tv(Content.CREATOR_HEAD, 22f, Pal.INK, Fonts.BOLD).apply { setLineSpacing(0f, 1.3f) })
        for (l in Content.CREATOR_MORE) c.addView(tv(l, 19f, Pal.SOFT).apply { setPadding(0, dp(12), 0, 0); setLineSpacing(0f, 1.4f) })
        c.addView(tv(Content.CREATOR_LAST, 19f, Pal.INK, Fonts.BOLD).apply { setPadding(0, dp(12), 0, 0) })
        c.addView(small("Not a study. What worked for me."))
        col.addView(c)
        for (t in Content.TIPS) {
            val k = card()
            k.addView(tv(t.title, 20f, Pal.INK, Fonts.BOLD).apply { setLineSpacing(0f, 1.25f) })
            t.sub?.let { k.addView(body(it, Pal.SOFT, 15f).apply { setPadding(0, dp(6), 0, 0) }) }
            col.addView(k)
        }
        val last = card().apply { background = rounded(Pal.INK, dpf(18f)) }
        last.addView(tv(Content.TIP_LAST_HEAD, 20f, Pal.BG, Fonts.BOLD))
        last.addView(tv(Content.TIP_LAST_SUB, 15f, Pal.BG).apply { setPadding(0, dp(6), 0, 0) })
        col.addView(last)
    }

    // ---------- tour ----------

    private val tour = listOf(
        "A" to "This is today: your minutes against your daily limit.",
        "U" to "Want to scroll? Tap this instead. It's for the weak moments.",
        "B" to "Your pace. The only way to make this number smaller is to scroll less. Sorry, not sorry.",
        "W" to "Time you won back. Counted per week, so one bad day doesn't reset it.",
        "tab1" to "History: every day, hour and app. Nothing hidden.",
        "tab2" to "Science: your 7-day test, and the study behind every rule.",
        "tab3" to "Rules: stricter right away, easier only after 24 hours.",
    )

    private fun endTour() {
        if (tourStep >= 0) { tourStep = -1; Store.setTourDone(this); renderTour() }
    }

    private fun renderTour() {
        overlayLayer.removeAllViews()
        for (i in 0 until col.childCount) col.getChildAt(i).alpha = 1f
        for ((k, v) in tourTargets) if (k.startsWith("tab")) v.background = null
        if (tourStep < 0 || page != TODAY || stats == null) return
        var step = tourStep
        while (step < tour.size && tourTargets[tour[step].first] == null) step++
        if (step >= tour.size) { endTour(); return }
        tourStep = step
        val (key, text) = tour[step]
        val target = tourTargets[key]!!
        for (i in 0 until col.childCount) { val v = col.getChildAt(i); v.alpha = if (v === target) 1f else 0.2f }
        if (key.startsWith("tab")) target.background = rounded(Color.TRANSPARENT, dpf(14f), Pal.INK, dp(2))
        else scroll.post { scroll.smoothScrollTo(0, (target.top - dp(40)).coerceAtLeast(0)) }

        val tip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = rounded(Pal.INK, dpf(18f))
            elevation = dpf(8f)
        }
        tip.addView(tv("${step + 1} / ${tour.size}", 12f, Color.rgb(102, 102, 102), Fonts.BOLD))
        tip.addView(tv(text, 17f, Pal.BG, Fonts.MED).apply { setPadding(0, dp(6), 0, 0) })
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        btns.addView(tv("Skip", 15f, Color.rgb(102, 102, 102)).apply { setPadding(0, dp(10), dp(10), dp(10)); isClickable = true; setOnClickListener { endTour() } },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        btns.addView(tv(if (step == tour.size - 1) "Got it" else "Next", 15f, Pal.INK, Fonts.MED).apply {
            setPadding(dp(22), dp(11), dp(22), dp(11))
            background = rounded(Pal.BG, dpf(22f))
            isClickable = true
            setOnClickListener { tourStep++; if (tourStep >= tour.size) endTour() else render() }
        })
        tip.addView(btns)
        overlayLayer.addView(tip, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(92)) })
    }

    // ---------- update ----------

    private fun checkUpdate() {
        Thread {
            val latest = try {
                val c = java.net.URL("https://api.github.com/repos/ropikuronen/kitka/releases/latest").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(body).optString("name").removePrefix("Build ").trim().toIntOrNull()
            } catch (e: Exception) { null }
            if (latest != null && latest > currentBuild()) runOnUiThread {
                if (!isFinishing) { latestBuild = latest; if (page == TODAY) render() }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun currentBuild(): Int = packageManager.getPackageInfo(packageName, 0).versionCode

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

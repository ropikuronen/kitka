package fi.ropi.kitka

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

class OnboardingActivity : Activity() {

    private val last = 13
    private var step = 0
    private var guess = -1
    private var tried = -1
    private val whens = LinkedHashSet<String>()
    private val wants = LinkedHashSet<String>()
    private var showAllApps = false
    private var query = ""
    private var allApps: List<Pair<String, String>>? = null

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var dots: LinearLayout
    private lateinit var backBtn: TextView
    private lateinit var next: TextView

    private val guessOpts = listOf("Under 30 min", "30 to 60 min", "1 to 2 hours", "2 to 4 hours", "More than 4 hours")
    private val whenOpts = listOf("In bed in the morning", "While studying or working", "Between things", "In the evening", "Late at night")
    private val forOpts = listOf("Music", "Studying", "Training", "Sleep", "People", "Work", "Reading")
    private val triedOpts = listOf("Yes, they didn't stick", "Yes, I just turned them off", "No, this is my first")
    private val triedReplies = listOf(
        "They let you skip, snooze and switch off without anyone knowing. FinalApp doesn't. And in 7 days it shows you in numbers whether it's working.",
        "Same here. That's the weak spot of every blocker, so FinalApp closes it. Switching off shows up in your Daily Proof, and any easier rule waits 24 hours.",
        "Then you start with the strongest setup there is, not the weakest.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paintWindow()
        step = savedInstanceState?.getInt("step") ?: 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        backBtn = tv("‹", 30f, Pal.INK).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { go(step - 1) }
        }
        top.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(dots, LinearLayout.LayoutParams(0, dp(3), 1f).apply { leftMargin = dp(8) })
        root.addView(top)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
        }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(content, FrameLayout.LayoutParams(MATCH, WRAP)) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        next = button("Start", primary = true) { go(step + 1) }
        root.addView(next)
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
    }

    override fun onResume() {
        super.onResume()
        render(animate = false)
    }

    private fun go(s: Int) {
        if (s > last) { finishOnboarding(); return }
        step = s.coerceIn(0, last)
        render(animate = true)
    }

    private fun finishOnboarding() {
        Store.setOnboarded(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun render(animate: Boolean) {
        dots.removeAllViews()
        for (i in 0..last) {
            dots.addView(View(this).apply { background = rounded(if (i <= step) Pal.INK else Pal.LINE, dpf(2f)) },
                LinearLayout.LayoutParams(0, dp(3), 1f).apply { if (i > 0) leftMargin = dp(3) })
        }
        backBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.text = when (step) {
            0 -> "Start"; 1 -> "Show me"; 11 -> "I'm in"; last -> "Open FinalApp"; else -> "Continue"
        }
        content.removeAllViews()
        when (step) {
            0 -> welcome(); 1 -> guessStep(); 2 -> reveal(); 3 -> pace()
            4 -> whenStep(); 5 -> forStep(); 6 -> triedStep(); 7 -> notNormal(); 8 -> appsStep()
            9 -> limitStep(); 10 -> proofStep(); 11 -> promise(); 12 -> permsStep(); 13 -> allSet()
        }
        scroll.scrollTo(0, 0)
        if (animate) {
            for (i in 0 until content.childCount) {
                val v = content.getChildAt(i)
                v.alpha = 0f; v.translationY = dpf(14f)
                v.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(380).start()
            }
        }
    }

    private fun title(t: String, size: Float = 32f) = content.addView(big(t, size))
    private fun sub(t: String) = content.addView(body(t, Pal.MUTED, 16f).apply { setPadding(0, dp(8), 0, 0) })

    // ---------- steps ----------

    private fun welcome() {
        content.addView(caps("FinalApp").apply { letterSpacing = 0.3f })
        content.addView(big("Stop scrolling.", 56f).apply { setPadding(0, dp(16), 0, 0) })
        content.addView(tv("Built only on what the research actually shows works.", 22f, Pal.SOFT).apply { setPadding(0, dp(16), 0, 0) })
        content.addView(tv("100% free. Forever.", 17f, Pal.INK, Fonts.MED).apply { setPadding(0, dp(32), 0, 0) })
    }

    private fun guessStep() {
        title("How long did you scroll yesterday?")
        sub("Guess. Don't check.")
        content.addView(space(16))
        for ((i, o) in guessOpts.withIndex()) content.addView(option(o, guess == i) { guess = i; render(false) })
    }

    private fun yesterdayMin(): Long {
        val d = LocalDate.now().minusDays(1)
        val (s, e) = Usage.dayBounds(d)
        return Usage.foregroundMs(this, s, e, Targets.active(this)).values.sum() / 60_000
    }

    private fun reveal() {
        val g = if (guess >= 0) guessOpts[guess].lowercase(EN) else "nothing"
        if (!Perms.usage(this)) {
            title("Let's check the real number.")
            sub("Android already records how long each app is open. Allow FinalApp to read it.")
            content.addView(button("Allow usage access", primary = false) { Perms.openUsage(this) })
            content.addView(small("Find FinalApp in the list, switch it on, then come back."))
            return
        }
        content.addView(tv("You guessed $g. It was:", 16f, Pal.MUTED))
        val num = big("0 min", 64f).apply { setPadding(0, dp(6), 0, 0) }
        content.addView(num)
        val real = yesterdayMin()
        ValueAnimator.ofInt(0, real.toInt()).apply {
            duration = 1100
            addUpdateListener { num.text = fmtMin((it.animatedValue as Int).toLong()) }
            start()
        }
        content.addView(tv("Almost everyone guesses wrong.", 22f, Pal.INK, Fonts.BOLD).apply { setPadding(0, dp(18), 0, 0) })
        content.addView(body("Researchers compared people's guesses with their real phone data in 47 studies, over 50,000 people. The guesses were accurate in only about 1 in 20 studies.", Pal.SOFT, 16f).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(body("That's why FinalApp never asks how you feel you're doing. It measures.", Pal.SOFT, 16f).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(underlineLink("Parry et al. 2021, Nature Human Behaviour") { Perms.openUrl(this, "https://doi.org/10.1038/s41562-021-01117-5") })
    }

    private fun ageStep() {
        title("How old are you?")
        sub("For one number on the next screen. Stays on your phone.")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Pal.CARD, dpf(18f), Pal.BORDER, dp(1))
        }
        val num = big("${Store.age(this)}", 56f).apply { gravity = Gravity.CENTER }
        fun stepBtn(d: Int) = tv(if (d < 0) "−" else "+", 26f).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, dpf(14f), Color.rgb(51, 51, 51), dp(1))
            isClickable = true
            setOnClickListener {
                val a = (Store.age(this@OnboardingActivity) + d).coerceIn(10, 79)
                Store.setAge(this@OnboardingActivity, a); num.text = "$a"
            }
        }
        box.addView(stepBtn(-1), LinearLayout.LayoutParams(dp(56), dp(56)))
        box.addView(num, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(stepBtn(1), LinearLayout.LayoutParams(dp(56), dp(56)))
        content.addView(box, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(28) })
    }

    private fun pace() {
        val s = Stats.compute(this)
        content.addView(Hourglass(this), LinearLayout.LayoutParams(dp(74), dp(112)))
        if (s.avg7 < 1f) {
            content.addView(body("Not enough data yet. FinalApp will show your pace after a few days.", Pal.SOFT, 18f).apply { setPadding(0, dp(24), 0, 0) })
            return
        }
        content.addView(tv("If nothing changes, over 80 years you'll spend", 17f, Pal.MUTED).apply { setPadding(0, dp(26), 0, 0) })
        content.addView(big(String.format(EN, "%.1f years", s.yearsBy80), 64f).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(tv("of your waking life scrolling.", 20f, Pal.SOFT).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(small(String.format(EN, "That's %.0f days every year at your pace of %d min a day. Waking day = 16 h.", s.wakingDaysYear, Math.round(s.avg7))).apply { setPadding(0, dp(18), 0, 0) })
    }

    private fun whenStep() {
        title("When does it get you?")
        sub("Pick all that fit.")
        content.addView(space(16))
        for (o in whenOpts) content.addView(option(o, o in whens) {
            if (!whens.remove(o)) whens.add(o); Store.setWhens(this, whens); render(false)
        })
    }

    private fun forStep() {
        title("What do you want the time for?")
        sub("Your Daily Proof will show it.")
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var rowL: LinearLayout? = null
        for ((i, o) in forOpts.withIndex()) {
            if (i % 3 == 0) { rowL = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; wrap.addView(rowL) }
            val on = o in wants
            val chip = tv(o, 15f, if (on) Pal.BG else Pal.INK).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = if (on) rounded(Pal.INK, dpf(22f)) else rounded(Color.TRANSPARENT, dpf(22f), Color.rgb(51, 51, 51), dp(1))
                isClickable = true
                setOnClickListener { if (!wants.remove(o)) wants.add(o); Store.setWantsFor(this@OnboardingActivity, wants); render(false) }
            }
            rowL!!.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8); topMargin = dp(8) })
        }
        content.addView(wrap, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
    }

    private fun triedStep() {
        title("Tried other apps?")
        content.addView(space(16))
        for ((i, o) in triedOpts.withIndex()) content.addView(option(o, tried == i) { tried = i; render(false) })
        if (tried >= 0) content.addView(tv(triedReplies[tried], 17f, Pal.INK).apply {
            setPadding(0, dp(22), 0, 0); setLineSpacing(0f, 1.35f)
        })
    }

    private fun notNormal() {
        title("This is not a normal anti-scroll app.", 34f)
        val pts = listOf(
            "Every rule comes from a study." to "Nothing is here because it looks nice.",
            "You can't loosen it on a weak moment." to "Stricter is instant. Easier waits 24 h.",
            "You can't quietly switch it off." to "If you do, your Daily Proof says so.",
            "It tests itself on you." to "In 7 days you see in numbers if it works.",
        )
        for ((i, p) in pts.withIndex()) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
            r.addView(tv("${i + 1}", 16f, Pal.MUTED, Fonts.BOLD), LinearLayout.LayoutParams(dp(24), WRAP))
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(tv(p.first, 16f, Pal.INK, Fonts.MED))
            t.addView(tv(p.second, 15f, Pal.SOFT))
            r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            content.addView(r)
        }
    }

    private fun appsStep() {
        title("Which apps?")
        sub("You can always change this later.")
        val list = card(top = 18, pad = 0).apply { setPadding(dp(16), 0, dp(16), 0) }
        val targets = Targets.active(this)
        if (showAllApps) {
            val search = EditText(this).apply {
                hint = "Search apps"
                setHintTextColor(Pal.DIM)
                setTextColor(Pal.INK)
                textSize = 16f
                setSingleLine()
                setText(query)
                setSelection(query.length)
                background = rounded(Pal.BG, dpf(12f), Color.rgb(51, 51, 51), dp(1))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; renderAppRows(list, targets, true) }
                })
            }
            content.addView(search, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        }
        content.addView(list)
        renderAppRows(list, targets, showAllApps)
    }

    private fun renderAppRows(list: LinearLayout, targets: Set<String>, all: Boolean) {
        list.removeAllViews()
        val apps = allApps ?: Perms.installedApps(this).also { allApps = it }
        val q = query.trim().lowercase(EN)
        val shown = if (all) apps.filter { q.isEmpty() || it.second.lowercase(EN).contains(q) }.sortedBy { it.first !in targets }
                    else apps.filter { it.first in targets }
        for ((pkg, name) in shown) {
            val on = pkg in Targets.active(this)
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(15), 0, dp(15))
                isClickable = true
                setOnClickListener {
                    if (on) Targets.removeNow(this@OnboardingActivity, pkg) else Targets.add(this@OnboardingActivity, pkg)
                    renderAppRows(list, Targets.active(this@OnboardingActivity), all)
                }
            }
            r.addView(tv(name, 16f), LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(dot(on))
            list.addView(r)
            list.addView(divider())
        }
        if (!all) {
            list.addView(tv("More…", 16f, Pal.SOFT).apply {
                setPadding(0, dp(15), 0, dp(15))
                isClickable = true
                setOnClickListener { showAllApps = true; render(false) }
            })
        }
    }

    private fun limitStep() {
        title("Daily limit")
        sub("Set it now, while you're calm.")
        val cur = Rules.get(this, Rules.LIMIT)
        val g = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, m) in listOf(30, 60, 90).withIndex()) {
            val on = cur == m
            g.addView(tv("$m min", 20f, if (on) Pal.BG else Pal.INK, Fonts.MED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, dp(22))
                background = if (on) rounded(Pal.INK, dpf(16f)) else rounded(Color.TRANSPARENT, dpf(16f), Color.rgb(51, 51, 51), dp(1))
                isClickable = true
                setOnClickListener { Rules.set(this@OnboardingActivity, Rules.LIMIT, m, force = true); render(false) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(10) })
        }
        content.addView(g, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) })
        content.addView(small("You can lower it any time. Raising it takes 24 hours.").apply { setPadding(0, dp(16), 0, 0) })
    }

    private var proofFriend = false

    private fun proofStep() {
        title("Daily Proof")
        sub("Every evening at 21:00 FinalApp gives you a short report. Tracking works better when someone else sees it.")
        content.addView(space(12))
        val hasFriend = proofFriend || Store.friendPhone(this).isNotEmpty()
        content.addView(option("Only me, as a notification", !hasFriend) { proofFriend = false; Store.setProof(this, "", Store.stake(this), force = true); render(false) })
        content.addView(option("Me and a friend", hasFriend) { proofFriend = true; render(false) })
        if (!hasFriend) return
        val phone = EditText(this).apply {
            hint = "Friend's number, e.g. +358 40 123 4567"; setHintTextColor(Pal.DIM); setTextColor(Pal.INK); textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(Store.friendPhone(this@OnboardingActivity))
            background = rounded(Pal.BG, dpf(12f), Color.rgb(51, 51, 51), dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { Store.setProof(this@OnboardingActivity, s?.toString()?.trim() ?: "", Store.stake(this@OnboardingActivity), force = true) }
            })
        }
        content.addView(phone, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        content.addView(tv("If you go over your limit, you owe them:", 15f, Pal.SOFT).apply { setPadding(0, dp(16), 0, dp(4)) })
        val g = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cur = Store.stake(this)
        for ((i, e) in listOf(0, 10, 20, 50).withIndex()) {
            val on = cur == e
            g.addView(tv(if (e == 0) "Nothing" else "$e €", 16f, if (on) Pal.BG else Pal.INK, Fonts.MED).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14))
                background = if (on) rounded(Pal.INK, dpf(14f)) else rounded(Color.TRANSPARENT, dpf(14f), Color.rgb(51, 51, 51), dp(1))
                isClickable = true
                setOnClickListener { Store.setProof(this@OnboardingActivity, phone.text.toString().trim(), e, force = true); render(false) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        content.addView(g)
        content.addView(small("At 21:00 you get a notification. One tap opens the message to your friend, ready to send."))
        content.addView(small("Example: \"Today I scrolled 23 min. Closed 6 of 8 pauses. Under my 90 min limit. I don't owe you 20 €.\""))
    }

    private fun promise() {
        content.addView(caps("Our promise"))
        content.addView(big("Do exactly what FinalApp says for 7 days.", 34f).apply { setPadding(0, dp(14), 0, 0) })
        content.addView(tv("If your scrolling doesn't drop, delete it and call the science useless.", 19f, Pal.SOFT).apply { setPadding(0, dp(16), 0, 0); setLineSpacing(0f, 1.35f) })
        content.addView(body("You won't have to take our word for it. The Science tab shows your before and after, in minutes.", Pal.MUTED, 16f).apply { setPadding(0, dp(16), 0, 0) })
    }

    private fun permsStep() {
        title("We need two permissions to make this work.")
        sub("Your data stays on your phone. If you join a league, only your league sees your minutes. We never sell or share it.")
        val c = card(top = 20, pad = 0).apply { setPadding(dp(16), dp(4), dp(16), dp(4)) }
        fun permRow(name: String, s: String, ok: Boolean, action: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                isClickable = true
                setOnClickListener { action() }
            }
            r.addView(dot(ok))
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
            t.addView(tv(name, 16f))
            t.addView(tv(s, 13f, Pal.MUTED))
            r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (!ok) r.addView(tv("Allow", 14f, Pal.INK, Fonts.MED))
            c.addView(r)
            c.addView(divider())
        }
        permRow("Accessibility", "Shows the pause and the timer. Installed apps > FinalApp > On.", Perms.accessibility(this)) { Perms.openAccessibility(this) }
        permRow("Notifications", "For your Daily Proof at 21:00.", Perms.notifications(this)) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }
        content.addView(c)
        content.addView(small("If Android says \"Restricted setting\": open App info, tap ⋮ top right, Allow restricted settings, then try again."))
        content.addView(underlineLink("Open App info") { Perms.openAppInfo(this) })
        val g = card(top = 8)
        g.addView(tv("Optional: grayscale", 15f, Pal.INK, Fonts.MED))
        g.addView(body(Grayscale.HOW, Pal.MUTED, 14f).apply { setPadding(0, dp(6), 0, 0) })
        g.addView(tv(if (Grayscale.phoneIsGray(this)) "Grayscale is on right now." else "Grayscale is off right now.", 13f, Pal.SOFT).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(g)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render(false)
    }

    private fun allSet() {
        val check = FrameLayout(this).apply { background = rounded(Pal.INK, dpf(32f)) }
        check.addView(tv("✓", 30f, Pal.BG, Fonts.BOLD).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(check, LinearLayout.LayoutParams(dp(64), dp(64)))
        check.scaleX = 0.6f; check.scaleY = 0.6f
        check.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
        content.addView(big("You're all set.", 40f).apply { setPadding(0, dp(24), 0, 0) })
        content.addView(tv("Day 1 of 7 starts now.", 17f, Pal.SOFT).apply { setPadding(0, dp(8), 0, 0) })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf("${Targets.active(this).size} apps", "${Rules.get(this, Rules.LIMIT)} min a day", if (Store.friendPhone(this).isEmpty()) "Proof at 21:00" else "Proof to a friend at 21:00")) {
            chips.addView(tv(t, 14f, Pal.SOFT).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(Color.TRANSPARENT, dpf(20f), Color.rgb(51, 51, 51), dp(1))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) })
        }
        content.addView(chips, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })
    }
}

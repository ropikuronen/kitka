package fi.ropi.kitka

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The pause. When a selected app comes to the front:
 *  1. Pause screen: Close works at once, Open anyway unlocks after the pause.
 *  2. Open anyway gives you 2 minutes.
 *  3. The app runs with a timer on top.
 *  4. Time is up: back to the home screen, the app is locked for a while.
 */
class KitkaService : AccessibilityService() {

    companion object {
        const val SCROLL_GAP_MS = 800L

        fun since(ctx: Context): Long =
            ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).getLong("since", 0L)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var overlayPkg: String? = null
    private var overlayIsGate = false
    private var timerView: View? = null
    private var timerText: TextView? = null
    private var timerBar: LinearLayout? = null
    private var currentPkg: String? = null

    private var sessionPkg: String? = null
    private var sessionStart = 0L
    private var sessionEnd = 0L
    private var sessionSwipes = 0
    private var lastScrollAt = 0L
    private val ignored = HashSet<String>()
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    private val tick = object : Runnable {
        override fun run() {
            val pkg = sessionPkg ?: return
            val left = sessionEnd - System.currentTimeMillis()
            if (left <= 0) { timeUp(pkg); return }
            if (currentPkg == pkg) updateTimer(left)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        val p = getSharedPreferences("kitka", Context.MODE_PRIVATE)
        if (!p.contains("since")) p.edit().putLong("since", System.currentTimeMillis()).apply()
        Store.markServiceOn(this)
        Proof.schedule(this)
        ignored.addAll(listOf(packageName, "android", "com.android.systemui",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller"))
        try {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { ignored.add(it.packageName) }
        } catch (e: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindow(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(pkg, e)
        }
    }

    private fun onWindow(pkg: String) {
        if (pkg in ignored) return

        if (overlay != null && pkg != overlayPkg) {
            if (overlayIsGate) overlayPkg?.let { Db.get(this).log(it, Ev.CLOSE) }
            hideOverlay()
        }
        currentPkg = pkg

        val now = System.currentTimeMillis()
        val inSession = sessionPkg == pkg && sessionEnd > now
        if (!inSession) hideTimer()

        if (overlay != null || pkg !in Targets.active(this)) return
        when {
            Store.lockedUntil(this, pkg) > now -> showLocked(pkg)
            inSession -> showTimer()
            else -> showGate(pkg)
        }
    }

    private var lastIndex = -1
    private var lastIndexClass = ""
    private var accY = 0

    /**
     * Counts one video only when the feed really moves to the next item:
     * the list's item index changes, or the feed moves vertically by at least
     * about a third of the screen. Taps, likes, comments and sideways movement
     * also fire scroll events, but move neither, so they are not counted.
     */
    private fun onScroll(pkg: String, e: AccessibilityEvent) {
        if (overlay != null || pkg != sessionPkg) return
        val cls = e.className?.toString() ?: ""
        var moved = false
        val idx = e.fromIndex
        if (idx >= 0) {
            if (cls == lastIndexClass && lastIndex >= 0 && idx != lastIndex) moved = true
            lastIndex = idx; lastIndexClass = cls
        }
        val dy = e.scrollDeltaY
        if (dy != -1 && dy != 0) {
            accY += Math.abs(dy)
            if (accY >= resources.displayMetrics.heightPixels / 3) moved = true
        }
        if (!moved) return
        accY = 0
        val now = System.currentTimeMillis()
        if (now - lastScrollAt < SCROLL_GAP_MS) return
        lastScrollAt = now
        Db.get(this).log(pkg, Ev.SCROLL); sessionSwipes++
        updateTimer(sessionEnd - System.currentTimeMillis())
    }

    // ---------- screens ----------

    private fun frame(): Pair<FrameLayout, LinearLayout> {
        val root = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(56), dp(28), dp(40))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(col, FrameLayout.LayoutParams(MATCH, WRAP)) }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        return root to col
    }

    private fun pauseSeconds(): Int {
        val base = Rules.get(this, Rules.PAUSE)
        val night = Rules.get(this, Rules.NIGHT)
        val h = LocalTime.now().hour
        return if (night > 0 && (h >= 23 || h < 7)) maxOf(base, night) else base
    }

    private fun showGate(pkg: String) {
        val db = Db.get(this)
        db.log(pkg, Ev.ATTEMPT)
        val name = appLabel(pkg)
        val opens = db.countToday(pkg, Ev.ATTEMPT)
        val (s, e) = Usage.dayBounds(LocalDate.now())
        val minutes = if (Usage.hasPermission(this)) (Usage.foregroundMs(this, s, e, setOf(pkg))[pkg] ?: 0L) / 60_000 else -1L

        val (root, col) = frame()
        col.addView(tv(name, 40f, Pal.INK, Fonts.BOLD))
        col.addView(tv("${ordinal(opens)} open today.", 18f, Pal.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        if (minutes >= 0) col.addView(tv("${fmtMin(minutes)} so far today.", 18f, Pal.MUTED))

        val note = card(top = 28).apply { background = rounded(Color.rgb(17, 17, 17), dpf(16f)) }
        note.addView(caps("From the creator"))
        note.addView(tv(Content.PAUSE_HEAD, 23f, Pal.INK, Fonts.BOLD).apply { setPadding(0, dp(8), 0, 0) })
        val more = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        for (line in Content.PAUSE_MORE) more.addView(body(line, Pal.SOFT, 16f).apply { setPadding(0, dp(10), 0, 0) })
        note.addView(more)
        val toggle = underlineLink("Read more") {}
        toggle.setOnClickListener {
            val show = more.visibility != View.VISIBLE
            more.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "Show less" else "Read more"
        }
        note.addView(toggle)
        col.addView(note)
        col.addView(space(24))

        col.addView(button("Close", primary = true) {
            db.log(pkg, Ev.CLOSE)
            hideOverlay()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        })
        val secs = pauseSeconds()
        val cont = button("Open anyway in $secs", primary = false) {}
        cont.isEnabled = false
        cont.alpha = 0.4f
        cont.setOnClickListener { if (cont.isEnabled) startSession(pkg, 2) }
        col.addView(cont)

        if (!addOverlay(root, pkg, gate = true)) return
        var left = secs
        val r = object : Runnable {
            override fun run() {
                if (overlay !== root) return
                left--
                if (left <= 0) { cont.isEnabled = true; cont.alpha = 1f; cont.text = "Open anyway for 2 min" }
                else { cont.text = "Open anyway in $left"; handler.postDelayed(this, 1000) }
            }
        }
        handler.postDelayed(r, 1000)
    }

    private fun showChoose(pkg: String, name: String) {
        hideOverlay()
        val (root, col) = frame()
        col.addView(tv("How long?", 40f, Pal.INK, Fonts.BOLD))
        col.addView(body("$name closes when the time is up, then stays locked for ${Rules.get(this, Rules.LOCK)} minutes.", Pal.MUTED, 18f).apply { setPadding(0, dp(10), 0, dp(28)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, m) in listOf(2, 5, 10).withIndex()) {
            val b = tv("$m min", 20f, Pal.INK, Fonts.MED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, dp(22))
                background = rounded(Color.TRANSPARENT, dpf(16f), Pal.DIM, dp(1))
                isClickable = true
                setOnClickListener { startSession(pkg, m) }
            }
            grid.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(10) })
        }
        col.addView(grid)
        col.addView(button("Close instead", primary = true) {
            Db.get(this).log(pkg, Ev.CLOSE)
            hideOverlay()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(24) })
        addOverlay(root, pkg, gate = true)
    }

    private fun startSession(pkg: String, minutes: Int) {
        Db.get(this).log(pkg, Ev.CONTINUE)
        hideOverlay()
        sessionPkg = pkg; lastIndex = -1; lastIndexClass = ""; accY = 0
        sessionStart = System.currentTimeMillis()
        sessionEnd = sessionStart + minutes * 60_000L
        sessionSwipes = 0
        currentPkg = pkg
        showTimer()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun timeUp(pkg: String) {
        handler.removeCallbacks(tick)
        val lockMin = Rules.get(this, Rules.LOCK)
        val until = System.currentTimeMillis() + lockMin * 60_000L
        Store.lock(this, pkg, until)
        val usedMin = ((sessionEnd - sessionStart) / 60_000).toInt()
        val swipes = sessionSwipes
        sessionPkg = null
        hideTimer()
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        val name = appLabel(pkg)
        val (s, e) = Usage.dayBounds(LocalDate.now())
        val todayMin = if (Usage.hasPermission(this)) (Usage.foregroundMs(this, s, e, setOf(pkg))[pkg] ?: 0L) / 60_000 else -1L
        val (root, col) = frame()
        col.addView(tv("Time is up.", 40f, Pal.INK, Fonts.BOLD))
        col.addView(tv("Your $usedMin minutes in $name are over.", 18f, Pal.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        col.addView(tv("It is locked until ${fmtTime(until)}.", 18f, Pal.MUTED))
        val c = card(top = 32)
        c.addView(row(metric("$swipes", "Videos this session"), metric(if (todayMin >= 0) fmtMin(todayMin) else "-", "Today in $name")))
        col.addView(c)
        col.addView(button("OK", primary = true) { hideOverlay() }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(32) })
        addOverlay(root, null, gate = false)
    }

    private fun showLocked(pkg: String) {
        val name = appLabel(pkg)
        val until = Store.lockedUntil(this, pkg)
        val (root, col) = frame()
        col.addView(tv("$name is locked.", 36f, Pal.INK, Fonts.BOLD))
        col.addView(tv("Until ${fmtTime(until)}. Your time was up.", 18f, Pal.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        col.addView(button("Close", primary = true) {
            hideOverlay()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(36) })
        col.addView(button("Ride out the urge", primary = false) {
            hideOverlay()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "urge"))
        })
        addOverlay(root, pkg, gate = false)
    }

    // ---------- timer pill ----------

    private fun showTimer() {
        if (timerView != null) return
        val manager = wm ?: return
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = rounded(Color.argb(200, 0, 0, 0), dpf(10f))
        }
        val t = tv("", 11f, Pal.INK, Fonts.MED).apply { gravity = Gravity.CENTER }
        pill.addView(t)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Sits in the status bar, over the phone's clock, so it never covers the app itself.
            gravity = Gravity.TOP or Gravity.START; x = dp(10)
            y = ((statusBarPx() - dp(18)) / 2).coerceAtLeast(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try { manager.addView(pill, lp) } catch (e: Exception) { return }
        timerView = pill; timerText = t; timerBar = null
        updateTimer(sessionEnd - System.currentTimeMillis())
    }

    private fun updateTimer(leftMs: Long) {
        val t = timerText ?: return
        val secs = (leftMs / 1000).coerceAtLeast(0)
        t.text = "${secs / 60}:${String.format(EN, "%02d", secs % 60)} · $sessionSwipes videos"
        val total = (sessionEnd - sessionStart).coerceAtLeast(1)
        val f = (leftMs.toFloat() / total).coerceIn(0f, 1f)
        val b = timerBar ?: return
        (b.getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight = f.coerceAtLeast(0.001f)
        (b.getChildAt(1).layoutParams as LinearLayout.LayoutParams).weight = (1f - f).coerceAtLeast(0.001f)
        b.requestLayout()
    }

    private fun statusBarPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private var focus: android.media.AudioFocusRequest? = null

    /** Pauses the video playing behind the pause screen. */
    private fun muteBehind() {
        try {
            val am = getSystemService(android.media.AudioManager::class.java) ?: return
            val r = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
                .setOnAudioFocusChangeListener { }.build()
            am.requestAudioFocus(r); focus = r
        } catch (e: Exception) { }
    }

    private fun unmute() {
        try { focus?.let { getSystemService(android.media.AudioManager::class.java)?.abandonAudioFocusRequest(it) } } catch (e: Exception) { }
        focus = null
    }

    private fun hideTimer() {
        timerView?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        timerView = null; timerText = null; timerBar = null
    }

    // ---------- helpers ----------

    private fun addOverlay(root: View, pkg: String?, gate: Boolean): Boolean {
        val manager = wm ?: return false
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            // Cover the whole screen, also behind the status bar and the camera hole.
            layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        root.setPadding(root.paddingLeft, root.paddingTop + statusBarPx(), root.paddingRight, root.paddingBottom)
        return try {
            manager.addView(root, lp)
            overlay = root; overlayPkg = pkg; overlayIsGate = gate
            muteBehind()
            true
        } catch (e: Exception) { false }
    }

    private fun hideOverlay() {
        overlay?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        overlay = null; overlayPkg = null; overlayIsGate = false
        unmute()
    }

    private fun fmtTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hhmm)

    private fun appLabel(pkg: String): String = Stats.labelOf(this, pkg) ?: pkg

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Store.markServiceOff(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Store.markServiceOff(this)
        hideOverlay(); hideTimer()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

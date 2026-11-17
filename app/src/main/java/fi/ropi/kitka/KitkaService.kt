package fi.ropi.kitka

import android.accessibilityservice.AccessibilityService
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
import android.widget.TextView
import java.time.LocalDate

/**
 * Portti. Kun kohdesovellus tulee etualalle, päälle tulee koko näytön kerros:
 *  - Sulje toimii heti (PNAS 2023: sulkemisvaihtoehto oli tehokkain osa).
 *  - Jatka aukeaa vasta viiveen jälkeen (viive vaikutti myös).
 *  - Ei pohdintaviestejä (niillä ei ollut vaikutusta).
 * Jatkamisen jälkeen portti tulee uudestaan, jos olet ollut sovelluksessa
 * RECHECK_MS putkeen.
 */
class KitkaService : AccessibilityService() {

    companion object {
        const val GATE_DELAY_S = 10
        const val RECHECK_MS = 10L * 60 * 1000
        const val SCROLL_GAP_MS = 800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var overlayPkg: String? = null
    private var currentPkg: String? = null
    private var allowedPkg: String? = null
    private var lastScrollAt = 0L
    private val ignored = HashSet<String>()
    private val recheck = Runnable { onRecheck() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        ignored.addAll(
            listOf(
                packageName,
                "android",
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
            )
        )
        try {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.forEach { ignored.add(it.packageName) }
        } catch (e: Exception) {
            // Näppäimistölistan haku ei ole kriittinen.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindow(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(pkg)
        }
    }

    private fun onWindow(pkg: String) {
        if (pkg in ignored) return

        // Poistuit portilta kotinapilla tai eleellä -> lasketaan sulkemiseksi.
        if (overlay != null && pkg != overlayPkg) {
            overlayPkg?.let { Db.get(this).log(it, Ev.CLOSE) }
            hideOverlay()
        }

        if (pkg != currentPkg) {
            currentPkg = pkg
            if (pkg != allowedPkg) {
                allowedPkg = null
                handler.removeCallbacks(recheck)
            }
        }

        if (overlay == null && pkg != allowedPkg && pkg in Targets.active(this)) {
            showGate(pkg, isRecheck = false)
        }
    }

    /** Arvio pyyhkäisyistä: uusi pyyhkäisy, kun edellisestä scroll-tapahtumasta on tauko. */
    private fun onScroll(pkg: String) {
        if (overlay != null || pkg != allowedPkg) return
        val now = System.currentTimeMillis()
        val gap = now - lastScrollAt
        lastScrollAt = now
        if (gap > SCROLL_GAP_MS) Db.get(this).log(pkg, Ev.SCROLL)
    }

    private fun onRecheck() {
        val pkg = allowedPkg ?: return
        if (currentPkg != pkg || overlay != null) return
        allowedPkg = null
        showGate(pkg, isRecheck = true)
    }

    private fun showGate(pkg: String, isRecheck: Boolean) {
        val manager = wm ?: return
        val db = Db.get(this)
        db.log(pkg, if (isRecheck) Ev.RECHECK else Ev.ATTEMPT)

        val attempts = db.countToday(pkg, Ev.ATTEMPT)
        val minutes: Long = if (Usage.hasPermission(this)) {
            val (s, e) = Usage.dayBounds(LocalDate.now())
            (Usage.foregroundMs(this, s, e, setOf(pkg))[pkg] ?: 0L) / 60_000
        } else -1L

        val root = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        col.addView(tv(appLabel(pkg), 34f, Pal.INK, Fonts.HEAD))
        val line1 = if (isRecheck) "${RECHECK_MS / 60_000} min putkeen." else "$attempts. avausyritys tänään."
        col.addView(tv(line1, 17f, Pal.MUTED, Fonts.NUM).apply { setPadding(0, dp(10), 0, 0) })
        if (minutes >= 0) {
            col.addView(tv("$minutes min tänään.", 17f, Pal.MUTED, Fonts.NUM).apply { setPadding(0, dp(4), 0, 0) })
        }

        col.addView(View(this), LinearLayout.LayoutParams(MATCH, dp(40)))

        col.addView(action("Sulje", primary = true) {
            db.log(pkg, Ev.CLOSE)
            hideOverlay()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        })

        val cont = action("Jatka ($GATE_DELAY_S)", primary = false) {}
        cont.isEnabled = false
        cont.alpha = 0.35f
        cont.setOnClickListener {
            if (!cont.isEnabled) return@setOnClickListener
            db.log(pkg, Ev.CONTINUE)
            allowedPkg = pkg
            currentPkg = pkg
            hideOverlay()
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, RECHECK_MS)
        }
        col.addView(cont)

        root.addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        try {
            manager.addView(root, lp)
        } catch (e: Exception) {
            return
        }
        overlay = root
        overlayPkg = pkg

        var left = GATE_DELAY_S
        val tick = object : Runnable {
            override fun run() {
                if (overlay !== root) return
                left--
                if (left <= 0) {
                    cont.isEnabled = true
                    cont.alpha = 1f
                    cont.text = "Jatka"
                } else {
                    cont.text = "Jatka ($left)"
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(tick, 1000)
    }

    private fun hideOverlay() {
        val v = overlay
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (e: Exception) {
                // jo poistettu
            }
        }
        overlay = null
        overlayPkg = null
    }

    private fun appLabel(pkg: String): String = try {
        @Suppress("DEPRECATION")
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

package fi.ropi.kitka

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Switches Android's own grayscale (colour correction, monochrome) on and off.
 * Needs WRITE_SECURE_SETTINGS, which only adb can grant:
 *   adb shell pm grant fi.ropi.kitka android.permission.WRITE_SECURE_SETTINGS
 */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    const val ADB = "adb shell pm grant fi.ropi.kitka android.permission.WRITE_SECURE_SETTINGS"

    /** True if the whole phone is already in grayscale (the user turned it on in Settings). */
    fun phoneIsGray(ctx: Context): Boolean = try {
        val cr = ctx.contentResolver
        val aosp = Settings.Secure.getInt(cr, ENABLED, 0) == 1 && Settings.Secure.getInt(cr, MODE, -1) == 0
        val samsung = listOf("greyscale_mode", "grayscale_mode", "accessibility_grayscale_enabled", "color_blind")
            .any { k -> runCatching { Settings.Secure.getInt(cr, k, 0) == 1 }.getOrDefault(false) }
        (aosp && !active) || samsung
    } catch (e: Exception) { false }

    /** Grayscale is taken care of one way or the other. */
    fun ready(ctx: Context): Boolean = phoneIsGray(ctx) || canControl(ctx)

    const val PHONE_PATH = "Settings > Accessibility > Vision enhancements > Colour adjustment > Greyscale"

    fun canControl(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private var savedEnabled: Int? = null
    private var savedMode: Int? = null
    var active = false
        private set

    fun on(ctx: Context) {
        if (active || !canControl(ctx) || phoneIsGray(ctx)) return
        val cr = ctx.contentResolver
        try {
            savedEnabled = Settings.Secure.getInt(cr, ENABLED, 0)
            savedMode = Settings.Secure.getInt(cr, MODE, -1)
            Settings.Secure.putInt(cr, MODE, 0)
            Settings.Secure.putInt(cr, ENABLED, 1)
            active = true
        } catch (e: Exception) { /* not allowed on this phone */ }
    }

    fun off(ctx: Context) {
        if (!active) return
        val cr = ctx.contentResolver
        try {
            val m = savedMode ?: -1
            if (m >= 0) Settings.Secure.putInt(cr, MODE, m)
            Settings.Secure.putInt(cr, ENABLED, savedEnabled ?: 0)
        } catch (e: Exception) { }
        active = false
    }
}

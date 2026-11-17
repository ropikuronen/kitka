package fi.ropi.kitka

import android.content.Context
import android.provider.Settings

/**
 * FinalApp does not switch grayscale itself. You set it with the phone's own settings
 * (on Samsung: Modes and Routines, or Accessibility). FinalApp only reads whether it is on.
 */
object Grayscale {
    fun phoneIsGray(ctx: Context): Boolean = try {
        val cr = ctx.contentResolver
        val aosp = Settings.Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) == 1 &&
            Settings.Secure.getInt(cr, "accessibility_display_daltonizer", -1) == 0
        val samsung = listOf("greyscale_mode", "grayscale_mode", "accessibility_grayscale_enabled", "color_blind")
            .any { k -> runCatching { Settings.Secure.getInt(cr, k, 0) == 1 }.getOrDefault(false) }
        aosp || samsung
    } catch (e: Exception) { false }

    const val HOW = "Use your phone's own grayscale. On Samsung: Modes and Routines, for example a routine that turns on grayscale when Instagram opens."
}

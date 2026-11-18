package fi.ropi.kitka

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object Perms {
    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** True when the pause can run: it needs both permissions. */
    fun accessibility(ctx: Context): Boolean = overlay(ctx) && Usage.hasPermission(ctx)

    fun usage(ctx: Context) = Usage.hasPermission(ctx)

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED


    fun openOverlay(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
        catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
    }

    fun openUsage(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun openAppInfo(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun openUrl(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("FinalApp", text))
    }

    /** Installed launcher apps (package, label), sorted by name. */
    fun installedApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .sortedBy { it.second.lowercase(EN) }
    }
}

package fi.ropi.kitka

import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate

/**
 * Keeps every day forever. Android only keeps its own usage data for a while,
 * so once a day FinalApp copies everything Android still has (minutes per app and
 * per hour) into its own database, going back as far as Android allows.
 */
object History {
    private const val MAX_BACK = 400

    fun oldest(ctx: Context): String = ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).getString("oldest_day", "") ?: ""

    fun sync(ctx: Context) {
        if (!Usage.hasPermission(ctx)) return
        val pr = ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (pr.getString("history_sync_day", "") == today.toString()) return
        val pkgs = Targets.active(ctx)
        if (pkgs.isEmpty()) return
        val db = Db.get(ctx)
        val saved = db.savedDays()
        val totals = HashMap<String, Long>()
        var oldest = pr.getString("oldest_day", "") ?: ""
        var emptyRun = 0
        for (i in 1..MAX_BACK) {
            val d = today.minusDays(i.toLong())
            val key = d.toString()
            // Re-read the last two days in case they were partial; older saved days stay as they are.
            if (key in saved && i > 2) { emptyRun = 0; continue }
            val (s, e) = Usage.dayBounds(d)
            var apps = Usage.foregroundMs(ctx, s, e, pkgs)
            var hours = Usage.byHour(ctx, s, e, pkgs).map { it * 60_000 }.toLongArray()
            if (apps.isEmpty()) {
                // Older days: Android may still have daily totals even when the detailed events are gone.
                apps = daily(ctx, s, e, pkgs)
                hours = LongArray(24)
            }
            if (apps.isEmpty() && i > 7) {
                if (++emptyRun >= 30) break   // a month empty in a row: Android has nothing older
                continue
            }
            emptyRun = 0
            db.saveDay(key, apps, hours)
            totals[key] = apps.values.sum() / 60_000
            if (oldest.isEmpty() || key < oldest) oldest = key
        }
        // A saved total wins over a later partial read only when missing; recent days are refreshed.
        Store.saveDayTotals(ctx, totals)
        pr.edit().putString("history_sync_day", today.toString()).putString("oldest_day", oldest).apply()
    }

    private fun daily(ctx: Context, start: Long, end: Long, pkgs: Set<String>): Map<String, Long> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val out = HashMap<String, Long>()
        try {
            for (u in usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end - 1)) {
                if (u.packageName !in pkgs) continue
                // Only buckets that start inside this day, so days are not counted twice.
                if (u.firstTimeStamp < start - 3 * 3600_000L || u.firstTimeStamp >= end) continue
                if (u.totalTimeInForeground > 0) out[u.packageName] = (out[u.packageName] ?: 0L) + u.totalTimeInForeground
            }
        } catch (e: Exception) { }
        return out
    }

    /** Average minutes per day over the last [n] complete days that exist, and how many days that was. */
    fun average(history: Map<LocalDate, Long>, n: Int): Pair<Float, Int> {
        val today = LocalDate.now()
        val v = (1..n).mapNotNull { history[today.minusDays(it.toLong())] }
        return if (v.isEmpty()) 0f to 0 else v.sum() / v.size.toFloat() to v.size
    }
}

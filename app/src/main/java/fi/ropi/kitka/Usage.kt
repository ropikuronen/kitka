package fi.ropi.kitka

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Screen time per app from Android's own usage events. */
object Usage {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayBounds(day: LocalDate): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        val start = day.atStartOfDay(z).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(), System.currentTimeMillis())
        return start to end
    }

    /** Foreground intervals (pkg, start, end) for the given packages. */
    fun intervals(ctx: Context, start: Long, end: Long, pkgs: Set<String>): List<Triple<String, Long, Long>> {
        val out = ArrayList<Triple<String, Long, Long>>()
        if (end <= start || pkgs.isEmpty() || !hasPermission(ctx)) return out
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return out
        val events = try { usm.queryEvents(start, end) } catch (e: Exception) { null } ?: return out
        val ev = UsageEvents.Event()
        val resumed = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val seen = HashSet<String>()

        fun close(pkg: String, at: Long) {
            val s = since[pkg] ?: return
            if (at > s) out.add(Triple(pkg, s, at))
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val type = ev.eventType
            if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                for ((p, set) in resumed) if (set.isNotEmpty()) { close(p, ev.timeStamp); set.clear() }
                continue
            }
            val pkg = ev.packageName ?: continue
            if (pkgs !== ALL && pkg !in pkgs) continue
            val cls = ev.className ?: ""
            when (type) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) since[pkg] = ev.timeStamp
                    set.add(cls)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.remove(cls)) {
                        if (set.isEmpty()) close(pkg, ev.timeStamp)
                    } else if (pkg !in seen && type == UsageEvents.Event.ACTIVITY_PAUSED) {
                        if (ev.timeStamp > start) out.add(Triple(pkg, start, ev.timeStamp))
                    }
                }
            }
            seen.add(pkg)
        }
        for ((p, set) in resumed) if (set.isNotEmpty()) close(p, end)
        return out
    }

    /** Pass as pkgs to count every app. */
    val ALL: Set<String> = setOf("*")

    /** Whole-phone screen time in the range: every app except the home screen, system UI and FinalApp. */
    fun phoneMs(ctx: Context, start: Long, end: Long): Long {
        val skip = hashSetOf(ctx.packageName, "android", "com.android.systemui")
        try {
            ctx.packageManager.resolveActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName?.let { skip.add(it) }
        } catch (e: Exception) { }
        return foregroundMs(ctx, start, end, ALL).filterKeys { it !in skip }.values.sum()
    }

    fun foregroundMs(ctx: Context, start: Long, end: Long, pkgs: Set<String>): Map<String, Long> {
        val r = HashMap<String, Long>()
        for ((p, a, b) in intervals(ctx, start, end, pkgs)) r[p] = (r[p] ?: 0L) + (b - a)
        return r
    }

    /** Minutes per hour of day (0..23), summed over the range. */
    fun byHour(ctx: Context, start: Long, end: Long, pkgs: Set<String>): LongArray {
        val hours = LongArray(24)
        val z = ZoneId.systemDefault()
        for ((_, a, b) in intervals(ctx, start, end, pkgs)) {
            var t = a
            while (t < b) {
                val zt = Instant.ofEpochMilli(t).atZone(z)
                val nextHour = zt.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val segEnd = minOf(b, nextHour)
                hours[zt.hour] += (segEnd - t)
                t = segEnd
            }
        }
        for (i in 0 until 24) hours[i] = hours[i] / 60_000
        return hours
    }

    /** Rough total over a long range from Android's coarser buckets. */
    fun aggregateMs(ctx: Context, start: Long, end: Long, pkgs: Set<String>): Long {
        if (end <= start || !hasPermission(ctx)) return 0L
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return 0L
        return try {
            usm.queryAndAggregateUsageStats(start, end).filterKeys { it in pkgs }.values.sumOf { it.totalTimeInForeground }
        } catch (e: Exception) { 0L }
    }
}

package fi.ropi.kitka

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Ruutuaika per sovellus Androidin omista käyttötapahtumista. */
object Usage {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayBounds(day: LocalDate): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        val start = day.atStartOfDay(z).toInstant().toEpochMilli()
        val end = minOf(
            day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(),
            System.currentTimeMillis()
        )
        return start to end
    }

    /**
     * Millisekunnit etualalla välillä [start, end) annetuille paketeille.
     * Sovellus lasketaan etualalle, kun jokin sen aktiviteeteista on resumed-tilassa.
     * Näytön sammuminen katkaisee kaikki käynnissä olevat jaksot.
     */
    fun foregroundMs(ctx: Context, start: Long, end: Long, pkgs: Set<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        if (end <= start || pkgs.isEmpty() || !hasPermission(ctx)) return result
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return result
        val events = usm.queryEvents(start, end) ?: return result
        val ev = UsageEvents.Event()
        val resumed = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val seen = HashSet<String>()

        fun add(pkg: String, ms: Long) {
            if (ms > 0) result[pkg] = (result[pkg] ?: 0L) + ms
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val type = ev.eventType

            if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                type == UsageEvents.Event.DEVICE_SHUTDOWN
            ) {
                for ((p, set) in resumed) {
                    if (set.isNotEmpty()) {
                        add(p, ev.timeStamp - (since[p] ?: ev.timeStamp))
                        set.clear()
                    }
                }
                continue
            }

            val pkg = ev.packageName ?: continue
            if (pkg !in pkgs) continue
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
                        if (set.isEmpty()) add(pkg, ev.timeStamp - (since[pkg] ?: ev.timeStamp))
                    } else if (pkg !in seen && type == UsageEvents.Event.ACTIVITY_PAUSED) {
                        // Oli jo auki, kun aikaväli alkoi.
                        add(pkg, ev.timeStamp - start)
                    }
                }
            }
            seen.add(pkg)
        }
        for ((p, set) in resumed) {
            if (set.isNotEmpty()) add(p, end - (since[p] ?: end))
        }
        return result
    }
}

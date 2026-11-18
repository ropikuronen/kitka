package fi.ropi.kitka

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Day(val date: LocalDate, val minutes: Map<String, Long>, val counts: Map<String, Map<String, Int>>) {
    val totalMin: Long get() = minutes.values.sum() / 60_000
    fun count(type: String) = counts.values.sumOf { it[type] ?: 0 }
    val gates: Int get() = count(Ev.ATTEMPT) + count(Ev.RECHECK)
}

class Stats(
    val days: List<Day>,                 // 0 = today, up to 7 back
    val targets: Set<String>,
    val labels: Map<String, String>,
    val since: Long,
    val avg7: Float,
    val hours: LongArray,
    val history: Map<LocalDate, Long>,   // minutes per day, as far back as saved
    val beforeAvg: Float?,
    val beforeDays: Int,
    val afterAvg: Float?,
    val afterDays: Int,
    val weekGates: Int,
    val weekClosed: Int,
    val wonBackMin: Long?,
    val limit: Int,
    val age: Int,
    val pace: Float = 0f,                // average min/day over the last (up to) 28 days
    val paceDays: Int = 0,
) {
    val today: Day get() = days[0]
    private val paceMin: Float get() = if (paceDays > 0) pace else avg7
    val yearHours: Float get() = paceMin * 365f / 60f
    val wakingDaysYear: Float get() = paceMin * 365f / 960f
    val yearsBy80: Float get() = 80 * yearHours / (16f * 365f)

    companion object {
        fun compute(ctx: Context): Stats {
            val targets = Targets.active(ctx)
            try { History.sync(ctx) } catch (e: Exception) { }
            val today = LocalDate.now()
            val z = ZoneId.systemDefault()
            val counts = Db.get(ctx).counts(today.minusDays(7))
            val logged = counts.values.flatMap { it.keys }.toSet()
            val all = targets + logged
            val days = (0..7).map { i ->
                val d = today.minusDays(i.toLong())
                val (s, e) = Usage.dayBounds(d)
                Day(d, Usage.foregroundMs(ctx, s, e, all), counts[d.toString()] ?: emptyMap())
            }
            val usage = Usage.hasPermission(ctx)
            if (usage) Store.saveDayTotals(ctx, days.drop(1).associate { it.date.toString() to it.totalMin })
            val saved = Store.dayTotals(ctx)
            val history = HashMap<LocalDate, Long>()
            for ((k, v) in saved) runCatching { history[LocalDate.parse(k)] = v }
            if (usage) history[today] = days[0].totalMin

            val complete = (1..7).mapNotNull { history[today.minusDays(it.toLong())] }
            val avg7 = if (complete.isNotEmpty()) complete.sum() / complete.size.toFloat() else 0f

            val weekStart = today.minusDays(6).atStartOfDay(z).toInstant().toEpochMilli()
            val hours = Usage.byHour(ctx, weekStart, System.currentTimeMillis(), all)

            val labels = HashMap<String, String>()
            for (p in all) labelOf(ctx, p)?.let { labels[p] = it }

            val since = KitkaService.since(ctx)
            val sinceDay = if (since > 0) Instant.ofEpochMilli(since).atZone(z).toLocalDate() else null

            // Baseline: complete days before the pause existed. Saved once.
            val pr = ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE)
            if (sinceDay != null && usage && !pr.contains("baseline_days")) {
                val before = history.filterKeys { it.isBefore(sinceDay) }.values
                if (before.isNotEmpty()) pr.edit()
                    .putFloat("baseline_avg", before.sum() / before.size.toFloat())
                    .putInt("baseline_days", before.size).apply()
            }
            val bDays = pr.getInt("baseline_days", 0)
            val beforeAvg = if (bDays > 0) pr.getFloat("baseline_avg", 0f) else null
            val after = if (sinceDay != null) history.filterKeys { it.isAfter(sinceDay) && it.isBefore(today) }.values else emptyList()
            val afterAvg = if (after.isNotEmpty()) after.sum() / after.size.toFloat() else null

            val week = days.take(7)
            val weekGates = week.sumOf { it.gates }
            val weekClosed = week.sumOf { it.count(Ev.CLOSE) }
            val wonBack = if (beforeAvg != null && sinceDay != null) {
                val counted = week.filter { !it.date.isBefore(sinceDay) && it.date.isBefore(today) }
                if (counted.isEmpty()) null else counted.sumOf { (beforeAvg - it.totalMin).toLong() }
            } else null

            return Stats(days, targets, labels, since, avg7, hours, history, beforeAvg, bDays, afterAvg, after.size,
                weekGates, weekClosed, wonBack, Rules.get(ctx, Rules.LIMIT), Store.age(ctx),
                History.average(history, 28).first, History.average(history, 28).second)
        }

        fun labelOf(ctx: Context, pkg: String): String? = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { null }
    }
}

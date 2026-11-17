package fi.ropi.kitka

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Daily Proof: a short report every evening at 21:00, sent to you as a notification. */
object Proof {
    const val HOUR = 21
    private const val CHANNEL = "proof"

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val z = ZoneId.systemDefault()
        var next = LocalDate.now().atTime(HOUR, 0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(z).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(ctx, 21, Intent(ctx, ProofReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e: Exception) { }
    }

    /** Title and lines of today's proof. */
    fun build(ctx: Context): Pair<String, String> {
        val s = Stats.compute(ctx)
        val t = s.today
        val gates = t.gates
        val closed = t.count(Ev.CLOSE)
        val title = "Today: ${fmtMin(t.totalMin)}. Closed $closed of $gates pauses."
        val lines = ArrayList<String>()
        lines.add(if (t.totalMin > s.limit) "Over your ${s.limit} min limit." else "Under your ${s.limit} min limit.")
        s.wonBackMin?.let { lines.add(if (it >= 0) "This week: +${fmtMin(it)} won back." else "This week: ${fmtMin(-it)} more than before.") }
        val z = ZoneId.systemDefault()
        val startToday = LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli()
        val f = DateTimeFormatter.ofPattern("HH:mm")
        for ((a, b) in Store.gaps(ctx)) if (b > startToday) {
            val from = Instant.ofEpochMilli(maxOf(a, startToday)).atZone(z).format(f)
            val to = Instant.ofEpochMilli(b).atZone(z).format(f)
            lines.add("Pause was off from $from to $to.")
        }
        return title to lines.joinToString(" ")
    }

    fun notify(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily Proof", NotificationManager.IMPORTANCE_DEFAULT))
        val (title, text) = build(ctx)
        val open = PendingIntent.getActivity(ctx, 22, Intent(ctx, MainActivity::class.java).putExtra("open", "proof"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(21, n) } catch (e: SecurityException) { }
    }
}

class ProofReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Proof.schedule(ctx); return }
        val pending = goAsync()
        Thread {
            try { Proof.notify(ctx) } finally {
                Proof.schedule(ctx)
                pending.finish()
            }
        }.start()
    }
}

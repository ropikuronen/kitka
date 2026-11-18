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

    /** The exact message, first person. Same text for the notification, Share and SMS. */
    fun message(ctx: Context, s: Stats = Stats.compute(ctx)): String {
        val t = s.today
        val gates = t.gates
        val closed = t.count(Ev.CLOSE)
        val over = t.totalMin > s.limit
        val stake = Store.stake(ctx)
        val sb = StringBuilder()
        sb.append("Today I scrolled ${fmtMin(t.totalMin)} and watched ${t.count(Ev.SCROLL)} videos. Closed $closed of $gates pauses.\n")
        sb.append(if (over) "Over my ${s.limit} min limit." else "Under my ${s.limit} min limit.")
        if (stake > 0 && Store.friendPhone(ctx).isNotEmpty()) sb.append(if (over) " I owe you $stake €." else " I don't owe you $stake €.")
        val z = ZoneId.systemDefault()
        val startToday = LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli()
        val f = DateTimeFormatter.ofPattern("HH:mm")
        for ((a, b) in Store.gaps(ctx)) if (b > startToday) {
            sb.append("\nFinalApp was switched off from ${Instant.ofEpochMilli(maxOf(a, startToday)).atZone(z).format(f)} to ${Instant.ofEpochMilli(b).atZone(z).format(f)}.")
        }
        sb.append("\n\nSent to you from FinalApp")
        return sb.toString()
    }

    /** Opens the message: straight to the friend's chat in the SMS app if there is a friend, otherwise the share sheet. No permission needed; you tap Send. */
    fun sendIntent(ctx: Context, text: String = message(ctx)): Intent {
        val phone = Store.friendPhone(ctx)
        return if (phone.isNotEmpty())
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + phone)).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Daily Proof").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(ctx: Context, text: String = message(ctx)): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Daily Proof").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
        val msg = message(ctx)
        val title = "Daily Proof"
        val text = msg.substringBefore("\n\nSent to you").replace("\n", " ")
        val open = PendingIntent.getActivity(ctx, 22, Intent(ctx, MainActivity::class.java).putExtra("open", "proof"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hasFriend = Store.friendPhone(ctx).isNotEmpty()
        val share = PendingIntent.getActivity(ctx, 23, shareIntent(ctx, msg), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toFriend = PendingIntent.getActivity(ctx, 24, sendIntent(ctx, msg), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(if (hasFriend) toFriend else open)
            .setAutoCancel(true)
            .addAction(android.app.Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat), "Share", share).build())
            .apply { if (hasFriend) addAction(android.app.Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat), "Send to friend", toFriend).build()) }
            .build()
        try { nm.notify(21, n) } catch (e: SecurityException) { }
    }
}

class ProofReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Proof.schedule(ctx); return }
        // Other apps can reach an exported receiver; ignore anything that is not our own 21:00 alarm.
        if (intent.action != null && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                Proof.notify(ctx)
                try { League.sync(ctx) } catch (e: Exception) { }
                Backup.daily(ctx)
            } finally {
                Proof.schedule(ctx)
                pending.finish()
            }
        }.start()
    }
}

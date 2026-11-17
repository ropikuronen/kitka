package fi.ropi.kitka

import android.content.Context
import android.content.SharedPreferences

/**
 * Kohdesovellukset.
 *
 * Lisäys toimii heti. Poisto astuu voimaan vasta 24 h kuluttua.
 * Tämä on tarkoituksellista: hetken mielijohteesta tehty löysennys on
 * juuri se kohta, jossa valmiit työkalut pettävät.
 */
object Targets {

    val DEFAULTS = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok (osa alueista)
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.reddit.frontpage",
        "com.twitter.android",           // X
    )

    const val REMOVE_DELAY_MS = 24L * 60 * 60 * 1000

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("kitka", Context.MODE_PRIVATE)

    /** Nykyiset kohteet. Toteuttaa samalla erääntyneet poistot. */
    fun active(ctx: Context): Set<String> {
        val p = prefs(ctx)
        val current = p.getStringSet("targets", null)?.toMutableSet() ?: DEFAULTS.toMutableSet()
        val pending = pendingRemovals(ctx)
        val now = System.currentTimeMillis()
        val due = pending.filterValues { it <= now }.keys
        if (due.isNotEmpty() || !p.contains("targets")) {
            current.removeAll(due)
            p.edit()
                .putStringSet("targets", HashSet(current))
                .putStringSet("pending", encode(pending.filterKeys { it !in due }))
                .apply()
        }
        return current
    }

    /** paketti -> aika (epoch ms), jolloin poisto astuu voimaan. */
    fun pendingRemovals(ctx: Context): Map<String, Long> {
        val raw = prefs(ctx).getStringSet("pending", emptySet()) ?: emptySet()
        return raw.mapNotNull { s ->
            val i = s.lastIndexOf('|')
            val t = if (i > 0) s.substring(i + 1).toLongOrNull() else null
            if (t == null) null else s.substring(0, i) to t
        }.toMap()
    }

    fun add(ctx: Context, pkg: String) {
        val cur = active(ctx).toMutableSet()
        cur.add(pkg)
        prefs(ctx).edit()
            .putStringSet("targets", HashSet(cur))
            .putStringSet("pending", encode(pendingRemovals(ctx) - pkg))
            .apply()
    }

    fun requestRemove(ctx: Context, pkg: String) {
        val pending = pendingRemovals(ctx).toMutableMap()
        if (pkg !in pending) pending[pkg] = System.currentTimeMillis() + REMOVE_DELAY_MS
        prefs(ctx).edit().putStringSet("pending", encode(pending)).apply()
    }

    fun cancelRemove(ctx: Context, pkg: String) {
        prefs(ctx).edit().putStringSet("pending", encode(pendingRemovals(ctx) - pkg)).apply()
    }

    private fun encode(m: Map<String, Long>): Set<String> =
        m.map { "${it.key}|${it.value}" }.toHashSet()
}

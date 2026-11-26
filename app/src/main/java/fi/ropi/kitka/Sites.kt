package fi.ropi.kitka

import android.content.Context

/**
 * Websites blocked in browsers. Same rule as apps: adding works at once,
 * removing takes effect only after 24 hours.
 */
object Sites {
    val PRESET = listOf("youtube.com", "instagram.com", "tiktok.com", "reddit.com", "x.com",
        "facebook.com", "snapchat.com", "linkedin.com", "pinterest.com")

    /** Other names of the same site, blocked together. */
    val ALIASES = mapOf("youtube.com" to listOf("youtu.be"), "x.com" to listOf("twitter.com"), "facebook.com" to listOf("fb.com"))

    private val FROM_APP = mapOf(
        "com.google.android.youtube" to "youtube.com", "com.instagram.android" to "instagram.com",
        "com.zhiliaoapp.musically" to "tiktok.com", "com.ss.android.ugc.trill" to "tiktok.com",
        "com.reddit.frontpage" to "reddit.com", "com.twitter.android" to "x.com",
        "com.facebook.katana" to "facebook.com", "com.snapchat.android" to "snapchat.com",
        "com.linkedin.android" to "linkedin.com", "com.pinterest" to "pinterest.com")

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("kitka", Context.MODE_PRIVATE)

    fun clean(raw: String): String? {
        val h = raw.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').removePrefix("www.").removePrefix("m.")
        return if (Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+").matches(h)) h else null
    }

    /** Current sites. First time: the sites of the apps already on your list. Applies removals that are due. */
    fun active(ctx: Context): Set<String> {
        val pr = p(ctx)
        val cur = pr.getStringSet("sites", null)?.toMutableSet()
            ?: Targets.active(ctx).mapNotNull { FROM_APP[it] }.toMutableSet()
        val pend = pending(ctx)
        val now = System.currentTimeMillis()
        val due = pend.filterValues { it <= now }.keys
        if (due.isNotEmpty() || !pr.contains("sites")) {
            cur.removeAll(due)
            pr.edit().putStringSet("sites", HashSet(cur)).putStringSet("sites_pending", enc(pend - due)).apply()
        }
        return cur
    }

    fun pending(ctx: Context): Map<String, Long> =
        (p(ctx).getStringSet("sites_pending", emptySet()) ?: emptySet()).mapNotNull {
            val i = it.lastIndexOf('|'); val t = if (i > 0) it.substring(i + 1).toLongOrNull() else null
            if (t == null) null else it.substring(0, i) to t
        }.toMap()

    private fun enc(m: Map<String, Long>) = m.map { "${it.key}|${it.value}" }.toHashSet()

    fun add(ctx: Context, site: String) {
        val cur = active(ctx).toMutableSet(); cur.add(site)
        p(ctx).edit().putStringSet("sites", HashSet(cur)).putStringSet("sites_pending", enc(pending(ctx) - site)).apply()
    }

    fun requestRemove(ctx: Context, site: String) {
        val pend = pending(ctx).toMutableMap()
        if (site !in pend) pend[site] = System.currentTimeMillis() + Targets.REMOVE_DELAY_MS
        p(ctx).edit().putStringSet("sites_pending", enc(pend)).apply()
    }

    fun cancelRemove(ctx: Context, site: String) {
        p(ctx).edit().putStringSet("sites_pending", enc(pending(ctx) - site)).apply()
    }

    /** The blocked site this host belongs to, or null. */
    fun match(ctx: Context, host: String): String? {
        for (s in active(ctx)) {
            for (name in listOf(s) + (ALIASES[s] ?: emptyList()))
                if (host == name || host.endsWith(".$name")) return s
        }
        return null
    }
}

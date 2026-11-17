package fi.ropi.kitka

import android.content.Context
import android.content.SharedPreferences

/**
 * Rules and settings. A change that makes a rule stricter applies at once.
 * A change that makes it easier waits 24 hours (Allcott, Gentzkow & Song 2022: limits set
 * in advance work; the weak spot of blockers is loosening them on the spot).
 */
object Rules {

    const val DELAY_MS = 24L * 60 * 60 * 1000

    /** key, label, options (strictest last), default, "stricter" = bigger index. */
    class Rule(val key: String, val name: String, val sub: String, val options: List<Int>, val default: Int, val unit: String, val higherIsStricter: Boolean)

    val PAUSE = Rule("pause", "Pause", "Close works at once. Open anyway after this.", listOf(5, 10, 15, 20, 30), 10, "s", true)
    val LOCK = Rule("lock", "Lock after time is up", "The app stays closed this long.", listOf(10, 30, 60, 120), 30, "min", true)
    val LIMIT = Rule("limit", "Daily limit", "Shown on Today and in your Daily Proof.", listOf(120, 90, 60, 45, 30), 60, "min", false)
    val NIGHT = Rule("night", "Night pause, 23 to 07", "Longer pause at night. Your own test, not from a study.", listOf(0, 30, 60, 120), 60, "s", true)

    val ALL = listOf(PAUSE, LOCK, LIMIT, NIGHT)

    private fun p(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("kitka", Context.MODE_PRIVATE)

    fun get(ctx: Context, r: Rule): Int {
        val pr = p(ctx)
        val pendAt = pr.getLong("rule_${r.key}_at", 0L)
        if (pendAt in 1..System.currentTimeMillis()) {
            val v = pr.getInt("rule_${r.key}_next", r.default)
            pr.edit().putInt("rule_${r.key}", v).remove("rule_${r.key}_at").remove("rule_${r.key}_next").apply()
            return v
        }
        return pr.getInt("rule_${r.key}", r.default)
    }

    fun pending(ctx: Context, r: Rule): Pair<Int, Long>? {
        get(ctx, r)
        val pr = p(ctx)
        val at = pr.getLong("rule_${r.key}_at", 0L)
        return if (at > 0) pr.getInt("rule_${r.key}_next", r.default) to at else null
    }

    fun isStricter(r: Rule, newV: Int, oldV: Int): Boolean = if (r.higherIsStricter) newV > oldV else newV < oldV

    /** Returns true if applied now, false if it waits 24 h. */
    fun set(ctx: Context, r: Rule, v: Int, force: Boolean = false): Boolean {
        val cur = get(ctx, r)
        val e = p(ctx).edit()
        return if (force || v == cur || isStricter(r, v, cur)) {
            e.putInt("rule_${r.key}", v).remove("rule_${r.key}_at").remove("rule_${r.key}_next").apply()
            true
        } else {
            e.putInt("rule_${r.key}_next", v).putLong("rule_${r.key}_at", System.currentTimeMillis() + DELAY_MS).apply()
            false
        }
    }

    fun cancelPending(ctx: Context, r: Rule) {
        p(ctx).edit().remove("rule_${r.key}_at").remove("rule_${r.key}_next").apply()
    }

    fun format(r: Rule, v: Int): String = when {
        r == NIGHT && v == 0 -> "Off"
        else -> "$v ${r.unit}"
    }
}

/** Everything else the app remembers. */
object Store {
    private fun p(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("kitka", Context.MODE_PRIVATE)

    fun onboarded(ctx: Context) = p(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = p(ctx).edit().putBoolean("onboarded", true).apply()

    fun tourDone(ctx: Context) = p(ctx).getBoolean("tour_done", false)
    fun setTourDone(ctx: Context) = p(ctx).edit().putBoolean("tour_done", true).apply()

    fun age(ctx: Context) = p(ctx).getInt("age", 21)
    fun setAge(ctx: Context, a: Int) = p(ctx).edit().putInt("age", a).apply()

    fun wantsFor(ctx: Context): Set<String> = p(ctx).getStringSet("wants_for", emptySet()) ?: emptySet()
    fun setWantsFor(ctx: Context, s: Set<String>) = p(ctx).edit().putStringSet("wants_for", HashSet(s)).apply()

    fun whens(ctx: Context): Set<String> = p(ctx).getStringSet("whens", emptySet()) ?: emptySet()
    fun setWhens(ctx: Context, s: Set<String>) = p(ctx).edit().putStringSet("whens", HashSet(s)).apply()

    /** App locked until (epoch ms), per package. */
    fun lockedUntil(ctx: Context, pkg: String) = p(ctx).getLong("lock_$pkg", 0L)
    fun lock(ctx: Context, pkg: String, until: Long) = p(ctx).edit().putLong("lock_$pkg", until).apply()

    /** Service on/off log for the Daily Proof. "off|start|end" entries. */
    fun markServiceOn(ctx: Context) {
        val pr = p(ctx)
        val offAt = pr.getLong("svc_off_at", 0L)
        val e = pr.edit().putLong("svc_on_at", System.currentTimeMillis()).remove("svc_off_at")
        if (offAt > 0) {
            val set = (pr.getStringSet("svc_gaps", emptySet()) ?: emptySet()).toMutableSet()
            set.add("$offAt|${System.currentTimeMillis()}")
            // keep the last 30
            val trimmed = set.sortedBy { it.substringBefore('|').toLongOrNull() ?: 0L }.takeLast(30).toHashSet()
            e.putStringSet("svc_gaps", trimmed)
        }
        e.apply()
    }

    fun markServiceOff(ctx: Context) = p(ctx).edit().putLong("svc_off_at", System.currentTimeMillis()).apply()

    /** (start, end) of gaps when the pause was off. end = now if still off. */
    fun gaps(ctx: Context): List<Pair<Long, Long>> {
        val pr = p(ctx)
        val out = (pr.getStringSet("svc_gaps", emptySet()) ?: emptySet()).mapNotNull {
            val a = it.substringBefore('|').toLongOrNull(); val b = it.substringAfter('|').toLongOrNull()
            if (a != null && b != null) a to b else null
        }.toMutableList()
        val offAt = pr.getLong("svc_off_at", 0L)
        if (offAt > 0) out.add(offAt to System.currentTimeMillis())
        return out.sortedBy { it.first }
    }

    // ---------- Daily Proof by text message ----------
    fun friendPhone(ctx: Context): String { applyProofPending(ctx); return p(ctx).getString("friend_phone", "") ?: "" }
    fun stake(ctx: Context): Int { applyProofPending(ctx); return p(ctx).getInt("stake", 20) }

    /** Pending looser proof settings (phone, stake, at). */
    fun proofPending(ctx: Context): Triple<String, Int, Long>? {
        val pr = p(ctx); val at = pr.getLong("proof_at", 0L)
        return if (at > 0) Triple(pr.getString("proof_next_phone", "") ?: "", pr.getInt("proof_next_stake", 20), at) else null
    }

    private fun applyProofPending(ctx: Context) {
        val pr = p(ctx); val at = pr.getLong("proof_at", 0L)
        if (at in 1..System.currentTimeMillis()) {
            pr.edit().putString("friend_phone", pr.getString("proof_next_phone", "")).putInt("stake", pr.getInt("proof_next_stake", 20))
                .remove("proof_at").remove("proof_next_phone").remove("proof_next_stake").apply()
        }
    }

    /**
     * Phone: adding a friend when none is set applies now; changing or removing waits 24 h.
     * Stake: raising applies now; lowering waits 24 h. The two are handled separately.
     * Returns a short note on what happened.
     */
    fun setProof(ctx: Context, phone: String, stake: Int, force: Boolean = false): String {
        val curPhone = friendPhone(ctx); val curStake = stake(ctx)
        val e = p(ctx).edit()
        val notes = ArrayList<String>()
        var pendPhone = p(ctx).getString("proof_next_phone", null)
        var pendStake = if (p(ctx).contains("proof_next_stake")) p(ctx).getInt("proof_next_stake", curStake) else null
        if (phone != curPhone) {
            if (force || curPhone.isEmpty()) { e.putString("friend_phone", phone); pendPhone = null; notes.add("Saved.") }
            else { pendPhone = phone; notes.add(if (phone.isEmpty()) "Removing the friend waits 24 hours." else "Changing the number waits 24 hours.") }
        }
        if (stake != curStake) {
            if (force || stake > curStake) { e.putInt("stake", stake); pendStake = null; if (notes.isEmpty()) notes.add("Saved.") }
            else { pendStake = stake; notes.add("Lowering the stake waits 24 hours.") }
        }
        if (pendPhone != null || pendStake != null) {
            e.putString("proof_next_phone", pendPhone ?: curPhone).putInt("proof_next_stake", pendStake ?: curStake)
                .putLong("proof_at", System.currentTimeMillis() + Rules.DELAY_MS)
        } else e.remove("proof_at").remove("proof_next_phone").remove("proof_next_stake")
        e.apply()
        return notes.joinToString(" ").ifEmpty { "No change." }
    }

    fun cancelProofPending(ctx: Context) = p(ctx).edit().remove("proof_at").remove("proof_next_phone").remove("proof_next_stake").apply()

    /** Daily totals of minutes on selected apps, saved as days complete (Android keeps only ~a week). */
    fun dayTotals(ctx: Context): Map<String, Long> =
        (p(ctx).getStringSet("day_totals", emptySet()) ?: emptySet()).mapNotNull {
            val k = it.substringBefore('|'); val v = it.substringAfter('|').toLongOrNull()
            if (v != null) k to v else null
        }.toMap()

    fun saveDayTotals(ctx: Context, add: Map<String, Long>) {
        val cur = dayTotals(ctx).toMutableMap()
        var changed = false
        for ((k, v) in add) if (cur[k] == null) { cur[k] = v; changed = true }
        if (changed) p(ctx).edit().putStringSet("day_totals", cur.map { "${it.key}|${it.value}" }.toHashSet()).apply()
    }
}

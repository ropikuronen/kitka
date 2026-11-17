package fi.ropi.kitka

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Friends league over Firebase (Firestore + anonymous sign-in), plain REST, no SDK.
 * Each phone writes only its own document: name + minutes per day. Everyone in the
 * league can read the league. Nothing else is sent.
 */
object League {
    private const val KEY = "AIzaSyDreXScj7EGOVzlYWlSti45lKcm-vyR26E"
    private const val PROJECT = "finalapp-ab415"
    private const val DOCS = "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"

    class Member(val uid: String, val name: String, val days: Map<String, Int>) {
        fun monthDays(month: String) = days.filterKeys { it.startsWith(month) }
        fun avg(month: String): Float? { val d = monthDays(month); return if (d.isEmpty()) null else d.values.sum() / d.size.toFloat() }
        fun total(month: String) = monthDays(month).values.sum()
    }

    class Board(val code: String, val pot: Int, val members: List<Member>, val error: String?)

    private fun p(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("kitka", Context.MODE_PRIVATE)

    fun code(ctx: Context): String = p(ctx).getString("league_code", "") ?: ""
    fun name(ctx: Context): String = p(ctx).getString("league_name", "") ?: ""
    fun uid(ctx: Context): String = p(ctx).getString("fb_uid", "") ?: ""
    fun lastBoard(ctx: Context): String = p(ctx).getString("league_cache", "") ?: ""

    // ---------- auth ----------

    private fun token(ctx: Context): String {
        val pr = p(ctx)
        val tok = pr.getString("fb_token", null)
        val exp = pr.getLong("fb_exp", 0L)
        if (tok != null && System.currentTimeMillis() < exp - 60_000) return tok
        val refresh = pr.getString("fb_refresh", null)
        val json = if (refresh != null) {
            val r = http("POST", "https://securetoken.googleapis.com/v1/token?key=$KEY", null,
                "grant_type=refresh_token&refresh_token=$refresh", "application/x-www-form-urlencoded")
            JSONObject().put("idToken", r.getString("id_token")).put("refreshToken", r.getString("refresh_token"))
                .put("expiresIn", r.getString("expires_in")).put("localId", r.getString("user_id"))
        } else {
            http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$KEY", null, "{\"returnSecureToken\":true}")
        }
        val t = json.getString("idToken")
        pr.edit().putString("fb_token", t).putString("fb_refresh", json.getString("refreshToken"))
            .putString("fb_uid", json.getString("localId"))
            .putLong("fb_exp", System.currentTimeMillis() + json.getString("expiresIn").toLong() * 1000).apply()
        return t
    }

    // ---------- actions ----------

    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** Create a league. Returns the code. */
    fun create(ctx: Context, myName: String, pot: Int): String {
        val t = token(ctx)
        val code = newCode()
        val doc = JSONObject().put("fields", JSONObject()
            .put("pot", JSONObject().put("integerValue", pot.toString()))
            .put("created", JSONObject().put("stringValue", LocalDate.now().toString())))
        http("POST", "$DOCS/groups?documentId=$code", t, doc.toString())
        p(ctx).edit().putString("league_code", code).putString("league_name", myName).apply()
        push(ctx)
        return code
    }

    /** Join an existing league. Throws if the code does not exist. */
    fun join(ctx: Context, code: String, myName: String) {
        val t = token(ctx)
        val c = code.trim().uppercase()
        http("GET", "$DOCS/groups/$c", t, null)
        p(ctx).edit().putString("league_code", c).putString("league_name", myName).apply()
        push(ctx)
    }

    fun leave(ctx: Context) {
        p(ctx).edit().remove("league_code").remove("league_cache").apply()
    }

    /** Write my name and minutes for the last 40 days. */
    fun push(ctx: Context) {
        val code = code(ctx); if (code.isEmpty()) return
        val t = token(ctx)
        val uid = uid(ctx)
        val since = LocalDate.now().minusDays(40)
        val days = JSONObject()
        val totals = Store.dayTotals(ctx).toMutableMap()
        val (s, e) = Usage.dayBounds(LocalDate.now())
        if (Usage.hasPermission(ctx)) totals[LocalDate.now().toString()] = Usage.foregroundMs(ctx, s, e, Targets.active(ctx)).values.sum() / 60_000
        for ((k, v) in totals) if (runCatching { !LocalDate.parse(k).isBefore(since) }.getOrDefault(false))
            days.put(k, JSONObject().put("integerValue", v.toString()))
        val doc = JSONObject().put("fields", JSONObject()
            .put("name", JSONObject().put("stringValue", name(ctx).ifEmpty { "Me" }))
            .put("days", JSONObject().put("mapValue", JSONObject().put("fields", days))))
        http("PATCH", "$DOCS/groups/$code/members/$uid", t, doc.toString())
    }

    /** Read the league. */
    fun fetch(ctx: Context): Board {
        val code = code(ctx)
        return try {
            val t = token(ctx)
            val g = http("GET", "$DOCS/groups/$code", t, null)
            val pot = g.optJSONObject("fields")?.optJSONObject("pot")?.optString("integerValue")?.toIntOrNull() ?: 0
            val list = http("GET", "$DOCS/groups/$code/members?pageSize=100", t, null)
            val docs = list.optJSONArray("documents")
            val members = ArrayList<Member>()
            if (docs != null) for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val id = d.getString("name").substringAfterLast('/')
                val f = d.optJSONObject("fields") ?: continue
                val nm = f.optJSONObject("name")?.optString("stringValue") ?: "?"
                val dm = HashMap<String, Int>()
                val df = f.optJSONObject("days")?.optJSONObject("mapValue")?.optJSONObject("fields")
                if (df != null) for (k in df.keys()) df.getJSONObject(k).optString("integerValue").toIntOrNull()?.let { dm[k] = it }
                members.add(Member(id, nm, dm))
            }
            Board(code, pot, members, null)
        } catch (e: Exception) {
            Board(code, 0, emptyList(), e.message ?: "Could not reach the league.")
        }
    }

    /** Push then fetch; safe to call from a background thread. */
    fun sync(ctx: Context): Board? {
        if (code(ctx).isEmpty()) return null
        try { push(ctx) } catch (e: Exception) { }
        return fetch(ctx)
    }

    // ---------- http ----------

    private fun http(method: String, url: String, token: String?, body: String?, type: String = "application/json"): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        if (method == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") }
        else c.requestMethod = method
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw RuntimeException(if (code == 404) "No league with that code." else msg)
        }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}

package fi.ropi.kitka

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * A daily backup file in Documents/FinalApp. Files there stay on the phone even when
 * FinalApp is uninstalled. After reinstalling, "Restore my data" reads it back.
 * Nothing is sent anywhere.
 */
object Backup {
    private const val NAME = "finalapp-backup.json"
    private const val DIR = "Documents/FinalApp/"
    // Short-lived sign-in token is not saved; it is refreshed after restore.
    private val SKIP = setOf("fb_token", "fb_exp", "last_backup_day", "history_sync_day")

    fun lastDay(ctx: Context): String =
        ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).getString("last_backup_day", "") ?: ""

    /** Writes the backup once a day. Safe to call often. */
    fun daily(ctx: Context) {
        if (lastDay(ctx) == LocalDate.now().toString()) return
        if (!Store.onboarded(ctx)) return
        try { write(ctx) } catch (e: Exception) { }
    }

    fun write(ctx: Context) {
        val json = export(ctx).toString().toByteArray()
        val cr = ctx.contentResolver
        val files = MediaStore.Files.getContentUri("external")
        var uri: Uri? = null
        cr.query(files, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(NAME, DIR), null)?.use { c -> if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(files, c.getLong(0)) }
        if (uri == null) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
            }
            uri = cr.insert(files, v)
        }
        val u = uri ?: return
        cr.openOutputStream(u, "wt")?.use { it.write(json) }
        ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).edit()
            .putString("last_backup_day", LocalDate.now().toString()).apply()
    }

    private fun export(ctx: Context): JSONObject {
        val prefs = JSONObject()
        for ((k, v) in ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).all) {
            if (k in SKIP || v == null) continue
            val t = when (v) {
                is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"
                is String -> "s"; is Set<*> -> "set"; else -> continue
            }
            prefs.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
        }
        val events = JSONArray()
        Db.get(ctx).readableDatabase.rawQuery("SELECT ts, day, pkg, type FROM events", null).use { c ->
            while (c.moveToNext()) events.put(JSONArray().put(c.getLong(0)).put(c.getString(1)).put(c.getString(2)).put(c.getString(3)))
        }
        val dayApp = JSONArray(); for (r in Db.get(ctx).table("day_app")) dayApp.put(JSONArray(r))
        val dayHour = JSONArray(); for (r in Db.get(ctx).table("day_hour")) dayHour.put(JSONArray(r))
        return JSONObject().put("app", "FinalApp").put("version", 2)
            .put("saved", LocalDate.now().toString()).put("prefs", prefs).put("events", events)
            .put("day_app", dayApp).put("day_hour", dayHour)
    }

    /** Reads a backup the user picked. Returns the date it was saved. Throws if it is not a FinalApp backup. */
    fun restore(ctx: Context, uri: Uri): String {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw RuntimeException("Could not open the file.")
        val j = JSONObject(text)
        if (j.optString("app") != "FinalApp") throw RuntimeException("This is not a FinalApp backup.")
        val e = ctx.getSharedPreferences("kitka", Context.MODE_PRIVATE).edit()
        val prefs = j.getJSONObject("prefs")
        for (k in prefs.keys()) {
            val o = prefs.getJSONObject(k)
            when (o.getString("t")) {
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "s" -> e.putString(k, o.getString("v"))
                "set" -> { val a = o.getJSONArray("v"); e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toHashSet()) }
            }
        }
        e.remove("fb_token").remove("fb_exp").apply()
        val db = Db.get(ctx).writableDatabase
        val ev = j.getJSONArray("events")
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            for (i in 0 until ev.length()) {
                val a = ev.getJSONArray(i)
                db.insert("events", null, ContentValues().apply {
                    put("ts", a.getLong(0)); put("day", a.getString(1)); put("pkg", a.getString(2)); put("type", a.getString(3))
                })
            }
            val da = j.optJSONArray("day_app")
            if (da != null) { db.delete("day_app", null, null); for (i in 0 until da.length()) { val a = da.getJSONArray(i)
                db.insert("day_app", null, ContentValues().apply { put("day", a.getString(0)); put("pkg", a.getString(1)); put("ms", a.getLong(2)) }) } }
            val dh = j.optJSONArray("day_hour")
            if (dh != null) { db.delete("day_hour", null, null); for (i in 0 until dh.length()) { val a = dh.getJSONArray(i)
                db.insert("day_hour", null, ContentValues().apply { put("day", a.getString(0)); put("hour", a.getLong(1)); put("ms", a.getLong(2)) }) } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return j.optString("saved")
    }
}

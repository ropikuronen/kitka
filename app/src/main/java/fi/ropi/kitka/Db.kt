package fi.ropi.kitka

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Tapahtumatyypit, jotka kirjataan lokiin. */
object Ev {
    const val ATTEMPT = "attempt"     // yritit avata kohdesovelluksen
    const val RECHECK = "recheck"     // 10 min putkeen -> portti uudestaan
    const val CLOSE = "close"         // valitsit Sulje (tai poistuit portilta)
    const val CONTINUE = "continue"   // valitsit Jatka
    const val SCROLL = "scroll"       // yksi pyyhkäisy kohdesovelluksessa (arvio)
}

/** Paikallinen SQLite-loki. Mitään ei lähetetä minnekään. */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "kitka.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "day TEXT NOT NULL, " +
                "pkg TEXT NOT NULL, " +
                "type TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_events_day ON events(day)")
        createV2(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createV2(db)
    }

    /** Saved forever: minutes per app per day, and per hour per day. Android itself forgets after a while. */
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS day_app (day TEXT NOT NULL, pkg TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY(day, pkg))")
        db.execSQL("CREATE TABLE IF NOT EXISTS day_hour (day TEXT NOT NULL, hour INTEGER NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY(day, hour))")
    }

    fun savedDays(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT DISTINCT day FROM day_hour", null).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Writes one full day. hours[i] is minutes; -1 hour row keeps the day marked even when it was 0. */
    fun saveDay(day: String, apps: Map<String, Long>, hours: LongArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("day_app", "day = ?", arrayOf(day))
            db.delete("day_hour", "day = ?", arrayOf(day))
            for ((p, ms) in apps) if (ms > 0) db.insert("day_app", null, ContentValues().apply { put("day", day); put("pkg", p); put("ms", ms) })
            db.insert("day_hour", null, ContentValues().apply { put("day", day); put("hour", -1); put("ms", 0) })
            for (h in 0 until 24) if (hours[h] > 0) db.insert("day_hour", null, ContentValues().apply { put("day", day); put("hour", h); put("ms", hours[h]) })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** day -> pkg -> minutes */
    fun appsByDay(): Map<String, Map<String, Long>> {
        val out = HashMap<String, HashMap<String, Long>>()
        readableDatabase.rawQuery("SELECT day, pkg, ms FROM day_app", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getString(0)) { HashMap() }[c.getString(1)] = c.getLong(2) / 60_000
        }
        return out
    }

    /** day -> 24 hour minutes */
    fun hoursByDay(): Map<String, LongArray> {
        val out = HashMap<String, LongArray>()
        readableDatabase.rawQuery("SELECT day, hour, ms FROM day_hour WHERE hour >= 0", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getString(0)) { LongArray(24) }[c.getInt(1)] = c.getLong(2)
        }
        return out
    }

    fun table(name: String): List<List<Any>> {
        val out = ArrayList<List<Any>>()
        readableDatabase.rawQuery("SELECT * FROM $name", null).use { c ->
            while (c.moveToNext()) out.add((0 until c.columnCount).map { if (c.getType(it) == android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(it) else c.getString(it) })
        }
        return out
    }

    fun log(pkg: String, type: String) {
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("day", LocalDate.now().toString())
            put("pkg", pkg)
            put("type", type)
        }
        writableDatabase.insert("events", null, v)
    }

    /** päivä -> paketti -> tyyppi -> määrä, alkaen päivästä [fromDay]. */
    fun counts(fromDay: LocalDate): Map<String, Map<String, Map<String, Int>>> {
        val out = HashMap<String, HashMap<String, HashMap<String, Int>>>()
        readableDatabase.rawQuery(
            "SELECT day, pkg, type, COUNT(*) FROM events WHERE day >= ? GROUP BY day, pkg, type",
            arrayOf(fromDay.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.getOrPut(c.getString(0)) { HashMap() }
                    .getOrPut(c.getString(1)) { HashMap() }[c.getString(2)] = c.getInt(3)
            }
        }
        return out
    }

    fun countToday(pkg: String, type: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM events WHERE day = ? AND pkg = ? AND type = ?",
            arrayOf(LocalDate.now().toString(), pkg, type)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) {
                inst ?: Db(ctx.applicationContext).also { inst = it }
            }
    }
}

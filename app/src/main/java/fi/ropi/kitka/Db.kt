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
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "kitka.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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

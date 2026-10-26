package com.riverrunner.data

import android.content.Context
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.SessionSummary
import com.riverrunner.engine.TableType
import java.io.File
import java.util.Locale

/** User settings and career totals (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("river_runner", Context.MODE_PRIVATE)

    var chinese: Boolean
        get() = sp.getBoolean("zh", Locale.getDefault().language == "zh")
        set(v) = sp.edit().putBoolean("zh", v).apply()

    var practiceDefault: Boolean
        get() = sp.getBoolean("practice", true)
        set(v) = sp.edit().putBoolean("practice", v).apply()

    /** 0 slow, 1 normal, 2 fast */
    var speed: Int
        get() = sp.getInt("speed", 1)
        set(v) = sp.edit().putInt("speed", v).apply()

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var showInBB: Boolean
        get() = sp.getBoolean("bb", true)
        set(v) = sp.edit().putBoolean("bb", v).apply()

    // last used setup
    var lastMode: GameMode
        get() = GameMode.fromCode(sp.getString("mode", "C") ?: "C")
        set(v) = sp.edit().putString("mode", v.code).apply()
    var lastSize: Int
        get() = sp.getInt("size", 6)
        set(v) = sp.edit().putInt("size", v).apply()
    var lastStackBB: Int
        get() = sp.getInt("stackBB", 100)
        set(v) = sp.edit().putInt("stackBB", v).apply()
    var lastTableType: TableType
        get() = if (sp.getString("table", "G") == "M") TableType.MIXED else TableType.GTO
        set(v) = sp.edit().putString("table", if (v == TableType.MIXED) "M" else "G").apply()
    var lastHandsPerLevel: Int
        get() = sp.getInt("hpl", 10)
        set(v) = sp.edit().putInt("hpl", v).apply()
    var lastAnte: Boolean
        get() = sp.getBoolean("ante", false)
        set(v) = sp.edit().putBoolean("ante", v).apply()

    // career totals
    val cashSessions: Int get() = sp.getInt("c_sessions", 0)
    val cashProfitBB: Double get() = sp.getFloat("c_profit", 0f).toDouble()
    val tourneys: Int get() = sp.getInt("t_count", 0)
    val tourneyWins: Int get() = sp.getInt("t_wins", 0)
    val tourneyItm: Int get() = sp.getInt("t_itm", 0)
    val tourneyProfit: Double get() = sp.getFloat("t_profit", 0f).toDouble()

    fun recordSession(s: SessionSummary, paidPlaces: Int) {
        val e = sp.edit()
        when (s.mode) {
            GameMode.CASH -> {
                e.putInt("c_sessions", cashSessions + 1)
                e.putFloat("c_profit", (cashProfitBB + s.heroProfit).toFloat())
            }
            GameMode.TOURNAMENT -> {
                e.putInt("t_count", tourneys + 1)
                if (s.heroPlace == 1) e.putInt("t_wins", tourneyWins + 1)
                if (s.heroPlace in 1..paidPlaces) e.putInt("t_itm", tourneyItm + 1)
                e.putFloat("t_profit", (tourneyProfit + s.heroProfit).toFloat())
            }
        }
        e.apply()
    }
}

/**
 * Hand histories, one encoded line per hand (~400 bytes).
 * Keeps the newest [LIMIT] hands plus every starred hand.
 */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "hands.txt")
    private val records = ArrayList<HandRecord>()

    companion object { const val LIMIT = 1000 }

    init {
        if (file.exists()) {
            file.forEachLine { line -> HandRecord.decode(line)?.let { records += it } }
        }
    }

    @Synchronized fun all(): List<HandRecord> = records.toList()

    @Synchronized fun add(r: HandRecord) {
        records += r
        val nonFav = records.count { !it.favorite }
        if (nonFav > LIMIT) {
            var drop = nonFav - LIMIT
            val iter = records.iterator()
            while (iter.hasNext() && drop > 0) {
                if (!iter.next().favorite) { iter.remove(); drop-- }
            }
            rewrite()
        } else {
            file.appendText(r.encode() + "\n")
        }
    }

    @Synchronized fun toggleFavorite(id: Long): HandRecord? {
        val i = records.indexOfFirst { it.id == id }
        if (i < 0) return null
        val updated = records[i].copy(favorite = !records[i].favorite)
        records[i] = updated
        rewrite()
        return updated
    }

    @Synchronized fun clearNonFavorites() {
        records.removeAll { !it.favorite }
        rewrite()
    }

    private fun rewrite() {
        val tmp = File(file.parentFile, "hands.tmp")
        tmp.writeText(records.joinToString("") { it.encode() + "\n" })
        tmp.renameTo(file)
    }
}

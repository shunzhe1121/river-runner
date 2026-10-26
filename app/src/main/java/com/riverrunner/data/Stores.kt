package com.riverrunner.data

import android.content.Context
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.Facts
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Progress
import com.riverrunner.engine.TableType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** App-wide settings (SharedPreferences). Career data lives in save slots, see [ProfileStore]. */
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

    /** Career totals written by version 0.2 and earlier (read once when migrating to save slots). */
    fun legacyCareer(): Map<String, Long> {
        if (!sp.contains("c_sessions") && !sp.contains("t_count")) return emptyMap()
        return mapOf(
            Progress.CASH_SESSIONS to sp.getInt("c_sessions", 0).toLong(),
            Progress.CASH_PROFIT_CENTI_BB to Math.round(sp.getFloat("c_profit", 0f) * 100.0),
            Progress.TOURNEYS to sp.getInt("t_count", 0).toLong(),
            Progress.TOURNEY_WINS to sp.getInt("t_wins", 0).toLong(),
            Progress.TOURNEY_ITM to sp.getInt("t_itm", 0).toLong(),
            Progress.TOURNEY_POINTS to Math.round(sp.getFloat("t_profit", 0f).toDouble()),
        )
    }
}

/** One save slot: career counters and unlocked achievements. Hand history is stored next to it. */
data class Profile(
    val id: String,
    val name: String,
    val created: Long,
    /** 0..3, the suit shown on the player card */
    val emblem: Int,
    val counters: Map<String, Long>,
    /** achievement id -> unlock time */
    val unlocked: Map<String, Long>,
) {
    fun counter(key: String): Long = counters[key] ?: 0L

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("created", created); put("emblem", emblem)
        put("counters", JSONObject().apply { counters.forEach { (k, v) -> put(k, v) } })
        put("unlocked", JSONObject().apply { unlocked.forEach { (k, v) -> put(k, v) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            fun map(obj: JSONObject?): Map<String, Long> {
                if (obj == null) return emptyMap()
                val m = HashMap<String, Long>()
                obj.keys().forEach { k -> m[k] = obj.getLong(k) }
                return m
            }
            return Profile(
                o.getString("id"), o.getString("name"), o.optLong("created"), o.optInt("emblem"),
                map(o.optJSONObject("counters")), map(o.optJSONObject("unlocked")),
            )
        }
    }
}

/**
 * Save slots stored as files: profiles.json (index), profile_<id>.json, hands_<id>.txt.
 * The first launch after upgrading moves the old single history into the first slot.
 */
class ProfileStore(private val dir: File, private val prefs: Prefs?, private val defaultName: (Int) -> String) {
    companion object { const val MAX_SLOTS = 6 }

    private val indexFile = File(dir, "profiles.json")
    private val profiles = LinkedHashMap<String, Profile>()
    var activeId: String = ""; private set

    init {
        if (indexFile.exists()) {
            runCatching {
                val idx = JSONObject(indexFile.readText())
                val ids = idx.getJSONArray("ids")
                for (i in 0 until ids.length()) {
                    val id = ids.getString(i)
                    val f = profileFile(id)
                    if (f.exists()) profiles[id] = Profile.fromJson(JSONObject(f.readText()))
                }
                activeId = idx.optString("active")
            }
        }
        if (profiles.isEmpty()) migrateOrCreate()
        if (activeId !in profiles) activeId = profiles.keys.first()
        writeIndex()
    }

    private fun profileFile(id: String) = File(dir, "profile_$id.json")
    fun historyFile(id: String) = File(dir, "hands_$id.txt")

    fun all(): List<Profile> = profiles.values.toList()
    fun active(): Profile = profiles.getValue(activeId)
    fun get(id: String): Profile? = profiles[id]

    private fun newId(): String = "p" + System.currentTimeMillis().toString(36) + (100..999).random()

    private fun migrateOrCreate() {
        val p = create(defaultName(1), activate = true)
        val legacyHands = File(dir, "hands.txt")
        val counters = HashMap<String, Long>()
        if (prefs != null) Progress.apply(counters, prefs.legacyCareer())
        if (legacyHands.exists()) {
            legacyHands.forEachLine { line ->
                HandRecord.decode(line)?.let { r -> runCatching { Progress.apply(counters, Facts.forHand(r)) } }
            }
            legacyHands.renameTo(historyFile(p.id))
        }
        if (counters.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val unlocked = Achievements.newlyUnlocked(counters, emptySet()).associate { it.id to now }
            save(p.copy(counters = counters, unlocked = unlocked))
        }
    }

    fun create(name: String, activate: Boolean = false): Profile {
        val used = profiles.values.map { it.emblem }.toSet()
        val emblem = (0..3).firstOrNull { it !in used } ?: (profiles.size % 4)
        val p = Profile(newId(), name, System.currentTimeMillis(), emblem, emptyMap(), emptyMap())
        profiles[p.id] = p
        save(p)
        if (activate) activeId = p.id
        writeIndex()
        return p
    }

    fun save(p: Profile) {
        profiles[p.id] = p
        val tmp = File(dir, "profile_${p.id}.tmp")
        tmp.writeText(p.toJson().toString())
        tmp.renameTo(profileFile(p.id))
    }

    fun rename(id: String, name: String) {
        profiles[id]?.let { save(it.copy(name = name)) }
    }

    fun activate(id: String) {
        if (id in profiles) { activeId = id; writeIndex() }
    }

    /** Deletes a slot and its files. Deleting the last slot leaves a fresh, empty one. */
    fun delete(id: String) {
        profiles.remove(id) ?: return
        profileFile(id).delete()
        historyFile(id).delete()
        if (profiles.isEmpty()) create(defaultName(1), activate = true)
        if (activeId == id) activeId = profiles.keys.first()
        writeIndex()
    }

    fun nextDefaultName(): String {
        var i = profiles.size + 1
        while (profiles.values.any { it.name == defaultName(i) }) i++
        return defaultName(i)
    }

    private fun writeIndex() {
        val o = JSONObject()
        o.put("active", activeId)
        o.put("ids", JSONArray(profiles.keys.toList()))
        indexFile.writeText(o.toString())
    }
}

/**
 * Hand histories of one save slot, one encoded line per hand (~400 bytes).
 * Keeps the newest [LIMIT] hands plus every starred hand.
 */
class HistoryStore(private val file: File) {
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

    @Synchronized fun count(): Int = records.size

    private fun rewrite() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(records.joinToString("") { it.encode() + "\n" })
        tmp.renameTo(file)
    }
}

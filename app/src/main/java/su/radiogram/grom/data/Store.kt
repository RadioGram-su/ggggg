package su.radiogram.grom.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import su.radiogram.grom.audio.Preset
import su.radiogram.grom.audio.Presets
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val ids: List<Long>, val created: Long)
data class Stat(val plays: Int, val last: Long)
data class TagOverride(val title: String?, val artist: String?, val album: String?, val cover: String?)

data class Settings(
    val amoled: Boolean = false,
    val coverTint: Boolean = true,
    val haptics: Boolean = true,
    val junkFilter: Boolean = true,
    val minDurSec: Int = 30,
    val boost: Int = 200,
    val speakerGuard: Boolean = true,
    val limiter: Boolean = true,
    val sort: Int = 0,
)

data class EqState(
    val presetId: String = "flat",
    val bands: List<Float> = List(10) { 0f },
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val warmth: Float = 0f,
    val comp: Float = 0f,
    val width: Float = 1f,
    val reverb: Float = 0f,
    val room: Float = 0.5f,
    val lowCut: Float = 0f,
    val highCut: Float = 0f,
    val eightD: Boolean = false,
    val modified: Boolean = false,
)

data class UserPreset(val id: String, val name: String, val eq: EqState)
data class QueueSnap(val ids: List<Long>, val index: Int, val pos: Long)

fun Preset.toEq(eightD: Boolean) = EqState(
    presetId = id, bands = bands, preamp = preamp, bass = bass, warmth = warmth, comp = comp,
    width = width, reverb = reverb, room = room, lowCut = lowCut, highCut = highCut, eightD = eightD, modified = false,
)

/** Всё, что пользователь накопил: избранное, плейлисты, статистика, настройки. Хранится в JSON. */
object Store {
    val favorites = MutableStateFlow<List<Long>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val stats = MutableStateFlow<Map<Long, Stat>>(emptyMap())
    val positions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val overrides = MutableStateFlow<Map<Long, TagOverride>>(emptyMap())
    val legacyTrash = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val lyricsFiles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val junkAllow = MutableStateFlow<Set<Long>>(emptySet())
    val settings = MutableStateFlow(Settings())
    val eq = MutableStateFlow(EqState())
    val userPresets = MutableStateFlow<List<UserPreset>>(emptyList())
    @Volatile var queue: QueueSnap? = null
    @Volatile var lastSeenAdded: Long = 0L
    @Volatile var firstRun = true

    private lateinit var file: File
    lateinit var appDir: File
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    fun init(ctx: Context) {
        appDir = ctx.filesDir
        file = File(ctx.filesDir, "grom.json")
        try {
            if (file.exists()) load(JSONObject(file.readText()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------- изменения ----------

    fun isFav(id: Long) = favorites.value.contains(id)

    fun toggleFavorite(id: Long): Boolean {
        var now = false
        favorites.update { list ->
            if (list.contains(id)) list - id else { now = true; listOf(id) + list }
        }
        save(); return now
    }

    fun createPlaylist(name: String, ids: List<Long> = emptyList()): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Без названия" }, ids.distinct(), System.currentTimeMillis())
        playlists.update { it + p }
        save(); return p
    }

    fun addToPlaylist(pid: String, ids: List<Long>) {
        playlists.update { list -> list.map { if (it.id == pid) it.copy(ids = (it.ids + ids).distinct()) else it } }
        save()
    }

    fun removeFromPlaylist(pid: String, id: Long) {
        playlists.update { list -> list.map { if (it.id == pid) it.copy(ids = it.ids - id) else it } }
        save()
    }

    fun renamePlaylist(pid: String, name: String) {
        playlists.update { list -> list.map { if (it.id == pid) it.copy(name = name.trim().ifEmpty { it.name }) else it } }
        save()
    }

    fun deletePlaylist(pid: String) {
        playlists.update { list -> list.filterNot { it.id == pid } }
        save()
    }

    fun recordPlay(id: Long) {
        stats.update { m ->
            val s = m[id]
            m + (id to Stat((s?.plays ?: 0) + 1, System.currentTimeMillis()))
        }
        save()
    }

    fun savePosition(id: Long, pos: Long) {
        positions.update { it + (id to pos) }
        save()
    }

    fun clearPosition(id: Long) {
        if (!positions.value.containsKey(id)) return
        positions.update { it - id }
        save()
    }

    fun setOverride(id: Long, o: TagOverride?) {
        overrides.update { if (o == null) it - id else it + (id to o) }
        save()
    }

    fun trashLegacy(ids: List<Long>) {
        val now = System.currentTimeMillis()
        legacyTrash.update { m -> m + ids.associateWith { now } }
        save()
    }

    fun restoreLegacy(ids: List<Long>) {
        legacyTrash.update { it - ids.toSet() }
        save()
    }

    fun setLyricsFile(id: Long, path: String?) {
        lyricsFiles.update { if (path == null) it - id else it + (id to path) }
        save()
    }

    fun allowJunk(id: Long) {
        junkAllow.update { it + id }
        save()
    }

    fun updateSettings(f: (Settings) -> Settings) {
        settings.update(f)
        save()
    }

    fun updateEq(f: (EqState) -> EqState) {
        eq.update(f)
        save()
    }

    fun applyPreset(p: Preset) {
        eq.update { p.toEq(it.eightD) }
        save()
    }

    fun applyUserPreset(u: UserPreset) {
        eq.update { u.eq.copy(eightD = it.eightD, modified = false) }
        save()
    }

    fun saveUserPreset(name: String) {
        val id = "u_" + UUID.randomUUID().toString().take(8)
        val e = eq.value.copy(presetId = id, modified = false)
        userPresets.update { it + UserPreset(id, name.trim().ifEmpty { "МОЙ" }.uppercase(), e) }
        eq.value = e
        save()
    }

    fun deleteUserPreset(id: String) {
        userPresets.update { list -> list.filterNot { it.id == id } }
        save()
    }

    fun saveQueue(q: QueueSnap) {
        queue = q
        save()
    }

    fun markSeen(maxAdded: Long) {
        if (maxAdded > lastSeenAdded) {
            lastSeenAdded = maxAdded
            save()
        }
    }

    fun markFirstRunDone() {
        if (firstRun) { firstRun = false; save() }
    }

    // ---------- сохранение ----------

    fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            writeNow()
        }
    }

    @Synchronized
    private fun writeNow() {
        try {
            val tmp = File(file.parentFile, "grom.json.tmp")
            tmp.writeText(toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("v", 1)
        o.put("fav", JSONArray(favorites.value))
        o.put("pl", JSONArray().apply {
            playlists.value.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name).put("created", p.created).put("ids", JSONArray(p.ids)))
            }
        })
        o.put("stats", JSONObject().apply { stats.value.forEach { (k, v) -> put(k.toString(), JSONArray().put(v.plays).put(v.last)) } })
        o.put("pos", JSONObject().apply { positions.value.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("ovr", JSONObject().apply {
            overrides.value.forEach { (k, v) ->
                put(k.toString(), JSONObject().put("t", v.title ?: JSONObject.NULL).put("a", v.artist ?: JSONObject.NULL)
                    .put("al", v.album ?: JSONObject.NULL).put("c", v.cover ?: JSONObject.NULL))
            }
        })
        o.put("trash", JSONObject().apply { legacyTrash.value.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("lyr", JSONObject().apply { lyricsFiles.value.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("allow", JSONArray(junkAllow.value.toList()))
        val s = settings.value
        o.put("set", JSONObject().put("amoled", s.amoled).put("tint", s.coverTint).put("hap", s.haptics).put("junk", s.junkFilter)
            .put("min", s.minDurSec).put("boost", s.boost).put("guard", s.speakerGuard).put("lim", s.limiter).put("sort", s.sort))
        o.put("eq", eqToJson(eq.value))
        o.put("up", JSONArray().apply { userPresets.value.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("eq", eqToJson(it.eq))) } })
        queue?.let { q -> o.put("queue", JSONObject().put("ids", JSONArray(q.ids)).put("i", q.index).put("p", q.pos)) }
        o.put("seen", lastSeenAdded)
        o.put("first", firstRun)
        return o
    }

    private fun eqToJson(e: EqState) = JSONObject().put("p", e.presetId).put("b", JSONArray(e.bands.map { it.toDouble() }))
        .put("pre", e.preamp.toDouble()).put("bass", e.bass.toDouble()).put("warm", e.warmth.toDouble()).put("comp", e.comp.toDouble())
        .put("w", e.width.toDouble()).put("rv", e.reverb.toDouble()).put("room", e.room.toDouble()).put("lc", e.lowCut.toDouble())
        .put("hc", e.highCut.toDouble()).put("8d", e.eightD).put("mod", e.modified)

    private fun eqFromJson(o: JSONObject): EqState {
        val b = o.optJSONArray("b")
        val bands = if (b != null && b.length() == 10) List(10) { b.optDouble(it, 0.0).toFloat() } else List(10) { 0f }
        return EqState(
            presetId = o.optString("p", "flat"), bands = bands,
            preamp = o.optDouble("pre", 0.0).toFloat(), bass = o.optDouble("bass", 0.0).toFloat(),
            warmth = o.optDouble("warm", 0.0).toFloat(), comp = o.optDouble("comp", 0.0).toFloat(),
            width = o.optDouble("w", 1.0).toFloat(), reverb = o.optDouble("rv", 0.0).toFloat(),
            room = o.optDouble("room", 0.5).toFloat(), lowCut = o.optDouble("lc", 0.0).toFloat(),
            highCut = o.optDouble("hc", 0.0).toFloat(), eightD = o.optBoolean("8d", false), modified = o.optBoolean("mod", false),
        )
    }

    private fun JSONArray?.longs(): List<Long> {
        if (this == null) return emptyList()
        return List(length()) { optLong(it) }
    }

    private fun load(o: JSONObject) {
        favorites.value = o.optJSONArray("fav").longs()
        o.optJSONArray("pl")?.let { a ->
            playlists.value = List(a.length()) { i ->
                val p = a.getJSONObject(i)
                Playlist(p.getString("id"), p.optString("name"), p.optJSONArray("ids").longs(), p.optLong("created"))
            }
        }
        o.optJSONObject("stats")?.let { s ->
            val m = HashMap<Long, Stat>()
            s.keys().forEach { k -> val a = s.getJSONArray(k); m[k.toLong()] = Stat(a.optInt(0), a.optLong(1)) }
            stats.value = m
        }
        o.optJSONObject("pos")?.let { s -> positions.value = s.keys().asSequence().associate { it.toLong() to s.optLong(it) } }
        o.optJSONObject("ovr")?.let { s ->
            overrides.value = s.keys().asSequence().associate { k ->
                val v = s.getJSONObject(k)
                fun str(n: String): String? = if (v.isNull(n)) null else v.optString(n)
                k.toLong() to TagOverride(str("t"), str("a"), str("al"), str("c"))
            }
        }
        o.optJSONObject("trash")?.let { s -> legacyTrash.value = s.keys().asSequence().associate { it.toLong() to s.optLong(it) } }
        o.optJSONObject("lyr")?.let { s -> lyricsFiles.value = s.keys().asSequence().associate { it.toLong() to s.optString(it) } }
        junkAllow.value = o.optJSONArray("allow").longs().toSet()
        o.optJSONObject("set")?.let { s ->
            settings.value = Settings(
                amoled = s.optBoolean("amoled", false), coverTint = s.optBoolean("tint", true), haptics = s.optBoolean("hap", true),
                junkFilter = s.optBoolean("junk", true), minDurSec = s.optInt("min", 30), boost = s.optInt("boost", 200),
                speakerGuard = s.optBoolean("guard", true), limiter = s.optBoolean("lim", true), sort = s.optInt("sort", 0),
            )
        }
        o.optJSONObject("eq")?.let { eq.value = eqFromJson(it) }
        o.optJSONArray("up")?.let { a ->
            userPresets.value = List(a.length()) { i ->
                val u = a.getJSONObject(i)
                UserPreset(u.getString("id"), u.optString("name"), eqFromJson(u.getJSONObject("eq")))
            }
        }
        o.optJSONObject("queue")?.let { q -> queue = QueueSnap(q.optJSONArray("ids").longs(), q.optInt("i"), q.optLong("p")) }
        lastSeenAdded = o.optLong("seen", 0L)
        firstRun = o.optBoolean("first", true)
    }

    fun presetName(id: String): String =
        Presets.byId(id)?.name ?: userPresets.value.firstOrNull { it.id == id }?.name ?: "СВОЙ"
}

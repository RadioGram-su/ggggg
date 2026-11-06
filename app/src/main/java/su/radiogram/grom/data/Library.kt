package su.radiogram.grom.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Трек в библиотеке (с учётом правок тегов). */
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val path: String,
    val folder: String,
    val mime: String,
    val trackNo: Int,
    val year: Int,
    val coverPath: String?,
    val searchKey: String,
) {
    val isNew: Boolean get() = dateAdded * 1000 > System.currentTimeMillis() - 3L * 24 * 3600 * 1000
    val artUri: Uri
        get() = if (coverPath != null) Uri.fromFile(File(coverPath))
        else Uri.parse("content://media/external/audio/media/$id/albumart")

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}

/** Сырые данные MediaStore до фильтров и правок. */
data class RawTrack(
    val id: Long, val title: String, val artist: String, val album: String, val albumId: Long,
    val durationMs: Long, val dateAdded: Long, val path: String, val relPath: String, val displayName: String,
    val mime: String, val trackNo: Int, val year: Int, val flags: Int,
)

private const val F_RINGTONE = 1
private const val F_NOTIFICATION = 2
private const val F_ALARM = 4
private const val F_RECORDING = 8
private const val F_PODCAST = 16

const val UNKNOWN_ARTIST = "Неизвестный артист"
const val UNKNOWN_ALBUM = "Без альбома"

object Library {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val raw = MutableStateFlow<List<RawTrack>>(emptyList())
    val loaded = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    /** Сколько новых файлов приехало, пока приложение открыто. */
    val arrivals = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private var started = false
    private var refreshJob: Job? = null

    /** Все треки (включая скрытые фильтром), по id. */
    val all: StateFlow<Map<Long, Track>>
    /** Видимая библиотека, новые сверху. */
    val tracks: StateFlow<List<Track>>
    /** Скрытые фильтром мусора. */
    val hidden: StateFlow<List<Pair<Track, String>>>

    init {
        val built = combine(raw, Store.overrides, Store.settings, Store.junkAllow, Store.legacyTrash) { r, ovr, s, allow, trash ->
            build(r, ovr, s, allow, trash)
        }.stateIn(scope, SharingStarted.Eagerly, Built(emptyMap(), emptyList(), emptyList()))
        all = built.map { it.all }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
        tracks = built.map { it.visible }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        hidden = built.map { it.hidden }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    private data class Built(val all: Map<Long, Track>, val visible: List<Track>, val hidden: List<Pair<Track, String>>)

    fun init(ctx: Context) { app = ctx.applicationContext }

    operator fun get(id: Long): Track? = all.value[id]

    fun hasPermission(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (started) { refresh(); return }
        if (!hasPermission(app)) return
        started = true
        app.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { refresh(1200) }
            })
        refresh(0)
    }

    fun refresh(debounce: Long = 0) {
        if (!started) return
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            if (debounce > 0) delay(debounce)
            scanning.value = true
            try {
                val before = raw.value.map { it.id }.toHashSet()
                val now = scan()
                val wasLoaded = loaded.value
                raw.value = now
                loaded.value = true
                if (wasLoaded && before.isNotEmpty()) {
                    val added = now.count { it.id !in before }
                    if (added > 0) arrivals.tryEmit(added)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loaded.value = true
            } finally {
                scanning.value = false
            }
        }
    }

    private fun scan(): List<RawTrack> {
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_PODCAST,
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 31) cols += MediaStore.Audio.Media.IS_RECORDING
        val out = ArrayList<RawTrack>()
        app.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols.toTypedArray(), null, null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val iName = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val iMime = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val iTrack = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val iRing = c.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
            val iNotif = c.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
            val iAlarm = c.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
            val iPod = c.getColumnIndex(MediaStore.Audio.Media.IS_PODCAST)
            val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val iRec = if (Build.VERSION.SDK_INT >= 31) c.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING) else -1
            fun s(i: Int) = if (i >= 0) c.getString(i) ?: "" else ""
            fun l(i: Int) = if (i >= 0 && !c.isNull(i)) c.getLong(i) else 0L
            fun f(i: Int) = if (i >= 0 && !c.isNull(i) && c.getInt(i) != 0) 1 else 0
            while (c.moveToNext()) {
                val flags = f(iRing) * F_RINGTONE or f(iNotif) * F_NOTIFICATION or f(iAlarm) * F_ALARM or
                    f(iRec) * F_RECORDING or f(iPod) * F_PODCAST
                val name = s(iName)
                out += RawTrack(
                    id = c.getLong(iId),
                    title = s(iTitle).ifBlank { name.substringBeforeLast('.') },
                    artist = s(iArtist),
                    album = s(iAlbum),
                    albumId = l(iAlbumId),
                    durationMs = l(iDur),
                    dateAdded = l(iAdded),
                    path = s(iData),
                    relPath = s(iRel),
                    displayName = name,
                    mime = s(iMime),
                    trackNo = (l(iTrack) % 1000).toInt(),
                    year = l(iYear).toInt(),
                    flags = flags,
                )
            }
        }
        return out
    }

    /** Почему файл считается мусором (или null, если это музыка). */
    fun junkReason(r: RawTrack, minDurSec: Int): String? {
        val p = (r.path + "/" + r.relPath).lowercase()
        val ext = r.displayName.substringAfterLast('.', "").lowercase()
        val unknownArtist = r.artist.isBlank() || r.artist == "<unknown>"
        return when {
            r.flags and F_RINGTONE != 0 -> "Рингтон"
            r.flags and F_NOTIFICATION != 0 -> "Звук уведомления"
            r.flags and F_ALARM != 0 -> "Будильник"
            r.flags and F_RECORDING != 0 -> "Запись диктофона"
            p.contains("voice notes") || p.contains("voice_notes") -> "Голосовое WhatsApp"
            p.contains("whatsapp") && (ext == "opus" || unknownArtist) -> "Голосовое WhatsApp"
            p.contains("telegram") && (ext == "ogg" || ext == "oga" || ext == "opus") && unknownArtist -> "Голосовое Telegram"
            p.contains("/call") || p.contains("callrecord") || p.contains("call_rec") || p.contains("call recordings") -> "Запись звонка"
            p.contains("/recordings/") || p.contains("voice recorder") || p.contains("sound_recorder") || p.contains("soundrecorder") -> "Запись диктофона"
            p.contains("/ringtones/") || p.contains("/notifications/") || p.contains("/alarms/") || p.contains("/ui/") -> "Системный звук"
            minDurSec > 0 && r.durationMs in 1 until minDurSec * 1000L -> "Короче ${minDurSec} сек"
            r.durationMs == 0L && ext in setOf("ogg", "opus", "amr", "3gp", "m4a") && unknownArtist -> "Пустая запись"
            else -> null
        }
    }

    private fun build(
        r: List<RawTrack>, ovr: Map<Long, TagOverride>, s: Settings, allow: Set<Long>, trash: Map<Long, Long>,
    ): Built {
        val all = HashMap<Long, Track>(r.size * 2)
        val visible = ArrayList<Track>(r.size)
        val hidden = ArrayList<Pair<Track, String>>()
        for (x in r) {
            val o = ovr[x.id]
            val artist = o?.artist?.takeIf { it.isNotBlank() } ?: x.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ARTIST
            val title = o?.title?.takeIf { it.isNotBlank() } ?: x.title
            val album = o?.album?.takeIf { it.isNotBlank() } ?: x.album.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ALBUM
            val folder = x.relPath.trimEnd('/').substringAfterLast('/').ifEmpty { x.path.substringBeforeLast('/').substringAfterLast('/') }
            val t = Track(
                id = x.id,
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, x.id),
                title = title, artist = artist, album = album, albumId = x.albumId,
                durationMs = x.durationMs, dateAdded = x.dateAdded, path = x.path, folder = folder,
                mime = x.mime, trackNo = x.trackNo, year = x.year,
                coverPath = o?.cover?.takeIf { File(it).exists() },
                searchKey = Search.key("$title $artist $album"),
            )
            all[x.id] = t
            if (trash.containsKey(x.id)) continue
            val reason = if (s.junkFilter && x.id !in allow) junkReason(x, s.minDurSec) else null
            if (reason != null) hidden += t to reason else visible += t
        }
        return Built(all, visible, hidden)
    }
}

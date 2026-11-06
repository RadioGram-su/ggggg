package su.radiogram.grom.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

// ======================= Обложки =======================

object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val missing = HashSet<String>()
    private val accents = HashMap<String, Color?>()

    fun cached(t: Track, px: Int): ImageBitmap? = cache.get(key(t, px))?.asImageBitmap()

    private fun key(t: Track, px: Int) = "${t.id}:${t.coverPath ?: ""}:$px"

    suspend fun load(ctx: Context, t: Track, px: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val k = key(t, px)
        cache.get(k)?.let { return@withContext it.asImageBitmap() }
        synchronized(missing) { if (k in missing) return@withContext null }
        val bmp = try { decode(ctx, t, px) } catch (e: Throwable) { null }
        if (bmp == null) {
            synchronized(missing) { missing += k }
            null
        } else {
            cache.put(k, bmp)
            bmp.asImageBitmap()
        }
    }

    fun invalidate(id: Long) {
        synchronized(missing) { missing.removeAll { it.startsWith("$id:") } }
        cache.snapshot().keys.filter { it.startsWith("$id:") }.forEach { cache.remove(it) }
        synchronized(accents) { accents.keys.filter { it.startsWith("$id:") }.forEach { accents.remove(it) } }
    }

    private fun decode(ctx: Context, t: Track, px: Int): Bitmap? {
        t.coverPath?.let { p -> return decodeBytes(File(p).readBytes(), px) }
        if (Build.VERSION.SDK_INT >= 29) {
            return try {
                ctx.contentResolver.loadThumbnail(t.uri, Size(px, px), null)
            } catch (e: Exception) {
                null
            }
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, t.uri)
            r.embeddedPicture?.let { decodeBytes(it, px) }
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    fun decodeBytes(b: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Акцентный цвет из обложки: самый «звонкий» оттенок, подогнанный под тёмный фон. */
    suspend fun accent(ctx: Context, t: Track): Color? = withContext(Dispatchers.Default) {
        val k = "${t.id}:${t.coverPath ?: ""}"
        synchronized(accents) { if (accents.containsKey(k)) return@withContext accents[k] }
        val img = load(ctx, t, 256)
        val c = img?.let { extract(it) }
        synchronized(accents) { accents[k] = c }
        c
    }

    private fun extract(img: ImageBitmap): Color? {
        val w = 40
        var full = img.asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= 26 && full.config == Bitmap.Config.HARDWARE) full = full.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val src = Bitmap.createScaledBitmap(full, w, w, true)
        val buckets = Array(24) { FloatArray(5) } // score, r, g, b, n
        val hsv = FloatArray(3)
        for (y in 0 until w) for (x in 0 until w) {
            val c = src.getPixel(x, y)
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]; val v = hsv[2]
            if (sat < 0.22f || v < 0.18f) continue
            val score = sat * sat * (0.35f + v)
            val bi = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
            val bk = buckets[bi]
            bk[0] += score; bk[1] += r * score; bk[2] += g * score; bk[3] += b * score; bk[4] += 1f
        }
        val best = buckets.maxByOrNull { it[0] } ?: return null
        if (best[0] < 6f || best[4] < 14f) return null
        val r = (best[1] / best[0]).toInt(); val g = (best[2] / best[0]).toInt(); val b = (best[3] / best[0]).toInt()
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        hsv[1] = hsv[1].coerceIn(0.5f, 0.9f)
        hsv[2] = hsv[2].coerceIn(0.88f, 1f)
        // слишком тёмно-синие/фиолетовые плохо читаются на графите — подсветлим
        if (hsv[0] in 215f..275f) hsv[1] = hsv[1].coerceAtMost(0.62f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** Сохраняет выбранную картинку как обложку трека. */
    fun importCover(ctx: Context, id: Long, uri: Uri): String? {
        return try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bmp = decodeBytes(bytes, 1000) ?: return null
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val f = File(dir, "$id-${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

// ======================= Плейлисты .m3u =======================

object M3u {
    fun export(ctx: Context, uri: Uri, tracks: List<Track>): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            val sb = StringBuilder("#EXTM3U\n")
            for (t in tracks) {
                sb.append("#EXTINF:").append(t.durationMs / 1000).append(',').append(t.artist).append(" - ").append(t.title).append('\n')
                sb.append(t.path.ifEmpty { t.uri.toString() }).append('\n')
            }
            os.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) {
        false
    }

    data class Result(val name: String, val ids: List<Long>, val total: Int)

    fun import(ctx: Context, uri: Uri, library: Collection<Track>): Result? {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)?.use { LyricsRepo.decode(it.readBytes()) } ?: return null
            var name = "Импорт"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = (c.getString(0) ?: name).substringBeforeLast('.')
            }
            val byPath = library.associateBy { it.path.lowercase() }
            val byName = library.groupBy { it.path.substringAfterLast('/').lowercase() }
            val byTitle = library.groupBy { Search.key(it.artist + " " + it.title) }
            val ids = ArrayList<Long>()
            var total = 0
            var pendingInfo: String? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#EXTINF", true)) { pendingInfo = line.substringAfter(',', ""); continue }
                if (line.startsWith("#")) continue
                total++
                val path = line.replace('\\', '/').removePrefix("file://")
                val file = path.substringAfterLast('/').lowercase()
                val t = byPath[path.lowercase()]
                    ?: byName[file]?.firstOrNull()
                    ?: pendingInfo?.let { info -> byTitle[Search.key(info.replace(" - ", " "))]?.firstOrNull() }
                if (t != null) ids += t.id
                pendingInfo = null
            }
            Result(name, ids.distinct(), total)
        } catch (e: Exception) {
            null
        }
    }
}

// ======================= Корзина =======================

data class TrashItem(val id: Long, val title: String, val artist: String, val expiresAt: Long, val uri: Uri)

object Trash {
    /** Android 11+: системная корзина с автоудалением через 30 дней. */
    val system: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun trashRequest(ctx: Context, uris: List<Uri>, trashed: Boolean): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createTrashRequest(ctx.contentResolver, uris, trashed) else null

    fun deleteRequest(ctx: Context, uris: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(ctx.contentResolver, uris) else null

    suspend fun list(ctx: Context): List<TrashItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 30) {
            val out = ArrayList<TrashItem>()
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.MediaColumns.DATE_EXPIRES)
            try {
                ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, args, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        out += TrashItem(
                            id, c.getString(1) ?: "Без названия",
                            (c.getString(2) ?: "").let { if (it.isBlank() || it == "<unknown>") UNKNOWN_ARTIST else it },
                            c.getLong(3) * 1000,
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            out
        } else {
            val all = Library.all.value
            Store.legacyTrash.value.mapNotNull { (id, at) ->
                all[id]?.let { TrashItem(id, it.title, it.artist, at + 30L * 24 * 3600 * 1000, it.uri) }
            }
        }
    }

    /** Старые Android: окончательное удаление. */
    fun deleteLegacy(ctx: Context, items: List<TrashItem>): Int {
        var n = 0
        for (it in items) {
            try {
                if (ctx.contentResolver.delete(it.uri, null, null) > 0) n++
                else Library[it.id]?.path?.let { p -> if (File(p).delete()) n++ }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Store.restoreLegacy(items.map { it.id })
        Library.refresh()
        return n
    }
}

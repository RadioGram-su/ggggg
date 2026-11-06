package su.radiogram.grom.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String)

/**
 * Тексты песен: прикреплённый .lrc, .lrc рядом с файлом, или текст внутри тегов
 * (ID3 USLT для MP3, LYRICS в FLAC). Синхронный текст подсвечивается построчно.
 */
object LyricsRepo {
    private val tagRe = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetRe = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metaRe = Regex("""^\[(ar|ti|al|by|length|re|ve|au|#):.*]$""", RegexOption.IGNORE_CASE)

    suspend fun load(ctx: Context, t: Track): Lyrics? = withContext(Dispatchers.IO) {
        try {
            Store.lyricsFiles.value[t.id]?.let { p ->
                val f = File(p)
                if (f.exists()) return@withContext parse(decode(f.readBytes()), "Прикреплённый файл")
            }
            if (t.path.isNotEmpty()) {
                val base = t.path.substringBeforeLast('.')
                for (ext in listOf(".lrc", ".LRC", ".Lrc")) {
                    val f = File(base + ext)
                    if (f.canRead()) return@withContext parse(decode(f.readBytes()), "Файл .lrc рядом")
                }
            }
            embedded(ctx, t.uri)?.let { return@withContext parse(it, "Текст из тегов") }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun parse(text: String, source: String): Lyrics? {
        if (text.isBlank()) return null
        val offset = offsetRe.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        var anyTag = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || metaRe.matches(line) || offsetRe.matches(line)) continue
            val tags = tagRe.findAll(line).toList()
            if (tags.isEmpty()) {
                out += LyricLine(-1, line)
                continue
            }
            anyTag = true
            val lyric = line.substring(tags.last().range.last + 1).trim()
            for (m in tags) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracS = m.groupValues[3]
                val frac = when (fracS.length) {
                    0 -> 0L
                    1 -> fracS.toLong() * 100
                    2 -> fracS.toLong() * 10
                    else -> fracS.take(3).toLong()
                }
                out += LyricLine(maxOf(0L, min * 60000 + sec * 1000 + frac - offset), lyric)
            }
        }
        if (out.isEmpty()) return null
        return if (anyTag) {
            Lyrics(true, out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }, source)
        } else {
            Lyrics(false, out, source)
        }
    }

    /** Байты в текст: UTF-8 / UTF-16 по BOM, иначе Windows-1251, если UTF-8 не сходится. */
    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        return if (isUtf8(b)) String(b, Charsets.UTF_8) else String(b, cp1251())
    }

    private fun cp1251(): Charset = try { Charset.forName("windows-1251") } catch (e: Exception) { Charsets.ISO_8859_1 }

    private fun isUtf8(b: ByteArray): Boolean {
        var i = 0
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            val n = when {
                c < 0x80 -> 0
                c shr 5 == 0x6 -> 1
                c shr 4 == 0xE -> 2
                c shr 3 == 0x1E -> 3
                else -> return false
            }
            for (k in 1..n) {
                if (i + k >= b.size) return false
                if ((b[i + k].toInt() and 0xC0) != 0x80) return false
            }
            i += n + 1
        }
        return true
    }

    // ---------- встроенные теги ----------

    private fun embedded(ctx: Context, uri: Uri): String? {
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            val ins = BufferedInputStream(raw, 64 * 1024)
            val head = ByteArray(10)
            if (!readFully(ins, head)) return null
            if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                return id3(ins, head)
            }
            if (head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() && head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte()) {
                return flac(ins, head)
            }
        }
        return null
    }

    private fun readFully(ins: InputStream, buf: ByteArray, off: Int = 0, len: Int = buf.size - off): Boolean {
        var o = off
        var left = len
        while (left > 0) {
            val r = ins.read(buf, o, left)
            if (r < 0) return false
            o += r; left -= r
        }
        return true
    }

    private fun id3(ins: InputStream, head: ByteArray): String? {
        val ver = head[3].toInt()
        val flags = head[5].toInt() and 0xFF
        val size = syncsafe(head, 6)
        if (size <= 0 || size > 32 * 1024 * 1024) return null
        var tag = ByteArray(size)
        if (!readFully(ins, tag)) return null
        if (ver < 4 && flags and 0x80 != 0) tag = unsync(tag)
        var pos = 0
        if (flags and 0x40 != 0) {
            val ext = if (ver >= 4) syncsafe(tag, 0) else be32(tag, 0) + 4
            pos = ext
        }
        var plain: String? = null
        while (pos + 10 <= tag.size) {
            if (tag[pos].toInt() == 0) break
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            val fsize = if (ver >= 4) syncsafe(tag, pos + 4) else be32(tag, pos + 4)
            val start = pos + 10
            if (fsize <= 0 || start + fsize > tag.size) break
            if (id == "USLT" && fsize > 4) {
                val enc = tag[start].toInt()
                var p = start + 4
                p = skipString(tag, p, start + fsize, enc)
                val txt = decodeId3(tag, p, start + fsize - p, enc).trim()
                if (txt.isNotEmpty()) {
                    if (tagRe.containsMatchIn(txt)) return txt
                    if (plain == null) plain = txt
                }
            } else if (id == "TXXX" && fsize > 1) {
                val enc = tag[start].toInt()
                val descEnd = skipString(tag, start + 1, start + fsize, enc)
                val desc = decodeId3(tag, start + 1, descEnd - (start + 1), enc).trim().trimEnd('\u0000').uppercase()
                if (desc.contains("LYRIC")) {
                    val txt = decodeId3(tag, descEnd, start + fsize - descEnd, enc).trim()
                    if (txt.isNotEmpty()) {
                        if (tagRe.containsMatchIn(txt)) return txt
                        if (plain == null) plain = txt
                    }
                }
            }
            pos = start + fsize
        }
        return plain
    }

    private fun skipString(b: ByteArray, from: Int, end: Int, enc: Int): Int {
        var p = from
        if (enc == 1 || enc == 2) {
            while (p + 1 < end) {
                if (b[p].toInt() == 0 && b[p + 1].toInt() == 0) return p + 2
                p += 2
            }
            return end
        }
        while (p < end) {
            if (b[p].toInt() == 0) return p + 1
            p++
        }
        return end
    }

    private fun decodeId3(b: ByteArray, off: Int, len: Int, enc: Int): String {
        if (len <= 0) return ""
        return when (enc) {
            1 -> {
                if (len >= 2 && b[off] == 0xFF.toByte() && b[off + 1] == 0xFE.toByte()) String(b, off + 2, len - 2, Charsets.UTF_16LE)
                else if (len >= 2 && b[off] == 0xFE.toByte() && b[off + 1] == 0xFF.toByte()) String(b, off + 2, len - 2, Charsets.UTF_16BE)
                else String(b, off, len, Charsets.UTF_16LE)
            }
            2 -> String(b, off, len, Charsets.UTF_16BE)
            3 -> String(b, off, len, Charsets.UTF_8)
            else -> {
                // часто русские теги лежат в cp1251 под видом Latin-1
                val slice = b.copyOfRange(off, off + len)
                val high = slice.count { (it.toInt() and 0xFF) >= 0xC0 }
                if (high > len / 5) String(slice, cp1251()) else String(slice, Charsets.ISO_8859_1)
            }
        }.replace("\u0000", "")
    }

    private fun syncsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun unsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun flac(ins: InputStream, head: ByteArray): String? {
        // head = "fLaC" + первые 6 байт первого блока
        var hdr = head.copyOfRange(4, 8)
        val rest = head.copyOfRange(8, 10)
        var pre: ByteArray? = rest
        var guard = 0
        while (guard++ < 64) {
            val last = hdr[0].toInt() and 0x80 != 0
            val type = hdr[0].toInt() and 0x7F
            val len = ((hdr[1].toInt() and 0xFF) shl 16) or ((hdr[2].toInt() and 0xFF) shl 8) or (hdr[3].toInt() and 0xFF)
            val body = ByteArray(len)
            var off = 0
            pre?.let { p ->
                val n = minOf(p.size, len)
                System.arraycopy(p, 0, body, 0, n)
                off = n
                pre = if (n < p.size) p.copyOfRange(n, p.size) else null
            }
            if (!readFully(ins, body, off, len - off)) return null
            if (type == 4) return vorbisLyrics(body)
            if (last) return null
            hdr = ByteArray(4)
            val leftover = pre
            if (leftover != null) {
                val n = minOf(4, leftover.size)
                System.arraycopy(leftover, 0, hdr, 0, n)
                pre = null
                if (n < 4 && !readFully(ins, hdr, n, 4 - n)) return null
            } else if (!readFully(ins, hdr)) return null
        }
        return null
    }

    private fun vorbisLyrics(b: ByteArray): String? {
        try {
            var p = 0
            val vlen = le32(b, p); p += 4 + vlen
            val count = le32(b, p); p += 4
            var plain: String? = null
            for (i in 0 until count) {
                if (p + 4 > b.size) break
                val l = le32(b, p); p += 4
                if (l < 0 || p + l > b.size) break
                val s = String(b, p, l, Charsets.UTF_8); p += l
                val eq = s.indexOf('=')
                if (eq <= 0) continue
                val k = s.substring(0, eq).uppercase()
                if (k == "LYRICS" || k == "UNSYNCEDLYRICS" || k == "SYNCEDLYRICS") {
                    val v = s.substring(eq + 1)
                    if (tagRe.containsMatchIn(v)) return v
                    if (plain == null) plain = v
                }
            }
            return plain
        } catch (e: Exception) {
            return null
        }
    }
}

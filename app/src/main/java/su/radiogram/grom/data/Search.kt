package su.radiogram.grom.data

/**
 * Поиск, который понимает транслит и неверную раскладку:
 * «kino» найдёт «Кино», «цой» найдёт «Tsoi», «rbyj» — тоже «Кино». Ё = Е.
 */
object Search {
    private val cyr: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "iu",
        'я' to "ia", 'і' to "i", 'ї' to "i", 'є' to "e", 'ґ' to "g", 'ў' to "u",
    )

    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"

    fun key(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s.lowercase()) {
            val m = cyr[ch]
            when {
                m != null -> sb.append(m)
                ch.isLetterOrDigit() -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        var r = sb.toString()
            .replace("shch", "sh").replace("sch", "sh").replace("kh", "h").replace("tz", "c").replace("ts", "c")
            .replace("ph", "f").replace("ck", "k").replace('w', 'v').replace('q', 'k').replace("x", "ks")
            .replace('j', 'i').replace('y', 'i')
        // схлопываем удвоения: «Mellody» ~ «Melody»
        val c = StringBuilder(r.length)
        var prev = ' '
        for (ch in r) {
            if (ch == prev && ch != ' ' && !ch.isDigit()) continue
            c.append(ch); prev = ch
        }
        r = c.toString()
        return r.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun swapLayout(q: String): String {
        val sb = StringBuilder(q.length)
        for (ch in q.lowercase()) {
            val i = EN.indexOf(ch)
            if (i >= 0) { sb.append(RU[i]); continue }
            val j = RU.indexOf(ch)
            if (j >= 0) { sb.append(EN[j]); continue }
            sb.append(ch)
        }
        return sb.toString()
    }

    /** Варианты запроса: как есть и в другой раскладке. */
    fun variants(q: String): List<List<String>> {
        val a = key(q).split(' ').filter { it.isNotEmpty() }
        val b = key(swapLayout(q)).split(' ').filter { it.isNotEmpty() }
        return if (a == b || b.isEmpty()) listOf(a) else listOf(a, b)
    }

    fun filter(tracks: List<Track>, q: String): List<Track> {
        if (q.isBlank()) return tracks
        val vars = variants(q).filter { it.isNotEmpty() }
        if (vars.isEmpty()) return tracks
        val first = vars[0].joinToString(" ")
        val res = tracks.filter { t -> vars.any { toks -> toks.all { t.searchKey.contains(it) } } }
        // сначала совпадения по началу названия
        return res.sortedBy { t ->
            val tk = key(t.title)
            when {
                tk.startsWith(first) -> 0
                t.searchKey.startsWith(first) -> 1
                else -> 2
            }
        }
    }
}

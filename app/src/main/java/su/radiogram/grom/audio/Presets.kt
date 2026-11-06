package su.radiogram.grom.audio

/** Звуковой «картридж»: не просто кривая эквалайзера, а целый характер звука. */
data class Preset(
    val id: String,
    val name: String,
    val tag: String,
    val color: Long,
    val bands: List<Float>,
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val warmth: Float = 0f,
    val comp: Float = 0f,
    val width: Float = 1f,
    val reverb: Float = 0f,
    val room: Float = 0.5f,
    val lowCut: Float = 0f,
    val highCut: Float = 0f,
)

object Presets {
    private fun b(vararg v: Number) = v.map { it.toFloat() }

    val all: List<Preset> = listOf(
        Preset("flat", "ЧИСТЫЙ", "Как записано. Без окраски", 0xFFD9D2C3,
            b(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        Preset("punch", "УДАР", "Бочка бьёт прямо в грудь", 0xFFC2522B,
            b(5, 7, 4, 0, -1, -1, 0, 1, 2, 1), preamp = -2f, bass = 0.55f, warmth = 0.15f, width = 1.05f),
        Preset("sub", "САБ", "Низ, который чувствуешь кожей", 0xFF8E2F2A,
            b(9, 7, 3, -1, -2, -1, 0, 0, 1, 1), preamp = -4f, bass = 0.3f),
        Preset("vocal", "ГОЛОС", "Вокал в шаге от тебя", 0xFFC9973A,
            b(-2, -2, -1, 0, 1.5, 3, 4, 3, 1, 0), preamp = -1.5f, width = 0.9f, comp = 0.2f),
        Preset("club", "КЛУБ", "Танцпол в три часа ночи", 0xFF9A3D6B,
            b(6, 5, 2, -1, -2, 0, 1, 3, 4, 3), preamp = -3f, bass = 0.4f, warmth = 0.2f, width = 1.2f, reverb = 0.10f, room = 0.45f),
        Preset("hall", "ЗАЛ", "Филармония, десятый ряд", 0xFF2F7A74,
            b(1, 2, 1, 0, 0, 0, 1, 2, 2, 1), preamp = -1f, width = 1.35f, reverb = 0.30f, room = 0.85f),
        Preset("tube", "ЛАМПА", "Тёплый ламповый усилитель", 0xFFD88A2E,
            b(1, 2, 3, 2, 1, 0, -1, -1, -2, -3), preamp = -1f, warmth = 0.55f),
        Preset("tape", "КАССЕТА", "Плёнка девяностых: тепло и мягкий верх", 0xFFB79B57,
            b(2, 3, 3, 2, 1, 1, 0, -2, -5, -9), preamp = -1f, warmth = 0.45f, width = 0.85f, highCut = 11000f),
        Preset("crystal", "КРИСТАЛЛ", "Каждая тарелка и каждый вдох", 0xFF9BB8C4,
            b(0, 0, 0, -1, -1, 0, 2, 4, 5, 6), preamp = -2f, width = 1.25f),
        Preset("night", "НОЧЬ", "Громко, но без резких пиков", 0xFF3D4C7A,
            b(-3, -2, 0, 1, 1, 1, 1, 0, -1, -2), comp = 0.8f),
        Preset("rock", "РОК", "Гитары стеной", 0xFFB23A2E,
            b(4, 3, 1, -1, -2, 1, 3, 4, 3, 2), preamp = -2f, warmth = 0.25f, width = 1.15f),
        Preset("rap", "РЭП", "Бит и читка вперёд", 0xFF6E7B3A,
            b(6, 6, 3, 0, -2, 0, 2, 2, 1, 1), preamp = -3f, bass = 0.5f, comp = 0.3f),
        Preset("electro", "ЭЛЕКТРО", "Синтезаторы во всю ширь", 0xFF3164B5,
            b(5, 4, 1, -2, -1, 0, 1, 3, 5, 5), preamp = -3f, width = 1.45f, reverb = 0.08f, room = 0.5f),
        Preset("acoustic", "АКУСТИКА", "Живые струны, дерево, воздух", 0xFF8C6A43,
            b(0, 1, 2, 1, 0, 0, 1, 2, 2, 2), preamp = -1f, reverb = 0.10f, room = 0.35f),
        Preset("radio", "РАДИО", "Старый приёмник на кухне", 0xFF6B4A35,
            b(0, 0, 0, 2, 4, 5, 3, 0, 0, 0), preamp = -3f, warmth = 0.5f, width = 0f, lowCut = 350f, highCut = 3800f),
        Preset("stadium", "СТАДИОН", "Сто тысяч человек вокруг", 0xFF3E8A4F,
            b(3, 3, 1, 0, -1, 0, 1, 2, 3, 2), preamp = -2f, width = 1.6f, reverb = 0.42f, room = 0.95f, comp = 0.3f),
        Preset("speaker", "ДИНАМИК+", "Выжимает максимум из телефона", 0xFFE2601F,
            b(-6, -4, 2, 4, 2, 1, 2, 3, 2, 0), preamp = -1f, bass = 0.9f, comp = 0.5f, lowCut = 150f),
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}

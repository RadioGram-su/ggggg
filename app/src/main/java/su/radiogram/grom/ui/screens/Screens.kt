package su.radiogram.grom.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.radiogram.grom.audio.EQ_LABELS
import su.radiogram.grom.audio.OutputMonitor
import su.radiogram.grom.audio.Presets
import su.radiogram.grom.audio.toParams
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.Track
import su.radiogram.grom.data.Trash
import su.radiogram.grom.data.TrashItem
import su.radiogram.grom.data.UserPreset
import su.radiogram.grom.ui.CollKind
import su.radiogram.grom.ui.LocalApp
import su.radiogram.grom.ui.LocalLaunchers
import su.radiogram.grom.ui.Route
import su.radiogram.grom.ui.comp.Cartridge
import su.radiogram.grom.ui.comp.Cover
import su.radiogram.grom.ui.comp.Empty
import su.radiogram.grom.ui.comp.EqCurve
import su.radiogram.grom.ui.comp.Fader
import su.radiogram.grom.ui.comp.GDialog
import su.radiogram.grom.ui.comp.GIcon
import su.radiogram.grom.ui.comp.GSheet
import su.radiogram.grom.ui.comp.HeartButton
import su.radiogram.grom.ui.comp.IconBtn
import su.radiogram.grom.ui.comp.KeyButton
import su.radiogram.grom.ui.comp.Knob
import su.radiogram.grom.ui.comp.KnobBlock
import su.radiogram.grom.ui.comp.Label
import su.radiogram.grom.ui.comp.ListBottomPad
import su.radiogram.grom.ui.comp.Segmented
import su.radiogram.grom.ui.comp.SheetItem
import su.radiogram.grom.ui.comp.TextPrompt
import su.radiogram.grom.ui.comp.Toaster
import su.radiogram.grom.ui.comp.fmtTime
import su.radiogram.grom.ui.comp.fmtTotal
import su.radiogram.grom.ui.comp.press
import su.radiogram.grom.ui.comp.rowPress
import su.radiogram.grom.ui.comp.tracksWord
import su.radiogram.grom.ui.theme.Ic
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.Unbounded
import su.radiogram.grom.ui.theme.bevel
import su.radiogram.grom.ui.theme.grain
import su.radiogram.grom.ui.theme.rememberHaptics
import kotlin.math.max
import kotlin.math.roundToInt

// ======================= Общие куски =======================

@Composable
fun ScreenHeader(title: String, sub: String? = null, actions: @Composable () -> Unit = {}) {
    val c = LocalGrom.current
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.Back, c.text) { app.pop() }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = T.h1, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Label(sub, color = c.text2)
        }
        actions()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalGrom.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(c.panelHi, c.panel)))
            .bevel(18.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun NavRow(icon: ImageVector, title: String, hint: String? = null, onClick: () -> Unit) {
    val c = LocalGrom.current
    Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).rowPress(onClick), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, c.text2, 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, style = T.title.copy(fontWeight = FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = T.num, color = c.text3)
        Spacer(Modifier.width(6.dp))
        GIcon(Ic.Back, c.text3, 16.dp, Modifier.rotate(180f))
    }
}

// ======================= Звук =======================

@Composable
fun EqScreen() {
    val c = LocalGrom.current
    val h = rememberHaptics()
    val eq by Store.eq.collectAsState()
    val settings by Store.settings.collectAsState()
    val out by OutputMonitor.state.collectAsState()
    val user by Store.userPresets.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UserPreset?>(null) }
    val params = remember(eq, settings, out) { eq.toParams(settings, out) }

    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize().grain(0.9f).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            ScreenHeader("Звук", "Выход: ${out.name} · ${Store.presetName(eq.presetId)}${if (eq.modified) "*" else ""}")
        }
        item(span = { GridItemSpan(2) }) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Итоговая кривая", Modifier.weight(1f))
                    Label("${settings.boost}%", color = c.accent)
                }
                Spacer(Modifier.height(6.dp))
                EqCurve(params, Modifier.fillMaxWidth().height(118.dp))
            }
        }
        item(span = { GridItemSpan(2) }) { Label("Картриджи — тапни, чтобы вставить", Modifier.padding(top = 6.dp)) }
        items(Presets.all, key = { it.id }) { p ->
            Cartridge(p.name, p.tag, Color(p.color), p.bands, eq.presetId == p.id) {
                h.heavy()
                Store.applyPreset(p)
            }
        }
        items(user, key = { it.id }) { u ->
            Cartridge(u.name, "Свой картридж · удерживай, чтобы удалить", c.accent, u.eq.bands, eq.presetId == u.id, onLong = { deleting = u }) {
                h.heavy()
                Store.applyUserPreset(u)
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(top = 10.dp)) {
                Label("Тонкая настройка · двойной тап — в ноль")
                Spacer(Modifier.height(10.dp))
                Panel {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        for (i in 0 until 10) {
                            Fader(eq.bands[i], EQ_LABELS[i], Modifier.weight(1f)) { v ->
                                Store.updateEq { e -> e.copy(bands = e.bands.toMutableList().also { it[i] = v }, modified = true) }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column {
                Label("Характер")
                Spacer(Modifier.height(10.dp))
                Panel {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        val ks = 56.dp
                        KnobBlock("Тепло", "${(eq.warmth * 100).roundToInt()}", eq.warmth, Modifier.weight(1f), knobSize = ks, onDoubleTap = { Store.updateEq { it.copy(warmth = 0f, modified = true) } }) { v ->
                            Store.updateEq { it.copy(warmth = v, modified = true) }
                        }
                        KnobBlock("Бас+", "${(eq.bass * 100).roundToInt()}", eq.bass, Modifier.weight(1f), knobSize = ks, onDoubleTap = { Store.updateEq { it.copy(bass = 0f, modified = true) } }) { v ->
                            Store.updateEq { it.copy(bass = v, modified = true) }
                        }
                        KnobBlock("Ширина", "${(eq.width * 100).roundToInt()}", eq.width / 2f, Modifier.weight(1f), knobSize = ks, hotFrom = 0.5f, onDoubleTap = { Store.updateEq { it.copy(width = 1f, modified = true) } }) { v ->
                            Store.updateEq { it.copy(width = v * 2f, modified = true) }
                        }
                        KnobBlock("Зал", "${(eq.reverb / 0.6f * 100).roundToInt()}", eq.reverb / 0.6f, Modifier.weight(1f), knobSize = ks, onDoubleTap = { Store.updateEq { it.copy(reverb = 0f, modified = true) } }) { v ->
                            Store.updateEq { it.copy(reverb = v * 0.6f, modified = true) }
                        }
                        KnobBlock("Сжатие", "${(eq.comp * 100).roundToInt()}", eq.comp, Modifier.weight(1f), knobSize = ks, onDoubleTap = { Store.updateEq { it.copy(comp = 0f, modified = true) } }) { v ->
                            Store.updateEq { it.copy(comp = v, modified = true) }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Panel {
                SwitchRow("8D-звук", "Музыка медленно кружит вокруг головы. Лучше в наушниках", eq.eightD) { v -> Store.updateEq { it.copy(eightD = v) } }
                SwitchRow("Лимитер", "Держит 200% громко, но без хрипа и треска", settings.limiter) { v -> Store.updateSettings { it.copy(limiter = v) } }
                SwitchRow("Защита динамика", "На динамике телефона срезает гул, который он не тянет: громче и чище", settings.speakerGuard) { v -> Store.updateSettings { it.copy(speakerGuard = v) } }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyButton("Сохранить как свой", Ic.Plus, primary = true, modifier = Modifier.weight(1f)) { saving = true }
                KeyButton("Сбросить", Ic.Restore, modifier = Modifier.weight(1f)) {
                    val p = Presets.byId(eq.presetId)
                    val u = user.firstOrNull { it.id == eq.presetId }
                    when {
                        p != null -> Store.applyPreset(p)
                        u != null -> Store.applyUserPreset(u)
                        else -> Presets.byId("flat")?.let { Store.applyPreset(it) }
                    }
                    Toaster.show("Картридж сброшен")
                }
            }
        }
    }
    if (saving) {
        TextPrompt("Свой картридж", placeholder = "Название, например «МАШИНА»", action = "Сохранить", onDismiss = { saving = false }) { name ->
            Store.saveUserPreset(name)
            saving = false
            Toaster.show("Картридж сохранён")
        }
    }
    deleting?.let { u ->
        GDialog("Удалить «${u.name}»?", { deleting = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyButton("Отмена", modifier = Modifier.weight(1f)) { deleting = null }
                KeyButton("Удалить", primary = true, modifier = Modifier.weight(1f)) {
                    Store.deleteUserPreset(u.id)
                    deleting = null
                }
            }
        }
    }
}

// ======================= Настройки =======================

@Composable
fun SettingsScreen() {
    val c = LocalGrom.current
    val app = LocalApp.current
    val launchers = LocalLaunchers.current
    val s by Store.settings.collectAsState()
    val hidden by Library.hidden.collectAsState()
    val scanning by Library.scanning.collectAsState()
    val all by Library.all.collectAsState()
    LazyColumn(Modifier.fillMaxSize().grain(0.9f).statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp)) {
        item { ScreenHeader("Настройки") }
        item { Section("Вид") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Тема", style = T.title.copy(fontWeight = FontWeight.Medium), color = c.text)
                    Text("AMOLED — чистый чёрный, бережёт батарею", style = T.sub, color = c.text2)
                }
            }
            Segmented(listOf("Графит", "AMOLED"), if (s.amoled) 1 else 0, Modifier.fillMaxWidth()) { i -> Store.updateSettings { it.copy(amoled = i == 1) } }
            Spacer(Modifier.height(6.dp))
            SwitchRow("Цвет из обложки", "Подсветка интерфейса подстраивается под трек", s.coverTint) { v -> Store.updateSettings { it.copy(coverTint = v) } }
            SwitchRow("Тактильная отдача", "Щелчки ручек, клавиш и перемотки", s.haptics) { v -> Store.updateSettings { it.copy(haptics = v) } }
        } }
        item { Section("Звук") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Громкость: ${s.boost}%", style = T.title.copy(fontWeight = FontWeight.Medium), color = c.text)
                    Text("100% — как у телефона, 200% — вдвое громче", style = T.sub, color = c.text2)
                }
                KeyButton("200%", height = 38.dp) { Store.updateSettings { it.copy(boost = 200) } }
            }
            SwitchRow("Лимитер", "Громко без хрипа", s.limiter) { v -> Store.updateSettings { it.copy(limiter = v) } }
            SwitchRow("Защита динамика", "Убирает гул на встроенном динамике", s.speakerGuard) { v -> Store.updateSettings { it.copy(speakerGuard = v) } }
            NavRow(Ic.Sliders, "Картриджи и эквалайзер") { app.push(Route.Eq()) }
        } }
        item { Section("Библиотека") {
            SwitchRow("Фильтр мусора", "Прячет голосовые, рингтоны, записи звонков и диктофона", s.junkFilter) { v -> Store.updateSettings { it.copy(junkFilter = v) } }
            Text("Скрывать файлы короче", style = T.sub, color = c.text2, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            val mins = listOf(0, 15, 30, 60)
            Segmented(listOf("Не скрывать", "15 с", "30 с", "60 с"), mins.indexOf(s.minDurSec).coerceAtLeast(0), Modifier.fillMaxWidth()) { i ->
                Store.updateSettings { it.copy(minDurSec = mins[i]) }
            }
            Spacer(Modifier.height(6.dp))
            NavRow(Ic.Eye, "Скрытые файлы", hidden.size.toString()) { app.push(Route.Hidden) }
            NavRow(Ic.Restore, if (scanning) "Сканирую…" else "Обновить библиотеку", tracksWord(all.size)) { Library.refresh(); Toaster.show("Обновляю библиотеку") }
            if (Build.VERSION.SDK_INT >= 30) {
                val granted = Environment.isExternalStorageManager()
                NavRow(Ic.Folder, "Искать тексты .lrc в папках", if (granted) "вкл" else "выкл") { launchers.allFiles() }
            }
        } }
        item { Section("Плейлисты и корзина") {
            NavRow(Ic.Import, "Импорт плейлиста .m3u") { launchers.importM3u() }
            NavRow(Ic.Trash, "Корзина", "30 дней") { app.push(Route.Trash) }
        } }
        item { Section("О плеере") {
            Text("ГРОМ", style = T.wordmark, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Версия ${su.radiogram.grom.BuildConfig.VERSION_NAME}. Без рекламы, без интернета, без слежки — всё хранится только на этом телефоне.",
                style = T.sub, color = c.text2,
            )
            Spacer(Modifier.height(6.dp))
        } }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Label(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Panel(content = content)
    }
}

// ======================= Корзина =======================

@Composable
fun TrashScreen() {
    val c = LocalGrom.current
    val ctx = LocalContext.current
    val launchers = LocalLaunchers.current
    val all by Library.all.collectAsState()
    val legacy by Store.legacyTrash.collectAsState()
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var reload by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(all.size, legacy, reload) { items = Trash.list(ctx) }
    val list = items
    Column(Modifier.fillMaxSize().grain(0.9f).statusBarsPadding()) {
        ScreenHeader("Корзина", if (list != null) tracksWord(list.size) else null)
        if (list == null) {
            Label("Загружаю…", Modifier.padding(20.dp))
        } else if (list.isEmpty()) {
            Empty("Корзина пуста", "Удалённые треки лежат здесь 30 дней — их можно вернуть одним нажатием.")
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyButton("Вернуть всё", Ic.Restore, primary = true, modifier = Modifier.weight(1f)) { launchers.restore(list) }
                KeyButton("Очистить", Ic.Trash, modifier = Modifier.weight(1f)) { launchers.deleteForever(list) { reload += 1f } }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottomPad) {
                items(list, key = { it.id }) { t ->
                    val days = max(0L, (t.expiresAt - System.currentTimeMillis()) / (24L * 3600 * 1000))
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.artist} · удалится через $days дн.", style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconBtn(Ic.Restore, c.accent, 20.dp) { launchers.restore(listOf(t)) }
                        IconBtn(Ic.Trash, c.text3, 20.dp) { launchers.deleteForever(listOf(t)) { reload += 1f } }
                    }
                }
            }
        }
    }
}

// ======================= Скрытые =======================

@Composable
fun HiddenScreen() {
    val c = LocalGrom.current
    val hidden by Library.hidden.collectAsState()
    Column(Modifier.fillMaxSize().grain(0.9f).statusBarsPadding()) {
        ScreenHeader("Скрытые файлы", "${hidden.size} · фильтр мусора")
        if (hidden.isEmpty()) {
            Empty("Ничего не скрыто", "Голосовые, рингтоны и записи звонков попадут сюда, а не в музыку.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottomPad) {
                items(hidden, key = { it.first.id }) { (t, why) ->
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(t, 44.dp, 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$why · ${fmtTime(t.durationMs)} · ${t.folder}", style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        KeyButton("Показать", height = 36.dp) { Store.allowJunk(t.id); Toaster.show("Теперь в библиотеке") }
                    }
                }
            }
        }
    }
}

// ======================= За рулём =======================

@Composable
fun DrivingScreen(track: Track?) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val playing by app.conn.isPlaying.collectAsState()
    val pos by app.conn.position.collectAsState()
    val dur by app.conn.duration.collectAsState()
    val favs by Store.favorites.collectAsState()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Column(Modifier.fillMaxSize().background(c.bg).grain(1f).statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Close, c.text, 26.dp, 56.dp) { app.pop() }
            Label("Режим «за рулём»", Modifier.weight(1f).padding(start = 6.dp), color = c.text2)
            if (track != null) HeartButton(favs.contains(track.id), iconSize = 30.dp, box = 60.dp) { Store.toggleFavorite(track.id) }
        }
        Column(Modifier.weight(0.9f).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(track?.title ?: "Тишина", style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(track?.artist ?: "", style = T.body.copy(fontSize = 20.sp), color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.panelHi)) {
                Box(Modifier.fillMaxWidth(f).height(6.dp).background(c.accent))
            }
        }
        BigKey(if (playing) Ic.Pause else Ic.Play, primary = true, modifier = Modifier.weight(1.15f).fillMaxWidth()) { app.conn.toggle() }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigKey(Ic.Prev, modifier = Modifier.weight(1f).fillMaxSize()) { app.conn.prev() }
            BigKey(Ic.Next, modifier = Modifier.weight(1f).fillMaxSize()) { app.conn.next() }
        }
    }
}

@Composable
private fun BigKey(icon: ImageVector, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalGrom.current
    val h = rememberHaptics()
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (primary) Brush.verticalGradient(listOf(c.accent, su.radiogram.grom.ui.comp.mixDark(c.accent)))
                else Brush.verticalGradient(listOf(c.raised, c.panel))
            )
            .bevel(28.dp, if (primary) 1.5f else 1f)
            .press({ h.heavy(); onClick() }, scaleTo = 0.97f, haptic = false),
        contentAlignment = Alignment.Center,
    ) { GIcon(icon, if (primary) c.ink else c.text, 72.dp) }
}

// ======================= Подборка / плейлист / альбом / артист =======================

@Composable
fun CollectionScreen(r: Route.Coll) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val launchers = LocalLaunchers.current
    val tracks by Library.tracks.collectAsState()
    val stats by Store.stats.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val all by Library.all.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val pl = playlists.firstOrNull { it.id == r.id }
    val list = remember(r, tracks, stats, pl, all) {
        when (r.kind) {
            CollKind.NEW, CollKind.TOP, CollKind.FORGOT, CollKind.NEVER -> Smart.list(r.kind, tracks, stats)
            CollKind.PLAYLIST -> pl?.ids?.mapNotNull { all[it] } ?: emptyList()
            CollKind.ALBUM -> tracks.filter { "${it.albumId}|${it.album}" == r.id }.sortedBy { it.trackNo }
            CollKind.ARTIST -> tracks.filter { it.artist == r.id }.sortedWith(compareBy({ it.album }, { it.trackNo }))
        }
    }
    val title = pl?.name ?: r.title
    val sub = when (r.kind) {
        CollKind.NEW -> "Добавлено за 2 недели"
        CollKind.TOP -> "Слушал два раза и больше"
        CollKind.FORGOT -> "Не включал больше месяца"
        CollKind.NEVER -> "Ещё ни разу не дослушал"
        CollKind.PLAYLIST -> "Плейлист"
        CollKind.ALBUM -> list.firstOrNull()?.let { t -> t.artist + if (t.year > 0) " · ${t.year}" else "" } ?: "Альбом"
        CollKind.ARTIST -> "Артист"
    }
    Column(Modifier.fillMaxSize().grain(0.9f).statusBarsPadding()) {
        TrackList(
            list,
            playlistId = if (r.kind == CollKind.PLAYLIST) r.id else null,
            showNew = r.kind != CollKind.NEW,
            header = {
                item(key = "hdr") {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBtn(Ic.Back, c.text) { app.pop() }
                            Spacer(Modifier.weight(1f))
                            if (r.kind == CollKind.PLAYLIST) IconBtn(Ic.More, c.text) { menu = true }
                        }
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (r.kind == CollKind.ALBUM) Cover(list.firstOrNull(), 120.dp, 12.dp, px = 400) else Mosaic(list.take(4), 120.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Label(sub, color = c.accent)
                                Spacer(Modifier.height(6.dp))
                                Text(title, style = T.h1, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                Text("${tracksWord(list.size)} · ${fmtTotal(list.sumOf { it.durationMs })}", style = T.sub, color = c.text2)
                            }
                        }
                        if (list.isNotEmpty()) PlayShuffleBar(list, "")
                    }
                }
            },
            empty = {
                Empty(
                    if (r.kind == CollKind.PLAYLIST) "Плейлист пуст" else "Пока пусто",
                    if (r.kind == CollKind.PLAYLIST) "Добавляй треки через меню ⋮ → «В плейлист»." else "Подборка соберётся сама, когда будет что показать.",
                )
            },
        )
    }
    if (menu && pl != null) {
        GSheet({ menu = false }) {
            Label(pl.name, Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            SheetItem(Ic.Edit, "Переименовать") { menu = false; renaming = true }
            SheetItem(Ic.Export, "Экспорт в .m3u") { menu = false; launchers.exportM3u(pl) }
            SheetItem(Ic.Trash, "Удалить плейлист", tint = c.text2, hint = "треки останутся") {
                menu = false
                Store.deletePlaylist(pl.id)
                app.pop()
                Toaster.show("Плейлист удалён")
            }
        }
    }
    if (renaming && pl != null) {
        TextPrompt("Переименовать", initial = pl.name, placeholder = "Название", action = "Готово", onDismiss = { renaming = false }) { n ->
            Store.renamePlaylist(pl.id, n)
            renaming = false
        }
    }
}

// ======================= Доступ =======================

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    val c = LocalGrom.current
    var v by remember { mutableFloatStateOf(1f) }
    Column(
        Modifier.fillMaxSize().grain(1f).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Knob(v, { v = it }, Modifier.size(190.dp), hotFrom = 0.5f, minLabel = "0", maxLabel = "MAX")
        Spacer(Modifier.height(10.dp))
        Text("${(v * 200).roundToInt()}%", style = T.counter.copy(fontSize = 15.sp), color = if (v > 0.5f) c.accent else c.text2)
        Spacer(Modifier.height(26.dp))
        Text("ГРОМ", style = T.display.copy(fontSize = 56.sp, letterSpacing = (-2).sp), color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Плеер, который выжимает из телефона всё: до 200% громкости, 17 звуковых картриджей, 8D и тексты песен. Без рекламы и интернета.",
            style = T.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.text2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        KeyButton("Дать доступ к музыке", primary = true, modifier = Modifier.fillMaxWidth(), height = 56.dp) { onGrant() }
        Spacer(Modifier.height(12.dp))
        Text("Нужно, чтобы найти песни на телефоне. Файлы никуда не уходят.", style = T.sub, color = c.text3, textAlign = TextAlign.Center)
    }
}

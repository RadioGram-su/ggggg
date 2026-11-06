package su.radiogram.grom.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import su.radiogram.grom.audio.AudioEngine
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Search
import su.radiogram.grom.data.Stat
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.Track
import su.radiogram.grom.ui.ActionCtx
import su.radiogram.grom.ui.CollKind
import su.radiogram.grom.ui.LocalApp
import su.radiogram.grom.ui.LocalLaunchers
import su.radiogram.grom.ui.Route
import su.radiogram.grom.ui.comp.Cover
import su.radiogram.grom.ui.comp.Empty
import su.radiogram.grom.ui.comp.GField
import su.radiogram.grom.ui.comp.GIcon
import su.radiogram.grom.ui.comp.IconBtn
import su.radiogram.grom.ui.comp.KeyButton
import su.radiogram.grom.ui.comp.Label
import su.radiogram.grom.ui.comp.ListBottomPad
import su.radiogram.grom.ui.comp.NewTag
import su.radiogram.grom.ui.comp.TextPrompt
import su.radiogram.grom.ui.comp.Toaster
import su.radiogram.grom.ui.comp.TrackRow
import su.radiogram.grom.ui.comp.fmtTotal
import su.radiogram.grom.ui.comp.press
import su.radiogram.grom.ui.comp.rowPress
import su.radiogram.grom.ui.comp.tracksWord
import su.radiogram.grom.ui.theme.Ic
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.bevel
import su.radiogram.grom.ui.theme.grain
import kotlin.math.abs
import kotlin.math.log10

// ======================= Умные подборки =======================

object Smart {
    private const val DAY = 24L * 3600 * 1000

    fun list(kind: CollKind, tracks: List<Track>, stats: Map<Long, Stat>): List<Track> {
        val now = System.currentTimeMillis()
        return when (kind) {
            CollKind.NEW -> tracks.filter { it.dateAdded * 1000 > now - 14 * DAY }
            CollKind.TOP -> tracks.filter { (stats[it.id]?.plays ?: 0) >= 2 }.sortedByDescending { stats[it.id]?.plays ?: 0 }.take(100)
            CollKind.FORGOT -> tracks.filter { val s = stats[it.id]; s != null && s.plays > 0 && s.last < now - 30 * DAY }.sortedBy { stats[it.id]?.last ?: 0 }
            CollKind.NEVER -> tracks.filter { (stats[it.id]?.plays ?: 0) == 0 }
            else -> emptyList()
        }
    }

    fun title(kind: CollKind) = when (kind) {
        CollKind.NEW -> "Новое"
        CollKind.TOP -> "Часто слушаю"
        CollKind.FORGOT -> "Давно не включал"
        CollKind.NEVER -> "Ни разу не играл"
        else -> ""
    }
}

fun sortTracks(list: List<Track>, mode: Int): List<Track> = when (mode) {
    1 -> list.sortedBy { Search.key(it.title) }
    2 -> list.sortedWith(compareBy({ Search.key(it.artist) }, { it.album }, { it.trackNo }))
    else -> list
}

private val SORT_NAMES = listOf("НОВЫЕ", "А–Я", "АРТИСТ")

// ======================= Главный экран =======================

@Composable
fun HomeScreen() {
    val c = LocalGrom.current
    val app = LocalApp.current
    val tracks by Library.tracks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val pager = rememberPagerState { 5 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(app.requestTab) {
        app.requestTab?.let { pager.scrollToPage(it); app.requestTab = null }
    }
    LaunchedEffect(app.requestQuery) {
        app.requestQuery?.let { query = it; app.requestQuery = null }
    }

    Column(Modifier.fillMaxSize().grain(0.9f).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ГРОМ", style = T.wordmark, color = c.text)
                    Spacer(Modifier.width(10.dp))
                    MiniLevel()
                }
                val total = remember(tracks) { tracks.sumOf { it.durationMs } }
                Label("${tracksWord(tracks.size)} · ${fmtTotal(total)}", color = c.text3)
            }
            IconBtn(Ic.Car, c.text2) { app.push(Route.Driving) }
            IconBtn(Ic.Sliders, c.text2) { app.push(Route.Eq()) }
            IconBtn(Ic.Gear, c.text2) { app.push(Route.Settings) }
        }
        Spacer(Modifier.height(14.dp))
        GField(
            query, { query = it }, "Название, артист — можно «kino»",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            leading = Ic.Search,
            trailing = { if (query.isNotEmpty()) IconBtn(Ic.Close, c.text2, 18.dp, 36.dp) { query = "" } },
        )
        if (query.isNotBlank()) {
            SearchResults(query, tracks)
        } else {
            Spacer(Modifier.height(8.dp))
            TabRail(listOf("Треки", "Избранное", "Списки", "Альбомы", "Артисты"), pager.currentPage + pager.currentPageOffsetFraction) {
                scope.launch { pager.animateScrollToPage(it) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> TracksTab(tracks)
                    1 -> FavTab()
                    2 -> ListsTab(tracks)
                    3 -> AlbumsTab(tracks)
                    else -> ArtistsTab(tracks)
                }
            }
        }
    }
}

/** Мини-индикатор уровня рядом с логотипом — живой, когда играет музыка. */
@Composable
fun MiniLevel() {
    val c = LocalGrom.current
    val playing by LocalApp.current.conn.isPlaying.collectAsState()
    var frame by remember { mutableLongStateOf(0L) }
    val lv = remember { FloatArray(2) }
    val shown = remember { FloatArray(2) }
    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos { now ->
                var l = 0f
                var r = 0f
                if (playing && AudioEngine.meter.at(AudioEngine.playPositionUs, lv)) {
                    l = ((20 * log10(lv[0] + 1e-6f) + 30f) / 30f).coerceIn(0f, 1f)
                    r = ((20 * log10(lv[1] + 1e-6f) + 30f) / 30f).coerceIn(0f, 1f)
                }
                shown[0] = if (l > shown[0]) l else shown[0] * 0.9f
                shown[1] = if (r > shown[1]) r else shown[1] * 0.9f
                frame = now
            }
            if (!playing && shown[0] < 0.01f && shown[1] < 0.01f) break
        }
    }
    Canvas(Modifier.size(28.dp, 16.dp)) {
        frame
        val cols = 7
        val w = size.width / (cols * 1.6f)
        for (row in 0..1) {
            val v = shown[row]
            for (i in 0 until cols) {
                val on = v * cols > i + 0.3f
                val col = when {
                    !on -> c.text3.copy(alpha = 0.35f)
                    i >= cols - 1 -> Color(0xFFFF4A26)
                    else -> c.accent
                }
                val x = i * w * 1.6f
                val y = row * size.height * 0.55f
                if (on) drawRoundRect(col.copy(alpha = 0.3f), topLeft = Offset(x - w * 0.4f, y - 1f), size = Size(w * 1.8f, size.height * 0.45f + 2f), cornerRadius = CornerRadius(w))
                drawRoundRect(col, topLeft = Offset(x, y), size = Size(w, size.height * 0.45f), cornerRadius = CornerRadius(w / 2))
            }
        }
    }
}

@Composable
fun TabRail(tabs: List<String>, pos: Float, onTab: (Int) -> Unit) {
    val c = LocalGrom.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val k = (1f - abs(pos - i)).coerceIn(0f, 1f)
            Column(
                Modifier.press({ onTab(i) }, scaleTo = 0.95f).padding(horizontal = 9.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t.uppercase(),
                    style = T.label.copy(fontSize = 10.5.sp),
                    color = androidx.compose.ui.graphics.lerp(c.text3, c.text, k),
                )
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.size(18.dp, 4.dp)) {
                    if (k > 0.02f) {
                        drawRoundRect(c.accent.copy(alpha = 0.35f * k), topLeft = Offset(-2f, -2f), size = Size(size.width + 4f, size.height + 4f), cornerRadius = CornerRadius(4f))
                        drawRoundRect(c.accent.copy(alpha = k), cornerRadius = CornerRadius(2f))
                    }
                }
            }
        }
    }
}

// ======================= Вкладки =======================

@Composable
fun PlayShuffleBar(list: List<Track>, info: String, sortMode: Int? = null, onSort: (() -> Unit)? = null) {
    val c = LocalGrom.current
    val app = LocalApp.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton("Играть", Ic.Play, primary = true, modifier = Modifier.weight(1f)) { app.conn.playList(list) }
            KeyButton("Вперемешку", Ic.Shuffle, modifier = Modifier.weight(1f)) { app.conn.playList(list, shuffled = true) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(info, Modifier.weight(1f))
            if (sortMode != null && onSort != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).press(onSort, scaleTo = 0.94f).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("Порядок:", color = c.text3)
                    Spacer(Modifier.width(6.dp))
                    Label(SORT_NAMES[sortMode], color = c.accent)
                }
            }
        }
    }
}

@Composable
fun TrackList(
    list: List<Track>,
    modifier: Modifier = Modifier,
    playlistId: String? = null,
    showNew: Boolean = true,
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    empty: (@Composable () -> Unit)? = null,
) {
    val app = LocalApp.current
    val current by app.conn.currentId.collectAsState()
    val playing by app.conn.isPlaying.collectAsState()
    val favs by Store.favorites.collectAsState()
    val favSet = remember(favs) { favs.toHashSet() }
    LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = ListBottomPad) {
        header?.invoke(this)
        if (list.isEmpty() && empty != null) item { empty() }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            TrackRow(
                t, current = t.id == current, playing = playing, fav = t.id in favSet,
                onClick = { app.conn.playList(list, i) },
                onFav = { Store.toggleFavorite(t.id) },
                onMore = { app.actions = ActionCtx(t.id, playlistId) },
                showNew = showNew,
                modifier = Modifier.animateItem(fadeInSpec = tween(220), placementSpec = androidx.compose.animation.core.spring(stiffness = 600f), fadeOutSpec = tween(160)),
            )
        }
    }
}

@Composable
private fun TracksTab(tracks: List<Track>) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val settings by Store.settings.collectAsState()
    val loaded by Library.loaded.collectAsState()
    val sorted = remember(tracks, settings.sort) { sortTracks(tracks, settings.sort) }
    val fresh = remember(tracks) { tracks.filter { it.dateAdded > app.seenAtStart && app.seenAtStart > 0 }.take(40) }
    TrackList(
        sorted,
        header = {
            if (fresh.isNotEmpty()) item(key = "fresh") { FreshStrip(fresh) }
            item(key = "bar") {
                PlayShuffleBar(sorted, "${tracksWord(tracks.size)}", settings.sort) {
                    Store.updateSettings { it.copy(sort = (it.sort + 1) % 3) }
                }
            }
        },
        empty = {
            if (!loaded) Empty("Сканирую…", "Ищу музыку на телефоне")
            else Empty("Музыки пока нет", "Скачай треки в телефон — они сами появятся здесь, сверху списка.")
        },
    )
}

@Composable
private fun FreshStrip(fresh: List<Track>) {
    val c = LocalGrom.current
    val app = LocalApp.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(7.dp)) {
                drawCircle(c.accent.copy(alpha = 0.35f), radius = size.minDimension)
                drawCircle(c.accent)
            }
            Spacer(Modifier.width(8.dp))
            Label("Новое с прошлого раза · ${fresh.size}", color = c.text2)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(fresh, key = { _, t -> "f" + t.id }) { i, t ->
                Column(Modifier.width(118.dp).press({ app.conn.playList(fresh, i) }, onLong = { app.actions = ActionCtx(t.id) }, scaleTo = 0.96f)) {
                    Box {
                        Cover(t, 118.dp, 10.dp, px = 300)
                        Box(Modifier.padding(6.dp)) { NewTag() }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(t.title, style = T.sub.copy(fontSize = 13.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = T.sub.copy(fontSize = 11.5.sp), color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FavTab() {
    val favs by Store.favorites.collectAsState()
    val all by Library.all.collectAsState()
    val list = remember(favs, all) { favs.mapNotNull { all[it] } }
    TrackList(
        list,
        showNew = false,
        header = { if (list.isNotEmpty()) item(key = "bar") { PlayShuffleBar(list, "${tracksWord(list.size)} · ${fmtTotal(list.sumOf { it.durationMs })}") } },
        empty = { Empty("Здесь будет любимое", "Жми ♥ на любом треке, в плеере или прямо в шторке уведомлений.") },
    )
}

@Composable
private fun ListsTab(tracks: List<Track>) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val launchers = LocalLaunchers.current
    val stats by Store.stats.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val all by Library.all.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val smart = remember(tracks, stats) {
        listOf(CollKind.NEW, CollKind.TOP, CollKind.FORGOT, CollKind.NEVER).map { it to Smart.list(it, tracks, stats).size }
    }
    val tileColors = listOf(c.accent, Color(0xFFE0A33A), Color(0xFF3F9C92), Color(0xFFD9D2C3))
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(smart.size) { i ->
            val (kind, n) = smart[i]
            SmartTile(Smart.title(kind), n, tileColors[i]) { app.push(Route.Coll(kind, kind.name, Smart.title(kind))) }
        }
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(top = 12.dp)) {
                Label("Мои плейлисты")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyButton("Создать", Ic.Plus, primary = true, modifier = Modifier.weight(1f)) { creating = true }
                    KeyButton("Импорт .m3u", Ic.Import, modifier = Modifier.weight(1f)) { launchers.importM3u() }
                }
            }
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text("Плейлистов пока нет. Создай свой или добавь трек через меню ⋮ → «В плейлист».", style = T.body, color = c.text2, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        items(playlists, key = { it.id }, span = { GridItemSpan(2) }) { p ->
            val list = p.ids.mapNotNull { all[it] }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).rowPress({ app.push(Route.Coll(CollKind.PLAYLIST, p.id, p.name)) }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mosaic(list.take(4), 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text("${tracksWord(list.size)} · ${fmtTotal(list.sumOf { it.durationMs })}", style = T.sub, color = c.text2)
                }
                GIcon(Ic.Back, c.text3, 18.dp, Modifier.rotate(180f))
            }
        }
    }
    if (creating) {
        TextPrompt("Новый плейлист", placeholder = "Название", action = "Создать", onDismiss = { creating = false }) { name ->
            val p = Store.createPlaylist(name)
            creating = false
            Toaster.show("Плейлист «${p.name}» создан")
        }
    }
}

@Composable
fun Mosaic(list: List<Track>, size: androidx.compose.ui.unit.Dp) {
    val c = LocalGrom.current
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(c.panelHi)) {
        if (list.size < 4) {
            Cover(list.firstOrNull(), size, 8.dp)
        } else {
            val h = size / 2
            Column {
                Row { Cover(list[0], h, 0.dp, px = 100); Cover(list[1], h, 0.dp, px = 100) }
                Row { Cover(list[2], h, 0.dp, px = 100); Cover(list[3], h, 0.dp, px = 100) }
            }
        }
    }
}

@Composable
private fun SmartTile(title: String, count: Int, color: Color, onClick: () -> Unit) {
    val c = LocalGrom.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(c.raised, c.panel)))
            .bevel(14.dp)
            .press(onClick, scaleTo = 0.96f)
            .drawBehind {
                // цветная «наклейка» сбоку
                drawRoundRect(color, topLeft = Offset(0f, 14.dp.toPx()), size = Size(3.dp.toPx(), size.height - 28.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
            }
            .padding(14.dp)
    ) {
        Column {
            Text(count.toString(), style = T.display.copy(fontSize = 30.sp), color = c.text)
            Spacer(Modifier.weight(1f))
            Text(title.uppercase(), style = T.label, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Canvas(Modifier.align(Alignment.TopEnd).size(8.dp)) {
            drawCircle(color.copy(alpha = 0.35f), radius = size.minDimension)
            drawCircle(color, radius = size.minDimension / 2.4f)
        }
    }
}

private data class Album(val key: String, val title: String, val artist: String, val tracks: List<Track>)

@Composable
private fun AlbumsTab(tracks: List<Track>) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val albums = remember(tracks) {
        tracks.groupBy { "${it.albumId}|${it.album}" }.map { (k, l) ->
            val artists = l.map { it.artist }.distinct()
            Album(k, l.first().album, if (artists.size == 1) artists[0] else "Разные артисты", l)
        }.sortedBy { Search.key(it.title) }
    }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.key }) { a ->
            Column(Modifier.press({ app.push(Route.Coll(CollKind.ALBUM, a.key, a.title)) }, scaleTo = 0.96f)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        Cover(a.tracks.first(), maxWidth, 10.dp, px = 360)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(a.title, style = T.title.copy(fontSize = 14.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.artist} · ${a.tracks.size}", style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsTab(tracks: List<Track>) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val artists = remember(tracks) {
        tracks.groupBy { it.artist }.map { (a, l) -> a to l }.sortedBy { Search.key(it.first) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottomPad) {
        items(artists, key = { it.first }) { (name, l) ->
            Row(
                Modifier.fillMaxWidth().height(64.dp).rowPress({ app.push(Route.Coll(CollKind.ARTIST, name, name)) }).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(c.panelHi).drawBehind {
                        drawCircle(c.accent.copy(alpha = 0.5f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", style = T.h2, color = c.text)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tracksWord(l.size), style = T.sub, color = c.text2)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, tracks: List<Track>) {
    val res = remember(query, tracks) { Search.filter(tracks, query) }
    TrackList(
        res,
        header = { item(key = "lbl") { Label("Найдено: ${res.size}", Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)) } },
        empty = { Empty("Ничего не нашлось", "Попробуй часть названия или имя артиста — латиницей тоже можно.") },
    )
}

// ======================= Мини-плеер =======================

@Composable
fun MiniPlayer(track: Track?, ext: String?, onOpen: () -> Unit) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val playing by app.conn.isPlaying.collectAsState()
    val pos by app.conn.position.collectAsState()
    val dur by app.conn.duration.collectAsState()
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    var dragX by remember { mutableStateOf(0f) }
    val shift by animateFloatAsState(dragX, label = "mini")
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(68.dp)
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 6.dp.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(18.dp.toPx()))
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(c.raised, c.panel)))
            .bevel(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -120f) app.conn.next() else if (dragX > 120f) app.conn.prev()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { ch, dx -> ch.consume(); dragX += dx },
                )
            }
            .rowPress(onOpen)
            .drawBehind {
                val y = size.height - 2.dp.toPx()
                drawRect(c.accent.copy(alpha = 0.25f), topLeft = Offset(0f, y - 2.dp.toPx()), size = Size(size.width * frac, 4.dp.toPx()))
                drawRect(c.accent, topLeft = Offset(0f, y), size = Size(size.width * frac, 2.dp.toPx()))
            }
    ) {
        Row(Modifier.fillMaxSize().padding(start = 9.dp, end = 6.dp).offset(x = (shift / 6).dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track, 48.dp, 9.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: ext ?: "", style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(track?.artist ?: "Файл", style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A3934), Color(0xFF1E1E1B))))
                    .drawBehind {
                        drawCircle(c.accent.copy(alpha = if (playing) 0.9f else 0.35f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    }
                    .press({ app.conn.toggle() }, scaleTo = 0.88f),
                contentAlignment = Alignment.Center,
            ) { GIcon(if (playing) Ic.Pause else Ic.Play, c.text, 20.dp) }
            Box(Modifier.size(44.dp).press({ app.conn.next() }, scaleTo = 0.86f), contentAlignment = Alignment.Center) {
                GIcon(Ic.Next, c.text2, 20.dp)
            }
        }
    }
}

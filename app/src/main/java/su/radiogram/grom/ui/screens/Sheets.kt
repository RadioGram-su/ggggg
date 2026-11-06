package su.radiogram.grom.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.radiogram.grom.audio.SleepTimer
import su.radiogram.grom.data.ArtLoader
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.TagOverride
import su.radiogram.grom.ui.ActionCtx
import su.radiogram.grom.ui.LocalApp
import su.radiogram.grom.ui.LocalLaunchers
import su.radiogram.grom.ui.comp.Cover
import su.radiogram.grom.ui.comp.GDialog
import su.radiogram.grom.ui.comp.GField
import su.radiogram.grom.ui.comp.GIcon
import su.radiogram.grom.ui.comp.GSheet
import su.radiogram.grom.ui.comp.IconBtn
import su.radiogram.grom.ui.comp.KeyButton
import su.radiogram.grom.ui.comp.Label
import su.radiogram.grom.ui.comp.LedSwitch
import su.radiogram.grom.ui.comp.PlayingBars
import su.radiogram.grom.ui.comp.SheetItem
import su.radiogram.grom.ui.comp.TextPrompt
import su.radiogram.grom.ui.comp.Toaster
import su.radiogram.grom.ui.comp.fmtTime
import su.radiogram.grom.ui.comp.press
import su.radiogram.grom.ui.comp.rowPress
import su.radiogram.grom.ui.comp.tracksWord
import su.radiogram.grom.ui.theme.Ic
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.bevel
import java.io.File

// ======================= Действия с треком =======================

@Composable
fun ActionsSheet(a: ActionCtx, onDismiss: () -> Unit) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val launchers = LocalLaunchers.current
    val all by Library.all.collectAsState()
    val favs by Store.favorites.collectAsState()
    val t = all[a.trackId] ?: run { onDismiss(); return }
    val fav = favs.contains(t.id)
    GSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t, 56.dp, 8.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = T.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${t.artist} · ${fmtTime(t.durationMs)}", style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        SheetItem(Ic.NextUp, "Играть следующим") { app.conn.playNext(t); onDismiss() }
        SheetItem(Ic.Queue, "Добавить в очередь") { app.conn.addToQueue(t); onDismiss() }
        SheetItem(Ic.AddList, "В плейлист…") { onDismiss(); app.addToPlaylist = listOf(t.id) }
        SheetItem(if (fav) Ic.HeartFill else Ic.Heart, if (fav) "Убрать из избранного" else "В избранное", tint = if (fav) c.accent else null) {
            Store.toggleFavorite(t.id); onDismiss()
        }
        SheetItem(Ic.Edit, "Изменить название и обложку") { onDismiss(); app.editTags = t.id }
        SheetItem(Ic.Lyrics, "Прикрепить текст (.lrc)") { onDismiss(); launchers.pickLrc(t.id) }
        if (a.playlistId != null) {
            SheetItem(Ic.Close, "Убрать из плейлиста") { Store.removeFromPlaylist(a.playlistId, t.id); onDismiss() }
        }
        SheetItem(Ic.Trash, "В корзину", tint = c.text2, hint = "30 дней на возврат") { onDismiss(); launchers.trash(listOf(t)) }
    }
}

@Composable
fun AddToPlaylistSheet(ids: List<Long>, onDismiss: () -> Unit) {
    val c = LocalGrom.current
    val playlists by Store.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    GSheet(onDismiss) {
        Label("Добавить в плейлист", Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        SheetItem(Ic.Plus, "Новый плейлист", tint = c.accent) { creating = true }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                val has = ids.all { it in p.ids }
                SheetItem(if (has) Ic.Check else Ic.AddList, p.name, tint = if (has) c.accent else null, hint = tracksWord(p.ids.size)) {
                    Store.addToPlaylist(p.id, ids)
                    Toaster.show("Добавлено в «${p.name}»")
                    onDismiss()
                }
            }
        }
    }
    if (creating) {
        TextPrompt("Новый плейлист", placeholder = "Название", action = "Создать", onDismiss = { creating = false }) { name ->
            val p = Store.createPlaylist(name, ids)
            Toaster.show("Создан «${p.name}»")
            creating = false
            onDismiss()
        }
    }
}

// ======================= Редактор тегов =======================

@Composable
fun TagEditor(id: Long, onDismiss: () -> Unit) {
    val c = LocalGrom.current
    val ctx = LocalContext.current
    val launchers = LocalLaunchers.current
    val t = Library[id] ?: run { onDismiss(); return }
    val ovr = Store.overrides.value[id]
    var title by remember { mutableStateOf(t.title) }
    var artist by remember { mutableStateOf(t.artist) }
    var album by remember { mutableStateOf(t.album) }
    var cover by remember { mutableStateOf(ovr?.cover) }
    val preview by produceState<ImageBitmap?>(null, cover) {
        value = cover?.let { p -> withContext(Dispatchers.IO) { ArtLoader.decodeBytes(File(p).readBytes(), 300)?.asImageBitmap() } }
    }
    GDialog("Название и обложка", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).press({
                launchers.pickCover { uri ->
                    if (uri != null) {
                        val p = ArtLoader.importCover(ctx, id, uri)
                        if (p != null) cover = p else Toaster.show("Не удалось открыть картинку")
                    }
                }
            })) {
                val pv = preview
                if (pv != null) Image(pv, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Cover(t, 84.dp, 10.dp)
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(24.dp).clip(RoundedCornerShape(6.dp)).background(c.bg.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    GIcon(Ic.Image, c.text, 15.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Text("Нажми на обложку, чтобы выбрать картинку из галереи. Файл песни не меняется — правки живут в плеере.", style = T.sub, color = c.text2)
        }
        Spacer(Modifier.height(16.dp))
        Label("Название"); Spacer(Modifier.height(6.dp))
        GField(title, { title = it }, "Название", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Label("Артист"); Spacer(Modifier.height(6.dp))
        GField(artist, { artist = it }, "Артист", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Label("Альбом"); Spacer(Modifier.height(6.dp))
        GField(album, { album = it }, "Альбом", Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton("Сбросить", modifier = Modifier.weight(1f)) {
                Store.setOverride(id, null)
                ArtLoader.invalidate(id)
                Toaster.show("Вернул как было")
                onDismiss()
            }
            KeyButton("Сохранить", primary = true, modifier = Modifier.weight(1f)) {
                Store.setOverride(id, TagOverride(title.trim(), artist.trim(), album.trim(), cover))
                ArtLoader.invalidate(id)
                Toaster.show("Сохранено")
                onDismiss()
            }
        }
    }
}

// ======================= Таймер сна =======================

@Composable
fun SleepSheet(onDismiss: () -> Unit) {
    val c = LocalGrom.current
    val st by SleepTimer.state.collectAsState()
    var finish by remember { mutableStateOf(st?.finishTrack ?: false) }
    var fade by remember { mutableStateOf(st?.fade ?: true) }
    GSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("Таймер сна", style = T.h2, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                if (st != null) "Включён. Можно поменять или выключить." else "Музыка мягко стихнет и остановится сама.",
                style = T.sub, color = c.text2,
            )
            Spacer(Modifier.height(16.dp))
            val opts = listOf(10, 15, 30, 45, 60, 90)
            for (rowI in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in opts.subList(rowI * 3, rowI * 3 + 3)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.raised, c.panelHi)))
                                .bevel(12.dp)
                                .press({ SleepTimer.start(m, finish, fade); Toaster.show("Уснёт через $m мин"); onDismiss() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$m", style = T.h2, color = c.text)
                                Label("мин", style = T.labelSm)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            KeyButton("После текущего трека", Ic.NextUp, modifier = Modifier.fillMaxWidth()) {
                SleepTimer.start(0, true, fade); Toaster.show("Остановлю после этого трека"); onDismiss()
            }
            Spacer(Modifier.height(14.dp))
            SwitchRow("Доиграть трек до конца", "Не обрывать песню на середине", finish) { finish = it }
            SwitchRow("Плавно затухать", "Последние секунды громкость уходит в ноль", fade) { fade = it }
            if (st != null) {
                Spacer(Modifier.height(10.dp))
                KeyButton("Выключить таймер", Ic.Close, modifier = Modifier.fillMaxWidth()) { SleepTimer.cancel(); Toaster.show("Таймер выключен"); onDismiss() }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalGrom.current
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.text)
            if (sub != null) Text(sub, style = T.sub, color = c.text2)
        }
        Spacer(Modifier.width(12.dp))
        LedSwitch(checked, onChange)
    }
}

// ======================= Очередь =======================

@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val queue by app.conn.queue.collectAsState()
    val qpos by app.conn.queuePos.collectAsState()
    val playing by app.conn.isPlaying.collectAsState()
    val all by Library.all.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (qpos - 1).coerceAtLeast(0))
    LaunchedEffect(Unit) { state.scrollToItem((qpos - 1).coerceAtLeast(0)) }
    GSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Очередь", style = T.h2, color = c.text, modifier = Modifier.weight(1f))
            Label("${qpos + 1} / ${queue.size}", color = c.text2)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = state) {
            itemsIndexed(queue) { i, id ->
                val t = all[id]
                val cur = i == qpos
                Row(
                    Modifier.fillMaxWidth().height(58.dp).rowPress({ app.conn.jumpToQueue(i) }).padding(start = 22.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(30.dp)) {
                        if (cur) PlayingBars(c.accent, playing)
                        else Text("%02d".format(i + 1), style = T.num, color = if (i < qpos) c.text3.copy(alpha = 0.6f) else c.text3)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t?.title ?: "Файл", style = T.title, color = if (cur) c.accent else if (i < qpos) c.text2 else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t?.artist ?: "", style = T.sub, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!cur) IconBtn(Ic.Close, c.text3, 16.dp, 40.dp) { app.conn.removeFromQueue(i) }
                }
            }
        }
    }
}

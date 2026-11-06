package su.radiogram.grom.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import su.radiogram.grom.audio.AudioEngine
import su.radiogram.grom.audio.OutKind
import su.radiogram.grom.audio.OutputMonitor
import su.radiogram.grom.audio.SleepTimer
import su.radiogram.grom.data.ArtLoader
import su.radiogram.grom.data.Lyrics
import su.radiogram.grom.data.LyricsRepo
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.Track
import su.radiogram.grom.ui.ActionCtx
import su.radiogram.grom.ui.LocalApp
import su.radiogram.grom.ui.LocalLaunchers
import su.radiogram.grom.ui.comp.Cover
import su.radiogram.grom.ui.comp.GIcon
import su.radiogram.grom.ui.comp.HardKey
import su.radiogram.grom.ui.comp.HeartButton
import su.radiogram.grom.ui.comp.IconBtn
import su.radiogram.grom.ui.comp.KeyButton
import su.radiogram.grom.ui.comp.Knob
import su.radiogram.grom.ui.comp.Label
import su.radiogram.grom.ui.comp.LedSeek
import su.radiogram.grom.ui.comp.Toaster
import su.radiogram.grom.ui.comp.VuMeter
import su.radiogram.grom.ui.comp.fmtTime
import su.radiogram.grom.ui.comp.press
import su.radiogram.grom.ui.comp.rowPress
import su.radiogram.grom.ui.theme.Ic
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.Onest
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.Unbounded
import su.radiogram.grom.ui.theme.bevel
import su.radiogram.grom.ui.theme.grain
import su.radiogram.grom.ui.theme.rememberHaptics
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(track: Track?, onClose: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val c = LocalGrom.current
    val app = LocalApp.current
    val conn = app.conn
    val playing by conn.isPlaying.collectAsState()
    val pos by conn.position.collectAsState()
    val dur by conn.duration.collectAsState()
    val shuffle by conn.shuffle.collectAsState()
    val repeat by conn.repeat.collectAsState()
    val queue by conn.queue.collectAsState()
    val qpos by conn.queuePos.collectAsState()
    val ext by conn.externalTitle.collectAsState()
    val favs by Store.favorites.collectAsState()
    val eq by Store.eq.collectAsState()
    val settings by Store.settings.collectAsState()
    val out by OutputMonitor.state.collectAsState()
    val sleep by SleepTimer.state.collectAsState()
    var dragTime by remember { mutableStateOf<Long?>(null) }
    val fav = track != null && favs.contains(track.id)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val sleepLabel = sleep?.let { if (it.waiting) "ДО КОНЦА" else fmtTime(max(0L, it.endAt - now)) } ?: "СОН"

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val screenH = constraints.maxHeight.toFloat()
        Backdrop(track)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ----- шапка: за неё можно стянуть плеер вниз -----
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { ch, dy -> ch.consume(); onDrag(dy, screenH) }
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Ic.Down, c.text) { onClose() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("Сейчас играет", color = c.text3)
                    Spacer(Modifier.height(2.dp))
                    Text(if (queue.isNotEmpty()) "%02d / %02d".format(qpos + 1, queue.size) else "— / —", style = T.counter, color = c.text2)
                }
                IconBtn(Ic.More, c.text) { track?.let { app.actions = ActionCtx(it.id) } }
            }

            // ----- обложка или текст -----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (app.showLyrics && track != null) {
                    LyricsView(track, pos) { conn.seekTo(it) }
                } else {
                    CoverArt(track, fav, onDoubleTap = { if (track != null && !fav) Store.toggleFavorite(track.id) }) { dir ->
                        if (dir < 0) conn.next() else conn.prev()
                    }
                }
            }

            // ----- название -----
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = track?.title ?: ext ?: "Тишина",
                        transitionSpec = { (fadeIn(tween(280)) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(tween(160))) },
                        label = "title",
                    ) { title ->
                        Text(title, style = T.h1, color = c.text, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2200))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(track?.artist ?: "Нажми ▶ — и поехали", style = T.body.copy(fontSize = 15.sp), color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HeartButton(fav, iconSize = 25.dp, box = 50.dp) { track?.let { Store.toggleFavorite(it.id) } }
            }

            // ----- перемотка -----
            Spacer(Modifier.height(8.dp))
            LedSeek(pos, dur, Modifier.padding(horizontal = 20.dp), onDragging = { dragTime = it }) { conn.seekTo(it) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(fmtTime(dragTime ?: pos), style = T.counter, color = if (dragTime != null) c.accent else c.text)
                Spacer(Modifier.weight(1f))
                Text("−" + fmtTime(max(0L, dur - (dragTime ?: pos))), style = T.counter, color = c.text3)
            }

            // ----- транспорт -----
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(Ic.Shuffle, shuffle) { conn.setShuffle(!shuffle) }
                TransportKey(Ic.Prev) { conn.prev() }
                PlayKey(playing) { conn.toggle() }
                TransportKey(Ic.Next) { conn.next() }
                ToggleIcon(Ic.Repeat, repeat != androidx.media3.common.Player.REPEAT_MODE_OFF, badge = if (repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) "1" else null) { conn.cycleRepeat() }
            }

            // ----- панель усилителя -----
            Spacer(Modifier.height(14.dp))
            AmpPanel(settings.boost, playing, eq.eightD, out.kind, out.name, Store.presetName(eq.presetId) + if (eq.modified) "*" else "")

            // ----- клавиши -----
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HardKey("ТЕКСТ", Ic.Lyrics, app.showLyrics, Modifier.weight(1f)) { app.showLyrics = !app.showLyrics }
                HardKey("8D", Ic.Orbit, eq.eightD, Modifier.weight(1f)) {
                    val on = !eq.eightD
                    Store.updateEq { it.copy(eightD = on) }
                    Toaster.show(if (on) "8D: звук кружит вокруг головы — лучше в наушниках" else "8D выключен")
                }
                HardKey(sleepLabel, Ic.Timer, sleep != null, Modifier.weight(1f)) { app.sleepSheet = true }
                HardKey("ОЧЕРЕДЬ", Ic.Queue, false, Modifier.weight(1f)) { app.queueSheet = true }
                HardKey("ЗВУК", Ic.Sliders, eq.presetId != "flat" || eq.modified, Modifier.weight(1f)) { app.openEqFromPlayer() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ======================= Фон =======================

@Composable
private fun Backdrop(track: Track?) {
    val c = LocalGrom.current
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, track?.id, track?.coverPath) {
        value = track?.let { ArtLoader.load(ctx, it, 300) }
    }
    Box(Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= 31) {
            Crossfade(img, animationSpec = tween(700), label = "bd") { bmp ->
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.55f })
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(c.accent.copy(alpha = 0.20f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.3f), radius = size.width * 0.95f),
                radius = size.width * 0.95f, center = Offset(size.width / 2, size.height * 0.3f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to c.bg.copy(alpha = 0.30f), 0.42f to c.bg.copy(alpha = 0.62f), 0.72f to c.bg.copy(alpha = 0.94f), 1f to c.bg)
            )
        )
        Box(Modifier.fillMaxSize().grain(1f))
    }
}

// ======================= Обложка =======================

@Composable
private fun CoverArt(track: Track?, fav: Boolean, onDoubleTap: () -> Unit, onSwipe: (Int) -> Unit) {
    val c = LocalGrom.current
    val h = rememberHaptics()
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var dx by remember { mutableFloatStateOf(0f) }
    val sx by animateFloatAsState(dx, spring(dampingRatio = 0.7f, stiffness = 500f), label = "cx")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value).dp
        val wpx = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .size(side)
                .graphicsLayer { translationX = sx; rotationZ = sx / 70f }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        onDoubleTap()
                        h.heavy()
                        scope.launch { pop.snapTo(0f); pop.animateTo(1f, tween(700)) }
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dx < -wpx * 0.2f) { h.click(); onSwipe(-1) } else if (dx > wpx * 0.2f) { h.click(); onSwipe(1) }
                            dx = 0f
                        },
                        onDragCancel = { dx = 0f },
                    ) { ch, d -> ch.consume(); dx += d }
                }
                .drawBehind {
                    drawRoundRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(size.width * 0.05f, 18.dp.toPx()), size = Size(size.width * 0.9f, size.height), cornerRadius = CornerRadius(22.dp.toPx()))
                    drawRoundRect(c.accent.copy(alpha = 0.10f), topLeft = Offset(-6.dp.toPx(), 6.dp.toPx()), size = Size(size.width + 12.dp.toPx(), size.height + 6.dp.toPx()), cornerRadius = CornerRadius(22.dp.toPx()))
                }
        ) {
            AnimatedContent(
                targetState = track,
                contentKey = { it?.id },
                transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(380), initialScale = 0.93f)).togetherWith(fadeOut(tween(200))) },
                label = "cover",
            ) { t -> Cover(t, side, 14.dp, px = 900) }
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.55f, size.height * 0.55f)),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
                drawRoundRect(Color.White.copy(alpha = 0.09f), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            val p = pop.value
            if (p < 1f) {
                val s = 0.6f + 0.6f * min(1f, p * 3f)
                GIcon(Ic.HeartFill, c.accent, 110.dp, Modifier.align(Alignment.Center).graphicsLayer { scaleX = s; scaleY = s; alpha = if (p < 0.6f) 1f else (1f - p) / 0.4f })
            }
        }
    }
}

// ======================= Транспорт =======================

@Composable
private fun TransportKey(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalGrom.current
    Box(
        Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.verticalGradient(listOf(c.raised, c.panelHi)))
            .bevel(19.dp)
            .press(onClick, scaleTo = 0.9f),
        contentAlignment = Alignment.Center,
    ) { GIcon(icon, c.text, 26.dp) }
}

@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, badge: String? = null, onClick: () -> Unit) {
    val c = LocalGrom.current
    Box(Modifier.size(48.dp).press(onClick, scaleTo = 0.86f), contentAlignment = Alignment.Center) {
        GIcon(icon, if (active) c.accent else c.text3, 23.dp)
        if (badge != null) Text(badge, style = T.labelSm.copy(fontSize = 8.sp), color = c.accent, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)) {
            if (active) {
                drawCircle(c.accent.copy(alpha = 0.4f), radius = size.minDimension * 1.3f)
                drawCircle(c.accent)
            }
        }
    }
}

/** Главная клавиша: фрезерованный металл, кольцо подсветки «дышит» в такт музыке. */
@Composable
private fun PlayKey(playing: Boolean, onClick: () -> Unit) {
    val c = LocalGrom.current
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        val lv = FloatArray(2)
        while (playing || level > 0.01f) {
            withFrameNanos {
                var target = 0f
                if (playing && AudioEngine.meter.at(AudioEngine.playPositionUs, lv)) {
                    target = ((20 * log10(max(lv[0], lv[1]) + 1e-6f) + 24f) / 24f).coerceIn(0f, 1f)
                }
                level += (target - level) * (if (target > level) 0.35f else 0.08f)
            }
        }
    }
    val ring by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "ring")
    Box(
        Modifier
            .size(84.dp)
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(Color.Black.copy(alpha = 0.55f), radius = r, center = center + Offset(0f, 6.dp.toPx()))
                if (ring > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.accent.copy(alpha = (0.18f + 0.35f * level) * ring), Color.Transparent), center = center, radius = r * (1.25f + 0.25f * level)),
                        radius = r * (1.25f + 0.25f * level), center = center,
                    )
                }
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF55524B), Color(0xFF2B2A27), Color(0xFF1A1A18)), center = Offset(70f, 50f), radius = 260f))
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(
                    Brush.sweepGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.1f), Color.White.copy(alpha = 0f))),
                    radius = r,
                )
                drawCircle(androidx.compose.ui.graphics.lerp(c.line, c.accent, ring), radius = r - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.4f))), radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            .press(onClick, scaleTo = 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, animationSpec = tween(160), label = "pp") { p ->
            GIcon(if (p) Ic.Pause else Ic.Play, c.text, 32.dp)
        }
    }
}

// ======================= Панель усилителя =======================

@Composable
private fun AmpPanel(boost: Int, playing: Boolean, eightD: Boolean, outKind: OutKind, outName: String, preset: String) {
    val c = LocalGrom.current
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(c.panelHi, c.panel)))
            .bevel(18.dp)
            .grain(0.8f)
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Knob(
                    boost / 200f,
                    { v -> Store.updateSettings { s -> s.copy(boost = (v * 200).roundToInt().coerceIn(0, 200)) } },
                    Modifier.size(86.dp),
                    hotFrom = 0.5f,
                    minLabel = "0",
                    maxLabel = "MAX",
                    onDoubleTap = { Store.updateSettings { s -> s.copy(boost = if (s.boost >= 200) 100 else 200) } },
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$boost", style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 17.sp), color = if (boost > 100) c.accent else c.text)
                    Text("%", style = T.counter.copy(fontSize = 11.sp), color = c.text2, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                }
                Label("Громкость", style = T.labelSm)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                VuMeter(playing, eightD, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(if (outKind == OutKind.SPEAKER) Ic.Speaker else Ic.Headphones, c.text3, 13.dp)
                    Spacer(Modifier.width(5.dp))
                    Label(outName, Modifier.weight(1f), color = c.text2, style = T.labelSm)
                    Label(preset, color = c.accent, style = T.labelSm)
                }
            }
        }
    }
}

// ======================= Текст песни =======================

@Composable
private fun LyricsView(track: Track, pos: Long, onSeek: (Long) -> Unit) {
    val c = LocalGrom.current
    val ctx = LocalContext.current
    val launchers = LocalLaunchers.current
    val files by Store.lyricsFiles.collectAsState()
    val res by produceState<Pair<Boolean, Lyrics?>>(false to null, track.id, files[track.id]) {
        value = false to null
        value = true to LyricsRepo.load(ctx, track)
    }
    val (ready, lyr) = res
    val fade = Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 0.14f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
    when {
        !ready -> Label("Ищу текст…")
        lyr == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Текста нет", style = T.h2, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Положи рядом с песней файл .lrc с тем же именем — или прикрепи текст сейчас. Текст внутри тегов MP3 и FLAC подхватится сам.",
                style = T.body, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            KeyButton("Прикрепить .lrc", Ic.Import, primary = true) { launchers.pickLrc(track.id) }
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                Spacer(Modifier.height(10.dp))
                KeyButton("Искать .lrc в папках", Ic.Folder) { launchers.allFiles() }
            }
        }
        lyr.synced -> {
            val lines = lyr.lines
            val idx = remember(pos, lines) {
                var i = -1
                for (k in lines.indices) { if (lines[k].timeMs <= pos + 250) i = k else break }
                i
            }
            val state = rememberLazyListState()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val hgt = maxHeight
                LaunchedEffect(idx) {
                    if (idx >= 0 && !state.isScrollInProgress) state.animateScrollToItem(idx)
                }
                LazyColumn(
                    Modifier.fillMaxSize().then(fade),
                    state = state,
                    contentPadding = PaddingValues(top = hgt * 0.36f, bottom = hgt * 0.55f),
                ) {
                    itemsIndexed(lines) { i, line ->
                        val cur = i == idx
                        val sc by animateFloatAsState(if (cur) 1f else 0.94f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "ly")
                        Text(
                            line.text.ifBlank { "♪" },
                            style = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
                            color = when {
                                cur -> c.text
                                i < idx -> c.text3
                                else -> c.text2.copy(alpha = 0.75f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                                .clip(RoundedCornerShape(8.dp))
                                .rowPress({ onSeek(line.timeMs) })
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        else -> {
            Column(Modifier.fillMaxSize().then(fade).verticalScroll(rememberScrollState()).padding(vertical = 40.dp)) {
                for (line in lyr.lines) {
                    Text(line.text, style = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp), color = c.text)
                }
            }
        }
    }
}

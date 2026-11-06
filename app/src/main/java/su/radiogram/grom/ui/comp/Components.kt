package su.radiogram.grom.ui.comp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import su.radiogram.grom.data.ArtLoader
import su.radiogram.grom.data.Track
import su.radiogram.grom.ui.theme.Ic
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.Unbounded
import su.radiogram.grom.ui.theme.bevel
import su.radiogram.grom.ui.theme.inset
import su.radiogram.grom.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ======================= Нажатия =======================

/** Нажатие с «пружинным» проседанием и тактильным щелчком. */
fun Modifier.press(
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null,
    scaleTo: Float = 0.94f,
    haptic: Boolean = true,
    enabled: Boolean = true,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    val h = rememberHaptics()
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            onClick = { if (haptic) h.click(); onClick() },
            onLongClick = onLong?.let { l -> { h.heavy(); l() } },
        )
}

/** Строка списка: мягкая подсветка при нажатии, без «чернильной» ряби. */
fun Modifier.rowPress(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier = composed {
    val c = LocalGrom.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 260), label = "row")
    val h = rememberHaptics()
    this
        .drawBehind { if (a > 0f) drawRect(c.text.copy(alpha = 0.05f * a)) }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
            onLongClick = onLong?.let { l -> { h.heavy(); l() } },
        )
}

// ======================= Базовые элементы =======================

@Composable
fun GIcon(v: ImageVector, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(v, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun IconBtn(v: ImageVector, tint: Color? = null, size: Dp = 22.dp, box: Dp = 44.dp, modifier: Modifier = Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = LocalGrom.current
    Box(modifier.size(box).press(onClick, onLong, scaleTo = 0.86f), contentAlignment = Alignment.Center) {
        GIcon(v, tint ?: c.text, size)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color? = null, style: TextStyle = T.label) {
    Text(text.uppercase(), style = style, color = color ?: LocalGrom.current.text3, modifier = modifier, maxLines = 1)
}

@Composable
fun NewTag() {
    val c = LocalGrom.current
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(c.accent.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) { Text("НОВОЕ", style = T.labelSm, color = c.accent) }
}

/** Кнопка-«клавиша» с фаской. primary — подсвеченная акцентом. */
@Composable
fun KeyButton(text: String, icon: ImageVector? = null, primary: Boolean = false, modifier: Modifier = Modifier, height: Dp = 46.dp, onClick: () -> Unit) {
    val c = LocalGrom.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(height)
            .then(
                if (primary) Modifier.drawBehind {
                    drawRoundRect(c.accent.copy(alpha = 0.25f), topLeft = Offset(0f, 6.dp.toPx()), size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(14.dp.toPx()))
                } else Modifier
            )
            .clip(shape)
            .background(
                if (primary) Brush.verticalGradient(listOf(c.accent, mixDark(c.accent)))
                else Brush.verticalGradient(listOf(c.raised, c.panelHi))
            )
            .bevel(12.dp, if (primary) 1.4f else 1f)
            .press(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                GIcon(icon, if (primary) c.ink else c.text, 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text.uppercase(), style = T.label.copy(fontWeight = FontWeight.Medium), color = if (primary) c.ink else c.text, maxLines = 1)
        }
    }
}

fun mixDark(c: Color) = Color(c.red * 0.78f, c.green * 0.78f, c.blue * 0.78f, 1f)

/** Клавиша с LED-индикатором — для переключателей на панели плеера. */
@Composable
fun HardKey(label: String, icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = LocalGrom.current
    val led by animateColorAsState(if (active) c.accent else c.text3.copy(alpha = 0.45f), tween(220), label = "led")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.raised, c.panelHi)))
            .bevel(10.dp)
            .press(onClick, onLong, scaleTo = 0.93f),
    ) {
        Canvas(Modifier.padding(start = 7.dp, top = 7.dp).size(5.dp)) {
            if (active) drawCircle(c.accent.copy(alpha = 0.35f), radius = size.minDimension * 1.5f)
            drawCircle(led)
        }
        Column(Modifier.align(Alignment.Center).padding(top = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GIcon(icon, if (active) c.text else c.text2, 19.dp)
            Spacer(Modifier.height(4.dp))
            Text(label, style = T.labelSm, color = if (active) c.text else c.text2, maxLines = 1)
        }
    }
}

/** Тумблер: металлическая каретка в утопленном пазу, в каретке — светодиод. */
@Composable
fun LedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalGrom.current
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "sw")
    Box(
        Modifier
            .size(50.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.bg)
            .inset(14.dp)
            .press({ onChange(!checked) }, scaleTo = 0.95f)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 3.dp.toPx()
            val k = size.height - pad * 2
            val kx = pad + (size.width - pad * 2 - k) * x
            if (x > 0f) drawRoundRect(c.accent.copy(alpha = 0.22f * x), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(kx, pad + 1.5.dp.toPx()), size = Size(k, k), cornerRadius = CornerRadius(k / 2))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF55524B), Color(0xFF2B2A27)), startY = pad, endY = pad + k),
                topLeft = Offset(kx, pad), size = Size(k, k), cornerRadius = CornerRadius(k / 2),
            )
            val ledC = androidx.compose.ui.graphics.lerp(Color(0xFF3A3833), c.accent, x)
            if (x > 0.5f) drawCircle(c.accent.copy(alpha = 0.4f * x), radius = 5.dp.toPx(), center = Offset(kx + k / 2, pad + k / 2))
            drawCircle(ledC, radius = 2.6.dp.toPx(), center = Offset(kx + k / 2, pad + k / 2))
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = LocalGrom.current
    Row(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(c.bg)
            .inset(11.dp)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (sel) Modifier.background(Brush.verticalGradient(listOf(c.raised, c.panelHi))).bevel(8.dp) else Modifier)
                    .press({ onSelect(i) }, scaleTo = 0.96f),
                contentAlignment = Alignment.Center,
            ) { Text(s.uppercase(), style = T.label, color = if (sel) c.text else c.text3, maxLines = 1) }
        }
    }
}

// ======================= Обложки =======================

@Composable
fun Cover(track: Track?, size: Dp, corner: Dp = 6.dp, modifier: Modifier = Modifier, px: Int = 160) {
    val c = LocalGrom.current
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(track?.let { ArtLoader.cached(it, px) }, track?.id, track?.coverPath) {
        value = track?.let { ArtLoader.load(ctx, it, px) }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(c.panelHi)) {
        val bmp = img
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else CoverPlaceholder(track, size)
    }
}

/** Обложка-заглушка: «пластинка» с канавками и буквой — у каждого трека своя. */
@Composable
fun CoverPlaceholder(track: Track?, sz: Dp) {
    val c = LocalGrom.current
    val seed = (track?.album?.hashCode() ?: 0) xor (track?.artist?.hashCode() ?: 0)
    val hue = ((seed and 0x7fffffff) % 360).toFloat()
    val tone = Color.hsv(hue, 0.28f, 0.30f)
    val tone2 = Color.hsv((hue + 30f) % 360f, 0.35f, 0.16f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(tone, tone2), start = Offset.Zero, end = Offset(this.size.width, this.size.height)))
            val cx = this.size.width * 0.78f
            val cy = this.size.height * 0.82f
            val maxR = this.size.width * 1.1f
            var r = this.size.width * 0.08f
            var i = 0
            while (r < maxR) {
                drawCircle(Color.White.copy(alpha = if (i % 4 == 0) 0.07f else 0.03f), radius = r, center = Offset(cx, cy), style = Stroke(0.8f))
                r += this.size.width * 0.045f
                i++
            }
            drawCircle(c.accent.copy(alpha = 0.55f), radius = this.size.width * 0.05f, center = Offset(cx, cy))
        }
        val letter = track?.title?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "♪"
        val fs = with(LocalDensity.current) { (sz * 0.42f).toSp() }
        Text(
            letter,
            style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = fs),
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.TopStart).padding(start = sz * 0.12f, top = sz * 0.06f),
        )
    }
}

/** Три полоски «сейчас играет». */
@Composable
fun PlayingBars(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "bars")
    val a = inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val d = inf.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Canvas(modifier.size(18.dp, 16.dp)) {
        val w = size.width / 5
        val hs = if (playing) listOf(a.value, b.value, d.value) else listOf(0.35f, 0.6f, 0.45f)
        hs.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(color, topLeft = Offset(i * w * 2, size.height - bh), size = Size(w, bh), cornerRadius = CornerRadius(w / 3))
        }
    }
}

// ======================= Сердце =======================

@Composable
fun HeartButton(fav: Boolean, iconSize: Dp = 21.dp, box: Dp = 44.dp, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val c = LocalGrom.current
    val burst = remember { Animatable(1f) }
    var last by remember { mutableStateOf(fav) }
    LaunchedEffect(fav) {
        if (fav && !last) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
        last = fav
    }
    val sc by animateFloatAsState(if (fav) 1f else 0.94f, spring(dampingRatio = 0.35f, stiffness = 420f), label = "heart")
    val tint by animateColorAsState(if (fav) c.accent else c.text3, tween(200), label = "ht")
    Box(modifier.size(box).press(onToggle, scaleTo = 0.78f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(iconSize * 2.4f)) {
            val p = burst.value
            if (p < 1f) {
                val base = iconSize.toPx() * 0.45f
                val r = base + p * iconSize.toPx() * 0.75f
                drawCircle(c.accent.copy(alpha = (1 - p) * 0.55f), radius = r, style = Stroke((1 - p) * 2.6.dp.toPx()))
                for (i in 0 until 8) {
                    val ang = i * PI / 4 + PI / 8
                    val rr = r * 1.18f
                    drawCircle(
                        c.accent.copy(alpha = (1 - p)),
                        radius = (1 - p) * 2.2.dp.toPx(),
                        center = Offset(center.x + (cos(ang) * rr).toFloat(), center.y + (sin(ang) * rr).toFloat()),
                    )
                }
            }
        }
        val pop = if (burst.value < 1f) 1f + 0.28f * sin(burst.value * PI).toFloat() else 1f
        GIcon(if (fav) Ic.HeartFill else Ic.Heart, tint, iconSize, Modifier.graphicsLayer { scaleX = sc * pop; scaleY = sc * pop })
    }
}

// ======================= Строка трека =======================

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtTotal(ms: Long): String {
    val min = ms / 60000
    return if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val a = abs(n) % 100
    val b = a % 10
    return when {
        a in 11..19 -> many
        b == 1 -> one
        b in 2..4 -> few
        else -> many
    }
}

fun tracksWord(n: Int) = "$n " + plural(n, "трек", "трека", "треков")

@Composable
fun TrackRow(
    t: Track,
    current: Boolean,
    playing: Boolean,
    fav: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showNew: Boolean = true,
    subtitle: String? = null,
) {
    val c = LocalGrom.current
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .rowPress(onClick, onMore)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Cover(t, 48.dp, 6.dp)
            if (current) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center,
                ) { PlayingBars(c.accent, playing) }
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showNew && t.isNew) {
                    NewTag()
                    Spacer(Modifier.width(6.dp))
                }
                Text(t.title, style = T.title, color = if (current) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(subtitle ?: t.artist, style = T.sub, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(fmtTime(t.durationMs), style = T.num, color = c.text3)
        HeartButton(fav, iconSize = 19.dp, box = 42.dp, onToggle = onFav)
        IconBtn(Ic.More, c.text3, size = 18.dp, box = 34.dp, onClick = onMore)
    }
}

// ======================= Поле ввода =======================

@Composable
fun GField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val c = LocalGrom.current
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.bg)
            .inset(12.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            GIcon(leading, c.text3, 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = T.body, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = T.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

// ======================= Шторки и диалоги =======================

@Composable
fun GSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalGrom.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.panel,
        contentColor = c.text,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(38.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), content = content)
    }
}

@Composable
fun SheetItem(icon: ImageVector, text: String, tint: Color? = null, hint: String? = null, onClick: () -> Unit) {
    val c = LocalGrom.current
    Row(
        Modifier.fillMaxWidth().height(54.dp).rowPress(onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(icon, tint ?: c.text2, 21.dp)
        Spacer(Modifier.width(18.dp))
        Text(text, style = T.title.copy(fontWeight = FontWeight.Medium), color = tint ?: c.text, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = T.num, color = c.text3)
    }
}

@Composable
fun GDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalGrom.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.panel)
                .bevel(20.dp)
                .padding(20.dp)
        ) {
            Text(title, style = T.h2, color = c.text)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun TextPrompt(title: String, initial: String = "", placeholder: String, action: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    GDialog(title, onDismiss) {
        GField(v, { v = it }, placeholder, Modifier.fillMaxWidth(), onDone = { onOk(v) })
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton("Отмена", modifier = Modifier.weight(1f)) { onDismiss() }
            KeyButton(action, primary = true, modifier = Modifier.weight(1f)) { onOk(v) }
        }
    }
}

// ======================= Всплывашки =======================

object Toaster {
    val msg = MutableStateFlow<Pair<String, Long>?>(null)
    fun show(s: String) { msg.value = s to System.nanoTime() }
}

@Composable
fun BoxScope.ToastHost(bottom: Dp) {
    val c = LocalGrom.current
    val m by Toaster.msg.collectAsState()
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(m) {
        val cur = m ?: return@LaunchedEffect
        text = cur.first
        visible = true
        delay(2300)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.7f)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom, start = 24.dp, end = 24.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.raised)
                .bevel(12.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(6.dp)) { drawCircle(c.accent) }
            Spacer(Modifier.width(10.dp))
            Text(text, style = T.sub, color = c.text)
        }
    }
}

/** Пустое состояние экрана. */
@Composable
fun Empty(title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalGrom.current
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = T.h2, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(text, style = T.body, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

val ListBottomPad = PaddingValues(bottom = 150.dp)

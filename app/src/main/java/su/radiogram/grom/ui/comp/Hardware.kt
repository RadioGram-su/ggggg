package su.radiogram.grom.ui.comp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.radiogram.grom.audio.AudioEngine
import su.radiogram.grom.audio.Coef
import su.radiogram.grom.audio.DspParams
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.Mono
import su.radiogram.grom.ui.theme.Onest
import su.radiogram.grom.ui.theme.T
import su.radiogram.grom.ui.theme.Unbounded
import su.radiogram.grom.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun rad(deg: Float) = (deg * PI / 180.0).toFloat()

// ======================= Поворотная ручка =======================

/**
 * Ручка как на усилителе: шкала на 270°, рифлёная кромка вращается вместе с ручкой,
 * зона выше [hotFrom] светится сильнее (это «буст» выше 100%). Щелчки-детенты под пальцем.
 */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    hotFrom: Float = 2f,
    detents: Int = 40,
    minLabel: String = "",
    maxLabel: String = "",
    onDoubleTap: (() -> Unit)? = null,
) {
    val c = LocalGrom.current
    val h = rememberHaptics()
    val cur by rememberUpdatedState(value)
    val onCh by rememberUpdatedState(onChange)
    val dbl by rememberUpdatedState(onDoubleTap)
    val tm = rememberTextMeasurer()
    val lblStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 8.sp, color = c.text3, letterSpacing = 0.8.sp)

    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { dbl?.invoke(); h.heavy() }) }
            .pointerInput(Unit) {
                var lastAngle = 0f
                var acc = 0f
                detectDragGestures(
                    onDragStart = { p ->
                        lastAngle = atan2(p.y - size.height / 2f, p.x - size.width / 2f)
                        acc = cur
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val p = change.position
                        val a = atan2(p.y - size.height / 2f, p.x - size.width / 2f)
                        var d = a - lastAngle
                        if (d > PI) d -= (2 * PI).toFloat()
                        if (d < -PI) d += (2 * PI).toFloat()
                        lastAngle = a
                        // вертикальный жест тоже крутит: вверх — громче
                        val vertical = -drag.y / (size.height * 2.2f)
                        val prev = acc
                        acc = (acc + d / rad(270f) * 0.85f + vertical * 0.5f).coerceIn(0f, 1f)
                        val stepPrev = floor(prev * detents).toInt()
                        val stepNow = floor(acc * detents).toInt()
                        if (stepNow != stepPrev) {
                            if ((acc >= 1f && prev < 1f) || (prev < hotFrom && acc >= hotFrom) || (prev >= hotFrom && acc < hotFrom)) h.heavy() else h.tick()
                        }
                        onCh(acc)
                    },
                )
            }
    ) {
        val r = min(size.width, size.height) / 2f
        val ctr = Offset(size.width / 2f, size.height / 2f)
        val start = 135f
        val sweep = 270f
        // шкала-тики
        val ticks = 41
        for (i in 0 until ticks) {
            val v = i / (ticks - 1f)
            val ang = rad(start + sweep * v)
            val major = i % 5 == 0
            val r1 = r * 0.985f
            val r0 = if (major) r * 0.86f else r * 0.91f
            val lit = v <= value + 0.0001f
            val hot = v >= hotFrom
            val col = when {
                lit && hot -> c.accent
                lit -> lerp(c.text2, c.accent, 0.55f)
                else -> c.text3.copy(alpha = if (major) 0.75f else 0.45f)
            }
            if (lit && hot) {
                drawLine(c.accent.copy(alpha = 0.25f), ctr + Offset(cos(ang) * r0, sin(ang) * r0), ctr + Offset(cos(ang) * r1, sin(ang) * r1),
                    strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
            }
            drawLine(col, ctr + Offset(cos(ang) * r0, sin(ang) * r0), ctr + Offset(cos(ang) * r1, sin(ang) * r1),
                strokeWidth = if (major) 1.8.dp.toPx() else 1.1.dp.toPx(), cap = StrokeCap.Round)
        }
        // дуга
        val ar = r * 0.78f
        val tl = ctr - Offset(ar, ar)
        drawArc(c.line, start, sweep, false, tl, Size(ar * 2, ar * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (value > 0.001f) {
            drawArc(c.accent.copy(alpha = 0.22f), start, sweep * value, false, tl, Size(ar * 2, ar * 2), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawArc(c.accent, start, sweep * value, false, tl, Size(ar * 2, ar * 2), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        }
        // тень под ручкой
        val rb = r * 0.6f
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent), center = ctr + Offset(0f, rb * 0.22f), radius = rb * 1.28f),
            radius = rb * 1.28f, center = ctr + Offset(0f, rb * 0.22f),
        )
        // корпус
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF55524B), Color(0xFF2E2D29), Color(0xFF1B1B19)), center = ctr - Offset(rb * 0.35f, rb * 0.45f), radius = rb * 1.6f),
            radius = rb, center = ctr,
        )
        // «шлифованный» металл: конический блик
        drawCircle(
            Brush.sweepGradient(
                listOf(
                    Color.White.copy(alpha = 0.00f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.00f),
                    Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.00f), Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.00f), Color.Black.copy(alpha = 0.12f), Color.White.copy(alpha = 0.00f),
                ),
                center = ctr,
            ),
            radius = rb * 0.98f, center = ctr,
        )
        // рифление, вращается с ручкой
        rotate(270f * value, ctr) {
            val n = 54
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                val o = rb * 0.985f
                val inn = rb * 0.90f
                drawLine(
                    if (i % 2 == 0) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.35f),
                    ctr + Offset(cos(a) * inn, sin(a) * inn), ctr + Offset(cos(a) * o, sin(a) * o), strokeWidth = 1.2.dp.toPx(),
                )
            }
        }
        // кромка
        drawCircle(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.5f)), startY = ctr.y - rb, endY = ctr.y + rb),
            radius = rb, center = ctr, style = Stroke(1.2.dp.toPx()),
        )
        // внутренняя шайба
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF3C3A35), Color(0xFF232320)), center = ctr - Offset(rb * 0.2f, rb * 0.3f), radius = rb),
            radius = rb * 0.74f, center = ctr,
        )
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = rb * 0.74f, center = ctr, style = Stroke(1.dp.toPx()))
        // указатель
        val pa = rad(start + sweep * value)
        val p0 = ctr + Offset(cos(pa) * rb * 0.30f, sin(pa) * rb * 0.30f)
        val p1 = ctr + Offset(cos(pa) * rb * 0.80f, sin(pa) * rb * 0.80f)
        drawLine(c.accent.copy(alpha = 0.3f), p0, p1, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(c.accent, p0, p1, strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
        // подписи у краёв шкалы
        if (minLabel.isNotEmpty()) {
            val m = tm.measure(minLabel, lblStyle)
            val a = rad(start)
            drawText(m, topLeft = ctr + Offset(cos(a) * r * 0.92f - m.size.width / 2f, sin(a) * r * 0.92f + 3.dp.toPx()))
        }
        if (maxLabel.isNotEmpty()) {
            val m = tm.measure(maxLabel, lblStyle)
            val a = rad(start + sweep)
            drawText(m, topLeft = ctr + Offset(cos(a) * r * 0.92f - m.size.width / 2f, sin(a) * r * 0.92f + 3.dp.toPx()))
        }
    }
}

// ======================= VU-метр =======================

private class Needle {
    var pos = 0f
    var vel = 0f
    fun step(target: Float, dt: Float) {
        val k = 240f
        val d = 2f * sqrt(k) * 0.6f
        val acc = k * (target - pos) - d * vel
        vel += acc * dt
        pos += vel * dt
        if (pos < -0.04f) { pos = -0.04f; vel = 0f }
        if (pos > 1.06f) { pos = 1.06f; vel *= -0.3f }
    }
}

/** Шкала VU линейна по напряжению: так стрелка ведёт себя как у настоящего прибора. */
private fun vuPos(db: Float): Float {
    val v = 10f.pow(db / 20f)
    return ((v - 0.1f) / (1.4125f - 0.1f))
}

/**
 * Стрелочный VU-метр с подсвеченной кремовой шкалой. Две стрелки: L и R.
 * Синхронизирован с реально звучащим сигналом (после всей обработки).
 */
@Composable
fun VuMeter(playing: Boolean, eightD: Boolean, modifier: Modifier = Modifier) {
    val c = LocalGrom.current
    val tm = rememberTextMeasurer()
    val nl = remember { Needle() }
    val nr = remember { Needle() }
    var frame by remember { mutableLongStateOf(0L) }
    var lim by remember { mutableStateOf(false) }
    val play by rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        val lv = FloatArray(2)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = now
                var tl = -0.03f
                var tr = -0.03f
                if (play && AudioEngine.meter.at(AudioEngine.playPositionUs, lv)) {
                    // 0 VU = −12 dBFS RMS
                    tl = vuPos(20f * log10(lv[0] + 1e-6f) + 12f)
                    tr = vuPos(20f * log10(lv[1] + 1e-6f) + 12f)
                }
                nl.step(tl.coerceIn(-0.03f, 1.05f), dt)
                nr.step(tr.coerceIn(-0.03f, 1.05f), dt)
                lim = play && AudioEngine.limiterGainDb < -1.5f
                frame = now
            }
        }
    }
    val marks = listOf(-20f to "20", -10f to "10", -7f to "7", -5f to "5", -3f to "3", -2f to "", -1f to "1", 0f to "0", 1f to "+1", 2f to "", 3f to "+3")
    val face = lerp(c.meterFace, c.accent, 0.06f)
    val ink = Color(0xFF1C1813)
    val red = Color(0xFFC4331C)
    Box(
        modifier.drawWithCache {
            val numStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 7.5.sp, color = ink)
            val labels = marks.map { (db, s) -> Triple(db, s, if (s.isEmpty()) null else tm.measure(s, numStyle)) }
            val vu = tm.measure("VU", TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ink.copy(alpha = 0.75f)))
            val limT = tm.measure("LIM", TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 6.5.sp, color = ink.copy(alpha = 0.7f)))
            val dT = tm.measure("8D", TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 6.5.sp, color = ink.copy(alpha = 0.7f)))
            val bez = 5.dp.toPx()
            val faceRect = Rect(bez, bez, size.width - bez, size.height - bez)
            val faceRR = RoundRect(faceRect, CornerRadius(7.dp.toPx()))
            val facePath = Path().apply { addRoundRect(faceRR) }
            val pivot = Offset(faceRect.center.x, faceRect.bottom + faceRect.height * 0.30f)
            val rs = faceRect.height * 0.98f
            val spanDeg = 46f
            fun angOf(p: Float) = rad(-90f - spanDeg + 2 * spanDeg * p)
            onDrawBehind {
                frame // подписка на кадры
                // корпус
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2A2926), Color(0xFF121210))), cornerRadius = CornerRadius(11.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(11.dp.toPx()), style = Stroke(1.dp.toPx()))
                clipPath(facePath) {
                    drawRect(Brush.verticalGradient(listOf(face, lerp(face, Color(0xFFB9A880), 0.35f)), startY = faceRect.top, endY = faceRect.bottom), faceRect.topLeft, faceRect.size)
                    // тёплая подсветка снизу
                    drawCircle(
                        Brush.radialGradient(listOf(lerp(Color(0xFFFFE6B0), c.accent, 0.25f).copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(faceRect.center.x, faceRect.bottom), radius = faceRect.width * 0.7f),
                        radius = faceRect.width * 0.7f, center = Offset(faceRect.center.x, faceRect.bottom),
                    )
                    // основная дуга и красная зона
                    val arcTl = pivot - Offset(rs, rs)
                    val p0 = vuPos(-20f); val pz = vuPos(0f); val p3 = vuPos(3f)
                    val a0 = -90f - spanDeg + 2 * spanDeg * p0
                    val az = -90f - spanDeg + 2 * spanDeg * pz
                    val a3 = -90f - spanDeg + 2 * spanDeg * p3
                    drawArc(ink.copy(alpha = 0.8f), a0, az - a0, false, arcTl, Size(rs * 2, rs * 2), style = Stroke(1.dp.toPx()))
                    drawArc(red, az, a3 - az, false, arcTl + Offset(-2.dp.toPx(), -2.dp.toPx()), Size(rs * 2 + 4.dp.toPx(), rs * 2 + 4.dp.toPx()), style = Stroke(4.dp.toPx()))
                    // деления и цифры
                    for ((db, _, lay) in labels) {
                        val a = angOf(vuPos(db))
                        val major = lay != null
                        val r0 = rs - (if (major) 7.dp.toPx() else 4.dp.toPx())
                        val col = if (db > 0f) red else ink
                        drawLine(col, pivot + Offset(cos(a) * r0, sin(a) * r0), pivot + Offset(cos(a) * (rs + 1.dp.toPx()), sin(a) * (rs + 1.dp.toPx())), strokeWidth = if (major) 1.3.dp.toPx() else 0.9.dp.toPx())
                        if (lay != null) {
                            val tr = rs + 9.dp.toPx()
                            val pos = pivot + Offset(cos(a) * tr, sin(a) * tr)
                            drawText(lay, color = col, topLeft = pos - Offset(lay.size.width / 2f, lay.size.height / 2f))
                        }
                    }
                    // мелкие деления
                    var d = -20f
                    while (d < 3f) {
                        val a = angOf(vuPos(d))
                        drawLine(ink.copy(alpha = 0.5f), pivot + Offset(cos(a) * (rs - 2.5.dp.toPx()), sin(a) * (rs - 2.5.dp.toPx())), pivot + Offset(cos(a) * rs, sin(a) * rs), strokeWidth = 0.7.dp.toPx())
                        d += if (d < -10f) 2.5f else 0.5f
                    }
                    drawText(vu, topLeft = Offset(faceRect.center.x - vu.size.width / 2f, faceRect.top + faceRect.height * 0.50f))
                    // LED: лимитер и 8D
                    val ledR = 2.6.dp.toPx()
                    val limC = Offset(faceRect.right - 10.dp.toPx(), faceRect.top + 9.dp.toPx())
                    if (lim) drawCircle(red.copy(alpha = 0.45f), radius = ledR * 2.6f, center = limC)
                    drawCircle(if (lim) Color(0xFFFF4A26) else Color(0xFF6D5B4A), radius = ledR, center = limC)
                    drawText(limT, topLeft = Offset(limC.x - limT.size.width - ledR * 2, limC.y - limT.size.height / 2f))
                    val dC = Offset(faceRect.left + 10.dp.toPx(), faceRect.top + 9.dp.toPx())
                    if (eightD) drawCircle(c.accent.copy(alpha = 0.45f), radius = ledR * 2.6f, center = dC)
                    drawCircle(if (eightD) c.accent else Color(0xFF6D5B4A), radius = ledR, center = dC)
                    drawText(dT, topLeft = Offset(dC.x + ledR * 2, dC.y - dT.size.height / 2f))
                    // стрелки
                    fun needle(p: Float, col: Color, w: Float) {
                        val a = angOf(p.coerceIn(-0.04f, 1.06f))
                        val tip = pivot + Offset(cos(a) * (rs + 4.dp.toPx()), sin(a) * (rs + 4.dp.toPx()))
                        drawLine(Color.Black.copy(alpha = 0.16f), pivot + Offset(2.5.dp.toPx(), 2.dp.toPx()), tip + Offset(2.5.dp.toPx(), 2.dp.toPx()), strokeWidth = w, cap = StrokeCap.Round)
                        drawLine(col, pivot, tip, strokeWidth = w, cap = StrokeCap.Round)
                    }
                    needle(nr.pos, lerp(c.accent, Color(0xFF7A2410), 0.45f), 1.4.dp.toPx())
                    needle(nl.pos, ink, 1.7.dp.toPx())
                    // основание стрелок
                    drawRect(
                        Brush.verticalGradient(listOf(Color(0xFF2B2925), Color(0xFF151513)), startY = faceRect.bottom - faceRect.height * 0.16f, endY = faceRect.bottom),
                        topLeft = Offset(faceRect.left, faceRect.bottom - faceRect.height * 0.16f), size = Size(faceRect.width, faceRect.height * 0.16f),
                    )
                    // стекло
                    drawRect(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = faceRect.topLeft, end = Offset(faceRect.center.x, faceRect.bottom)),
                        faceRect.topLeft, faceRect.size,
                    )
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), startY = faceRect.top, endY = faceRect.top + 6.dp.toPx()), faceRect.topLeft, Size(faceRect.width, 6.dp.toPx()))
                }
            }
        }
    )
}

// ======================= LED-перемотка =======================

/** Перемотка в виде ленты светодиодов-делений: каждое десятое — выше, как на линейке. */
@Composable
fun LedSeek(position: Long, duration: Long, modifier: Modifier = Modifier, onDragging: (Long?) -> Unit = {}, onSeek: (Long) -> Unit) {
    val c = LocalGrom.current
    val h = rememberHaptics()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur by rememberUpdatedState(duration)
    val seek by rememberUpdatedState(onSeek)
    val dragging by rememberUpdatedState(onDragging)
    var lastStep by remember { mutableIntStateOf(-1) }
    val f = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val glow by animateFloatAsState(if (drag != null) 1f else 0f, spring(stiffness = 300f), label = "seekglow")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val v = (p.x / size.width).coerceIn(0f, 1f)
                    h.click()
                    seek((v * dur).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { p -> drag = (p.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { seek((it * dur).toLong()) }
                        drag = null
                        dragging(null)
                    },
                    onDragCancel = { drag = null; dragging(null) },
                    onHorizontalDrag = { ch, dx ->
                        ch.consume()
                        val v = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                        drag = v
                        dragging((v * dur).toLong())
                        val step = (v * 60).toInt()
                        if (step != lastStep) { lastStep = step; h.tick() }
                    },
                )
            }
    ) {
        val seg = 3.dp.toPx()
        val gap = 2.4.dp.toPx()
        val n = max(10, ((size.width + gap) / (seg + gap)).toInt())
        val total = n * seg + (n - 1) * gap
        val x0 = (size.width - total) / 2f
        val head = (f * n).toInt().coerceIn(0, n - 1)
        val cy = size.height / 2f
        for (i in 0 until n) {
            val x = x0 + i * (seg + gap)
            val tall = i % 10 == 0
            val lit = i < head
            var hgt = if (tall) 16.dp.toPx() else 11.dp.toPx()
            val col: Color
            if (i == head) {
                hgt = 26.dp.toPx() + 4.dp.toPx() * glow
                col = c.text
                drawRoundRect(c.accent.copy(alpha = 0.35f + 0.25f * glow), topLeft = Offset(x - seg * 1.5f, cy - hgt / 2 - 2.dp.toPx()),
                    size = Size(seg * 4, hgt + 4.dp.toPx()), cornerRadius = CornerRadius(seg * 2))
            } else if (lit) {
                col = c.accent.copy(alpha = 0.55f + 0.45f * (i.toFloat() / max(1, head)))
            } else {
                col = c.text3.copy(alpha = if (tall) 0.6f else 0.32f)
            }
            drawRoundRect(col, topLeft = Offset(x, cy - hgt / 2), size = Size(seg, hgt), cornerRadius = CornerRadius(seg / 2))
        }
    }
}

// ======================= Фейдер полосы EQ =======================

@Composable
fun Fader(valueDb: Float, label: String, modifier: Modifier = Modifier, range: Float = 12f, onChange: (Float) -> Unit) {
    val c = LocalGrom.current
    val h = rememberHaptics()
    val cur by rememberUpdatedState(valueDb)
    val ch by rememberUpdatedState(onChange)
    var active by remember { mutableStateOf(false) }
    var acc by remember { mutableFloatStateOf(0f) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val shown = if (valueDb > 0.05f) "+%.0f".format(valueDb) else if (valueDb < -0.05f) "%.0f".format(valueDb) else "0"
        Text(shown, style = T.labelSm, color = if (active) c.accent else c.text2)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { ch(0f); h.heavy() }) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { active = true; acc = cur },
                        onDragEnd = { active = false },
                        onDragCancel = { active = false },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            val track = size.height - 24.dp.toPx()
                            val prev = acc
                            acc = (acc - dy / track * range * 2).coerceIn(-range, range)
                            val snapped = (acc * 2).roundToInt() / 2f
                            if (prev.roundToInt() != acc.roundToInt()) { if (acc.roundToInt() == 0) h.heavy() else h.tick() }
                            ch(snapped)
                        },
                    )
                }
        ) {
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val cx = size.width / 2
            fun y(db: Float) = top + (1 - (db + range) / (2 * range)) * (bottom - top)
            // деления
            for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
                if (kotlin.math.abs(db) > range) continue
                val yy = y(db)
                val w = if (db == 0f) 9.dp.toPx() else 5.dp.toPx()
                drawLine(c.text3.copy(alpha = if (db == 0f) 0.9f else 0.5f), Offset(cx - 9.dp.toPx() - w, yy), Offset(cx - 9.dp.toPx(), yy), 1.dp.toPx())
                drawLine(c.text3.copy(alpha = if (db == 0f) 0.9f else 0.5f), Offset(cx + 9.dp.toPx(), yy), Offset(cx + 9.dp.toPx() + w, yy), 1.dp.toPx())
            }
            // паз
            drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(cx - 2.5.dp.toPx(), top), size = Size(5.dp.toPx(), bottom - top), cornerRadius = CornerRadius(2.5.dp.toPx()))
            val vy = y(valueDb)
            val zy = y(0f)
            if (kotlin.math.abs(vy - zy) > 1f) {
                drawRoundRect(c.accent.copy(alpha = 0.85f), topLeft = Offset(cx - 1.5.dp.toPx(), min(vy, zy)), size = Size(3.dp.toPx(), kotlin.math.abs(vy - zy)), cornerRadius = CornerRadius(1.5.dp.toPx()))
            }
            // колпачок
            val cw = 26.dp.toPx(); val chh = 17.dp.toPx()
            val tl = Offset(cx - cw / 2, vy - chh / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = tl + Offset(0f, 3.dp.toPx()), size = Size(cw, chh), cornerRadius = CornerRadius(4.dp.toPx()))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5A5750), Color(0xFF2C2B28)), startY = tl.y, endY = tl.y + chh), topLeft = tl, size = Size(cw, chh), cornerRadius = CornerRadius(4.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = tl, size = Size(cw, chh), cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(0.8.dp.toPx()))
            for (k in -1..1) {
                if (k == 0) continue
                val gy = vy + k * 4.dp.toPx()
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(tl.x + 4.dp.toPx(), gy), Offset(tl.x + cw - 4.dp.toPx(), gy), 1.dp.toPx())
            }
            drawLine(if (active) c.accent else c.accent.copy(alpha = 0.8f), Offset(tl.x + 3.dp.toPx(), vy), Offset(tl.x + cw - 3.dp.toPx(), vy), 1.6.dp.toPx())
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = T.labelSm, color = c.text3)
    }
}

// ======================= Кривая звука =======================

@Composable
fun EqCurve(p: DspParams, modifier: Modifier = Modifier) {
    val c = LocalGrom.current
    val tm = rememberTextMeasurer()
    val pts = remember(p) {
        val n = 96
        FloatArray(n) { i ->
            val f = 20.0 * 1000.0.pow(i / (n - 1.0))
            Coef.responseDb(f, p).toFloat()
        }
    }
    Canvas(modifier) {
        val top = 6.dp.toPx(); val bottom = size.height - 14.dp.toPx()
        val range = 15f
        fun y(db: Float) = top + (1 - (db.coerceIn(-range, range) + range) / (2 * range)) * (bottom - top)
        val lbl = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 7.sp, color = c.text3)
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
            drawLine(c.line.copy(alpha = if (db == 0f) 1f else 0.5f), Offset(0f, y(db)), Offset(size.width, y(db)), 1f)
        }
        for ((f, s) in listOf(100.0 to "100", 1000.0 to "1K", 10000.0 to "10K")) {
            val x = (ln(f / 20.0) / ln(1000.0)).toFloat() * size.width
            drawLine(c.line.copy(alpha = 0.6f), Offset(x, top), Offset(x, bottom), 1f)
            val m = tm.measure(s, lbl)
            drawText(m, topLeft = Offset(x - m.size.width / 2f, bottom + 2.dp.toPx()))
        }
        val path = Path()
        val fill = Path()
        for (i in pts.indices) {
            val x = i / (pts.size - 1f) * size.width
            val yy = y(pts[i])
            if (i == 0) { path.moveTo(x, yy); fill.moveTo(x, bottom); fill.lineTo(x, yy) } else { path.lineTo(x, yy); fill.lineTo(x, yy) }
        }
        fill.lineTo(size.width, bottom); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = bottom))
        drawPath(path, c.accent.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ======================= Картридж =======================

/** Звуковой картридж: корпус с выемкой, цветная наклейка с названием и мини-кривой, светодиод. */
@Composable
fun Cartridge(
    name: String,
    tag: String,
    color: Color,
    bands: List<Float>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalGrom.current
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "lift")
    Box(
        modifier
            .height(132.dp)
            .graphicsLayer { translationY = -5.dp.toPx() * lift }
            .press(onClick, onLong, scaleTo = 0.95f)
    ) {
        Canvas(Modifier.fillMaxSize()) { drawCartridge(this, c.raised, c.panelHi, c.accent, color, bands, lift) }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
            Text(name, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = (-0.2).sp), color = Color(0xFF15120E), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(tag, style = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp), color = Color(0xFF15120E).copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun drawCartridge(ds: DrawScope, body: Color, body2: Color, accent: Color, label: Color, bands: List<Float>, lift: Float) = with(ds) {
    val r = 12.dp.toPx()
    val notchW = size.width * 0.34f
    val notchH = 5.dp.toPx()
    val shell = Path().apply {
        moveTo(r, 0f)
        lineTo(size.width / 2 - notchW / 2, 0f)
        lineTo(size.width / 2 - notchW / 2 + notchH, notchH)
        lineTo(size.width / 2 + notchW / 2 - notchH, notchH)
        lineTo(size.width / 2 + notchW / 2, 0f)
        lineTo(size.width - r, 0f)
        quadraticBezierTo(size.width, 0f, size.width, r)
        lineTo(size.width, size.height - r)
        quadraticBezierTo(size.width, size.height, size.width - r, size.height)
        lineTo(r, size.height)
        quadraticBezierTo(0f, size.height, 0f, size.height - r)
        lineTo(0f, r)
        quadraticBezierTo(0f, 0f, r, 0f)
        close()
    }
    // тень и свечение выбранного
    if (lift > 0.01f) {
        drawRoundRect(accent.copy(alpha = 0.22f * lift), topLeft = Offset(-3.dp.toPx(), 4.dp.toPx()), size = Size(size.width + 6.dp.toPx(), size.height + 4.dp.toPx()), cornerRadius = CornerRadius(r + 3.dp.toPx()))
    }
    drawPath(shell, Color.Black.copy(alpha = 0.45f), style = Stroke(6.dp.toPx()))
    drawPath(shell, Brush.verticalGradient(listOf(body, body2)))
    drawPath(shell, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.4f))), style = Stroke(1.dp.toPx()))
    // наклейка
    val lt = Offset(8.dp.toPx(), 12.dp.toPx())
    val ls = Size(size.width - 16.dp.toPx(), size.height * 0.62f)
    drawRoundRect(lerp(label, Color(0xFFF2E9D6), 0.18f), topLeft = lt, size = ls, cornerRadius = CornerRadius(6.dp.toPx()))
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.12f)), startY = lt.y, endY = lt.y + ls.height), topLeft = lt, size = ls, cornerRadius = CornerRadius(6.dp.toPx()))
    // мини-кривая по наклейке
    if (bands.size == 10) {
        val p = Path()
        val base = lt.y + ls.height - 12.dp.toPx()
        val amp = 0.75.dp.toPx()
        for (i in 0 until 10) {
            val x = lt.x + 8.dp.toPx() + i / 9f * (ls.width - 16.dp.toPx())
            val yy = base - bands[i] * amp
            if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
        }
        drawLine(Color(0xFF15120E).copy(alpha = 0.2f), Offset(lt.x + 8.dp.toPx(), base), Offset(lt.x + ls.width - 8.dp.toPx(), base), 0.8.dp.toPx())
        drawPath(p, Color(0xFF15120E).copy(alpha = 0.8f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
    // окошки и винты
    val by = lt.y + ls.height + (size.height - lt.y - ls.height) / 2
    for (sx in listOf(14.dp.toPx(), size.width - 14.dp.toPx())) {
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 3.6.dp.toPx(), center = Offset(sx, by))
        drawCircle(Color(0xFF4A4843), radius = 3.dp.toPx(), center = Offset(sx, by))
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(sx - 2.dp.toPx(), by), Offset(sx + 2.dp.toPx(), by), 1.dp.toPx())
    }
    val wy = by - 3.dp.toPx()
    for (k in 0..1) {
        val wx = size.width / 2 - 26.dp.toPx() + k * 30.dp.toPx()
        drawRoundRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(wx, wy), size = Size(22.dp.toPx(), 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
    }
    // светодиод
    val led = Offset(size.width - 14.dp.toPx(), 6.5.dp.toPx() + 0f)
    if (lift > 0.01f) drawCircle(accent.copy(alpha = 0.5f * lift), radius = 6.dp.toPx(), center = led)
    drawCircle(lerp(Color(0xFF3A3833), accent, lift), radius = 2.4.dp.toPx(), center = led)
}

@Composable
fun KnobBlock(title: String, valueText: String, value: Float, modifier: Modifier = Modifier, knobSize: androidx.compose.ui.unit.Dp = 78.dp, hotFrom: Float = 2f, minLabel: String = "", maxLabel: String = "", onDoubleTap: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val c = LocalGrom.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Knob(value, onChange, Modifier.width(knobSize).height(knobSize), hotFrom = hotFrom, minLabel = minLabel, maxLabel = maxLabel, onDoubleTap = onDoubleTap)
        Spacer(Modifier.height(2.dp))
        Text(valueText, style = T.counter.copy(fontSize = 12.sp), color = c.text)
        Text(title.uppercase(), style = T.labelSm, color = c.text3)
    }
}

@Composable
fun LabeledRow(content: @Composable () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { content() } }

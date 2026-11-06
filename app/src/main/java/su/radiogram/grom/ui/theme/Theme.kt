package su.radiogram.grom.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.radiogram.grom.R
import su.radiogram.grom.data.Store
import kotlin.random.Random

// ======================= Шрифты =======================

val Unbounded = FontFamily(
    Font(R.font.unbounded_black, FontWeight.Black),
    Font(R.font.unbounded_medium, FontWeight.Medium),
)
val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)
/** Martian Mono: Normal — обычная ширина, Medium — узкая (для шкал и счётчиков). */
val Mono = FontFamily(
    Font(R.font.martian_mono, FontWeight.Normal),
    Font(R.font.martian_mono_cond, FontWeight.Medium),
)

object T {
    val wordmark = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-0.6).sp)
    val display = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp)
    val h1 = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 21.sp, letterSpacing = (-0.3).sp, lineHeight = 26.sp)
    val h2 = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.1).sp)
    val title = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)
    val body = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    val sub = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.4.sp)
    val labelSm = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 8.5.sp, letterSpacing = 1.2.sp)
    val num = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.3.sp)
    val counter = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.sp)
}

// ======================= Цвета =======================

val SIGNAL = Color(0xFFFF5B1F)

@Immutable
data class GromColors(
    val bg: Color,
    val panel: Color,
    val panelHi: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val meterFace: Color,
    val amoled: Boolean,
) {
    val accentDim: Color get() = accent.copy(alpha = 0.16f)
    val ink: Color get() = Color(0xFF14110E)
}

fun graphite(accent: Color) = GromColors(
    bg = Color(0xFF0F0F0E), panel = Color(0xFF171715), panelHi = Color(0xFF1E1E1B), raised = Color(0xFF282824),
    line = Color(0xFF2C2B27), text = Color(0xFFEEE8DC), text2 = Color(0xFF9C968B), text3 = Color(0xFF5F5B54),
    accent = accent, meterFace = Color(0xFFEFE4C8), amoled = false,
)

fun amoled(accent: Color) = GromColors(
    bg = Color(0xFF000000), panel = Color(0xFF0B0B0B), panelHi = Color(0xFF131313), raised = Color(0xFF1C1C1B),
    line = Color(0xFF222220), text = Color(0xFFEEE8DC), text2 = Color(0xFF9C968B), text3 = Color(0xFF5A5650),
    accent = accent, meterFace = Color(0xFFE9DDBF), amoled = true,
)

val LocalGrom = staticCompositionLocalOf { graphite(SIGNAL) }

/** Акцент поверх тёмного: смешиваем с фоном для «подсветки». */
fun Color.glow(a: Float) = copy(alpha = a)
fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

// ======================= Фактура =======================

private var noiseCache: ImageBitmap? = null

private fun noise(): ImageBitmap {
    noiseCache?.let { return it }
    val n = 160
    val px = IntArray(n * n)
    val r = Random(42)
    for (i in px.indices) {
        val v = r.nextFloat()
        px[i] = when {
            v < 0.5f -> android.graphics.Color.argb((r.nextFloat() * 22).toInt(), 255, 255, 255)
            else -> android.graphics.Color.argb((r.nextFloat() * 30).toInt(), 0, 0, 0)
        }
    }
    val b = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    noiseCache = b
    return b
}

/** Плёночное зерно поверх поверхности — убирает «пластиковость» плоских заливок. */
fun Modifier.grain(alpha: Float = 1f): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(noise(), TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}

/** Фаска: светлая кромка сверху, тёмная снизу — как у фрезерованной детали. */
fun Modifier.bevel(radius: Dp, strength: Float = 1f): Modifier = drawWithContent {
    drawContent()
    val r = radius.toPx()
    val sw = 1.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f * strength),
            0.35f to Color.White.copy(alpha = 0.02f * strength),
            1f to Color.Black.copy(alpha = 0.35f * strength),
        ),
        topLeft = Offset(sw / 2, sw / 2),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(sw),
    )
}

/** Утопленная поверхность: внутренняя тень сверху. */
fun Modifier.inset(radius: Dp): Modifier = drawWithContent {
    drawContent()
    val r = radius.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.18f to Color.Transparent,
            0.9f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.05f),
        ),
        cornerRadius = CornerRadius(r, r),
    )
}

// ======================= Тактильная отдача =======================

class Haptics(private val view: View) {
    private fun on() = Store.settings.value.haptics
    fun tick() { if (on()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun click() { if (on()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun heavy() {
        if (!on()) return
        if (Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val v = LocalView.current
    return remember(v) { Haptics(v) }
}

package su.radiogram.grom.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import su.radiogram.grom.data.EqState
import su.radiogram.grom.data.Settings
import su.radiogram.grom.data.Store
import kotlin.math.max
import kotlin.math.min

/** Параметры звуковой цепочки. Неизменяемый снимок: процессор сравнивает по ссылке. */
data class DspParams(
    val bands: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
    val lowCut: Float = 0f,
    val highCut: Float = 0f,
    val bass: Float = 0f,
    val warmth: Float = 0f,
    val comp: Float = 0f,
    val width: Float = 1f,
    val reverb: Float = 0f,
    val room: Float = 0.5f,
    val eightD: Boolean = false,
    val boost: Float = 2f,
    val limiter: Boolean = true,
)

/** Кольцевой буфер уровней с метками времени — чтобы стрелки VU шли ровно в такт тому, что слышно. */
class LevelRing(private val n: Int) {
    private val pts = LongArray(n)
    private val l = FloatArray(n)
    private val r = FloatArray(n)
    @Volatile private var w = 0
    @Volatile private var count = 0

    fun push(p: Long, lv: Float, rv: Float) {
        val i = w
        pts[i] = p; l[i] = lv; r[i] = rv
        w = (i + 1) % n
        count = min(n, count + 1)
    }

    fun clear() { count = 0 }

    /** Уровни для позиции воспроизведения posUs; out[0]=L, out[1]=R (RMS 0..1). */
    fun at(posUs: Long, out: FloatArray): Boolean {
        val c = count
        if (c == 0) return false
        var i = (w - 1 + n) % n
        val newest = i
        repeat(c) {
            if (pts[i] <= posUs) {
                out[0] = l[i]; out[1] = r[i]
                return true
            }
            i = (i - 1 + n) % n
        }
        out[0] = l[newest]; out[1] = r[newest]
        return true
    }
}

object AudioEngine {
    @Volatile var params = DspParams()
    @Volatile var fade = 1f
    @Volatile var sinkPtsUs = 0L
    @Volatile var playPositionUs = Long.MIN_VALUE
    @Volatile var limiterGainDb = 0f
    val meter = LevelRing(384)

    fun bind(scope: CoroutineScope) {
        combine(Store.eq, Store.settings, OutputMonitor.state) { eq, s, out -> eq.toParams(s, out) }
            .onEach { params = it }
            .launchIn(scope)
    }
}

fun EqState.toParams(s: Settings, out: Output): DspParams {
    val guard = if (s.speakerGuard && out.kind == OutKind.SPEAKER) 120f else 0f
    return DspParams(
        bands = bands,
        preampDb = preamp,
        lowCut = max(lowCut, guard),
        highCut = highCut,
        bass = bass,
        warmth = warmth,
        comp = comp,
        width = width,
        reverb = reverb,
        room = room,
        eightD = eightD,
        boost = s.boost / 100f,
        limiter = s.limiter,
    )
}

enum class OutKind { SPEAKER, WIRED, BLUETOOTH, USB }

data class Output(val kind: OutKind, val name: String)

/** Следит, куда идёт звук: динамик, провод, Bluetooth. */
object OutputMonitor {
    private val _state = MutableStateFlow(Output(OutKind.SPEAKER, "Динамик"))
    val state: StateFlow<Output> = _state
    private var am: AudioManager? = null

    fun init(ctx: Context) {
        val m = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am = m
        m.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = update()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = update()
        }, Handler(Looper.getMainLooper()))
        update()
    }

    fun update() {
        val m = am ?: return
        val devs = try { m.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } catch (e: Exception) { emptyArray() }
        var best: Output? = null
        var bestRank = 0
        for (d in devs) {
            val (kind, rank) = when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27 -> OutKind.BLUETOOTH to 5
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> OutKind.USB to 4
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutKind.WIRED to 3
                AudioDeviceInfo.TYPE_HEARING_AID -> OutKind.BLUETOOTH to 2
                else -> null to 0
            }
            if (kind != null && rank > bestRank) {
                bestRank = rank
                val pn = d.productName?.toString()?.trim().orEmpty()
                val name = when (kind) {
                    OutKind.BLUETOOTH -> pn.ifEmpty { "Bluetooth" }
                    OutKind.USB -> pn.ifEmpty { "USB-наушники" }
                    OutKind.WIRED -> "Наушники"
                    OutKind.SPEAKER -> "Динамик"
                }
                best = Output(kind, name)
            }
        }
        _state.value = best ?: Output(OutKind.SPEAKER, "Динамик")
    }
}

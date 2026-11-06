package su.radiogram.grom.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Центральные частоты 10-полосного эквалайзера. */
val EQ_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = arrayOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
const val EQ_Q = 1.05

/** Расчёт коэффициентов биквадов (RBJ cookbook). Массив: b0, b1, b2, a1, a2 (нормированы на a0). */
object Coef {
    fun peaking(fs: Double, f0: Double, q: Double, gainDb: Double): DoubleArray? {
        if (abs(gainDb) < 0.05 || f0 >= fs * 0.46) return null
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha / a
        return doubleArrayOf((1 + alpha * a) / a0, (-2 * c) / a0, (1 - alpha * a) / a0, (-2 * c) / a0, (1 - alpha / a) / a0)
    }

    fun lowpass(fs: Double, f0: Double, q: Double = 0.7071): DoubleArray? {
        if (f0 <= 0.0 || f0 >= fs * 0.46) return null
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha
        return doubleArrayOf((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, (-2 * c) / a0, (1 - alpha) / a0)
    }

    fun highpass(fs: Double, f0: Double, q: Double = 0.7071): DoubleArray? {
        if (f0 <= 0.0 || f0 >= fs * 0.46) return null
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha
        return doubleArrayOf((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, (-2 * c) / a0, (1 - alpha) / a0)
    }

    /** АЧХ биквада в дБ на частоте f. */
    fun magDb(k: DoubleArray?, f: Double, fs: Double): Double {
        if (k == null) return 0.0
        val w = 2 * PI * f / fs
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = k[0] + k[1] * c1 + k[2] * c2
        val ni = -(k[1] * s1 + k[2] * s2)
        val dr = 1 + k[3] * c1 + k[4] * c2
        val di = -(k[3] * s1 + k[4] * s2)
        val num = nr * nr + ni * ni
        val den = dr * dr + di * di
        return 10 * log10(max(num, 1e-20) / max(den, 1e-20))
    }

    /** Суммарная кривая звука для экрана настройки. */
    fun responseDb(f: Double, p: DspParams, fs: Double = 48000.0): Double {
        var db = p.preampDb.toDouble()
        for (i in 0 until 10) db += magDb(peaking(fs, EQ_FREQS[i].toDouble(), EQ_Q, p.bands[i].toDouble()), f, fs)
        if (p.lowCut > 0f) db += magDb(highpass(fs, p.lowCut.toDouble()), f, fs)
        if (p.highCut > 0f) db += magDb(lowpass(fs, p.highCut.toDouble()), f, fs)
        return db
    }
}

/** Стерео-биквад, транспонированная прямая форма II. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var l1 = 0.0; private var l2 = 0.0; private var r1 = 0.0; private var r2 = 0.0
    var active = false
        private set

    fun set(k: DoubleArray?) {
        if (k == null) {
            if (active) reset()
            active = false
            return
        }
        b0 = k[0]; b1 = k[1]; b2 = k[2]; a1 = k[3]; a2 = k[4]
        active = true
    }

    fun reset() { l1 = 0.0; l2 = 0.0; r1 = 0.0; r2 = 0.0 }

    fun left(x: Double): Double {
        val y = b0 * x + l1
        l1 = b1 * x - a1 * y + l2
        l2 = b2 * x - a2 * y
        return y
    }

    fun right(x: Double): Double {
        val y = b0 * x + r1
        r1 = b1 * x - a1 * y + r2
        r2 = b2 * x - a2 * y
        return y
    }
}

private class Comb(size: Int) {
    private val buf = DoubleArray(max(2, size))
    private var idx = 0
    private var store = 0.0
    fun process(x: Double, fb: Double, d1: Double, d2: Double): Double {
        val out = buf[idx]
        store = out * d2 + store * d1
        buf[idx] = x + store * fb
        if (++idx >= buf.size) idx = 0
        return out
    }
    fun clear() { buf.fill(0.0); store = 0.0 }
}

private class Allpass(size: Int) {
    private val buf = DoubleArray(max(2, size))
    private var idx = 0
    fun process(x: Double): Double {
        val b = buf[idx]
        val out = -x + b
        buf[idx] = x + b * 0.5
        if (++idx >= buf.size) idx = 0
        return out
    }
    fun clear() = buf.fill(0.0)
}

/** Компактный ревербератор по схеме Freeverb: 6 гребёнок + 4 всепропускающих на канал. */
class Reverb {
    private val combTune = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491)
    private val apTune = intArrayOf(556, 441, 341, 225)
    private var combL: Array<Comb> = emptyArray()
    private var combR: Array<Comb> = emptyArray()
    private var apL: Array<Allpass> = emptyArray()
    private var apR: Array<Allpass> = emptyArray()
    private var feedback = 0.84
    private var d1 = 0.3
    private var d2 = 0.7
    var outL = 0.0
    var outR = 0.0

    fun prepare(fs: Int) {
        val s = fs / 44100.0
        combL = Array(combTune.size) { Comb((combTune[it] * s).toInt()) }
        combR = Array(combTune.size) { Comb(((combTune[it] + 23) * s).toInt()) }
        apL = Array(apTune.size) { Allpass((apTune[it] * s).toInt()) }
        apR = Array(apTune.size) { Allpass(((apTune[it] + 23) * s).toInt()) }
    }

    fun set(room: Double, damp: Double) {
        feedback = 0.70 + 0.27 * room.coerceIn(0.0, 1.0)
        d1 = damp.coerceIn(0.0, 0.95)
        d2 = 1 - d1
    }

    fun process(input: Double) {
        val x = input * 0.02
        var l = 0.0
        var r = 0.0
        for (c in combL) l += c.process(x, feedback, d1, d2)
        for (c in combR) r += c.process(x, feedback, d1, d2)
        for (a in apL) l = a.process(l)
        for (a in apR) r = a.process(r)
        outL = l
        outR = r
    }

    fun clear() {
        combL.forEach { it.clear() }; combR.forEach { it.clear() }
        apL.forEach { it.clear() }; apR.forEach { it.clear() }
        outL = 0.0; outR = 0.0
    }
}

/** Стерео-связанный RMS-компрессор с мягким коленом и автокомпенсацией. */
class Compressor {
    private var env = 0.0
    private var att = 0.0
    private var rel = 0.0
    private var thresh = -20.0
    private var ratio = 2.0
    private var makeup = 0.0
    var active = false
        private set

    fun prepare(fs: Double) {
        att = 1 - exp(-1.0 / (0.008 * fs))
        rel = 1 - exp(-1.0 / (0.180 * fs))
    }

    fun set(amount: Double) {
        active = amount > 0.01
        thresh = -12.0 - 18.0 * amount
        ratio = 1.6 + 4.4 * amount
        makeup = -thresh * (1 - 1 / ratio) * 0.42
    }

    fun gain(l: Double, r: Double): Double {
        val x2 = max(l * l, r * r)
        env += (x2 - env) * (if (x2 > env) att else rel)
        val db = 10 * log10(env + 1e-10)
        val over = db - thresh
        val knee = 8.0
        val slope = 1 - 1 / ratio
        val gr = when {
            over <= -knee / 2 -> 0.0
            over >= knee / 2 -> over * slope
            else -> slope * (over + knee / 2) * (over + knee / 2) / (2 * knee)
        }
        return 10.0.pow((makeup - gr) / 20.0)
    }

    fun reset() { env = 0.0 }
}

/**
 * Лимитер с заглядыванием вперёд: удержание минимума усиления, плавный отпуск
 * и сглаживающий фильтр. Пики не пролезают выше потолка, но и не режутся в лоб,
 * поэтому 200% звучат громко и без хрипа.
 */
class Limiter {
    private var d = 1
    private var delayL = DoubleArray(2)
    private var delayR = DoubleArray(2)
    private var dIdx = 0
    private var qv = DoubleArray(4)
    private var qt = LongArray(4)
    private var qHead = 0
    private var qSize = 0
    private var t = 0L
    private var env = 1.0
    private var relCoef = 0.0
    private var box = DoubleArray(2)
    private var boxIdx = 0
    private var boxSum = 0.0
    private var sinceResum = 0
    var ceiling = 0.966
    var gain = 1.0
        private set

    fun prepare(fs: Int) {
        d = max(1, (fs * 0.004).toInt())
        delayL = DoubleArray(d)
        delayR = DoubleArray(d)
        qv = DoubleArray(d + 2)
        qt = LongArray(d + 2)
        box = DoubleArray(d + 1)
        relCoef = 1 - exp(-1.0 / (0.16 * fs))
        reset()
    }

    fun reset() {
        delayL.fill(0.0); delayR.fill(0.0); dIdx = 0
        qHead = 0; qSize = 0; t = 0
        env = 1.0
        box.fill(1.0); boxIdx = 0; boxSum = box.size.toDouble(); sinceResum = 0
        gain = 1.0
    }

    /** Обрабатывает пару сэмплов, результат кладёт в out[0], out[1]. */
    fun process(l: Double, r: Double, out: DoubleArray) {
        t++
        val peak = max(abs(l), abs(r))
        val target = if (peak > ceiling) ceiling / peak else 1.0
        // скользящий минимум по окну d+1 (монотонная очередь)
        val cap = qv.size
        while (qSize > 0) {
            val last = (qHead + qSize - 1) % cap
            if (qv[last] >= target) qSize-- else break
        }
        val ins = (qHead + qSize) % cap
        qv[ins] = target; qt[ins] = t; qSize++
        while (qt[qHead] <= t - (d + 1)) { qHead = (qHead + 1) % cap; qSize-- }
        val hold = qv[qHead]
        env = if (hold < env) hold else env + (hold - env) * relCoef
        boxSum += env - box[boxIdx]
        box[boxIdx] = env
        if (++boxIdx >= box.size) boxIdx = 0
        if (++sinceResum > 48000) {
            sinceResum = 0
            var s = 0.0
            for (v in box) s += v
            boxSum = s
        }
        val g = min(1.0, boxSum / box.size)
        gain = g
        val dl = delayL[dIdx]
        val dr = delayR[dIdx]
        delayL[dIdx] = l
        delayR[dIdx] = r
        if (++dIdx >= d) dIdx = 0
        out[0] = (dl * g).coerceIn(-0.999, 0.999)
        out[1] = (dr * g).coerceIn(-0.999, 0.999)
    }
}

/** 8D: источник медленно кружит вокруг головы — панорама, межушная задержка, «тень» сзади. */
class EightD {
    private var fs = 48000.0
    private var phase = 0.0
    private val buf = DoubleArray(256)
    private var idx = 0
    private var lp = 0.0
    private var lpCoef = 0.3
    var rateHz = 0.11

    fun prepare(sampleRate: Double) {
        fs = sampleRate
        lpCoef = 1 - exp(-2 * PI * 2600.0 / fs)
        reset()
    }

    fun reset() { buf.fill(0.0); idx = 0; lp = 0.0 }

    private fun read(delay: Double): Double {
        val pos = idx - delay
        val i0 = floor(pos).toInt()
        val frac = pos - i0
        val a = buf[i0 and 255]
        val b = buf[(i0 + 1) and 255]
        return a + (b - a) * frac
    }

    fun process(l: Double, r: Double, out: DoubleArray) {
        phase += 2 * PI * rateHz / fs
        if (phase > 2 * PI) phase -= 2 * PI
        val pan = sin(phase)
        val front = cos(phase)
        val mid = (l + r) * 0.5
        val side = (l - r) * 0.5
        lp += (mid - lp) * lpCoef
        val back = max(0.0, -front) * 0.6
        val src = mid * (1 - back) + lp * back
        buf[idx] = src
        val maxD = min(0.00066 * fs, 250.0)
        val sL = read(max(0.0, pan) * maxD)
        val sR = read(max(0.0, -pan) * maxD)
        idx = (idx + 1) and 255
        val ang = (pan + 1) * PI / 4
        val gL = cos(ang) * SQRT2
        val gR = sin(ang) * SQRT2
        out[0] = sL * gL * 0.92 + side * 0.28
        out[1] = sR * gR * 0.92 - side * 0.28
    }

    companion object { private val SQRT2 = sqrt(2.0) }
}

/** Вся цепочка обработки «Грома». */
class DspChain {
    var fs = 44100.0
        private set
    private val eq = Array(10) { Biquad() }
    private val lowCut = Biquad()
    private val highCut = Biquad()
    private val bassLp = Biquad()
    private val reverb = Reverb()
    private val comp = Compressor()
    val limiter = Limiter()
    private val eightD = EightD()
    private var applied: DspParams? = null

    private var preamp = 1.0
    private var gainNow = 2.0
    private var gainTarget = 2.0
    private var fadeNow = 1.0
    private var smooth = 0.001
    private var bassHpPrevX = 0.0
    private var bassHpPrevY = 0.0
    private var bassHpCoef = 0.98
    private var dcPrevXL = 0.0; private var dcPrevYL = 0.0
    private var dcPrevXR = 0.0; private var dcPrevYR = 0.0
    private var reverbMix = 0.0
    private var widthNow = 1.0
    private val tmp = DoubleArray(2)

    fun prepare(sampleRate: Int) {
        fs = sampleRate.toDouble()
        reverb.prepare(sampleRate)
        limiter.prepare(sampleRate)
        comp.prepare(fs)
        eightD.prepare(fs)
        smooth = 1 - exp(-1.0 / (0.025 * fs))
        bassHpCoef = exp(-2 * PI * 150.0 / fs)
        applied = null
        reset()
    }

    fun reset() {
        eq.forEach { it.reset() }
        lowCut.reset(); highCut.reset(); bassLp.reset()
        reverb.clear(); comp.reset(); limiter.reset(); eightD.reset()
        bassHpPrevX = 0.0; bassHpPrevY = 0.0
        dcPrevXL = 0.0; dcPrevYL = 0.0; dcPrevXR = 0.0; dcPrevYR = 0.0
        gainNow = gainTarget
    }

    private fun apply(p: DspParams) {
        for (i in 0 until 10) eq[i].set(Coef.peaking(fs, EQ_FREQS[i].toDouble(), EQ_Q, p.bands[i].toDouble()))
        lowCut.set(if (p.lowCut > 0f) Coef.highpass(fs, p.lowCut.toDouble(), 0.75) else null)
        highCut.set(if (p.highCut > 0f) Coef.lowpass(fs, p.highCut.toDouble(), 0.75) else null)
        bassLp.set(if (p.bass > 0.01f) Coef.lowpass(fs, 120.0) else null)
        comp.set(p.comp.toDouble())
        val rv = if (p.eightD) max(p.reverb, 0.16f) else p.reverb
        reverbMix = rv.toDouble()
        reverb.set(p.room.toDouble(), 0.25 + 0.3 * (1 - p.room))
        preamp = 10.0.pow(p.preampDb / 20.0)
        gainTarget = p.boost.toDouble()
        widthNow = p.width.toDouble()
        limiter.ceiling = 0.966
        applied = p
    }

    /**
     * Обработка блока. l/r — сэмплы в диапазоне [-1, 1], обрабатываются на месте.
     */
    fun process(l: DoubleArray, r: DoubleArray, n: Int, p: DspParams, fadeTarget: Double) {
        if (p !== applied) apply(p)
        val warm = p.warmth.toDouble()
        val drive = 1 + 3 * warm
        val bias = 0.18 * warm
        val tb = tanh(bias)
        val norm = 1.0 / (drive * (1 - tb * tb))
        val warmMix = min(1.0, warm * 1.4)
        val bassAmt = p.bass.toDouble()
        val useLimiter = p.limiter
        val use8d = p.eightD
        for (i in 0 until n) {
            gainNow += (gainTarget - gainNow) * smooth
            fadeNow += (fadeTarget - fadeNow) * smooth
            var x = l[i] * preamp * fadeNow
            var y = r[i] * preamp * fadeNow
            if (lowCut.active) { x = lowCut.left(x); y = lowCut.right(y) }
            if (highCut.active) { x = highCut.left(x); y = highCut.right(y) }
            for (b in eq) if (b.active) { x = b.left(x); y = b.right(y) }
            if (bassAmt > 0.01 && bassLp.active) {
                val low = bassLp.left((x + y) * 0.5)
                val sat = tanh(low * 5.0) * 0.32
                // убираем фундамент из гармоник: ФВЧ 1-го порядка
                val hp = bassHpCoef * (bassHpPrevY + sat - bassHpPrevX)
                bassHpPrevX = sat; bassHpPrevY = hp
                val add = hp * bassAmt * 1.6
                x += add; y += add
            }
            if (warmMix > 0.01) {
                val wx = (tanh(drive * x + bias) - tb) * norm
                val wy = (tanh(drive * y + bias) - tb) * norm
                x += (wx - x) * warmMix
                y += (wy - y) * warmMix
            }
            if (comp.active) {
                val g = comp.gain(x, y)
                x *= g; y *= g
            }
            if (widthNow != 1.0) {
                val m = (x + y) * 0.5
                val s = (x - y) * 0.5 * widthNow
                x = m + s; y = m - s
            }
            if (use8d) {
                eightD.process(x, y, tmp)
                x = tmp[0]; y = tmp[1]
            }
            if (reverbMix > 0.005) {
                reverb.process((x + y) * 0.5)
                x += reverb.outL * reverbMix * 3.0
                y += reverb.outR * reverbMix * 3.0
            }
            x *= gainNow; y *= gainNow
            // блокировка постоянной составляющей
            val ox = x - dcPrevXL + 0.9995 * dcPrevYL
            dcPrevXL = x; dcPrevYL = ox
            val oy = y - dcPrevXR + 0.9995 * dcPrevYR
            dcPrevXR = y; dcPrevYR = oy
            if (useLimiter) {
                limiter.process(ox, oy, tmp)
                l[i] = tmp[0]; r[i] = tmp[1]
            } else {
                l[i] = ox.coerceIn(-1.0, 1.0); r[i] = oy.coerceIn(-1.0, 1.0)
            }
        }
    }
}

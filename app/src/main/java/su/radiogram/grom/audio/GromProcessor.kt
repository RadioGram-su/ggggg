package su.radiogram.grom.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Процессор «Грома»: весь звук проходит через собственную цепочку —
 * эквалайзер, характер, 8D, усиление до 200% и лимитер. Работает одинаково
 * на динамике, проводе и Bluetooth, потому что меняет сам сигнал.
 */
class GromProcessor : BaseAudioProcessor() {
    private val chain = DspChain()
    private var preparedRate = -1
    private var bufL = DoubleArray(4096)
    private var bufR = DoubleArray(4096)
    private var lastPts = Long.MIN_VALUE
    private var framesSincePts = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount < 1) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val channels = fmt.channelCount
        val isFloat = fmt.encoding == C.ENCODING_PCM_FLOAT
        val bps = if (isFloat) 4 else 2
        val frameBytes = bps * channels
        val frames = inputBuffer.remaining() / frameBytes
        if (frames <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        if (preparedRate != fmt.sampleRate) {
            chain.prepare(fmt.sampleRate)
            preparedRate = fmt.sampleRate
        }
        if (bufL.size < frames) {
            bufL = DoubleArray(frames)
            bufR = DoubleArray(frames)
        }
        val src = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        var p = src.position()
        for (i in 0 until frames) {
            if (channels == 1) {
                val s = if (isFloat) src.getFloat(p).toDouble() else src.getShort(p) / 32768.0
                bufL[i] = s; bufR[i] = s
            } else if (channels == 2) {
                if (isFloat) {
                    bufL[i] = src.getFloat(p).toDouble(); bufR[i] = src.getFloat(p + 4).toDouble()
                } else {
                    bufL[i] = src.getShort(p) / 32768.0; bufR[i] = src.getShort(p + 2) / 32768.0
                }
            } else {
                // простое сведение многоканала в стерео: FL FR C LFE BL BR
                fun ch(c: Int): Double = if (c >= channels) 0.0 else if (isFloat) src.getFloat(p + c * 4).toDouble() else src.getShort(p + c * 2) / 32768.0
                val c = ch(2) * 0.707
                bufL[i] = (ch(0) + c + ch(4) * 0.707) * 0.6
                bufR[i] = (ch(1) + c + ch(5) * 0.707) * 0.6
            }
            p += frameBytes
        }
        inputBuffer.position(inputBuffer.position() + frames * frameBytes)

        chain.process(bufL, bufR, frames, AudioEngine.params, AudioEngine.fade.toDouble())

        val out = replaceOutputBuffer(frames * 4)
        for (i in 0 until frames) {
            out.putShort((bufL[i] * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
            out.putShort((bufR[i] * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
        meter(frames, fmt.sampleRate)
    }

    private fun meter(frames: Int, rate: Int) {
        val pts = AudioEngine.sinkPtsUs
        if (pts != lastPts) {
            lastPts = pts
            framesSincePts = 0
        }
        val block = 512
        var start = 0
        while (start < frames) {
            val end = minOf(frames, start + block)
            var sl = 0.0
            var sr = 0.0
            for (i in start until end) {
                sl += bufL[i] * bufL[i]
                sr += bufR[i] * bufR[i]
            }
            val cnt = (end - start).toDouble()
            val t = lastPts + ((framesSincePts + start) * 1_000_000L) / max(1, rate)
            AudioEngine.meter.push(t, sqrt(sl / cnt).toFloat(), sqrt(sr / cnt).toFloat())
            start = end
        }
        framesSincePts += frames
        val g = chain.limiter.gain
        AudioEngine.limiterGainDb = if (g >= 0.9999) 0f else (20 * log10(max(g, 1e-4))).toFloat()
    }

    override fun onFlush() {
        if (preparedRate == inputAudioFormat.sampleRate) chain.reset()
        lastPts = Long.MIN_VALUE
        framesSincePts = 0
    }

    override fun onReset() {
        preparedRate = -1
    }
}

/** Обёртка над системным выводом: отдаёт метки времени для синхронизации VU-метра. */
class MeterSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        AudioEngine.sinkPtsUs = presentationTimeUs
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        AudioEngine.playPositionUs = p
        return p
    }

    override fun flush() {
        super.flush()
        AudioEngine.meter.clear()
    }
}

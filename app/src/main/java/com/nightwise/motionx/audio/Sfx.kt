package com.nightwise.motionx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthesised arcade sound effects (no audio assets) + haptics. */
class Sfx(private val context: Context) {
    enum class S { COIN, STAR, HIT, JUMP, DUCK, TICK, GO, SPEED, OVER }

    var soundOn = true
    var hapticsOn = true

    private val rate = 22050
    private val pcm = HashMap<S, ShortArray>()
    private val exec = Executors.newSingleThreadExecutor()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        exec.execute {
            pcm[S.COIN] = tones(listOf(1318f to 0.05f, 1976f to 0.11f), 0.35f, square = true)
            pcm[S.STAR] = tones(listOf(1046f to 0.06f, 1318f to 0.06f, 1568f to 0.06f, 2093f to 0.18f), 0.35f, square = true)
            pcm[S.HIT] = noiseHit()
            pcm[S.JUMP] = sweep(380f, 900f, 0.16f, 0.3f)
            pcm[S.DUCK] = sweep(700f, 260f, 0.14f, 0.25f)
            pcm[S.TICK] = tones(listOf(880f to 0.12f), 0.35f)
            pcm[S.GO] = tones(listOf(1760f to 0.3f), 0.4f)
            pcm[S.SPEED] = tones(listOf(660f to 0.07f, 990f to 0.07f, 1320f to 0.12f), 0.3f, square = true)
            pcm[S.OVER] = tones(listOf(523f to 0.18f, 392f to 0.18f, 262f to 0.4f), 0.35f, square = true)
        }
    }

    fun play(s: S) {
        if (!soundOn) return
        exec.execute {
            val data = pcm[s] ?: return@execute
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.setNotificationMarkerPosition(data.size - 1)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) = t.release()
                    override fun onPeriodicNotification(t: AudioTrack) = Unit
                })
                track.play()
            } catch (_: Throwable) {
            }
        }
    }

    fun buzz(ms: Long, amplitude: Int = 180) {
        if (!hapticsOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Throwable) {
        }
    }

    fun release() = exec.shutdown()

    private fun tones(notes: List<Pair<Float, Float>>, vol: Float, square: Boolean = false): ShortArray {
        val out = ArrayList<Short>()
        for ((f, d) in notes) {
            val n = (d * rate).toInt()
            for (i in 0 until n) {
                val t = i.toFloat() / rate
                val env = minOf(1f, i / 80f) * exp(-t * 6f)
                var s = sin(2 * PI * f * t).toFloat()
                if (square) s = if (s > 0) 0.6f else -0.6f
                out.add((s * env * vol * Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }

    private fun sweep(f0: Float, f1: Float, d: Float, vol: Float): ShortArray {
        val n = (d * rate).toInt()
        var phase = 0.0
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val f = f0 + (f1 - f0) * p
            phase += 2 * PI * f / rate
            val env = minOf(1f, i / 60f) * (1f - p)
            (sin(phase).toFloat() * env * vol * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun noiseHit(): ShortArray {
        val n = (0.32f * rate).toInt()
        val r = Random(3)
        var phase = 0.0
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            phase += 2 * PI * (140f - 80f * p) / rate
            val s = (r.nextFloat() * 2 - 1) * 0.5f + sin(phase).toFloat() * 0.6f
            (s * (1f - p) * (1f - p) * 0.45f * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

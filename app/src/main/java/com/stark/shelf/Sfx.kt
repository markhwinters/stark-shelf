package com.stark.shelf

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesizes the HUD sounds in code (no audio files) and plays them on the
 * media stream, so they follow your media volume.
 */
class Sfx {
    private val rate = 22050
    private val tracks = HashMap<String, AudioTrack>()

    init {
        add("tick", 1900f, 1500f, 30, 0.45f)
        add("lock", 700f, 1250f, 70, 0.6f)
        add("launch", 500f, 2000f, 240, 0.7f)
        add("boot", 250f, 1400f, 650, 0.6f)
        add("cancel", 500f, 250f, 120, 0.5f)
    }

    private fun add(name: String, f0: Float, f1: Float, ms: Int, vol: Float) {
        try {
            val pcm = synth(f0, f1, ms, vol)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            tracks[name] = t
        } catch (e: Exception) {
        }
    }

    private fun synth(f0: Float, f1: Float, ms: Int, vol: Float): ShortArray {
        val n = rate * ms / 1000
        val out = ShortArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * t
            ph += 2.0 * PI * f / rate
            val env = if (t < 0.1) t / 0.1 else (1.0 - t) / 0.9
            val s = (sin(ph) + 0.35 * sin(2 * ph)) / 1.35
            out[i] = (s * env * env * vol * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    fun play(name: String) {
        val t = tracks[name] ?: return
        try {
            t.stop()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (e: Exception) {
        }
    }

    fun release() {
        tracks.values.forEach {
            try { it.release() } catch (e: Exception) { }
        }
        tracks.clear()
    }
}

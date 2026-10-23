package com.swaystack.app.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundManager {
    var enabled = true
    fun onBlockImpact(event: BlockImpactEvent) {
        if (!enabled) return
        val frequency = when (event.type) { ImpactType.PERFECT -> 330f + event.positionX % 7f * 35f; ImpactType.GOOD -> 250f; ImpactType.MISS -> 145f }
        play(frequency, if (event.type == ImpactType.MISS) 180 else 260, event.type == ImpactType.MISS)
    }
    private fun play(frequency: Float, durationMs: Int, rough: Boolean) {
        Thread {
            try {
                val rate = 22050; val count = rate * durationMs / 1000; val data = ShortArray(count)
                for (i in data.indices) { val t = i.toFloat() / rate; val envelope = exp(-t * if (rough) 14f else 5f); val tone = sin(2 * PI * frequency * t) + if (rough) .28 * sin(2 * PI * frequency * 1.41 * t) else .0; data[i] = (tone * envelope * 2600).toInt().coerceIn(-32768, 32767).toShort() }
                val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(data.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(data, 0, data.size); track.play(); Thread.sleep(durationMs.toLong() + 20); track.release()
            } catch (_: Exception) { }
        }.start()
    }
    fun release() = Unit
}

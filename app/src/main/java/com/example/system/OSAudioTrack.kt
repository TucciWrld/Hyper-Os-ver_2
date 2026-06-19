package com.example.system

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

object OSAudioTrack {

    private const val SAMPLE_RATE = 22050

    /**
     * Synthesizes and plays a custom high-fidelity melody track for the "Hyper OS Startup Sound"
     * Melodic Motif: D# (311Hz) -> F (349Hz) -> A# (466Hz) -> G (392Hz) -> D# (622Hz)
     */
    suspend fun playStartupMelody() = withContext(Dispatchers.Default) {
        val notes = doubleArrayOf(311.13, 349.23, 466.16, 392.00, 622.25)
        val durations = doubleArrayOf(0.25, 0.25, 0.35, 0.35, 0.8) // duration of notes in seconds
        
        val totalLengthSamples = (notes.indices.sumOf { (durations[it] * SAMPLE_RATE) }).toInt()
        val generatedSfx = ShortArray(totalLengthSamples)
        
        var samplePointer = 0
        for (i in notes.indices) {
            val freq = notes[i]
            val durationSec = durations[i]
            val noteSamplesLength = (durationSec * SAMPLE_RATE).toInt()
            
            for (s in 0 until noteSamplesLength) {
                // Sine wave synthesis
                val angle = 2.0 * Math.PI * s / (SAMPLE_RATE / freq)
                val baseValue = sin(angle)
                
                // Add minor harmonics for sub-base and digital warmth
                val doubleHarmonic = sin(angle * 2.0) * 0.2
                val tripleHarmonic = sin(angle * 3.0) * 0.1
                var synthesizedVal = baseValue + doubleHarmonic + tripleHarmonic
                
                // Attack Decay Sustain Release (ADSR) envelope calculation to smooth the sound
                val envelope = when {
                    s < 400 -> s / 400.0 // Quick Attack
                    s > noteSamplesLength - 800 -> (noteSamplesLength - s) / 800.0 // Fade Decay
                    else -> 1.0
                }
                
                // Scale signal to prevent digital clipping
                val sampleValue = (synthesizedVal * 12000.0 * envelope).toInt().coerceIn(-32768, 32767).toShort()
                if (samplePointer < totalLengthSamples) {
                    generatedSfx[samplePointer++] = sampleValue
                }
            }
        }

        try {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedSfx.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(generatedSfx, 0, generatedSfx.size)
            audioTrack.play()
            
            // Release resources after completion
            kotlinx.coroutines.delay(2500)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synthesizes and plays a quick biometric scan completion chime (high pitched resolving beeps).
     */
    suspend fun playScanChime() = withContext(Dispatchers.Default) {
        val notes = doubleArrayOf(523.25, 659.25, 783.99) // C -> E -> G ascending
        val durationSec = 0.12
        val noteSamplesLength = (durationSec * SAMPLE_RATE).toInt()
        val generatedSfx = ShortArray(noteSamplesLength * notes.size)
        
        var samplePointer = 0
        for (freq in notes) {
            for (s in 0 until noteSamplesLength) {
                val angle = 2.0 * Math.PI * s / (SAMPLE_RATE / freq)
                val envelope = if (s > noteSamplesLength - 300) (noteSamplesLength - s) / 300.0 else 1.0
                val sampleValue = (sin(angle) * 14000.0 * envelope).toInt().coerceIn(-32768, 32767).toShort()
                generatedSfx[samplePointer++] = sampleValue
            }
        }
        try {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedSfx.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(generatedSfx, 0, generatedSfx.size)
            audioTrack.play()
            kotlinx.coroutines.delay(600)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

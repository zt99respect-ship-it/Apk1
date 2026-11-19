package com.livetranslator.app.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCaptureManager(private val context: Context) {

  private var captureJob: Job? = null
  private var audioRecord: AudioRecord? = null

  @SuppressLint("MissingPermission")
  fun startPcmStream(
    scope: CoroutineScope,
    mediaProjection: MediaProjection?,
    preferSystemPlayback: Boolean = true,
    maxSpeechWindowSecondsProvider: () -> Int,
    onModeChanged: (String) -> Unit,
    onAudioLevel: (Float) -> Unit,
    onPcmFrame: (ByteArray, Int) -> Unit,
    onNaturalPauseFlush: () -> Unit,
    onError: (String) -> Unit,
  ) {
    stop()
    captureJob =
      scope.launch(Dispatchers.IO) {
        val sampleRate = SAMPLE_RATE_HZ
        val minBuffer =
          AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
          )
        // Small low-latency buffer (~250ms or minBuffer*2) so audio arrives immediately without overrun
        val bufferSize = maxOf(minBuffer * 2, (sampleRate * 2) / 4)

        val record =
          if (preferSystemPlayback) {
            createPlaybackAudioRecord(mediaProjection, bufferSize)?.also {
              onModeChanged("صوت النظام 16kHz")
            }
              ?: createMicAudioRecord(bufferSize)?.also {
                onModeChanged("الميكروفون 16kHz")
              }
          } else {
            createMicAudioRecord(bufferSize)?.also {
              onModeChanged("الميكروفون 16kHz")
            }
              ?: createPlaybackAudioRecord(mediaProjection, bufferSize)?.also {
                onModeChanged("صوت النظام 16kHz")
              }
          }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
          record?.release()
          onError(
            "تعذر تهيئة التقاط الصوت. تأكد من منح إذن التقاط صوت الهاتف أو إذن الميكروفون."
          )
          return@launch
        }

        audioRecord = record
        try {
          record.startRecording()
          // 64ms PCM16 mono frames at 16kHz (1024 samples = 2048 bytes) for millisecond responsiveness
          val readBuffer = ByteArray(FRAME_BYTES)
          var agcGain = 1.0
          var hadSpeechInUtterance = false
          var silenceStartMs = 0L
          var utteranceStartMs = System.currentTimeMillis()
          var lastMeterUpdateMs = 0L

          while (isActive) {
            val read = record.read(readBuffer, 0, readBuffer.size)
            if (read > 0) {
              val frame = readBuffer.copyOf(read)
              val rawRms = computePcm16Rms(frame, read)

              // Adaptive smooth AGC & DC offset cleanup for accurate STT on quiet or loud media
              agcGain = conditionPcm16InPlace(frame, read, rawRms, agcGain)

              val now = System.currentTimeMillis()
              if (now - lastMeterUpdateMs >= 80L) {
                lastMeterUpdateMs = now
                val normalized = (rawRms / 1800.0).toFloat().coerceIn(0f, 1f)
                onAudioLevel(normalized)
              }

              // Dispatch immediately without suspending so hardware AudioRecord never stalls
              onPcmFrame(frame, read)

              // Natural pause VAD tracking to avoid mid-word chopping
              val isSpeechFrame = rawRms >= SPEECH_RMS_THRESHOLD
              if (isSpeechFrame) {
                if (!hadSpeechInUtterance) {
                  utteranceStartMs = now
                }
                hadSpeechInUtterance = true
                silenceStartMs = 0L
              } else if (hadSpeechInUtterance) {
                if (silenceStartMs == 0L) {
                  silenceStartMs = now
                } else if (now - silenceStartMs >= NATURAL_PAUSE_MS) {
                  hadSpeechInUtterance = false
                  silenceStartMs = 0L
                  utteranceStartMs = now
                  onNaturalPauseFlush()
                }
              }

              val maxWindowMs = (maxSpeechWindowSecondsProvider().coerceIn(4, 12)) * 1000L
              if (hadSpeechInUtterance && now - utteranceStartMs >= maxWindowMs) {
                hadSpeechInUtterance = false
                silenceStartMs = 0L
                utteranceStartMs = now
                onNaturalPauseFlush()
              }
            } else if (read < 0) {
              onError("خطأ في قراءة الصوت ($read)")
              break
            }
          }
        } catch (t: Throwable) {
          if (isActive) {
            onError(t.message ?: "انقطع التقاط الصوت")
          }
        } finally {
          onAudioLevel(0f)
          runCatching { record.stop() }
          runCatching { record.release() }
          if (audioRecord === record) {
            audioRecord = null
          }
        }
      }
  }

  @SuppressLint("MissingPermission")
  private fun createPlaybackAudioRecord(
    mediaProjection: MediaProjection?,
    bufferSize: Int,
  ): AudioRecord? {
    if (mediaProjection == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return null
    }
    return runCatching {
        val captureConfig =
          AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat =
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        AudioRecord.Builder()
          .setAudioFormat(audioFormat)
          .setBufferSizeInBytes(bufferSize)
          .setAudioPlaybackCaptureConfig(captureConfig)
          .build()
      }
      .getOrNull()
      ?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
  }

  @SuppressLint("MissingPermission")
  private fun createMicAudioRecord(bufferSize: Int): AudioRecord? {
    val hasMic =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) return null

    return runCatching {
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize,
        )
      }
      .getOrNull()
      ?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
      ?: runCatching {
          AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
          )
        }
        .getOrNull()
        ?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
  }

  fun stop() {
    captureJob?.cancel()
    captureJob = null
    runCatching { audioRecord?.stop() }
    runCatching { audioRecord?.release() }
    audioRecord = null
  }

  companion object {
    const val SAMPLE_RATE_HZ = 16_000
    private const val FRAME_BYTES = 2048 // 64ms @ 16kHz 16-bit mono
    private const val SPEECH_RMS_THRESHOLD = 140.0
    private const val NATURAL_PAUSE_MS = 450L

    fun computePcm16Rms(pcmBytes: ByteArray, length: Int = pcmBytes.size): Double {
      val safeLength = length.coerceAtMost(pcmBytes.size)
      val sampleCount = safeLength / 2
      if (sampleCount == 0) return 0.0
      var sumSquares = 0.0
      var i = 0
      while (i + 1 < safeLength) {
        val low = pcmBytes[i].toInt() and 0xFF
        val high = pcmBytes[i + 1].toInt()
        val sample = (high shl 8) or low
        sumSquares += sample.toDouble() * sample.toDouble()
        i += 2
      }
      return sqrt(sumSquares / sampleCount)
    }

    /**
     * Removes DC offset and applies gentle Automatic Gain Control + soft limiting in place,
     * improving STT recognition on quiet streams without clipping loud speech.
     */
    internal fun conditionPcm16InPlace(
      pcmBytes: ByteArray,
      length: Int,
      rawRms: Double,
      previousGain: Double,
    ): Double {
      val safeLength = length.coerceAtMost(pcmBytes.size)
      val sampleCount = safeLength / 2
      if (sampleCount <= 0) return previousGain

      // Target ~3400 RMS for active speech, cap boost at 4.0x to avoid amplifying pure noise floor
      val targetGain =
        if (rawRms > 80.0) {
          (3400.0 / rawRms).coerceIn(0.65, 4.0)
        } else {
          1.0
        }
      val smoothedGain = (previousGain * 0.72) + (targetGain * 0.28)

      // Compute frame DC mean
      var sum = 0L
      var i = 0
      while (i + 1 < safeLength) {
        val sample = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF))
        sum += sample
        i += 2
      }
      val mean = (sum / sampleCount).toInt()

      if (abs(mean) < 12 && abs(smoothedGain - 1.0) < 0.08) {
        return smoothedGain
      }

      i = 0
      while (i + 1 < safeLength) {
        val sample = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)) - mean
        var scaled = (sample * smoothedGain).toInt()
        // Soft knee limiter near 16-bit bounds
        if (scaled > 28000) {
          scaled = 28000 + ((scaled - 28000) / 4)
        } else if (scaled < -28000) {
          scaled = -28000 + ((scaled + 28000) / 4)
        }
        val clamped = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcmBytes[i] = (clamped and 0xFF).toByte()
        pcmBytes[i + 1] = ((clamped shr 8) and 0xFF).toByte()
        i += 2
      }
      return smoothedGain
    }
  }
}

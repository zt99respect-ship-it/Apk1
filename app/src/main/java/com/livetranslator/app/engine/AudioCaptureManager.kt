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
    chunkSecondsProvider: () -> Int,
    onModeChanged: (String) -> Unit,
    onAudioLevel: (Float) -> Unit,
    onPcmFrame: suspend (ByteArray, Int) -> Unit,
    onChunkWindowElapsed: suspend () -> Unit,
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
        val bufferSize = maxOf(minBuffer * 2, sampleRate * 2)

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
          val readBuffer = ByteArray(4096)
          var bytesSinceWindowFlush = 0
          var lastMeterUpdateMs = 0L

          while (isActive) {
            val targetWindowBytes = (chunkSecondsProvider().coerceIn(2, 15)) * SAMPLE_RATE_HZ * 2
            val read = record.read(readBuffer, 0, readBuffer.size)
            if (read > 0) {
              val now = System.currentTimeMillis()
              if (now - lastMeterUpdateMs >= 120L) {
                lastMeterUpdateMs = now
                val frameRms = computePcm16Rms(readBuffer, read)
                val normalized = (frameRms / 2200.0).toFloat().coerceIn(0f, 1f)
                onAudioLevel(normalized)
              }

              onPcmFrame(readBuffer.copyOf(read), read)
              bytesSinceWindowFlush += read
              if (bytesSinceWindowFlush >= targetWindowBytes) {
                bytesSinceWindowFlush = 0
                onChunkWindowElapsed()
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
  }
}

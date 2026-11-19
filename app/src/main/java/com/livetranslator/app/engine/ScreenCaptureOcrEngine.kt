package com.livetranslator.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Screen OCR engine using MediaProjection VirtualDisplay + ImageReader + ML Kit TextRecognition.
 * Excludes this app's own floating overlay bounds so Arabic subtitles never trigger an infinite self-read loop.
 */
class ScreenCaptureOcrEngine(private val context: Context) {

  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var scanJob: Job? = null

  private val excludedRects = CopyOnWriteArrayList<Rect>()
  private var captureWidth: Int = 720
  private var captureHeight: Int = 1280
  private var scaleFactorToScreen: Float = 1f

  @Volatile private var forceSnapRequested = false

  private val textRecognizer: TextRecognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  fun updateExcludedOverlayRects(rects: List<Rect>) {
    excludedRects.clear()
    excludedRects.addAll(rects)
  }

  fun requestImmediateSnap() {
    forceSnapRequested = true
  }

  @SuppressLint("WrongConstant")
  fun startScreenOcrLoop(
    scope: CoroutineScope,
    mediaProjection: MediaProjection,
    intervalMsProvider: () -> Long,
    onExtractedScreenText: suspend (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    stop()

    val metrics = context.resources.displayMetrics
    val fullW = metrics.widthPixels.coerceAtLeast(360)
    val fullH = metrics.heightPixels.coerceAtLeast(640)
    val targetW = fullW.coerceAtMost(960)
    scaleFactorToScreen = fullW.toFloat() / targetW.toFloat()
    val targetH = ((fullH.toFloat() / fullW.toFloat()) * targetW).toInt().coerceAtLeast(360)

    captureWidth = targetW
    captureHeight = targetH

    val reader =
      runCatching {
          ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
        }
        .getOrElse {
          onError("تعذر تهيئة التقاط الشاشة")
          return
        }

    val display =
      runCatching {
          mediaProjection.createVirtualDisplay(
            "InstantScreenTranslatorDisplay",
            targetW,
            targetH,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
          )
        }
        .getOrElse {
          runCatching { reader.close() }
          onError("تعذر فتح عرض الشاشة الافتراضي")
          return
        }

    imageReader = reader
    virtualDisplay = display

    scanJob =
      scope.launch(Dispatchers.Default) {
        var lastFrameSignature = 0L
        var lastRecognizedText = ""

        while (isActive) {
          val forced = forceSnapRequested
          forceSnapRequested = false

          val bitmap = acquireLatestBitmap(reader)
          if (bitmap != null) {
            try {
              val sig = computeFrameSignature(bitmap)
              if (forced || sig != lastFrameSignature) {
                lastFrameSignature = sig
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = runCatching { textRecognizer.process(inputImage).awaitResult() }.getOrNull()
                if (visionText != null) {
                  val extracted = extractReadableForeignLines(visionText)
                  if (extracted.isNotBlank() && (forced || extracted != lastRecognizedText)) {
                    lastRecognizedText = extracted
                    onExtractedScreenText(extracted)
                  }
                }
              }
            } finally {
              bitmap.recycle()
            }
          }

          val stepMs = intervalMsProvider().coerceIn(450L, 2500L)
          var slept = 0L
          while (isActive && slept < stepMs && !forceSnapRequested) {
            delay(120L)
            slept += 120L
          }
        }
      }
  }

  private fun acquireLatestBitmap(reader: ImageReader): Bitmap? {
    val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return null
    return try {
      val plane = image.planes.firstOrNull() ?: return null
      val buffer = plane.buffer
      val pixelStride = plane.pixelStride
      val rowStride = plane.rowStride
      val rowPadding = rowStride - pixelStride * captureWidth
      val paddedBitmap =
        Bitmap.createBitmap(
          captureWidth + rowPadding / pixelStride,
          captureHeight,
          Bitmap.Config.ARGB_8888,
        )
      paddedBitmap.copyPixelsFromBuffer(buffer)
      if (rowPadding == 0) {
        paddedBitmap
      } else {
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, captureWidth, captureHeight)
        paddedBitmap.recycle()
        cropped
      }
    } catch (_: Throwable) {
      null
    } finally {
      runCatching { image.close() }
    }
  }

  private fun extractReadableForeignLines(visionText: Text): String {
    val blocks = visionText.textBlocks
    if (blocks.isEmpty()) return ""

    val keptLines = ArrayList<String>()
    for (block in blocks) {
      val box = block.boundingBox
      if (box != null && overlapsExcludedOverlay(box)) {
        continue
      }
      val blockText = block.text.trim()
      if (blockText.length < 2) continue
      if (MlKitTranslationEngine.isMostlyArabic(blockText)) continue
      if (!containsMeaningfulWord(blockText)) continue
      keptLines.add(blockText.replace("\n", " "))
    }
    return keptLines.joinToString("\n").take(650).trim()
  }

  private fun overlapsExcludedOverlay(captureRect: Rect): Boolean {
    if (excludedRects.isEmpty()) return false
    val screenRect =
      Rect(
        (captureRect.left * scaleFactorToScreen).toInt(),
        (captureRect.top * scaleFactorToScreen).toInt(),
        (captureRect.right * scaleFactorToScreen).toInt(),
        (captureRect.bottom * scaleFactorToScreen).toInt(),
      )
    return excludedRects.any { Rect.intersects(screenRect, it) }
  }

  fun stop() {
    scanJob?.cancel()
    scanJob = null
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
  }

  companion object {
    internal fun computeFrameSignature(bitmap: Bitmap): Long {
      val w = bitmap.width
      val h = bitmap.height
      if (w <= 0 || h <= 0) return 0L
      var hash = 17L
      val stepX = (w / 10).coerceAtLeast(1)
      val stepY = (h / 14).coerceAtLeast(1)
      var y = stepY / 2
      while (y < h) {
        var x = stepX / 2
        while (x < w) {
          val pixel = bitmap.getPixel(x, y)
          hash = hash * 31L + (pixel ushr 4).toLong()
          x += stepX
        }
        y += stepY
      }
      return hash
    }

    internal fun containsMeaningfulWord(text: String): Boolean {
      var letterCount = 0
      for (ch in text) {
        if (ch.isLetter()) letterCount++
      }
      return letterCount >= 2
    }
  }
}

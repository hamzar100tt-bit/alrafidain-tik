package com.alrafidain.tik

import android.graphics.ImageFormat
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import livekit.org.webrtc.JavaI420Buffer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * On-device face-aware beauty processor for the outgoing LiveKit camera track.
 * ML Kit detects faces locally; luminance softening is limited to the detected face oval.
 * No camera frames are uploaded to a third-party service by this processor.
 */
object LiveBeautySettings {
    @Volatile var smoothing: Float = 0.30f
    @Volatile var brightness: Float = 0.015f
    @Volatile var warmth: Float = 0f
    @Volatile var tone: Int = 0
    @Volatile var lipstick: Float = 0f
    @Volatile var blush: Float = 0f
    @Volatile var eyeShadow: Float = 0f
}

class LiveBeautyVideoProcessor : VideoProcessor {
    @Volatile private var output: VideoSink? = null
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
    )

    override fun setSink(sink: VideoSink?) { output = sink }
    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val sink = output ?: return
        val source = frame.buffer.toI420()
        if (source == null) {
            sink.onFrame(frame)
            return
        }
        var result: JavaI420Buffer? = null
        try {
            val w = source.width
            val h = source.height
            val smoothing = LiveBeautySettings.smoothing.coerceIn(0f, 0.55f)
            val lift = (LiveBeautySettings.brightness.coerceIn(-0.08f, 0.12f) * 255f).toInt()
            val faceRects = if (smoothing > 0.001f || LiveBeautySettings.lipstick > 0f || LiveBeautySettings.blush > 0f || LiveBeautySettings.eyeShadow > 0f) detectFaces(source, frame.rotation) else emptyList()
            if (smoothing <= 0.001f && lift == 0 && LiveBeautySettings.warmth == 0f && LiveBeautySettings.tone == 0 && LiveBeautySettings.lipstick == 0f && LiveBeautySettings.blush == 0f && LiveBeautySettings.eyeShadow == 0f) {
                sink.onFrame(frame)
                return
            }

            result = JavaI420Buffer.allocate(w, h)
            val inputY = ByteArray(w * h)
            val outputY = result.dataY
            for (row in 0 until h) {
                for (col in 0 until w) {
                    inputY[row * w + col] = source.dataY.get(row * source.strideY + col)
                }
            }

            // Smooth only inside AI-detected face ovals. Background remains untouched.
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val original = inputY[row * w + col].toInt() and 0xff
                    var amount = 0f
                    if (smoothing > 0f) {
                        for (face in faceRects) {
                            val cx = (face.left + face.right) * 0.5f
                            val cy = face.top + (face.bottom - face.top) * 0.48f
                            val rx = max(1f, face.width() * 0.48f)
                            val ry = max(1f, face.height() * 0.52f)
                            val nx = (col - cx) / rx
                            val ny = (row - cy) / ry
                            val distance = nx * nx + ny * ny
                            if (distance < 1f) {
                                val edge = ((1f - distance) / 0.22f).coerceIn(0f, 1f)
                                amount = max(amount, smoothing * edge)
                            }
                        }
                    }
                    val value = if (amount > 0f) {
                        val total = neighborhoodAverage(inputY, w, h, col, row)
                        (original * (1f - amount) + total * amount + lift).toInt().coerceIn(0, 255)
                    } else (original + lift).coerceIn(0, 255)
                    outputY.put(row * result.strideY + col, value.toByte())
                }
            }

            val chromaW = (w + 1) / 2
            val chromaH = (h + 1) / 2
            val warmth = LiveBeautySettings.warmth.coerceIn(-0.35f, 0.35f)
            val toneU = when (LiveBeautySettings.tone) { 1 -> -5; 2 -> 5; 3 -> -3; else -> 0 }
            val toneV = when (LiveBeautySettings.tone) { 1 -> 7; 2 -> -5; 3 -> 9; else -> 0 }
            for (row in 0 until chromaH) for (col in 0 until chromaW) {
                var u = source.dataU.get(row * source.strideU + col).toInt() and 0xff
                var v = source.dataV.get(row * source.strideV + col).toInt() and 0xff
                u = (u + toneU - warmth * 9f).toInt()
                v = (v + toneV + warmth * 8f).toInt()
                val px = col * 2f
                val py = row * 2f
                for (face in faceRects) {
                    val fw = face.width().toFloat()
                    val fh = face.height().toFloat()
                    val lip = ellipse(px, py, (face.left + face.right) * 0.5f, face.top + fh * 0.73f, fw * 0.16f, fh * 0.055f)
                    val cheekL = ellipse(px, py, face.left + fw * 0.28f, face.top + fh * 0.64f, fw * 0.16f, fh * 0.09f)
                    val cheekR = ellipse(px, py, face.left + fw * 0.72f, face.top + fh * 0.64f, fw * 0.16f, fh * 0.09f)
                    val eyeL = ellipse(px, py, face.left + fw * 0.32f, face.top + fh * 0.43f, fw * 0.17f, fh * 0.055f)
                    val eyeR = ellipse(px, py, face.left + fw * 0.68f, face.top + fh * 0.43f, fw * 0.17f, fh * 0.055f)
                    val lm = (lip * LiveBeautySettings.lipstick.coerceIn(0f, 1f)).coerceIn(0f, 0.7f)
                    val bm = (max(cheekL, cheekR) * LiveBeautySettings.blush.coerceIn(0f, 1f)).coerceIn(0f, 0.5f)
                    val em = (max(eyeL, eyeR) * LiveBeautySettings.eyeShadow.coerceIn(0f, 1f)).coerceIn(0f, 0.4f)
                    val totalMix = (lm + bm * 0.45f + em * 0.15f).coerceAtMost(0.85f)
                    u = (u * (1f - totalMix) + 154f * lm + 112f * bm * 0.45f + 145f * em * 0.15f).toInt()
                    v = (v * (1f - totalMix) + 145f * lm + 164f * bm * 0.45f + 105f * em * 0.15f).toInt()
                }
                result.dataU.put(row * result.strideU + col, u.coerceIn(0, 255).toByte())
                result.dataV.put(row * result.strideV + col, v.coerceIn(0, 255).toByte())
            }

            val processed = VideoFrame(result, frame.rotation, frame.timestampNs)
            sink.onFrame(processed)
            processed.release()
            result = null
        } catch (_: Throwable) {
            sink.onFrame(frame)
        } finally {
            result?.release()
            source.release()
        }
    }

    private fun detectFaces(buffer: livekit.org.webrtc.VideoFrame.I420Buffer, rotation: Int): List<Rect> {
        val width = buffer.width
        val height = buffer.height
        val nv21 = ByteArray(width * height + (width * height / 2))
        var offset = 0
        for (row in 0 until height) for (col in 0 until width) {
            nv21[offset++] = buffer.dataY.get(row * buffer.strideY + col)
        }
        for (row in 0 until (height + 1) / 2) for (col in 0 until (width + 1) / 2) {
            nv21[offset++] = buffer.dataV.get(row * buffer.strideV + col)
            nv21[offset++] = buffer.dataU.get(row * buffer.strideU + col)
        }
        val image = InputImage.fromByteArray(nv21, width, height, rotation, ImageFormat.NV21)
        val faces = try {
            Tasks.await(detector.process(image), 35, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            emptyList()
        }
        return faces.mapNotNull { face ->
            rotateRectToBuffer(face.boundingBox, width, height, rotation)
        }
    }

    private fun rotateRectToBuffer(rect: Rect, width: Int, height: Int, rotation: Int): Rect? {
        val points = listOf(
            Pair(rect.left.toFloat(), rect.top.toFloat()),
            Pair(rect.right.toFloat(), rect.top.toFloat()),
            Pair(rect.left.toFloat(), rect.bottom.toFloat()),
            Pair(rect.right.toFloat(), rect.bottom.toFloat())
        ).map { (x, y) ->
            when ((rotation % 360 + 360) % 360) {
                90 -> Pair(y, height - x)
                180 -> Pair(width - x, height - y)
                270 -> Pair(width - y, x)
                else -> Pair(x, y)
            }
        }
        val left = points.minOf { it.first }.toInt().coerceIn(0, width - 1)
        val top = points.minOf { it.second }.toInt().coerceIn(0, height - 1)
        val right = points.maxOf { it.first }.toInt().coerceIn(left + 1, width)
        val bottom = points.maxOf { it.second }.toInt().coerceIn(top + 1, height)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }

    private fun ellipse(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        if (rx <= 0f || ry <= 0f) return 0f
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return ((1f - dx * dx - dy * dy) / 0.35f).coerceIn(0f, 1f)
    }

    private fun neighborhoodAverage(pixels: ByteArray, width: Int, height: Int, x: Int, y: Int): Int {
        var total = 0
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = (x + dx).coerceIn(0, width - 1)
            val yy = (y + dy).coerceIn(0, height - 1)
            total += pixels[yy * width + xx].toInt() and 0xff
            count++
        }
        return total / max(1, count)
    }
}

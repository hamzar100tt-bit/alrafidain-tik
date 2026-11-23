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
    @Volatile var noseSlim: Float = 0f
    @Volatile var jawSlim: Float = 0f
}

class LiveBeautyVideoProcessor : VideoProcessor {
    @Volatile private var output: VideoSink? = null
    private data class FaceMesh(val bounds: Rect, val nose: List<android.graphics.PointF>, val faceOval: List<android.graphics.PointF>, val upperLip: List<android.graphics.PointF>, val lowerLip: List<android.graphics.PointF>, val leftEye: List<android.graphics.PointF>, val rightEye: List<android.graphics.PointF>)

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
            val faceMeshes = if (smoothing > 0.001f || LiveBeautySettings.lipstick > 0f || LiveBeautySettings.blush > 0f || LiveBeautySettings.eyeShadow > 0f || LiveBeautySettings.noseSlim > 0f || LiveBeautySettings.jawSlim > 0f) detectFaces(source, frame.rotation) else emptyList()
            val faceRects = faceMeshes.map { it.bounds }
            if (smoothing <= 0.001f && lift == 0 && LiveBeautySettings.warmth == 0f && LiveBeautySettings.tone == 0 && LiveBeautySettings.lipstick == 0f && LiveBeautySettings.blush == 0f && LiveBeautySettings.eyeShadow == 0f && LiveBeautySettings.noseSlim == 0f && LiveBeautySettings.jawSlim == 0f) {
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
                    val warp = displacement(col.toFloat(), row.toFloat(), faceMeshes)
                    val original = bilinear(inputY, w, h, (col + warp.first).coerceIn(0f, (w - 1).toFloat()), (row + warp.second).coerceIn(0f, (h - 1).toFloat()))
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
                for (mesh in faceMeshes) {
                    val face = mesh.bounds
                    val fw = face.width().toFloat()
                    val fh = face.height().toFloat()
                    val lip = max(contourInfluence(px, py, mesh.upperLip, fw * 0.05f), contourInfluence(px, py, mesh.lowerLip, fw * 0.05f))
                    val cheekL = ellipse(px, py, face.left + fw * 0.28f, face.top + fh * 0.64f, fw * 0.16f, fh * 0.09f)
                    val cheekR = ellipse(px, py, face.left + fw * 0.72f, face.top + fh * 0.64f, fw * 0.16f, fh * 0.09f)
                    val eyeL = contourInfluence(px, py, mesh.leftEye, fw * 0.06f)
                    val eyeR = contourInfluence(px, py, mesh.rightEye, fw * 0.06f)
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

    private fun detectFaces(buffer: livekit.org.webrtc.VideoFrame.I420Buffer, rotation: Int): List<FaceMesh> {
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
            val bounds = rotateRectToBuffer(face.boundingBox, width, height, rotation) ?: return@mapNotNull null
            fun contour(type: Int): List<android.graphics.PointF> =
                face.getContour(type)?.points?.map { p -> rotatePoint(p.x, p.y, width, height, rotation) } ?: emptyList()
            FaceMesh(
                bounds,
                contour(com.google.mlkit.vision.face.FaceContour.NOSE_BRIDGE) + contour(com.google.mlkit.vision.face.FaceContour.NOSE_BOTTOM),
                contour(com.google.mlkit.vision.face.FaceContour.FACE),
                contour(com.google.mlkit.vision.face.FaceContour.UPPER_LIP_TOP),
                contour(com.google.mlkit.vision.face.FaceContour.LOWER_LIP_BOTTOM),
                contour(com.google.mlkit.vision.face.FaceContour.LEFT_EYE),
                contour(com.google.mlkit.vision.face.FaceContour.RIGHT_EYE)
            )
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


    private fun rotatePoint(x: Float, y: Float, width: Int, height: Int, rotation: Int): android.graphics.PointF {
        val p = when ((rotation % 360 + 360) % 360) {
            90 -> android.graphics.PointF(y, height - x)
            180 -> android.graphics.PointF(width - x, height - y)
            270 -> android.graphics.PointF(width - y, x)
            else -> android.graphics.PointF(x, y)
        }
        return android.graphics.PointF(p.x.coerceIn(0f, (width - 1).toFloat()), p.y.coerceIn(0f, (height - 1).toFloat()))
    }

    private fun contourInfluence(x: Float, y: Float, points: List<android.graphics.PointF>, radius: Float): Float {
        if (points.isEmpty()) return 0f
        var best = Float.MAX_VALUE
        for (p in points) {
            val dx = x - p.x
            val dy = y - p.y
            best = min(best, dx * dx + dy * dy)
        }
        return (1f - kotlin.math.sqrt(best) / max(1f, radius)).coerceIn(0f, 1f)
    }

    private fun displacement(x: Float, y: Float, meshes: List<FaceMesh>): Pair<Float, Float> {
        var dx = 0f
        for (mesh in meshes) {
            val b = mesh.bounds
            val fw = b.width().toFloat()
            val fh = b.height().toFloat()
            if (LiveBeautySettings.noseSlim > 0f && mesh.nose.isNotEmpty()) {
                val cx = mesh.nose.map { it.x }.average().toFloat()
                val cy = mesh.nose.map { it.y }.average().toFloat()
                val rx = max(1f, fw * 0.20f)
                val ry = max(1f, fh * 0.24f)
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val falloff = kotlin.math.exp(-(nx * nx + ny * ny) * 2f)
                dx += nx * fw * 0.035f * LiveBeautySettings.noseSlim.coerceIn(0f, 1f) * falloff
            }
            if (LiveBeautySettings.jawSlim > 0f && mesh.faceOval.size >= 8) {
                val center = (b.left + b.right) * 0.5f
                val t = ((y - (b.top + fh * 0.58f)) / max(1f, fh * 0.42f)).coerceIn(0f, 1f)
                val side = (x - center) / max(1f, fw * 0.5f)
                dx += side * fw * 0.025f * LiveBeautySettings.jawSlim.coerceIn(0f, 1f) * kotlin.math.sin(t * Math.PI).toFloat()
            }
        }
        return Pair(dx, 0f)
    }

    private fun bilinear(pixels: ByteArray, width: Int, height: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0
        fun at(px: Int, py: Int) = pixels[py * width + px].toInt() and 0xff
        val top = at(x0, y0) * (1f - fx) + at(x1, y0) * fx
        val bottom = at(x0, y1) * (1f - fx) + at(x1, y1) * fx
        return (top * (1f - fy) + bottom * fy).toInt().coerceIn(0, 255)
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

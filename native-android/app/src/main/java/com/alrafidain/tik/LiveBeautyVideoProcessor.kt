package com.alrafidain.tik

import livekit.org.webrtc.JavaI420Buffer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight real-time softening processor for the outgoing LiveKit camera track.
 * It smooths luminance slightly while preserving chroma and frame timing.
 * This is a skin-softening base effect, not AI face reshaping.
 */
object LiveBeautySettings {
    @Volatile var smoothing: Float = 0.30f
    @Volatile var brightness: Float = 0.015f
    @Volatile var warmth: Float = 0f
    @Volatile var tone: Int = 0
}

class LiveBeautyVideoProcessor : VideoProcessor {
    @Volatile private var output: VideoSink? = null

    override fun setSink(sink: VideoSink?) {
        output = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val sink = output ?: return
        val source = frame.buffer.toI420()
        if (source == null || LiveBeautySettings.smoothing <= 0.001f) {
            sink.onFrame(frame)
            source?.release()
            return
        }

        try {
            val w = source.width
            val h = source.height
            val result = JavaI420Buffer.allocate(w, h)
            val yIn = source.dataY
            val yOut = result.dataY
            val amount = LiveBeautySettings.smoothing.coerceIn(0f, 0.55f)
            val lift = (LiveBeautySettings.brightness.coerceIn(-0.08f, 0.12f) * 255f).toInt()
            val src = ByteArray(w * h)
            for (row in 0 until h) {
                for (col in 0 until w) {
                    src[row * w + col] = yIn.get(row * source.strideY + col)
                }
            }
            for (row in 0 until h) {
                for (col in 0 until w) {
                    var total = 0
                    var count = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        val yy = row + dy
                        val xx = col + dx
                        if (yy in 0 until h && xx in 0 until w) {
                            total += src[yy * w + xx].toInt() and 0xff
                            count++
                        }
                    }
                    val original = src[row * w + col].toInt() and 0xff
                    val blurred = total / max(1, count)
                    val value = (original * (1f - amount) + blurred * amount + lift).toInt().coerceIn(0, 255)
                    yOut.put(row * result.strideY + col, value.toByte())
                }
            }
            val chromaW = (w + 1) / 2
            val chromaH = (h + 1) / 2
            val warmth = LiveBeautySettings.warmth.coerceIn(-0.35f, 0.35f)
            val toneU = when (LiveBeautySettings.tone) { 1 -> -5; 2 -> 5; 3 -> -3; else -> 0 }
            val toneV = when (LiveBeautySettings.tone) { 1 -> 7; 2 -> -5; 3 -> 9; else -> 0 }
            for (row in 0 until chromaH) for (col in 0 until chromaW) {
                val u = source.dataU.get(row * source.strideU + col).toInt() and 0xff
                val v = source.dataV.get(row * source.strideV + col).toInt() and 0xff
                result.dataU.put(row * result.strideU + col, (u + toneU - warmth * 9f).toInt().coerceIn(0, 255).toByte())
                result.dataV.put(row * result.strideV + col, (v + toneV + warmth * 8f).toInt().coerceIn(0, 255).toByte())
            }
            val processed = VideoFrame(result, frame.rotation, frame.timestampNs)
            sink.onFrame(processed)
            processed.release()
        } catch (_: Throwable) {
            sink.onFrame(frame)
        } finally {
            source.release()
        }
    }

    private fun copyPlane(input: java.nio.ByteBuffer, inputStride: Int, output: java.nio.ByteBuffer,
                          outputStride: Int, width: Int, height: Int) {
        for (row in 0 until height) for (col in 0 until width) {
            output.put(row * outputStride + col, input.get(row * inputStride + col))
        }
    }
}

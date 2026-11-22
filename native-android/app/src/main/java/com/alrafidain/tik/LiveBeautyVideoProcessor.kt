package com.alrafidain.tik

import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight real-time softening processor for the outgoing LiveKit camera track.
 * It smooths luminance slightly while preserving chroma and frame timing.
 * This is a skin-softening base effect, not AI face reshaping.
 */
class LiveBeautyVideoProcessor(
    @Volatile var smoothing: Float = 0.28f,
    @Volatile var brightness: Float = 0.02f
) : VideoProcessor {
    @Volatile private var output: VideoSink? = null

    override fun setSink(sink: VideoSink?) {
        output = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val sink = output ?: return
        val source = frame.buffer.toI420()
        if (source == null || smoothing <= 0.001f) {
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
            val amount = smoothing.coerceIn(0f, 0.55f)
            val lift = (brightness.coerceIn(-0.08f, 0.12f) * 255f).toInt()
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
            copyPlane(source.dataU, source.strideU, result.dataU, result.strideU, (w + 1) / 2, (h + 1) / 2)
            copyPlane(source.dataV, source.strideV, result.dataV, result.strideV, (w + 1) / 2, (h + 1) / 2)
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

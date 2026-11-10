package com.alrafidain.tik

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Effects
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
suspend fun exportEditedVideo(
    context: Context,
    source: Uri,
    music: Uri?,
    filter: String
): Uri = suspendCancellableCoroutine { continuation ->
    try {
        val videoEffects: List<Effect> = when (filter) {
            "أبيض وأسود", "رمادي" -> listOf(RgbFilter.createGrayscaleFilter())
            "دافئ" -> listOf(rgb(1.12f, 1.02f, 0.88f))
            "ذهبي" -> listOf(rgb(1.18f, 1.08f, 0.78f))
            "بارد" -> listOf(rgb(0.90f, 1.02f, 1.18f))
            "زاهي" -> listOf(rgb(1.22f, 1.22f, 1.22f))
            "سينما" -> listOf(rgb(1.08f, 1.02f, 0.92f))
            "وردي" -> listOf(rgb(1.12f, 0.96f, 1.08f))
            "قديم" -> listOf(rgb(0.96f, 0.92f, 0.78f))
            "ليلي" -> listOf(rgb(0.82f, 0.90f, 1.12f))
            "ناعم" -> listOf(rgb(1.04f, 1.02f, 1.00f))
            else -> emptyList()
        }
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val videoSequence = EditedMediaItemSequence.Builder(videoItem).build()
        val sequences = mutableListOf(videoSequence)
        if (music != null) {
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(music))
                .setRemoveVideo(true)
                .build()
            sequences += EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build()
        }
        val composition = Composition.Builder(sequences).build()
        val output = File(context.cacheDir, "alrafidain_edit_${System.currentTimeMillis()}.mp4")
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Uri.fromFile(output))
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IOException("تعذر تجهيز الفيديو: ${exportException.message}", exportException)
                    )
                }
            })
            .build()
        continuation.invokeOnCancellation { transformer.cancel() }
        transformer.start(composition, output.absolutePath)
    } catch (e: Exception) {
        if (continuation.isActive) continuation.resumeWithException(e)
    }
}

@OptIn(UnstableApi::class)
private fun rgb(red: Float, green: Float, blue: Float): RgbMatrix {
    val matrix = floatArrayOf(
        red, 0f, 0f, 0f,
        0f, green, 0f, 0f,
        0f, 0f, blue, 0f,
        0f, 0f, 0f, 1f
    )
    return RgbMatrix { _, _ -> matrix }
}

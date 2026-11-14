package com.alrafidain.tik

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OpenMusicTrack(
    val id: String, val title: String, val creator: String, val url: String,
    val landingUrl: String, val license: String, val licenseVersion: String,
    val licenseUrl: String, val attribution: String, val durationMs: Long
)

object OpenMusicLibrary {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    fun search(query: String): List<OpenMusicTrack> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val endpoint = "https://api.openverse.org/v1/audio/?q=$q&license=cc0,by,by-sa,pdm&category=music&page_size=30&filter_dead=true"
        val request = Request.Builder().url(endpoint).header("User-Agent", "AlrafidainTik/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("مكتبة الموسيقى غير متاحة حالياً")
            val root = JSONObject(response.body?.string().orEmpty())
            val results = root.optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    val license = item.optString("license").lowercase()
                    if (url.isBlank() || license !in setOf("cc0", "by", "by-sa", "pdm")) continue
                    add(OpenMusicTrack(
                        item.optString("id"), item.optString("title", "موسيقى بدون عنوان"),
                        item.optString("creator", "فنان غير معروف"), url,
                        item.optString("foreign_landing_url"), license.uppercase(),
                        item.optString("license_version"), item.optString("license_url"),
                        item.optString("attribution"), item.optLong("duration", 0L)
                    ))
                }
            }
        }
    }

    fun download(track: OpenMusicTrack, destination: java.io.File) {
        val request = Request.Builder().url(track.url).header("User-Agent", "AlrafidainTik/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("تعذر تنزيل المقطع الصوتي")
            val body = response.body ?: throw java.io.IOException("ملف الصوت فارغ")
            if (body.contentLength() > 25L * 1024L * 1024L) throw java.io.IOException("حجم المقطع أكبر من 25 ميغابايت")
            body.byteStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        }
    }
}

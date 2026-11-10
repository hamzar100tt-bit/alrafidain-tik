package com.alrafidain.tik

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class TikVideo(val id: String, val caption: String, val videoUrl: String, val userId: String)

class SupabaseRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("alrafidain_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }

    fun hasSession(): Boolean = !accessToken.isNullOrBlank()
    fun signOut() {
        accessToken = null
        prefs.edit().remove("refresh_token").remove("user_id").apply()
    }

    fun authenticate(email: String, password: String, createAccount: Boolean): String {
        val endpoint = if (createAccount) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val payload = JSONObject().put("email", email).put("password", password)
        val request = Request.Builder().url(base + endpoint).addHeader("apikey", key)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    val error = JSONObject(body)
                    error.optString("msg").ifBlank { error.optString("message") }
                }.getOrNull()
                throw IOException(message?.ifBlank { null } ?: "تعذر تسجيل الدخول، تحقق من البيانات وحاول مرة ثانية")
            }
            val json = JSONObject(body)
            val token = json.optString("access_token")
            if (token.isNotBlank()) {
                accessToken = token
                prefs.edit().putString("refresh_token", json.optString("refresh_token"))
                    .putString("user_id", json.optJSONObject("user")?.optString("id")).apply()
            }
            return if (token.isBlank() && createAccount)
                "تم إنشاء الحساب. إذا طُلب تأكيد البريد، فعّل حسابك ثم سجّل الدخول."
            else "تم تسجيل الدخول"
        }
    }

    fun loadVideos(): List<TikVideo> {
        val token = accessToken ?: key
        val request = Request.Builder()
            .url("$base/rest/v1/videos?select=id,caption,video_url,storage_path,user_id&order=created_at.desc&limit=30")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر تحميل الفيديوهات (${response.code})")
            val array = JSONArray(body)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val stored = item.optString("video_url")
                val path = item.optString("storage_path")
                val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
                val url = if (stored.isNotBlank()) stored else "$base/storage/v1/object/public/videos/$encodedPath"
                TikVideo(item.optString("id"), item.optString("caption"), url, item.optString("user_id"))
            }
        }
    }

    fun uploadVideo(uri: Uri, caption: String): String {
        val token = accessToken ?: throw IOException("سجّل الدخول أولاً")
        val userId = prefs.getString("user_id", null) ?: throw IOException("تعذر تحديد الحساب الحالي")
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        if (!mime.startsWith("video/")) throw IOException("الملف المحدد ليس فيديو")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("تعذر قراءة الفيديو من الجهاز")
        if (bytes.isEmpty()) throw IOException("الفيديو فارغ")
        if (bytes.size > 100 * 1024 * 1024) throw IOException("حجم الفيديو أكبر من 100 ميغابايت")
        val path = "$userId/${UUID.randomUUID()}.mp4"
        val upload = Request.Builder().url("$base/storage/v1/object/videos/$path")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", mime).addHeader("x-upsert", "false")
            .put(bytes.toRequestBody(mime.toMediaType())).build()
        client.newCall(upload).execute().use { response ->
            if (!response.isSuccessful) throw IOException("فشل رفع الفيديو إلى التخزين (${response.code})")
        }
        val publicUrl = "$base/storage/v1/object/public/videos/$path"
        val row = JSONObject().put("user_id", userId).put("caption", caption)
            .put("storage_path", path).put("video_url", publicUrl)
        val insert = Request.Builder().url("$base/rest/v1/videos")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
            .post(row.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(insert).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تم رفع الملف لكن تعذر تسجيله في قائمة الفيديوهات (${response.code})")
        }
        return "تم نشر الفيديو بنجاح"
    }
}

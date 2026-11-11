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

data class TikVideo(val id: String, val caption: String, val videoUrl: String, val userId: String, val creatorName: String = "", val creatorAvatar: String? = null, val mediaType: String = "video")
data class TikComment(val body: String, val displayName: String, val username: String, val avatarUrl: String?, val userId: String = "")
data class TikProfileStats(val posts: List<TikVideo>, val following: Int, val followers: Int, val likes: Int)
data class TikNotification(val id: String, val actorName: String, val actorUsername: String, val kind: String, val createdAt: String, val videoId: String?)

data class LiveKitCredentials(val serverUrl: String, val participantToken: String, val room: String, val role: String)

class SupabaseRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("alrafidain_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }

    fun requestLiveKitToken(room: String, role: String): LiveKitCredentials {
        val payload = JSONObject().put("room", room).put("role", role)
        val request = authedRequest("$base/functions/v1/livekit-token")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) throw IOException(json.optString("error").ifBlank { "تعذر الاتصال بخدمة البث (${response.code})" })
            val url = json.optString("serverUrl")
            val token = json.optString("participantToken")
            if (url.isBlank() || token.isBlank()) throw IOException("بيانات الاتصال بالبث غير مكتملة")
            return LiveKitCredentials(url, token, json.optString("room", room), json.optString("role", role))
        }
    }
    fun createLiveRoom(title: String, roomType: String): String {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val roomKey = "live-" + UUID.randomUUID().toString().replace("-", "").take(20)
        val payload = JSONObject()
            .put("room_key", roomKey)
            .put("title", title.trim().ifBlank { "بث مباشر" })
            .put("room_type", roomType)
            .put("owner_id", userId)
            .put("is_live", true)
        val request = authedRequest("$base/rest/v1/live_rooms")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر إنشاء الغرفة (${response.code}): $body")
            val rows = JSONArray(body)
            if (rows.length() == 0) throw IOException("لم يتم إنشاء الغرفة")
            return rows.getJSONObject(0).optString("room_key")
        }
    }

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
            .url("$base/rest/v1/videos?select=id,caption,video_url,storage_path,user_id,media_type,profiles!videos_user_id_fkey(display_name,username,avatar_url)&order=created_at.desc&limit=30")
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
                TikVideo(item.optString("id"), item.optString("caption"), url, item.optString("user_id"),
                    item.optJSONObject("profiles")?.optString("display_name")?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("profiles")?.optString("username").orEmpty(),
                    item.optJSONObject("profiles")?.optString("avatar_url")?.takeIf { it.isNotBlank() },
                    item.optString("media_type", "video"))
            }
        }
    }


    private fun authedRequest(url: String): Request.Builder {
        val token = accessToken ?: throw IOException("سجّل الدخول أولاً")
        return Request.Builder().url(url).addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $token")
    }

    fun currentUserId(): String? = prefs.getString("user_id", null)

    fun loadProfile(): JSONObject {
        val userId = currentUserId() ?: throw IOException("تعذر تحديد الحساب")
        val request = authedRequest("$base/rest/v1/profiles?id=eq.$userId&select=username,display_name,bio,avatar_url")
            .get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر تحميل الملف الشخصي")
            val rows = JSONArray(body)
            return if (rows.length() > 0) rows.getJSONObject(0) else JSONObject()
        }
    }



    private fun fetchAuthedRows(path: String): JSONArray {
        val request = authedRequest("$base/rest/v1/$path").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر تحميل بيانات الحساب (\${response.code})")
            return JSONArray(body)
        }
    }

    private fun parseVideoRows(rows: JSONArray): List<TikVideo> {
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val stored = item.optString("video_url")
            val path = item.optString("storage_path")
            val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
            val url = if (stored.isNotBlank()) stored else "$base/storage/v1/object/public/videos/$encodedPath"
            val creator = item.optJSONObject("profiles")
            TikVideo(
                item.optString("id"),
                item.optString("caption"),
                url,
                item.optString("user_id"),
                creator?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: creator?.optString("username").orEmpty(),
                creator?.optString("avatar_url")?.takeIf { it.isNotBlank() },
                item.optString("media_type", "video")
            )
        }
    }


    fun loadSavedVideos(): List<TikVideo> = loadRelatedVideos("saved_videos")
    fun loadLikedVideos(): List<TikVideo> = loadRelatedVideos("likes")

    private fun loadRelatedVideos(table: String): List<TikVideo> {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val relations = fetchAuthedRows("$table?user_id=eq.$userId&select=video_id&order=created_at.desc&limit=100")
        if (relations.length() == 0) return emptyList()
        val ids = (0 until relations.length()).map { relations.getJSONObject(it).optString("video_id") }
        val fields = "id,caption,video_url,storage_path,user_id,media_type,profiles!videos_user_id_fkey(display_name,username,avatar_url)"
        val rows = fetchAuthedRows("videos?id=in.(${ids.joinToString(",")})&select=$fields&limit=100")
        val byId = (0 until rows.length()).associateBy { rows.getJSONObject(it).optString("id") }
        return ids.mapNotNull { id -> byId[id]?.let { parseVideoRows(JSONArray().put(it))[0] } }
    }

    fun loadMyProfileStats(): TikProfileStats {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val fields = "id,caption,video_url,storage_path,user_id,media_type,profiles!videos_user_id_fkey(display_name,username,avatar_url)"
        val videoRows = fetchAuthedRows("videos?user_id=eq.$userId&select=$fields&order=created_at.desc&limit=100")
        val posts = parseVideoRows(videoRows)
        val following = fetchAuthedRows("follows?follower_id=eq.$userId&select=followed_id").length()
        val followers = fetchAuthedRows("follows?followed_id=eq.$userId&select=follower_id").length()
        val likes = if (posts.isEmpty()) 0 else {
            val ids = posts.joinToString(",") { it.id }
            fetchAuthedRows("likes?video_id=in.($ids)&select=video_id&limit=1000").length()
        }
        return TikProfileStats(posts, following, followers, likes)
    }

    fun updateProfile(displayName: String, username: String, bio: String) {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        if (displayName.isBlank()) throw IOException("اكتب اسم العرض")
        if (!username.matches(Regex("[A-Za-z0-9_]{3,24}"))) {
            throw IOException("اسم المستخدم يجب أن يكون 3-24 حرفاً إنجليزياً أو رقماً أو _")
        }
        val payload = JSONObject().put("display_name", displayName.trim())
            .put("username", username.trim()).put("bio", bio.trim())
        val request = authedRequest("$base/rest/v1/profiles?id=eq.$userId")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IOException(if (response.code == 409) "اسم المستخدم مستخدم من شخص آخر" else "تعذر حفظ الملف الشخصي: ${response.code} $body")
            }
        }
    }

    fun uploadAvatar(uri: Uri): String {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val token = accessToken ?: throw IOException("سجّل الدخول أولاً")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        if (!mime.startsWith("image/")) throw IOException("اختَر صورة")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("تعذرت قراءة الصورة")
        if (bytes.size > 5 * 1024 * 1024) throw IOException("حجم الصورة أكبر من 5 ميغابايت")
        val extension = when (mime.substringAfter("/", "jpeg")) {
            "png" -> "png"; "webp" -> "webp"; else -> "jpg"
        }
        val path = "$userId/avatar.$extension"
        val upload = Request.Builder().url("$base/storage/v1/object/avatars/$path")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", mime).addHeader("x-upsert", "true")
            .put(bytes.toRequestBody(mime.toMediaType())).build()
        client.newCall(upload).execute().use { response ->
            if (!response.isSuccessful) throw IOException("فشل رفع الصورة (${response.code})")
        }
        val publicUrl = "$base/storage/v1/object/public/avatars/$path?v=${System.currentTimeMillis()}"
        val update = authedRequest("$base/rest/v1/profiles?id=eq.$userId")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(JSONObject().put("avatar_url", publicUrl).toString()
                .toRequestBody("application/json".toMediaType())).build()
        client.newCall(update).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تم رفع الصورة لكن تعذر ربطها بالحساب")
        }
        return publicUrl
    }

    fun isLiked(videoId: String): Boolean = exists("likes", videoId)
    fun isSaved(videoId: String): Boolean = exists("saved_videos", videoId)

    private fun exists(table: String, videoId: String): Boolean {
        val userId = currentUserId() ?: return false
        val request = authedRequest("$base/rest/v1/$table?user_id=eq.$userId&video_id=eq.$videoId&select=video_id")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            return JSONArray(response.body?.string().orEmpty()).length() > 0
        }
    }

    fun isFollowing(userId: String): Boolean {
        val me = currentUserId() ?: return false
        val rows = fetchAuthedRows("follows?follower_id=eq.$me&followed_id=eq.$userId&select=followed_id")
        return rows.length() > 0
    }

    fun toggleFollow(userId: String): Boolean {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        if (me == userId) throw IOException("ما تكدر تتابع حسابك")
        val already = isFollowing(userId)
        val url = "$base/rest/v1/follows?follower_id=eq.$me&followed_id=eq.$userId"
        val builder = authedRequest(url).addHeader("Content-Type", "application/json")
        val request = if (already) builder.delete().build() else builder
            .post(JSONObject().put("follower_id", me).put("followed_id", userId)
                .toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تعذر تحديث المتابعة (${response.code})")
        }
        return !already
    }

    fun toggleLike(videoId: String): Boolean = toggleRelation("likes", videoId)
    fun toggleSaved(videoId: String): Boolean = toggleRelation("saved_videos", videoId)

    private fun toggleRelation(table: String, videoId: String): Boolean {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val already = exists(table, videoId)
        val builder = authedRequest("$base/rest/v1/$table?user_id=eq.$userId&video_id=eq.$videoId")
            .addHeader("Content-Type", "application/json")
        val request = if (already) builder.delete().build() else builder
            .post(JSONObject().put("user_id", userId).put("video_id", videoId)
                .toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تعذر تحديث التفاعل (${response.code})")
        }
        return !already
    }

    fun loadComments(videoId: String): List<TikComment> {
        val token = accessToken ?: key
        val request = Request.Builder()
            .url("$base/rest/v1/comments?video_id=eq.$videoId&select=body,user_id,profiles!comments_user_id_fkey(display_name,username,avatar_url)&order=created_at.desc&limit=100")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر تحميل التعليقات")
            val rows = JSONArray(body)
            return (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val profile = row.optJSONObject("profiles")
                TikComment(
                    body = row.optString("body"),
                    displayName = profile?.optString("display_name")?.takeIf { it.isNotBlank() } ?: "مستخدم",
                    username = profile?.optString("username").orEmpty(),
                    avatarUrl = profile?.optString("avatar_url")?.takeIf { it.isNotBlank() },
                    userId = row.optString("user_id")
                )
            }
        }
    }

    fun addComment(videoId: String, body: String) {
        val userId = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val payload = JSONObject().put("video_id", videoId).put("user_id", userId).put("body", body)
        val request = authedRequest("$base/rest/v1/comments")
            .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تعذر إضافة التعليق (${response.code})")
        }
    }


    data class DiscoverProfile(val id: String, val username: String, val displayName: String, val avatarUrl: String?)
    data class InboxConversation(val userId: String, val username: String, val displayName: String, val avatarUrl: String?, val lastMessage: String)
    data class DirectMessage(val id: String, val senderId: String, val body: String, val createdAt: String)

    private fun publicRows(path: String): JSONArray {
        val token = accessToken ?: key
        val request = Request.Builder().url("$base/rest/v1/$path")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("تعذر تحميل البيانات (${response.code})")
            return JSONArray(body)
        }
    }

    fun loadPublicProfile(userId: String): DiscoverProfile {
        val rows = publicRows("profiles?id=eq.$userId&select=id,username,display_name,avatar_url&limit=1")
        if (rows.length() == 0) throw IOException("الحساب غير موجود")
        val row = rows.getJSONObject(0)
        return DiscoverProfile(row.optString("id"), row.optString("username"),
            row.optString("display_name").ifBlank { row.optString("username") },
            row.optString("avatar_url").takeIf { it.isNotBlank() })
    }

    fun loadPublicUserVideos(userId: String): List<TikVideo> {
        val fields = "id,caption,video_url,storage_path,user_id,media_type,profiles!videos_user_id_fkey(display_name,username,avatar_url)"
        return parseVideoRows(publicRows("videos?user_id=eq.$userId&select=$fields&order=created_at.desc&limit=60"))
    }
    fun searchProfiles(term: String): List<DiscoverProfile> {
        val q = java.net.URLEncoder.encode("%$term%", "UTF-8")
        val rows = publicRows("profiles?select=id,username,display_name,avatar_url&or=(username.ilike.$q,display_name.ilike.$q)&limit=30")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            DiscoverProfile(row.optString("id"), row.optString("username"),
                row.optString("display_name").ifBlank { row.optString("username") },
                row.optString("avatar_url").takeIf { it.isNotBlank() })
        }
    }

    fun searchVideos(term: String): List<TikVideo> {
        val q = java.net.URLEncoder.encode("%$term%", "UTF-8")
        val rows = publicRows("videos?select=id,caption,video_url,storage_path,user_id,media_type,profiles!videos_user_id_fkey(display_name,username,avatar_url)&caption=ilike.$q&order=created_at.desc&limit=30")
        return parseVideoRows(rows)
    }

    fun loadNotifications(): List<TikNotification> {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val rows = fetchAuthedRows("notifications?recipient_id=eq.$me&select=id,actor_id,kind,created_at,video_id,profiles!notifications_actor_id_fkey(display_name,username)&order=created_at.desc&limit=100")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val actor = row.optJSONObject("profiles")
            TikNotification(row.optString("id"),
                actor?.optString("display_name")?.takeIf { it.isNotBlank() } ?: "مستخدم",
                actor?.optString("username").orEmpty(),
                row.optString("kind"), row.optString("created_at"),
                row.optString("video_id").takeIf { it.isNotBlank() })
        }
    }

    fun markNotificationsRead() {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val request = authedRequest("$base/rest/v1/notifications?recipient_id=eq.$me&read_at=is.null")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(JSONObject().put("read_at", java.time.Instant.now().toString()).toString()
                .toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تعذر تحديث الإشعارات")
        }
    }

    fun loadInbox(): List<InboxConversation> {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val rows = fetchAuthedRows("messages?or=(sender_id.eq.$me,receiver_id.eq.$me)&select=id,sender_id,receiver_id,body,created_at&order=created_at.desc&limit=500")
        val latest = linkedMapOf<String, JSONObject>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val peer = if (row.optString("sender_id") == me) row.optString("receiver_id") else row.optString("sender_id")
            if (peer.isNotBlank() && !latest.containsKey(peer)) latest[peer] = row
        }
        return latest.map { (peer, message) ->
            val profiles = publicRows("profiles?id=eq.$peer&select=username,display_name,avatar_url")
            val profile = if (profiles.length() > 0) profiles.getJSONObject(0) else JSONObject()
            InboxConversation(peer, profile.optString("username"),
                profile.optString("display_name").ifBlank { profile.optString("username").ifBlank { "مستخدم" } },
                profile.optString("avatar_url").takeIf { it.isNotBlank() }, message.optString("body"))
        }
    }

    fun loadConversation(otherUserId: String): List<DirectMessage> {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        val rows = fetchAuthedRows("messages?or=(and(sender_id.eq.$me,receiver_id.eq.$otherUserId),and(sender_id.eq.$otherUserId,receiver_id.eq.$me))&select=id,sender_id,body,created_at&order=created_at.asc&limit=500")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            DirectMessage(row.optString("id"), row.optString("sender_id"), row.optString("body"), row.optString("created_at"))
        }
    }

    fun sendMessage(otherUserId: String, body: String) {
        val me = currentUserId() ?: throw IOException("سجّل الدخول أولاً")
        if (body.isBlank()) return
        val payload = JSONObject().put("sender_id", me).put("receiver_id", otherUserId).put("body", body.trim())
        val request = authedRequest("$base/rest/v1/messages")
            .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تعذر إرسال الرسالة (${response.code})")
        }
    }

    private fun applyPhotoFilter(source: android.graphics.Bitmap, filter: String): android.graphics.Bitmap {
        if (filter == "طبيعي") return source
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val color = pixels[i]
            var r = android.graphics.Color.red(color).toFloat()
            var g = android.graphics.Color.green(color).toFloat()
            var b = android.graphics.Color.blue(color).toFloat()
            when (filter) {
                "أبيض وأسود", "رمادي" -> {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    r = gray; g = gray; b = gray
                }
                "دافئ", "ذهبي" -> { r = (r + if (filter == "ذهبي") 30f else 22f).coerceAtMost(255f); g = (g + 10f).coerceAtMost(255f); b = (b - if (filter == "ذهبي") 24f else 14f).coerceAtLeast(0f) }
                "بارد" -> { r = (r - 8f).coerceAtLeast(0f); g = (g + 3f).coerceAtMost(255f); b = (b + 24f).coerceAtMost(255f) }
                "زاهي" -> {
                    val avg = (r + g + b) / 3f
                    r = (avg + (r - avg) * 1.35f).coerceIn(0f,255f)
                    g = (avg + (g - avg) * 1.35f).coerceIn(0f,255f)
                    b = (avg + (b - avg) * 1.35f).coerceIn(0f,255f)
                }
                "وردي" -> { r = (r + 22f).coerceAtMost(255f); g = (g - 5f).coerceAtLeast(0f); b = (b + 16f).coerceAtMost(255f) }
                "قديم" -> { r = (r * .92f + 20f).coerceIn(0f,255f); g = (g * .88f + 15f).coerceIn(0f,255f); b = (b * .72f + 8f).coerceIn(0f,255f) }
                "سينما" -> { r = (r * 1.10f - 8f).coerceIn(0f,255f); g = (g * 1.04f - 4f).coerceIn(0f,255f); b = (b * .92f + 5f).coerceIn(0f,255f) }
                "ليلي" -> { r = (r * .78f).coerceAtLeast(0f); g = (g * .86f).coerceAtLeast(0f); b = (b * 1.14f).coerceAtMost(255f) }
                "ناعم" -> { r = (r * 1.04f + 8f).coerceAtMost(255f); g = (g * 1.02f + 5f).coerceAtMost(255f); b = (b + 4f).coerceAtMost(255f) }
            }
            pixels[i] = android.graphics.Color.argb(android.graphics.Color.alpha(color), r.toInt(), g.toInt(), b.toInt())
        }
        return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun uploadPhoto(uri: Uri, caption: String, filter: String = "طبيعي"): String {
        val token = accessToken ?: throw IOException("سجّل الدخول أولاً")
        val userId = currentUserId() ?: throw IOException("تعذر تحديد الحساب الحالي")
        val sourceMime = context.contentResolver.getType(uri) ?: "image/jpeg"
        if (!sourceMime.startsWith("image/")) throw IOException("الملف المحدد ليس صورة")
        val mime = "image/jpeg"
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("تعذر قراءة الصورة")
        if (original.isEmpty()) throw IOException("الصورة فارغة")
        val decoded = android.graphics.BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: throw IOException("صيغة الصورة غير مدعومة")
        val filtered = applyPhotoFilter(decoded, filter)
        val output = java.io.ByteArrayOutputStream()
        filtered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)
        val bytes = output.toByteArray()
        if (bytes.size > 15 * 1024 * 1024) throw IOException("حجم الصورة أكبر من 15 ميغابايت")
        val path = "$userId/${UUID.randomUUID()}.jpg"
        val upload = Request.Builder().url("$base/storage/v1/object/videos/$path")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", mime).put(bytes.toRequestBody(mime.toMediaType())).build()
        client.newCall(upload).execute().use { response ->
            if (!response.isSuccessful) throw IOException("فشل رفع الصورة (${response.code})")
        }
        val publicUrl = "$base/storage/v1/object/public/videos/$path"
        val row = JSONObject().put("user_id", userId).put("caption", caption)
            .put("storage_path", path).put("video_url", publicUrl).put("media_type", "image")
        val insert = Request.Builder().url("$base/rest/v1/videos")
            .addHeader("apikey", key).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
            .post(row.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(insert).execute().use { response ->
            if (!response.isSuccessful) throw IOException("تم رفع الصورة لكن تعذر نشرها (${response.code})")
        }
        return "تم نشر الصورة بنجاح"
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
            .put("storage_path", path).put("video_url", publicUrl).put("media_type", "video")
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

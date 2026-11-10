package com.alrafidain.tik

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Brand = Color(0xFFFF3157)
private val Ink = Color(0xFF17171B)
private val Muted = Color(0xFF777780)
private val Canvas = Color(0xFFF7F7F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { NativeTheme { AlrafidainApp() } }
    }
}

@Composable
private fun NativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Brand, onPrimary = Color.White, background = Canvas,
            surface = Color.White, onSurface = Ink, onBackground = Ink),
        content = content
    )
}

@Composable
private fun AlrafidainApp() {
    val context = LocalContext.current
    val repository = remember { SupabaseRepository(context.applicationContext) }
    var signedIn by remember { mutableStateOf(repository.hasSession()) }
    var selected by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf<List<TikVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshFeed() {
        scope.launch {
            loading = true
            error = null
            try { videos = withContext(Dispatchers.IO) { repository.loadVideos() } }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل الفيديوهات" }
            finally { loading = false }
        }
    }

    LaunchedEffect(signedIn) { refreshFeed() }

    if (!signedIn) {
        AuthScreen(
            onAuth = { email, password, create ->
                scope.launch {
                    loading = true
                    error = null
                    try {
                        val message = withContext(Dispatchers.IO) { repository.authenticate(email, password, create) }
                        if (repository.hasSession()) signedIn = true else error = message
                    } catch (e: Exception) { error = e.message ?: "حدث خطأ" }
                    finally { loading = false }
                }
            },
            loading = loading,
            error = error
        )
        return
    }

    val labels = listOf("الرئيسية", "اكتشف", "نشر", "الرسائل", "حسابي")
    val icons = listOf(Icons.Default.Home, Icons.Default.Explore, Icons.Default.AddCircle, Icons.Default.ChatBubble, Icons.Default.Person)
    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Brand, selectedTextColor = Brand,
                            indicatorColor = Color(0xFFFFE8ED), unselectedIconColor = Muted, unselectedTextColor = Muted)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Canvas)) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("الرافدين تيك", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                if (selected == 0) IconButton(onClick = { refreshFeed() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث الفيديوهات", tint = Ink)
                } else IconButton(onClick = { }) { Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = Ink) }
            }
            when (selected) {
                0 -> FeedScreen(videos, loading, error, ::refreshFeed, repository)
                1 -> NativePlaceholder("اكتشف", "قريباً: البحث عن الفيديوهات والحسابات", Icons.Default.Explore)
                2 -> UploadScreen(repository, onUploaded = { refreshFeed(); selected = 0 })
                3 -> NativePlaceholder("الرسائل", "محادثاتك ورسائلك الخاصة", Icons.Default.ChatBubble)
                else -> ProfileScreen(repository, onSignOut = { repository.signOut(); signedIn = false })
            }
        }
    }
}

@Composable
private fun AuthScreen(
    onAuth: (String, String, Boolean) -> Unit,
    loading: Boolean,
    error: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PlayCircleFilled, null, tint = Brand, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text("الرافدين تيك", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
        Text(if (createAccount) "أنشئ حسابك وابدأ المشاركة" else "سجّل الدخول لمتابعة الفيديوهات",
            color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("كلمة المرور") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onAuth(email.trim(), password, createAccount) },
            enabled = !loading && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (createAccount) "إنشاء حساب" else "تسجيل الدخول", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { createAccount = !createAccount }) {
            Text(if (createAccount) "عندك حساب؟ سجّل الدخول" else "ما عندك حساب؟ أنشئ حساب جديد", color = Brand)
        }
    }
}

@Composable
private fun FeedScreen(
    videos: List<TikVideo>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    repository: SupabaseRepository
) {
    when {
        loading && videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Brand)
        }
        error != null && videos.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(error, color = Muted, textAlign = TextAlign.Center)
            TextButton(onClick = onRefresh) { Text("إعادة المحاولة", color = Brand) }
        }
        videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("ماكو فيديوهات منشورة حالياً", color = Muted)
        }
        else -> {
            val pagerState = rememberPagerState(pageCount = { videos.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> videos[index].id }
            ) { page ->
                VideoFeedPage(video = videos[page], repository = repository, active = pagerState.currentPage == page)
            }
        }
    }
}

@Composable
private fun VideoFeedPage(video: TikVideo, repository: SupabaseRepository, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liked by remember(video.id) { mutableStateOf(false) }
    var saved by remember(video.id) { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(video.id) {
        withContext(Dispatchers.IO) {
            liked = repository.isLiked(video.id)
            saved = repository.isSaved(video.id)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NativeVideoPlayer(video.videoUrl, active)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { liked = withContext(Dispatchers.IO) { repository.toggleLike(video.id) } }
                        finally { busy = false }
                    }
                }) {
                    Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "إعجاب", tint = if (liked) Brand else Color.White, modifier = Modifier.size(32.dp))
                }
                Text("إعجاب", color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { commentsOpen = true }) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "تعليق", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("تعليق", color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, video.videoUrl)
                    }
                    context.startActivity(Intent.createChooser(send, "مشاركة الفيديو"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("مشاركة", color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { saved = withContext(Dispatchers.IO) { repository.toggleSaved(video.id) } }
                        finally { busy = false }
                    }
                }) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "حفظ", tint = if (saved) Brand else Color.White, modifier = Modifier.size(30.dp))
                }
                Text("حفظ", color = Color.White, fontSize = 11.sp)
            }
        }
        if (video.caption.isNotBlank()) {
            Text(video.caption, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 20.dp))
        }
    }
    if (commentsOpen) CommentDialog(video.id, repository, onDismiss = { commentsOpen = false })
}

@Composable
private fun NativeVideoPlayer(url: String, active: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
        }
    }
    LaunchedEffect(active, player) { player.playWhenReady = active; if (!active) player.pause() }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
            if (event == Lifecycle.Event.ON_RESUME && active) player.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext -> PlayerView(viewContext).apply {
            this.player = player
            useController = false
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun CommentDialog(videoId: String, repository: SupabaseRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    fun reload() {
        scope.launch {
            loading = true
            try { comments = withContext(Dispatchers.IO) { repository.loadComments(videoId) }; error = null }
            catch (e: Exception) { error = e.message }
            finally { loading = false }
        }
    }
    LaunchedEffect(videoId) { reload() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التعليقات") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(color = Brand)
                comments.forEach { item ->
                    Text(item.second, color = Ink, modifier = Modifier.padding(vertical = 5.dp))
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("اكتب تعليقك") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.addComment(videoId, text.trim()) }
                        text = ""
                        reload()
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("إرسال", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun UploadScreen(repository: SupabaseRepository, onUploaded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploading = true
                message = "جاري رفع الفيديو..."
                try {
                    val result = withContext(Dispatchers.IO) { repository.uploadVideo(uri, caption.trim()) }
                    message = result
                    onUploaded()
                } catch (e: Exception) {
                    message = e.message ?: "تعذر رفع الفيديو"
                } finally { uploading = false }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudUpload, null, tint = Brand, modifier = Modifier.size(62.dp))
        Spacer(Modifier.height(16.dp))
        Text("انشر فيديو جديد", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("اختر فيديو من جهازك حتى يظهر في صفحة الرئيسية", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = caption, onValueChange = { caption = it },
            label = { Text("اكتب وصف الفيديو (اختياري)") },
            modifier = Modifier.fillMaxWidth(), minLines = 2
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { picker.launch("video/*") },
            enabled = !uploading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.VideoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار فيديو ورفعه")
            }
        }
        if (message != null) {
            Spacer(Modifier.height(14.dp))
            Text(message!!, color = if (message!!.contains("نجاح")) Brand else Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Text("الحد الأعلى لحجم الفيديو 100 ميغابايت", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileScreen(repository: SupabaseRepository, onSignOut: () -> Unit) {
    var profile by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { profile = withContext(Dispatchers.IO) { repository.loadProfile() } }
        catch (e: Exception) { error = e.message }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AccountCircle, null, tint = Brand, modifier = Modifier.size(86.dp))
        Spacer(Modifier.height(12.dp))
        Text(profile?.optString("display_name")?.takeIf { !it.isNullOrBlank() }
            ?: profile?.optString("username")?.takeIf { !it.isNullOrBlank() } ?: "حسابي",
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        profile?.optString("username")?.takeIf { it.isNotBlank() }?.let {
            Text("@$it", color = Muted, fontSize = 14.sp)
        }
        profile?.optString("bio")?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Ink, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
        }
        if (error != null) Text(error!!, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        OutlinedButton(onClick = onSignOut) { Text("تسجيل الخروج", color = Brand) }
    }
}

@Composable
private fun NativePlaceholder(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Brand, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(description, fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

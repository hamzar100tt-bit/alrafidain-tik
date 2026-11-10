package com.alrafidain.tik

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import coil.compose.AsyncImage
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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
    var showAuth by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) { refreshFeed() }

    if (showAuth) {
        AuthScreen(
            onContinueGuest = { showAuth = false },
            onAuth = { email, password, create ->
                scope.launch {
                    loading = true
                    error = null
                    try {
                        val message = withContext(Dispatchers.IO) { repository.authenticate(email, password, create) }
                        if (repository.hasSession()) { signedIn = true; showAuth = false; selected = 0; refreshFeed() } else error = message
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (selected) {
            0 -> FeedScreen(videos, loading, error, ::refreshFeed, repository, signedIn) { showAuth = true }
            1 -> NativePlaceholder("اكتشف", "قريباً: البحث عن الفيديوهات والحسابات", Icons.Default.Explore)
            2 -> if (signedIn) UploadScreen(repository, onUploaded = { refreshFeed(); selected = 0 }) else GuestGate { showAuth = true }
            3 -> if (signedIn) NativePlaceholder("الرسائل", "محادثاتك ورسائلك الخاصة", Icons.Default.ChatBubble) else GuestGate { showAuth = true }
            else -> if (signedIn) ProfileScreen(repository, onSignOut = { repository.signOut(); signedIn = false; selected = 0 }) else GuestGate { showAuth = true }
        }

        Row(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { if (selected == 0) refreshFeed() }) {
                Icon(
                    if (selected == 0) Icons.Default.Refresh else Icons.Default.ArrowBack,
                    contentDescription = "تحديث",
                    tint = if (selected == 0) Color.White else Ink
                )
            }
            Text(
                "الرافدين تيك",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected == 0) Color.White else Brand
            )
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0)
        ) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Icon(icons[index], contentDescription = label) },
                    label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Brand,
                        selectedTextColor = Brand,
                        indicatorColor = Color.White.copy(alpha = 0.16f),
                        unselectedIconColor = Color.White.copy(alpha = 0.82f),
                        unselectedTextColor = Color.White.copy(alpha = 0.82f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    onContinueGuest: () -> Unit,
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
        TextButton(onClick = onContinueGuest) { Text("متابعة بدون حساب", color = Muted) }
    }
}

@Composable
private fun GuestGate(onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.PersonOutline, null, tint = Brand, modifier = Modifier.size(64.dp))
        Text("أنشئ حساب حتى تستخدم هذه الميزة", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("تسجيل الدخول أو إنشاء حساب") }
    }
}

@Composable
private fun FeedScreen(
    videos: List<TikVideo>, loading: Boolean, error: String?, onRefresh: () -> Unit,
    repository: SupabaseRepository, signedIn: Boolean, onLogin: () -> Unit
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
                VideoFeedPage(video = videos[page], repository = repository, active = pagerState.currentPage == page, signedIn = signedIn, onLogin = onLogin)
            }
        }
    }
}

@Composable
private fun VideoFeedPage(video: TikVideo, repository: SupabaseRepository, active: Boolean, signedIn: Boolean, onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liked by remember(video.id) { mutableStateOf(false) }
    var saved by remember(video.id) { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(video.id, signedIn) {
        if (signedIn) withContext(Dispatchers.IO) { liked = repository.isLiked(video.id); saved = repository.isSaved(video.id) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video.mediaType == "image") {
            AsyncImage(model = video.videoUrl, contentDescription = video.caption, modifier = Modifier.fillMaxSize())
        } else NativeVideoPlayer(video.videoUrl, active)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(enabled = !busy, onClick = {
                    if (!signedIn) onLogin() else scope.launch {
                        busy = true
                        try { liked = withContext(Dispatchers.IO) { repository.toggleLike(video.id) } }
                        catch (e: Exception) { actionError = e.message ?: "تعذر تسجيل الإعجاب" }
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
                    if (!signedIn) onLogin() else scope.launch {
                        busy = true
                        try { saved = withContext(Dispatchers.IO) { repository.toggleSaved(video.id) }; actionError = if (saved) "تم حفظ الفيديو" else "تم إلغاء الحفظ" }
                        catch (e: Exception) { actionError = e.message ?: "تعذر حفظ الفيديو" }
                        finally { busy = false }
                    }
                }) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "حفظ", tint = if (saved) Brand else Color.White, modifier = Modifier.size(30.dp))
                }
                Text("حفظ", color = Color.White, fontSize = 11.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 22.dp)) {
            Text(video.creatorName.ifBlank { "مستخدم" }, color = Color.White, fontWeight = FontWeight.Bold)
            if (video.caption.isNotBlank()) Text(video.caption, color = Color.White, fontSize = 15.sp)
            actionError?.let { Text(it, color = Color.White, fontSize = 11.sp) }
        }
    }
    if (commentsOpen) CommentDialog(video.id, repository, signedIn, onLogin, onDismiss = { commentsOpen = false })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentDialog(videoId: String, repository: SupabaseRepository, signedIn: Boolean, onLogin: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var comments by remember { mutableStateOf<List<TikComment>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                comments = withContext(Dispatchers.IO) { repository.loadComments(videoId) }
                error = null
            } catch (e: Exception) {
                error = e.message ?: "تعذر تحميل التعليقات"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(videoId) { reload() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        contentColor = Ink,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(38.dp).height(4.dp).background(Color.LightGray, RoundedCornerShape(4.dp)))
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("التعليقات  ·  ${comments.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
            HorizontalDivider(color = Color(0xFFEDEDED))
            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
                comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("ماكو تعليقات لحد الآن", color = Muted)
                        Text("كن أول واحد يعلّق", color = Muted, fontSize = 12.sp)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(comments) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!item.avatarUrl.isNullOrBlank()) AsyncImage(model = item.avatarUrl, contentDescription = null, modifier = Modifier.size(38.dp).background(Color(0xFFE9E9ED), RoundedCornerShape(50)))
                            else Icon(Icons.Default.AccountCircle, null, tint = Muted, modifier = Modifier.size(38.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                                if (item.username.isNotBlank()) Text("@${item.username}", fontSize = 11.sp, color = Muted)
                                Text(item.body, fontSize = 14.sp, color = Ink)
                            }
                        }
                    }
                }
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp))
            }
            HorizontalDivider(color = Color(0xFFEDEDED))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("أضف تعليقاً...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )
                IconButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        if (!signedIn) { onLogin(); return@IconButton }
                        val comment = text.trim()
                        scope.launch {
                            sending = true
                            try {
                                withContext(Dispatchers.IO) { repository.addComment(videoId, comment) }
                                text = ""
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: "تعذر إرسال التعليق"
                            } finally {
                                sending = false
                            }
                        }
                    }
                ) {
                    if (sending) CircularProgressIndicator(color = Brand, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, contentDescription = "إرسال التعليق",
                        tint = if (text.isNotBlank()) Brand else Muted)
                }
            }
        }
    }
}

@Composable
private fun UploadScreen(repository: SupabaseRepository, onUploaded: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("طبيعي") }

    fun upload(uri: Uri, isPhoto: Boolean) {
        scope.launch {
            uploading = true
            message = if (isPhoto) "جاري رفع الصورة..." else "جاري رفع الفيديو..."
            try {
                val result = withContext(Dispatchers.IO) {
                    if (isPhoto) repository.uploadPhoto(uri, caption.trim(), selectedFilter)
                    else repository.uploadVideo(uri, caption.trim())
                }
                message = result
                onUploaded()
            } catch (e: Exception) {
                message = e.message ?: "تعذر النشر"
            } finally { uploading = false }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) upload(uri, false)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) upload(uri, true)
    }
    val cameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                uploading = true
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Alrafidain_" + System.currentTimeMillis() + ".jpg")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException("تعذر إنشاء ملف الصورة")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                    } ?: throw java.io.IOException("تعذر حفظ الصورة")
                    upload(uri, true)
                } catch (e: Exception) {
                    message = e.message ?: "تعذر التقاط الصورة"
                    uploading = false
                }
            }
        }
    }
    val cameraVideo = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) upload(uri, false)
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AddCircle, null, tint = Brand, modifier = Modifier.size(58.dp))
        Text("إنشاء منشور", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("التقط صورة أو فيديو أو اختر من الاستديو", color = Color.LightGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text("فلتر الصورة", color = Color.White, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("طبيعي", "دافئ", "أبيض وأسود", "زاهي").forEach { filter ->
                FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Brand, selectedLabelColor = Color.White, labelColor = Color.White))
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = caption, onValueChange = { caption = it },
            label = { Text("اكتب وصفاً (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Brand, unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Brand, unfocusedLabelColor = Color.LightGray
            )
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = { cameraPhoto.launch(null) }, enabled = !uploading,
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
            Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("التقاط صورة")
        }
        Button(onClick = {
            cameraVideo.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE))
        }, enabled = !uploading, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29292F))) {
            Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text("تصوير فيديو")
        }
        OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("اختيار صورة من الاستديو")
        }
        OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("اختيار فيديو من الاستديو")
        }
        if (uploading) CircularProgressIndicator(color = Brand, modifier = Modifier.padding(12.dp))
        message?.let { Text(it, color = if (it.contains("نجاح")) Brand else Color.White, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ProfileScreen(repository: SupabaseRepository, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val data = withContext(Dispatchers.IO) { repository.loadProfile() }
                profile = data
                displayName = data.optString("display_name")
                username = data.optString("username")
                bio = data.optString("bio")
                avatarUrl = data.optString("avatar_url").takeIf { it.isNotBlank() }
                message = null
            } catch (e: Exception) {
                message = e.message ?: "تعذر تحميل الحساب"
            } finally { loading = false }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                saving = true
                message = "جاري رفع الصورة..."
                try {
                    avatarUrl = withContext(Dispatchers.IO) { repository.uploadAvatar(uri) }
                    message = "تم تحديث الصورة الشخصية"
                } catch (e: Exception) {
                    message = e.message ?: "تعذر رفع الصورة"
                } finally { saving = false }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier.fillMaxSize().background(Canvas).padding(horizontal = 22.dp, vertical = 18.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading && profile == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else {
            Spacer(Modifier.height(22.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "الصورة الشخصية",
                        modifier = Modifier.size(112.dp).background(Color.White, RoundedCornerShape(100.dp))
                    )
                } else {
                    Icon(Icons.Default.AccountCircle, null, tint = Brand, modifier = Modifier.size(112.dp))
                }
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.size(36.dp).background(Color.White, RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "تغيير الصورة", tint = Brand)
                }
            }
            Text("ملفي الشخصي", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("غيّر صورتك واسمك ومعلومات حسابك", fontSize = 13.sp, color = Muted)
            OutlinedTextField(
                value = displayName, onValueChange = { displayName = it },
                label = { Text("الاسم الظاهر") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("اسم المستخدم (إنجليزي)") },
                supportingText = { Text("3 إلى 24 حرفاً: a-z و0-9 وشرطة سفلية") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("نبذة عنك") }, minLines = 2, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            if (message != null) Text(message!!, color = if (message!!.contains("تم")) Brand else MaterialTheme.colorScheme.error,
                fontSize = 13.sp, textAlign = TextAlign.Center)
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        message = null
                        try {
                            withContext(Dispatchers.IO) { repository.updateProfile(displayName, username, bio) }
                            message = "تم حفظ الملف الشخصي"
                            refresh()
                        } catch (e: Exception) {
                            message = e.message ?: "تعذر حفظ التغييرات"
                        } finally { saving = false }
                    }
                },
                enabled = !saving && displayName.isNotBlank() && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("تسجيل الخروج", color = Brand)
            }
        }
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

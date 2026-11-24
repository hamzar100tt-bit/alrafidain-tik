package com.alrafidain.tik

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.livekit.android.room.track.video.CameraCapturerUtils
import livekit.org.webrtc.CameraXHelper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import io.livekit.android.compose.ui.VideoTrackView

private val Brand = Color(0xFFFF3157)
private val Ink = Color(0xFF17171B)
private val Muted = Color(0xFF777780)
private val Canvas = Color(0xFFF7F7F8)

private data class LiveChatMessage(val userId: String, val text: String, val displayName: String, val avatarUrl: String?)

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
    var showNotifications by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    var replaySignal by remember { mutableIntStateOf(0) }
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

    val labels = listOf("الرئيسية", "اكتشف", "لايف", "الرسائل", "حسابي")
    val icons = listOf(Icons.Default.Home, Icons.Default.Explore, Icons.Default.Videocam, Icons.Default.ChatBubble, Icons.Default.Person)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showNotifications) {
            NotificationsScreen(repository, onBack = { showNotifications = false })
        } else when (selected) {
            0 -> FeedScreen(videos, loading, error, ::refreshFeed, replaySignal, repository, signedIn) { showAuth = true }
            1 -> DiscoverScreen(repository)
            2 -> LiveRoomsScreen(repository, onBack = { selected = 0 }, onStartVideoPost = { if (signedIn) selected = 5 else showAuth = true }, onRequireLogin = { showAuth = true })
            3 -> if (signedIn) MessagesScreen(repository) else GuestGate { showAuth = true }
            4 -> if (signedIn) ProfileScreen(repository, onSignOut = { repository.signOut(); signedIn = false; selected = 0 }) else GuestGate { showAuth = true }
            5 -> if (signedIn) NativeCameraScreen(repository, onBack = { selected = 2 }, onUploaded = { refreshFeed(); selected = 0 }) else GuestGate { showAuth = true }
        }

        if (selected == 0) {
            IconButton(
                onClick = { if (signedIn) showNotifications = true else showAuth = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 10.dp, top = 6.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "الإشعارات", tint = Color.White, modifier = Modifier.size(27.dp))
            }
        }
        if (selected == 3) {
            Row(
                Modifier.align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selected = 0 }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Ink, modifier = Modifier.size(27.dp))
                }
            }
        } else if (selected != 0 && selected != 2 && selected != 4 && selected != 5) Row(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { if (signedIn) showNotifications = true else showAuth = true }) {
                Icon(Icons.Default.Notifications, contentDescription = "الإشعارات", tint = Ink)
            }
            Text(
                "الرافدين تيك",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Brand
            )
        }

        if (selected != 2) {
            // Light pages need a solid navigation surface; white icons were invisible on the profile.
            val lightNavigation = selected == 1 || selected == 3 || selected == 4 || selected == 5
            NavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth(),
                containerColor = if (lightNavigation) Color.White else Color.Transparent,
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
                            indicatorColor = if (lightNavigation) Color(0xFFFFE7ED) else Color.White.copy(alpha = 0.16f),
                            unselectedIconColor = if (lightNavigation) Muted else Color.White.copy(alpha = 0.92f),
                            unselectedTextColor = if (lightNavigation) Muted else Color.White.copy(alpha = 0.92f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRoomsScreen(repository: SupabaseRepository, onBack: () -> Unit, onStartVideoPost: () -> Unit, onRequireLogin: () -> Unit) {
    var openedRoom by remember { mutableStateOf<String?>(null) }
    var openedVideoRoom by remember { mutableStateOf<String?>(null) }
    var openedVideoRoomAsHost by remember { mutableStateOf(false) }
    var openedAudioRoom by remember { mutableStateOf<String?>(null) }
    var showCreateAudioRoom by remember { mutableStateOf(false) }
    var audioRoomTitle by remember { mutableStateOf("غرفة الرافدين الصوتية") }
    var selectedAudioBackground by remember { mutableIntStateOf(0) }
    var creatingAudioRoom by remember { mutableStateOf(false) }
    var audioRoomError by remember { mutableStateOf<String?>(null) }
    var roomType by remember { mutableStateOf("جميع الغرف") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showCreateVideoRoom by remember { mutableStateOf(false) }
    var newRoomTitle by remember { mutableStateOf("بث الرافدين تيك") }
    var creatingRoom by remember { mutableStateOf(false) }
    var createRoomError by remember { mutableStateOf<String?>(null) }
    val liveScope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<LiveRoomInfo>>(emptyList()) }
    var roomsLoading by remember { mutableStateOf(true) }
    var roomsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            rooms = withContext(Dispatchers.IO) { repository.loadLiveRooms() }
            roomsError = null
        } catch (e: Exception) {
            roomsError = e.message ?: "تعذر تحميل الغرف المباشرة"
        } finally { roomsLoading = false }
    }
    val shownRooms = rooms.filter { room ->
        room.title.contains(searchText.trim(), ignoreCase = true) &&
            (roomType == "جميع الغرف" || (roomType == "الصوتية" && room.roomType == "audio") || (roomType == "الفيديو" && room.roomType == "video"))
    }
    if (openedAudioRoom != null) {
        AudioLiveRoomScreen(repository, roomName = openedAudioRoom!!, backgroundStyle = selectedAudioBackground, onBack = { openedAudioRoom = null })
        return
    }
    if (openedVideoRoom != null) {
        VideoLiveRoomScreen(repository, roomName = openedVideoRoom!!, isHost = openedVideoRoomAsHost, onBack = { openedVideoRoom = null; openedVideoRoomAsHost = false }, onRequireLogin = onRequireLogin)
        return
    }
    if (openedRoom != null) {
        var muted by remember(openedRoom) { mutableStateOf(true) }
        Column(Modifier.fillMaxSize().background(Color(0xFF111B2B)).statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF17354B)))
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { openedRoom = null }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White) }
                        Text(openedRoom!!, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("● 447", color = Color.White, fontSize = 13.sp)
                        IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, "المزيد", tint = Color.White) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(78.dp).background(Color(0xFF704A83), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(42.dp))
                            }
                            Text("المضيف", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        items(4) { seat ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(54.dp).background(Color(0xFF344E69), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Text((seat + 1).toString(), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        items(4) { seat ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(54.dp).background(Color(0xFF26384E), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(25.dp))
                                }
                                Text((seat + 5).toString(), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("الدردشة", color = Color.White, fontWeight = FontWeight.Bold)
                    listOf("أحمد انضم إلى الغرفة", "نور: مساء الخير للجميع ❤️", "علي: أجواء جميلة").forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFB9C8E0))
                            Text(line, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0C101A)).navigationBarsPadding().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { }) { Icon(Icons.Default.VolumeUp, "الصوت", tint = Color.White) }
                IconButton(onClick = { muted = !muted }) { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "كتم", tint = if (muted) Brand else Color.White) }
                IconButton(onClick = onRequireLogin) { Icon(Icons.Default.People, "الحضور", tint = Color.White) }
                IconButton(onClick = onRequireLogin) { Icon(Icons.Default.CardGiftcard, "هدايا", tint = Brand) }
                IconButton(onClick = { }) { Icon(Icons.Default.MoreHoriz, "المزيد", tint = Color.White) }
            }
        }
        return
    }
    if (showCreateAudioRoom) {
            val backgrounds = listOf("ملعب كرة القدم", "خلفية زرقاء", "غرفة ألعاب", "مدينة ليلية", "شاطئ الغروب", "مسجد ليلي")
            val backgroundIcons = listOf("⚽", "🌊", "🎮", "🌃", "🌅", "🕌")
            val backgroundBrushes = listOf(
                listOf(Color(0xFF062C58), Color(0xFF087C9B), Color(0xFF164A32)),
                listOf(Color(0xFF0066CC), Color(0xFF123A9C), Color(0xFF061B4A)),
                listOf(Color(0xFF35105D), Color(0xFF711D86), Color(0xFF120D2B)),
                listOf(Color(0xFF071B3D), Color(0xFF183C66), Color(0xFF030A19)),
                listOf(Color(0xFFFFA45B), Color(0xFFB84F73), Color(0xFF253C70)),
                listOf(Color(0xFF102A53), Color(0xFF28517A), Color(0xFF08192E))
            )
            Column(
                Modifier.fillMaxSize().background(Color(0xFF050817)).statusBarsPadding()
                    .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (!creatingAudioRoom) showCreateAudioRoom = false }) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("إنشاء بث صوتي مباشر", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Mic, null, tint = Color(0xFF31C8FF), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF102C50), Color(0xFF071426))))
                    .border(1.dp, Color(0xFF168BD1), RoundedCornerShape(22.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C36E8), Color(0xFF11B8E8)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مضيف البث", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("غرفتك الصوتية • 8 مقاعد للمايك", color = Color(0xFFB7C9E2), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Verified, null, tint = Color(0xFF29B6F6))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0A1B31)).border(1.dp, Color(0xFF176B9E), RoundedCornerShape(22.dp)).padding(14.dp)) {
                    Text("عنوان البث", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = audioRoomTitle, onValueChange = { audioRoomTitle = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("اكتب اسم الغرفة") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF24B9F4), unfocusedBorderColor = Color(0xFF385878),
                            focusedLabelColor = Color(0xFF70D7FF), unfocusedLabelColor = Color(0xFFB5C5D8),
                            cursorColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("اختر خلفية البث", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("الخلفية المختارة تظهر داخل غرفة البث", color = Color(0xFFB7C9E2), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(backgrounds.size) { index ->
                            val chosen = selectedAudioBackground == index
                            Column(
                                Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.verticalGradient(backgroundBrushes[index]))
                                    .border(if (chosen) 3.dp else 1.dp, if (chosen) Color(0xFFE43DFF) else Color(0xFF287EB2), RoundedCornerShape(16.dp))
                                    .clickable { selectedAudioBackground = index }.padding(11.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(backgroundIcons[index], fontSize = 28.sp)
                                    if (chosen) Icon(Icons.Default.CheckCircle, "محدد", tint = Color.White, modifier = Modifier.size(25.dp))
                                }
                                Text(backgrounds[index], color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("إعدادات الغرفة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().padding(top = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("عدد مقاعد المايك", color = Color.White)
                        Text("8 مقاعد", color = Color(0xFF6BD5FF), fontWeight = FontWeight.Bold)
                    }
                    if (audioRoomError != null) Text(audioRoomError!!, color = Color(0xFFFF7B91))
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !creatingAudioRoom && audioRoomTitle.isNotBlank(),
                    onClick = {
                        creatingAudioRoom = true
                        audioRoomError = null
                        liveScope.launch {
                            try {
                                val roomKey = withContext(Dispatchers.IO) { repository.createLiveRoom(audioRoomTitle, "audio") }
                                showCreateAudioRoom = false
                                openedAudioRoom = roomKey
                            } catch (e: Exception) {
                                audioRoomError = e.message ?: "تعذر إنشاء الغرفة الصوتية"
                            } finally { creatingAudioRoom = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B24E8), contentColor = Color.White),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(if (creatingAudioRoom) "جارٍ بدء البث..." else "بدء البث الآن  ◉", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = { if (!creatingAudioRoom) showCreateAudioRoom = false }) {
                    Text("إلغاء", color = Color(0xFFB9C9E0))
                }
            }
        return
    }
    if (showCreateVideoRoom) {
        VideoBroadcastSetupScreen(
            onClose = { if (!creatingRoom) showCreateVideoRoom = false },
            creating = creatingRoom,
            onStart = { title ->
                creatingRoom = true
                createRoomError = null
                liveScope.launch {
                    try {
                        val roomKey = withContext(Dispatchers.IO) { repository.createLiveRoom(title, "video") }
                        showCreateVideoRoom = false
                        openedVideoRoomAsHost = true
                        openedVideoRoom = roomKey
                    } catch (e: Exception) {
                        createRoomError = e.message ?: "تعذر إنشاء البث"
                    } finally { creatingRoom = false }
                }
            },
            errorMessage = createRoomError
        )
        return
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF09070D)).statusBarsPadding().padding(bottom = 78.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع إلى الرئيسية", tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("البث المباشر", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Default.Search, "بحث عن غرفة", tint = Color.White) }
        }
        if (searchOpen) {
            OutlinedTextField(value = searchText, onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                singleLine = true, placeholder = { Text("اكتب اسم الغرفة") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { searchText = "" }) { Icon(Icons.Default.Close, "مسح") } })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF211D29)).padding(4.dp)) {
            listOf("جميع الغرف", "الصوتية", "الفيديو").forEach { tab ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(if (roomType == tab) Color(0xFFB23BE0) else Color.Transparent)
                    .clickable { roomType = tab }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(tab, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("غرف مباشرة الآن", color = Color(0xFFFFC04D), modifier = Modifier.padding(18.dp))
        when {
            roomsLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }
            roomsError != null -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(roomsError!!, color = Color.White)
                TextButton(onClick = { liveScope.launch { roomsLoading = true; try { rooms = withContext(Dispatchers.IO) { repository.loadLiveRooms() }; roomsError = null } catch (e: Exception) { roomsError = e.message } finally { roomsLoading = false } } }) { Text("إعادة المحاولة", color = Color.White) }
            }
            shownRooms.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("ماكو غرف مباشرة حالياً", color = Color.LightGray)
            }
            else -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shownRooms.size) { i ->
                    val room = shownRooms[i]
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF251B2B))
                        .clickable { if (room.roomType == "video") openedVideoRoom = room.roomKey else openedAudioRoom = room.roomKey }) {
                        Box(Modifier.fillMaxWidth().height(130.dp)
                            .background(listOf(Color(0xFF573C67), Color(0xFF17456A), Color(0xFF70483E))[i % 3], RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (room.roomType == "video") Icons.Default.Videocam else Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(54.dp))
                            Text("LIVE", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).clip(RoundedCornerShape(8.dp)).background(Brand).padding(6.dp), fontSize = 11.sp)
                        }
                        Text(room.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        Text("المضيف: ${room.ownerName}", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showCreateAudioRoom = true }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9226C5))) {
                Icon(Icons.Default.Headphones, null); Text("ابدأ بث صوتي", maxLines = 1)
            }
            Button(onClick = { showCreateVideoRoom = true }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                Icon(Icons.Default.Videocam, null); Text("ابدأ بث فيديو", maxLines = 1)
            }
        }
        TextButton(onClick = onStartVideoPost, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("نشر فيديو عادي", color = Color.LightGray)
        }
    }
}
@Composable
private fun AudioLiveRoomScreen(repository: SupabaseRepository, roomName: String, backgroundStyle: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val room = remember(roomName) { LiveKit.create(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("جارٍ الاتصال بالغرفة...") }
    var error by remember { mutableStateOf<String?>(null) }
    var micEnabled by remember { mutableStateOf(false) }
    var canSpeak by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(true) }
    var seatIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var seatProfiles by remember { mutableStateOf<List<SupabaseRepository.DiscoverProfile>>(emptyList()) }
    var activeSpeakers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var host by remember { mutableStateOf<LiveRoomInfo?>(null) }
    var viewerCount by remember { mutableStateOf(1) }
    var viewerProfiles by remember { mutableStateOf<List<SupabaseRepository.DiscoverProfile>>(emptyList()) }
    var viewersDialog by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf<List<LiveChatMessage>>(emptyList()) }
    var myChatProfile by remember { mutableStateOf<SupabaseRepository.DiscoverProfile?>(null) }
    var selectedChatUserId by remember { mutableStateOf<String?>(null) }
    var selectedModerationUserId by remember { mutableStateOf<String?>(null) }
    var moderationBusy by remember { mutableStateOf(false) }
    var moderationMessage by remember { mutableStateOf<String?>(null) }
    var giftsOpen by remember { mutableStateOf(false) }
    var quickGift by remember { mutableStateOf(false) }
    var selectedGiftReceiverId by remember { mutableStateOf<String?>(null) }
    var giftHistoryOpen by remember { mutableStateOf(false) }
    var giftHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var giftBusy by remember { mutableStateOf(false) }
    var chatComposerOpen by remember { mutableStateOf(false) }
    var showAudioControls by remember { mutableStateOf(false) }
    var audioEffectsOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var isFollowingHost by remember { mutableStateOf(false) }
    var followHostBusy by remember { mutableStateOf(false) }

    fun refreshSeats() {
        val ids = mutableListOf<String>()
        if (room.localParticipant.isMicrophoneEnabled) repository.currentUserId()?.let(ids::add)
        room.remoteParticipants.values.filter { it.isMicrophoneEnabled }.forEach { p ->
            p.identity?.value?.let(ids::add)
        }
        seatIds = ids.take(8)
        viewerCount = 1 + room.remoteParticipants.size
        val allIds = (listOfNotNull(repository.currentUserId()) + room.remoteParticipants.values.mapNotNull { it.identity?.value }).distinct()
        scope.launch(Dispatchers.IO) {
            runCatching { repository.loadPublicProfiles(ids) }.onSuccess { seatProfiles = it }
            runCatching { repository.loadPublicProfiles(allIds) }.onSuccess { viewerProfiles = it }
        }
    }

    LaunchedEffect(host?.ownerId) {
        val ownerId = host?.ownerId
        val me = repository.currentUserId()
        if (!ownerId.isNullOrBlank() && ownerId != me) {
            isFollowingHost = withContext(Dispatchers.IO) {
                runCatching { repository.isFollowing(ownerId) }.getOrDefault(false)
            }
        } else isFollowingHost = false
    }

    DisposableEffect(room) { onDispose { room.disconnect() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch {
            try { room.localParticipant.setMicrophoneEnabled(true); micEnabled = true; refreshSeats() }
            catch (e: Exception) { error = e.message ?: "تعذر تشغيل المايك" }
        } else error = "اسمح للتطبيق باستخدام المايك."
    }
    LaunchedEffect(room) {
        room.events.collect { e ->
            when (e) {
                is RoomEvent.ParticipantConnected, is RoomEvent.ParticipantDisconnected,
                is RoomEvent.TrackMuted, is RoomEvent.TrackUnmuted -> refreshSeats()
                is RoomEvent.ActiveSpeakersChanged -> {
                    activeSpeakers = e.speakers.mapNotNull { it.identity?.value }.toSet()
                    refreshSeats()
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(roomName, connected) {
        if (connected) {
            while (true) {
                try {
                    val latest = withContext(Dispatchers.IO) { repository.loadLiveChatMessages(roomName) }
                    chat = latest.map { LiveChatMessage(it.userId, it.body, it.displayName, it.avatarUrl) }
                } catch (_: Exception) { }
                delay(2500)
            }
        }
    }

    LaunchedEffect(roomName) {
        try {
            myChatProfile = withContext(Dispatchers.IO) {
                val me = repository.currentUserId()
                val profile = repository.loadProfile()
                if (me.isNullOrBlank()) null else SupabaseRepository.DiscoverProfile(
                    me, profile.optString("username"),
                    profile.optString("display_name").ifBlank { profile.optString("username").ifBlank { "مستخدم" } },
                    profile.optString("avatar_url").takeIf { it.isNotBlank() }
                )
            }
            host = withContext(Dispatchers.IO) {
                repository.loadLiveRooms().firstOrNull { it.roomKey == roomName }
            }
            val c = withContext(Dispatchers.IO) { repository.requestLiveKitToken(roomName, "viewer") }
            canSpeak = c.role == "host" || c.role == "speaker"
            room.connect(c.serverUrl, c.participantToken)
            connected = true
            status = if (canSpeak) "أنت المضيف" else "متصل — مستمع"
            viewerCount = 1 + room.remoteParticipants.size
            if (canSpeak) permission.launch(android.Manifest.permission.RECORD_AUDIO)
            refreshSeats()
        } catch (e: Exception) { status = "تعذر الاتصال"; error = e.message ?: "فشل الاتصال بالغرفة" }
        finally { connecting = false }
    }

    val sky = when (backgroundStyle) {
        1 -> Brush.verticalGradient(listOf(Color(0xFF0066CC), Color(0xFF123A9C), Color(0xFF061B4A)))
        2 -> Brush.verticalGradient(listOf(Color(0xFF35105D), Color(0xFF711D86), Color(0xFF120D2B)))
        3 -> Brush.verticalGradient(listOf(Color(0xFF071B3D), Color(0xFF183C66), Color(0xFF030A19)))
        4 -> Brush.verticalGradient(listOf(Color(0xFFFFA45B), Color(0xFFB84F73), Color(0xFF253C70)))
        5 -> Brush.verticalGradient(listOf(Color(0xFF102A53), Color(0xFF28517A), Color(0xFF08192E)))
        else -> Brush.verticalGradient(listOf(Color(0xFF35105D), Color(0xFF260B4B), Color(0xFF16082F), Color(0xFF0D0620)))
    }
    Box(Modifier.fillMaxSize().background(sky)) {
        // إضاءة زخرفية ثابتة تعطي إحساس المسرح البنفسجي دون التأثير على الصوت أو اللمس.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 185.dp).size(270.dp)
                .background(Brush.radialGradient(listOf(Color(0x885D21A8), Color.Transparent)), CircleShape))
            Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 115.dp)
                .size(125.dp).background(Brush.radialGradient(listOf(Color(0x665D21A8), Color.Transparent)), CircleShape))
            Icon(Icons.Default.MusicNote, null, tint = Color(0xFFB66BFF).copy(alpha = .22f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp).size(42.dp))
            Icon(Icons.Default.MusicNote, null, tint = Color(0xFFD08AFF).copy(alpha = .20f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 34.dp, top = 110.dp).size(34.dp))
            Icon(Icons.Default.Mic, null, tint = Color(0xFFCE9AFF).copy(alpha = .16f),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 105.dp).size(92.dp))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Live room header: close, PiP minimize, share, add/invite, host name + account ID.
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, "الخروج من البث", tint = Color.White, modifier = Modifier.size(23.dp))
                }
                IconButton(onClick = {
                    val activity = context as? Activity
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && activity != null) {
                        runCatching {
                            activity.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
                            )
                        }.onFailure { error = "تعذر تصغير البث على هذا الجهاز" }
                    } else error = "تصغير البث يحتاج Android 8 أو أحدث"
                }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Fullscreen, "تصغير البث", tint = Color.White, modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "انضم إلى بث " + (host?.title ?: "غرفة الرافدين") + " على الرافدين تيك")
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "مشاركة البث")) }
                        .onFailure { error = "تعذرت مشاركة البث" }
                }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Share, "مشاركة البث", tint = Color.White, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    enabled = !followHostBusy && !host?.ownerId.isNullOrBlank() && host?.ownerId != repository.currentUserId(),
                    onClick = {
                        val ownerId = host?.ownerId ?: return@IconButton
                        followHostBusy = true
                        scope.launch {
                            try {
                                isFollowingHost = withContext(Dispatchers.IO) { repository.toggleFollow(ownerId) }
                            } catch (e: Exception) {
                                error = e.message ?: "تعذرت متابعة صاحب البث"
                            } finally { followHostBusy = false }
                        }
                    },
                    modifier = Modifier.size(29.dp).background(Color(0xFF17C9C6), CircleShape)
                ) {
                    Icon(
                        if (isFollowingHost) Icons.Default.Check else Icons.Default.Add,
                        if (isFollowingHost) "تتابع صاحب البث" else "متابعة صاحب البث",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text(host?.ownerName ?: "صاحب البث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("ID: ${host?.ownerId?.take(12) ?: "—"}", color = Color.White.copy(alpha = .8f), fontSize = 9.sp, maxLines = 1)
                    Text(if (connected) "● بث مباشر" else status, color = Color.White.copy(alpha = .9f), fontSize = 9.sp)
                }
                Spacer(Modifier.width(7.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF244F7D)), contentAlignment = Alignment.Center) {
                    val avatar = host?.ownerAvatar
                    if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = "صورة صاحب البث", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
            // Room counters and participant avatars.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color(0x55304F7A)).clickable { viewersDialog = true }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(" $viewerCount", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.width(7.dp))
                Spacer(Modifier.weight(1f))
                Text("🏠 ${host?.title ?: "غرفة الرافدين"}", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.background(Color(0x55304F7A), RoundedCornerShape(18.dp)).padding(horizontal = 11.dp, vertical = 6.dp), maxLines = 1)
            }

            // Eight voice seats in the same 4-by-2 order as the reference room.
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(158.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                items(8) { i ->
                    val id = seatIds.getOrNull(i)
                    // Match profile data by user ID, not database row order.
                    val profile = id?.let { seatUserId -> seatProfiles.firstOrNull { it.id == seatUserId } }
                    val isSpeaking = id != null && activeSpeakers.contains(id)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp).clip(CircleShape)
                            .border(2.dp, if (isSpeaking) Color(0xFF31F0D0) else Color(0xFFD05BFF), CircleShape)
                            .background(if (isSpeaking) Color(0xFF16B98D) else if (profile != null) Color(0xFF25194F) else Color(0x553A2C79))
                            .clickable(enabled = id != null || (connected && !connecting)) {
                                if (id != null) {
                                    if (id == repository.currentUserId()) showAudioControls = true
                                    else selectedChatUserId = id
                                } else {
                                    connecting = true
                                    scope.launch {
                                        try {
                                            val credentials = withContext(Dispatchers.IO) { repository.joinLiveRoomSeat(roomName) }
                                            room.disconnect()
                                            room.connect(credentials.serverUrl, credentials.participantToken)
                                            canSpeak = true
                                            connected = true
                                            status = "انضممت إلى المايك"
                                            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasMicPermission) {
                                                room.localParticipant.setMicrophoneEnabled(true)
                                                micEnabled = true
                                            } else permission.launch(android.Manifest.permission.RECORD_AUDIO)
                                            kotlinx.coroutines.delay(350)
                                            refreshSeats()
                                        } catch (e: Exception) {
                                            error = e.message ?: "تعذر الانضمام للمقعد"
                                        } finally { connecting = false }
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center) {
                            if (!profile?.avatarUrl.isNullOrBlank()) AsyncImage(model = profile?.avatarUrl, contentDescription = profile?.displayName,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(if (profile == null) Icons.Default.Add else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            if (isSpeaking) Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(13.dp))
                        }
                        Text(profile?.displayName ?: "مقعد ${i + 1}", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        if (profile != null) {
                            Text(
                                "⭐ LV ${profile.accountLevel.coerceIn(1, 100)}",
                                color = Color(0xFFFFD36A),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .background(Color(0x55301850), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Chat visibility is controlled from the bottom toolbar to keep mic seats clear.
            if (chatOpen) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Bottom) {
                    chat.takeLast(5).forEach { message ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(29.dp).clip(CircleShape).background(Color(0xFFD2E7F8))
                                .clickable { if (message.userId.isNotBlank()) selectedChatUserId = message.userId },
                                contentAlignment = Alignment.Center) {
                                if (!message.avatarUrl.isNullOrBlank()) AsyncImage(
                                    model = message.avatarUrl, contentDescription = message.displayName,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                ) else Icon(Icons.Default.AccountCircle, null, tint = Color(0xFF176B78))
                            }
                            Column(Modifier.padding(start = 7.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color(0x55304F7A)).clickable {
                                    if (message.userId.isNotBlank()) selectedChatUserId = message.userId
                                }.padding(horizontal = 11.dp, vertical = 6.dp)) {
                                Text(message.displayName, color = Color(0xFF9FE8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(message.text, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFFFD0D8), fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            } else Spacer(Modifier.weight(1f))

            // Single compact bottom toolbar matching the supplied live-room reference.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.5.dp, Color(0xFFB44BFF), RoundedCornerShape(32.dp))
                    .background(Color(0xDD100B35))
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎁", fontSize = 27.sp, modifier = Modifier.clickable { quickGift = false; giftsOpen = true }.padding(horizontal = 7.dp, vertical = 5.dp))
                Text("🌹", fontSize = 26.sp, modifier = Modifier.clickable { quickGift = true; giftsOpen = true }.padding(horizontal = 7.dp, vertical = 5.dp))
                Text("🏷️", fontSize = 25.sp, modifier = Modifier.clickable {
                    scope.launch {
                        giftHistory = runCatching { withContext(Dispatchers.IO) { repository.loadLiveGiftHistory(roomName) } }.getOrDefault(emptyList())
                        giftHistoryOpen = true
                    }
                }.padding(horizontal = 7.dp, vertical = 5.dp))
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.Menu, "الخيارات الإضافية", tint = Color.White, modifier = Modifier.size(29.dp))
                }
                Text(
                    "الإنضمام ✋",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(28.dp))
                        .background(Color(0x664A4D91))
                        .clickable(enabled = connected && !connecting && !canSpeak) {
                            connecting = true
                            scope.launch {
                                try {
                                    val credentials = withContext(Dispatchers.IO) { repository.joinLiveRoomSeat(roomName) }
                                    room.disconnect()
                                    room.connect(credentials.serverUrl, credentials.participantToken)
                                    canSpeak = true
                                    connected = true
                                    status = "انضممت إلى المايك"
                                    val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasMicPermission) {
                                        room.localParticipant.setMicrophoneEnabled(true)
                                        micEnabled = true
                                    } else permission.launch(android.Manifest.permission.RECORD_AUDIO)
                                    kotlinx.coroutines.delay(350)
                                    refreshSeats()
                                } catch (e: Exception) { error = e.message ?: "تعذر طلب الصعود للمايك" }
                                finally { connecting = false }
                            }
                        }
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                )
                IconButton(onClick = { chatOpen = true; chatComposerOpen = true }) {
                    Icon(Icons.Default.ChatBubbleOutline, "كتابة تعليق أو دردشة", tint = Color.White, modifier = Modifier.size(27.dp))
                }
            }

        }
    }
    if (viewersDialog) {
        AlertDialog(
            onDismissRequest = { viewersDialog = false },
            title = { Text("المشاهدون ($viewerCount)") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (viewerProfiles.isEmpty()) Text("لا توجد حسابات ظاهرة حالياً")
                    viewerProfiles.forEach { viewer ->
                        Row(Modifier.fillMaxWidth().clickable { selectedModerationUserId = viewer.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE4EAF3)), contentAlignment = Alignment.Center) {
                                if (!viewer.avatarUrl.isNullOrBlank()) AsyncImage(model = viewer.avatarUrl, contentDescription = viewer.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Icon(Icons.Default.Person, null, tint = Brand)
                            }
                            Text(viewer.displayName, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewersDialog = false }) { Text("إغلاق") } }
        )
    }
    selectedChatUserId?.let { profileUserId ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedChatUserId = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            PublicCommentProfileScreen(repository, profileUserId, onBack = { selectedChatUserId = null })
        }
    }
    if (menuOpen) AlertDialog(
        onDismissRequest = { menuOpen = false },
        title = { Text("قائمة البث") },
        text = { Column {
            TextButton(onClick = { menuOpen = false; viewersDialog = true }) { Text("عرض المشاهدين وإدارة الحسابات") }
            TextButton(onClick = { menuOpen = false; inviteOpen = true }) { Text("دعوة صديق إلى الغرفة") }
            TextButton(onClick = { menuOpen = false; chatOpen = !chatOpen }) { Text(if (chatOpen) "إخفاء الدردشة" else "إظهار الدردشة") }
        } },
        confirmButton = { TextButton(onClick = { menuOpen = false }) { Text("إغلاق") } }
    )
    if (showAudioControls) {
        AlertDialog(
            onDismissRequest = { showAudioControls = false },
            title = { Text("إعدادات المايك 🎙️") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تحكم بمقعدك والصوت أثناء البث")
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val enabled = !room.localParticipant.isMicrophoneEnabled
                                    room.localParticipant.setMicrophoneEnabled(enabled)
                                    micEnabled = enabled
                                    refreshSeats()
                                } catch (e: Exception) { error = e.message ?: "تعذر تغيير حالة المايك" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (micEnabled) Icons.Default.MicOff else Icons.Default.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (micEnabled) "كتم المايك" else "تشغيل المايك")
                    }
                    OutlinedButton(onClick = { audioEffectsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Tune, null)
                        Spacer(Modifier.width(8.dp))
                        Text("مؤثرات الصوت والصدى")
                    }
                    Text("تفاعلات سريعة", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("😀", "❤️", "👏", "🔥", "🌹").forEach { reaction ->
                            Text(reaction, fontSize = 25.sp, modifier = Modifier.clickable {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { repository.sendLiveChatMessage(roomName, reaction) } }
                                    showAudioControls = false
                                }.let { }
                            }.padding(5.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    room.localParticipant.setMicrophoneEnabled(false)
                                    withContext(Dispatchers.IO) { repository.leaveLiveRoomSeat(roomName) }
                                    room.disconnect()
                                    val credentials = withContext(Dispatchers.IO) { repository.requestLiveKitToken(roomName, "viewer") }
                                    room.connect(credentials.serverUrl, credentials.participantToken)
                                    micEnabled = false
                                    canSpeak = false
                                    status = "نزلت من المايك"
                                    showAudioControls = false
                                    refreshSeats()
                                } catch (e: Exception) { error = e.message ?: "تعذر النزول من المايك" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("النزول من المايك")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAudioControls = false }) { Text("إغلاق") } }
        )
    }
    if (audioEffectsOpen) {
        AlertDialog(
            onDismissRequest = { audioEffectsOpen = false },
            title = { Text("مؤثرات الصوت") },
            text = { Text("خيارات دمج الصوت والصدى والإطارات الصوتية تحتاج محرك معالجة صوت إضافي. لم أفعل أزراراً شكلية حتى لا تتوقع أنها تغيّر صوتك فعلياً.") },
            confirmButton = { TextButton(onClick = { audioEffectsOpen = false }) { Text("حسناً") } }
        )
    }
    if (chatComposerOpen) {
        AlertDialog(
            onDismissRequest = { chatComposerOpen = false },
            title = { Text("الدردشة المباشرة") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("اكتب تعليقك") },
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    val message = draft.trim()
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.sendLiveChatMessage(roomName, message) }
                            chat = withContext(Dispatchers.IO) { repository.loadLiveChatMessages(roomName) }
                                .map { LiveChatMessage(it.userId, it.body, it.displayName, it.avatarUrl) }
                            draft = ""
                            chatComposerOpen = false
                        } catch (e: Exception) { error = e.message ?: "تعذر إرسال التعليق" }
                    }
                }) { Text("إرسال") }
            },
            dismissButton = { TextButton(onClick = { chatComposerOpen = false }) { Text("إلغاء") } }
        )
    }
    if (giftsOpen) {
        AlertDialog(
            onDismissRequest = { if (!giftBusy) giftsOpen = false },
            title = { Text(if (quickGift) "إرسال وردة سريعة 🌹" else "إرسال هدية 🎁") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text("اختر المستلم:")
                    (viewerProfiles + seatProfiles).distinctBy { it.id }.filter { it.id != repository.currentUserId() }.forEach { person ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedGiftReceiverId = person.id }
                                .background(if (selectedGiftReceiverId == person.id) Color(0xFFEDE5FF) else Color.Transparent,
                                    RoundedCornerShape(10.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!person.avatarUrl.isNullOrBlank()) AsyncImage(
                                model = person.avatarUrl, contentDescription = person.displayName,
                                modifier = Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Crop
                            ) else Icon(Icons.Default.AccountCircle, null, tint = Brand)
                            Text(person.displayName, modifier = Modifier.padding(start = 8.dp))
                            Spacer(Modifier.weight(1f))
                            if (selectedGiftReceiverId == person.id) Icon(Icons.Default.CheckCircle, null, tint = Brand)
                        }
                    }
                    if ((viewerProfiles + seatProfiles).none { it.id != repository.currentUserId() }) {
                        Text("لا يوجد مستلم ظاهر حالياً. انتظر دخول شخص إلى الغرفة.")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = !giftBusy && selectedGiftReceiverId != null, onClick = {
                            giftBusy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repository.sendLiveGift(roomName, selectedGiftReceiverId!!, "rose") }
                                    giftsOpen = false
                                    error = "تم إرسال الوردة 🌹"
                                } catch (e: Exception) { error = e.message ?: "تعذر إرسال الوردة" }
                                finally { giftBusy = false }
                            }
                        }) { Text("🌹 وردة · 10 عملات") }
                        if (!quickGift) TextButton(enabled = !giftBusy && selectedGiftReceiverId != null, onClick = {
                            giftBusy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repository.sendLiveGift(roomName, selectedGiftReceiverId!!, "gift_box") }
                                    giftsOpen = false
                                    error = "تم إرسال الهدية 🎁"
                                } catch (e: Exception) { error = e.message ?: "تعذر إرسال الهدية" }
                                finally { giftBusy = false }
                            }
                        }) { Text("🎁 صندوق · 25 عملة") }
                        if (!quickGift) TextButton(enabled = !giftBusy && selectedGiftReceiverId != null, onClick = {
                            giftBusy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repository.sendLiveGift(roomName, selectedGiftReceiverId!!, "clap") }
                                    giftsOpen = false
                                    error = "تم إرسال التصفيق 👏"
                                } catch (e: Exception) { error = e.message ?: "تعذر إرسال الهدية" }
                                finally { giftBusy = false }
                            }
                        }) { Text("👏 تصفيق · 5 عملات") }
                    }
                    Text("رصيد العملات يُخصم عند إرسال الهدية، وتُضاف نقاط دعم لمستواك.")
                }
            },
            confirmButton = { TextButton(onClick = { giftsOpen = false }) { Text("إغلاق") } }
        )
    }
    if (giftHistoryOpen) AlertDialog(
        onDismissRequest = { giftHistoryOpen = false },
        title = { Text("العروض والحقائب — سجل الهدايا") },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            if (giftHistory.isEmpty()) Text("ماكو هدايا مسجلة بهذا البث بعد.")
            giftHistory.forEach { Text(it, modifier = Modifier.padding(vertical = 5.dp), fontSize = 18.sp) }
        } },
        confirmButton = { TextButton(onClick = { giftHistoryOpen = false }) { Text("إغلاق") } }
    )
    selectedModerationUserId?.let { targetId ->
        val targetProfile = viewerProfiles.firstOrNull { it.id == targetId }
        val isOwner = repository.currentUserId() == host?.ownerId
        AlertDialog(
            onDismissRequest = { if (!moderationBusy) selectedModerationUserId = null },
            title = { Text("إدارة ${targetProfile?.displayName ?: "المشارك"}") },
            text = {
                Column {
                    Text(if (isOwner) "اختر الإجراء المطلوب لهذا الحساب." else "يمكن للمضيف أو المشرف إدارة المشاركين.")
                    moderationMessage?.let { Text(it, color = Brand, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                Row {
                    if (isOwner) TextButton(enabled = !moderationBusy, onClick = {
                        moderationBusy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { repository.moderateLiveRoom(roomName, targetId, "promote_admin") }; moderationMessage = "تم تعيينه مشرفاً" }
                            catch (e: Exception) { moderationMessage = e.message ?: "تعذر تعيين المشرف" }
                            finally { moderationBusy = false }
                        }
                    }) { Text("جعله مشرف") }
                    TextButton(enabled = !moderationBusy, onClick = {
                        moderationBusy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { repository.moderateLiveRoom(roomName, targetId, "mute") }; moderationMessage = "تم كتم المشارك" }
                            catch (e: Exception) { moderationMessage = e.message ?: "تعذر الكتم" }
                            finally { moderationBusy = false }
                        }
                    }) { Text("كتم") }
                    TextButton(enabled = !moderationBusy, onClick = {
                        moderationBusy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { repository.moderateLiveRoom(roomName, targetId, "kick") }; moderationMessage = "تم طرد المشارك"; selectedModerationUserId = null; refreshSeats() }
                            catch (e: Exception) { moderationMessage = e.message ?: "تعذر الطرد" }
                            finally { moderationBusy = false }
                        }
                    }) { Text("طرد") }
                }
            },
            dismissButton = { TextButton(enabled = !moderationBusy, onClick = { selectedModerationUserId = null; moderationMessage = null }) { Text("إغلاق") } }
        )
    }
    if (inviteOpen) AlertDialog(onDismissRequest = { inviteOpen = false }, title = { Text("دعوة إلى الغرفة") },
        text = { Text("شارك اسم الغرفة مع أصدقائك: $roomName") },
        confirmButton = { TextButton(onClick = { val clip = android.content.ClipData.newPlainText("room", roomName); (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip); inviteOpen = false }) { Text("نسخ") } },
        dismissButton = { TextButton(onClick = { inviteOpen = false }) { Text("إغلاق") } })
}

@Composable
private fun VideoLiveRoomScreen(
    repository: SupabaseRepository,
    roomName: String,
    isHost: Boolean,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val context = LocalContext.current

    // Register LiveKit's CameraX provider before the room attempts to open
    // the camera. This follows the official Android SDK sample and avoids
    // relying on the fallback camera capturer on devices with CameraX quirks.
    val cameraProvider = remember(context.applicationContext) {
        CameraXHelper.createCameraProvider(ProcessLifecycleOwner.get())
    }
    DisposableEffect(cameraProvider) {
        if (cameraProvider.isSupported(context.applicationContext)) {
            CameraCapturerUtils.registerCameraProvider(cameraProvider)
        }
        onDispose {
            if (cameraProvider.isSupported(context.applicationContext)) {
                CameraCapturerUtils.unregisterCameraProvider(cameraProvider)
            }
        }
    }

    val liveRoom = remember(roomName) { LiveKit.create(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var cameraEnabled by remember(roomName) { mutableStateOf(false) }
    var micEnabled by remember(roomName) { mutableStateOf(false) }
    var connectionState by remember(roomName) { mutableStateOf("جارٍ الاتصال بالغرفة...") }
    var connectionError by remember(roomName) { mutableStateOf<String?>(null) }
    var connected by remember(roomName) { mutableStateOf(false) }
    var credentials by remember(roomName) { mutableStateOf<LiveKitCredentials?>(null) }
    var remoteVideoTrack by remember(roomName) { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember(roomName) { mutableStateOf<VideoTrack?>(null) }
    var showChat by remember(roomName) { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraGranted = grants[android.Manifest.permission.CAMERA] == true
        val microphoneGranted = grants[android.Manifest.permission.RECORD_AUDIO] == true

        if (!cameraGranted && !microphoneGranted) {
            connectionError = "لم يتم منح صلاحية الكاميرا أو المايك."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            delay(350)
            if (cameraGranted) {
                try {
                    cameraEnabled = liveRoom.localParticipant.setCameraEnabled(true)
                    localVideoTrack = liveRoom.localParticipant
                        .getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                    if (!cameraEnabled || localVideoTrack == null) {
                        cameraEnabled = false
                        connectionError = "تعذر تشغيل الكاميرا. حاول مرة ثانية."
                    }
                } catch (e: Throwable) {
                    cameraEnabled = false
                    localVideoTrack = null
                    connectionError = e.message ?: "تعذر تشغيل الكاميرا"
                }
            }
            if (microphoneGranted) {
                try {
                    micEnabled = liveRoom.localParticipant.setMicrophoneEnabled(true)
                    if (!micEnabled) connectionError = "تعذر تشغيل المايك."
                } catch (e: Throwable) {
                    micEnabled = false
                    if (connectionError == null) connectionError = e.message ?: "تعذر تشغيل المايك"
                }
            }
            if (cameraEnabled && micEnabled) connectionError = null
        }
    }
    DisposableEffect(liveRoom) {
        onDispose { liveRoom.disconnect() }
    }
    LaunchedEffect(liveRoom) {
        liveRoom.events.collect { event ->
            when (event) {
                is RoomEvent.TrackSubscribed -> {
                    val track = event.track
                    if (track is VideoTrack) remoteVideoTrack = track
                }
                is RoomEvent.TrackUnsubscribed -> {
                    if (event.track == remoteVideoTrack) remoteVideoTrack = null
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(roomName) {
        try {
            connectionState = "جارٍ جلب تصريح الدخول..."
            // Bound the complete startup sequence so the room cannot remain stuck forever.
            withTimeout(30_000) {
                val auth = withContext(Dispatchers.IO) { repository.requestLiveKitToken(roomName, if (isHost) "host" else "viewer") }
                credentials = auth
                connectionState = "جارٍ الاتصال بخادم البث..."
                liveRoom.connect(auth.serverUrl, auth.participantToken)
                connected = true
                connectionState = "متصل بالغرفة"
            }
            if (isHost) {
                // Hosts enter the video room with camera and microphone requested
                // together; the camera is not left for a second manual activation.
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        } catch (e: Exception) {
            connected = false
            connectionState = "فشل الاتصال"
            connectionError = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "انتهت مهلة الاتصال بخادم البث. تحقق من رابط LiveKit والإنترنت."
                e.message.isNullOrBlank() -> "تعذر الاتصال بالغرفة. أعد المحاولة."
                else -> e.message ?: "تعذر الاتصال بالغرفة"
            }
        }
    }
    var message by remember(roomName) { mutableStateOf("") }
    var chatItems by remember(roomName) { mutableStateOf(listOf("أحمد انضم إلى الغرفة", "نور: هلا بالجميع ❤️")) }
    var showMenu by remember(roomName) { mutableStateOf(false) }
    var showParticipants by remember(roomName) { mutableStateOf(false) }
    var followed by remember(roomName) { mutableStateOf(false) }
    var compactChat by remember(roomName) { mutableStateOf(true) }
    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF120B1D))
            .statusBarsPadding()
    ) {
        // Full-screen vertical camera stage, like the supplied reference.
        LiveKitRemoteVideo(
            if (isHost) localVideoTrack else remoteVideoTrack,
            Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.48f),
                        0.22f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.68f)
                    )
                )
        )

        // Top bar: close, viewers, room identity, follow, share and expand.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "إنهاء البث", tint = Color.White)
            }
            TextButton(onClick = { showParticipants = !showParticipants }) {
                Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text(" ${if (showParticipants) "إخفاء" else "المشاهدون"}", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(roomName.take(22), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (connected) "● مباشر الآن" else connectionState,
                    color = if (connected) Color(0xFF64E6B5) else Color.White.copy(alpha = .85f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { followed = !followed },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (followed) Color.DarkGray else Color(0xFFFF315C)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(if (followed) "✓ متابَع" else "+ متابعة", color = Color.White, fontSize = 12.sp)
            }
            IconButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("رابط الغرفة", roomName))
                connectionError = "تم نسخ رمز الغرفة"
            }) { Icon(Icons.Default.Share, "مشاركة الغرفة", tint = Color.White) }
            IconButton(onClick = { compactChat = !compactChat }) {
                Icon(Icons.Default.Expand, "تبديل عرض الشاشة", tint = Color.White)
            }
        }

        if (showParticipants) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 12.dp)
                    .widthIn(min = 150.dp, max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = .72f)).padding(12.dp)
            ) {
                Text("المتواجدون في البث", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = Color.White)
                    Text(if (isHost) "أنت • المضيف" else "المضيف", color = Color.White, fontSize = 13.sp)
                }
                Text("عدد المشاهدين يظهر حسب بيانات الغرفة المتاحة", color = Color.LightGray, fontSize = 10.sp)
            }
        }

        // Host / viewer state and connection feedback.
        if (connectionError != null) {
            Text(
                connectionError!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 66.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xCC8B1E3F))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 12.sp
            )
        }
        if (!isHost && remoteVideoTrack == null) {
            Text(
                "بانتظار تشغيل كاميرا المضيف...",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .55f))
                    .padding(14.dp)
            )
        }

        // Live chat overlays the lower part of the video.
        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth(if (compactChat) 0.82f else 1f)
                .padding(start = 12.dp, end = 12.dp, bottom = 78.dp)
        ) {
            if (showChat) {
                chatItems.takeLast(5).forEach { line ->
                    Row(
                        Modifier.padding(top = 5.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = .38f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(20.dp))
                        Text(line, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("اكتب تعليقاً...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = .55f),
                        unfocusedBorderColor = Color.White.copy(alpha = .35f),
                        focusedContainerColor = Color.Black.copy(alpha = .35f),
                        unfocusedContainerColor = Color.Black.copy(alpha = .35f),
                        focusedPlaceholderColor = Color.White.copy(alpha = .7f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = .7f)
                    )
                )
                IconButton(onClick = {
                    if (message.isNotBlank()) {
                        chatItems = chatItems + ("أنت: " + message.trim())
                        message = ""
                    }
                }) { Icon(Icons.Default.Send, "إرسال التعليق", tint = Color.White) }
            }
        }

        // Functional controls: gifts, chat visibility, microphone and camera.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE100B16))))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRequireLogin) {
                Icon(Icons.Default.CardGiftcard, "الهدايا", tint = Color(0xFFFF3D70), modifier = Modifier.size(27.dp))
            }
            IconButton(onClick = { showChat = !showChat }) {
                Icon(Icons.Default.Chat, "إظهار أو إخفاء التعليقات", tint = Color.White, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = {
                if (!connected) connectionError = "انتظر اتصال الغرفة أولاً"
                else if (credentials?.role == "viewer") connectionError = "أنت مشاهد حالياً؛ المضيف يمنحك صلاحية المايك"
                else if (micEnabled) {
                    scope.launch {
                        try { micEnabled = !liveRoom.localParticipant.setMicrophoneEnabled(false) }
                        catch (e: Exception) { connectionError = e.message ?: "تعذر إيقاف المايك" }
                    }
                } else permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
            }) {
                Icon(if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff, "تشغيل أو كتم المايك",
                    tint = if (micEnabled) Color(0xFF36E0D0) else Color.White, modifier = Modifier.size(27.dp))
            }
            IconButton(onClick = {
                if (!connected) connectionError = "انتظر اتصال الغرفة أولاً"
                else if (credentials?.role == "viewer") connectionError = "أنت مشاهد حالياً؛ المضيف يمنحك صلاحية الكاميرا"
                else if (cameraEnabled) {
                    scope.launch {
                        try {
                            cameraEnabled = !liveRoom.localParticipant.setCameraEnabled(false)
                            localVideoTrack = null
                        } catch (e: Exception) { connectionError = e.message ?: "تعذر إيقاف الكاميرا" }
                    }
                } else permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
            }) {
                Icon(if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    "تشغيل أو إيقاف الكاميرا", tint = if (cameraEnabled) Color(0xFFFF315C) else Color.White,
                    modifier = Modifier.size(27.dp))
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ExitToApp, "مغادرة البث", tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun LiveKitRemoteVideo(track: VideoTrack?, modifier: Modifier = Modifier) {
    if (track != null) {
        VideoTrackView(videoTrack = track, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(72.dp))
            Text("بانتظار فيديو المضيف...", color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
    }
}

@Composable
private fun PublicCommentProfileScreen(repository: SupabaseRepository, userId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember(userId) { mutableStateOf<SupabaseRepository.DiscoverProfile?>(null) }
    var stats by remember(userId) { mutableStateOf<TikProfileStats?>(null) }
    var following by remember(userId) { mutableStateOf(false) }
    var loading by remember(userId) { mutableStateOf(true) }
    var followBusy by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        try {
            val result = withContext(Dispatchers.IO) {
                Triple(repository.loadPublicProfile(userId), repository.loadPublicProfileStats(userId), repository.isFollowing(userId))
            }
            profile = result.first
            stats = result.second
            following = result.third
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل الحساب"
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth(0.88f)
            .heightIn(max = 610.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Close, "إغلاق الملف", tint = Color(0xFF22222B))
            }
            Spacer(Modifier.weight(1f))
            Text("الملف الشخصي", color = Color(0xFF22222B), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxWidth().height(250.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                Text(error!!, color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(142.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(104.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFF291047), Color(0xFF0A0B25))))
                    )
                    val avatar = profile?.avatarUrl
                    Box(
                        Modifier.align(Alignment.BottomCenter).size(94.dp)
                            .clip(CircleShape).background(Color(0xFFE8E8EC))
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = "صورة ${profile?.displayName}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, null, tint = Brand, modifier = Modifier.size(55.dp))
                        }
                    }
                }

                Spacer(Modifier.height(9.dp))
                Text(
                    profile?.displayName.orEmpty(), color = Color(0xFF17171D),
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "@${profile?.username.orEmpty()}", color = Color(0xFF85858E), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "⭐ المستوى LV ${profile?.accountLevel ?: 1}", color = Color(0xFF8A3FC2),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp), textAlign = TextAlign.Center
                )
                Text(
                    "ID: $userId", color = Color(0xFF9A9AA3), fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp), textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(stats?.likes ?: 0, "إعجابات")
                    ProfileStat(stats?.following ?: 0, "يتابع")
                    ProfileStat(stats?.followers ?: 0, "متابعون")
                    ProfileStat(stats?.posts?.size ?: 0, "منشور")
                }

                Spacer(Modifier.height(19.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB8B8BE))
                    ) {
                        Text("إغلاق الملف", color = Color(0xFFD45C83), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (!followBusy) {
                                followBusy = true
                                scope.launch {
                                    try {
                                        following = withContext(Dispatchers.IO) { repository.toggleFollow(userId) }
                                    } catch (e: Exception) {
                                        error = e.message ?: "تعذر تحديث المتابعة"
                                    } finally {
                                        followBusy = false
                                    }
                                }
                            }
                        },
                        enabled = !followBusy && repository.currentUserId() != userId,
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (following) Color(0xFFE7E7EA) else Color(0xFF16C8C3),
                            contentColor = if (following) Color(0xFF22222B) else Color.White
                        ),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            if (followBusy) "..." else if (following) "متابَع ✓" else "＋ متابعة",
                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
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
    videos: List<TikVideo>, loading: Boolean, error: String?, onRefresh: () -> Unit, replaySignal: Int,
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
                VideoFeedPage(video = videos[page], repository = repository, active = pagerState.currentPage == page, replaySignal = replaySignal, signedIn = signedIn, onLogin = onLogin)
            }
        }
    }
}

@Composable
private fun VideoFeedPage(video: TikVideo, repository: SupabaseRepository, active: Boolean, replaySignal: Int, signedIn: Boolean, onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liked by remember(video.id) { mutableStateOf(false) }
    var saved by remember(video.id) { mutableStateOf(false) }
    var following by remember(video.userId) { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    var muted by remember(video.id) { mutableStateOf(false) }
    var likeCount by remember(video.id) { mutableIntStateOf(0) }
    var commentCount by remember(video.id) { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var selectedAdminSection by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(video.id, signedIn, active) {
        // Pager pre-composes nearby pages. Do not send database requests for pages
        // the user is not watching yet.
        if (!active) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            likeCount = runCatching { repository.videoLikeCount(video.id) }.getOrDefault(0)
            commentCount = runCatching { repository.videoCommentCount(video.id) }.getOrDefault(0)
            if (signedIn) {
                liked = runCatching { repository.isLiked(video.id) }.getOrDefault(false)
                saved = runCatching { repository.isSaved(video.id) }.getOrDefault(false)
                following = runCatching { repository.isFollowing(video.userId) }.getOrDefault(false)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video.mediaType == "image") {
            AsyncImage(model = video.videoUrl, contentDescription = video.caption,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else NativeVideoPlayer(video.videoUrl, active, replaySignal, muted)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 9.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF292929)).clickable {
                    if (!signedIn) onLogin() else actionError = "افتح الحساب من اسم الناشر"
                }, contentAlignment = Alignment.Center) {
                    if (!video.creatorAvatar.isNullOrBlank()) AsyncImage(
                        model = video.creatorAvatar, contentDescription = "صورة صاحب الحساب",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    ) else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(29.dp))
                }
                if (video.userId != repository.currentUserId()) {
                    Box(Modifier.align(Alignment.BottomCenter).size(19.dp).clip(CircleShape)
                        .background(if (following) Color(0xFF21C7B7) else Brand)
                        .clickable(enabled = !followBusy) {
                            if (!signedIn) onLogin() else scope.launch {
                                followBusy = true
                                try { following = withContext(Dispatchers.IO) { repository.toggleFollow(video.userId) } }
                                catch (e: Exception) { actionError = e.message ?: "تعذرت المتابعة" }
                                finally { followBusy = false }
                            }
                        }, contentAlignment = Alignment.Center) {
                        Icon(if (following) Icons.Default.Check else Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(enabled = !busy, onClick = {
                    if (!signedIn) onLogin() else scope.launch {
                        busy = true
                        try {
                            liked = withContext(Dispatchers.IO) { repository.toggleLike(video.id) }
                            likeCount = withContext(Dispatchers.IO) { repository.videoLikeCount(video.id) }
                        } catch (e: Exception) { actionError = e.message ?: "تعذر تسجيل الإعجاب" }
                        finally { busy = false }
                    }
                }) {
                    Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "إعجاب", tint = if (liked) Brand else Color.White, modifier = Modifier.size(33.dp))
                }
                Text(if (likeCount >= 1000) String.format("%.1fK", likeCount / 1000f) else likeCount.toString(),
                    color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { commentsOpen = true }) {
                    Icon(Icons.Default.ChatBubbleOutline, "التعليقات", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(if (commentCount >= 1000) String.format("%.1fK", commentCount / 1000f) else commentCount.toString(),
                    color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "شاهد هذا الفيديو على الرافدين تيك: ${video.videoUrl}")
                        }
                        context.startActivity(Intent.createChooser(send, "مشاركة الفيديو"))
                    }.onFailure { actionError = "تعذرت مشاركة الفيديو" }
                }) {
                    Icon(Icons.Default.Share, "مشاركة", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("مشاركة", color = Color.White, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { muted = !muted }) {
                    Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                        "الصوت والموسيقى", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(if (muted) "صامت" else "الصوت", color = Color.White, fontSize = 11.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 78.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${video.creatorName.ifBlank { "مستخدم" }}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (video.userId != repository.currentUserId()) {
                    Text(if (following) " ✓" else "  متابعة +", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 9.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0x55333333)).clickable(enabled = !followBusy) {
                                if (!signedIn) onLogin() else scope.launch {
                                    followBusy = true
                                    try { following = withContext(Dispatchers.IO) { repository.toggleFollow(video.userId) } }
                                    catch (e: Exception) { actionError = e.message ?: "تعذرت المتابعة" }
                                    finally { followBusy = false }
                                }
                            }.padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
            if (video.caption.isNotBlank()) Text(video.caption, color = Color.White, fontSize = 14.sp, maxLines = 3)
            Text("♪ الصوت الأصلي · ${video.creatorName.ifBlank { "الرافدين تيك" }}",
                color = Color.White.copy(alpha = .88f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            actionError?.let { Text(it, color = Color.White, fontSize = 11.sp) }
        }
    }
    if (commentsOpen) CommentDialog(video.id, repository, signedIn, onLogin, onDismiss = {
        commentsOpen = false
        scope.launch { commentCount = withContext(Dispatchers.IO) { repository.videoCommentCount(video.id) } }
    })
}

@Composable
private fun NativeVideoPlayer(url: String, active: Boolean, replaySignal: Int, muted: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            // Prepare only when this page becomes active; avoid buffering every
            // off-screen pager page at the same time.
        }
    }
    var previousReplaySignal by remember(url) { mutableIntStateOf(replaySignal) }
    LaunchedEffect(active, replaySignal, player) {
        player.volume = if (muted) 0f else 1f
        if (active && replaySignal != previousReplaySignal) player.seekTo(0)
        previousReplaySignal = replaySignal
        if (active) {
            if (player.playbackState == ExoPlayer.STATE_IDLE) player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }
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
    var selectedCommentProfile by remember { mutableStateOf<String?>(null) }

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
        if (selectedCommentProfile != null) {
            PublicCommentProfileScreen(repository, selectedCommentProfile!!, onBack = { selectedCommentProfile = null })
        } else Column(
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
                            if (!item.avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = item.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE9E9ED))
                                        .clickable { if (item.userId.isNotBlank()) selectedCommentProfile = item.userId })
                            } else {
                                Icon(Icons.Default.AccountCircle, null, tint = Muted,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .clickable { if (item.userId.isNotBlank()) selectedCommentProfile = item.userId })
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { if (item.userId.isNotBlank()) selectedCommentProfile = item.userId })
                                if (item.username.isNotBlank()) Text("@${item.username}", fontSize = 11.sp, color = Muted, modifier = Modifier.clickable { if (item.userId.isNotBlank()) selectedCommentProfile = item.userId })
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
private fun UploadScreen(repository: SupabaseRepository, onBack: () -> Unit, onUploaded: () -> Unit) {
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
        Modifier.fillMaxSize().background(Color.Black).imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الرافدين تيك", color = Brand, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))
            Box(
                Modifier.size(82.dp).background(Brand, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "إنشاء منشور", tint = Color.Black, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("إنشاء منشور", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("التقط صورة أو فيديو أو اختر من الاستديو", color = Color.LightGray, fontSize = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(26.dp))
            Text("فلتر الصورة", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("طبيعي", "دافئ", "أبيض وأسود", "زاهي").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Brand,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Black,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = Color.Gray,
                            selectedBorderColor = Brand
                        )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = caption, onValueChange = { caption = it },
                placeholder = { Text("اكتب وصفاً (اختياري)", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth().height(112.dp),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Gray, unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black,
                    cursorColor = Brand
                )
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { cameraPhoto.launch(null) }, enabled = !uploading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text("التقاط صورة", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { cameraVideo.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)) },
                enabled = !uploading, modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29292F)),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text("تصوير فيديو", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Videocam, null, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { photoPicker.launch("image/*") }, enabled = !uploading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("اختيار صورة من الاستديو", color = Brand, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Image, null, tint = Brand)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { videoPicker.launch("video/*") }, enabled = !uploading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("اختيار فيديو من الاستديو", color = Brand, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.VideoLibrary, null, tint = Brand)
            }
            if (uploading) CircularProgressIndicator(color = Brand, modifier = Modifier.padding(12.dp))
            message?.let { Text(it, color = if (it.contains("نجاح") || it.contains("تم")) Brand else Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProfileScreen(repository: SupabaseRepository, onSignOut: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        ProfileEditScreen(repository, onBack = { editing = false }, onSaved = { editing = false }, onSignOut = onSignOut)
    } else {
        ProfileHomeScreen(repository, onEdit = { editing = true }, onSignOut = onSignOut)
    }
}


@Composable
private fun AdminDashboardScreen(repository: SupabaseRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedAdminSection by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun refreshSummary() {
        scope.launch {
            loading = true
            error = null
            try { summary = withContext(Dispatchers.IO) { repository.loadAdminDashboardSummary() } }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل الإحصائيات" }
            finally { loading = false }
        }
    }
    fun searchUsers() {
        if (query.trim().length < 2) {
            actionError = "اكتب حرفين على الأقل للبحث"
            return
        }
        scope.launch {
            searching = true
            actionError = null
            actionMessage = null
            try { users = withContext(Dispatchers.IO) { repository.adminSearchUsers(query) } }
            catch (e: Exception) { actionError = e.message ?: "تعذر البحث" }
            finally { searching = false }
        }
    }
    fun runUserAction(userId: String, success: String, action: () -> Unit) {
        scope.launch {
            actionError = null
            actionMessage = null
            try {
                withContext(Dispatchers.IO) { action() }
                actionMessage = success
                users = withContext(Dispatchers.IO) { repository.adminSearchUsers(query) }
                refreshSummary()
            } catch (e: Exception) { actionError = e.message ?: "تعذر تنفيذ الإجراء" }
        }
    }

    LaunchedEffect(Unit) { refreshSummary() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7FA)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("لوحة إدارة الرافدين تيك", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            TextButton(onClick = { refreshSummary() }, enabled = !loading) { Text("تحديث") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مرحباً بك، المدير الرئيسي", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("إحصائيات التطبيق", color = Muted)
            if (loading && summary == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshSummary() }) { Text("إعادة المحاولة") }
            } else {
                val statsItems = listOf(
                    "المستخدمون" to (summary?.optInt("users", 0) ?: 0),
                    "الفيديوهات" to (summary?.optInt("videos", 0) ?: 0),
                    "البثوث النشطة" to (summary?.optInt("live_rooms", 0) ?: 0),
                    "التعليقات" to (summary?.optInt("comments", 0) ?: 0),
                    "طلبات شحن معلّقة" to (summary?.optInt("pending_topups", 0) ?: 0)
                )
                statsItems.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { item ->
                            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(item.first, color = Muted, fontSize = 12.sp)
                                    Text(item.second.toString(), color = Brand, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text("إدارة الحسابات", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("ابحث باسم المستخدم أو الاسم الظاهر، ثم اختر الإجراء المناسب.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم المستخدم أو الاسم") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { searchUsers() }, enabled = !searching) {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, contentDescription = "بحث")
                    }
                }
            )
            Button(onClick = { searchUsers() }, modifier = Modifier.fillMaxWidth(), enabled = !searching) {
                Text(if (searching) "جاري البحث..." else "بحث عن حساب")
            }
            actionMessage?.let { Text(it, color = Color(0xFF16834A), fontWeight = FontWeight.SemiBold) }
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (users.isEmpty() && query.trim().length >= 2 && !searching) {
                Text("لا توجد نتائج حالياً. اضغط بحث عن حساب.", color = Muted, fontSize = 13.sp)
            }
            users.forEach { user ->
                val userId = user.optString("id")
                val username = user.optString("username").ifBlank { "بدون اسم مستخدم" }
                val displayName = user.optString("display_name").ifBlank { username }
                val status = user.optString("account_status", "active")
                val role = user.optString("admin_role")
                val vipUntil = user.optString("vip_until")
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text("@$username", color = Muted, fontSize = 12.sp)
                        Text("الحالة: $status   •   المستوى: ${user.optInt("account_level", 1)}   •   Coins: ${user.optLong("coin_balance", 0)}", color = Muted, fontSize = 12.sp)
                        if (role.isNotBlank()) Text("الصلاحية: $role", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (vipUntil.isNotBlank() && vipUntil != "null") Text("VIP لغاية: $vipUntil", color = Color(0xFF8A5A00), fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val next = if (status == "active") "suspended" else "active"
                                    runUserAction(userId, if (next == "active") "تم تفعيل الحساب" else "تم إيقاف الحساب") {
                                        repository.adminSetAccountStatus(userId, next, if (next == "active") null else "إيقاف من لوحة الإدارة")
                                    }
                                }
                            ) { Text(if (status == "active") "إيقاف" else "تفعيل") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val nextVip = if (vipUntil.isNotBlank() && vipUntil != "null") null
                                        else java.time.Instant.now().plusSeconds(30L * 24 * 60 * 60).toString()
                                    runUserAction(userId, if (nextVip == null) "تم إلغاء VIP" else "تم منح VIP لمدة 30 يوماً") {
                                        repository.adminSetVip(userId, nextVip)
                                    }
                                }
                            ) { Text(if (vipUntil.isNotBlank() && vipUntil != "null") "إلغاء VIP" else "منح VIP") }
                        }
                        when (role) {
                            "super_admin" -> Text("مدير رئيسي — لا يمكن تغيير هذه الصلاحية من هنا", color = Muted, fontSize = 12.sp)
                            "admin", "moderator" -> OutlinedButton(
                                onClick = { runUserAction(userId, "تم سحب صلاحية الإدارة") { repository.adminRevokeRole(userId) } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("سحب صلاحية المدير") }
                            else -> OutlinedButton(
                                onClick = { runUserAction(userId, "تم تعيينه مشرفاً مساعداً") { repository.adminGrantRole(userId, "moderator") } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("تعيينه مشرفاً مساعداً") }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFE7E7EC))
            Text("باقي أقسام الإدارة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            listOf(
                "إدارة الفيديوهات والمنشورات",
                "إدارة البث المباشر",
                "الترويج والإعلانات",
                "المحفظة و Coins وطلبات الشحن",
                "البلاغات والتعليقات",
                "إعدادات التطبيق",
                "سجل الإدارة"
            ).forEach { title ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedAdminSection = title },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("فتح القسم  ›", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            selectedAdminSection?.let { section ->
                val details = when (section) {
                    "إدارة الفيديوهات والمنشورات" -> "قسم مراجعة محتوى الفيديوهات والمنشورات. افتح القسم لتحديث القائمة ومراجعة المحتوى."
                    "إدارة البث المباشر" -> "قسم متابعة غرف البث المباشر وحالتها."
                    "الترويج والإعلانات" -> "قسم الحملات الإعلانية والترويج. لا توجد حالياً جداول حملات إعلانية موثقة في قاعدة بيانات المشروع."
                    "المحفظة و Coins وطلبات الشحن" -> "قسم مراجعة طلبات شحن Coins. اعتماد الطلب يضيف العملات إلى رصيد المستخدم عبر إجراء قاعدة البيانات المحمي."
                    "البلاغات والتعليقات" -> "قسم مراجعة البلاغات والتعليقات. يلزم ربط جدول البلاغات قبل تفعيل إجراءات البلاغات."
                    "إعدادات التطبيق" -> "إعدادات عامة للتطبيق. لم يتم العثور على مخطط إعدادات مركزي في قاعدة البيانات الحالية."
                    else -> "سجل الإجراءات الإدارية. يلزم إنشاء جدول سجل الإدارة وربط تسجيل العمليات قبل عرض السجل."
                }
                AlertDialog(
                    onDismissRequest = { selectedAdminSection = null },
                    title = { Text(section) },
                    text = { Text(details) },
                    confirmButton = {
                        TextButton(onClick = { selectedAdminSection = null }) { Text("إغلاق") }
                    }
                )
            }
            Text("إجراءات الحسابات محمية أيضاً بصلاحيات قاعدة البيانات، وليس بمجرد إخفاء الواجهة.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileHomeScreen(repository: SupabaseRepository, onEdit: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var stats by remember { mutableStateOf<TikProfileStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var extraPosts by remember { mutableStateOf<List<TikVideo>>(emptyList()) }
    var extraLoading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var topupInfoOpen by remember { mutableStateOf(false) }
    var adminPanelOpen by remember { mutableStateOf(false) }
    var hasAdminAccess by remember { mutableStateOf(false) }
    var adminCheckFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasAdminAccess = withContext(Dispatchers.IO) { runCatching { repository.isAdmin() }.getOrDefault(false) }
        adminCheckFinished = true
    }
    if (adminPanelOpen && hasAdminAccess) {
        AdminDashboardScreen(repository, onBack = { adminPanelOpen = false })
        return
    }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.loadProfile() to repository.loadMyProfileStats()
                }
                profile = result.first
                stats = result.second
            } catch (e: Exception) {
                error = e.message ?: "تعذر تحميل الملف الشخصي"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 3 || selectedTab == 4) {
            extraLoading = true
            try {
                extraPosts = withContext(Dispatchers.IO) {
                    if (selectedTab == 3) repository.loadSavedVideos() else repository.loadLikedVideos()
                }
            } catch (_: Exception) {
                extraPosts = emptyList()
            } finally {
                extraLoading = false
            }
        }
    }
    val displayName = profile?.optString("display_name")?.takeIf { it.isNotBlank() } ?: "مستخدم الرافدين"
    val username = profile?.optString("username").orEmpty()
    val bio = profile?.optString("bio").orEmpty()
    val avatar = profile?.optString("avatar_url")?.takeIf { it.isNotBlank() }
    var avatarLoadFailed by remember(avatar) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White).padding(bottom = 82.dp)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل الملف الشخصي", tint = Ink, modifier = Modifier.size(27.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = Ink, modifier = Modifier.size(27.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasAdminAccess && adminCheckFinished) {
                            DropdownMenuItem(text = { Text("لوحة الإدارة") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; adminPanelOpen = true })
                        }
                        DropdownMenuItem(text = { Text("تسجيل الخروج") }, leadingIcon = { Icon(Icons.Default.Logout, null) },
                            onClick = { menuOpen = false; onSignOut() })
                    }
                }
                IconButton(onClick = { /* friend discovery */ }) {
                    Icon(Icons.Default.PersonAddAlt, contentDescription = "إضافة أصدقاء", tint = Ink, modifier = Modifier.size(27.dp))
                }
            }
        }

        if (loading && profile == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else {
            val profileFrame = profile?.optString("profile_frame", "none")
            val dragonBackground = profile?.optString("profile_background", "default") == "fire_dragon"
            val fireMotion = rememberInfiniteTransition(label = "dragon_fire")
            val fireAlpha by fireMotion.animateFloat(
                initialValue = 0.55f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "fire_alpha"
            )
            val dragonOffset by fireMotion.animateFloat(
                initialValue = -3f, targetValue = 5f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "dragon_float"
            )
            // Dragon-themed profile header inspired by the supplied references.
            Box(
                Modifier.fillMaxWidth().height(252.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF100B18), Color(0xFF4A1728), Color(0xFF20102D))))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0x55FFD36B), Color.Transparent), radius = 520f)
                ))
                Text("DRAGON • LV 90", color = Color(0xFFFFD27A), fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart))
                Column(
                    Modifier.align(Alignment.CenterEnd).width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🐉", fontSize = 66.sp, modifier = Modifier.graphicsLayer {
                        translationY = dragonOffset * 1.5f
                    })
                    Text("🔥", fontSize = 26.sp, modifier = Modifier.graphicsLayer { alpha = fireAlpha })
                    Text("حارس الرافدين", color = Color(0xFFFFD98A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    Modifier.align(Alignment.Center).padding(start = 112.dp, end = 94.dp, top = 18.dp, bottom = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(displayName, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, maxLines = 1)
                    Text("@${username.ifBlank { "user" }}", fontSize = 13.sp, color = Color(0xFFE8D9E6), maxLines = 1)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfileStat(stats?.likes ?: 0, "الإعجابات")
                        ProfileStat(stats?.followers ?: 0, "المتابعون")
                        ProfileStat(stats?.following ?: 0, "أتابع")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👑", fontSize = 17.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("الأسد الذهبي", color = Color(0xFFFFD98A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.size(124.dp).align(Alignment.CenterStart).clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(116.dp)
                            .background(Brush.sweepGradient(listOf(
                                Color(0xFFFFF1B8), Color(0xFFB56A19), Color(0xFFFFD76C),
                                Color(0xFF7C3B16), Color(0xFFFFF1B8)
                            )), CircleShape)
                            .padding(5.dp)
                            .border(2.dp, Color(0xFFFFE9A0), CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF21151B)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatar.isNullOrBlank() && !avatarLoadFailed) {
                            AsyncImage(model = avatar, contentDescription = "الصورة الشخصية - اضغط للتغيير",
                                contentScale = ContentScale.Crop, onError = { avatarLoadFailed = true },
                                modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else Icon(Icons.Default.AddAPhoto, "أضف صورة شخصية", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    if (profileFrame == "lion") {
                        Text("🦁", fontSize = 29.sp,
                            modifier = Modifier.align(Alignment.TopStart).offset(x = (-2).dp, y = (-4).dp))
                        Box(Modifier.size(122.dp).border(3.dp, Color(0xFFFFD36B), CircleShape))
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)
                            .size(34.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFFFFE9A0), Color(0xFF9B5716))), CircleShape)
                            .border(2.dp, Color(0xFF4A1728), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "شارة الميكروفون", tint = Color(0xFF4A1728), modifier = Modifier.size(19.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)) {
                    Icon(Icons.Default.Edit, "تعديل الملف الشخصي", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
            OutlinedButton(
                onClick = { topupInfoOpen = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp).height(40.dp)
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("شحن العملات")
            }
            if (topupInfoOpen) {
                val packages = listOf(
                    2480 to 50000, 3980 to 80000, 5250 to 105000,
                    10500 to 210000, 32150 to 630000, 43000 to 840000,
                    49500 to 945000, 84000 to 1575000, 97200 to 1800000,
                    108000 to 2000000
                )
                AlertDialog(
                    onDismissRequest = { topupInfoOpen = false },
                    title = {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                            Text("وكيل شحن معتمد", fontWeight = FontWeight.ExtraBold, color = Ink)
                            Text("شركة الرافدين تيك", color = Brand, fontSize = 13.sp)
                        }
                    },
                    text = {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!avatar.isNullOrBlank()) {
                                    AsyncImage(model = avatar, contentDescription = "صورتك الشخصية",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(46.dp).clip(CircleShape))
                                } else {
                                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFEDE4F7)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = Brand)
                                    }
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(displayName, fontWeight = FontWeight.Bold, color = Ink)
                                    Text("@${username.ifBlank { "user" }}", color = Muted, fontSize = 12.sp)
                                }
                            }
                            Divider()
                            Text("اختر باقة العملات", fontWeight = FontWeight.Bold, color = Ink)
                            packages.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { (coins, dinars) ->
                                        Card(
                                            modifier = Modifier.weight(1f).clickable {
                                                val message = "مرحباً، أريد شحن $coins عملة في حساب الرافدين تيك: @$username"
                                                runCatching {
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("https://wa.me/9647723756476?text=" + java.net.URLEncoder.encode(message, "UTF-8"))
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF3)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9E3D7))
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("%,d 💎".format(coins), fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 15.sp)
                                                Text("%,d د.ع".format(dinars), color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Divider()
                            Text("طريقة الشحن", fontWeight = FontWeight.Bold, color = Ink)
                            Text("1. اختر الباقة المناسبة لحسابك.", color = Muted, fontSize = 12.sp)
                            Text("2. تواصل مع وكيل الشحن على الرقم 07723756476 عبر واتساب.", color = Muted, fontSize = 12.sp)
                            Text("3. أرسل المبلغ إلى سوبر كي، ثم أرسل صورة عملية التحويل إلى الوكيل على واتساب.", color = Muted, fontSize = 12.sp)
                            Text("4. بعد التحقق من التحويل، تُسلّم العملات إلى حسابك.", color = Muted, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    val message = "مرحباً، أريد شحن عملات الرافدين تيك لحساب @$username"
                                    runCatching {
                                        context.startActivity(android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://wa.me/9647723756476?text=" + java.net.URLEncoder.encode(message, "UTF-8"))
                                        ))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18A85B))
                            ) { Text("تواصل مع الوكيل عبر واتساب", color = Color.White) }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { topupInfoOpen = false }) { Text("إغلاق") }
                    }
                )
            }

            val accountLevel = (profile?.optInt("account_level", 1) ?: 1).coerceIn(1, 100)
            val levelPoints = (profile?.optLong("level_points", 0L) ?: 0L).coerceAtLeast(0L)
            val coinBalance = (profile?.optLong("coin_balance", 0L) ?: 0L).coerceAtLeast(0L)
            val pointsIntoLevel = (levelPoints % 1000L).toInt()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp)
                    .background(Color(0xFFF7F1FF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙 $coinBalance عملة", color = Color(0xFF7A35B5), fontWeight = FontWeight.Bold)
                    Text("المستوى LV $accountLevel", color = Color(0xFF7A35B5), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { pointsIntoLevel / 1000f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)),
                    color = Brand,
                    trackColor = Color(0xFFE6D9F2)
                )
                Text(
                    "$pointsIntoLevel / 1000 نقطة للمستوى التالي",
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            Text(
                bio.ifBlank { "أضف نبذة عنك حتى يعرفك الناس" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                maxLines = 1,
                fontSize = 13.sp,
                color = if (bio.isBlank()) Muted else Ink,
                textAlign = TextAlign.Start
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(Icons.Default.GridView, Icons.Default.Lock, Icons.Default.Repeat, Icons.Default.BookmarkBorder, Icons.Default.FavoriteBorder)
                tabs.forEachIndexed { index, icon ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedTab = index }, modifier = Modifier.size(42.dp)) {
                            Icon(icon, contentDescription = null, tint = if (selectedTab == index) Ink else Muted, modifier = Modifier.size(25.dp))
                        }
                        Box(Modifier.width(42.dp).height(3.dp).background(if (selectedTab == index) Ink else Color.Transparent, RoundedCornerShape(2.dp)))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFECECEC))
            when {
                error != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        TextButton(onClick = { load() }) { Text("إعادة المحاولة", color = Brand) }
                    }
                }
                selectedTab == 1 || selectedTab == 2 -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (selectedTab == 1) "المحتوى الخاص يظهر لك وحدك" else "إعادات النشر ستظهر هنا", color = Muted)
                }
                selectedTab == 3 || selectedTab == 4 -> {
                    when {
                        extraLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Brand)
                        }
                        extraPosts.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (selectedTab == 3) "ماكو فيديوهات محفوظة" else "ماكو إعجابات لحد الآن", color = Muted)
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(extraPosts.size, key = { index -> extraPosts[index].id }) { index ->
                                val post = extraPosts[index]
                                Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(Color(0xFF181818))) {
                                    if (post.mediaType == "image") {
                                        AsyncImage(model = post.videoUrl, contentDescription = post.caption,
                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        VideoPostThumbnail(videoUrl = post.videoUrl, contentDescription = post.caption, modifier = Modifier.fillMaxSize())
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                stats?.posts.isNullOrEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoLibrary, null, tint = Muted, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ما نشرت أي فيديو أو صورة لحد الآن", color = Muted)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(stats!!.posts.size, key = { index -> stats!!.posts[index].id }) { index ->
                        val post = stats!!.posts[index]
                        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(Color(0xFF181818))) {
                            if (post.mediaType == "image") {
                                AsyncImage(model = post.videoUrl, contentDescription = post.caption,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                VideoPostThumbnail(
                                    videoUrl = post.videoUrl,
                                    contentDescription = post.caption,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(Modifier.align(Alignment.Center).size(34.dp)
                                    .background(Color.Black.copy(alpha = 0.42f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(27.dp))
                                }
                            }
                            Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("0", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (error == null && stats != null) {
                Text("لقد اطلعت على جميع المنشورات", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun VideoPostThumbnail(
    videoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, videoUrl) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoUrl, emptyMap())
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFF181818)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = "فيديو", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun ProfileStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 11.sp, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ProfileEditScreen(repository: SupabaseRepository, onBack: () -> Unit, onSaved: () -> Unit, onSignOut: () -> Unit) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Ink) }
                Text("تعديل الملف الشخصي", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.size(44.dp))
            }
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
                            onSaved()
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
private fun NotificationsScreen(repository: SupabaseRepository, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<TikNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            items = withContext(Dispatchers.IO) { repository.loadNotifications() }
            withContext(Dispatchers.IO) { repository.markNotificationsRead() }
        } catch (e: Exception) { error = e.message ?: "تعذر تحميل الإشعارات" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(bottom = 82.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Ink) }
            Text("الإشعارات", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = Muted) }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ما عندك إشعارات حالياً", color = Muted) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(items, key = { it.id }) { item ->
                    val action = when (item.kind) { "follow" -> "بدأ بمتابعتك"; "like" -> "أعجب بالفيديو الخاص بك"; "comment" -> "علّق على الفيديو الخاص بك"; else -> "تفاعل مع حسابك" }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (item.kind) { "follow" -> Icons.Default.PersonAdd; "like" -> Icons.Default.Favorite; "comment" -> Icons.Default.ChatBubble; else -> Icons.Default.Notifications }, null, tint = Brand, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.actorName, color = Ink, fontWeight = FontWeight.Bold)
                            Text("@${item.actorUsername} $action", color = Muted, fontSize = 13.sp)
                            Text(item.createdAt.replace("T", " ").take(16), color = Muted, fontSize = 10.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEFEFEF))
                }
            }
        }
    }
}
@Composable
private fun DiscoverScreen(repository: SupabaseRepository) {
    var query by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf<List<SupabaseRepository.DiscoverProfile>>(emptyList()) }
    var videos by remember { mutableStateOf<List<TikVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("ابحث عن حساب أو فيديو") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(top = 56.dp, bottom = 86.dp).padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("بحث عن حسابات وفيديوهات") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = ""; profiles = emptyList(); videos = emptyList(); message = "ابحث عن حساب أو فيديو" }) {
                    Icon(Icons.Default.Close, "مسح")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (query.trim().isNotEmpty()) scope.launch {
                    loading = true
                    message = ""
                    try {
                        val result = withContext(Dispatchers.IO) {
                            Pair(repository.searchProfiles(query.trim()), repository.searchVideos(query.trim()))
                        }
                        profiles = result.first
                        videos = result.second
                        if (profiles.isEmpty() && videos.isEmpty()) message = "ما لقينا نتائج"
                    } catch (e: Exception) { message = e.message ?: "تعذر البحث" }
                    finally { loading = false }
                }
            },
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("بحث") }

        if (message.isNotBlank()) Text(message, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        if (profiles.isNotEmpty()) {
            Text("الحسابات", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(profiles, key = { it.id }) { profile ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(model = profile.avatarUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape))
                        else Icon(Icons.Default.AccountCircle, null, tint = Brand, modifier = Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(profile.displayName, color = Ink, fontWeight = FontWeight.Bold)
                            Text("@${profile.username}", color = Muted, fontSize = 12.sp)
                        }
                        var following by remember(profile.id) { mutableStateOf(false) }
                        var followBusy by remember(profile.id) { mutableStateOf(false) }
                        LaunchedEffect(profile.id) {
                            following = withContext(Dispatchers.IO) { runCatching { repository.isFollowing(profile.id) }.getOrDefault(false) }
                        }
                        OutlinedButton(
                            enabled = !followBusy && repository.currentUserId() != profile.id,
                            onClick = {
                                scope.launch {
                                    followBusy = true
                                    try { following = withContext(Dispatchers.IO) { repository.toggleFollow(profile.id) } }
                                    catch (_: Exception) { message = "تعذر تحديث المتابعة" }
                                    finally { followBusy = false }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(if (following) "تتابعه" else "متابعة", color = if (following) Muted else Brand, fontSize = 12.sp) }
                    }
                    HorizontalDivider(color = Color(0xFFEFEFEF))
                }
            }
        }
        if (videos.isNotEmpty()) {
            Text("الفيديوهات", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(vertical = 10.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(videos, key = { it.id }) { video ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 62.dp, height = 82.dp).background(Color.Black, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (video.mediaType == "image") Icons.Default.Image else Icons.Default.PlayArrow, null, tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(video.caption.ifBlank { "منشور بدون وصف" }, color = Ink, maxLines = 2)
                            Text(video.creatorName, color = Muted, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEFEFEF))
                }
            }
        }
    }
}

@Composable
private fun MessagesScreen(repository: SupabaseRepository) {
    var conversations by remember { mutableStateOf<List<SupabaseRepository.InboxConversation>>(emptyList()) }
    var selectedChat by remember { mutableStateOf<SupabaseRepository.InboxConversation?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            try { conversations = withContext(Dispatchers.IO) { repository.loadInbox() }; error = null }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل الرسائل" }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    if (selectedChat != null) {
        DirectChatScreen(repository, selectedChat!!, onBack = { selectedChat = null; refresh() })
        return
    }
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(top = 56.dp, bottom = 86.dp).padding(horizontal = 16.dp)) {
        Text("الرسائل", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 14.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }
            error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error!!, color = Muted)
                TextButton(onClick = { refresh() }) { Text("إعادة المحاولة", color = Brand) }
            }
            conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ما عندك محادثات حالياً", color = Muted)
            }
            else -> LazyColumn {
                items(conversations, key = { it.userId }) { chat ->
                    Row(Modifier.fillMaxWidth().clickable { selectedChat = chat }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!chat.avatarUrl.isNullOrBlank()) AsyncImage(model = chat.avatarUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
                        else Icon(Icons.Default.AccountCircle, null, tint = Brand, modifier = Modifier.size(52.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(chat.displayName, color = Ink, fontWeight = FontWeight.Bold)
                            Text(chat.lastMessage, color = Muted, maxLines = 1, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEFEFEF))
                }
            }
        }
    }
}

@Composable
private fun DirectChatScreen(repository: SupabaseRepository, chat: SupabaseRepository.InboxConversation, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me = repository.currentUserId()
    var messages by remember(chat.userId) { mutableStateOf<List<SupabaseRepository.DirectMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            try { messages = withContext(Dispatchers.IO) { repository.loadConversation(chat.userId) }; error = null }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل المحادثة" }
            finally { loading = false }
        }
    }
    LaunchedEffect(chat.userId) { refresh() }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Ink) }
            Text(chat.displayName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), reverseLayout = false) {
            items(messages, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.senderId == me) Arrangement.End else Arrangement.Start) {
                    Text(item.body, color = if (item.senderId == me) Color.White else Ink,
                        modifier = Modifier.padding(vertical = 4.dp).background(
                            if (item.senderId == me) Brand else Color.White, RoundedCornerShape(16.dp)
                        ).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
        Row(Modifier.fillMaxWidth().background(Color.White).imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالة...") }, maxLines = 4)
            IconButton(enabled = draft.isNotBlank() && !sending, onClick = {
                val text = draft.trim()
                scope.launch {
                    sending = true
                    try {
                        withContext(Dispatchers.IO) { repository.sendMessage(chat.userId, text) }
                        draft = ""
                        refresh()
                    } catch (e: Exception) { error = e.message ?: "تعذر إرسال الرسالة" }
                    finally { sending = false }
                }
            }) { Icon(Icons.Default.Send, "إرسال", tint = Brand) }
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

package com.alrafidain.tik

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val CameraBrand = Color(0xFFFF3157)

@Composable
fun NativeCameraScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit,
    onUploaded: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("فيديو") }
    var filter by remember { mutableStateOf("طبيعي") }
    var caption by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewState by remember { mutableStateOf<PreviewView?>(null) }
    var chosenSound by remember { mutableStateOf<android.net.Uri?>(null) }
    var soundName by remember { mutableStateOf("") }
    var soundPlaying by remember { mutableStateOf(false) }
    var soundPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                soundPlayer?.release()
                chosenSound = uri
                soundName = "صوت مختار"
                val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use { if (it.moveToFirst()) soundName = it.getString(0) ?: "صوت مختار" }
                val player = MediaPlayer()
                player.setDataSource(context, uri)
                player.setOnPreparedListener { it.start(); soundPlaying = true }
                player.setOnCompletionListener { soundPlaying = false; it.seekTo(0) }
                player.prepareAsync()
                soundPlayer = player
                status = "تم اختيار الصوت: $soundName"
            } catch (e: Exception) { status = e.message ?: "تعذر فتح ملف الصوت" }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }
    val galleryPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { repository.uploadPhoto(uri, caption, filter) }
                    status = result
                    onUploaded()
                } catch (e: Exception) { status = e.message ?: "تعذر نشر الصورة" }
                finally { busy = false }
            }
        }
    }
    val galleryVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val editedUri = if (chosenSound != null || filter != "طبيعي") exportEditedVideo(context, uri, chosenSound, filter) else uri
                    val result = withContext(Dispatchers.IO) { repository.uploadVideo(editedUri, caption) }
                    status = result
                    onUploaded()
                } catch (e: Exception) { status = e.message ?: "تعذر نشر الفيديو" }
                finally { busy = false }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { activeRecording?.stop(); cameraProvider?.unbindAll(); soundPlayer?.release() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        previewViewState = previewView
                        applyCameraFilter(previewView, filter)
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HD))
                                .build()
                            val video = VideoCapture.withOutput(recorder)
                            imageCapture = capture
                            videoCapture = video
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.Builder().requireLensFacing(lens).build(),
                                    preview, capture, video
                                )
                            } catch (e: Exception) { status = e.message ?: "تعذر تشغيل الكاميرا" }
                        }, executor)
                        previewView
                    },
                    update = { view -> applyCameraFilter(view, filter) }
                )
                // Visible live tint overlay: the preview responds immediately to the selected filter.
                if (filter != "طبيعي") {
                    Box(
                        Modifier.fillMaxSize().background(cameraPreviewTint(filter))
                    )
                }
                // Camera preview is edge-to-edge, with TikTok-style capture controls.
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
                            TextButton(onClick = { soundPicker.launch("audio/*") }) {
                                Text(if (soundName.isBlank()) "♫ إضافة صوت" else "♫ ${soundName.take(18)}", color = Color.White, modifier = Modifier
                                    .background(Color(0xAA222222), RoundedCornerShape(30.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("صورة", "فيديو").forEach { item ->
                                TextButton(onClick = { mode = item }) {
                                    Text(item, color = if (mode == item) Color.White else Color.LightGray,
                                        fontSize = if (mode == item) 18.sp else 15.sp)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { galleryPhoto.launch("image/*") }) {
                                Icon(Icons.Default.PhotoLibrary, "المعرض", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Box(Modifier.size(82.dp).background(Color.White, CircleShape).padding(5.dp)
                                .background(if (recording) CameraBrand else Color.Black, CircleShape)) {
                                IconButton(
                                    modifier = Modifier.fillMaxSize(),
                                    enabled = !busy,
                                    onClick = {
                                        if (mode == "صورة") {
                                            val values = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, "Alrafidain_${System.currentTimeMillis()}.jpg")
                                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                            }
                                            val options = ImageCapture.OutputFileOptions.Builder(
                                                context.contentResolver,
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                values
                                            ).build()
                                            busy = true
                                            imageCapture?.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                                    val uri = result.savedUri
                                                    if (uri != null) scope.launch {
                                                        try {
                                                            status = withContext(Dispatchers.IO) { repository.uploadPhoto(uri, caption, filter) }
                                                            onUploaded()
                                                        } catch (e: Exception) { status = e.message ?: "فشل نشر الصورة" }
                                                        finally { busy = false }
                                                    } else { busy = false; status = "تعذر حفظ الصورة" }
                                                }
                                                override fun onError(exception: ImageCaptureException) {
                                                    busy = false; status = exception.message ?: "فشل التقاط الصورة"
                                                }
                                            })
                                        } else {
                                            val capture = videoCapture ?: return@IconButton
                                            if (recording) {
                                                activeRecording?.stop()
                                                activeRecording = null
                                                recording = false
                                            } else {
                                                val values = ContentValues().apply {
                                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Alrafidain_${System.currentTimeMillis()}.mp4")
                                                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                                }
                                                val output = MediaStoreOutputOptions.Builder(
                                                    context.contentResolver,
                                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                                ).setContentValues(values).build()
                                                var pending = capture.output.prepareRecording(context, output)
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    pending = pending.withAudioEnabled()
                                                }
                                                activeRecording = pending.start(executor) { event ->
                                                    when (event) {
                                                        is VideoRecordEvent.Start -> recording = true
                                                        is VideoRecordEvent.Finalize -> {
                                                            recording = false
                                                            activeRecording = null
                                                            if (!event.hasError()) {
                                                                val uri = event.outputResults.outputUri
                                                                busy = true
                                                                scope.launch {
                                                                    try {
                                                                        val editedUri = if (chosenSound != null || filter != "طبيعي") exportEditedVideo(context, uri, chosenSound, filter) else uri
                                                                        status = withContext(Dispatchers.IO) { repository.uploadVideo(editedUri, caption) }
                                                                        onUploaded()
                                                                    } catch (e: Exception) { status = e.message ?: "فشل نشر الفيديو" }
                                                                    finally { busy = false }
                                                                }
                                                            } else status = "فشل تسجيل الفيديو"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(if (recording) Icons.Default.Stop else if (mode == "صورة") Icons.Default.CameraAlt else Icons.Default.FiberManualRecord,
                                        "التقاط", tint = if (recording) Color.White else CameraBrand, modifier = Modifier.size(42.dp))
                                }
                            }
                            IconButton(onClick = { galleryVideo.launch("video/*") }) {
                                Icon(Icons.Default.VideoLibrary, "اختيار فيديو", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = {
                                flash = !flash
                                imageCapture?.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, "الفلاش", tint = Color.White, modifier = Modifier.size(26.dp)) }
                            IconButton(onClick = {
                                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                cameraProvider?.let { provider ->
                                    val preview = Preview.Builder().build()
                                    val capture = imageCapture
                                    val video = videoCapture
                                    try {
                                        provider.unbindAll()
                                        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                                        val previewView = previewViewState
                                        if (previewView != null) {
                                            applyCameraFilter(previewView, filter)
                                            preview.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        if (capture != null && video != null) provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                                    } catch (e: Exception) { status = e.message ?: "تعذر تبديل الكاميرا" }
                                }
                            }) { Icon(Icons.Default.Cameraswitch, "تبديل الكاميرا", tint = Color.White, modifier = Modifier.size(28.dp)) }
                        }
                        val filters = listOf("طبيعي", "ناعم", "دافئ", "ذهبي", "بارد", "أبيض وأسود", "رمادي", "زاهي", "سينما", "وردي", "قديم", "ليلي")
                        Text("الفلاتر", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            filters.forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(62.dp)
                                ) {
                                    Box(
                                        Modifier.size(48.dp).background(
                                            filterSwatch(item),
                                            RoundedCornerShape(14.dp)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(Modifier.size(22.dp).background(Color.White.copy(alpha = 0.28f), CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(9.dp).background(Color.White, CircleShape))
                                        }
                                    }
                                    TextButton(
                                        onClick = { filter = item },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Text(item, color = if (filter == item) CameraBrand else Color.White, fontSize = 10.sp)
                                    }
                                    if (filter == item) Box(Modifier.width(26.dp).height(3.dp).background(CameraBrand, CircleShape))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = caption, onValueChange = { caption = it },
                            placeholder = { Text("اكتب وصفاً (اختياري)", color = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = CameraBrand, unfocusedBorderColor = Color.White,
                                cursorColor = CameraBrand
                            )
                        )
                        if (chosenSound != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الصوت المختار: ${soundName.take(24)}", color = Color.White, fontSize = 11.sp)
                                TextButton(onClick = { if (soundPlaying) { soundPlayer?.pause(); soundPlaying = false } else { soundPlayer?.start(); soundPlaying = true } }) { Text(if (soundPlaying) "إيقاف" else "استماع", color = CameraBrand) }
                                TextButton(onClick = { soundPlayer?.release(); soundPlayer = null; chosenSound = null; soundName = ""; soundPlaying = false }) { Text("إزالة", color = Color.White) }
                            }
                            Text("سيُضاف الصوت المختار إلى الفيديو عند تجهيزه للنشر", color = Color.White, fontSize = 10.sp)
                        }
                        if (busy) CircularProgressIndicator(color = CameraBrand, modifier = Modifier.padding(8.dp))
                        if (status.isNotBlank()) Text(status, color = Color.White, fontSize = 12.sp)
                    }
                    }
                }
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("اسمح للرافدين تيك باستخدام الكاميرا والمايك", color = Color.White)
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                        Text("السماح")
                    }
                }
            }
        }
    }
}


private fun cameraPreviewTint(name: String): Color = when (name) {
    "ناعم" -> Color(0x22F4C8B8)
    "دافئ" -> Color(0x33FF9A42)
    "ذهبي" -> Color(0x3DDAA02D)
    "بارد" -> Color(0x3380BCEB)
    "أبيض وأسود", "رمادي" -> Color(0x22777777)
    "زاهي" -> Color(0x22FF3D78)
    "سينما" -> Color(0x33415C50)
    "وردي" -> Color(0x33FF83B3)
    "قديم" -> Color(0x339B7048)
    "ليلي" -> Color(0x443D478B)
    else -> Color.Transparent
}

private fun filterSwatch(name: String): Color = when (name) {
    "ناعم" -> Color(0xFFE8B8A8)
    "دافئ" -> Color(0xFFE69B55)
    "ذهبي" -> Color(0xFFC58B35)
    "بارد" -> Color(0xFF729BBF)
    "أبيض وأسود", "رمادي" -> Color(0xFF777777)
    "زاهي" -> Color(0xFFCB4C70)
    "سينما" -> Color(0xFF465C53)
    "وردي" -> Color(0xFFE58BAA)
    "قديم" -> Color(0xFF9A7956)
    "ليلي" -> Color(0xFF343A61)
    else -> Color(0xFF8B9A83)
}

private fun applyCameraFilter(view: PreviewView, name: String) {
    val matrix = when (name) {
        "أبيض وأسود", "رمادي" -> AndroidColorMatrix().apply { setSaturation(0f) }
        "دافئ" -> AndroidColorMatrix(floatArrayOf(1.18f,0f,0f,0f,18f, 0f,1.02f,0f,0f,5f, 0f,0f,.82f,0f,0f, 0f,0f,0f,1f,0f))
        "ذهبي" -> AndroidColorMatrix(floatArrayOf(1.22f,0f,0f,0f,22f, 0f,1.08f,0f,0f,10f, 0f,0f,.70f,0f,0f, 0f,0f,0f,1f,0f))
        "بارد" -> AndroidColorMatrix(floatArrayOf(.90f,0f,0f,0f,0f, 0f,1.02f,0f,0f,0f, 0f,0f,1.22f,0f,18f, 0f,0f,0f,1f,0f))
        "زاهي" -> AndroidColorMatrix(floatArrayOf(1.28f,-.10f,-.10f,0f,4f, -.08f,1.20f,-.08f,0f,4f, -.08f,-.08f,1.28f,0f,4f, 0f,0f,0f,1f,0f))
        "سينما" -> AndroidColorMatrix(floatArrayOf(1.12f,0f,0f,0f,-8f, 0f,1.05f,0f,0f,-5f, 0f,0f,.92f,0f,5f, 0f,0f,0f,1f,0f))
        "وردي" -> AndroidColorMatrix(floatArrayOf(1.12f,0f,0f,0f,10f, 0f,.94f,0f,0f,0f, 0f,0f,1.02f,0f,8f, 0f,0f,0f,1f,0f))
        "قديم" -> AndroidColorMatrix(floatArrayOf(.92f,.12f,.04f,0f,12f, .08f,.90f,.02f,0f,7f, .04f,.08f,.72f,0f,0f, 0f,0f,0f,1f,0f))
        "ليلي" -> AndroidColorMatrix(floatArrayOf(.78f,0f,.08f,0f,0f, 0f,.86f,.04f,0f,0f, .08f,.04f,1.16f,0f,12f, 0f,0f,0f,1f,0f))
        "ناعم" -> AndroidColorMatrix(floatArrayOf(1.06f,0f,0f,0f,8f, 0f,1.02f,0f,0f,5f, 0f,0f,1f,0f,5f, 0f,0f,0f,1f,0f))
        else -> null
    }
    if (matrix == null) view.setLayerType(View.LAYER_TYPE_NONE, null)
    else view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
}

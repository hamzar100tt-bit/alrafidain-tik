package com.alrafidain.tik

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
                    val result = withContext(Dispatchers.IO) { repository.uploadVideo(uri, caption) }
                    status = result
                    onUploaded()
                } catch (e: Exception) { status = e.message ?: "تعذر نشر الفيديو" }
                finally { busy = false }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { activeRecording?.stop(); cameraProvider?.unbindAll() }
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
                    update = { }
                )
                // Camera preview is edge-to-edge, with TikTok-style capture controls.
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
                        Text("إضافة صوت  ♪", color = Color.White, modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(30.dp)).padding(horizontal = 20.dp, vertical = 12.dp))
                        IconButton(onClick = {
                            flash = !flash
                            imageCapture?.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, "الفلاش", tint = Color.White) }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("نص", "صورة", "فيديو").forEach { item ->
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
                                .background(if (recording) Brand else Color.Black, CircleShape)) {
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
                                                                        status = withContext(Dispatchers.IO) { repository.uploadVideo(uri, caption) }
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
                                        "التقاط", tint = if (recording) Color.White else Brand, modifier = Modifier.size(42.dp))
                                }
                            }
                            IconButton(onClick = { galleryVideo.launch("video/*") }) {
                                Icon(Icons.Default.VideoLibrary, "اختيار فيديو", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = {
                                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                cameraProvider?.let { provider ->
                                    val preview = Preview.Builder().build()
                                    val capture = imageCapture
                                    val video = videoCapture
                                    try {
                                        provider.unbindAll()
                                        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                                        val view = (context as? android.app.Activity)?.window?.decorView
                                        val previewView = findPreviewView(view)
                                        if (previewView != null) preview.setSurfaceProvider(previewView.surfaceProvider)
                                        if (capture != null && video != null) provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                                    } catch (e: Exception) { status = e.message ?: "تعذر تبديل الكاميرا" }
                                }
                            }) { Icon(Icons.Default.Cameraswitch, "تبديل الكاميرا", tint = Color.White, modifier = Modifier.size(28.dp)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            listOf("طبيعي", "دافئ", "أبيض وأسود", "زاهي").forEach { item ->
                                TextButton(onClick = { filter = item }) {
                                    Text(if (filter == item) "● $item" else item, color = if (filter == item) Brand else Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = caption, onValueChange = { caption = it },
                            placeholder = { Text("اكتب وصفاً (اختياري)", color = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Brand, unfocusedBorderColor = Color.White,
                                cursorColor = Brand
                            )
                        )
                        if (busy) CircularProgressIndicator(color = Brand, modifier = Modifier.padding(8.dp))
                        if (status.isNotBlank()) Text(status, color = Color.White, fontSize = 12.sp)
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

private fun findPreviewView(view: android.view.View?): PreviewView? {
    if (view is PreviewView) return view
    if (view is android.view.ViewGroup) {
        for (index in 0 until view.childCount) {
            val found = findPreviewView(view.getChildAt(index))
            if (found != null) return found
        }
    }
    return null
}

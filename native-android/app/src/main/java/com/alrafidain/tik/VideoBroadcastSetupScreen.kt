package com.alrafidain.tik

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

private val SetupPink = Color(0xFFFF3157)

@Composable
fun VideoBroadcastSetupScreen(
    onClose: () -> Unit,
    creating: Boolean,
    onStart: (String) -> Unit,
    errorMessage: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var title by remember { mutableStateOf("بث الرافدين تيك") }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var selectedTool by remember { mutableStateOf("المؤثرات") }
    var showToolMessage by remember { mutableStateOf(false) }
    var showBeautyPanel by remember { mutableStateOf(false) }
    var beautyTab by remember { mutableStateOf("تنعيم") }
    var smoothing by remember { mutableFloatStateOf(LiveBeautySettings.smoothing) }
    var brightness by remember { mutableFloatStateOf(LiveBeautySettings.brightness) }
    var warmth by remember { mutableFloatStateOf(LiveBeautySettings.warmth) }
    var tone by remember { mutableIntStateOf(LiveBeautySettings.tone) }
    var lipstick by remember { mutableFloatStateOf(LiveBeautySettings.lipstick) }
    var blush by remember { mutableFloatStateOf(LiveBeautySettings.blush) }
    var eyeShadow by remember { mutableFloatStateOf(LiveBeautySettings.eyeShadow) }
    var noseSlim by remember { mutableFloatStateOf(LiveBeautySettings.noseSlim) }
    var jawSlim by remember { mutableFloatStateOf(LiveBeautySettings.jawSlim) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) cameraError = "اسمح للرافدين تيك باستخدام الكاميرا"
    }

    LaunchedEffect(cameraGranted, lens, previewView, lifecycleOwner) {
        val view = previewView
        if (cameraGranted && view != null) {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                        cameraError = null
                    } catch (e: Exception) {
                        cameraError = e.message ?: "تعذر تشغيل الكاميرا"
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                cameraError = e.message ?: "تعذر فتح الكاميرا"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) { } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(54.dp))
                Text("اسمح باستخدام الكاميرا للمعاينة", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("السماح بالكاميرا") }
            }
        }

        // تدرج خفيف يحافظ على وضوح النصوص فوق صورة الكاميرا.
        Box(Modifier.fillMaxWidth().fillMaxHeight(.34f).background(Brush.verticalGradient(listOf(Color.Black.copy(.48f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().fillMaxHeight(.62f).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.76f), Color.Black.copy(.94f)))))

        // الشريط العلوي كما في مرجع مركز البث.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(color = Color.White.copy(.38f), shape = RoundedCornerShape(28.dp),
                modifier = Modifier.clickable { selectedTool = "مركز LIVE"; showToolMessage = true }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("مركز LIVE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onClose, enabled = !creating) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(29.dp))
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.TopEnd).statusBarsPadding().padding(top = 76.dp, end = 24.dp, start = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("ابدأ التجهيز لبثك المباشر:", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("تدرّب على انفراد قبل فتح بث مباشر أمام المشاهدين", color = Color.White.copy(.92f), fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Surface(
                color = Color.White.copy(.10f), shape = RoundedCornerShape(28.dp),
                modifier = Modifier.clickable { selectedTool = "التدرب الآن"; showToolMessage = true }
            ) {
                Text("التدرب الآن", color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp), fontSize = 13.sp)
            }
        }

        // أدوات البث: صفّان من الأيقونات مثل الصورة المرجعية.
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 22.dp, end = 22.dp, bottom = 178.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                SetupTool(Icons.Default.OpenInNew, "Service+") { selectedTool = "Service+"; showToolMessage = true }
                SetupTool(Icons.Default.Settings, "الإعدادات") { selectedTool = "الإعدادات"; showToolMessage = true }
                SetupTool(Icons.Default.AutoAwesome, "المؤثرات") { showBeautyPanel = true }
                SetupTool(Icons.Default.Edit, "تعديل") { selectedTool = "تعديل"; showToolMessage = true }
                SetupTool(Icons.Default.Cameraswitch, "قلب") {
                    lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SetupTool(Icons.Default.Share, "مشاركة") { selectedTool = "مشاركة"; showToolMessage = true }
                SetupTool(Icons.Default.Forum, "التفاعل") { selectedTool = "التفاعل"; showToolMessage = true }
                SetupTool(Icons.Default.FavoriteBorder, "مجتمع المعجبين") { selectedTool = "مجتمع المعجبين"; showToolMessage = true }
            }
        }

        // لوحة العنوان وزر بدء البث في أسفل الشاشة.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(.38f))
                    .clickable { selectedTool = "عنوان البث"; showToolMessage = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (title.isEmpty()) Text("إضافة عنوان", color = Color.White.copy(.65f), fontSize = 15.sp)
                        inner()
                    }
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.AccountCircle, null, tint = Color.White.copy(.8f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (title.isNotBlank() && cameraGranted) onStart(title.trim()) },
                enabled = !creating && cameraGranted && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SetupPink, disabledContainerColor = SetupPink.copy(.45f))
            ) {
                if (creating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(21.dp), strokeWidth = 2.dp)
                else {
                    Text("بدء LIVE", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Videocam, null)
                }
            }
            if (cameraError != null) Text(cameraError!!, color = Color(0xFFFFB4AB), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            if (errorMessage != null) Text(errorMessage, color = Color(0xFFFFB4AB), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        }

        if (showBeautyPanel) {
            AlertDialog(
                onDismissRequest = { showBeautyPanel = false },
                containerColor = Color(0xFF24142F),
                title = { Text("جمال وفلاتر البث", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("تنعيم", "إضاءة", "دفء", "الفلاتر", "المكياج", "ملامح الوجه").forEach { tab ->
                                FilterChip(selected = beautyTab == tab, onClick = { beautyTab = tab }, label = { Text(tab) })
                            }
                        }
                        when (beautyTab) {
                            "تنعيم" -> {
                                Text("مستوى التنعيم: " + (smoothing * 100).toInt() + "%", color = Color.White)
                                Slider(value = smoothing, onValueChange = { smoothing = it; LiveBeautySettings.smoothing = it }, valueRange = 0f..0.55f)
                            }
                            "إضاءة" -> {
                                Text("سطوع الصورة: " + (brightness * 100).toInt() + "%", color = Color.White)
                                Slider(value = brightness, onValueChange = { brightness = it; LiveBeautySettings.brightness = it }, valueRange = -0.08f..0.12f)
                            }
                            "دفء" -> {
                                Text("دفء اللون: " + (warmth * 100).toInt() + "%", color = Color.White)
                                Slider(value = warmth, onValueChange = { warmth = it; LiveBeautySettings.warmth = it }, valueRange = -0.35f..0.35f)
                            }
                            "الفلاتر" -> {
                                listOf("طبيعي", "دافئ", "بارد", "وردي").forEachIndexed { index, name ->
                                    TextButton(onClick = { tone = index; LiveBeautySettings.tone = index }) {
                                        Text(if (tone == index) "✓ " + name else name, color = Color.White)
                                    }
                                }
                            }
                            "ملامح الوجه" -> {
                                Text("تصغير الأنف: " + (noseSlim * 100).toInt() + "%", color = Color.White)
                                Slider(value = noseSlim, onValueChange = { noseSlim = it; LiveBeautySettings.noseSlim = it }, valueRange = 0f..1f)
                                Text("تنحيف الفك والخدود: " + (jawSlim * 100).toInt() + "%", color = Color.White)
                                Slider(value = jawSlim, onValueChange = { jawSlim = it; LiveBeautySettings.jawSlim = it }, valueRange = 0f..1f)
                                Text("تُطبّق التغييرات على الفيديو المرسل إلى البث عند اكتشاف الوجه", color = Color.LightGray, fontSize = 11.sp)
                            }
                            else -> {
                                Text("أحمر الشفاه: " + (lipstick * 100).toInt() + "%", color = Color.White)
                                Slider(value = lipstick, onValueChange = { lipstick = it; LiveBeautySettings.lipstick = it })
                                Text("توريد الخدود: " + (blush * 100).toInt() + "%", color = Color.White)
                                Slider(value = blush, onValueChange = { blush = it; LiveBeautySettings.blush = it })
                                Text("ظل العيون: " + (eyeShadow * 100).toInt() + "%", color = Color.White)
                                Slider(value = eyeShadow, onValueChange = { eyeShadow = it; LiveBeautySettings.eyeShadow = it })
                            }
                        }
                        Text("تُرسل المؤثرات المفعّلة مع فيديو البث. دقة تشكيل الملامح تعتمد على وضوح الوجه والإضاءة.", color = Color.LightGray, fontSize = 11.sp)
                        TextButton(onClick = {
                            smoothing = 0.30f; brightness = 0.015f; warmth = 0f; tone = 0
                            lipstick = 0f; blush = 0f; eyeShadow = 0f
                            noseSlim = 0f; jawSlim = 0f
                            LiveBeautySettings.noseSlim = noseSlim
                            LiveBeautySettings.jawSlim = jawSlim
                            LiveBeautySettings.smoothing = smoothing
                            LiveBeautySettings.brightness = brightness
                            LiveBeautySettings.warmth = warmth
                            LiveBeautySettings.tone = tone
                            LiveBeautySettings.lipstick = lipstick
                            LiveBeautySettings.blush = blush
                            LiveBeautySettings.eyeShadow = eyeShadow
                        }) { Text("إعادة ضبط", color = Color(0xFFFF7892)) }
                    }
                },
                confirmButton = { TextButton(onClick = { showBeautyPanel = false }) { Text("تم", color = Color(0xFFFF7892)) } }
            )
        }

        if (showToolMessage) {
            LaunchedEffect(selectedTool) {
                kotlinx.coroutines.delay(1800)
                showToolMessage = false
            }
            Surface(
                color = Color.Black.copy(.72f), shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp)
            ) {
                Text(
                    if (selectedTool == "المؤثرات") "المؤثرات قيد التجهيز" else "$selectedTool قيد التجهيز",
                    color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 52.dp).clickable { onClick() }
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(31.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

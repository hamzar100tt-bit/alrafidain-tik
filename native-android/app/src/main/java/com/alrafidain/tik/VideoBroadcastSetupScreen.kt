package com.alrafidain.tik

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    var selectedFilter by remember { mutableStateOf("طبيعي") }
    var beautyLevel by remember { mutableFloatStateOf(35f) }
    var activeTab by remember { mutableStateOf("تحسين") }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) cameraError = "لازم تسمح باستخدام الكاميرا حتى تشوف المعاينة"
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
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) { }
        }
    }

    val filterTint = when (selectedFilter) {
        "ناعم" -> Color(0x22F5C5B8)
        "دافئ" -> Color(0x33FF9A42)
        "ذهبي" -> Color(0x33DCA52E)
        "بارد" -> Color(0x3380BCEB)
        "وردي" -> Color(0x33FF83B3)
        "سينما" -> Color(0x33415C50)
        "ليلي" -> Color(0x443D478B)
        "أبيض وأسود" -> Color(0x55777777)
        else -> Color.Transparent
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
            Box(Modifier.fillMaxSize().background(filterTint))
        } else {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("اسمح للرافدين تيك باستخدام الكاميرا", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = SetupPink)) { Text("السماح بالكاميرا") }
            }
        }

        Box(Modifier.fillMaxWidth().fillMaxHeight(.42f).background(
            Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent))
        ))
        Box(Modifier.fillMaxWidth().fillMaxHeight(.38f).align(Alignment.BottomCenter).background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.88f)))
        ))

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, enabled = !creating) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("تجهيز بث فيديو", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT },
                enabled = cameraGranted && !creating) {
                Icon(Icons.Default.Cameraswitch, "تبديل الكاميرا", tint = Color.White, modifier = Modifier.size(27.dp))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("معاينة مباشرة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("تأكد من الإضاءة والزاوية قبل بدء البث", color = Color.White.copy(.78f), fontSize = 12.sp)
                }
                Surface(color = Color.Black.copy(.38f), shape = RoundedCornerShape(20.dp)) {
                    Text("● LIVE PREVIEW", color = Color(0xFFFF6A82), fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(.38f), RoundedCornerShape(18.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("تحسين", "الجمال", "المكياج", "الفلاتر").forEach { tab ->
                    Box(Modifier.weight(1f).background(if (activeTab == tab) SetupPink.copy(.88f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { activeTab = tab }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(tab, color = Color.White, fontSize = 12.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (activeTab == "الفلاتر") "اختيار الفلتر" else "مستوى التأثير", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(if (activeTab == "الفلاتر") selectedFilter else "${beautyLevel.toInt()}%", color = Color.White, fontSize = 12.sp)
            }
            if (activeTab == "الفلاتر") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("طبيعي", "ناعم", "دافئ", "ذهبي", "بارد", "وردي", "سينما", "ليلي", "أبيض وأسود").forEach { name ->
                        Surface(
                            color = if (selectedFilter == name) SetupPink else Color.White.copy(.16f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.clickable { selectedFilter = name }
                        ) { Text(name, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) }
                    }
                }
            } else {
                Slider(value = beautyLevel, onValueChange = { beautyLevel = it }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = SetupPink, inactiveTrackColor = Color.White.copy(.35f)))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("عنوان البث") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(.65f),
                    focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(.8f),
                    cursorColor = SetupPink
                )
            )
            if (cameraError != null) Text(cameraError!!, color = Color(0xFFFFB4AB), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            if (errorMessage != null) Text(errorMessage, color = Color(0xFFFFB4AB), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(9.dp))
            Button(
                onClick = { if (title.isNotBlank() && cameraGranted) onStart(title.trim()) },
                enabled = !creating && cameraGranted && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SetupPink, disabledContainerColor = SetupPink.copy(.45f))
            ) {
                if (creating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Videocam, null)
                Spacer(Modifier.width(8.dp))
                Text(if (creating) "جارٍ إنشاء البث..." else "بدء LIVE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.alrafidain.tik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        content = content
    )
}

@Composable
private fun AlrafidainApp() {
    var selected by remember { mutableStateOf(0) }
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
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand,
                            selectedTextColor = Brand,
                            indicatorColor = Color(0xFFFFE8ED),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(Canvas)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("الرافدين تيك", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                IconButton(onClick = { }) { Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = Ink) }
            }
            when (selected) {
                0 -> NativeHome()
                1 -> NativePlaceholder("اكتشف", "اكتشف الفيديوهات والحسابات الجديدة", Icons.Default.Explore)
                2 -> NativePlaceholder("إنشاء منشور", "اختيار فيديو من الاستديو أو تصوير فيديو جديد", Icons.Default.VideoLibrary)
                3 -> NativePlaceholder("الرسائل", "محادثاتك ورسائلك الخاصة", Icons.Default.ChatBubble)
                else -> NativePlaceholder("حسابي", "ملفك الشخصي وفيديوهاتك وإعدادات الحساب", Icons.Default.Person)
            }
        }
    }
}

@Composable
private fun NativeHome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 2.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Brand, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("أهلاً بك في الرافدين تيك", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "هذه أول نسخة من واجهة أندرويد الأصلية. نكمل الآن ربط الحسابات والفيديوهات بقاعدة بياناتك.",
                    fontSize = 14.sp, lineHeight = 23.sp, color = Muted, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Text("نسخة Android Native • Kotlin", fontSize = 12.sp, color = Brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NativePlaceholder(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Brand, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(description, fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

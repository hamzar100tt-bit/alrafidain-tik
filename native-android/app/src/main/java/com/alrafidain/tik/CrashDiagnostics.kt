package com.alrafidain.tik

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AlrafidainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                File(filesDir, "last_crash.txt").writeText(
                    "Thread: ${thread.name}\nApp: ${packageName}\n\n${writer}"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

@Composable
fun CrashReportScreen(report: String) {
    val context = LocalContext.current
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("تم حفظ سبب توقف التطبيق", style = MaterialTheme.typography.headlineSmall)
                Text("اضغط نسخ السجل وأرسله هنا حتى أصلح الخطأ المحدد. لا يحتوي السجل على كلمة مرورك.")
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Alrafidain crash report", report))
                        Toast.makeText(context, "تم نسخ سجل الخطأ، أرسله في المحادثة", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("نسخ سجل الخطأ") }
                Button(
                    onClick = {
                        File(context.filesDir, "last_crash.txt").delete()
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("مسح السجل وفتح التطبيق") }
                Text(report, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

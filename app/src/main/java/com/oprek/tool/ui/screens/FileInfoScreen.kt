package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoScreen(navController: NavController, vm: MainViewModel) {
    val fileInfo by vm.currentFile.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Info", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        if (fileInfo == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No file loaded", color = TextSecondary)
            }
        } else {
            val info = fileInfo!!
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Main info card
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val typeIcon = when (info.type) {
                            com.oprek.tool.core.FileType.ELF, com.oprek.tool.core.FileType.SO -> "📦"
                            com.oprek.tool.core.FileType.APK -> "📱"
                            com.oprek.tool.core.FileType.SH -> "📜"
                            com.oprek.tool.core.FileType.BIN -> "💾"
                            else -> "📄"
                        }
                        Text("$typeIcon ${info.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Spacer(Modifier.height(16.dp))

                        CopyableField("Type", info.type.name, context)
                        CopyableField("Path", info.path, context)
                        CopyableField("Size", formatInfoSize(info.size), context)
                        CopyableField("Magic", info.magic, context)
                        CopyableField("Modified", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(info.lastModified)), context)
                    }
                }

                // Hash card
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🔐 Hashes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        CopyableField("MD5", info.md5, context)
                        CopyableField("SHA-256", info.sha256, context)
                    }
                }

                Spacer(Modifier.height(24.dp))

            }
        }
    }
}

@Composable
fun CopyableField(label: String, value: String, context: Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("$label: ", fontSize = 12.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp))
        Text(value, fontSize = 12.sp, color = AccentCyan, fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()))
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = TextMuted)
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "File info loaded" },
                filename = "file_info.txt",
                subfolder = "info"
            )

        }

    }
}

private fun formatInfoSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes bytes"
    bytes < 1048576 -> "${bytes / 1024} KB ($bytes bytes)"
    else -> "${"%.2f".format(bytes / 1048576.0)} MB ($bytes bytes)"
}

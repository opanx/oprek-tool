package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.oprek.tool.core.FileAnalyzer
import com.oprek.tool.core.FileType
import com.oprek.tool.core.NativeLib
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidToolsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dexInfo by remember { mutableStateOf("") }
    var dexClasses by remember { mutableStateOf<List<String>>(emptyList()) }
    var apkInfo by remember { mutableStateOf("") }
    var hasNative by remember { mutableStateOf(false) }
    var fileType by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { NativeLib.dexValidate(byteArrayOf()); hasNative = true } catch (_: Exception) {}
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        val data = withContext(Dispatchers.IO) { file.readBytes() }

        when {
            NativeLib.dexValidate(data) -> {
                fileType = "DEX"
                dexInfo = withContext(Dispatchers.Default) { NativeLib.dexGetInfo(data) }
                dexClasses = withContext(Dispatchers.Default) { NativeLib.dexGetClasses(data).toList() }
            }
            NativeLib.elfValidate(data) -> fileType = "ELF (not DEX)"
            file.name.endsWith(".apk") -> {
                fileType = "APK"
                val apkData = FileAnalyzer.parseApkInfo(file)
                apkInfo = buildString {
                    appendLine("Entries: ${apkData.totalEntries}")
                    appendLine("Has DEX: ${apkData.hasDex}")
                    appendLine("Has Native Libs: ${apkData.hasNativeLibs}")
                    appendLine("Has Manifest: ${apkData.hasManifest}")
                    appendLine("File Size: ${apkData.size} bytes")
                    appendLine()
                    appendLine("=== ZIP Entries ===")
                    apkData.entries.take(50).forEach { e ->
                        appendLine("${e.name} [${e.methodStr}] ${e.uncompressedSize} bytes")
                    }
                    if (apkData.entries.size > 50) appendLine("... and ${apkData.entries.size - 50} more")
                }
            }
            else -> fileType = "Unknown (${file.name})"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🤖 Android Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // File type
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📱", fontSize = 32.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Android Tools", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("File type: $fileType", fontSize = 13.sp, color = AccentCyan)
                    }
                }
            }

            // DEX Info
            if (dexInfo.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📋 DEX Header", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        Text(dexInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                    }
                }
            }

            // Classes
            if (dexClasses.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📦 Classes (${dexClasses.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange)
                        Spacer(Modifier.height(8.dp))
                        dexClasses.take(50).forEach { line ->
                            val parts = line.split("|")
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(parts.getOrElse(0) { "?" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = if (parts.getOrElse(1) { "" }.contains("public")) AccentCyan else TextPrimary,
                                    maxLines = 1, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                                Text(parts.getOrElse(1) { "" }, fontSize = 10.sp, color = AccentOrange, modifier = Modifier.width(60.dp))
                            }
                        }
                    }
                }
            }

            // APK Info
            if (apkInfo.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📱 APK Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange)
                        Spacer(Modifier.height(8.dp))
                        Text(apkInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            if (dexInfo.isEmpty() && apkInfo.isEmpty() && fileType.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("Open a .dex, .apk, or DEX file to analyze", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))

            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Android tools loaded" },
                filename = "android_tools.txt",
                subfolder = "android"
            )

        }
    }
}

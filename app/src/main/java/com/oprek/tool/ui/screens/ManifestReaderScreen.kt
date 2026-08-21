package com.oprek.tool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.FileAnalyzer
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManifestReaderScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var manifestInfo by remember { mutableStateOf("") }
    var commonPerms by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() } ?: return@LaunchedEffect
        if (!file.name.endsWith(".apk")) return@LaunchedEffect
        scope.launch(Dispatchers.Default) {
            val info = withContext(Dispatchers.IO) { FileAnalyzer.parseApkInfo(file) }
            manifestInfo = buildString {
                appendLine("=== APK Manifest Info ===")
                appendLine("Entries: ${info.totalEntries}")
                appendLine("Has DEX: ${info.hasDex}")
                appendLine("Has Native Libs: ${info.hasNativeLibs}")
                appendLine("Has Manifest: ${info.hasManifest}")
                appendLine("Size: ${info.size} bytes")
                appendLine()
                appendLine("=== ZIP Entries ===")
                info.entries.forEach { e ->
                    appendLine("${e.name} [${e.methodStr}] ${e.uncompressedSize} bytes")
                }
            }
            // Extract permission-like strings
            commonPerms = info.entries.map { it.name }.filter { it.contains("permission") || it.contains("Permission") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📋 Manifest Reader", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (manifestInfo.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Text("Open an APK file first to analyze manifest", modifier = Modifier.padding(16.dp), color = TextSecondary)
                }
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Text(manifestInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                        modifier = Modifier.padding(12.dp).fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
                if (commonPerms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("🔒 Permissions Found", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                            Spacer(Modifier.height(8.dp))

                            commonPerms.forEach { p ->
                                Text("• $p", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
                            }
                        }
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Manifest read" },
                filename = "manifest.txt",
                subfolder = "apk"
            )

        }
    }
}

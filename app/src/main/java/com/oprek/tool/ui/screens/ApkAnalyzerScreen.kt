package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkAnalyzerScreen(navController: NavController, vm: MainViewModel) {
    val apkInfo by vm.apkInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APK Analyzer", fontWeight = FontWeight.Bold) },
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
        if (apkInfo == null || apkInfo!!.totalEntries == 0) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No APK file loaded\nOpen an APK from Home", color = TextSecondary)
            }
        } else {
            val info = apkInfo!!
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                // Summary
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("📱 APK Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange)
                            Spacer(Modifier.height(8.dp))
                            ApkField("Total Entries", "${info.totalEntries}")
                            ApkField("File Size", formatApkSize(info.size))
                            ApkField("Has DEX", if (info.hasDex) "✅ Yes" else "❌ No")
                            ApkField("Has Native Libs", if (info.hasNativeLibs) "✅ Yes" else "❌ No")
                            ApkField("Has Manifest", if (info.hasManifest) "✅ Yes" else "❌ No")
                        }
                    }
                }

                // Entry list
                item {
                    Text("  📂 Entries (${info.entries.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = AccentOrange, modifier = Modifier.padding(top = 8.dp))
                }
                itemsIndexed(info.entries.take(200)) { idx, entry ->
                    ApkEntryRow(idx, entry)
                }
                if (info.entries.size > 200) {
                    item {
                        Text("  ... and ${info.entries.size - 200} more entries", fontSize = 11.sp,
                            color = TextMuted, modifier = Modifier.padding(12.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { apkInfo.toString() },
                filename = "apk_info.txt",
                subfolder = "apk"
            )

            }
        }
    }
}

@Composable
fun ApkField(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 12.sp, color = AccentGreen, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ApkEntryRow(idx: Int, entry: com.oprek.tool.core.ApkEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(if (idx % 2 == 0) DarkBg else DarkSurface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            color = if (entry.name.endsWith(".dex")) AccentBlue else if (entry.name.contains(".so")) AccentPurple else TextPrimary,
            maxLines = 1, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
        Text(entry.methodStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentOrange,
            modifier = Modifier.width(60.dp))
        Text(formatApkSize(entry.uncompressedSize), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
    }
}

private fun formatApkSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)}KB"
    else -> "${"%.1f".format(bytes / 1048576.0)}MB"
}

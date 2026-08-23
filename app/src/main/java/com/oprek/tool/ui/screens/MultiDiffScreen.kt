package com.oprek.tool.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiDiffScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var files by remember { mutableStateOf(listOf<Pair<String, ByteArray>>()) }
    var result by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try {
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "file${files.size}"
                val data = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                withContext(Dispatchers.Main) { files = files + (name to data) }
            } catch (_: Exception) {}
        }}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚖️ Multi-File Compare", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Files (${files.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    files.forEachIndexed { idx, (name, data) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${idx + 1}. $name (${data.size} bytes)", fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { files = files.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp), tint = AccentRed)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add File")
                        }
                        if (files.size >= 2) {
                            Button(onClick = {
                                result = compareFiles(files)
                            }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                                Icon(Icons.Default.Compare, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Compare All")
                            }
                        }
                    }
                }
            }

            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Comparison Results", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Spacer(Modifier.height(8.dp))
                        Text(result, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun compareFiles(files: List<Pair<String, ByteArray>>): String {
    val sb = StringBuilder()
    sb.appendLine("═══════════════════════════════════════")
    sb.appendLine("  MULTI-FILE COMPARISON (${files.size} files)")
    sb.appendLine("═══════════════════════════════════════")
    sb.appendLine()

    // Size comparison
    sb.appendLine("📊 File Sizes:")
    files.forEach { (name, data) -> sb.appendLine("  $name: ${data.size} bytes (${data.size / 1024}KB)") }
    sb.appendLine()

    // Magic bytes
    sb.appendLine("🔍 Magic Bytes:")
    files.forEach { (name, data) ->
        val magic = if (data.size >= 4) data.take(4).joinToString("") { "%02X".format(it) } else "??"
        sb.appendLine("  $name: $magic")
    }
    sb.appendLine()

    // Pairwise comparison
    for (i in files.indices) {
        for (j in i + 1 until files.size) {
            val (name1, data1) = files[i]
            val (name2, data2) = files[j]
            sb.appendLine("━━━ $name1 vs $name2 ━━━")
            val minLen = minOf(data1.size, data2.size)
            var diffs = 0
            var firstDiff = -1
            for (k in 0 until minLen) {
                if (data1[k] != data2[k]) {
                    diffs++
                    if (firstDiff < 0) firstDiff = k
                }
            }
            if (diffs == 0 && data1.size == data2.size) {
                sb.appendLine("  ✅ IDENTICAL")
            } else {
                sb.appendLine("  ❌ $diffs byte differences")
                sb.appendLine("  Size: ${data1.size} vs ${data2.size}")
                if (firstDiff >= 0) sb.appendLine("  First diff at offset: 0x${"%08X".format(firstDiff)}")
                // Show first 5 diffs
                var shown = 0
                for (k in 0 until minLen) {
                    if (data1[k] != data2[k] && shown < 5) {
                        sb.appendLine("  0x${"%08X".format(k)}: ${"%02X".format(data1[k])} → ${"%02X".format(data2[k])}")
                        shown++
                    }
                }
                if (diffs > 5) sb.appendLine("  ... and ${diffs - 5} more")
            }
            sb.appendLine()
        }
    }
    return sb.toString()
}

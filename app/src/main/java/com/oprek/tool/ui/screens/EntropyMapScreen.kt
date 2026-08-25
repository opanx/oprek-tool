package com.oprek.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.sqrt

data class EntropyBlock(
    val offset: Long,
    val size: Int,
    val entropy: Double,
    val entropyColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntropyMapScreen(navController: NavController) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var entropyBlocks by remember { mutableStateOf(listOf<EntropyBlock>()) }
    var avgEntropy by remember { mutableDoubleStateOf(0.0) }
    var maxEntropy by remember { mutableDoubleStateOf(0.0) }
    var maxOffset by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var blockSize by remember { mutableStateOf("1024") }
    var fileNameDisplay by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                fileNameDisplay = File(path).name
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    val data = File(path).readBytes()
                    val bs = blockSize.toIntOrNull() ?: 1024
                    val blocks = computeEntropyMap(data, bs)
                    val avg = if (blocks.isNotEmpty()) blocks.map { it.entropy }.average() else 0.0
                    val mx = blocks.maxByOrNull { it.entropy }
                    withContext(Dispatchers.Main) {
                        entropyBlocks = blocks
                        avgEntropy = avg
                        maxEntropy = mx?.entropy ?: 0.0
                        maxOffset = mx?.offset ?: 0L
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗺️ Entropy Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fileNameDisplay.isNotEmpty()) fileNameDisplay else "Select file", modifier = Modifier.weight(1f), color = if (fileNameDisplay.isNotEmpty()) TextPrimary else TextSecondary)
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Block size selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Block size:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    listOf("256", "512", "1024", "2048", "4096").forEach { bs ->
                        FilterChip(
                            selected = blockSize == bs,
                            onClick = { blockSize = bs },
                            label = { Text(bs, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp),
                            colors = FilterChipDefaults.filterChipColors(containerColor = DarkBg)
                        )
                    }
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = AccentGreen)
            }

            // Stats
            if (entropyBlocks.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📊 Entropy Statistics", fontWeight = FontWeight.Bold, color = AccentGreen)
                        Spacer(Modifier.height(4.dp))
                        Text("Average: ${String.format("%.4f", avgEntropy)}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("Maximum: ${String.format("%.4f", maxEntropy)} @ 0x${String.format("%08X", maxOffset)}", color = AccentOrange, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("Blocks: ${entropyBlocks.size}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        // Classification
                        val classification = when {
                            avgEntropy > 7.5 -> "HIGH — Likely encrypted/packed/compressed"
                            avgEntropy > 6.0 -> "MEDIUM — Mix of code and data"
                            avgEntropy > 4.0 -> "NORMAL — Typical compiled code"
                            else -> "LOW — Mostly zeros or structured data"
                        }
                        Text(classification, color = when {
                            avgEntropy > 7.5 -> AccentRed
                            avgEntropy > 6.0 -> AccentOrange
                            else -> AccentGreen
                        }, fontSize = 11.sp)
                    }
                }

                // Legend
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0.0 to "0.0",
                            2.0 to "2.0",
                            4.0 to "4.0",
                            6.0 to "6.0",
                            8.0 to "8.0"
                        ).forEach { (v, label) ->
                            val c = entropyToColor(v)
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            Text(label, fontSize = 9.sp, color = TextMuted)
                        }
                        Text("(entropy)", fontSize = 9.sp, color = TextMuted)
                    }
                }

                // Visual heatmap
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔥 Heatmap", fontWeight = FontWeight.Bold, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        // Render blocks as colored bars
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            val cols = 128
                            val rows = (entropyBlocks.size / cols) + 1
                            Column {
                                for (row in 0 until rows) {
                                    Row {
                                        for (col in 0 until cols) {
                                            val idx = row * cols + col
                                            if (idx < entropyBlocks.size) {
                                                val block = entropyBlocks[idx]
                                                Box(
                                                    Modifier.size(6.dp)
                                                        .padding(0.2.dp)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(block.entropyColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Per-block detail (limited)
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📋 Block Details (first 100)", fontWeight = FontWeight.Bold, color = AccentCyan)
                        Spacer(Modifier.height(4.dp))
                        entropyBlocks.take(100).forEach { block ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text("0x${String.format("%08X", block.offset)}", color = TextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.3f))
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(block.entropyColor))
                                Spacer(Modifier.width(4.dp))
                                Text(String.format("%.4f", block.entropy), color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.2f))
                                Text("${block.size}B", color = TextMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeEntropyMap(data: ByteArray, blockSize: Int): List<EntropyBlock> {
    if (data.isEmpty()) return emptyList()
    val blocks = mutableListOf<EntropyBlock>()
    var offset = 0L

    while (offset < data.size) {
        val end = minOf((offset + blockSize).toInt(), data.size)
        val chunk = data.sliceArray(offset.toInt() until end)
        val entropy = shannonEntropy(chunk)
        blocks.add(EntropyBlock(offset, chunk.size, entropy, entropyToColor(entropy)))
        offset += blockSize
    }

    return blocks
}

private fun shannonEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val freq = IntArray(256)
    for (b in data) freq[b.toInt() and 0xFF]++
    var entropy = 0.0
    for (f in freq) {
        if (f > 0) {
            val p = f.toDouble() / data.size
            entropy -= p * ln(p) / ln(2.0)
        }
    }
    return entropy
}

private fun entropyToColor(e: Double): Color {
    val t = (e / 8.0).coerceIn(0.0, 1.0)
    return when {
        t < 0.25 -> Color(0x66, 0x00, 0x66) // Dark purple (low)
        t < 0.50 -> Color(0x00, 0x66, 0xFF) // Blue
        t < 0.625 -> Color(0x00, 0xCC, 0x00) // Green
        t < 0.75 -> Color(0xFF, 0xCC, 0x00) // Yellow
        t < 0.875 -> Color(0xFF, 0x66, 0x00) // Orange
        else -> Color(0xFF, 0x00, 0x00) // Red (high)
    }
}

private fun getPathFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                val name = it.getString(idx)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val file = File(context.cacheDir, name)
                file.outputStream().use { out -> inputStream.copyTo(out) }
                return file.absolutePath
            }
        }
    }
    return null
}

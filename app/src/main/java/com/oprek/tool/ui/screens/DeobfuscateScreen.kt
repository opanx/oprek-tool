package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import com.oprek.tool.utils.ObfuscatedString
import com.oprek.tool.utils.PatternDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeobfuscateScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("strings") }
    var isProcessing by remember { mutableStateOf(false) }
    var autoDetected by remember { mutableStateOf<List<ObfuscatedString>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val modes = listOf(
        "strings" to "Extract Strings", "unicode" to "Decode Unicode",
        "hex" to "Decode Hex", "base64" to "Decode Base64",
        "url" to "Decode URL", "xor" to "XOR Decrypt",
        "reverse" to "Reverse", "unescape" to "Unescape Shell"
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔓 Deobfuscate", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Auto-detect section
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 Auto-Detect Obfuscated", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.weight(1f))
                        if (isScanning) CircularProgressIndicator(Modifier.size(18.dp), color = AccentPurple, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Scan binary for Base64, Hex, XOR, Unicode, URL-encoded strings", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        isScanning = true
                        scope.launch(Dispatchers.Default) {
                            try {
                                val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull()
                                if (file != null) {
                                    val data = withContext(Dispatchers.IO) {
                                        val raf = java.io.RandomAccessFile(file, "r")
                                        val size = minOf(raf.length(), 5_000_000L).toInt()
                                        val buf = ByteArray(size)
                                        raf.readFully(buf)
                                        raf.close()
                                        buf
                                    }
                                    autoDetected = withContext(Dispatchers.Default) { PatternDetector.detectObfuscatedStrings(data) }
                                }
                            } catch (_: Exception) {}
                            isScanning = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), shape = RoundedCornerShape(8.dp),
                        enabled = !isScanning) {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Binary for Obfuscated Strings", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Auto-detected results
            if (autoDetected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Found ${autoDetected.size} obfuscated strings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val all = autoDetected.joinToString("\n") { "[${it.type}] ${it.offset}: ${it.raw} → ${it.decoded}" }
                                clipboard.setPrimaryClip(ClipData.newPlainText("deobf", all))
                                Toast.makeText(context, "Copied ${autoDetected.size} results!", Toast.LENGTH_SHORT).show()
                            }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), shape = RoundedCornerShape(8.dp)) {
                                Text("Copy All", fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        autoDetected.take(100).forEach { obs ->
                            val typeColor = when {
                                obs.type.contains("Base64") -> AccentCyan
                                obs.type.contains("XOR") -> AccentOrange
                                obs.type.contains("Hex") -> AccentGreen
                                else -> AccentPurple
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(DarkSurface).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${obs.type} (${obs.confidence}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = typeColor)
                                    Text("Raw: ${obs.raw.take(60)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, maxLines = 1)
                                    Text("Decoded: ${obs.decoded.take(60)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("decoded", obs.decoded))
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = AccentGreen)
                                }
                            }
                        }
                        if (autoDetected.size > 100) {
                            Text("... and ${autoDetected.size - 100} more", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Manual mode selector
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Manual Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { modes.take(4).forEach { (k, l) -> ModeChip(l, selectedMode == k) { selectedMode = k } }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { modes.drop(4).forEach { (k, l) -> ModeChip(l, selectedMode == k) { selectedMode = k } }
                }
            }

            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Input", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("Paste obfuscated text...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                }
            }

            Button(onClick = {
                isProcessing = true
                scope.launch(Dispatchers.Default) { outputText = processDeobfuscate(inputText, selectedMode); isProcessing = false }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp),
                enabled = inputText.isNotEmpty() && !isProcessing) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Deobfuscate", fontWeight = FontWeight.Bold) }
            }

            if (outputText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("output", outputText)) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentGreen)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(outputText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { output.ifEmpty { "No results" } },
                filename = "deobfuscated.txt",
                subfolder = "deobfuscate"
            )

        }

    }
}

@Composable
fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, fontSize = 10.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f), selectedLabelColor = AccentPurple),
        modifier = Modifier.height(32.dp))
}

private fun processDeobfuscate(input: String, mode: String): String {
    if (input.isBlank()) return ""
    return try {
        when (mode) {
            "strings" -> {
                val sb = StringBuilder(); val cur = StringBuilder()
                for (c in input) { if (c.code in 0x20..0x7E) cur.append(c) else { if (cur.length >= 3) { if (sb.isNotEmpty()) sb.append("\n"); sb.append(cur) }; cur.clear() } }
                if (cur.length >= 3) { if (sb.isNotEmpty()) sb.append("\n"); sb.append(cur) }; sb.toString()
            }
            "unicode" -> Regex("\\\\u([0-9a-fA-F]{4})").replace(input) { it.groupValues[1].toInt(16).toChar().toString() }
            "hex" -> input.replace("\\s".toRegex(), "").chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            "base64" -> String(android.util.Base64.decode(input.trim(), android.util.Base64.DEFAULT))
            "url" -> java.net.URLDecoder.decode(input, "UTF-8")
            "xor" -> {
                val bytes = input.toByteArray(); val results = mutableListOf<String>()
                for (key in 0..255) { val decoded = bytes.map { (it.toInt() xor key).toChar() }.joinToString(""); val score = decoded.count { it.code in 0x20..0x7E || it == '\n' }
                    if (score > bytes.size * 0.7) results.add("Key 0x${"%02X".format(key)}:\n$decoded") }
                if (results.isNotEmpty()) results.joinToString("\n\n") else "No likely XOR key found"
            }
            "reverse" -> input.reversed()
            "unescape" -> input.replace("\\\\n", "\n").replace("\\\\t", "\t").replace("\\\\\\\\", "\\").replace("\\\\r", "\r").replace("\\\\0", "\u0000").replace("\\'", "'").replace("\\\"", "\"")
            else -> input
        }
    } catch (e: Exception) { "Error: ${e.message}" }
}

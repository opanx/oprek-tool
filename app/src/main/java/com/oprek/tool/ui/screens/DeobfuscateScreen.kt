package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
        "reverse" to "Reverse", "unescape" to "Unescape Shell",
        "rot13" to "ROT13", "rot47" to "ROT47",
        "caesar" to "Caesar Brute",
        "multibase" to "Multi-Decode",
        "utf16" to "UTF-16 Decode",
        "chain" to "Chain Decode"
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("\uD83D\uDD13 Deobfuscate", fontWeight = FontWeight.Bold) },
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
                        Text("\uD83E\uDD16 Auto-Detect Obfuscated", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.weight(1f))
                        if (isScanning) CircularProgressIndicator(Modifier.size(18.dp), color = AccentPurple, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Scan binary for Base64, Hex, XOR, Unicode, URL-encoded strings", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        isScanning = true
                        scope.launch(Dispatchers.Default) {
                            try {
                                val file = SharedFileState.findFile(context)
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
                                val all = autoDetected.joinToString("\n") { "[${it.type}] ${it.offset}: ${it.raw} -> ${it.decoded}" }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.take(4).forEach { (k, l) ->
                            FilterChip(selected = selectedMode == k, onClick = { selectedMode = k },
                                label = { Text(l, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(0.3f)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.drop(4).forEach { (k, l) ->
                            FilterChip(selected = selectedMode == k, onClick = { selectedMode = k },
                                label = { Text(l, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(0.3f)))
                        }
                    }
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
                scope.launch(Dispatchers.Default) {
                    outputText = runDeobfuscate(inputText, selectedMode)
                    isProcessing = false
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp),
                enabled = inputText.isNotEmpty() && !isProcessing) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
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
        }
    }
}

private fun runDeobfuscate(input: String, mode: String): String {
    if (input.isBlank()) return ""
    return try {
        when (mode) {
            "auto" -> {
                // Auto-detect encoding and apply best decode
                val results = mutableListOf<String>()
                results.add("=== Auto-Detect Results ===")
                results.add("")

                // 1. Try Base64
                try {
                    val decoded = String(android.util.Base64.decode(input.trim(), android.util.Base64.DEFAULT))
                    val printable = decoded.count { it.code in 0x20..0x7E || it.code in 9..13 }
                    if (printable > decoded.length * 0.7f && decoded.length >= 4) {
                        results.add("[Base64] Confidence: ${(printable * 100 / decoded.length.coerceAtLeast(1))}%")
                        results.add("  → $decoded")
                        results.add("")
                    }
                } catch (_: Exception) {}

                // 2. Try Hex
                try {
                    val hex = input.trim().replace(" ", "").replace("0x", "")
                    if (hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } && hex.length >= 8) {
                        val decoded = hex.chunked(2).mapNotNull { it.toIntOrNull(16)?.toChar() }.joinToString("")
                        val printable = decoded.count { it.code in 0x20..0x7E }
                        if (printable > decoded.length * 0.5f) {
                            results.add("[Hex] Confidence: ${(printable * 100 / decoded.length.coerceAtLeast(1))}%")
                            results.add("  → $decoded")
                            results.add("")
                        }
                    }
                } catch (_: Exception) {}

                // 3. Try ROT13
                val rot13 = input.map { c -> when { c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26; c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26; else -> c }}.joinToString("")
                val rot13Score = rot13.count { it.isLetter() || it == ' ' }.toFloat() / rot13.length.coerceAtLeast(1)
                if (rot13Score > 0.6f && rot13 != input) {
                    results.add("[ROT13] Confidence: ${(rot13Score * 100).toInt()}%")
                    results.add("  → $rot13")
                    results.add("")
                }

                // 4. Try ROT47
                val rot47 = input.map { c -> if (c in '!'..'~') (((c.code - 33 + 47) % 94) + 33).toChar() else c }.joinToString("")
                val rot47Score = rot47.count { it.isLetter() || it == ' ' }.toFloat() / rot47.length.coerceAtLeast(1)
                if (rot47Score > 0.5f && rot47 != input) {
                    results.add("[ROT47] Confidence: ${(rot47Score * 100).toInt()}%")
                    results.add("  → $rot47")
                    results.add("")
                }

                // 5. Try URL decode
                try {
                    val decoded = java.net.URLDecoder.decode(input, "UTF-8")
                    if (decoded != input && decoded.length >= 4) {
                        results.add("[URL Decode]")
                        results.add("  → $decoded")
                        results.add("")
                    }
                } catch (_: Exception) {}

                // 6. Try XOR brute (top 3)
                val bytes = input.toByteArray()
                val xorResults = mutableListOf<Pair<Int, String>>()
                for (k in 0..255) {
                    val decoded = String(bytes.map { (it.toInt() xor k).toChar() }.toCharArray())
                    val score = decoded.count { it.code in 0x20..0x7E || it == '\n' }.toFloat() / decoded.length.coerceAtLeast(1)
                    if (score > 0.7f) xorResults.add(k to decoded)
                }
                if (xorResults.isNotEmpty()) {
                    results.add("[XOR] Top results:")
                    xorResults.take(3).forEach { (key, decoded) ->
                        results.add("  Key 0x${"%02X".format(key)}: $decoded")
                    }
                    results.add("")
                }

                // 7. Detect language patterns
                val lower = input.lowercase()
                val langHints = mutableListOf<String>()
                if (lower.contains("function") || lower.contains("var ") || lower.contains("const ") || lower.contains("let ")) langHints.add("JavaScript")
                if (lower.contains("def ") || lower.contains("import ") || lower.contains("class ")) langHints.add("Python")
                if (lower.contains("#include") || lower.contains("void ") || lower.contains("int main")) langHints.add("C/C++")
                if (lower.contains("public class") || lower.contains("private ") || lower.contains("void ")) langHints.add("Java/Kotlin")
                if (lower.contains("<html") || lower.contains("<div") || lower.contains("<!doctype")) langHints.add("HTML")
                if (lower.contains("select ") || lower.contains("from ") || lower.contains("where ")) langHints.add("SQL")
                if (langHints.isNotEmpty()) {
                    results.add("[Language] Detected: ${langHints.joinToString(", ")}")
                    results.add("")
                }

                if (results.size <= 2) {
                    results.add("No encoding detected. Try specific modes.")
                }

                results.joinToString("\n")
            }
            "strings" -> {
                val sb = StringBuilder()
                val cur = StringBuilder()
                for (c in input) {
                    if (c.code in 0x20..0x7E) cur.append(c)
                    else {
                        if (cur.length >= 3) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append(cur)
                        }
                        cur.clear()
                    }
                }
                if (cur.length >= 3) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(cur)
                }
                sb.toString()
            }
            "unicode" -> {
                val regex = Regex("\\\\u([0-9a-fA-F]{4})")
                regex.replace(input) { match ->
                    match.groupValues[1].toInt(16).toChar().toString()
                }
            }
            "hex" -> {
                input.replace("\\s".toRegex(), "").chunked(2).mapNotNull {
                    it.toIntOrNull(16)?.toChar()
                }.joinToString("")
            }
            "base64" -> {
                String(android.util.Base64.decode(input.trim(), android.util.Base64.DEFAULT))
            }
            "url" -> {
                java.net.URLDecoder.decode(input, "UTF-8")
            }
            "xor" -> {
                val bytes = input.toByteArray()
                val results = mutableListOf<String>()
                for (key in 0..255) {
                    val decoded = bytes.map { (it.toInt() xor key).toChar() }.joinToString("")
                    val score = decoded.count { it.code in 0x20..0x7E || it == '\n' }
                    if (score > bytes.size * 0.7) {
                        results.add("Key 0x${"%02X".format(key)}:\n$decoded")
                    }
                }
                if (results.isNotEmpty()) results.joinToString("\n\n") else "No likely XOR key found"
            }
            "reverse" -> input.reversed()
            "unescape" -> {
                input
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")
                    .replace("\\r", "\r")
            }
            else -> input
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

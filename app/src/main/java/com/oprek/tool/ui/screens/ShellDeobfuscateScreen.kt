package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
// import com.oprek.tool.core.SharedFileState // replaced by SharedFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.io.File

data class DeobResult(val line: Int, val method: String, val original: String, val decoded: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellDeobfuscateScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<DeobResult>()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(0) }
    val modes = listOf("🔍 Auto Detect", "📋 Base64", "🔢 Hex", "🔄 ROT13/47", "🌐 URL Decode", "⚡ XOR Brute")

    fun loadFromCache() {
        val f = SharedFileState.findFile(context)
        if (f != null && f.length() < 1_000_000) { // Only load text files < 1MB
            try {
                val text = f.readText(Charsets.UTF_8)
                if (text.any { it.code in 0..6 || it.code in 14..31 } && !text.contains('\n')) {
                    // Binary file, skip
                    return
                }
                content = text
                loaded = true
            } catch (_: Exception) {
                // Not a text file
            }
        }
    }

    fun deobfuscate() {
        val lines = content.lines()
        val res = mutableListOf<DeobResult>()

        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Base64 detection
            if (selectedMode == 0 || selectedMode == 1) {
                val b64Patterns = listOf(
                    Regex("""base64\s+-d\s*[<]*\s*['"]?([A-Za-z0-9+/=]{8,})['"]?"""),
                    Regex("""echo\s+['"]([A-Za-z0-9+/=]{8,})['"]\s*\|\s*base64\s+-d"""),
                    Regex("""printf\s+['"]([A-Za-z0-9+/=]{8,})['"]\s*\|\s*base64"""),
                    Regex("""([A-Za-z0-9+/]{40,}={0,2})""")
                )
                for (pat in b64Patterns) {
                    val m = pat.find(trimmed)
                    if (m != null) {
                        try {
                            val b64 = m.groupValues.getOrElse(1) { m.value }
                            val decoded = String(Base64.decode(b64, Base64.DEFAULT))
                            if (decoded.any { it.isLetter() } && decoded != trimmed) {
                                res.add(DeobResult(i + 1, "Base64", trimmed.take(60), decoded.take(200)))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // Hex detection
            if (selectedMode == 0 || selectedMode == 2) {
                if (trimmed.contains("xxd") && trimmed.contains("-r")) {
                    res.add(DeobResult(i + 1, "Hex (xxd)", trimmed.take(60), "(hex binary data detected)"))
                }
                val hexStr = Regex("""\\x([0-9a-fA-F]{2})""").findAll(trimmed).toList()
                if (hexStr.size >= 3) {
                    try {
                        val bytes = hexStr.map { it.groupValues[1].toInt(16).toByte() }.toByteArray()
                        val decoded = String(bytes)
                        if (decoded.any { it.isLetter() }) {
                            res.add(DeobResult(i + 1, "Hex Escape", trimmed.take(60), decoded.take(200)))
                        }
                    } catch (_: Exception) {}
                }
            }

            // ROT13/ROT47
            if (selectedMode == 0 || selectedMode == 3) {
                val rot13 = trimmed.map { c ->
                    when {
                        c in 'a'..'m' || c in 'A'..'M' -> (c.code + 13).toChar()
                        c in 'n'..'z' || c in 'N'..'Z' -> (c.code - 13).toChar()
                        else -> c
                    }
                }.joinToString("")
                if (rot13 != trimmed && trimmed.length > 5 && trimmed.contains(Regex("[a-zA-Z]{5,}"))) {
                    res.add(DeobResult(i + 1, "ROT13", trimmed.take(60), rot13.take(200)))
                }
                // ROT47
                val rot47 = trimmed.map { c ->
                    if (c in '!'..'O') (c.code + 47).toChar()
                    else if (c in 'P'..'~') (c.code - 47).toChar()
                    else c
                }.joinToString("")
                if (rot47 != trimmed && rot47 != rot13 && trimmed.length > 5) {
                    res.add(DeobResult(i + 1, "ROT47", trimmed.take(60), rot47.take(200)))
                }
            }

            // URL decode
            if (selectedMode == 0 || selectedMode == 4) {
                if (trimmed.contains("%")) {
                    try {
                        val decoded = URLDecoder.decode(trimmed, "UTF-8")
                        if (decoded != trimmed && decoded.length > 3) {
                            res.add(DeobResult(i + 1, "URL Encode", trimmed.take(60), decoded.take(200)))
                        }
                    } catch (_: Exception) {}
                }
            }

            // XOR brute (common single-byte keys)
            if (selectedMode == 5) {
                val bytes = trimmed.toByteArray()
                if (bytes.size >= 4) {
                    for (key in 1..255) {
                        val decrypted = bytes.map { (it.toInt() xor key).toByte() }.toByteArray()
                        val text = String(decrypted, Charsets.UTF_8)
                        val printable = text.count { it.code in 0x20..0x7E || it == '\n' || it == '\t' }
                        if (printable > text.length * 0.8 && text.contains(Regex("[a-zA-Z]{3,}"))) {
                            res.add(DeobResult(i + 1, "XOR(0x${"%02X".format(key)})", trimmed.take(60), text.take(200)))
                            break
                        }
                    }
                }
            }
        }

        results = res
    }

    LaunchedEffect(SharedFileState.revision) { loadFromCache() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Shell Deobfuscator", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (loaded) IconButton(onClick = { results = emptyList(); content = ""; loaded = false }) { Icon(Icons.Default.Refresh, "New") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!loaded) {
                // Welcome + auto-load
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Shell Deobfuscator", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Open a shell script or .sh file", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadFromCache(); if (!loaded) { content = "# Paste shell script here\n"; loaded = true } },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                            Text("Load from Cache or Paste")
                        }
                    }
                }
            } else {
                // Mode selector
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEachIndexed { idx, mode ->
                        FilterChip(selected = selectedMode == idx, onClick = { selectedMode = idx },
                            label = { Text(mode, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                    }
                }

                // Content editor
                OutlinedTextField(value = content, onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 12.dp),
                    label = { Text("Shell Script Content", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace))

                // Deobfuscate button
                Button(onClick = { deobfuscate() }, Modifier.fillMaxWidth().padding(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deobfuscate (${content.lines().size} lines)", fontSize = 12.sp)
                }

                // Results
                if (results.isNotEmpty()) {
                    Text("  ${results.size} findings:", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
                    LazyColumn(Modifier.weight(0.6f).padding(horizontal = 12.dp)) {
                        itemsIndexed(results) { _, r ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("L${r.line} [${r.method}]", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("→ ${r.decoded}", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

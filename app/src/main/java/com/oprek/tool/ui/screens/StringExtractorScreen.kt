package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringExtractorScreen(navController: NavController, vm: MainViewModel) {
    val strings by vm.strings.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var minLength by remember { mutableStateOf("4") }
    var filter by remember { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(true) }
    var showEncrypted by remember { mutableStateOf(false) }
    var encryptedResults by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.extractStrings() }

    val filtered = remember(strings, filter) {
        if (filter.isEmpty()) strings
        else strings.filter { it.value.contains(filter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Strings (${filtered.size}/${strings.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilter = !showFilter }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = {
                        val text = filtered.joinToString("\n") { "0x${"%08X".format(it.offset)}: ${it.value}" }
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("strings", text))
                        Toast.makeText(context, "Copied ${filtered.size} filtered strings!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy Filtered") }
                    IconButton(onClick = {
                        val text = strings.joinToString("\n") { "0x${"%08X".format(it.offset)}: ${it.value}" }
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("all_strings", text))
                        Toast.makeText(context, "Copied ALL ${strings.size} strings!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.SelectAll, "Copy ALL") }
                    IconButton(onClick = { vm.extractStrings(minLength.toIntOrNull() ?: 4) }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                    IconButton(onClick = {
                        // Export ALL strings to file
                        val allText = buildString {
                            appendLine("# OprekTool String Export")
                            appendLine("# Total: ${strings.size} strings")
                            appendLine("# Format: OFFSET: STRING")
                            appendLine()
                            strings.forEach { sp ->
                                appendLine("0x${String.format("%08X", sp.offset)}: ${sp.value}")
                            }
                        }
                        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val outFile = java.io.File(dir, "oprektool_strings_${System.currentTimeMillis()}.txt")
                        outFile.writeText(allText)
                        Toast.makeText(context, "Exported ${strings.size} strings to ${outFile.name}", Toast.LENGTH_LONG).show()
                    }) {
                        Icon(Icons.Default.FileDownload, "Export All Strings")
                    }
                    IconButton(onClick = {
                        // Import strings from clipboard
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cb.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val imported = clip.getItemAt(0).text.toString()
                            val lines = imported.lines().filter { it.contains(":") && !it.startsWith("#") }
                            Toast.makeText(context, "Imported ${lines.size} strings from clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.FileUpload, "Import Strings")
                    }
                    IconButton(onClick = {
                        showEncrypted = !showEncrypted
                        if (showEncrypted) {
                            encryptedResults = strings.filter { sp ->
                                val v = sp.value
                                (v.length >= 8 && v.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) ||
                                v.contains("=") && v.length >= 12 ||
                                v.startsWith("U2") || v.startsWith("H4") ||
                                v.all { it.code in 0x21..0x7E } && v.length >= 16 && v.count { it == ' ' } < 3
                            }.map { sp ->
                                val decoded = tryAutoDecrypt(sp.value)
                                sp.value to decoded
                            }.filter { it.second.isNotEmpty() }
                        }
                    }) {
                        Icon(Icons.Default.Security, "Auto-Detect Encrypted", tint = if (showEncrypted) AccentRed else TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search bar (always visible)
            if (showFilter) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Search strings...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentGreen) },
                    trailingIcon = {
                        if (filter.isNotEmpty()) {
                            IconButton(onClick = { filter = "" }) {
                                Icon(Icons.Default.Close, "Clear", tint = AccentGreen)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        cursorColor = AccentGreen
                    )
                )
                // Min length + count
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Min: ", fontSize = 11.sp, color = TextMuted)
                    OutlinedTextField(
                        value = minLength,
                        onValueChange = { minLength = it },
                        modifier = Modifier.width(50.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.extractStrings(minLength.toIntOrNull() ?: 4) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, "Apply", Modifier.size(16.dp), tint = AccentGreen)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} results", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            }

            if (showEncrypted && encryptedResults.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔒 Auto-Detected Encrypted Strings (${encryptedResults.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentRed)
                        Spacer(Modifier.height(8.dp))
                        encryptedResults.take(50).forEach { (enc, dec) ->
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text("ENC: $enc", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentOrange, maxLines = 1)
                                Text("DEC: $dec", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, maxLines = 1)
                                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                            }
                        }
                        if (encryptedResults.size > 50) {
                            Text("... and ${encryptedResults.size - 50} more", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, fontSize = 11.sp, color = AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }

            // String list with highlight
            LazyColumn(Modifier.fillMaxSize()) {

                itemsIndexed(filtered) { idx, sp ->
                    StringRowWithHighlight(idx, sp, filter, context)
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📝", fontSize = 48.sp)
                                Spacer(Modifier.height(12.dp))
                                if (filter.isNotEmpty()) {
                                    Text("No matches for \"$filter\"", color = AccentOrange, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("No strings found", color = TextSecondary)
                                    Text("Extract strings from a loaded file", fontSize = 13.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StringRowWithHighlight(idx: Int, sp: com.oprek.tool.core.StringPair, filter: String, context: Context) {
    val annotatedText = buildAnnotatedString {
        if (filter.isNotEmpty() && sp.value.contains(filter, ignoreCase = true)) {
            val lowerValue = sp.value.lowercase()
            val lowerFilter = filter.lowercase()
            var start = 0
            var idx = lowerValue.indexOf(lowerFilter, start)
            while (idx >= 0) {
                if (idx > start) append(sp.value.substring(start, idx))
                withStyle(SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold)) {
                    append(sp.value.substring(idx, idx + filter.length))
                }
                start = idx + filter.length
                idx = lowerValue.indexOf(lowerFilter, start)
            }
            if (start < sp.value.length) append(sp.value.substring(start))
        } else {
            append(sp.value)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .background(if (idx % 2 == 0) DarkBg else DarkSurface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "0x${"%08X".format(sp.offset)}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentPurple,
            modifier = Modifier.width(90.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            annotatedText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentGreen,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        )
        IconButton(onClick = {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("str", sp.value))
        }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(12.dp), tint = TextMuted)

        }
    }
}

private fun tryAutoDecrypt(input: String): String {
    // Try Base64
    try {
        val bytes = android.util.Base64.decode(input, android.util.Base64.NO_WRAP)
        val text = String(bytes)
        if (text.all { it.code in 0x20..0x7E || it.code in 9..13 } && text.length >= 4) return "[Base64] $text"
    } catch (_: Exception) {}

    // Try ROT13
    val rot13 = input.map { c -> when {
        c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
        c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
        else -> c
    }}.joinToString("")
    if (rot13 != input && rot13.count { it.isLetter() } > rot13.length * 0.6f) return "[ROT13] $rot13"

    // Try ROT47
    val rot47 = input.map { c -> if (c in '!'..'~') (((c.code - 33 + 47) % 94) + 33).toChar() else c }.joinToString("")
    if (rot47 != input && rot47.count { it.isLetter() || it == ' ' } > rot47.length * 0.5f) return "[ROT47] $rot47"

    // Try XOR brute force
    val bytes = input.toByteArray()
    var bestKey = 0; var bestScore = 0f; var bestResult = ""
    for (k in 0..255) {
        val decoded = String(bytes.map { (it.toInt() xor k) and 0xFF }.map { it.toByte() }.toByteArray())
        val score = decoded.count { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }.toFloat() / decoded.length.coerceAtLeast(1)
        if (score > bestScore) { bestScore = score; bestKey = k; bestResult = decoded }
    }
    if (bestScore > 0.5f) return "[XOR key=0x${"%02X".format(bestKey)}] $bestResult"

    return ""
}

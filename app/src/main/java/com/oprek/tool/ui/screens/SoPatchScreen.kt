@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoPatchScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var findHex by remember { mutableStateOf("") }
    var replaceHex by remember { mutableStateOf("") }
    var patchMode by remember { mutableStateOf("nop") } // nop, ret, jmp, custom

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isRunning = true
            output = listOf("[*] Loading .so file...")
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(it) ?: return@launch
                    val data = stream.readBytes()
                    stream.close()

                    withContext(Dispatchers.Main) {
                        output = listOf(
                            "[+] File size: ${data.size} bytes",
                            "[+] ELF: ${if (data[4] == 2.toByte()) "64-bit" else "32-bit"}",
                            "[+] Arch: ${when (data[18].toInt() and 0xFF) { 0x28 -> "ARM"; 0xB7 -> "ARM64"; 0x03 -> "x86"; 0x3E -> "x86_64"; else -> "Unknown (${data[18].toInt()})" }}",
                            ""
                        )

                        if (findHex.isNotBlank()) {
                            val pattern = hexStringToBytes2(findHex)
                            val replacement = when (patchMode) {
                                "nop" -> ByteArray(pattern.size) { if (data[4] == 2.toByte()) 0x1F.toByte() else 0x00.toByte() } // NOP
                                "ret" -> ByteArray(pattern.size) { 0xC3.toByte() } // x86 RET
                                "jmp" -> ByteArray(pattern.size) { 0xEB.toByte() } // x86 JMP
                                else -> hexStringToBytes2(replaceHex)
                            }

                            if (pattern.isEmpty()) {
                                output = output + "[-] Invalid hex pattern"
                            } else {
                                var count = 0
                                var offset = 0
                                while (offset <= data.size - pattern.size) {
                                    var match = true
                                    for (j in pattern.indices) {
                                        if (data[offset + j] != pattern[j]) { match = false; break }
                                    }
                                    if (match) {
                                        output = output + "[+] Match @ 0x${offset.toString(16).uppercase()} (${pattern.size} bytes)"
                                        count++
                                    }
                                    offset++
                                    if (count > 100) break
                                }
                                output = output + "[*] Found $count matches"
                                output = output + "[*] Use -x option on PC to actually patch"
                            }
                        }

                        // Show important symbols
                        val text = data.toString(Charsets.US_ASCII)
                        val symbols = listOf("ptrace", "strcmp", "malloc", "dlopen", "mprotect", "fork", "kill", "exit", "pthread_create", "android_dlopen_ext")
                        val found = symbols.filter { text.contains(it) }
                        if (found.isNotEmpty()) {
                            output = output + ""
                            output = output + "[*] Interesting symbols found:"
                            found.forEach { s -> output = output + "  🎯 $s" }
                        }

                        isRunning = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { output = listOf("[-] Error: ${e.message}"); isRunning = false }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 SO Patcher", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Analyze & patch .so native libraries", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(findHex, { findHex = it }, label = { Text("Find hex (e.g. 48 89 E5)", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(replaceHex, { replaceHex = it }, label = { Text("Replace hex (optional)", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("nop" to "NOP", "ret" to "RET", "jmp" to "JMP", "custom" to "Custom").forEach { (mode, label) ->
                            FilterChip(selected = patchMode == mode, onClick = { patchMode = mode }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), shape = RoundedCornerShape(8.dp)) {
                        Text("Analyze .so File", fontSize = 11.sp)
                    }
                }
            }
            if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentOrange)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n"))) }) { Text("Copy", fontSize = 10.sp, color = AccentCyan) }
                    }
                    LazyColumn { items(output) { line -> Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = when { line.startsWith("[+") -> AccentGreen; line.startsWith("[-]") -> AccentRed; line.startsWith("🎯") -> AccentCyan; else -> TextPrimary }, lineHeight = 12.sp) } }
                }
            }
        }
    }
}

private fun hexStringToBytes2(hex: String): ByteArray {
    val cleaned = hex.replace(" ", "").replace(":", "")
    if (cleaned.length % 2 != 0 || cleaned.isEmpty()) return byteArrayOf()
    return try {
        ByteArray(cleaned.length / 2) { i -> cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    } catch (e: Exception) { byteArrayOf() }
}

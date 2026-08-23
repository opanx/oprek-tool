package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isScanning by remember { mutableStateOf(false) }
    var patternInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isScanning = true
            output = listOf("[*] Scanning...")
            scope.launch(Dispatchers.IO) {
                val result = scanSignatures(context, it, patternInput)
                withContext(Dispatchers.Main) { output = result; isScanning = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Signature Scanner", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Scan binary for known signatures/patterns", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = patternInput, onValueChange = { patternInput = it },
                        label = { Text("Custom hex pattern (e.g. 48 89 E5 ?? ?? FF)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f).height(40.dp), enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(8.dp)) {
                            Text("Scan File", fontSize = 11.sp)
                        }
                        Button(onClick = { filePicker.launch(arrayOf("*/*")); patternInput = "KNOWN_CRYPTO" }, modifier = Modifier.weight(1f).height(40.dp), enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), shape = RoundedCornerShape(8.dp)) {
                            Text("Crypto Patterns", fontSize = 11.sp)
                        }
                        Button(onClick = { filePicker.launch(arrayOf("*/*")); patternInput = "KNOWN_ANTIDEBUG" }, modifier = Modifier.weight(1f).height(40.dp), enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed), shape = RoundedCornerShape(8.dp)) {
                            Text("Anti-Debug", fontSize = 11.sp)
                        }
                    }
                }
            }
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentCyan)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Results (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(output) { line ->
                            val color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[!]") -> AccentRed; line.startsWith("[~]") -> AccentOrange; else -> TextPrimary }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val knownCryptoPatterns = listOf(
    "AES S-Box" to "63 7C 77 7B F2 6B 6F C5 30 01 67 2B FE D7 AB 76",
    "DES S-Box" to "0E 04 0B 02 08 03 01 0C 06 0D 07 09 00 0A 05 0F",
    "SHA-256 Constants" to "42 8A 2F 98 D7 28 AE 22 71 37 44 91 23 13 1D 99",
    "MD5 Constants" to "D7 6A A4 78 E8 17 FB B0 C6 A9 E1 F7 9F 57 50 04",
    "RSA Public Exponent" to "01 00 01",
    "ECDSA P-256" to "FF FF FF FF 00 00 00 00 00 00 00 00 00 00 00 00",
    "CRC32 Table" to "00 00 00 00 77 07 30 96 EE 0E 61 2C 99 09 51 BA",
    "RC4 S-Box Init" to "00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F"
)

private val knownAntiDebugPatterns = listOf(
    "ptrace" to "ptrace(PTRACE_TRACEME)",
    "TracerPid" to "/proc/self/status TracerPid check",
    "IsDebuggerPresent" to "Win32 IsDebuggerPresent",
    "CheckRemoteDebuggerPresent" to "Win32 CheckRemoteDebugger",
    "NtQueryInformationProcess" to "ProcessDebugPort check",
    "fork bomb" to "Anti-debug fork bomb",
    " timing" to "Timing-based anti-debug"
)

private fun scanSignatures(context: Context, uri: android.net.Uri, mode: String): List<String> {
    val result = mutableListOf<String>()
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open")
        val data = stream.readBytes()
        stream.close()
        result.add("[+] File size: ${data.size} bytes")

        if (mode == "KNOWN_CRYPTO") {
            result.add("[*] Scanning for crypto signatures...")
            for ((name, hex) in knownCryptoPatterns) {
                val pattern = hexStringToBytes(hex)
                val offset = findPattern(data, pattern)
                if (offset >= 0) result.add("[+] $name @ 0x${offset.toString(16)}")
            }
            result.add("[*] Crypto scan complete")
        } else if (mode == "KNOWN_ANTIDEBUG") {
            result.add("[*] Scanning for anti-debug patterns...")
            val text = data.toString(Charsets.US_ASCII)
            for ((name, desc) in knownAntiDebugPatterns) {
                if (text.contains(name, ignoreCase = true)) result.add("[!] $name - $desc")
            }
            // Also check for string references
            val strings = listOf("ptrace", "TracerPid", "frida", "xposed", "gdb", "lldb", "strace")
            for (s in strings) {
                var idx = 0
                while (idx < data.size - s.length) {
                    if (data.sliceArray(idx until idx + s.size).toString(Charsets.US_ASCII) == s) {
                        result.add("[!] Found '$s' @ 0x${idx.toString(16)}")
                        break
                    }
                    idx++
                }
            }
            result.add("[*] Anti-debug scan complete")
        } else if (mode.isNotEmpty()) {
            // Custom hex pattern
            val pattern = hexStringToBytes(mode)
            if (pattern.isNotEmpty()) {
                result.add("[*] Searching for pattern: $mode (${pattern.size} bytes)")
                var count = 0
                var offset = 0
                while (offset < data.size - pattern.size) {
                    val found = findPattern(data, pattern, offset)
                    if (found < 0) break
                    result.add("[+] Match @ 0x${found.toString(16)}")
                    count++
                    offset = found + 1
                    if (count > 500) { result.add("[!] Stopping after 500 matches"); break }
                }
                result.add("[*] Found $count matches")
            }
        } else {
            result.add("[*] No pattern specified. Use Crypto or Anti-Debug buttons.")
        }
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

private fun hexStringToBytes(hex: String): ByteArray {
    val cleaned = hex.replace(" ", "").replace(":", "")
    if (cleaned.length % 2 != 0) return byteArrayOf()
    return ByteArray(cleaned.length / 2) { i ->
        cleaned.substring(i * 2, i * 2 + 2).toByte(16)
    }
}

private fun findPattern(data: ByteArray, pattern: ByteArray, startOffset: Int = 0): Int {
    if (pattern.isEmpty() || data.size < pattern.size) return -1
    for (i in startOffset until data.size - pattern.size + 1) {
        var match = true
        for (j in pattern.indices) { if (data[i + j] != pattern[j]) { match = false; break } }
        if (match) return i
    }
    return -1
}

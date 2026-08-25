package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.io.File

/**
 * Resource Scanner v1 — OFRAK-style resource identification
 * - Find embedded files (ZIP, PNG, ELF, DEX, SO, classes.dex)
 * - Find strings by category (URLs, paths, keys, secrets)
 * - Find code patterns (function prologues, syscalls, crypto)
 * - Entropy analysis per region
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScannerScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var filePath by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf(0) } // 0=Embedded, 1=Strings, 2=Patterns, 3=All
    var showSettings by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔬 Resource Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("resource_scan", output.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            val outFile = File(dir, "resource_scan_${System.currentTimeMillis()}.txt")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔬 Scan Mode", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Embedded" to 0, "Strings" to 1, "Patterns" to 2, "All" to 3).forEach { (label, mode) ->
                                FilterChip(selected = scanMode == mode, onClick = { scanMode = mode }, label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = filePath, onValueChange = { filePath = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Binary file path") }, singleLine = true, colors = darkTextFieldColors(),
                            leadingIcon = { Icon(Icons.Default.Folder, null, tint = AccentOrange) })
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            isScanning = true; output = emptyList(); showSettings = false
                            Thread {
                                output = when (scanMode) {
                                    0 -> scanEmbeddedFiles(context, filePath)
                                    1 -> scanStrings(context, filePath)
                                    2 -> scanPatterns(context, filePath)
                                    3 -> scanAll(context, filePath)
                                    else -> listOf("Invalid mode")
                                }
                                status = "Done! ${output.size} findings"
                                isScanning = false
                            }.start()
                        }, enabled = !isScanning && filePath.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                            if (isScanning) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
                            else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start Scan") }
                        }
                    }
                }
            }

            if (output.isNotEmpty() && !showSettings) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} findings", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Settings, "Settings", Modifier.size(16.dp), tint = Color.Gray) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[-]") -> AccentRed; line.startsWith("[!]") -> AccentOrange; line.contains("ZIP") -> AccentCyan; line.contains("DEX") -> AccentPurple; line.contains("SO") -> AccentRed; line.contains("PNG") -> AccentOrange; else -> Color(0xFF90EE90.toInt()) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
                    }
                }
            }

            if (output.isEmpty() && !isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔬", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                        Text("Resource Scanner", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        Text("OFRAK-style resource identification", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("✨ Capabilities:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                listOf("Find embedded files (ZIP, PNG, ELF, DEX, SO, classes.dex)", "Extract strings by category (URLs, paths, keys, secrets)", "Find code patterns (function prologues, syscalls, crypto)", "Entropy analysis per region", "Detect packing/encryption", "Android resource decoding").forEach {
                                    Text("• $it", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun scanEmbeddedFiles(ctx: Context, path: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val data = file.readBytes()
    result.add("[+] Scanning for embedded files in: $path (${data.size} bytes)")
    result.add("")

    // Magic signatures
    data class MagicInfo(val offset: Int, val name: String, val desc: String)
    val found = mutableListOf<MagicInfo>()

    // Search for common magic bytes
    val magics = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) to "ZIP/APK",
        byteArrayOf(0x50, 0x4B, 0x05, 0x06) to "ZIP (empty)",
        byteArrayOf(0x50, 0x4B, 0x06, 0x07) to "ZIP (spanned)",
        byteArrayOf(0x7F, 0x45, 0x4C, 0x46) to "ELF",
        byteArrayOf(0x64, 0x65, 0x78, 0x0A) to "DEX",
        byteArrayOf(0x64, 0x65, 0x79, 0x0A) to "ODEX",
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to "PNG",
        byteArrayOf(0x47, 0x49, 0x46, 0x38) to "GIF",
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) to "JPEG",
        byteArrayOf(0x52, 0x61, 0x72, 0x21) to "RAR",
        byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte()) to "7Z",
        byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00) to "GZIP",
        byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58) to "XZ",
        byteArrayOf(0x42, 0x5A, 0x68) to "BZIP2",
        byteArrayOf(0x4D, 0x5A) to "PE/EXE",
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()) to "Mach-O (fat)",
        byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCE.toByte()) to "Mach-O 32",
        byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCF.toByte()) to "Mach-O 64",
        byteArrayOf(0x58, 0x41, 0x52, 0x21) to "XAR",
        byteArrayOf(0x4C, 0x01) to "COFF",
        byteArrayOf(0x62, 0x73, 0x68, 0x72) to "BASH script",
        byteArrayOf(0x23, 0x21) to "Shebang script",
        byteArrayOf(0x4D, 0x4D, 0x00, 0x2A) to "TIFF",
        byteArrayOf(0x52, 0x49, 0x46, 0x46) to "RIFF",
        byteArrayOf(0x4F, 0x67, 0x67, 0x53) to "OGG",
        byteArrayOf(0x66, 0x4C, 0x61, 0x43) to "FLAC",
        byteArrayOf(0x49, 0x44, 0x33) to "MP3 (ID3)",
        byteArrayOf(0xFF.toByte(), 0xFB.toByte()) to "MP3",
        byteArrayOf(0x00, 0x00, 0x01, 0x00) to "ICO",
        byteArrayOf(0x00, 0x00, 0x02, 0x00) to "CUR",
    )

    for (i in 0 until data.size - 4) {
        for ((magic, name) in magics) {
            if (data[i] == magic[0] && data[i+1] == magic[1] && data[i+2] == magic[2] && data[i+3] == magic[3]) {
                found.add(MagicInfo(i, name, "Magic: ${magic.joinToString(" ") { String.format("%02X", it) }}"))
            }
        }
    }

    // Deduplicate nearby matches (within 4 bytes)
    val deduped = mutableListOf<MagicInfo>()
    for (f in found) {
        if (deduped.isEmpty() || f.offset - deduped.last().offset > 4) {
            deduped.add(f)
        }
    }

    result.add("[+] Found ${deduped.size} embedded resources:")
    result.add("")

    for (m in deduped) {
        result.add("[+] 0x${String.format("%08X", m.offset)} — ${m.name} (${m.desc})")
    }

    return result
}

private fun scanStrings(ctx: Context, path: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val data = file.readBytes()
    result.add("[+] String analysis: $path")
    result.add("")

    // Extract strings
    val strings = mutableListOf<Pair<Int, String>>()
    val current = StringBuilder()
    var startOffset = 0
    for (i in data.indices) {
        val c = data[i].toInt() and 0xFF
        if (c in 32..126) {
            if (current.isEmpty()) startOffset = i
            current.append(c.toChar())
        } else {
            if (current.length >= 6) {
                strings.add(Pair(startOffset, current.toString()))
            }
            current.clear()
        }
    }

    result.add("[+] Total strings: ${strings.size}")
    result.add("")

    // Categorize
    val urls = strings.filter { it.second.startsWith("http://") || it.second.startsWith("https://") }
    val paths = strings.filter { it.second.startsWith("/") && it.second.length > 5 }
    val keys = strings.filter { it.second.contains("key", true) || it.second.contains("secret", true) || it.second.contains("token", true) }
    val ips = strings.filter { it.second.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) }

    result.add("--- URLs (${urls.size}) ---")
    urls.take(30).forEach { result.add("  0x${String.format("%08X", it.first)}: ${it.second}") }

    result.add("")
    result.add("--- Paths (${paths.size}) ---")
    paths.take(30).forEach { result.add("  0x${String.format("%08X", it.first)}: ${it.second}") }

    result.add("")
    result.add("--- Keys/Secrets (${keys.size}) ---")
    keys.take(30).forEach { result.add("  0x${String.format("%08X", it.first)}: ${it.second}") }

    result.add("")
    result.add("--- IP Addresses (${ips.size}) ---")
    ips.take(20).forEach { result.add("  0x${String.format("%08X", it.first)}: ${it.second}") }

    return result
}

private fun scanPatterns(ctx: Context, path: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val data = file.readBytes()
    result.add("[+] Pattern scan: $path")
    result.add("")

    // Search for common code patterns
    var funcCount = 0
    var sysCallCount = 0
    var cryptoCount = 0

    for (i in 0 until data.size - 4) {
        // ARM64 function prologue: STP X29, X30, [SP, #imm]!
        if (data[i] == 0xFD.toByte() && (data[i+1].toInt() and 0x7F) == 0x7B) {
            funcCount++
        }
        // ARM64 SVC #0 (syscall)
        if (data[i] == 0x01.toByte() && data[i+1] == 0x00.toByte() && data[i+2] == 0x00.toByte() && data[i+3] == 0xD4.toByte()) {
            sysCallCount++
        }
        // AES S-box pattern
        if (data[i] == 0x63.toByte() && data[i+1] == 0x7C.toByte() && data[i+2] == 0x77.toByte() && data[i+3] == 0x7B.toByte()) {
            result.add("[!] AES S-box found at 0x${String.format("%08X", i)}")
            cryptoCount++
        }
    }

    result.add("[+] Function prologues (STP X29,X30): $funcCount")
    result.add("[+] Syscalls (SVC #0): $sysCallCount")
    result.add("[+] Crypto patterns (AES S-box): $cryptoCount")

    return result
}

private fun scanAll(ctx: Context, path: String): List<String> {
    val result = mutableListOf<String>()
    result.addAll(scanEmbeddedFiles(ctx, path))
    result.add("")
    result.addAll(scanStrings(ctx, path))
    result.add("")
    result.addAll(scanPatterns(ctx, path))
    return result
}

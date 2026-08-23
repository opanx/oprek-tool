package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun MemoryScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanValue by remember { mutableStateOf("") }
    var scanType by remember { mutableStateOf("int32") }
    var pid by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔬 Memory Scanner", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }, enabled = output.isNotEmpty()) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Scan process memory for values (requires root)", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pid, onValueChange = { pid = it }, label = { Text("PID (or package name)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = scanValue, onValueChange = { scanValue = it }, label = { Text("Value to scan (e.g. 100, 0x1234ABCD, 3.14)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("int32" to "Int32", "int64" to "Int64", "float" to "Float", "string" to "String", "hex" to "Hex Pattern").forEach { (type, label) ->
                            FilterChip(selected = scanType == type, onClick = { scanType = type }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (pid.isBlank() || scanValue.isBlank()) return@Button
                        isScanning = true
                        output = listOf("[*] Scanning memory...")
                        scope.launch(Dispatchers.IO) {
                            val result = scanMemory(pid, scanValue, scanType)
                            withContext(Dispatchers.Main) { output = result; isScanning = false }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(8.dp)) {
                        Text("🔍 Scan Memory", fontSize = 11.sp)
                    }
                }
            }
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentGreen)

            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📖 Quick Reference", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(4.dp))
                    Text("• Find PID: cat /proc/<package>/status | grep Tgid", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text("• Find PID: pidof <package>", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text("• Memory maps: cat /proc/<pid>/maps", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text("• Common game offsets: libil2cpp.so + offset", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }
            }

            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Scan Results (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(output) { line ->
                            val color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[!]") -> AccentRed; line.startsWith("[*]") -> AccentCyan; else -> TextPrimary }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun scanMemory(pid: String, value: String, type: String): List<String> {
    val result = mutableListOf<String>()
    try {
        val actualPid = pid.toIntOrNull() ?: run {
            // Try to find PID from package name
            result.add("[*] Looking up PID for: $pid")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pidof $pid"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.isNotEmpty()) {
                val foundPid = output.split("\\s+".toRegex()).first()
                result.add("[+] Found PID: $foundPid")
                foundPid.toIntOrNull()
            } else null
        } ?: return listOf("[-] Cannot find PID for: $pid")

        result.add("[+] Target PID: $actualPid")
        result.add("[+] Scan type: $type")
        result.add("[+] Value: $value")
        result.add("")

        // Read memory maps
        val mapsProc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/$actualPid/maps 2>/dev/null"))
        val maps = mapsProc.inputStream.bufferedReader().readText()
        mapsProc.waitFor()

        if (maps.isBlank()) {
            return listOf("[-] Cannot read /proc/$actualPid/maps", "[!] Make sure you have root access")
        }

        val regions = maps.lines().filter { it.contains("rw-p") }
        result.add("[+] Found ${regions.size} writable regions")

        // Search each region using /proc/pid/mem
        var found = 0
        for (region in regions) {
            val parts = region.split("\\s+".toRegex())
            if (parts.isEmpty()) continue
            val addrRange = parts[0].split("-")
            if (addrRange.size < 2) continue
            val startAddr = addrRange[0].toLongOrNull(16) ?: continue
            val endAddr = addrRange[1].toLongOrNull(16) ?: continue
            val regionSize = (endAddr - startAddr).toInt()
            if (regionSize <= 0 || regionSize > 100 * 1024 * 1024) continue // skip >100MB regions

            val regionName = if (parts.size > 5) parts.subList(5, parts.size).joinToString(" ") else ""

            // Use dd to read region
            try {
                val readCmd = "dd if=/proc/$actualPid/mem bs=1 skip=$startAddr count=$regionSize 2>/dev/null"
                val readProc = Runtime.getRuntime().exec(arrayOf("sh", "-c", readCmd))
                val regionData = readProc.inputStream.readNBytes(minOf(regionSize, 1024 * 1024)) // max 1MB per region
                readProc.waitFor()

                // Search in region
                val searchBytes = when (type) {
                    "int32" -> {
                        val v = value.toLongOrNull() ?: value.replace("0x", "").toLongOrNull(16) ?: continue
                        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
                    }
                    "string" -> value.toByteArray()
                    else -> continue
                }

                var idx = 0
                while (idx < regionData.size - searchBytes.size) {
                    var match = true
                    for (j in searchBytes.indices) { if (regionData[idx + j] != searchBytes[j]) { match = false; break } }
                    if (match) {
                        val addr = startAddr + idx
                        result.add("[+] Found @ 0x${addr.toString(16)} in $regionName")
                        found++
                        if (found > 100) { result.add("[!] Stopping after 100 results"); return result }
                    }
                    idx++
                }
            } catch (e: Exception) { /* skip */ }
        }

        result.add("")
        result.add("[+] Total matches: $found")
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

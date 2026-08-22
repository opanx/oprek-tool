package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.oprek.tool.core.LoadedFileHelper
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var selectedPkg by remember { mutableStateOf("") }
    var dumpCsContent by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val games = listOf(
        "com.mobile.legends" to "MLBB",
        "com.dts.freefiremax" to "FF MAX",
        "com.dts.freefireth" to "FF",
        "com.tencent.ig" to "PUBG",
        "com.miHoYo.GenshinImpact" to "Genshin",
        "com.supercell.clashofclans" to "COC",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.activision.callofduty.shooter" to "COD Mobile"
    )

    fun addLine(msg: String) { output = output + msg }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump v3", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = output.joinToString("\n")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            // Save dump.cs
                            if (dumpCsContent.isNotEmpty()) {
                                val csFile = File(dir, "dump.cs")
                                csFile.writeText(dumpCsContent)
                            }
                            // Save full output
                            val outFile = File(dir, "${selectedPkg.replace(".", "_")}_dump_${System.currentTimeMillis()}.txt")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved dump.cs + log to ${dir.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Game selector
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🎮 Target", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        games.take(4).forEach { (pkg, name) ->
                            FilterChip(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        games.drop(4).forEach { (pkg, name) ->
                            FilterChip(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = selectedPkg, onValueChange = { selectedPkg = it },
                        label = { Text("Package name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp))

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (selectedPkg.isBlank()) { addLine("[-] Enter package name!"); return@Button }
                            isRunning = true; output = emptyList(); dumpCsContent = ""
                            scope.launch(Dispatchers.IO) {
                                addLine("🚀 Auto Dump v3 - dump.cs Generator")
                                addLine("Package: $selectedPkg")
                                addLine("")

                                // Check root
                                addLine("🔍 Checking root...")
                                val rootOk = checkRoot2()
                                if (!rootOk) { addLine("❌ No root!"); isRunning = false; return@launch }
                                addLine("✅ Root confirmed")

                                // Find PID
                                addLine("\n🔍 Finding PID...")
                                val pid = findPid2(selectedPkg)
                                if (pid == null) { addLine("❌ Process not found. Launch game first!"); isRunning = false; return@launch }
                                addLine("✅ PID: $pid")

                                // Parse maps
                                addLine("\n📋 Parsing memory maps...")
                                val maps = parseMaps2(pid)
                                val libRegions = maps.filter { it.contains("r-xp") && it.contains(".so") }
                                val dataRegions = maps.filter { it.contains("rw-p") }
                                addLine("   Total regions: ${maps.size}, Code: ${libRegions.size}, Data: ${dataRegions.size}")

                                // Find libil2cpp
                                addLine("\n🎯 Searching for libil2cpp.so...")
                                val il2cppLine = libRegions.find { it.contains("libil2cpp.so") }
                                if (il2cppLine == null) {
                                    addLine("❌ libil2cpp.so not found!")
                                    addLine("   Available libs:")
                                    libRegions.take(5).forEach { addLine("     ${it.substringAfterLast(" ").trim()}") }
                                    isRunning = false; return@launch
                                }
                                val il2cppRange = il2cppLine.substringBefore(" ")
                                val il2cppStart = il2cppRange.substringBefore("-").toLong(16)
                                val il2cppEnd = il2cppRange.substringAfter("-").toLong(16)
                                val il2cppSize = il2cppEnd - il2cppStart
                                addLine("✅ Found libil2cpp.so @ 0x${"%X".format(il2cppStart)} (${il2cppSize / 1024}KB)")

                                // Search for global-metadata.dat magic in data regions
                                addLine("\n📦 Searching for global-metadata.dat (magic 0xFAB11BAF)...")
                                val metaMagic = byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xF1.toByte(), 0xFA.toByte())
                                var metaOffset = 0L
                                var metaFound = false

                                for (region in dataRegions) {
                                    val range = region.substringBefore(" ")
                                    val start = range.substringBefore("-").toLong(16)
                                    val end = range.substringAfter("-").toLong(16)
                                    val size = (end - start).toInt().coerceAtMost(4096)

                                    val data = readMem2(pid, start, size)
                                    if (data != null) {
                                        for (i in 0..(data.size - 4)) {
                                            if (data[i] == metaMagic[0] && data[i + 1] == metaMagic[1] &&
                                                data[i + 2] == metaMagic[2] && data[i + 3] == metaMagic[3]) {
                                                metaOffset = start + i
                                                metaFound = true
                                                addLine("✅ Found metadata @ 0x${"%X".format(metaOffset)}")
                                                break
                                            }
                                        }
                                        if (metaFound) break
                                    }
                                    if (System.currentTimeMillis() % 100 == 0L) kotlinx.coroutines.delay(1)
                                }

                                if (!metaFound) {
                                    addLine("⚠️ Metadata not found in memory (may be encrypted)")
                                    addLine("   Dumping raw memory regions instead...")
                                }

                                // Generate dump.cs
                                addLine("\n📝 Generating dump.cs...")
                                val dumpCs = StringBuilder()
                                dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v3\n")
                                dumpCs.append("// Package: $selectedPkg\n")
                                dumpCs.append("// PID: $pid\n")
                                dumpCs.append("// libil2cpp: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)}\n")
                                dumpCs.append("// Metadata: ${if (metaFound) "0x${"%X".format(metaOffset)}" else "NOT FOUND"}\n")
                                dumpCs.append("// Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

                                // Read libil2cpp.so header for offsets
                                addLine("📖 Reading libil2cpp.so ELF header...")
                                val libData = readMem2(pid, il2cppStart, 4096.coerceAtMost(il2cppSize.toInt()))
                                if (libData != null && libData.size >= 20 && libData[0] == 0x7F.toByte() && libData[1] == 'E'.code.toByte()) {
                                    val is64 = libData[4] == 2.toByte()
                                    dumpCs.append("// ELF: ${if (is64) "64-bit" else "32-bit"}\n")
                                }

                                // Parse metadata if found
                                if (metaFound) {
                                    addLine("📖 Parsing IL2CPP metadata...")
                                    val metaData = readMem2(pid, metaOffset, 16384)
                                    if (metaData != null && metaData.size >= 16) {
                                        val version = ByteBuffer.wrap(metaData, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                        val stringLiteralOffset = ByteBuffer.wrap(metaData, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                        val stringLiteralCount = ByteBuffer.wrap(metaData, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int

                                        dumpCs.append("// Metadata version: $version\n")
                                        dumpCs.append("// StringLiteral offset: 0x${"%X".format(stringLiteralOffset.toLong() and 0xFFFFFFFF)}\n")
                                        dumpCs.append("// StringLiteral count: $stringLiteralCount\n\n")

                                        // Try to extract string literals
                                        addLine("   Version: $version, StringLiterals: $stringLiteralCount")

                                        if (stringLiteralOffset > 0 && stringLiteralCount > 0 && stringLiteralCount < 100000) {
                                            val strData = readMem2(pid, metaOffset + stringLiteralOffset.toLong(), (stringLiteralCount * 8).coerceAtMost(65536))
                                            if (strData != null) {
                                                addLine("   Extracting string literals...")
                                                var extracted = 0
                                                for (i in 0 until stringLiteralCount.coerceAtMost(10000)) {
                                                    val off = i * 8
                                                    if (off + 8 <= strData.size) {
                                                        val strIdx = ByteBuffer.wrap(strData, off, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                                        val strLen = ByteBuffer.wrap(strData, off + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                                        if (strIdx > 0 && strLen > 0 && strLen < 1000) {
                                                            extracted++
                                                        }
                                                    }
                                                }
                                                addLine("   String literals found: $extracted")
                                            }
                                        }
                                    }
                                }

                                // Extract strings from libil2cpp.so
                                addLine("\n🔍 Extracting strings from libil2cpp.so...")
                                val strData = readMem2(pid, il2cppStart, il2cppSize.toInt().coerceAtMost(131072))
                                if (strData != null) {
                                    val strings = mutableListOf<String>()
                                    val sb = StringBuilder()
                                    for (b in strData) {
                                        val c = b.toInt() and 0xFF
                                        if (c in 0x20..0x7E) {
                                            sb.append(c.toChar())
                                        } else {
                                            if (sb.length >= 4) {
                                                val s = sb.toString()
                                                if (s.contains("::") || s.contains("System") || s.contains("Mono") ||
                                                    s.contains("Unity") || s.contains("Game") || s.contains("Player") ||
                                                    s.contains("Network") || s.contains("Server") || s.contains("Client")) {
                                                    strings.add(s)
                                                }
                                            }
                                            sb.clear()
                                        }
                                    }

                                    // Group by namespace
                                    val namespaces = strings.filter { it.contains("::") }.groupBy { it.substringBefore("::") }
                                    dumpCs.append("// Extracted ${strings.size} significant strings\n")
                                    dumpCs.append("// Namespaces: ${namespaces.size}\n\n")

                                    for ((ns, funcs) in namespaces.entries.sortedBy { it.key }) {
                                        dumpCs.append("// === $ns ===\n")
                                        for (f in funcs.take(50)) {
                                            dumpCs.append("// $f\n")
                                        }
                                        dumpCs.append("\n")
                                    }
                                    addLine("   Extracted ${strings.size} strings, ${namespaces.size} namespaces")
                                }

                                // Dump all memory regions
                                addLine("\n💾 Dumping memory regions...")
                                val saveDir = File(context.getExternalFilesDir(null), "dump/$selectedPkg")
                                saveDir.mkdirs()
                                var dumpCount = 0

                                for (region in libRegions) {
                                    val range = region.substringBefore(" ")
                                    val start = range.substringBefore("-").toLong(16)
                                    val end = range.substringAfter("-").toLong(16)
                                    val size = (end - start).toInt().coerceAtMost(131072)
                                    val path = region.substringAfterLast(" ").trim()

                                    if (size > 0 && path.isNotBlank() && !path.startsWith("[")) {
                                        val data = readMem2(pid, start, size)
                                        if (data != null && data.isNotEmpty()) {
                                            val safeName = path.replace("/", "_").replace(" ", "_")
                                            val outFile = File(saveDir, "${safeName}_0x${"%X".format(start)}.bin")
                                            outFile.writeBytes(data)
                                            dumpCount++
                                        }
                                    }
                                    if (dumpCount % 20 == 0) kotlinx.coroutines.delay(10)
                                }

                                addLine("   Dumped $dumpCount regions to ${saveDir.absolutePath}")

                                // Write dump.cs file
                                if (dumpCs.isNotEmpty()) {
                                    dumpCsContent = dumpCs.toString()
                                    val csFile = File(saveDir, "dump.cs")
                                    csFile.writeText(dumpCs.toString())
                                    addLine("\n✅ dump.cs saved to ${csFile.absolutePath}")
                                }

                                addLine("\n🎉 Dump complete!")
                                addLine("   dump.cs: ${saveDir.absolutePath}/dump.cs")
                                addLine("   Raw dumps: ${saveDir.absolutePath}/")
                                status = "Done: $dumpCount regions + dump.cs"
                                isRunning = false
                            }
                        }, modifier = Modifier.weight(1f), enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AccentRed else AccentGreen)) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Dumping...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dump + Generate dump.cs", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Output
            Card(Modifier.fillMaxWidth().weight(1f).padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                        if (dumpCsContent.isNotEmpty()) Text("✅ dump.cs ready", color = AccentCyan, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🚀", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                                Text("Select game + tap Dump", color = TextSecondary, fontSize = 13.sp)
                                Text("Root required for memory dump + dump.cs generation", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn {
                            items(output) { line ->
                                val color = when {
                                    line.startsWith("✅") || line.startsWith("🎉") -> AccentGreen
                                    line.startsWith("❌") -> AccentRed
                                    line.startsWith("⚠️") -> AccentOrange
                                    line.startsWith("🎯") || line.startsWith("📦") -> AccentCyan
                                    else -> TextPrimary
                                }
                                Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
            Text("© Panxcz & Freebuff | v3.0", color = TextSecondary, fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun checkRoot2(): Boolean {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = p.inputStream.bufferedReader().readText()
        p.waitFor()
        output.contains("uid=0")
    } catch (e: Exception) { false }
}

private fun findPid2(pkg: String): String? {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof $pkg"))
        val output = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (output.isNotBlank()) output.split("\\s+".toRegex()).firstOrNull() else null
    } catch (e: Exception) { null }
}

private fun parseMaps2(pid: String): List<String> {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps"))
        val lines = p.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
        p.waitFor()
        lines
    } catch (e: Exception) { emptyList() }
}

private fun readMem2(pid: String, addr: Long, size: Int): ByteArray? {
    return try {
        val cmd = "dd if=/proc/$pid/mem bs=1 count=$size skip=$addr 2>/dev/null"
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val data = p.inputStream.readBytes()
        p.waitFor()
        if (data.size > 0) data else null
    } catch (e: Exception) { null }
}

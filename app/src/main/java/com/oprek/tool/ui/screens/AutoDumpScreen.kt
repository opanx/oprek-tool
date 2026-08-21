package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.oprek.tool.ui.theme.*
import java.io.File

/**
 * AutoDumpScreen — One-click root dump for games
 * Flow: Check root → Detect running games → Auto-dump libil2cpp + DEX → Save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var runningGames by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var selectedPkg by remember { mutableStateOf("") }
    var autoDetected by remember { mutableStateOf(false) }
    var dumpFormat by remember { mutableStateOf(0) } // 0=Full, 1=il2cpp.h, 2=game.h

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = output.joinToString("\n")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("auto_dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            val name = if (selectedPkg.isNotEmpty()) selectedPkg.replace(".", "_") else "auto_dump"
                            val ext = when (dumpFormat) { 1 -> "h"; 2 -> "h"; else -> "txt" }
                            val outFile = File(dir, "${name}_${System.currentTimeMillis()}.$ext")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Root check card
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏴 Root Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentRed, modifier = Modifier.weight(1f))
                        when (rootOk) {
                            true -> Text("✅ Rooted", color = AccentGreen, fontWeight = FontWeight.Bold)
                            false -> Text("❌ No Root", color = AccentRed, fontWeight = FontWeight.Bold)
                            null -> {
                                IconButton(onClick = {
                                    Thread {
                                        rootOk = checkAutoDumpRoot()
                                    }.start()
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Refresh, "Check", tint = AccentCyan)
                                }
                            }
                        }
                    }
                    if (rootOk == false) {
                        Spacer(Modifier.height(4.dp))
                        Text("⚠️ Root required. Install Magisk/KernelSU/SuperSU and grant permission.", fontSize = 11.sp, color = AccentOrange)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Auto-detect running games
                    if (rootOk == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎮 Running Games", fontSize = 12.sp, color = AccentCyan, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                isRunning = true
                                Thread {
                                    runningGames = detectRunningGames(context)
                                    autoDetected = true
                                    isRunning = false
                                    if (runningGames.isNotEmpty() && selectedPkg.isEmpty()) {
                                        selectedPkg = runningGames.first().second
                                    }
                                }.start()
                            }, modifier = Modifier.size(28.dp)) {
                                if (isRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentCyan)
                                else Icon(Icons.Default.Refresh, "Detect", tint = AccentCyan)
                            }
                        }
                        if (autoDetected) {
                            if (runningGames.isEmpty()) {
                                Text("No game processes found. Start a game first.", fontSize = 11.sp, color = TextMuted)
                            } else {
                                runningGames.forEach { (pid, pkg) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg },
                                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
                                        Text("$pkg (PID: $pid)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Manual package input
                        OutlinedTextField(value = selectedPkg, onValueChange = { selectedPkg = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Package name", color = TextMuted) },
                            placeholder = { Text("com.mobile.legends", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = AccentCyan) })

                        Spacer(Modifier.height(8.dp))

                        // Quick presets
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MLBB" to "com.mobile.legends", "FF" to "com.dts.freefiremax", "PUBG" to "com.tencent.ig").forEach { (label, pkg) ->
                                AssistChip(onClick = { selectedPkg = pkg },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Format selector
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Full" to 0, "il2cpp.h" to 1, "game.h" to 2).forEach { (label, idx) ->
                                FilterChip(selected = dumpFormat == idx, onClick = { dumpFormat = idx },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Start dump button
                        Button(onClick = {
                            isRunning = true; output = emptyList(); status = "Starting auto-dump..."; progress = 0f
                            Thread {
                                try {
                                    val results = autoDump(context, selectedPkg, dumpFormat) { msg, p ->
                                        status = msg; if (p >= 0) progress = p
                                    }
                                    output = results
                                    status = "✅ Dump complete! ${results.size} lines"
                                    progress = 1f
                                } catch (e: Exception) {
                                    output = listOf("ERROR: ${e.message}") +
                                        (e.stackTrace?.take(5)?.map { "  at $it" } ?: emptyList())
                                    status = "❌ Error: ${e.message}"
                                }
                                isRunning = false
                            }.start()
                        },
                        enabled = !isRunning && selectedPkg.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(12.dp)) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Dumping...")
                            } else {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(4.dp))
                                Text("🚀 Start Auto Dump", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Progress bar
            if (isRunning && progress > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AccentRed, trackColor = DarkCard)
            }

            if (status.isNotEmpty()) {
                Text(status, fontSize = 11.sp, color = if (status.startsWith("❌")) AccentRed else AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 3)
            }

            // Output
            if (output.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} lines", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("📁 /sdcard/Download/OprekTool/dump/", fontSize = 10.sp, color = TextMuted)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            .background(DarkBg).padding(horizontal = 4.dp)) {
                            Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.startsWith("[+]") -> AccentGreen
                                    line.startsWith("[-]") -> AccentRed
                                    line.startsWith("[!]") -> AccentOrange
                                    line.startsWith("//") -> AccentPurple
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (output.isEmpty() && !isRunning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚀", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Auto Dump", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("One-click root dump for games", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("Flow:", fontSize = 12.sp, color = AccentRed)
                        Text("1. Check root access", fontSize = 11.sp, color = TextMuted)
                        Text("2. Detect running game or enter package", fontSize = 11.sp, color = TextMuted)
                        Text("3. Auto-dump libil2cpp + DEX + metadata", fontSize = 11.sp, color = TextMuted)
                        Text("4. Save to /sdcard/Download/OprekTool/dump/", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

/* ─── Root check ─── */
private fun checkAutoDumpRoot(): Boolean {
    val suPaths = listOf("su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk/su")
    for (suPath in suPaths) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "id"))
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (stdout.contains("uid=0") || stderr.contains("uid=0")) return true
            val proc2 = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$suPath -c 'id 2>&1'"))
            val out2 = proc2.inputStream.bufferedReader().readText()
            proc2.waitFor()
            if (out2.contains("uid=0")) return true
        } catch (_: Exception) { }
    }
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which su 2>/dev/null && su -c 'echo ROOT_OK' 2>/dev/null"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (output.contains("ROOT_OK")) return true
    } catch (_: Exception) { }
    return false
}

/* ─── Detect running game processes ─── */
private fun detectRunningGames(ctx: Context): List<Pair<Int, String>> {
    val results = mutableListOf<Pair<Int, String>>()
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // Known game package patterns
        val gamePatterns = listOf(
            "com.mobile.legends", "com.dts.freefiremax", "com.tencent.ig",
            "com.miHoYo", "com.supercell", "com.ea.", "com.garena",
            "com.activision", "com.pubg", "com.epicgames", "com.levelinfinite",
            "com.moonton", "com.ngagames", "com.vng.", "com.gameloft"
        )
        for (line in output.lines()) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                val pid = parts[1].toIntOrNull()
                val cmdline = if (parts.size >= 10) parts.last() else ""
                if (pid != null && gamePatterns.any { cmdline.contains(it, ignoreCase = true) }) {
                    results.add(pid to cmdline)
                }
            }
        }
    } catch (_: Exception) { }
    return results
}

/* ─── Auto dump ─── */
private fun autoDump(ctx: Context, pkg: String, format: Int, onProgress: (String, Float) -> Unit): List<String> {
    val results = mutableListOf<String>()
    val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
    outDir.mkdirs()

    onProgress("Finding PID for $pkg...", 0.05f)

    // Find PID
    val psOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A")).inputStream.bufferedReader().readText()
    val line = psOut.lines().firstOrNull { it.contains(pkg) }
    val pid = line?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull()
    if (pid == null) {
        results.add("[-] Process not found: $pkg")
        results.add("[!] Make sure the game is running")
        results.add("[!] Available processes:")
        psOut.lines().filter { it.contains(pkg.substringBefore(".")) }.take(5).forEach { results.add("    $it") }
        return results
    }

    results.add("[+] Found: $pkg (PID: $pid)")
    onProgress("Reading /proc/$pid/maps...", 0.15f)

    // Read maps
    val maps = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps")).inputStream.bufferedReader().readText().lines()
    results.add("[+] Memory maps: ${maps.size} entries")

    // Find libil2cpp.so
    val libEntry = maps.firstOrNull { it.contains("libil2cpp.so") && it.contains("r-xp") }
        ?: maps.firstOrNull { it.contains("libil2cpp.so") }

    if (libEntry != null) {
        val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)")
        val match = addrRegex.find(libEntry)
        if (match != null) {
            val start = match.groupValues[1].toLong(16)
            val end = match.groupValues[2].toLong(16)
            val size = end - start
            results.add("[+] libil2cpp.so: 0x${start.toString(16)}-0x${end.toString(16)} ($size bytes)")
            onProgress("Dumping libil2cpp.so...", 0.3f)

            val outFile = File(outDir, "${pkg.replace(".", "_")}_libil2cpp.so")
            val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$start count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()
            if (outFile.exists() && outFile.length() > 0) {
                results.add("[+] Saved: ${outFile.absolutePath} (${outFile.length()} bytes)")
            } else {
                results.add("[-] Failed to dump libil2cpp.so")
            }
        }
    } else {
        results.add("[!] libil2cpp.so not found in process (not Unity/IL2CPP?)")
    }

    // Find global-metadata.dat
    onProgress("Looking for global-metadata.dat...", 0.5f)
    val metaEntry = maps.firstOrNull { it.contains("global-metadata.dat") }
    if (metaEntry != null) {
        val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)")
        val match = addrRegex.find(metaEntry)
        if (match != null) {
            val start = match.groupValues[1].toLong(16)
            val end = match.groupValues[2].toLong(16)
            val size = end - start
            results.add("[+] global-metadata.dat: 0x${start.toString(16)}-0x${end.toString(16)} ($size bytes)")

            val outFile = File(outDir, "${pkg.replace(".", "_")}_global-metadata.dat")
            val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$start count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()
            if (outFile.exists() && outFile.length() > 0) {
                results.add("[+] Saved: ${outFile.absolutePath} (${outFile.length()} bytes)")
            }
        }
    }

    // Find DEX in memory
    onProgress("Scanning for DEX in memory...", 0.7f)
    val potentialDex = maps.filter { (it.contains("r--p") || it.contains("r-xp")) && !it.contains("libil2cpp") }
    var dexFound = 0
    for (entry in potentialDex.take(200)) {
        val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)")
        val match = addrRegex.find(entry) ?: continue
        val start = match.groupValues[1].toLong(16)
        val size = match.groupValues[2].toLong(16) - start
        if (size < 1024 || size > 52428800) continue

        // Read first 4 bytes to check DEX magic
        val headerCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=$start count=4 2>/dev/null'"
        val headerOut = Runtime.getRuntime().exec(arrayOf("sh", "-c", headerCmd)).inputStream.readBytes()
        if (headerOut.size >= 4 && headerOut[0] == 0x64.toByte() && headerOut[1] == 0x65.toByte() &&
            headerOut[2] == 0x78.toByte() && headerOut[3] == 0x0A.toByte()) {
            dexFound++
            val outFile = File(outDir, "${pkg.replace(".", "_")}_classes${if (dexFound == 1) "" else dexFound}.dex")
            val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$start count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()
            if (outFile.exists() && outFile.length() > 0) {
                results.add("[+] DEX #$dexFound at 0x${start.toString(16)} ($size bytes) → ${outFile.name}")
            }
        }
    }
    if (dexFound == 0) results.add("[!] No DEX files found in process memory")
    else results.add("[+] Total DEX files dumped: $dexFound")

    // List all .so libraries loaded
    onProgress("Listing loaded libraries...", 0.9f)
    val libs = maps.filter { it.contains(".so") && it.contains("r-xp") }.mapNotNull {
        Regex("(/[\\w./-]+\\.so)").find(it)?.groupValues?.get(1)
    }.distinct().sorted()
    if (libs.isNotEmpty()) {
        results.add("")
        results.add("[+] Loaded native libraries (${libs.size}):")
        libs.take(30).forEach { results.add("    $it") }
        if (libs.size > 30) results.add("    ... and ${libs.size - 30} more")
    }

    // Summary
    results.add("")
    results.add("[+] ========================================")
    results.add("[+] DUMP COMPLETE")
    results.add("[+] Package: $pkg (PID: $pid)")
    results.add("[+] Output: ${outDir.absolutePath}")
    results.add("[+] Files saved to /sdcard/Download/OprekTool/dump/")
    results.add("[+] ========================================")

    return results
}

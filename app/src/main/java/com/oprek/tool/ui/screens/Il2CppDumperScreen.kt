package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * IL2CPP Dumper Screen — Root + File modes
 * Dumps class/method/field metadata from libil2cpp.so + global-metadata.dat
 * Supports: file dump, root process attach, output as il2cpp.h / game.h / main.h
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Il2CppDumperScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isDumping by remember { mutableStateOf(false) }
    var dumpMode by remember { mutableStateOf(0) } // 0=File, 1=Root Process
    var libPath by remember { mutableStateOf("") }
    var metaPath by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var outputFormat by remember { mutableStateOf(0) } // 0=Full, 1=il2cpp.h, 2=game.h, 3=script.json
    var filterPublic by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 IL2CPP Dumper", fontWeight = FontWeight.Bold) },
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
                            cb.setPrimaryClip(ClipData.newPlainText("il2cpp_dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool")
                            dir.mkdirs()
                            val ext = when (outputFormat) {
                                1 -> "h"; 2 -> "h"; 3 -> "json"; else -> "cs"
                            }
                            val name = when (outputFormat) {
                                1 -> "il2cpp"; 2 -> "game"; 3 -> "script"; else -> "il2cpp_dump"
                            }
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
            if (showSettings) {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // Mode selector
                        Text("🎯 Dump Mode", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dumpMode == 0, onClick = { dumpMode = 0 },
                                label = { Text("📁 From Files", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                            FilterChip(selected = dumpMode == 1, onClick = { dumpMode = 1 },
                                label = { Text("🏴 Root Process", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentRed.copy(alpha = 0.2f)))
                        }

                        Spacer(Modifier.height(8.dp))

                        if (dumpMode == 0) {
                            // File-based dump
                            OutlinedTextField(value = libPath, onValueChange = { libPath = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("libil2cpp.so path", color = TextMuted) },
                                placeholder = { Text("/data/app/.../lib/arm64/libil2cpp.so", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                                leadingIcon = { Icon(Icons.Default.Extension, null, tint = AccentCyan) })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = metaPath, onValueChange = { metaPath = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("global-metadata.dat path", color = TextMuted) },
                                placeholder = { Text("/data/app/.../assets/bin/Data/Managed/Metadata/global-metadata.dat", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                                leadingIcon = { Icon(Icons.Default.Storage, null, tint = AccentCyan) })
                            Spacer(Modifier.height(8.dp))
                            // Quick paths
                            Text("Quick presets:", fontSize = 11.sp, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                listOf("MLBB" to "com.mobile.legends", "FF" to "com.dts.freefiremax",
                                    "PUBG" to "com.tencent.ig", "Genshin" to "com.miHoYo.GenshinImpact"
                                ).forEach { (label, pkg) ->
                                    AssistChip(onClick = {
                                        libPath = "/data/data/$pkg/files/UnityIL2CPP/arm64-v8a/libil2cpp.so"
                                        metaPath = "/data/data/$pkg/files/UnityIL2CPP/Metadata/global-metadata.dat"
                                    }, label = { Text(label, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface))
                                }
                            }
                        } else {
                            // Root process dump
                            OutlinedTextField(value = packageName, onValueChange = { packageName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Package name / PID", color = TextMuted) },
                                placeholder = { Text("com.mobile.legends or 12345", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, cursorColor = AccentRed),
                                leadingIcon = { Icon(Icons.Default.Memory, null, tint = AccentRed) })
                            Spacer(Modifier.height(6.dp))
                            Text("⚠️ Requires root (Magisk/KernelSU/SuperSU)", fontSize = 11.sp, color = AccentOrange)
                            Text("Will read from /proc/PID/maps and attach to process memory", fontSize = 10.sp, color = TextMuted)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Output format
                        Text("Output format:", fontSize = 11.sp, color = TextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Full Dump" to 0, "il2cpp.h" to 1, "game.h" to 2, "script.json" to 3).forEach { (label, idx) ->
                                AssistChip(onClick = { outputFormat = idx },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (outputFormat == idx) AccentCyan.copy(alpha = 0.3f) else DarkSurface))
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterPublic, onCheckedChange = { filterPublic = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentCyan))
                            Text("Public members only", fontSize = 12.sp, color = TextSecondary)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                isDumping = true; output = emptyList(); status = "Starting..."; showSettings = false; progress = 0f
                                Thread {
                                    try {
                                        val results = if (dumpMode == 0) {
                                            dumpIl2CppFiles(context, libPath, metaPath, outputFormat, filterPublic) { msg, p ->
                                                status = msg; if (p >= 0) progress = p
                                            }
                                        } else {
                                            dumpIl2CppRoot(context, packageName, outputFormat, filterPublic) { msg, p ->
                                                status = msg; if (p >= 0) progress = p
                                            }
                                        }
                                        output = results
                                        status = "Done! ${results.size} lines"
                                        progress = 1f
                                    } catch (e: Exception) {
                                        output = listOf("ERROR: ${e.message}") +
                                            (e.stackTrace?.take(10)?.map { "  at $it" } ?: emptyList())
                                        status = "Error: ${e.message}"
                                    }
                                    isDumping = false
                                }.start()
                            },
                            enabled = !isDumping && (dumpMode == 0 && libPath.isNotEmpty() && metaPath.isNotEmpty() || dumpMode == 1 && packageName.isNotEmpty()),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dumpMode == 1) AccentRed else AccentCyan)
                            ) {
                                if (isDumping) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Dumping...")
                                } else {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (dumpMode == 1) "Root Dump" else "Start Dump")
                                }
                            }
                            OutlinedButton(onClick = { libPath = ""; metaPath = ""; packageName = ""; output = emptyList(); status = ""; showSettings = true }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Progress bar
            if (isDumping && progress > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AccentCyan, trackColor = DarkCard)
            }

            if (status.isNotEmpty()) {
                Text(status, fontSize = 11.sp, color = if (status.startsWith("Error")) AccentRed else AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 2)
            }

            if (output.isNotEmpty() && !showSettings) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} lines", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Settings, "Settings", Modifier.size(16.dp), tint = TextMuted)
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            .background(when {
                                line.startsWith("//") -> DarkCard.copy(alpha = 0.3f)
                                line.startsWith("#pragma") || line.startsWith("#include") -> AccentCyan.copy(alpha = 0.1f)
                                else -> DarkBg
                            }).padding(horizontal = 4.dp)) {
                            Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.startsWith("//") -> AccentPurple
                                    line.startsWith("#pragma") || line.startsWith("#include") -> AccentCyan
                                    line.startsWith("class ") || line.startsWith("struct ") -> AccentOrange
                                    line.startsWith("namespace ") -> AccentGreen
                                    line.contains("void ") || line.contains("int ") || line.contains("bool ") -> AccentGreen
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                        }
                    }
                }
            }

            if (output.isEmpty() && !isDumping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("IL2CPP Dumper", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Dump class/method/field metadata from Unity games", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Text("Modes:", fontSize = 12.sp, color = AccentCyan)
                        Text("• File Mode: libil2cpp.so + global-metadata.dat", fontSize = 11.sp, color = TextMuted)
                        Text("• Root Mode: Attach to running game process", fontSize = 11.sp, color = TextMuted)
                        Text("Output: il2cpp.h / game.h / script.json / Full", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

/* ─── Robust root check ─── */
private fun checkRootAccess(): Boolean {
    // Try multiple su paths and methods
    val suPaths = listOf("su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk/su")
    
    for (suPath in suPaths) {
        try {
            // Method 1: su -c id
            val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "id"))
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (stdout.contains("uid=0") || stderr.contains("uid=0")) return true
            
            // Method 2: su 0 id
            val proc2 = Runtime.getRuntime().exec(arrayOf(suPath, "0", "id"))
            val stdout2 = proc2.inputStream.bufferedReader().readText()
            proc2.waitFor()
            if (stdout2.contains("uid=0")) return true
            
            // Method 3: echo 1 > /proc/sys/kernel/su/enabled style
            val proc3 = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$suPath -c 'id 2>&1'"))
            val out3 = proc3.inputStream.bufferedReader().readText()
            proc3.waitFor()
            if (out3.contains("uid=0")) return true
        } catch (_: Exception) { }
    }
    
    // Method 4: check if su binary exists and test with echo
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which su 2>/dev/null && su -c 'echo ROOT_OK' 2>/dev/null"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (output.contains("ROOT_OK")) return true
    } catch (_: Exception) { }
    
    return false
}

/* ─── File-based dump ─── */
private fun dumpIl2CppFiles(ctx: Context, libPath: String, metaPath: String, format: Int, publicOnly: Boolean, onProgress: (String, Float) -> Unit): List<String> {
    val libFile = File(libPath)
    val metaFile = File(metaPath)
    if (!libFile.exists()) return listOf("// ERROR: libil2cpp.so not found: $libPath")
    if (!metaFile.exists()) return listOf("// ERROR: global-metadata.dat not found: $metaPath")

    onProgress("Reading libil2cpp.so (${libFile.length()} bytes)...", 0.1f)
    val libBytes = libFile.readBytes()
    onProgress("Reading global-metadata.dat (${metaFile.length()} bytes)...", 0.3f)
    val metaBytes = metaFile.readBytes()

    return analyzeIl2Cpp(libBytes, metaBytes, libFile.length(), metaFile.length(), format, publicOnly, onProgress)
}

/* ─── Root-based dump ─── */
private fun dumpIl2CppRoot(ctx: Context, target: String, format: Int, publicOnly: Boolean, onProgress: (String, Float) -> Unit): List<String> {
    onProgress("Checking root access...", 0.05f)
    val hasRoot = checkRootAccess()
    if (!hasRoot) return listOf(
        "// ERROR: No root access found",
        "// Solutions:",
        "// 1. Install Magisk (https://topjohnwu.github.io/Magisk/)",
        "// 2. Install KernelSU (https://kernelsu.org/)",
        "// 3. Make sure to GRANT root permission when prompted",
        "// 4. Try: su -c id in Termux to verify"
    )

    onProgress("Finding process...", 0.1f)
    // Find PID
    val pid = if (target.toIntOrNull() != null) {
        target.toInt()
    } else {
        val psOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A")).inputStream.bufferedReader().readText()
        val line = psOut.lines().firstOrNull { it.contains(target) }
        line?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull()
            ?: return listOf("// ERROR: Process '$target' not found", "// Make sure the game is running")
    }

    onProgress("Reading /proc/$pid/maps...", 0.2f)
    val mapsOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps")).inputStream.bufferedReader().readText()
    val maps = mapsOut.lines()

    // Find libil2cpp.so in memory maps
    val libEntry = maps.firstOrNull { it.contains("libil2cpp.so") && it.contains("r-xp") }
        ?: maps.firstOrNull { it.contains("libil2cpp.so") }

    val metaEntry = maps.firstOrNull { it.contains("global-metadata.dat") }

    if (libEntry == null) return listOf("// ERROR: libil2cpp.so not found in process $pid maps", "// Game may not use IL2CPP")

    // Parse address range from maps
    val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)")
    val libMatch = addrRegex.find(libEntry) ?: return listOf("// ERROR: Cannot parse libil2cpp.so address")
    val libStart = libMatch.groupValues[1].toLong(16)
    val libEnd = libMatch.groupValues[2].toLong(16)
    val libSize = libEnd - libStart

    onProgress("Found libil2cpp.so at 0x${libStart.toString(16)}-0x${libEnd.toString(16)} ($libSize bytes)", 0.3f)

    // Dump libil2cpp.so from memory using dd
    val tmpLib = File(ctx.cacheDir, "il2cpp_dump_lib.so")
    val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${tmpLib.absolutePath} bs=1 skip=$libStart count=$libSize 2>/dev/null'"
    val ddProc = Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd))
    ddProc.waitFor()

    if (!tmpLib.exists() || tmpLib.length() == 0L) {
        return listOf("// ERROR: Failed to dump libil2cpp.so from memory", "// dd command failed")
    }

    onProgress("Dumped libil2cpp.so from memory (${tmpLib.length()} bytes)", 0.5f)
    val libBytes = tmpLib.readBytes()

    // Try to dump metadata too
    var metaBytes = byteArrayOf()
    if (metaEntry != null) {
        val metaMatch = addrRegex.find(metaEntry)
        if (metaMatch != null) {
            val metaStart = metaMatch.groupValues[1].toLong(16)
            val metaEnd = metaMatch.groupValues[2].toLong(16)
            val metaSize = metaEnd - metaStart
            val tmpMeta = File(ctx.cacheDir, "il2cpp_dump_meta.dat")
            val ddMeta = "su -c 'dd if=/proc/$pid/mem of=${tmpMeta.absolutePath} bs=1 skip=$metaStart count=$metaSize 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddMeta)).waitFor()
            if (tmpMeta.exists() && tmpMeta.length() > 0) {
                metaBytes = tmpMeta.readBytes()
                onProgress("Dumped global-metadata.dat from memory (${metaBytes.size} bytes)", 0.7f)
            }
        }
    }

    tmpLib.delete()

    val results = analyzeIl2Cpp(libBytes, metaBytes, libBytes.size.toLong(), metaBytes.size.toLong(), format, publicOnly, onProgress)
    // Prepend root info
    return listOf(
        "// Root dump from PID $pid",
        "// Target: $target",
        "// libil2cpp.so: ${libBytes.size} bytes (from 0x${libStart.toString(16)})",
        "// global-metadata.dat: ${metaBytes.size} bytes",
        ""
    ) + results
}

/* ─── Common IL2CPP analysis ─── */
private fun analyzeIl2Cpp(libBytes: ByteArray, metaBytes: ByteArray, libSize: Long, metaSize: Long, format: Int, publicOnly: Boolean, onProgress: (String, Float) -> Unit): List<String> {
    val results = mutableListOf<String>()

    // Validate ELF
    if (libBytes.size < 64 || libBytes[0] != 0x7F.toByte() || libBytes[1] != 'E'.code.toByte()) {
        return listOf("// ERROR: libil2cpp.so is not a valid ELF file")
    }
    val is64 = libBytes[4] == 2.toByte()
    val isLE = libBytes[5] == 1.toByte()
    val endian = if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

    // Validate metadata
    var metaValid = false
    var metaVersion = 0
    if (metaBytes.size >= 20) {
        val buf = ByteBuffer.wrap(metaBytes).order(endian)
        metaVersion = buf.getInt(4)
        metaValid = buf.getInt(0) == -559038737 // 0xFAB11BAF
    }

    onProgress("Analyzing patterns...", 0.8f)

    if (format == 1 || format == 2) {
        // Header file format
        results.add("// Auto-generated by OprekTool IL2CPP Dumper")
        results.add("// Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        results.add("#pragma once")
        results.add("#include <cstdint>")
        results.add("#include <string>")
        results.add("")
        results.add("// Architecture: ${if (is64) "ARM64" else "ARM32"}")
        results.add("// Metadata version: $metaVersion")
        results.add("")
    } else if (format == 3) {
        // JSON format
        results.add("{")
        results.add("  \"arch\": \"${if (is64) "arm64" else "arm32"}\",")
        results.add("  \"metadata_version\": $metaVersion,")
        results.add("  \"lib_size\": $libSize,")
        results.add("  \"meta_size\": $metaSize,")
    } else {
        // Full dump
        results.add("// ==========================================")
        results.add("// IL2CPP Dump - Generated by OprekTool")
        results.add("// Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        results.add("// libil2cpp.so: $libSize bytes")
        results.add("// global-metadata.dat: $metaSize bytes")
        results.add("// Architecture: ${if (is64) "ARM64 (x64)" else "ARM32 (x86)"}")
        results.add("// Metadata version: $metaVersion")
        results.add("// ==========================================")
        results.add("")
    }

    // Extract strings from both files
    onProgress("Extracting strings...", 0.85f)
    val stringsLib = extractStringsFromBytes(libBytes, 4)
    val stringsMeta = if (metaBytes.isNotEmpty()) extractStringsFromBytes(metaBytes, 3) else emptyList()

    // Class names heuristic
    val classNames = stringsLib.filter {
        it.length in 3..64 && it[0].isUpperCase() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '<' || c == '>' }
        && !it.startsWith("0x") && !it.contains("...") && !it.startsWith("//")
        && !it.contains("__") && it != "main" && it != "string"
    }.distinct().sorted()

    // Method names heuristic
    val methodNames = stringsLib.filter {
        it.length in 3..64 && it[0].isLowerCase() && it.all { c -> c.isLetterOrDigit() || c == '_' }
        && !it.startsWith("0x") && !it.startsWith("get") && !it.startsWith("set")
        && it != "main" && it != "malloc" && it != "free" && it != "memcpy"
    }.distinct().sorted()

    // Namespace patterns
    val namespaces = stringsLib.filter {
        it.contains('.') && it.length in 5..80 && it[0].isUpperCase()
        && it.split('.').all { part -> part.all { c -> c.isLetterOrDigit() || c == '_' } }
    }.distinct().sorted()

    // Known IL2CPP API functions
    val il2cppApis = stringsLib.filter { it.startsWith("il2cpp_") }.distinct().sorted()

    // String literals from metadata
    val stringLiterals = stringsMeta.filter { it.length >= 4 && !it.contains(0.toChar()) }
        .filter { it.all { c -> c.code in 0x20..0x7E || c == '\n' || c == '\t' } }

    when (format) {
        1, 2 -> {
            // Header format
            if (namespaces.isNotEmpty()) {
                results.add("// Namespaces: ${namespaces.size}")
                namespaces.take(100).forEach { results.add("// $it") }
                results.add("")
            }
            results.add("// Classes: ${classNames.size}")
            classNames.take(500).forEach { cls ->
                if (!publicOnly || cls[0].isUpperCase()) {
                    results.add("class $cls {")
                    val related = methodNames.filter { m -> m.lowercase().take(4).let { cls.lowercase().take(4) == it || cls.lowercase().contains(it.take(4)) } }.take(8)
                    related.forEach { results.add("    // $it();") }
                    results.add("};")
                    results.add("")
                }
            }
        }
        3 -> {
            // JSON format
            results.add("  \"namespaces\": [")
            namespaces.take(100).forEachIndexed { i, ns -> results.add("    \"$ns\"${if (i < 100.coerceAtMost(namespaces.size) - 1) "," else ""}") }
            results.add("  ],")
            results.add("  \"classes\": [")
            classNames.take(1000).forEachIndexed { i, cls -> results.add("    \"$cls\"${if (i < 1000.coerceAtMost(classNames.size) - 1) "," else ""}") }
            results.add("  ],")
            results.add("  \"methods\": [")
            methodNames.take(1000).forEachIndexed { i, m -> results.add("    \"$m\"${if (i < 1000.coerceAtMost(methodNames.size) - 1) "," else ""}") }
            results.add("  ],")
            results.add("  \"apis\": [")
            il2cppApis.take(500).forEachIndexed { i, a -> results.add("    \"$a\"${if (i < 500.coerceAtMost(il2cppApis.size) - 1) "," else ""}") }
            results.add("  ],")
            results.add("  \"string_literals\": [")
            stringLiterals.take(2000).forEachIndexed { i, s ->
                val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
                results.add("    \"$escaped\"${if (i < 2000.coerceAtMost(stringLiterals.size) - 1) "," else ""}")
            }
            results.add("  ]")
            results.add("}")
        }
        else -> {
            // Full dump
            if (namespaces.isNotEmpty()) {
                results.add("// ==========================================")
                results.add("// Namespaces (${namespaces.size})")
                results.add("// ==========================================")
                namespaces.take(200).forEach { results.add("// $it") }
                if (namespaces.size > 200) results.add("// ... and ${namespaces.size - 200} more")
                results.add("")
            }
            results.add("// ==========================================")
            results.add("// Classes (${classNames.size})")
            results.add("// ==========================================")
            results.add("")
            classNames.groupBy { it.first() }.forEach { (letter, classes) ->
                results.add("// --- $letter ---")
                classes.forEach { cls ->
                    results.add("class $cls {")
                    val related = methodNames.filter { m -> cls.lowercase().take(4).let { c -> m.lowercase().contains(c) || c == m.lowercase().take(4) } }.take(10)
                    related.forEach { results.add("    // $it();") }
                    results.add("};")
                    results.add("")
                }
            }
            results.add("// ==========================================")
            results.add("// Methods (${methodNames.size})")
            results.add("// ==========================================")
            methodNames.take(2000).forEach { results.add("// $it()") }
            if (methodNames.size > 2000) results.add("// ... and ${methodNames.size - 2000} more")
            results.add("")
            if (il2cppApis.isNotEmpty()) {
                results.add("// ==========================================")
                results.add("// IL2CPP APIs (${il2cppApis.size})")
                results.add("// ==========================================")
                il2cppApis.forEach { results.add("// $it") }
                results.add("")
            }
            if (stringLiterals.isNotEmpty()) {
                results.add("// ==========================================")
                results.add("// String Literals (${stringLiterals.size})")
                results.add("// ==========================================")
                stringLiterals.take(5000).forEach { results.add("// \"$it\"") }
                if (stringLiterals.size > 5000) results.add("// ... and ${stringLiterals.size - 5000} more")
                results.add("")
            }
            results.add("// ==========================================")
            results.add("// SUMMARY")
            results.add("// ==========================================")
            results.add("// Namespaces: ${namespaces.size}")
            results.add("// Classes: ${classNames.size}")
            results.add("// Methods: ${methodNames.size}")
            results.add("// IL2CPP APIs: ${il2cppApis.size}")
            results.add("// String Literals: ${stringLiterals.size}")
            results.add("// Total strings analyzed: ${stringsLib.size + stringsMeta.size}")
        }
    }

    return results
}

/* ─── Extract printable strings from byte array ─── */
private fun extractStringsFromBytes(bytes: ByteArray, minLen: Int): List<String> {
    val results = mutableListOf<String>()
    val sb = StringBuilder()
    for (b in bytes) {
        val c = (b.toInt() and 0xFF).toChar()
        if (c.code in 0x20..0x7E || c == '\t' || c == '\n') {
            sb.append(c)
        } else {
            if (sb.length >= minLen) results.add(sb.toString())
            sb.clear()
        }
    }
    if (sb.length >= minLen) results.add(sb.toString())
    return results
}

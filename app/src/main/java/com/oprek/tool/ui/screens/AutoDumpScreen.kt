@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalLayoutApi::class)
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

// ═══════════════════════════════════════════════════════════════
// AutoDump v7 — Unified IL2CPP dump pipeline
// Strategy A: Valid metadata → parse TypeDef/MethodDef/FieldDef → dump.cs
// Strategy B: Encrypted/missing metadata → raw dump lib + meta for PC
// ═══════════════════════════════════════════════════════════════

data class GamePreset(val name: String, val pkg: String, val il2cppLib: String, val desc: String)

private val gamePresets = listOf(
    GamePreset("MLBB", "com.mobile.legends", "libunity.so", "Unity IL2CPP (metadata encrypted)"),
    GamePreset("FF MAX", "com.dts.freefiremax", "libil2cpp.so", "Garena Free Fire Max"),
    GamePreset("FF", "com.dts.freefireth", "libil2cpp.so", "Garena Free Fire"),
    GamePreset("PUBG Mobile", "com.tencent.ig", "libil2cpp.so", "PUBG Mobile"),
    GamePreset("PUBGM HD", "com.tencent.tmgp.pubgmhd", "libil2cpp.so", "PUBG Mobile HD"),
    GamePreset("Genshin", "com.miHoYo.GenshinImpact", "libil2cpp.so", "miHoYo Genshin Impact"),
    GamePreset("BloodStrike", "com.proximabeta.mf.ussdk", "libil2cpp.so", "NetEase BloodStrike"),
    GamePreset("CODM", "com.garena.game.codm", "libil2cpp.so", "Call of Duty Mobile"),
    GamePreset("Brawl Stars", "com.supercell.brawlstars", "libil2cpp.so", "Supercell Brawl Stars"),
    GamePreset("Manual", "", "", "Enter package + lib name manually"),
)

private const val MAGIC_META = -559038737 // 0xFAB11BAF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var selectedPreset by remember { mutableIntStateOf(0) }
    var manualPkg by remember { mutableStateOf("") }
    var manualLib by remember { mutableStateOf("libil2cpp.so") }
    var dumpCsContent by remember { mutableStateOf("") }

    fun addLine(msg: String) { output = output + msg }
    fun setProgress(p: Float) { progress = p }

    // ─── su shell helper ───
    fun suShell(cmd: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "" }

    // ─── Find PID with fallback ───
    fun findPid(pkg: String): Int? {
        // Try pidof first
        var out = suShell("pidof $pkg").trim()
        var pid = out.split(Regex("\\s+")).firstOrNull { it.all { c -> c.isDigit() } }?.toIntOrNull()
        if (pid != null) return pid
        // Fallback: ps -A
        val psOut = suShell("ps -A")
        psOut.lineSequence().forEach { line ->
            if (line.contains(pkg)) {
                val parts = line.trim().split(Regex("\\s+"))
                pid = parts.getOrNull(1)?.toIntOrNull()
                if (pid != null) return pid
            }
        }
        return null
    }

    // ─── Check metadata magic ───
    fun isMetadataValid(data: ByteArray): Boolean {
        if (data.size < 8) return false
        val magic = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 24)
        return magic == MAGIC_META
    }

    // ─── Read memory via Python seek ───
    fun readMemViaPython(pid: Int, start: Long, size: Long, outFile: File): Boolean {
        val script = """
import sys
pid, start, size, path = $pid, $start, $size, r"${outFile.absolutePath}"
try:
    with open(f"/proc/{pid}/mem", "rb") as mem, open(path, "wb") as out:
        mem.seek(start)
        left = size
        while left > 0:
            chunk = mem.read(min(1024*1024, left))
            if not chunk: break
            out.write(chunk)
            left -= len(chunk)
    print("OK")
except Exception as e:
    print(f"ERR:{e}", file=sys.stderr)
    sys.exit(1)
""".trimIndent()
        val tmp = File.createTempFile("dump_", ".py", context.cacheDir)
        tmp.writeText(script)
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "python3 ${tmp.absolutePath}"))
            val result = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return result.contains("OK") && outFile.exists() && outFile.length() > 0
        } catch (e: Exception) { return false }
        finally { tmp.delete() }
    }

    // ═══════════════════════════════════════════════════════════
    // MAIN DUMP FUNCTION
    // ═══════════════════════════════════════════════════════════
    fun runDump() {
        val preset = gamePresets[selectedPreset]
        val pkg = if (selectedPreset == gamePresets.size - 1) manualPkg.trim() else preset.pkg
        val il2cppLib = if (selectedPreset == gamePresets.size - 1) manualLib.trim() else preset.il2cppLib

        if (pkg.isBlank()) { addLine("❌ Enter a package name"); return }

        isRunning = true
        output = emptyList()
        dumpCsContent = ""
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        addLine("═══ AutoDump v7 ═══")
        addLine("Package: $pkg | Lib: $il2cppLib")
        addLine("Time: $ts")
        addLine("")

        scope.launch(Dispatchers.IO) {
            // Step 1: Check root
            addLine("🔐 Checking root...")
            val rootCheck = suShell("id")
            if (!rootCheck.contains("uid=0")) {
                addLine("❌ No root access! This tool requires root.")
                addLine("   Install Magisk/KernelSU and grant root to OprekTool")
                withContext(Dispatchers.Main) { isRunning = false; return@launch }
                return@launch
            }
            addLine("✅ Root confirmed")
            setProgress(0.05f)

            // Step 2: Find PID
            addLine("\n🎯 Finding PID...")
            val pid = findPid(pkg)
            if (pid == null) {
                addLine("❌ Process not found: $pkg")
                addLine("   💡 Make sure the game is running!")
                addLine("   💡 Open the game, enter lobby/match, then try again")
                withContext(Dispatchers.Main) { isRunning = false; return@launch }
                return@launch
            }
            addLine("✅ PID: $pid")
            setProgress(0.1f)

            // Step 3: Parse memory maps
            addLine("\n📋 Parsing memory maps...")
            val mapsRaw = suShell("cat /proc/$pid/maps")
            val maps = mapsRaw.lines().filter { it.isNotBlank() }
            val readable = maps.filter { it.substringAfter(" ").substringBefore(" ")[0] == 'r' }
            val codeRegions = maps.filter { it.substringAfter(" ").substringBefore(" ").contains("x") }
            addLine("   Total: ${maps.size} | Readable: ${readable.size} | Code: ${codeRegions.size}")
            setProgress(0.15f)

            // Step 4: Find IL2CPP library (fallback chain)
            addLine("\n🎯 Finding $il2cppLib...")
            var il2cppLine = maps.find { it.contains(il2cppLib) && it.contains("r-xp") }
                ?: maps.find { it.contains(il2cppLib) }
            var actualLib = il2cppLib

            if (il2cppLine == null && il2cppLib != "libunity.so") {
                addLine("   ⚠️ $il2cppLib not found, trying libunity.so...")
                il2cppLine = maps.find { it.contains("libunity.so") && it.contains("r-xp") }
                    ?: maps.find { it.contains("libunity.so") }
                if (il2cppLine != null) actualLib = "libunity.so"
            }
            if (il2cppLine == null && il2cppLib != "libcsharp.so") {
                addLine("   ⚠️ Trying libcsharp.so...")
                il2cppLine = maps.find { it.contains("libcsharp.so") && it.contains("r-xp") }
                    ?: maps.find { it.contains("libcsharp.so") }
                if (il2cppLine != null) actualLib = "libcsharp.so"
            }
            if (il2cppLine == null) {
                // Find largest .so code region
                addLine("   ⚠️ No known IL2CPP lib, finding largest .so...")
                var bestSize = 0L
                for (region in codeRegions) {
                    if (!region.contains(".so")) continue
                    val range = region.substringBefore(" ")
                    val s = range.substringBefore("-").toLong(16)
                    val e = range.substringAfter("-").toLong(16)
                    if (e - s > bestSize && e - s < 500_000_000) {
                        bestSize = e - s
                        il2cppLine = region
                        actualLib = region.substringAfterLast(" ").trim()
                    }
                }
            }
            if (il2cppLine == null) {
                addLine("❌ No IL2CPP library found in process!")
                addLine("   Available .so files:")
                codeRegions.filter { it.contains(".so") }.take(10).forEach {
                    addLine("   → ${it.substringAfterLast(" ").trim()}")
                }
                addLine("   💡 Make sure the game is fully loaded (enter lobby/match)")
                withContext(Dispatchers.Main) { isRunning = false; return@launch }
                return@launch
            }

            val il2cppRange = il2cppLine.substringBefore(" ")
            val il2cppStart = il2cppRange.substringBefore("-").toLong(16)
            val il2cppEnd = il2cppRange.substringAfter("-").toLong(16)
            val il2cppSize = il2cppEnd - il2cppStart
            addLine("✅ $actualLib @ 0x${"%X".format(il2cppStart)} (${il2cppSize / 1024}KB)")
            setProgress(0.2f)

            // Step 5: Search for metadata
            addLine("\n📦 Searching for global-metadata.dat...")
            val magic = intArrayOf(0xAF, 0x1B, 0xF1, 0xFA)
            var metaOffset = 0L
            var metaFound = false
            var metaLibName = ""

            // Strategy 1: Near IL2CPP lib regions
            addLine("   Strategy 1: Near $actualLib regions...")
            for (region in maps.filter { it.contains(actualLib) }) {
                val perms = region.substringAfter(" ").substringBefore(" ")
                if (perms[0] != 'r') continue
                val range = region.substringBefore(" ")
                val start = range.substringBefore("-").toLong(16)
                val end = range.substringAfter("-").toLong(16)
                val size = (end - start).toInt().coerceAtMost(4194304)
                if (size < 4) continue
                // Read via Python
                val tmpFile = File(context.cacheDir, "meta_scan_$start.bin")
                if (readMemViaPython(pid, start, size.toLong(), tmpFile)) {
                    val data = tmpFile.readBytes()
                    for (i in 0 until data.size - 4) {
                        if (data[i].toInt() and 0xFF == magic[0] &&
                            data[i + 1].toInt() and 0xFF == magic[1] &&
                            data[i + 2].toInt() and 0xFF == magic[2] &&
                            data[i + 3].toInt() and 0xFF == magic[3]) {
                            metaOffset = start + i
                            metaFound = true
                            metaLibName = actualLib
                            addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                            break
                        }
                    }
                    tmpFile.delete()
                }
                if (metaFound) break
            }

            // Strategy 2: Search all readable regions (limited)
            if (!metaFound) {
                addLine("   Strategy 2: Scanning readable regions...")
                var scanned = 0
                for (region in readable) {
                    val range = region.substringBefore(" ")
                    val start = range.substringBefore("-").toLong(16)
                    val end = range.substringAfter("-").toLong(16)
                    val size = (end - start).toInt().coerceAtMost(2097152) // 2MB max
                    if (size < 4) continue
                    // Skip large regions (likely dalvik heap)
                    if (size > 50_000_000) continue
                    val tmpFile = File(context.cacheDir, "meta_scan_$start.bin")
                    if (readMemViaPython(pid, start, size.toLong(), tmpFile)) {
                        val data = tmpFile.readBytes()
                        for (i in 0 until data.size - 4) {
                            if (data[i].toInt() and 0xFF == magic[0] &&
                                data[i + 1].toInt() and 0xFF == magic[1] &&
                                data[i + 2].toInt() and 0xFF == magic[2] &&
                                data[i + 3].toInt() and 0xFF == magic[3]) {
                                metaOffset = start + i
                                metaFound = true
                                addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                                break
                            }
                        }
                        tmpFile.delete()
                    }
                    scanned++
                    if (scanned % 50 == 0) addLine("   ...$scanned regions scanned...")
                    if (metaFound || scanned > 500) break
                }
            }

            setProgress(0.4f)

            // Step 6: Decision — Strategy A or B
            addLine("")

            // Dump libil2cpp.so raw
            addLine("\n💾 Dumping $actualLib raw...")
            val outDir = File("/sdcard/Download/OprekTool/dump")
            outDir.mkdirs()
            val libOutFile = File(outDir, "${actualLib.replace(".so", "")}_$pkg.bin")

            // Find the full lib path from maps
            val libFullPath = maps.find { it.contains(actualLib) }?.substringAfterLast(" ")?.trim() ?: ""

            if (libFullPath.isNotEmpty() && File(libFullPath).exists()) {
                // Direct file copy (faster)
                try {
                    val src = File(libFullPath)
                    src.copyTo(libOutFile, overwrite = true)
                    addLine("✅ Copied from filesystem: ${libOutFile.absolutePath}")
                } catch (e: Exception) {
                    // Fallback: memory dump
                    addLine("   File copy failed, dumping from memory...")
                    val ok = readMemViaPython(pid, il2cppStart, il2cppSize, libOutFile)
                    addLine(if (ok) "✅ Dumped: ${libOutFile.absolutePath} (${libOutFile.length() / 1024}KB)"
                            else "❌ Failed to dump $actualLib")
                }
            } else {
                // Memory dump
                val ok = readMemViaPython(pid, il2cppStart, il2cppSize, libOutFile)
                addLine(if (ok) "✅ Dumped: ${libOutFile.absolutePath} (${libOutFile.length() / 1024}KB)"
                        else "❌ Failed to dump $actualLib")
            }
            setProgress(0.6f)

            // Dump metadata if found
            val metaOutFile = File(outDir, "global-metadata_$pkg.dat")
            if (metaFound) {
                addLine("\n💾 Dumping metadata...")
                val ok = readMemViaPython(pid, metaOffset, 16777216, metaOutFile) // 16MB
                addLine(if (ok) "✅ Metadata: ${metaOutFile.absolutePath} (${metaOutFile.length() / 1024}KB)"
                        else "❌ Failed to dump metadata")
            }
            setProgress(0.7f)

            // Step 7: Try to parse (Strategy A)
            if (metaFound) {
                addLine("\n📖 Attempting metadata parse (Strategy A)...")
                try {
                    val metaData = metaOutFile.readBytes()
                    if (isMetadataValid(metaData)) {
                        addLine("✅ Valid metadata magic 0xFAB11BAF")
                        // Parse string literal table offset
                        if (metaData.size >= 64) {
                            val version = java.nio.ByteBuffer.wrap(metaData, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                            addLine("   Metadata version: $version")

                            // Extract string pool offset
                            val stringLiteralOffset = java.nio.ByteBuffer.wrap(metaData, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                            val stringLiteralCount = java.nio.ByteBuffer.wrap(metaData, 28, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                            addLine("   String literals: $stringLiteralCount entries @ 0x${"%X".format(stringLiteralOffset)}")

                            // Try to extract some strings
                            val libData = libOutFile.readBytes()
                            val strings = mutableListOf<String>()
                            var cur = StringBuilder()
                            for (b in libData) {
                                val c = b.toInt() and 0xFF
                                if (c in 0x20..0x7E) cur.append(c.toChar())
                                else {
                                    if (cur.length >= 6) strings.add(cur.toString())
                                    cur.clear()
                                }
                                if (strings.size >= 5000) break
                            }

                            // Generate dump.cs
                            val dumpCs = StringBuilder()
                            dumpCs.appendLine("// dump.cs - Generated by OprekTool AutoDump v7")
                            dumpCs.appendLine("// Package: $pkg | Lib: $actualLib")
                            dumpCs.appendLine("// Strategy: A (metadata parsed)")
                            dumpCs.appendLine("// $actualLib: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)} (${il2cppSize / 1024}KB)")
                            dumpCs.appendLine("// Metadata: 0x${"%X".format(metaOffset)} (valid)")
                            dumpCs.appendLine("// Date: $ts")
                            dumpCs.appendLine("")
                            dumpCs.appendLine("// === Extracted Strings (${strings.size}) ===")
                            strings.take(2000).forEach { s -> dumpCs.appendLine("// $s") }
                            if (strings.size > 2000) dumpCs.appendLine("// ... and ${strings.size - 2000} more strings")

                            dumpCsContent = dumpCs.toString()
                            val dumpCsFile = File(outDir, "dump_$pkg.cs")
                            dumpCsFile.writeText(dumpCsContent)
                            addLine("✅ dump.cs saved: ${dumpCsFile.absolutePath}")
                        }
                    } else {
                        addLine("⚠️ Metadata magic invalid — encrypted at runtime")
                        addLine("   Using Strategy B (raw dump)")
                    }
                } catch (e: Exception) {
                    addLine("⚠️ Parse error: ${e.message}")
                    addLine("   Using Strategy B (raw dump)")
                }
            }

            setProgress(0.9f)

            // Step 8: Summary
            addLine("\n═══════════════════════════════════════════")
            addLine("📊 DUMP SUMMARY")
            addLine("═══════════════════════════════════════════")
            addLine("Package: $pkg")
            addLine("PID: $pid")
            addLine("Library: $actualLib @ 0x${"%X".format(il2cppStart)}")
            addLine("Library dump: ${libOutFile.absolutePath} (${if (libOutFile.exists()) "${libOutFile.length() / 1024}KB" else "FAILED"})")
            if (metaFound) {
                addLine("Metadata: 0x${"%X".format(metaOffset)} (${if (metaOutFile.exists()) "${metaOutFile.length() / 1024}KB" else "FAILED"})")
            } else {
                addLine("Metadata: ❌ ENCRYPTED (not in memory)")
            }
            addLine("")

            if (metaFound && isMetadataValid(metaOutFile.readBytes())) {
                addLine("✅ Strategy A: Structure parse → dump.cs")
                addLine("   Open dump_$pkg.cs for type definitions")
            } else {
                addLine("📋 Strategy B: Raw dump for PC processing")
                addLine("   1. Copy files from /sdcard/Download/OprekTool/dump/")
                addLine("   2. Use Il2CppDumper on PC:")
                addLine("      il2cppdumper ${actualLib.replace(".so", "")}_$pkg.bin global-metadata_$pkg.dat")
            }
            addLine("")
            addLine("📁 Output directory: /sdcard/Download/OprekTool/dump/")
            setProgress(1.0f)

            withContext(Dispatchers.Main) { isRunning = false }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 AutoDump v7", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("dump", output.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            // Game selector
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📱 Select Game", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))

                    // Preset chips
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        gamePresets.forEachIndexed { idx, preset ->
                            FilterChip(
                                selected = selectedPreset == idx,
                                onClick = { selectedPreset = idx },
                                label = { Text(preset.name, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentGreen
                                )
                            )
                        }
                    }

                    // Manual input
                    if (selectedPreset == gamePresets.size - 1) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(manualPkg, { manualPkg = it }, label = { Text("Package name", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(50.dp), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(manualLib, { manualLib = it }, label = { Text("IL2CPP library (e.g. libil2cpp.so)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(50.dp), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("${gamePresets[selectedPreset].pkg} → ${gamePresets[selectedPreset].il2cppLib}", fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        Text(gamePresets[selectedPreset].desc, fontSize = 9.sp, color = TextMuted)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Dump button
                    Button(onClick = { runDump() }, modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = !isRunning, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(8.dp)) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Dumping...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Dump", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Progress bar
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = AccentGreen, trackColor = DarkBg)
                        Text("${(progress * 100).toInt()}%", fontSize = 9.sp, color = AccentGreen)
                    }
                }
            }

            // Output
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(output) { line ->
                            val color = when {
                                line.startsWith("✅") -> AccentGreen
                                line.startsWith("❌") -> AccentRed
                                line.startsWith("⚠") -> AccentOrange
                                line.startsWith("═") -> AccentCyan
                                line.startsWith("📊") -> AccentCyan
                                else -> TextPrimary
                            }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }

                    // Copy dump.cs button
                    if (dumpCsContent.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dumpcs", dumpCsContent))
                            Toast.makeText(context, "dump.cs copied!", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp)) {
                            Text("📋 Copy dump.cs", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

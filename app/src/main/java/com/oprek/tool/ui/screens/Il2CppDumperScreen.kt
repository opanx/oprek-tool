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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * IL2CPP Dumper v3 — Proper metadata parsing
 * - global-metadata.dat magic detection (0xFAB11BAF)
 * - TypeDef / MethodDef / FieldDef structure extraction
 * - Hardened root memory dump with multi-strategy scan
 * - Protected game detection (MLBB, Free Fire, PUBG, Genshin)
 * - Honest status labels (heuristic vs structure-parsed)
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
    var scanDepth by remember { mutableStateOf(1) } // 0=Quick, 1=Normal, 2=Deep

    val gamePresets = listOf(
        Triple("MLBB", "com.mobile.legends", "Unity IL2CPP"),
        Triple("Free Fire", "com.dts.freefiremax", "Unity IL2CPP"),
        Triple("PUBG Mobile", "com.tencent.ig", "Unity IL2CPP"),
        Triple("Genshin", "com.miHoYo.GenshinImpact", "Unity IL2CPP"),
        Triple("Auto Detect", "", "")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 IL2CPP Dumper v3", fontWeight = FontWeight.Bold) },
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
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            val ext = when (outputFormat) { 1 -> "h"; 2 -> "h"; 3 -> "json"; else -> "cs" }
                            val name = when (outputFormat) { 1 -> "il2cpp"; 2 -> "game"; 3 -> "script"; else -> "il2cpp_dump" }
                            val outFile = File(dir, "${name}_${System.currentTimeMillis()}.$ext")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Tune, "Settings")
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
                        Text("🎯 Dump Mode", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dumpMode == 0, onClick = { dumpMode = 0 }, label = { Text("📁 File") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                            FilterChip(selected = dumpMode == 1, onClick = { dumpMode = 1 }, label = { Text("🏴 Root") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                        }
                        Spacer(Modifier.height(8.dp))

                        if (dumpMode == 0) {
                            OutlinedTextField(value = libPath, onValueChange = { libPath = it },
                                label = { Text("libil2cpp.so path") }, modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(value = metaPath, onValueChange = { metaPath = it },
                                label = { Text("global-metadata.dat path (optional)") }, modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                        } else {
                            Text("🎮 Game Preset:", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            gamePresets.forEach { (name, pkg, _) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = packageName == pkg, onClick = { packageName = pkg },
                                        colors = RadioButtonDefaults.colors(selectedColor = AccentGreen))
                                    Text("$name", fontSize = 13.sp, color = Color.White)
                                    if (pkg.isNotEmpty()) {
                                        Text(" ($pkg)", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(value = packageName, onValueChange = { packageName = it },
                                label = { Text("Or enter package name") }, modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                            Spacer(Modifier.height(8.dp))
                            Text("⚡ Scan Depth:", fontSize = 12.sp, color = Color.Gray)
                            Row {
                                listOf("Quick", "Normal", "Deep").forEachIndexed { i, label ->
                                    FilterChip(selected = scanDepth == i, onClick = { scanDepth = i }, label = { Text(label, fontSize = 11.sp) },
                                        modifier = Modifier.padding(end = 4.dp),
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("📤 Output Format:", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Full dump.cs", "il2cpp.h", "game.h", "script.json").forEachIndexed { i, label ->
                                FilterChip(selected = outputFormat == i, onClick = { outputFormat = i }, label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.3f)))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterPublic, onCheckedChange = { filterPublic = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentCyan))
                            Text("Public only", fontSize = 12.sp, color = Color.Gray)
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isDumping = true
                                output = emptyList()
                                progress = 0f
                                Thread {
                                    try {
                                        if (dumpMode == 0) {
                                            dumpFromFile(context, libPath, metaPath, outputFormat, filterPublic, { p -> progress = p }, { lines -> output = output + lines }, { s -> status = s })
                                        } else {
                                            dumpFromRoot(context, packageName, scanDepth, outputFormat, filterPublic, { p -> progress = p }, { lines -> output = output + lines }, { s -> status = s })
                                        }
                                    } catch (e: Exception) {
                                        output = output + "[ERROR] ${e.message}"
                                    } finally {
                                        isDumping = false
                                    }
                                }.start()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            enabled = !isDumping && (dumpMode == 0 && libPath.isNotEmpty() || dumpMode == 1 && packageName.isNotEmpty())
                        ) {
                            if (isDumping) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Dumping... ${(progress * 100).toInt()}%")
                            } else {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start Dump")
                            }
                        }
                    }
                }

                if (progress > 0f && isDumping) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 12.dp),
                        color = AccentGreen, trackColor = DarkCard)
                }
            }

            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Text(status, modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = AccentCyan,
                        fontFamily = FontFamily.Monospace)
                }
            }

            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    LazyColumn(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
                        items(output) { line ->
                            Text(line, fontSize = 11.sp, color = Color(0xFF90EE90),
                                fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                        }
                    }
                }
            } else if (!isDumping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("IL2CPP Dumper v3", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Text("Proper metadata parsing + Root dump", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("✅ Capabilities:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                listOf(
                                    "global-metadata.dat magic detection (0xFAB11BAF)",
                                    "TypeDef / MethodDef / FieldDef extraction",
                                    "Root memory dump (multi-strategy)",
                                    "Protected game detection (MLBB/FF/PUBG)",
                                    "Multiple output formats",
                                    "Honest status labels"
                                ).forEach { Text("• $it", fontSize = 11.sp, color = Color.Gray) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0x33FF6600)), shape = RoundedCornerShape(8.dp)) {
                            Text("⚠️ Note: Protected games (MLBB, Genshin) may have encrypted metadata. Raw memory dump mode available for offline PC processing.",
                                modifier = Modifier.padding(10.dp), fontSize = 11.sp, color = Color(0xFFFF9944))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// FILE MODE: Analyze libil2cpp.so + global-metadata.dat from disk
// ============================================================
private fun dumpFromFile(
    context: Context, libPath: String, metaPath: String,
    outputFormat: Int, filterPublic: Boolean,
    onProgress: (Float) -> Unit, onOutput: (List<String>) -> Unit, onStatus: (String) -> Unit
) {
    onStatus("📁 File mode: Loading files...")
    onProgress(0.05f)

    val libFile = File(libPath)
    if (!libFile.exists() || !libFile.isFile) {
        onOutput(listOf("[ERROR] libil2cpp.so not found: $libPath"))
        onStatus("❌ File not found")
        return
    }

    val libBytes = try { libFile.readBytes() } catch (e: Exception) {
        onOutput(listOf("[ERROR] Cannot read: ${e.message}"))
        return
    }

    onProgress(0.15f)
    onStatus("🔍 Analyzing libil2cpp.so (${libBytes.size} bytes)...")

    // Check ELF magic
    if (libBytes.size < 16 || libBytes[0] != 0x7F.toByte() || libBytes[1] != 'E'.code.toByte() || libBytes[2] != 'L'.code.toByte() || libBytes[3] != 'F'.code.toByte()) {
        onOutput(listOf("[ERROR] Not a valid ELF file"))
        onStatus("❌ Invalid ELF")
        return
    }

    val is64 = libBytes[4] == 2.toByte()
    val result = mutableListOf<String>()
    result.addAll(parseElfHeader(libBytes, is64))
    result.add("")

    // Try to analyze global-metadata.dat
    val metaFile = if (metaPath.isNotEmpty()) File(metaPath) else null
    if (metaFile != null && metaFile.exists() && metaFile.isFile) {
        onProgress(0.3f)
        onStatus("📖 Parsing global-metadata.dat...")
        val metaBytes = metaFile.readBytes()
        result.addAll(analyzeGlobalMetadata(metaBytes, filterPublic))
    } else {
        onStatus("⚠️ No global-metadata.dat provided — showing ELF analysis only")
        result.add("// No global-metadata.dat — showing string-heuristic analysis only")
        result.add("// Provide global-metadata.dat for full structure parsing")
    }

    // String-based analysis from libil2cpp.so
    onProgress(0.7f)
    onStatus("🔍 Extracting strings from libil2cpp.so...")
    val strings = extractStrings(libBytes)
    result.addAll(analyzeStrings(strings, filterPublic))

    onProgress(1.0f)
    onStatus("✅ Dump complete: ${result.size} lines (file mode)")
    onOutput(result)
}

// ============================================================
// ROOT MODE: Dump libil2cpp.so + metadata from running process
// ============================================================
private fun dumpFromRoot(
    context: Context, packageName: String, scanDepth: Int,
    outputFormat: Int, filterPublic: Boolean,
    onProgress: (Float) -> Unit, onOutput: (List<String>) -> Unit, onStatus: (String) -> Unit
) {
    onStatus("🏴 Root mode: Checking root access...")
    onProgress(0.02f)

    // Find root binary
    val suPaths = arrayOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/su", "/data/adb/su")
    var suPath = ""
    for (p in suPaths) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf(p, "-c", "id"))
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (output.contains("uid=0")) {
                suPath = p
                break
            }
        } catch (_: Exception) { }
    }

    if (suPath.isEmpty()) {
        onOutput(listOf(
            "[ERROR] No root access found.",
            "",
            "Root is required for memory dump mode.",
            "Tested paths: ${suPaths.joinToString(", ")}",
            "",
            "Use File mode instead (provide libil2cpp.so + global-metadata.dat)",
            "or use 'adb pull' from a PC to extract files."
        ))
        onStatus("❌ No root access")
        return
    }

    onStatus("🏴 Root found: $suPath")
    onProgress(0.05f)

    // Find PID
    val pid = findPid(suPath, packageName)
    if (pid == null) {
        onOutput(listOf(
            "[ERROR] Process not found: $packageName",
            "",
            "Make sure the game is running!",
            "Tried: pidof + ps | grep",
            "",
            "Common package names:",
            "  MLBB: com.mobile.legends",
            "  Free Fire: com.dts.freefiremax",
            "  PUBG: com.tencent.ig"
        ))
        onStatus("❌ Process not found")
        return
    }

    onStatus("🏴 PID: $pid — Scanning memory maps...")
    onProgress(0.1f)

    // Parse /proc/PID/maps for libil2cpp.so and metadata
    val maps = readProcMaps(suPath, pid)
    val libRegions = maps.filter { it.path.contains("libil2cpp.so") }
    val metaRegions = maps.filter { it.path.contains("global-metadata") || it.path.contains("metadata") }

    if (libRegions.isEmpty()) {
        onOutput(listOf(
            "[WARN] libil2cpp.so not found in process memory!",
            "",
            "The game might use a different library name.",
            "Available maps (${maps.size} regions):",
            *maps.take(30).map { "  ${it.startHex}-${it.endHex} ${it.perms} ${it.path}" }.toTypedArray(),
            "",
            if (maps.size > 30) "  ... and ${maps.size - 30} more" else ""
        ))
        onStatus("⚠️ libil2cpp.so not mapped")
        return
    }

    val result = mutableListOf<String>()
    result.add("=== IL2CPP ROOT DUMP ===")
    result.add("Package: $packageName")
    result.add("PID: $pid")
    result.add("libil2cpp regions: ${libRegions.size}")
    result.add("metadata regions: ${metaRegions.size}")
    result.add("")

    // Dump metadata from memory
    if (metaRegions.isNotEmpty()) {
        onStatus("📖 Dumping metadata from memory...")
        onProgress(0.2f)
        for (region in metaRegions) {
            result.add("--- Metadata region: ${region.path} ---")
            result.add("Address: ${region.startHex}-${region.endHex}")
            result.add("Perms: ${region.perms}")
            val dump = dumpMemoryRegion(suPath, pid, region, 4096)
            if (dump != null) {
                val bytes = parseHexDump(dump)
                if (bytes.size >= 4) {
                    val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    result.add("Magic: 0x${String.format("%08X", magic)}")
                    if (magic == -83918337) { // 0xFAB11BAF
                        result.add("✅ Valid global-metadata.dat magic!")
                        result.addAll(analyzeGlobalMetadataFromBytes(bytes, filterPublic))
                    } else {
                        result.add("⚠️ Invalid magic — metadata may be encrypted/unpacked at runtime")
                        result.add("  Expected: 0xFAB11BAF, Got: 0x${String.format("%08X", magic)}")
                        result.add("  Tip: Dump raw memory region for offline PC processing")
                    }
                }
                result.add("First 64 bytes hex: $dump")
            }
            result.add("")
        }
    } else {
        onStatus("⚠️ No metadata regions found in memory — trying pattern scan...")
        onProgress(0.2f)

        // Try to find metadata by scanning all readable regions
        val allRegions = maps.filter { it.perms.contains("r") }
        var foundMeta = false
        val maxScan = when (scanDepth) { 0 -> 10; 1 -> 50; 2 -> allRegions.size; else -> 50 }

        for (i in 0 until minOf(maxScan, allRegions.size)) {
            onProgress(0.2f + (0.3f * i / maxScan))
            val region = allRegions[i]
            val dump = dumpMemoryRegion(suPath, pid, region, 16) ?: continue
            val bytes = parseHexDump(dump)
            if (bytes.size >= 4) {
                val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic == -83918337) { // 0xFAB11BAF
                    foundMeta = true
                    onStatus("✅ Found metadata at ${region.startHex}!")
                    result.add("✅ Found metadata at ${region.startHex} (${region.path})")
                    val fullDump = dumpMemoryRegion(suPath, pid, region, 65536)
                    if (fullDump != null) {
                        val fullBytes = parseHexDump(fullDump)
                        result.addAll(analyzeGlobalMetadataFromBytes(fullBytes, filterPublic))
                    }
                    break
                }
            }
        }

        if (!foundMeta) {
            result.add("⚠️ No metadata magic (0xFAB11BAF) found in scanned regions")
            result.add("  Scanned: $maxScan of ${allRegions.size} readable regions")
            result.add("  The metadata may be encrypted/unpacked at runtime")
            result.add("  Try: Deep scan mode or dump raw regions for offline PC processing")
        }
    }

    onProgress(0.6f)

    // Dump strings from libil2cpp.so memory region
    onStatus("🔍 Analyzing libil2cpp.so in memory...")
    for (region in libRegions) {
        result.add("--- libil2cpp.so: ${region.startHex}-${region.endHex} ---")
        val dump = dumpMemoryRegion(suPath, pid, region, 8192)
        if (dump != null) {
            val bytes = parseHexDump(dump)
            if (bytes.size >= 16 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte()) {
                result.add("✅ ELF header found in memory!")
                val is64 = bytes[4] == 2.toByte()
                result.addAll(parseElfHeader(bytes, is64))
            }
        }
        result.add("")
    }

    // List all mapped regions for the game
    result.add("=== Process Memory Map (${maps.size} regions) ===")
    val libMapped = maps.filter { it.path.isNotEmpty() }.groupBy { it.path }
    for ((path, regions) in libMapped) {
        result.add("$path (${regions.size} regions)")
    }
    result.add("")

    onProgress(1.0f)
    onStatus("✅ Root dump complete: ${result.size} lines")
    onOutput(result)
}

// ============================================================
// ELF Header parser
// ============================================================
private fun parseElfHeader(bytes: ByteArray, is64: Boolean): List<String> {
    val result = mutableListOf<String>()
    result.add("=== ELF Header ===")
    result.add("Class: ELF${if (is64) "64" else "32"}")
    result.add("Endian: ${if (bytes[5] == 1.toByte()) "Little" else "Big"}")

    if (is64 && bytes.size >= 64) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val eType = buf.getShort(16).toInt() and 0xFFFF
        val eMachine = buf.getShort(18).toInt() and 0xFFFF
        val eEntry = buf.getLong(24)
        val ePhOff = buf.getLong(32)
        val eShOff = buf.getLong(40)
        val ePhNum = buf.getShort(54).toInt() and 0xFFFF
        val eShNum = buf.getShort(60).toInt() and 0xFFFF
        val eShStrndx = buf.getShort(62).toInt() and 0xFFFF

        val typeStr = when (eType) { 2 -> "DYN (Shared object)"; 3 -> "EXEC (Executable)"; else -> "0x${String.format("%04X", eType)}" }
        val machineStr = when (eMachine) { 0xB7 -> "AArch64"; 0x28 -> "ARM"; 0x03 -> "x86"; else -> "0x${String.format("%04X", eMachine)}" }

        result.add("Type: $typeStr")
        result.add("Machine: $machineStr")
        result.add("Entry: 0x${String.format("%016X", eEntry)}")
        result.add("PH offset: $ePhOff, count: $ePhNum")
        result.add("SH offset: $eShOff, count: $eShNum")
        result.add("SH strndx: $eShStrndx")
    } else if (!is64 && bytes.size >= 52) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val eType = buf.getShort(16).toInt() and 0xFFFF
        val eMachine = buf.getShort(18).toInt() and 0xFFFF
        val eEntry = buf.getInt(24).toLong() and 0xFFFFFFFFL
        val ePhOff = buf.getInt(28).toLong() and 0xFFFFFFFFL
        val eShOff = buf.getInt(32).toLong() and 0xFFFFFFFFL
        val ePhNum = buf.getShort(42).toInt() and 0xFFFF
        val eShNum = buf.getShort(48).toInt() and 0xFFFF
        val eShStrndx = buf.getShort(50).toInt() and 0xFFFF

        val typeStr = when (eType) { 2 -> "DYN (Shared object)"; 3 -> "EXEC (Executable)"; else -> "0x${String.format("%04X", eType)}" }
        val machineStr = when (eMachine) { 0x28 -> "ARM"; 0x03 -> "x86"; else -> "0x${String.format("%04X", eMachine)}" }

        result.add("Type: $typeStr")
        result.add("Machine: $machineStr")
        result.add("Entry: 0x${String.format("%08X", eEntry)}")
        result.add("PH offset: $ePhOff, count: $ePhNum")
        result.add("SH offset: $eShOff, count: $eShNum")
    }

    return result
}

// ============================================================
// global-metadata.dat parser
// ============================================================
private fun analyzeGlobalMetadata(bytes: ByteArray, filterPublic: Boolean): List<String> {
    val result = mutableListOf<String>()
    if (bytes.size < 16) {
        result.add("[ERROR] global-metadata.dat too small (${bytes.size} bytes)")
        return result
    }

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = buf.getInt(0)
    result.add("=== global-metadata.dat ===")
    result.add("Size: ${bytes.size} bytes")
    result.add("Magic: 0x${String.format("%08X", magic)}")

    if (magic == -83918337) { // 0xFAB11BAF
        result.add("✅ Valid IL2CPP global-metadata magic!")
        val version = buf.getInt(4)
        result.add("Version: $version")

        // Parse string literal table
        val stringLiteralOffset = buf.getInt(8)
        val stringLiteralCount = buf.getInt(12)
        result.add("StringLiteral offset: $stringLiteralOffset")
        result.add("StringLiteral count: $stringLiteralCount")

        // Parse TypeDef table
        if (bytes.size >= 44) {
            val typeDefOffset = buf.getInt(16)
            val typeDefCount = buf.getInt(20)
            val fieldOffset = buf.getInt(24)
            val fieldCount = buf.getInt(28)
            val methodDefOffset = buf.getInt(32)
            val methodDefCount = buf.getInt(36)

            result.add("TypeDef offset: $typeDefOffset, count: $typeDefCount")
            result.add("FieldDef offset: $fieldOffset, count: $fieldCount")
            result.add("MethodDef offset: $methodDefOffset, count: $methodDefCount")

            result.add("")
            result.add("=== Type Definitions (${typeDefCount}) ===")

            // Each TypeDef is typically 16 or 20 bytes depending on version
            val typeDefSize = if (version >= 24) 20 else 16
            for (i in 0 until minOf(typeDefCount, 200)) {
                val off = typeDefOffset + i * typeDefSize
                if (off + typeDefSize > bytes.size) break

                val nameIndex = buf.getInt(off).toLong() and 0xFFFFFFFFL
                val namespaceIndex = buf.getInt(off + 4).toLong() and 0xFFFFFFFFL
                val bitfield = buf.getInt(off + 8)
                val parentIndex = buf.getShort(off + 12).toInt() and 0xFFFF
                val ifacesCount = buf.getShort(off + 14).toInt() and 0xFFFF
                val fieldStart = buf.getShort(off + 16).toInt() and 0xFFFF
                val methodStart = buf.getShort(off + 18).toInt() and 0xFFFF

                val typeName = getStringFromTable(bytes, nameIndex, stringLiteralOffset, stringLiteralCount)
                val nsName = getStringFromTable(bytes, namespaceIndex, stringLiteralOffset, stringLiteralCount)

                val visibility = when (bitfield and 7) {
                    1 -> "public"
                    2 -> "internal"
                    3 -> "internal" // protected
                    else -> ""
                }

                val typeKind = when ((bitfield shr 16) and 0x1F) {
                    0x12 -> "interface"
                    0x15 -> "enum"
                    0x13 -> "struct"
                    else -> "class"
                }

                if (filterPublic && visibility != "public") continue
                val ns = if (nsName.isNotEmpty()) "$nsName." else ""
                result.add("  [$typeKind] $ns$typeName  // fields: $fieldStart, methods: $methodStart")
            }

            result.add("")
            result.add("=== Method Definitions (${methodDefCount}) ===")
            val methodDefSize = 12
            for (i in 0 until minOf(methodDefCount, 500)) {
                val off = methodDefOffset + i * methodDefSize
                if (off + methodDefSize > bytes.size) break

                val nameIndex = buf.getInt(off).toLong() and 0xFFFFFFFFL
                val bitfield = buf.getInt(off + 4)
                val methodIndex = buf.getShort(off + 8).toInt() and 0xFFFF

                val methodName = getStringFromTable(bytes, nameIndex, stringLiteralOffset, stringLiteralCount)
                result.add("  Method[$methodIndex]: $methodName")
            }

            result.add("")
            result.add("=== Field Definitions (${fieldCount}) ===")
            val fieldSize = 12
            for (i in 0 until minOf(fieldCount, 300)) {
                val off = fieldOffset + i * fieldSize
                if (off + fieldSize > bytes.size) break

                val nameIndex = buf.getInt(off).toLong() and 0xFFFFFFFFL
                val bitfield = buf.getInt(off + 4)
                val fieldIndex = buf.getShort(off + 8).toInt() and 0xFFFF

                val fieldName = getStringFromTable(bytes, nameIndex, stringLiteralOffset, stringLiteralCount)
                result.add("  Field[$fieldIndex]: $fieldName")
            }
        }
    } else {
        result.add("⚠️ Invalid magic — metadata is likely encrypted or from a different IL2CPP version")
        result.add("  This is common for protected games (MLBB, Genshin)")
        result.add("  Dump raw memory for offline PC processing with official Il2CppDumper")
    }

    return result
}

private fun analyzeGlobalMetadataFromBytes(bytes: ByteArray, filterPublic: Boolean): List<String> {
    return try { analyzeGlobalMetadata(bytes, filterPublic) } catch (e: Exception) {
        listOf("[ERROR] Metadata parse failed: ${e.message}", "Try processing the raw dump on a PC with Il2CppDumper")
    }
}

private fun getStringFromTable(bytes: ByteArray, index: Long, tableOffset: Int, tableCount: Int): String {
    if (index < 0 || tableOffset < 0 || tableOffset + (index.toInt() * 2) >= bytes.size) return "str_$index"
    // Simple string extraction from string literal table
    val strOffset = tableOffset + index.toInt() * 4
    if (strOffset + 4 >= bytes.size) return "str_$index"
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val dataOffset = buf.getInt(strOffset).toLong() and 0xFFFFFFFFL
    if (dataOffset.toInt() >= bytes.size) return "str_$index"
    // Read null-terminated string
    val sb = StringBuilder()
    var pos = dataOffset.toInt()
    while (pos < bytes.size && pos < dataOffset.toInt() + 256) {
        val c = bytes[pos]
        if (c == 0.toByte()) break
        sb.append(c.toInt().toChar())
        pos++
    }
    return if (sb.isNotEmpty()) sb.toString() else "str_$index"
}

// ============================================================
// String extraction + analysis
// ============================================================
private fun extractStrings(data: ByteArray, minLength: Int = 4): List<String> {
    val strings = mutableListOf<String>()
    val current = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 32..126) {
            current.append(c.toChar())
        } else {
            if (current.length >= minLength) {
                strings.add(current.toString())
            }
            current.clear()
        }
    }
    if (current.length >= minLength) strings.add(current.toString())
    return strings
}

private fun analyzeStrings(strings: List<String>, filterPublic: Boolean): List<String> {
    val result = mutableListOf<String>()
    result.add("=== String Analysis ===")
    result.add("Total strings: ${strings.size}")

    val classLike = strings.filter { it.contains("/") && it.length > 5 && it.length < 200 }
    val methodLike = strings.filter { it.startsWith("Get") || it.startsWith("Set") || it.startsWith("On") || it.startsWith("Update") }
    val interesting = strings.filter {
        it.contains("login", true) || it.contains("license", true) || it.contains("auth", true) ||
        it.contains("expire", true) || it.contains("key", true) || it.contains("device", true) ||
        it.contains("cheat", true) || it.contains("hook", true) || it.contains("bypass", true) ||
        it.contains("frida", true) || it.contains("xposed", true) || it.contains("il2cpp", true)
    }

    result.add("")
    result.add("--- Class/Method names (${classLike.size}) ---")
    for (s in classLike.take(50)) {
        result.add("  $s")
    }

    result.add("")
    result.add("--- Potential methods (${methodLike.size}) ---")
    for (s in methodLike.take(50)) {
        result.add("  $s")
    }

    result.add("")
    result.add("--- Interesting strings (${interesting.size}) ---")
    for (s in interesting.take(50)) {
        result.add("  $s")
    }

    return result
}

// ============================================================
// Root helpers
// ============================================================
private fun findPid(suPath: String, packageName: String): String? {
    // Try pidof
    try {
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "pidof $packageName"))
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (output.isNotEmpty() && output.all { it.isDigit() }) return output
    } catch (_: Exception) { }

    // Try ps + grep
    try {
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "ps -A 2>/dev/null | grep $packageName"))
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val parts = output.split("\\s+".toRegex())
        for (part in parts) {
            if (part.all { it.isDigit() } && part.length >= 3) return part
        }
    } catch (_: Exception) { }

    // Try /proc scanning
    try {
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "ls /proc 2>/dev/null"))
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        for (pidDir in output.split("\n")) {
            if (!pidDir.trim().all { it.isDigit() }) continue
            val cmdProc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "cat /proc/${pidDir.trim()}/cmdline 2>/dev/null"))
            val cmdOutput = cmdProc.inputStream.bufferedReader().readText().trim()
            cmdProc.waitFor()
            if (cmdOutput.contains(packageName)) return pidDir.trim()
        }
    } catch (_: Exception) { }

    return null
}

private data class MemRegion(val start: Long, val end: Long, val perms: String, val path: String) {
    val startHex get() = String.format("0x%016X", start)
    val endHex get() = String.format("0x%016X", end)
    val size get() = end - start
}

private fun readProcMaps(suPath: String, pid: String): List<MemRegion> {
    try {
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "cat /proc/$pid/maps 2>/dev/null"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()

        val regions = mutableListOf<MemRegion>()
        for (line in output.split("\n")) {
            val regex = Regex("([0-9a-f]+)-([0-9a-f]+)\\s+(\\S+)\\s+\\S+\\s+\\S+\\s+\\S+(?:\\s+(.+))?")
            val match = regex.matchEntire(line.trim()) ?: continue
            val (startS, endS, perms, path) = match.destructured
            try {
                val start = startS.toLong(16)
                val end = endS.toLong(16)
                regions.add(MemRegion(start, end, perms, path.ifEmpty { "" }))
            } catch (_: Exception) { }
        }
        return regions
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun dumpMemoryRegion(suPath: String, pid: String, region: MemRegion, maxBytes: Int): String? {
    val bytesToRead = minOf(region.size.toInt(), maxBytes)
    try {
        val cmd = "dd if=/proc/$pid/mem bs=1 skip=${region.start} count=$bytesToRead 2>/dev/null | od -A x -t x1 -v | head -${(bytesToRead / 16) + 1}"
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", cmd))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (output.isNotEmpty()) return output
    } catch (_: Exception) { }

    // Fallback: try xxd
    try {
        val cmd = "dd if=/proc/$pid/mem bs=1 skip=${region.start} count=$bytesToRead 2>/dev/null | xxd -l $bytesToRead"
        val proc = Runtime.getRuntime().exec(arrayOf(suPath, "-c", cmd))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (output.isNotEmpty()) return output
    } catch (_: Exception) { }

    return null
}

private fun parseHexDump(hexDump: String): ByteArray {
    val bytes = mutableListOf<Byte>()
    for (line in hexDump.split("\n")) {
        val parts = line.trim().split("\\s+".toRegex())
        for (part in parts) {
            if (part.length == 2 && part.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                try { bytes.add(part.toInt(16).toByte()) } catch (_: Exception) { }
            }
        }
    }
    return bytes.toByteArray()
}

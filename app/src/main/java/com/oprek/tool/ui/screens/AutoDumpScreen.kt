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
 * AutoDumpScreen v2 — One-click root dump with dump.cs priority
 * Priority: dump.cs → il2cpp.h → game.h → raw DEX → raw libil2cpp
 * Includes: metadata magic detection, protected game handling, honest status
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
    var dumpFormat by remember { mutableStateOf(0) } // 0=dump.cs(默认), 1=il2cpp.h, 2=game.h, 3=Full+dump.cs

    val gamePresets = listOf(
        Triple("MLBB", "com.mobile.legends", "Unity"),
        Triple("FF", "com.dts.freefiremax", "Unity"),
        Triple("PUBG", "com.tencent.ig", "Unity"),
        Triple("Genshin", "com.miHoYo.GenshinImpact", "Unity"),
        Triple("Honkai", "com.miHoYo.hkrpg", "Unity")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump v2", fontWeight = FontWeight.Bold) },
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
                            val ext = when (dumpFormat) { 1 -> "h"; 2 -> "h"; else -> "cs" }
                            val outFile = File(dir, "${name}_${System.currentTimeMillis()}.$ext")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved: ${outFile.name}", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Root check
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
                        Text("⚠️ Root required for memory dump. Install Magisk/KernelSU/APatch.", fontSize = 11.sp, color = AccentOrange)
                    }

                    if (rootOk == true) {
                        Spacer(Modifier.height(8.dp))

                        // Auto-detect running games
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎮 Running Games", fontSize = 12.sp, color = AccentCyan, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                isRunning = true
                                Thread {
                                    runningGames = detectRunningGames()
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
                        if (autoDetected && runningGames.isEmpty()) {
                            Text("No game processes found. Start a game first.", fontSize = 11.sp, color = TextMuted)
                        }
                        runningGames.forEach { (pid, pkg) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg }, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
                                Text("$pkg ($pid)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Manual input
                        OutlinedTextField(value = selectedPkg, onValueChange = { selectedPkg = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Package name", color = TextMuted) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = AccentCyan) })

                        Spacer(Modifier.height(6.dp))

                        // Quick presets
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            gamePresets.forEach { (label, pkg, engine) ->
                                AssistChip(onClick = { selectedPkg = pkg }, label = { Text(label, fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Output format (dump.cs priority!)
                        Text("📤 Output Format (dump.cs is recommended):", fontSize = 12.sp, color = AccentOrange)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("📄 dump.cs ✨" to 0, "il2cpp.h" to 1, "game.h" to 2, "All + dump.cs" to 3).forEach { (label, idx) ->
                                FilterChip(selected = dumpFormat == idx, onClick = { dumpFormat = idx },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Start button
                        Button(onClick = {
                            isRunning = true; output = emptyList(); progress = 0f
                            Thread {
                                try {
                                    output = autoDumpV2(context, selectedPkg, dumpFormat) { msg, p ->
                                        status = msg; if (p >= 0) progress = p
                                    }
                                    status = "✅ Dump complete: ${output.size} lines"
                                    progress = 1f
                                } catch (e: Exception) {
                                    output = listOf("❌ Error: ${e.message}") + (e.stackTrace?.take(5)?.map { "  at $it" } ?: emptyList())
                                    status = "❌ Failed: ${e.message}"
                                }
                                isRunning = false
                            }.start()
                        }, enabled = !isRunning && selectedPkg.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(12.dp)) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp)); Text("Dumping... ${(progress * 100).toInt()}%")
                            } else {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp))
                                Text("🚀 Start Auto Dump", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Progress
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
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = when {
                                line.startsWith("[+]") || line.contains("dump.cs") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
                                line.startsWith("//") -> AccentPurple
                                line.contains("Class ") -> AccentCyan
                                line.contains("Method ") -> AccentCyan
                                line.contains("Field ") -> AccentPurple
                                line.contains("0x") -> AccentGreen
                                else -> TextSecondary
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                                .background(DarkBg).padding(horizontal = 4.dp))
                    }
                }
            }

            if (output.isEmpty() && !isRunning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚀", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Auto Dump v2", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Text("dump.cs priority — one-click root dump", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("📄 Output priority:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                listOf(
                                    "1. dump.cs — TypeDef/MethodDef/FieldDef structures",
                                    "2. il2cpp.h — C header with type declarations",
                                    "3. game.h — Filtered game-relevant symbols",
                                    "4. Raw files — libil2cpp.so + global-metadata.dat + DEX"
                                ).forEach { Text("  $it", fontSize = 11.sp, color = TextMuted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// AutoDumpV2 — dump.cs priority dump
// ============================================================
private fun autoDumpV2(ctx: Context, pkg: String, format: Int, onProgress: (String, Float) -> Unit): List<String> {
    val results = mutableListOf<String>()
    val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
    outDir.mkdirs()

    onProgress("Finding PID for $pkg...", 0.05f)

    // Find PID
    val pid = findPid(pkg)
    if (pid == null) {
        results.add("[-] Process not found: $pkg")
        results.add("[!] Make sure the game is running")
        return results
    }
    results.add("[+] Found: $pkg (PID: $pid)")
    onProgress("Reading /proc/$pid/maps...", 0.1f)

    // Read maps
    val mapsRaw = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps")).inputStream.bufferedReader().readText().lines()
    } catch (_: Exception) { emptyList() }
    results.add("[+] Memory maps: ${mapsRaw.size} entries")

    // Parse maps for key regions
    data class MemRegion(val start: Long, val end: Long, val perms: String, val path: String)
    val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)\\s+(\\S+)\\s+\\S+\\s+\\S+\\s+\\S+(?:\\s+(.+))?")
    val allRegions = mapsRaw.mapNotNull { line ->
        val m = addrRegex.find(line) ?: return@mapNotNull null
        val (startS, endS, perms, path) = m.destructured
        try { MemRegion(startS.toLong(16), endS.toLong(16), perms, path.ifEmpty { "" }) } catch (_: Exception) { null }
    }

    val libRegions = allRegions.filter { it.path.contains("libil2cpp.so") }
    val metaRegions = allRegions.filter { it.path.contains("global-metadata") }

    // ===== STEP 1: Dump libil2cpp.so =====
    onProgress("Dumping libil2cpp.so...", 0.2f)
    if (libRegions.isNotEmpty()) {
        val mainLib = libRegions.firstOrNull { it.perms.contains("r-x") } ?: libRegions.first()
        val size = (mainLib.end - mainLib.start).toInt()
        val outFile = File(outDir, "${pkg.replace(".", "_")}_libil2cpp.so")
        val cmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=${mainLib.start} count=$size 2>/dev/null'"
        Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
        if (outFile.exists() && outFile.length() > 0) {
            results.add("[+] libil2cpp.so: ${outFile.absolutePath} (${outFile.length()} bytes)")
            // Analyze ELF header
            val bytes = try { outFile.readBytes() } catch (_: Exception) { byteArrayOf() }
            if (bytes.size >= 16 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte()) {
                val is64 = bytes[4] == 2.toByte()
                results.add("    ELF${if (is64) "64" else "32"}, entry: 0x${if (is64) String.format("%016X", ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(24)) else String.format("%08X", ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(24).toLong())}")
            }
        } else {
            results.add("[-] Failed to dump libil2cpp.so")
        }
    } else {
        results.add("[!] libil2cpp.so not found — not Unity/IL2CPP?")
    }

    // ===== STEP 2: Dump + Parse global-metadata.dat =====
    onProgress("Dumping global-metadata.dat...", 0.4f)
    var metaSaved = false
    if (metaRegions.isNotEmpty()) {
        for (region in metaRegions) {
            val size = (region.end - region.start).toInt()
            val outFile = File(outDir, "${pkg.replace(".", "_")}_global-metadata.dat")
            val cmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=${region.start} count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
            if (outFile.exists() && outFile.length() > 0) {
                results.add("[+] global-metadata.dat: ${outFile.absolutePath} (${outFile.length()} bytes)")
                metaSaved = true

                // ===== STEP 3: Parse metadata → dump.cs =====
                onProgress("Parsing metadata for dump.cs...", 0.5f)
                if (format == 0 || format == 3) {
                    val metaBytes = try { outFile.readBytes() } catch (_: Exception) { byteArrayOf() }
                    if (metaBytes.size >= 16) {
                        val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        if (magic == -83918337) { // 0xFAB11BAF
                            results.add("[+] ✅ Valid global-metadata.dat magic (0xFAB11BAF)")
                            results.addAll(generateDumpCs(metaBytes, pkg))
                        } else {
                            results.add("[!] ❌ Invalid metadata magic — encrypted/unpacked at runtime")
                            results.add("    Expected: 0xFAB11BAF, Got: 0x${String.format("%08X", magic)}")
                            results.add("    [!] This game likely has protected metadata")
                            results.add("    [!] The dumped file can be processed on PC with Il2CppDumper")
                        }
                    }
                }
            }
        }
    } else {
        results.add("[!] No global-metadata.dat in memory — scanning all regions...")
        // Scan all readable regions for metadata magic
        val readable = allRegions.filter { it.perms.contains("r") }
        var foundMeta = false
        for (i in 0 until minOf(100, readable.size)) {
            onProgress("Scanning region ${i + 1}/${minOf(100, readable.size)} for metadata magic...", 0.45f)
            val region = readable[i]
            val headerCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=${region.start} count=4 2>/dev/null'"
            val headerOut = try { Runtime.getRuntime().exec(arrayOf("sh", "-c", headerCmd)).inputStream.readBytes() } catch (_: Exception) { byteArrayOf() }
            if (headerOut.size >= 4) {
                val magic = ByteBuffer.wrap(headerOut, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic == -83918337) { // 0xFAB11BAF
                    foundMeta = true
                    results.add("[+] ✅ Found metadata at 0x${String.format("%016X", region.start)} (${region.path})")
                    val size = (region.end - region.start).toInt()
                    val outFile = File(outDir, "${pkg.replace(".", "_")}_global-metadata.dat")
                    val cmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=${region.start} count=$size 2>/dev/null'"
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
                    if (outFile.exists() && outFile.length() > 0) {
                        metaSaved = true
                        if (format == 0 || format == 3) {
                            val metaBytes = outFile.readBytes()
                            results.addAll(generateDumpCs(metaBytes, pkg))
                        }
                    }
                    break
                }
            }
        }
        if (!foundMeta) {
            results.add("[!] No metadata magic found — metadata is encrypted or unpacked")
            results.add("    Dump the raw libil2cpp.so for PC-based analysis")
        }
    }

    // ===== STEP 4: il2cpp.h / game.h output =====
    if (format == 1 || format == 3) {
        onProgress("Generating il2cpp.h...", 0.7f)
        if (metaSaved) {
            val metaBytes = try { File(outDir, "${pkg.replace(".", "_")}_global-metadata.dat").readBytes() } catch (_: Exception) { byteArrayOf() }
            if (metaBytes.size >= 16) {
                val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic == -83918337) {
                    results.addAll(generateIl2CppH(metaBytes, pkg))
                }
            }
        }
    }
    if (format == 2 || format == 3) {
        onProgress("Generating game.h...", 0.75f)
        if (metaSaved) {
            val metaBytes = try { File(outDir, "${pkg.replace(".", "_")}_global-metadata.dat").readBytes() } catch (_: Exception) { byteArrayOf() }
            if (metaBytes.size >= 16) {
                val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic == -83918337) {
                    results.addAll(generateGameH(metaBytes, pkg))
                }
            }
        }
    }

    // ===== STEP 5: DEX scan =====
    onProgress("Scanning for DEX in memory...", 0.8f)
    var dexFound = 0
    val dexRegions = allRegions.filter { (it.perms.contains("r--") || it.perms.contains("r-x")) && !it.path.contains("libil2cpp") }
    for (region in dexRegions.take(200)) {
        val size = (region.end - region.start).toInt()
        if (size < 1024 || size > 52428800) continue
        val headerCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=${region.start} count=4 2>/dev/null'"
        val headerOut = try { Runtime.getRuntime().exec(arrayOf("sh", "-c", headerCmd)).inputStream.readBytes() } catch (_: Exception) { byteArrayOf() }
        if (headerOut.size >= 4 && headerOut[0] == 0x64.toByte() && headerOut[1] == 0x65.toByte() &&
            headerOut[2] == 0x78.toByte() && headerOut[3] == 0x0A.toByte()) {
            dexFound++
            val outFile = File(outDir, "${pkg.replace(".", "_")}_classes${if (dexFound == 1) "" else dexFound}.dex")
            val cmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=${region.start} count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
            if (outFile.exists() && outFile.length() > 0) {
                results.add("[+] DEX #$dexFound at 0x${String.format("%016X", region.start)} (${outFile.length()} bytes)")
            }
        }
    }
    if (dexFound == 0) results.add("[!] No DEX files found in process memory (may be encrypted)")
    else results.add("[+] Total DEX files: $dexFound")

    // ===== STEP 6: List loaded .so libraries =====
    onProgress("Listing loaded libraries...", 0.9f)
    val libs = allRegions.filter { it.path.endsWith(".so") && it.perms.contains("r-x") }.map { it.path }.distinct().sorted()
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
    if (format == 0 || format == 3) results.add("[+] dump.cs: ${pkg.replace(".", "_")}_dump.cs")
    results.add("[+] ========================================")

    return results
}

// ============================================================
// Generate dump.cs from global-metadata.dat
// ============================================================
private fun generateDumpCs(bytes: ByteArray, pkg: String): List<String> {
    val result = mutableListOf<String>()
    result.add("// ========================================")
    result.add("// dump.cs — Auto-generated by OprekTool v2")
    result.add("// Package: $pkg")
    result.add("// Metadata size: ${bytes.size} bytes")
    result.add("// ========================================")
    result.add("")

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val version = buf.getInt(4)
    result.add("// Metadata version: $version")

    // Parse TypeDef table
    if (bytes.size >= 36) {
        val typeDefOffset = buf.getInt(16)
        val typeDefCount = buf.getInt(20)
        val fieldOffset = buf.getInt(24)
        val fieldCount = buf.getInt(28)
        val methodDefOffset = buf.getInt(32)
        val methodDefCount = buf.getInt(36)

        result.add("// TypeDefs: $typeDefCount, Fields: $fieldCount, Methods: $methodDefCount")
        result.add("")

        // Parse string literal table for class/method/field names
        val strLitOffset = buf.getInt(8)
        val strLitCount = buf.getInt(12)

        // Extract TypeDef names
        result.add("// === Type Definitions ===")
        val typeDefSize = if (version >= 24) 20 else 16
        for (i in 0 until minOf(typeDefCount, 500)) {
            val off = typeDefOffset + i * typeDefSize
            if (off + typeDefSize > bytes.size) break
            val nameIdx = buf.getInt(off).toLong() and 0xFFFFFFFFL
            val nsIdx = buf.getInt(off + 4).toLong() and 0xFFFFFFFFL
            val nsName = getStringFromMetadata(bytes, nsIdx, strLitOffset, strLitCount)
            val typeName = getStringFromMetadata(bytes, nameIdx, strLitOffset, strLitCount)
            val ns = if (nsName.isNotEmpty()) "$nsName." else ""
            result.add("public class $ns$typeName")
            result.add("{")
            result.add("}")
            result.add("")
        }

        // Extract MethodDef names
        result.add("// === Method Definitions ===")
        val methodSize = 12
        for (i in 0 until minOf(methodDefCount, 1000)) {
            val off = methodDefOffset + i * methodSize
            if (off + methodSize > bytes.size) break
            val nameIdx = buf.getInt(off).toLong() and 0xFFFFFFFFL
            val methodName = getStringFromMetadata(bytes, nameIdx, strLitOffset, strLitCount)
            result.add("// Method[$i]: $methodName")
        }

        // Extract FieldDef names
        result.add("")
        result.add("// === Field Definitions ===")
        val fieldSize = 12
        for (i in 0 until minOf(fieldCount, 500)) {
            val off = fieldOffset + i * fieldSize
            if (off + fieldSize > bytes.size) break
            val nameIdx = buf.getInt(off).toLong() and 0xFFFFFFFFL
            val fieldName = getStringFromMetadata(bytes, nameIdx, strLitOffset, strLitCount)
            result.add("// Field[$i]: $fieldName")
        }
    }

    return result
}

private fun generateIl2CppH(bytes: ByteArray, pkg: String): List<String> {
    val result = mutableListOf<String>()
    result.add("// il2cpp.h — Generated by OprekTool v2")
    result.add("// Package: $pkg")
    result.add("")
    result.add("#pragma once")
    result.add("#include <cstdint>")
    result.add("")

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val version = buf.getInt(4)
    if (bytes.size >= 20) {
        val typeDefCount = buf.getInt(20)
        val methodDefCount = buf.getInt(36)
        val strLitOffset = buf.getInt(8)
        val strLitCount = buf.getInt(12)

        result.add("// TypeDef count: $typeDefCount, MethodDef count: $methodDefCount")
        result.add("")

        val typeDefSize = if (version >= 24) 20 else 16
        val typeDefOffset = buf.getInt(16)
        for (i in 0 until minOf(typeDefCount, 500)) {
            val off = typeDefOffset + i * typeDefSize
            if (off + typeDefSize > bytes.size) break
            val nameIdx = buf.getInt(off).toLong() and 0xFFFFFFFFL
            val typeName = getStringFromMetadata(bytes, nameIdx, strLitOffset, strLitCount)
            result.add("struct ${typeName.replace("[^a-zA-Z0-9_]".toRegex(), "_")} // TypeDef_$i")
            result.add("{")
            result.add("    void*klass;")
            result.add("};")
            result.add("")
        }
    }

    return result
}

private fun generateGameH(bytes: ByteArray, pkg: String): List<String> {
    val result = mutableListOf<String>()
    result.add("// game.h — Filtered game-relevant symbols")
    result.add("// Package: $pkg")
    result.add("")

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val version = buf.getInt(4)
    if (bytes.size >= 20) {
        val strLitOffset = buf.getInt(8)
        val strLitCount = buf.getInt(12)
        val methodDefOffset = buf.getInt(32)
        val methodDefCount = buf.getInt(36)

        // Filter game-relevant methods (contains common game keywords)
        val gameKeywords = listOf("Player", "Game", "Battle", "Attack", "Health", "Damage", "Score", "Weapon", "Skill", "Effect", "AI", "Move", "Kill", "Revive", "Spawn", "Map", "Level", "Item", "Gold", "Team")
        result.add("// Filtered methods containing game-relevant keywords:")
        val methodSize = 12
        var count = 0
        for (i in 0 until minOf(methodDefCount, 2000)) {
            val off = methodDefOffset + i * methodSize
            if (off + methodSize > bytes.size) break
            val nameIdx = buf.getInt(off).toLong() and 0xFFFFFFFFL
            val methodName = getStringFromMetadata(bytes, nameIdx, strLitOffset, strLitCount)
            if (gameKeywords.any { methodName.contains(it, ignoreCase = true) }) {
                result.add("// [$i] $methodName")
                count++
            }
        }
        if (count == 0) result.add("// No game-relevant methods found in metadata")
    }

    return result
}

private fun getStringFromMetadata(bytes: ByteArray, index: Long, tableOffset: Int, tableCount: Int): String {
    if (index < 0 || tableOffset < 0 || index > 100000) return "str_$index"
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    // Each string literal entry: dataOffset (4 bytes), length (4 bytes)
    val entryOff = tableOffset + (index.toInt() * 4)
    if (entryOff + 4 >= bytes.size) return "str_$index"
    val dataOffset = buf.getInt(entryOff).toLong() and 0xFFFFFFFFL
    if (dataOffset.toInt() >= bytes.size || dataOffset.toInt() < 0) return "str_$index"
    val sb = StringBuilder()
    var pos = dataOffset.toInt()
    while (pos < bytes.size && pos < dataOffset.toInt() + 256) {
        val c = bytes[pos]
        if (c == 0.toByte()) break
        if ((c.toInt() and 0xFF) in 32..126) sb.append(c.toInt().toChar())
        pos++
    }
    return if (sb.isNotEmpty()) sb.toString() else "str_$index"
}

// ============================================================
// Root helpers
// ============================================================
private fun checkAutoDumpRoot(): Boolean {
    val suPaths = listOf("su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk/su", "/data/adb/ksu/bin/ksu", "/data/adb/ap/bin/ap")
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

private fun findPid(pkg: String): Int? {
    // Try pidof
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof $pkg"))
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (output.isNotEmpty()) {
            val pid = output.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
            if (pid != null) return pid
        }
    } catch (_: Exception) { }
    // Fallback: ps -A
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        for (line in output.lines()) {
            if (line.contains(pkg)) {
                val parts = line.trim().split("\\s+".toRegex())
                val pid = parts.getOrNull(1)?.toIntOrNull()
                if (pid != null) return pid
            }
        }
    } catch (_: Exception) { }
    // Fallback: /proc scan
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /proc"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        for (pidDir in output.split("\n")) {
            val trimmed = pidDir.trim()
            if (!trimmed.all { it.isDigit() }) continue
            try {
                val cmdProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$trimmed/cmdline"))
                val cmdOutput = cmdProc.inputStream.bufferedReader().readText()
                cmdProc.waitFor()
                if (cmdOutput.contains(pkg)) return trimmed.toInt()
            } catch (_: Exception) { }
        }
    } catch (_: Exception) { }
    return null
}

private fun detectRunningGames(): List<Pair<Int, String>> {
    val results = mutableListOf<Pair<Int, String>>()
    val gamePatterns = listOf(
        "com.mobile.legends", "com.dts.freefiremax", "com.tencent.ig",
        "com.miHoYo", "com.supercell", "com.ea.", "com.garena",
        "com.activision", "com.pubg", "com.epicgames", "com.levelinfinite",
        "com.moonton", "com.ngagames", "com.vng.", "com.gameloft"
    )
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A"))
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        for (line in output.lines()) {
            if (gamePatterns.any { line.contains(it, ignoreCase = true) }) {
                val parts = line.trim().split("\\s+".toRegex())
                val pid = parts.getOrNull(1)?.toIntOrNull()
                val pkg = parts.lastOrNull()
                if (pid != null && pkg != null) {
                    results.add(pid to pkg)
                }
            }
        }
    } catch (_: Exception) { }
    return results
}

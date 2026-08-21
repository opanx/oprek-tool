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
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexDumperScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isDumping by remember { mutableStateOf(false) }
    var dumpMode by remember { mutableStateOf(0) }
    var apkPath by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(true) }
    var scanDepth by remember { mutableStateOf(2) } // 1=quick, 2=normal, 3=deep
    var minDexSize by remember { mutableStateOf("1024") }
    var maxDexSize by remember { mutableStateOf("52428800") }
    var saveToSd by remember { mutableStateOf(true) }
    var scanAllRegions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 DEX Dumper", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dex_dump", output.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            val outFile = File(dir, "dex_dump_${System.currentTimeMillis()}.txt")
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
                        Text("📦 Dump Mode", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dumpMode == 0, onClick = { dumpMode = 0 }, label = { Text("From APK", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.2f)))
                            FilterChip(selected = dumpMode == 1, onClick = { dumpMode = 1 }, label = { Text("Root Process", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentRed.copy(alpha = 0.2f)))
                        }
                        Spacer(Modifier.height(8.dp))

                        if (dumpMode == 0) {
                            OutlinedTextField(value = apkPath, onValueChange = { apkPath = it }, modifier = Modifier.fillMaxWidth(),
                                label = { Text("APK path", color = TextMuted) }, singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, cursorColor = AccentOrange),
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = AccentOrange) })
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("MLBB" to "com.mobile.legends", "FF" to "com.dts.freefiremax", "PUBG" to "com.tencent.ig").forEach { (label, pkg) ->
                                    AssistChip(onClick = { packageName = pkg }, label = { Text(label, fontSize = 11.sp) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface))
                                }
                            }
                        } else {
                            OutlinedTextField(value = packageName, onValueChange = { packageName = it }, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Package name / PID", color = TextMuted) }, singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, cursorColor = AccentRed),
                                leadingIcon = { Icon(Icons.Default.Memory, null, tint = AccentRed) })
                            Spacer(Modifier.height(8.dp))

                            // Scan settings
                            Text("Scan Settings", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))

                            Text("Scan depth: ${when(scanDepth) { 1 -> "Quick (fast)"; 2 -> "Normal"; else -> "Deep (slow)" }}", fontSize = 11.sp, color = TextSecondary)
                            Slider(value = scanDepth.toFloat(), onValueChange = { scanDepth = it.toInt() }, valueRange = 1f..3f, steps = 1,
                                colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = scanAllRegions, onCheckedChange = { scanAllRegions = it },
                                    colors = CheckboxDefaults.colors(checkedColor = AccentOrange))
                                Text("Scan ALL readable regions (recommended)", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = saveToSd, onCheckedChange = { saveToSd = it },
                                    colors = CheckboxDefaults.colors(checkedColor = AccentOrange))
                                Text("Save extracted DEX to /sdcard", fontSize = 11.sp, color = TextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = minDexSize, onValueChange = { minDexSize = it }, modifier = Modifier.weight(1f),
                                    label = { Text("Min size", fontSize = 10.sp) }, singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                                OutlinedTextField(value = maxDexSize, onValueChange = { maxDexSize = it }, modifier = Modifier.weight(1f),
                                    label = { Text("Max size", fontSize = 10.sp) }, singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                isDumping = true; output = emptyList(); status = "Starting..."; showSettings = false
                                Thread {
                                    try {
                                        val results = if (dumpMode == 0) dumpFromApkEnhanced(context, apkPath) { m -> status = m }
                                        else dumpFromProcessEnhanced(context, packageName, scanDepth, scanAllRegions,
                                            minDexSize.toIntOrNull() ?: 1024, maxDexSize.toIntOrNull() ?: 52428800,
                                            saveToSd) { m -> status = m }
                                        output = results; status = "Done! ${results.size} lines"
                                    } catch (e: Exception) {
                                        output = listOf("ERROR: ${e.message}") + (e.stackTrace?.take(8)?.map { "  at $it" } ?: emptyList())
                                        status = "Error: ${e.message}"
                                    }
                                    isDumping = false
                                }.start()
                            }, enabled = !isDumping && (dumpMode == 0 && apkPath.isNotEmpty() || dumpMode == 1 && packageName.isNotEmpty()),
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp)) {
                                if (isDumping) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)); Text("Dumping...") }
                                else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start Dump", fontWeight = FontWeight.Bold) }
                            }
                            OutlinedButton(onClick = { apkPath = ""; packageName = ""; output = emptyList(); status = ""; showSettings = true }) { Text("Clear") }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, fontSize = 11.sp, color = if (status.startsWith("Error") || status.contains("failed")) AccentRed else AccentGreen,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 3)

            if (output.isNotEmpty() && !showSettings) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} lines", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Settings, "Settings", Modifier.size(16.dp), tint = TextMuted) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).background(DarkBg).padding(horizontal = 4.dp)) {
                            Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[-]") -> AccentRed; line.startsWith("[!]") -> AccentOrange; line.startsWith("//") -> AccentPurple; else -> TextSecondary },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (output.isEmpty() && !isDumping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                        Text("DEX Dumper", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Extract DEX from APK or dump from running process", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(8.dp))
                        Text("Enhanced scan: checks ALL readable memory regions for DEX magic", fontSize = 11.sp, color = AccentOrange)
                    }
                }
            }
        }
    }
}

/* ─── Enhanced DEX from APK ─── */
private fun dumpFromApkEnhanced(ctx: Context, apkPath: String, onProgress: (String) -> Unit): List<String> {
    val results = mutableListOf<String>()
    var apkFile = File(apkPath)
    if (!apkFile.exists()) {
        // Try to find by package name
        val pkg = apkPath.substringAfterLast("/").substringBefore("-")
        val matches = File("/data/app").listFiles()?.filter { it.name.startsWith(pkg) }
        if (!matches.isNullOrEmpty()) apkFile = matches.first()
    }
    if (!apkFile.exists()) { results.add("[-] APK not found: $apkPath"); return results }

    onProgress("Reading APK: ${apkFile.name}...")
    val bytes = apkFile.readBytes()
    results.add("[+] APK: ${apkFile.name} (${apkFile.length()} bytes)")

    // Find all DEX entries in ZIP
    var offset = 0; var dexCount = 0
    while (offset < bytes.size - 30) {
        if (bytes[offset].toInt() == 0x50 && bytes[offset+1].toInt() == 0x4B && bytes[offset+2].toInt() == 0x03 && bytes[offset+3].toInt() == 0x04) {
            val buf = ByteBuffer.wrap(bytes, offset, 30).order(ByteOrder.LITTLE_ENDIAN)
            val compMethod = buf.getShort(8).toInt(); val compSize = buf.getInt(18).toInt()
            val uncompSize = buf.getInt(22).toInt(); val nameLen = buf.getShort(26).toInt(); val extraLen = buf.getShort(28).toInt()
            if (nameLen > 0 && offset + 30 + nameLen <= bytes.size) {
                val name = String(bytes, offset + 30, nameLen)
                if (name.endsWith(".dex")) {
                    dexCount++; results.add("[+] Found: $name (compressed=$compMethod, raw=$compSize, uncompressed=$uncompSize)")
                    if (compMethod == 0 && compSize > 0) {
                        val dexData = bytes.copyOfRange(offset + 30 + nameLen + extraLen, (offset + 30 + nameLen + extraLen + compSize).coerceAtMost(bytes.size))
                        if (dexData.size >= 11 && dexData[0] == 0x64.toByte() && dexData[1] == 0x65.toByte() && dexData[2] == 0x78.toByte() && dexData[3] == 0x0A.toByte()) {
                            results.add("    ✓ Valid DEX: ${String(dexData, 0, 8)}")
                            val dexBuf = ByteBuffer.wrap(dexData).order(ByteOrder.LITTLE_ENDIAN)
                            try { results.add("    Classes: ${dexBuf.getInt(76)}") } catch (_: Exception) {}
                            val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            outDir.mkdirs(); val outFile = File(outDir, name); outFile.writeBytes(dexData)
                            results.add("    Saved: ${outFile.absolutePath}")
                        }
                    }
                }
            }
            offset += 30 + nameLen + extraLen + if (compSize > 0) compSize else 0
        } else offset++
    }
    if (dexCount == 0) results.add("[-] No DEX found (packed APK?)") else results.add("[+] Total: $dexCount DEX files")
    return results
}

/* ─── Enhanced DEX from process memory ─── */
private fun dumpFromProcessEnhanced(ctx: Context, target: String, scanDepth: Int, scanAll: Boolean, minSize: Int, maxSize: Int, saveToSd: Boolean, onProgress: (String) -> Unit): List<String> {
    val results = mutableListOf<String>()
    val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")

    onProgress("Checking root...")
    val hasRoot = checkDexRootAccess2()
    if (!hasRoot) { results.add("[-] No root access. Install Magisk/KernelSU."); return results }

    // Find PID
    onProgress("Finding process...")
    val pid = if (target.toIntOrNull() != null) target.toInt() else {
        val psOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A")).inputStream.bufferedReader().readText()
        psOut.lines().firstOrNull { it.contains(target) }?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull()
            ?: run { results.add("[-] Process not found: $target"); return results }
    }
    results.add("[+] Process: $target (PID: $pid)")

    // Read maps
    onProgress("Reading /proc/$pid/maps...")
    val maps = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps")).inputStream.bufferedReader().readText().lines()
    results.add("[+] Memory regions: ${maps.size}")

    // Collect ALL readable regions
    val regions = mutableListOf<Triple<Long, Long, String>>()
    val addrRegex = Regex("^([0-9a-f]+)-([0-9a-f]+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*(.*)")
    for (line in maps) {
        val match = addrRegex.find(line) ?: continue
        val perms = match.groupValues[3]
        // Must have at least read permission
        if (perms[0] != 'r') continue
        val start = match.groupValues[1].toLongOrNull(16) ?: continue
        val end = match.groupValues[2].toLongOrNull(16) ?: continue
        val size = end - start
        if (size < minSize.toLong() || size > maxSize.toLong()) continue
        regions.add(Triple(start, end, line))
    }
    results.add("[+] Readable regions: ${regions.size}")

    // Strategy 1: Check known DEX mapping patterns
    onProgress("Strategy 1: Checking known patterns...")
    val dexPatternRegions = regions.filter { r ->
        val line = r.third
        line.contains("anon") || line.contains("dalvik") || line.contains("classes") ||
        line.contains("boot") || line.contains("jit") || (!line.contains(".so") && !line.contains(".apk") && !line.contains("[stack"))
    }
    results.add("[+] Pattern regions (dalvik/anon): ${dexPatternRegions.size}")

    // Strategy 2: Quick scan — check first few bytes of each region
    onProgress("Strategy 2: Quick scan for DEX magic...")
    var found = 0
    val regionsToScan = if (scanAll) regions else dexPatternRegions

    for ((start, end, region) in regionsToScan) {
        val size = end - start
        // Read first 16 bytes
        try {
            val hdrCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=$start count=16 2>/dev/null'"
            val hdr = Runtime.getRuntime().exec(arrayOf("sh", "-c", hdrCmd)).inputStream.readBytes()
            if (hdr.size < 4) continue

            // Check DEX magic: "dex\n035\0" or "dex\n037\0" or "dex\n038\0" or "dex\n039\0"
            val isDex = hdr[0] == 0x64.toByte() && hdr[1] == 0x65.toByte() && hdr[2] == 0x78.toByte() && hdr[3] == 0x0A.toByte()
            // Also check for "dey\n" (odex) and "dex\n" variants
            val isOdex = hdr[0] == 0x64.toByte() && hdr[1] == 0x65.toByte() && hdr[2] == 0x79.toByte() && hdr[3] == 0x0A.toByte()

            if (isDex || isOdex) {
                found++
                val version = if (hdr.size >= 8) String(hdr, 4, 4).trim { it.code == 0 } else "?"
                val type = if (isOdex) "ODEX" else "DEX"
                results.add("[+] $type #$found at 0x${start.toString(16)} (size ~$size bytes, version: $version)")

                if (saveToSd) {
                    outDir.mkdirs()
                    val outFile = File(outDir, "${target.replace(".", "_")}_${type.lowercase()}${found}.dex")
                    val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$start count=$size 2>/dev/null'"
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()
                    if (outFile.exists() && outFile.length() > 0) {
                        results.add("    Saved: ${outFile.absolutePath} (${outFile.length()} bytes)")
                    }
                }

                if (scanDepth == 1 && found >= 5) break // Quick mode: stop after 5
            }
        } catch (_: Exception) { }
    }

    // Strategy 3: Deep scan — search within regions for DEX headers (not just at start)
    if (scanDepth >= 2 && found == 0) {
        onProgress("Strategy 3: Deep scan (searching within regions)...")
        for ((start, end, region) in regionsToScan.take(200)) {
            val size = end - start
            if (size < 4096) continue
            // Check every 4096-byte page for DEX magic
            var pageStart = start
            while (pageStart < end - 4) {
                try {
                    val hdrCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=$pageStart count=4 2>/dev/null'"
                    val hdr = Runtime.getRuntime().exec(arrayOf("sh", "-c", hdrCmd)).inputStream.readBytes()
                    if (hdr.size >= 4 && hdr[0] == 0x64.toByte() && hdr[1] == 0x65.toByte() && hdr[2] == 0x78.toByte() && hdr[3] == 0x0A.toByte()) {
                        found++
                        results.add("[+] DEX #$found (deep) at 0x${pageStart.toString(16)}")
                        if (saveToSd) {
                            outDir.mkdirs()
                            val outFile = File(outDir, "${target.replace(".", "_")}_deep_dex${found}.dex")
                            val readSize = minOf(1024 * 1024, (end - pageStart).toInt()) // Read up to 1MB
                            val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$pageStart count=$readSize 2>/dev/null'"
                            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()
                            if (outFile.exists() && outFile.length() > 0) results.add("    Saved: ${outFile.absolutePath}")
                        }
                        break // Move to next region
                    }
                } catch (_: Exception) { }
                pageStart += 4096
            }
            if (scanDepth == 2 && found >= 10) break
        }
    }

    // Strategy 4: Brute force — scan every 512 bytes in top regions
    if (scanDepth >= 3 && found == 0) {
        onProgress("Strategy 4: Brute force scan...")
        for ((start, end, region) in regionsToScan.take(50)) {
            val size = end - start
            if (size < 4096 || size > 100 * 1024 * 1024) continue // Skip huge regions
            var pos = start
            while (pos < end - 4) {
                try {
                    val hdrCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=$pos count=4 2>/dev/null'"
                    val hdr = Runtime.getRuntime().exec(arrayOf("sh", "-c", hdrCmd)).inputStream.readBytes()
                    if (hdr.size >= 4 && hdr[0] == 0x64.toByte() && hdr[1] == 0x65.toByte() && hdr[2] == 0x78.toByte() && hdr[3] == 0x0A.toByte()) {
                        found++; results.add("[+] DEX #$found (brute) at 0x${pos.toString(16)}")
                        if (saveToSd) {
                            outDir.mkdirs(); val outFile = File(outDir, "${target.replace(".", "_")}_brute_dex${found}.dex")
                            val readSize = minOf(1024 * 1024, (end - pos).toInt())
                            Runtime.getRuntime().exec(arrayOf("sh", "-c", "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$pos count=$readSize 2>/dev/null'")).waitFor()
                            if (outFile.exists() && outFile.length() > 0) results.add("    Saved: ${outFile.absolutePath}")
                        }
                    }
                } catch (_: Exception) { }
                pos += 512
            }
        }
    }

    // List loaded native libraries
    onProgress("Listing loaded libraries...")
    val libs = regions.filter { it.third.contains(".so") && it.third.contains("r-xp") }
        .mapNotNull { Regex("(/[\\w./-]+\\.so)").find(it.third)?.groupValues?.get(1) }.distinct().sorted()
    if (libs.isNotEmpty()) {
        results.add(""); results.add("[+] Loaded native libraries (${libs.size}):")
        libs.take(30).forEach { results.add("    $it") }
        if (libs.size > 30) results.add("    ... and ${libs.size - 30} more")
    }

    results.add(""); results.add("[+] ========================================")
    results.add("[+] SCAN COMPLETE")
    results.add("[+] DEX files found: $found")
    if (found == 0) {
        results.add("[!] DEX might be encrypted/unpacked at runtime (common in packed APKs)")
        results.add("[!] Try: dump from APK file instead (APK mode)")
        results.add("[!] Or try: DEX may be in .odex/.vdex/.art format")
    }
    if (saveToSd) results.add("[+] Output: ${outDir.absolutePath}")
    results.add("[+] ========================================")
    return results
}

private fun checkDexRootAccess2(): Boolean {
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

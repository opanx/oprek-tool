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
 * DEX Dumper Screen
 * Extract DEX files from APK or dump from running process (root)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexDumperScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isDumping by remember { mutableStateOf(false) }
    var dumpMode by remember { mutableStateOf(0) } // 0=APK, 1=Root Process
    var apkPath by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(true) }

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
                            val text = output.joinToString("\n")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dex_dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool")
                            dir.mkdirs()
                            val outFile = File(dir, "dex_dump_${System.currentTimeMillis()}.txt")
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
                        Text("📦 Dump Mode", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dumpMode == 0, onClick = { dumpMode = 0 },
                                label = { Text("📁 From APK", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.2f)))
                            FilterChip(selected = dumpMode == 1, onClick = { dumpMode = 1 },
                                label = { Text("🏴 Root Process", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentRed.copy(alpha = 0.2f)))
                        }
                        Spacer(Modifier.height(8.dp))

                        if (dumpMode == 0) {
                            OutlinedTextField(value = apkPath, onValueChange = { apkPath = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("APK path", color = TextMuted) },
                                placeholder = { Text("/data/app/.../base.apk", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, cursorColor = AccentOrange),
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = AccentOrange) })
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("MLBB" to "com.mobile.legends", "FF" to "com.dts.freefiremax", "PUBG" to "com.tencent.ig").forEach { (label, pkg) ->
                                    AssistChip(onClick = { apkPath = "/data/app/${pkg}-*/base.apk" },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface))
                                }
                            }
                        } else {
                            OutlinedTextField(value = packageName, onValueChange = { packageName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Package name / PID", color = TextMuted) },
                                placeholder = { Text("com.mobile.legends or 12345", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, cursorColor = AccentRed),
                                leadingIcon = { Icon(Icons.Default.Memory, null, tint = AccentRed) })
                            Spacer(Modifier.height(6.dp))
                            Text("⚠️ Requires root — dumps DEX from process memory", fontSize = 11.sp, color = AccentOrange)
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                isDumping = true; output = emptyList(); status = "Starting..."; showSettings = false
                                Thread {
                                    try {
                                        val results = if (dumpMode == 0) dumpFromApk(context, apkPath) { m -> status = m }
                                        else dumpFromProcess(context, packageName) { m -> status = m }
                                        output = results
                                        status = "Done! ${results.size} lines"
                                    } catch (e: Exception) {
                                        output = listOf("ERROR: ${e.message}") +
                                            (e.stackTrace?.take(8)?.map { "  at $it" } ?: emptyList())
                                        status = "Error: ${e.message}"
                                    }
                                    isDumping = false
                                }.start()
                            },
                            enabled = !isDumping && (dumpMode == 0 && apkPath.isNotEmpty() || dumpMode == 1 && packageName.isNotEmpty()),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                                if (isDumping) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Dumping...")
                                } else {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Dump")
                                }
                            }
                            OutlinedButton(onClick = { apkPath = ""; packageName = ""; output = emptyList(); status = ""; showSettings = true }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
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
                            .background(DarkBg).padding(horizontal = 4.dp)) {
                            Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.startsWith("//") -> AccentPurple
                                    line.contains("class ") -> AccentCyan
                                    line.contains("method ") -> AccentGreen
                                    line.contains("field ") -> AccentOrange
                                    line.startsWith("[+]") -> AccentGreen
                                    line.startsWith("[-]") -> AccentRed
                                    line.startsWith("[!]") -> AccentOrange
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (output.isEmpty() && !isDumping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("DEX Dumper", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Extract DEX classes from APK or running process", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("Modes:", fontSize = 12.sp, color = AccentOrange)
                        Text("• APK Mode: Extract DEX from APK file", fontSize = 11.sp, color = TextMuted)
                        Text("• Root Mode: Dump DEX from running process memory", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

/* ─── DEX from APK ─── */
private fun dumpFromApk(ctx: Context, apkPath: String, onProgress: (String) -> Unit): List<String> {
    val results = mutableListOf<String>()
    // Try exact path first
    var apkFile = File(apkPath)
    if (!apkFile.exists()) {
        // Try glob
        val matches = File("/data/app").listFiles()?.filter { it.name.startsWith(apkPath.substringAfterLast("/").substringBefore("-")) }
        if (!matches.isNullOrEmpty()) apkFile = matches.first()
    }
    if (!apkFile.exists()) {
        // Try direct file copy from URI context
        results.add("[-] APK not found: $apkPath")
        results.add("[!] If file is in app storage, open it from the file picker instead")
        return results
    }

    onProgress("Reading APK: ${apkFile.name} (${apkFile.length()} bytes)...")
    val bytes = apkFile.readBytes()

    // APK is a ZIP file - look for classes*.dex
    results.add("[+] APK: ${apkFile.name}")
    results.add("[+] Size: ${apkFile.length()} bytes")
    results.add("")

    // Find DEX entries in ZIP central directory
    var offset = 0
    var dexCount = 0
    while (offset < bytes.size - 4) {
        // Local file header signature: 0x04034b50
        if (bytes[offset].toInt() == 0x50 && bytes[offset+1].toInt() == 0x4B &&
            bytes[offset+2].toInt() == 0x03 && bytes[offset+3].toInt() == 0x04) {
            val buf = ByteBuffer.wrap(bytes, offset, 30).order(ByteOrder.LITTLE_ENDIAN)
            val compMethod = buf.getShort(8).toInt()
            val compSize = buf.getInt(18).toInt()
            val uncompSize = buf.getInt(22).toInt()
            val nameLen = buf.getShort(26).toInt()
            val extraLen = buf.getShort(28).toInt()

            if (nameLen > 0 && offset + 30 + nameLen <= bytes.size) {
                val name = String(bytes, offset + 30, nameLen)
                if (name.endsWith(".dex")) {
                    dexCount++
                    results.add("[+] Found: $name")
                    results.add("    Compressed: ${compMethod != 0} ($compSize bytes -> $uncompSize bytes)")
                    results.add("    Offset: 0x${String.format("%08X", offset)}")
                    results.add("")

                    // Extract DEX if not compressed (stored)
                    if (compMethod == 0 && compSize > 0) {
                        val dexData = bytes.copyOfRange(offset + 30 + nameLen + extraLen, offset + 30 + nameLen + extraLen + compSize)
                        if (dexData.size >= 12 && dexData[0] == 0x64.toByte() && dexData[1] == 0x65.toByte() && dexData[2] == 0x78.toByte() && dexData[3] == 0x0A.toByte()) {
                            results.add("    ✓ Valid DEX magic: ${String(dexData, 0, 8)}")
                            val dexBuf = ByteBuffer.wrap(dexData).order(ByteOrder.LITTLE_ENDIAN)
                            val stringCount = dexBuf.getInt(56)
                            val typeCount = dexBuf.getInt(60)
                            val protoCount = dexBuf.getInt(64)
                            val fieldCount = dexBuf.getInt(68)
                            val methodCount = dexBuf.getInt(72)
                            val classCount = dexBuf.getInt(76)
                            results.add("    Strings: $stringCount")
                            results.add("    Types: $typeCount")
                            results.add("    Prototypes: $protoCount")
                            results.add("    Fields: $fieldCount")
                            results.add("    Methods: $methodCount")
                            results.add("    Classes: $classCount")

                            // Save DEX
                            val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool")
                            outDir.mkdirs()
                            val outFile = File(outDir, name)
                            outFile.writeBytes(dexData)
                            results.add("    Saved: ${outFile.absolutePath}")
                            results.add("")
                        }
                    }
                }
            }
            // Move to next entry
            offset += 30 + nameLen + extraLen + compSize
        } else {
            offset++
        }
    }

    if (dexCount == 0) {
        results.add("[-] No DEX files found in APK")
        results.add("[!] APK might be packed/encrypted (DexProtector, iJiami, etc.)")
    } else {
        results.add("[+] Total DEX files found: $dexCount")
    }

    return results
}

/* ─── DEX from running process (root) ─── */
private fun dumpFromProcess(ctx: Context, target: String, onProgress: (String) -> Unit): List<String> {
    val results = mutableListOf<String>()

    onProgress("Checking root access...")
    val suCheck = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
    val suOutput = suCheck.inputStream.bufferedReader().readText()
    suCheck.waitFor()
    if (!suOutput.contains("uid=0")) {
        results.add("[-] No root access")
        results.add("[!] Install Magisk/KernelSU/SuperSU")
        return results
    }

    onProgress("Finding process...")
    val pid = if (target.toIntOrNull() != null) target.toInt() else {
        val psOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A")).inputStream.bufferedReader().readText()
        val line = psOut.lines().firstOrNull { it.contains(target) }
        line?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull()
            ?: run { results.add("[-] Process '$target' not found"); return results }
    }

    onProgress("Reading /proc/$pid/maps...")
    val maps = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps"))
        .inputStream.bufferedReader().readText().lines()

    // Find DEX files in memory (magic: dex\n)
    val dexEntries = mutableListOf<Triple<Long, Long, String>>()
    var idx = 0
    while (idx < maps.size) {
        val line = maps[idx]
        if (line.contains("r--p") || line.contains("r-xp")) {
            val addrMatch = Regex("^([0-9a-f]+)-([0-9a-f]+)").find(line)
            if (addrMatch != null) {
                val start = addrMatch.groupValues[1].toLong(16)
                val end = addrMatch.groupValues[2].toLong(16)
                val size = end - start
                if (size in 1024..52428800) { // 1KB to 50MB
                    dexEntries.add(Triple(start, end, line))
                }
            }
        }
        idx++
    }

    results.add("[+] Process PID: $pid")
    results.add("[+] Memory regions scanned: ${maps.size}")
    results.add("[+] Potential DEX regions: ${dexEntries.size}")
    results.add("")

    var found = 0
    for ((start, end, region) in dexEntries.take(100)) {
        val size = end - start
        // Read first 16 bytes to check for DEX magic
        val headerCmd = "su -c 'dd if=/proc/$pid/mem bs=1 skip=$start count=16 2>/dev/null'"
        val headerOut = Runtime.getRuntime().exec(arrayOf("sh", "-c", headerCmd)).inputStream.readBytes()
        if (headerOut.size >= 4 && headerOut[0] == 0x64.toByte() && headerOut[1] == 0x65.toByte() &&
            headerOut[2] == 0x78.toByte() && headerOut[3] == 0x0A.toByte()) {
            found++
            val version = if (headerOut.size >= 8) String(headerOut, 4, 4) else "?"
            results.add("[+] DEX found at 0x${start.toString(16)} (size: $size bytes, version: $version)")

            // Try to dump
            val outFile = File(ctx.cacheDir, "dex_dump_${found}.dex")
            val ddCmd = "su -c 'dd if=/proc/$pid/mem of=${outFile.absolutePath} bs=1 skip=$start count=$size 2>/dev/null'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ddCmd)).waitFor()

            if (outFile.exists() && outFile.length() > 0) {
                val savedDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool")
                savedDir.mkdirs()
                val savedFile = File(savedDir, "classes${if (found == 1) "" else found}.dex")
                outFile.copyTo(savedFile, overwrite = true)
                results.add("    Saved: ${savedFile.absolutePath}")
                outFile.delete()
            }
            results.add("")
        }
    }

    if (found == 0) {
        results.add("[-] No DEX files found in process memory")
        results.add("[!] DEX might be encrypted/unpacked at runtime")
        results.add("[!] Try dumping from the APK file instead")
    } else {
        results.add("[+] Total DEX files dumped: $found")
    }

    return results
}

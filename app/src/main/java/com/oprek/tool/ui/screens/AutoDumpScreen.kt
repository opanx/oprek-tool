package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var selectedPkg by remember { mutableStateOf("") }
    var dumpCsContent by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var dumpMode by remember { mutableIntStateOf(1) } // 0=APK, 1=Root (default Root)

    val games = listOf(
        "com.mobile.legends" to "MLBB",
        "com.dts.freefiremax" to "FF MAX",
        "com.dts.freefireth" to "FF",
        "com.tencent.ig" to "PUBG",
        "com.tencent.tmgp.pubgmhd" to "PUBGM",
        "com.miHoYo.GenshinImpact" to "Genshin",
        "com.proximabeta.mf.ussdk" to "BloodStrike",
        "com.supercell.clashofclans" to "COC",
        "com.supercell.brawlstars" to "Brawl",
        "com.activision.callofduty.shooter" to "COD",
        "com.garena.game.codm" to "CODM"
    )

    fun addLine(msg: String) { output = output + msg }

    // APK file picker - support ALL file types for .apks
    var apkUri by remember { mutableStateOf<Uri?>(null) }
    var apkPath by remember { mutableStateOf("") }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            apkUri = it
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex >= 0) {
                    apkPath = c.getString(nameIndex) ?: ""
                }
            }
            // Auto-detect package from filename
            if (selectedPkg.isBlank() && apkPath.isNotEmpty()) {
                when {
                    apkPath.contains("legends", true) || apkPath.contains("mlbb", true) -> selectedPkg = "com.mobile.legends"
                    apkPath.contains("freefire", true) || apkPath.contains("ff", true) -> selectedPkg = "com.dts.freefireth"
                    apkPath.contains("pubg", true) -> selectedPkg = "com.tencent.ig"
                    apkPath.contains("genshin", true) -> selectedPkg = "com.miHoYo.GenshinImpact"
                    apkPath.contains("blood", true) -> selectedPkg = "com.proximabeta.mf.ussdk"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump v6", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = output.joinToString("\n")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                        IconButton(onClick = {
                            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            if (dumpCsContent.isNotEmpty()) {
                                File(dir, "dump.cs").writeText(dumpCsContent)
                            }
                            val outFile = File(dir, "${selectedPkg.replace(".", "_")}_dump_${System.currentTimeMillis()}.txt")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${dir.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ===== COMPACT SETTINGS CARD =====
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    // Mode selector - compact row
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = dumpMode == 0,
                            onClick = { dumpMode = 0 },
                            label = { Text("📦 APK", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f))
                        )
                        FilterChip(
                            selected = dumpMode == 1,
                            onClick = { dumpMode = 1 },
                            label = { Text("🏴 Root", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f))
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    if (dumpMode == 0) {
                        // APK mode - compact
                        OutlinedTextField(
                            value = apkPath,
                            onValueChange = {},
                            label = { Text("APK/APKS file", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { apkPicker.launch(arrayOf("application/zip", "application/vnd.android.package-archive", "*/*")) }) {
                                    Icon(Icons.Default.FolderOpen, "Browse", Modifier.size(18.dp))
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                            singleLine = true
                        )
                    }

                    // Package name - always show
                    OutlinedTextField(
                        value = selectedPkg,
                        onValueChange = { selectedPkg = it },
                        label = { Text("Package name", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                        trailingIcon = {
                            if (selectedPkg.isNotEmpty()) {
                                IconButton(onClick = { selectedPkg = "" }) {
                                    Icon(Icons.Default.Clear, "Clear", Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    if (dumpMode == 1) {
                        // Game chips - compact
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            games.take(6).forEach { (pkg, name) ->
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = { selectedPkg = pkg },
                                    label = { Text(name, fontSize = 8.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            games.drop(6).forEach { (pkg, name) ->
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = { selectedPkg = pkg },
                                    label = { Text(name, fontSize = 8.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    // Dump button - compact
                    Button(
                        onClick = {
                            if (dumpMode == 0 && apkUri == null) {
                                addLine("[-] Select APK file first!")
                                return@Button
                            }
                            if (selectedPkg.isBlank()) {
                                addLine("[-] Enter package name!")
                                return@Button
                            }
                            isRunning = true
                            progress = 0f
                            scope.launch(Dispatchers.IO) {
                                if (dumpMode == 0) {
                                    runApkDump(context, apkUri!!, selectedPkg, ::addLine,
                                        { p -> progress = p },
                                        { cs -> dumpCsContent = cs },
                                        { s -> status = s })
                                } else {
                                    runAutoDumpV6(selectedPkg, context, ::addLine,
                                        { p -> progress = p },
                                        { cs -> dumpCsContent = cs },
                                        { s -> status = s })
                                }
                                isRunning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) AccentRed else AccentGreen
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Dumping... ${"%.0f".format(progress * 100)}%", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dump", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isRunning) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = AccentCyan, trackColor = DarkBg)
                    }
                }
            }

            // ===== OUTPUT CARD =====
            Card(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 ${output.size} lines", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        if (dumpCsContent.isNotEmpty()) Text("✅ dump.cs", color = AccentCyan, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🚀", fontSize = 36.sp)
                                Spacer(Modifier.height(4.dp))
                                if (dumpMode == 0) {
                                    Text("Select APK → Enter package → Dump", color = TextSecondary, fontSize = 11.sp)
                                    Text("Supports .apk, .apks (split APK)", color = Color.Gray, fontSize = 9.sp)
                                } else {
                                    Text("Select game → Tap Dump", color = TextSecondary, fontSize = 11.sp)
                                    Text("Root required for memory dump", color = Color.Gray, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn {
                            items(output) { line ->
                                val color = when {
                                    line.startsWith("✅") || line.startsWith("🎉") -> AccentGreen
                                    line.startsWith("❌") -> AccentRed
                                    line.startsWith("⚠️") -> AccentOrange
                                    line.startsWith("🎯") || line.startsWith("📦") || line.startsWith("🔍") -> AccentCyan
                                    line.startsWith("📊") -> AccentPurple
                                    line.startsWith("  →") -> Color(0xFF888888)
                                    line.contains("namespace") || line.contains("class ") -> AccentCyan
                                    line.contains("offset=") -> AccentOrange
                                    else -> TextPrimary
                                }
                                Text(line, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }

            Text("© Panxcz & Freebuff | v6.0", color = TextSecondary, fontSize = 8.sp,
                modifier = Modifier.fillMaxWidth().padding(2.dp), textAlign = TextAlign.Center)
        }
    }
}

// ========== APK DUMP ENGINE ==========
private suspend fun runApkDump(
    context: Context,
    apkUri: Uri,
    pkgHint: String,
    addLine: (String) -> Unit,
    setProgress: (Float) -> Unit,
    setDumpCs: (String) -> Unit,
    setStatus: (String) -> Unit
) = withContext(Dispatchers.IO) {

    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    addLine("🚀 Auto Dump v6 - APK Extractor")
    addLine("Time: $ts")
    addLine("")

    // 1. Copy APK to cache
    addLine("📦 Copying APK to cache...")
    setProgress(0.05f)
    val cacheDir = File(context.cacheDir, "dumps")
    cacheDir.mkdirs()
    val cacheFile = File(cacheDir, "temp_apk.apk")
    try {
        context.contentResolver.openInputStream(apkUri)?.use { ins ->
            FileOutputStream(cacheFile).use { out ->
                ins.copyTo(out)
            }
        } ?: run {
            addLine("❌ Cannot read APK file")
            return@withContext
        }
        addLine("✅ APK: ${cacheFile.length() / 1024}KB")
    } catch (e: Exception) {
        addLine("❌ Failed: ${e.message}")
        return@withContext
    }

    // 2. Determine if split APK
    addLine("\n📦 Analyzing...")
    setProgress(0.1f)
    val isApks = cacheFile.name.endsWith(".apks", true) || cacheFile.name.endsWith(".apkm", true)
    val pkg = pkgHint.ifEmpty { "unknown" }

    try {
        val zipFile = ZipFile(cacheFile)
        val entries = zipFile.entries().toList()

        if (isApks) {
            addLine("📦 Split APK (${entries.size} entries)")
            setProgress(0.15f)

            // Find arm64 split
            val arm64Split = entries.find { it.name.contains("arm64") && it.name.endsWith(".apk") }

            if (arm64Split != null) {
                val splitFile = File(cacheDir, "arm64_split.apk")
                zipFile.getInputStream(arm64Split).use { input ->
                    FileOutputStream(splitFile).use { output -> input.copyTo(output) }
                }

                val splitZip = ZipFile(splitFile)
                val splitEntries = splitZip.entries().toList()

                // List all .so files
                val allSo = splitEntries.filter { it.name.endsWith(".so") }
                addLine("   .so files: ${allSo.size}")
                allSo.forEach { addLine("   → ${it.name.substringAfterLast("/")} (${it.size / 1024}KB)") }

                // Find libil2cpp.so
                val il2cppEntry = splitEntries.find { it.name.contains("libil2cpp.so") }
                if (il2cppEntry != null) {
                    addLine("\n🎯 libil2cpp.so: ${il2cppEntry.size / 1024}KB")
                    setProgress(0.25f)

                    val il2cppFile = File(cacheDir, "libil2cpp.so")
                    splitZip.getInputStream(il2cppEntry).use { input ->
                        FileOutputStream(il2cppFile).use { output -> input.copyTo(output) }
                    }

                    val il2cppBytes = il2cppFile.readBytes()
                    val dumpCs = StringBuilder()
                    dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6\n")
                    dumpCs.append("// Package: $pkg\n")
                    dumpCs.append("// APK: $apkPath\n")
                    dumpCs.append("// Date: $ts\n\n")

                    // Parse ELF
                    val elfResult = parseElfHeaderFull(il2cppBytes, addLine)
                    dumpCs.append(elfResult)
                    setProgress(0.4f)

                    // Extract strings
                    addLine("\n📝 Extracting strings...")
                    val allStrings = extractStringsFromBinary(il2cppBytes, 4)
                    addLine("   Total: ${allStrings.size}")

                    val categories = categorizeStrings(allStrings)
                    dumpCs.append(buildDumpCsContent(categories, allStrings.size))
                    setDumpCs(dumpCs.toString())

                    // Save
                    val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump/$pkg")
                    saveDir.mkdirs()
                    val csFile = File(saveDir, "dump.cs")
                    csFile.writeText(dumpCs.toString())
                    addLine("\n✅ dump.cs: ${csFile.absolutePath}")
                    addLine("   (${dumpCs.lines().size} lines)")

                    // Summary
                    addLine("\n📊 Categories:")
                    addLine("   Game: ${categories.game.size} | Types: ${categories.types.size}")
                    addLine("   Methods: ${categories.methods.size} | NS: ${categories.namespaces.size}")
                    addLine("   Unity: ${categories.unity.size} | Network: ${categories.network.size}")
                } else {
                    addLine("❌ libil2cpp.so not found in split!")
                }

                // Check metadata
                val metaEntry = splitEntries.find { it.name.contains("global-metadata") }
                if (metaEntry != null) {
                    if (metaEntry.size > 0) {
                        addLine("\n📦 global-metadata.dat: ${metaEntry.size} bytes")
                        val metaFile = File(cacheDir, "global-metadata.dat")
                        splitZip.getInputStream(metaEntry).use { input ->
                            FileOutputStream(metaFile).use { output -> input.copyTo(output) }
                        }
                        val metaBytes = metaFile.readBytes()
                        if (metaBytes.size >= 4) {
                            val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            if (magic == -83918337) {
                                addLine("   ✅ Valid metadata! Parsing...")
                                parseMetadataFull(metaBytes, addLine, setDumpCs)
                            } else {
                                addLine("   ⚠️ Invalid magic (encrypted)")
                            }
                        }
                    } else {
                        addLine("   ⚠️ Empty (encrypted/downloaded at runtime)")
                    }
                } else {
                    addLine("   ⚠️ Not in APK (downloaded at runtime)")
                }

                splitZip.close()
            }
            zipFile.close()
        } else {
            // Regular APK
            addLine("📦 Regular APK (${entries.size} entries)")
            val soEntries = entries.filter { it.name.endsWith(".so") }
            val il2cppEntry = entries.find { it.name.contains("libil2cpp.so") }
            val metaEntry = entries.find { it.name.contains("global-metadata") }

            addLine("   .so: ${soEntries.size}")

            if (il2cppEntry != null) {
                addLine("🎯 libil2cpp.so: ${il2cppEntry.size / 1024}KB")
                val il2cppFile = File(cacheDir, "libil2cpp.so")
                zipFile.getInputStream(il2cppEntry).use { input ->
                    FileOutputStream(il2cppFile).use { output -> input.copyTo(output) }
                }
                val il2cppBytes = il2cppFile.readBytes()

                val dumpCs = StringBuilder()
                dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6\n")
                dumpCs.append("// Package: $pkg\n")
                dumpCs.append("// Date: $ts\n\n")

                dumpCs.append(parseElfHeaderFull(il2cppBytes, addLine))

                val allStrings = extractStringsFromBinary(il2cppBytes, 4)
                addLine("\n📝 Strings: ${allStrings.size}")
                val categories = categorizeStrings(allStrings)
                dumpCs.append(buildDumpCsContent(categories, allStrings.size))
                setDumpCs(dumpCs.toString())

                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump/$pkg")
                saveDir.mkdirs()
                val csFile = File(saveDir, "dump.cs")
                csFile.writeText(dumpCs.toString())
                addLine("✅ dump.cs: ${csFile.absolutePath}")
            }

            if (metaEntry != null && metaEntry.size > 0) {
                addLine("📦 global-metadata.dat: ${metaEntry.size} bytes")
                val metaFile = File(cacheDir, "global-metadata.dat")
                zipFile.getInputStream(metaEntry).use { input ->
                    FileOutputStream(metaFile).use { output -> input.copyTo(output) }
                }
                val metaBytes = metaFile.readBytes()
                if (metaBytes.size >= 4) {
                    val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (magic == -83918337) {
                        addLine("   ✅ Valid!")
                        parseMetadataFull(metaBytes, addLine, setDumpCs)
                    } else {
                        addLine("   ⚠️ Encrypted")
                    }
                }
            } else {
                addLine("⚠️ global-metadata.dat: not found/empty (encrypted)")
            }

            zipFile.close()
        }
    } catch (e: Exception) {
        addLine("❌ Error: ${e.message}")
    }

    // Cleanup
    cacheFile.delete()
    File(cacheDir, "arm64_split.apk").delete()
    File(cacheDir, "libil2cpp.so").delete()
    File(cacheDir, "global-metadata.dat").delete()

    setProgress(1.0f)
    addLine("\n🎉 Done!")
    setStatus("Complete")
}

// ========== ROOT DUMP ENGINE V6 ==========
private suspend fun runAutoDumpV6(
    pkg: String,
    context: Context,
    addLine: (String) -> Unit,
    setProgress: (Float) -> Unit,
    setDumpCs: (String) -> Unit,
    setStatus: (String) -> Unit
) = withContext(Dispatchers.IO) {

    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    addLine("🚀 Auto Dump v6 - Root Memory")
    addLine("Package: $pkg | Time: $ts")
    addLine("")

    // Root check
    addLine("🔍 Checking root...")
    val rootOk = suShell("id").contains("uid=0")
    if (!rootOk) {
        addLine("❌ No root! Use APK mode or run with root.")
        return@withContext
    }
    addLine("✅ Root OK")
    setProgress(0.05f)

    // Find PID
    val pidOut = suShell("pidof $pkg")
    val pid = pidOut.trim().split("\\s+".toRegex()).firstOrNull { it.all { c -> c.isDigit() } }
    if (pid == null) {
        addLine("❌ Process not found. Launch $pkg first!")
        return@withContext
    }
    addLine("✅ PID: $pid")
    setProgress(0.1f)

    // Parse maps
    addLine("\n📋 Memory maps...")
    val mapsRaw = suShell("cat /proc/$pid/maps")
    val maps = mapsRaw.lines().filter { it.isNotBlank() }
    val allReadable = maps.filter { it.substringAfter(" ").substringBefore(" ")[0] == 'r' }
    val codeRegions = maps.filter { it.substringAfter(" ").substringBefore(" ").contains("x") && (it.contains(".so") || it.contains(".oat")) }
    addLine("   Total: ${maps.size} | Readable: ${allReadable.size}")
    setProgress(0.15f)

    // Find libil2cpp.so
    addLine("\n🎯 Finding libil2cpp.so...")
    val il2cppLine = maps.find { it.contains("libil2cpp.so") && it.contains("r-xp") }
        ?: maps.find { it.contains("libil2cpp.so") }
    if (il2cppLine == null) {
        addLine("❌ libil2cpp.so not found!")
        addLine("   .so files:")
        codeRegions.take(5).forEach { addLine("   → ${it.substringAfterLast(" ").trim()}") }
        return@withContext
    }

    val il2cppRange = il2cppLine.substringBefore(" ")
    val il2cppStart = il2cppRange.substringBefore("-").toLong(16)
    val il2cppEnd = il2cppRange.substringAfter("-").toLong(16)
    val il2cppSize = il2cppEnd - il2cppStart
    addLine("✅ 0x${"%X".format(il2cppStart)} (${il2cppSize / 1024}KB)")
    setProgress(0.2f)

    // Find libcsharp.so
    val csharpLine = maps.find { it.contains("libcsharp.so") && it.contains("r-xp") }
    if (csharpLine != null) {
        val csharpRange = csharpLine.substringBefore(" ")
        val cs = csharpRange.substringBefore("-").toLong(16)
        val ce = csharpRange.substringAfter("-").toLong(16)
        addLine("✅ libcsharp.so: 0x${"%X".format(cs)} (${(ce - cs) / 1024}KB)")
    }

    // Search metadata
    addLine("\n📦 Searching metadata...")
    val magic = intArrayOf(0xAF, 0x1B, 0xF1, 0xFA)
    var metaOffset = 0L
    var metaFound = false

    // Strategy 1: Near il2cpp
    addLine("   Strategy 1: Near libil2cpp...")
    for (region in maps.filter { it.contains("libil2cpp.so") }) {
        val perms = region.substringAfter(" ").substringBefore(" ")
        if (perms[0] != 'r') continue
        val range = region.substringBefore(" ")
        val start = range.substringBefore("-").toLong(16)
        val end = range.substringAfter("-").toLong(16)
        val size = (end - start).toInt().coerceAtMost(4194304)
        if (size < 4) continue
        val data = readMemChunked(pid, start, size) ?: continue
        val found = findMagic(data, magic)
        if (found >= 0) {
            metaOffset = start + found
            metaFound = true
            addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
            break
        }
    }

    // Strategy 2: All readable
    if (!metaFound) {
        addLine("   Strategy 2: Scanning all readable...")
        var scanned = 0
        for (region in allReadable) {
            val range = region.substringBefore(" ")
            val start = range.substringBefore("-").toLong(16)
            val end = range.substringAfter("-").toLong(16)
            val size = (end - start).toInt().coerceAtMost(4194304)
            if (size < 4) continue
            val data = readMemChunked(pid, start, size) ?: continue
            val found = findMagic(data, magic)
            if (found >= 0) {
                metaOffset = start + found
                metaFound = true
                addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                break
            }
            scanned++
            if (scanned % 100 == 0) addLine("   ...$scanned regions...")
        }
    }

    if (!metaFound) {
        addLine("   ⚠️ Metadata ENCRYPTED (not in memory)")
        addLine("   → Enter lobby/match first, or use APK mode")
    }
    setProgress(0.4f)

    // Build dump.cs
    val dumpCs = StringBuilder()
    dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6 (Root)\n")
    dumpCs.append("// Package: $pkg | PID: $pid\n")
    dumpCs.append("// libil2cpp: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)} (${il2cppSize / 1024}KB)\n")
    dumpCs.append("// Metadata: ${if (metaFound) "0x${"%X".format(metaOffset)}" else "ENCRYPTED"}\n")
    dumpCs.append("// Date: $ts\n\n")

    // Read ELF header
    val elfData = readMemChunked(pid, il2cppStart, 4096.coerceAtMost(il2cppSize.toInt()))
    var is64bit = true
    if (elfData != null && elfData.size >= 20 && elfData[0] == 0x7F.toByte() && elfData[1] == 'E'.code.toByte()) {
        is64bit = elfData[4] == 2.toByte()
        dumpCs.append("// ELF: ${if (is64bit) "64-bit" else "32-bit"}\n")
        addLine("\n📖 ELF: ${if (is64bit) "64-bit" else "32-bit"}")
    }

    // Parse metadata
    if (metaFound) {
        val metaData = readMemChunked(pid, metaOffset, 256)
        if (metaData != null && metaData.size >= 24) {
            val bb = ByteBuffer.wrap(metaData).order(ByteOrder.LITTLE_ENDIAN)
            val magicFound = bb.getInt(0)
            val version = bb.getInt(4)
            if (magicFound == -83918337 && version in 24..29) {
                addLine("   ✅ v$version")
                parseMetadataFull(metaData, addLine, setDumpCs)
            }
        }
    }
    setProgress(0.6f)

    // Extract strings
    addLine("\n📝 Extracting strings...")
    val allStrings = mutableSetOf<String>()
    var bytesRead = 0L
    while (bytesRead < il2cppSize) {
        val toRead = 1048576.coerceAtMost((il2cppSize - bytesRead).toInt())
        val data = readMemChunked(pid, il2cppStart + bytesRead, toRead)
        if (data == null || data.isEmpty()) break
        allStrings.addAll(extractStringsFromBinary(data, 5))
        bytesRead += toRead
    }

    // Also from libcsharp.so
    val csharpLine2 = maps.find { it.contains("libcsharp.so") && it.contains("r-xp") }
    if (csharpLine2 != null) {
        val csharpRange = csharpLine2.substringBefore(" ")
        val csStart = csharpRange.substringBefore("-").toLong(16)
        val csEnd = csharpRange.substringAfter("-").toLong(16)
        val csSize = csEnd - csStart
        addLine("   Also scanning libcsharp.so...")
        bytesRead = 0L
        while (bytesRead < csSize) {
            val toRead = 1048576.coerceAtMost((csSize - bytesRead).toInt())
            val data = readMemChunked(pid, csStart + bytesRead, toRead)
            if (data == null || data.isEmpty()) break
            allStrings.addAll(extractStringsFromBinary(data, 5))
            bytesRead += toRead
        }
    }

    addLine("   ✅ ${allStrings.size} strings")
    val categories = categorizeStrings(allStrings)
    dumpCs.append(buildDumpCsContent(categories, allStrings.size))
    setProgress(0.8f)

    // Dump IL2CPP regions
    addLine("\n💾 Dumping regions...")
    val saveDir = File(context.getExternalFilesDir(null), "dump/$pkg")
    saveDir.mkdirs()
    var dumpCount = 0
    for (region in maps.filter { it.contains("libil2cpp.so") }) {
        val range = region.substringBefore(" ")
        val perms = region.substringAfter(" ").substringBefore(" ")
        val start = range.substringBefore("-").toLong(16)
        val end = range.substringAfter("-").toLong(16)
        val size = (end - start).toInt().coerceAtMost(524288)
        if (size <= 0) continue
        val data = readMemChunked(pid, start, size)
        if (data != null && data.isNotEmpty()) {
            val tag = if (perms.contains("x")) "code" else if (perms.contains("w")) "data" else "ro"
            File(saveDir, "il2cpp_${tag}_0x${"%X".format(start)}.bin").writeBytes(data)
            dumpCount++
        }
        delay(5)
    }
    addLine("   ✅ $dumpCount regions dumped")

    // Save dump.cs
    setDumpCs(dumpCs.toString())
    val csFile = File(saveDir, "dump.cs")
    csFile.writeText(dumpCs.toString())
    addLine("\n✅ dump.cs: ${csFile.absolutePath}")

    setProgress(1.0f)
    addLine("\n🎉 Done! ${allStrings.size} strings, $dumpCount regions")
    setStatus("Complete: ${allStrings.size} strings")
}

// ========== ELF PARSER ==========
private fun parseElfHeaderFull(data: ByteArray, addLine: (String) -> Unit): String {
    if (data.size < 16 || data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte()) {
        return "// Invalid ELF\n"
    }

    val is64 = data[4] == 2.toByte()
    val isLE = data[5] == 1.toByte()
    val bb = ByteBuffer.wrap(data).order(if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

    val sb = StringBuilder()
    sb.append("// ELF: ${if (is64) "ELF64" else "ELF32"} ${if (isLE) "LE" else "BE"}\n")

    if (is64) {
        val entry = bb.getLong(24)
        val phoff = bb.getLong(32)
        val shoff = bb.getLong(40)
        val phnum = bb.getShort(56).toInt() and 0xFFFF
        val shnum = bb.getShort(60).toInt() and 0xFFFF
        sb.append("// Entry: 0x${"%016X".format(entry)} | PH: $phnum | SH: $shnum\n")
        addLine("   ELF64: Entry=0x${"%X".format(entry)} PH=$phnum SH=$shnum")
    } else {
        val entry = bb.getInt(24).toLong() and 0xFFFFFFFF
        val phnum = bb.getShort(44).toInt() and 0xFFFF
        val shnum = bb.getShort(48).toInt() and 0xFFFF
        sb.append("// Entry: 0x${"%08X".format(entry)} | PH: $phnum | SH: $shnum\n")
        addLine("   ELF32: Entry=0x${"%X".format(entry)} PH=$phnum SH=$shnum")
    }

    return sb.toString()
}

// ========== METADATA PARSER ==========
private fun parseMetadataFull(
    metaData: ByteArray,
    addLine: (String) -> Unit,
    setDumpCs: (String) -> Unit
) {
    if (metaData.size < 128) return

    val bb = ByteBuffer.wrap(metaData).order(ByteOrder.LITTLE_ENDIAN)
    val version = bb.getInt(4)

    addLine("   📊 IL2CPP Metadata v$version")

    if (version in 24..29 && metaData.size >= 128) {
        val typeDefOffset = bb.getInt(if (version >= 29) 44 else 24)
        val typeDefCount = bb.getInt(if (version >= 29) 48 else 28)
        val methodDefOffset = bb.getInt(if (version >= 29) 52 else 32)
        val methodDefCount = bb.getInt(if (version >= 29) 56 else 36)
        val fieldDefOffset = bb.getInt(if (version >= 29) 60 else 40)
        val fieldDefCount = bb.getInt(if (version >= 29) 64 else 44)

        addLine("   TypeDef: $typeDefCount | MethodDef: $methodDefCount | FieldDef: $fieldDefCount")
    }
}

// ========== STRING CATEGORIZER ==========
private data class StringCategories(
    val game: List<String>,
    val types: List<String>,
    val methods: List<String>,
    val namespaces: List<String>,
    val unity: List<String>,
    val network: List<String>,
    val il2cppApi: List<String>,
    val other: List<String>
)

private fun categorizeStrings(allStrings: Set<String>): StringCategories {
    val gameKw = listOf("Player", "Weapon", "Damage", "Health", "Score", "Enemy", "Bullet",
        "Aim", "Shoot", "Kill", "ESP", "Battle", "Entity", "Monster", "Hero", "Skill",
        "Camp", "Death", "Gun", "Knife", "Grenade", "Team", "Rank", "Match")
    val unityKw = listOf("UnityEngine", "Mono.", "System.", "Unity.", "MonoBehaviour",
        "GameObject", "Transform", "Rigidbody", "Collider", "Animator", "Camera")
    val netKw = listOf("http", "api", "token", "auth", "login", "session", "key",
        "secret", "supabase", "firebase", "cloudflare", "workers.dev", "convex", "telegram")
    val ilKw = listOf("il2cpp_", "mono_", "il2cpp_class", "il2cpp_method", "il2cpp_field")

    val game = allStrings.filter { s -> gameKw.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val types = allStrings.filter { it.startsWith("L") && it.contains("/") && it.endsWith(";") }.sorted()
    val methods = allStrings.filter { it.contains("(") && it.contains("->") && (it.contains("V") || it.contains("I") || it.contains("Z")) }.sorted()
    val namespaces = allStrings.filter { it.contains("::") && !it.contains("(") }.sorted()
    val unity = allStrings.filter { s -> unityKw.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val network = allStrings.filter { s -> netKw.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val il2cppApi = allStrings.filter { s -> ilKw.any { k -> s.startsWith(k) } }.sorted()
    val used = (game + types + methods + namespaces + unity + network + il2cppApi).toSet()
    val other = allStrings.filter { it !in used }.sorted()

    return StringCategories(game, types, methods, namespaces, unity, network, il2cppApi, other)
}

private fun buildDumpCsContent(c: StringCategories, total: Int): String {
    val sb = StringBuilder()
    sb.append("\n// === Strings ($total total) ===\n\n")

    if (c.game.isNotEmpty()) {
        sb.append("// === Game Structures (${c.game.size}) ===\n")
        c.game.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.types.isNotEmpty()) {
        sb.append("// === Type Descriptors (${c.types.size}) ===\n")
        c.types.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.methods.isNotEmpty()) {
        sb.append("// === Method Signatures (${c.methods.size}) ===\n")
        c.methods.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.namespaces.isNotEmpty()) {
        sb.append("// === Namespaces (${c.namespaces.size}) ===\n")
        c.namespaces.take(2000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.unity.isNotEmpty()) {
        sb.append("// === Unity Engine (${c.unity.size}) ===\n")
        c.unity.take(1000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.network.isNotEmpty()) {
        sb.append("// === Network/Auth (${c.network.size}) ===\n")
        c.network.take(1000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.il2cppApi.isNotEmpty()) {
        sb.append("// === IL2CPP API (${c.il2cppApi.size}) ===\n")
        c.il2cppApi.forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (c.other.isNotEmpty()) {
        sb.append("// === Other (${c.other.size}) ===\n")
        c.other.take(10000).forEach { sb.append("// $it\n") }
    }
    return sb.toString()
}

// ========== UTILITIES ==========
private fun readMemChunked(pid: String, addr: Long, size: Int): ByteArray? {
    if (size <= 0 || addr < 0) return null
    return try {
        val cmd = "python3 -c \"import sys;f=open('/proc/$pid/mem','rb');f.seek($addr);d=f.read($size);f.close();sys.stdout.buffer.write(d)\""
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val data = p.inputStream.readBytes()
        p.waitFor()
        if (data.isNotEmpty()) data else null
    } catch (_: Exception) {
        try {
            val cmd = "dd if=/proc/$pid/mem bs=4096 count=$((($size + 4095) / 4096)) skip=$(($addr / 4096)) 2>/dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val data = p.inputStream.readBytes()
            p.waitFor()
            val alignOffset = (addr % 4096).toInt()
            if (data.size > alignOffset) {
                data.copyOfRange(alignOffset, (alignOffset + size).coerceAtMost(data.size))
            } else null
        } catch (_: Exception) { null }
    }
}

private fun findMagic(data: ByteArray, magic: IntArray): Int {
    if (data.size < magic.size) return -1
    for (i in 0..(data.size - magic.size)) {
        if ((data[i].toInt() and 0xFF) == magic[0] &&
            (data[i + 1].toInt() and 0xFF) == magic[1] &&
            (data[i + 2].toInt() and 0xFF) == magic[2] &&
            (data[i + 3].toInt() and 0xFF) == magic[3]) {
            return i
        }
    }
    return -1
}

private fun extractStringsFromBinary(data: ByteArray, minLen: Int): Set<String> {
    val result = mutableSetOf<String>()
    val sb = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) {
            sb.append(c.toChar())
        } else {
            if (sb.length >= minLen) {
                val s = sb.toString()
                if (s.any { it.isLetter() }) result.add(s)
            }
            sb.clear()
        }
    }
    return result
}

private fun suShell(cmd: String): String {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (_: Exception) { "" }
}

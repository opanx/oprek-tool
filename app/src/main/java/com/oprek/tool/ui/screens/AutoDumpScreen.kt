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
import java.io.InputStream
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
    var dumpMode by remember { mutableStateOf(0) } // 0=APK, 1=Root

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

    // APK file picker
    var apkUri by remember { mutableStateOf<Uri?>(null) }
    var apkPath by remember { mutableStateOf("") }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            apkUri = it
            // Get filename from URI
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex >= 0) {
                    apkPath = c.getString(nameIndex) ?: ""
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump v6", fontWeight = FontWeight.Bold) },
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
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            if (dumpCsContent.isNotEmpty()) {
                                File(dir, "dump.cs").writeText(dumpCsContent)
                            }
                            val outFile = File(dir, "${selectedPkg.replace(".", "_")}_dump_${System.currentTimeMillis()}.txt")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${dir.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // Mode selector
                    Text("🎯 Dump Mode", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dumpMode == 0,
                            onClick = { dumpMode = 0 },
                            label = { Text("📦 APK File", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f))
                        )
                        FilterChip(
                            selected = dumpMode == 1,
                            onClick = { dumpMode = 1 },
                            label = { Text("🏴 Root Memory", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f))
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (dumpMode == 0) {
                        // APK mode
                        Text("📦 APK/APKS File", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { apkPicker.launch("application/zip") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select .apk or .apks file", fontSize = 11.sp)
                        }
                        if (apkPath.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("📁 ${apkPath.take(50)}...", color = AccentGreen, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = selectedPkg,
                            onValueChange = { selectedPkg = it },
                            label = { Text("Package name (auto-detect from APK)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp)
                        )
                    } else {
                        // Root mode
                        Text("🎮 Target", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            games.take(4).forEach { (pkg, name) ->
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = { selectedPkg = pkg },
                                    label = { Text(name, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            games.drop(4).take(4).forEach { (pkg, name) ->
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = { selectedPkg = pkg },
                                    label = { Text(name, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            games.drop(8).forEach { (pkg, name) ->
                                FilterChip(
                                    selected = selectedPkg == pkg,
                                    onClick = { selectedPkg = pkg },
                                    label = { Text(name, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = selectedPkg,
                            onValueChange = { selectedPkg = it },
                            label = { Text("Package name", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (dumpMode == 0 && apkUri == null) { addLine("[-] Select APK file first!"); return@Button }
                            if (dumpMode == 1 && selectedPkg.isBlank()) { addLine("[-] Enter package name!"); return@Button }
                            isRunning = true; output = emptyList(); dumpCsContent = ""; progress = 0f
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
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) AccentRed else AccentGreen
                        )
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Dumping... ${"%.0f".format(progress * 100)}%", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (dumpMode == 0) "📦 Dump APK → dump.cs" else "🏴 Root Dump → dump.cs", fontSize = 11.sp)
                        }
                    }

                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = AccentCyan, trackColor = DarkBg)
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                        if (dumpCsContent.isNotEmpty()) Text("✅ dump.cs ready", color = AccentCyan, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🚀", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                if (dumpMode == 0) {
                                    Text("Select APK file → tap Dump", color = TextSecondary, fontSize = 13.sp)
                                    Text("Extracts libil2cpp.so + global-metadata.dat", color = Color.Gray, fontSize = 11.sp)
                                } else {
                                    Text("Select game + tap Dump", color = TextSecondary, fontSize = 13.sp)
                                    Text("Root required for memory dump", color = Color.Gray, fontSize = 11.sp)
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
                                    line.contains("namespace") || line.contains("class ") || line.contains("struct ") -> AccentCyan
                                    line.contains("offset=") -> AccentOrange
                                    else -> TextPrimary
                                }
                                Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
            Text("© Panxcz & Freebuff | AutoDump v6.0", color = TextSecondary, fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

    addLine("🚀 Auto Dump v6 - APK IL2CPP Extractor")
    addLine("Time: $ts")
    addLine("")

    // 1. Copy APK to cache
    addLine("📦 Copying APK to cache...")
    setProgress(0.05f)
    val cacheDir = File(context.cacheDir, "dumps")
    cacheDir.mkdirs()
    val cacheFile = File(cacheDir, "temp_apk.apk")
    try {
        val ins: InputStream = context.contentResolver.openInputStream(apkUri) ?: run {
            addLine("❌ Cannot read APK file")
            return@withContext
        }
        FileOutputStream(cacheFile).use { out ->
            ins.copyTo(out)
            ins.close()
        }
        addLine("✅ APK copied: ${cacheFile.length() / 1024}KB")
    } catch (e: Exception) {
        addLine("❌ Failed to copy APK: ${e.message}")
        return@withContext
    }

    // 2. Determine if it's .apks (split APK) or .apk
    addLine("\n📦 Analyzing APK structure...")
    setProgress(0.1f)
    val isApks = cacheFile.name.endsWith(".apks", true) || cacheFile.name.endsWith(".apkm", true)
    val pkg = pkgHint.ifEmpty { "unknown" }

    try {
        val zipFile = ZipFile(cacheFile)
        val entries = zipFile.entries().toList()

        if (isApks) {
            addLine("📦 Split APK bundle detected (${entries.size} entries)")

            // Find arm64 split
            val arm64Split = entries.find { it.name.contains("arm64") && it.name.endsWith(".apk") }
            val baseApk = entries.find { it.name == "base.apk" }

            if (arm64Split != null) {
                addLine("📦 Found arm64 split: ${arm64Split.name}")
                setProgress(0.15f)

                // Extract arm64 split to temp
                val splitFile = File(cacheDir, "arm64_split.apk")
                zipFile.getInputStream(arm64Split).use { input ->
                    FileOutputStream(splitFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Parse the arm64 split
                val splitZip = ZipFile(splitFile)
                val splitEntries = splitZip.entries().toList()

                // Find libil2cpp.so
                val il2cppEntry = splitEntries.find { it.name.contains("libil2cpp.so") }
                val metadataEntry = splitEntries.find { it.name.contains("global-metadata") }
                val allSoEntries = splitEntries.filter { it.name.endsWith(".so") }

                addLine("   .so files: ${allSoEntries.size}")
                allSoEntries.forEach { addLine("   → ${it.name.substringAfterLast("/")} (${it.size / 1024}KB)") }

                if (il2cppEntry != null) {
                    addLine("\n🎯 Found libil2cpp.so (${il2cppEntry.size / 1024}KB)")
                    setProgress(0.2f)

                    // Extract libil2cpp.so
                    val il2cppFile = File(cacheDir, "libil2cpp.so")
                    splitZip.getInputStream(il2cppEntry).use { input ->
                        FileOutputStream(il2cppFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Parse ELF + extract dump.cs
                    val il2cppBytes = il2cppFile.readBytes()
                    addLine("   ELF magic: ${il2cppBytes[0].toInt() and 0xFF} ${il2cppBytes[1].toInt().toChar()} ${il2cppBytes[2].toInt().toChar()} ${il2cppBytes[3].toInt().toChar()}")

                    val dumpCs = StringBuilder()
                    dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6 (APK Mode)\n")
                    dumpCs.append("// Package: $pkg\n")
                    dumpCs.append("// APK: ${apkUri.lastPathSegment ?: "unknown"}\n")
                    dumpCs.append("// Date: $ts\n\n")

                    // Parse ELF header
                    val elfResult = parseElfHeaderFull(il2cppBytes, addLine)
                    dumpCs.append(elfResult.first)

                    setProgress(0.4f)

                    // Extract all strings
                    addLine("\n📝 Extracting strings from libil2cpp.so...")
                    val allStrings = extractStringsFromBinary(il2cppBytes, 4)
                    addLine("   Total strings: ${allStrings.size}")

                    // Categorize
                    val categories = categorizeStrings(allStrings)
                    dumpCs.append("\n// === String Literals (${allStrings.size} total) ===\n\n")

                    // Game-specific strings
                    if (categories.game.isNotEmpty()) {
                        dumpCs.append("// === Game Structures ===\n")
                        categories.game.take(5000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // Type descriptors
                    if (categories.types.isNotEmpty()) {
                        dumpCs.append("// === Type Descriptors (${categories.types.size}) ===\n")
                        categories.types.take(5000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // Method signatures
                    if (categories.methods.isNotEmpty()) {
                        dumpCs.append("// === Method Signatures (${categories.methods.size}) ===\n")
                        categories.methods.take(5000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // Namespaces
                    if (categories.namespaces.isNotEmpty()) {
                        dumpCs.append("// === Namespaces (${categories.namespaces.size}) ===\n")
                        categories.namespaces.take(2000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // Unity
                    if (categories.unity.isNotEmpty()) {
                        dumpCs.append("// === Unity Engine (${categories.unity.size}) ===\n")
                        categories.unity.take(1000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // Network/Auth
                    if (categories.network.isNotEmpty()) {
                        dumpCs.append("// === Network/Auth (${categories.network.size}) ===\n")
                        categories.network.take(1000).forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // IL2CPP API
                    if (categories.il2cppApi.isNotEmpty()) {
                        dumpCs.append("// === IL2CPP API (${categories.il2cppApi.size}) ===\n")
                        categories.il2cppApi.forEach { dumpCs.append("// $it\n") }
                        dumpCs.append("\n")
                    }

                    // All other strings
                    if (categories.other.isNotEmpty()) {
                        dumpCs.append("// === Other Strings (${categories.other.size}) ===\n")
                        categories.other.take(10000).forEach { dumpCs.append("// $it\n") }
                    }

                    setDumpCs(dumpCs.toString())

                    // Summary
                    addLine("\n📊 String Categories:")
                    addLine("   Game: ${categories.game.size}")
                    addLine("   Types: ${categories.types.size}")
                    addLine("   Methods: ${categories.methods.size}")
                    addLine("   Namespaces: ${categories.namespaces.size}")
                    addLine("   Unity: ${categories.unity.size}")
                    addLine("   Network: ${categories.network.size}")
                    addLine("   IL2CPP API: ${categories.il2cppApi.size}")
                    addLine("   Other: ${categories.other.size}")

                    // Save dump.cs
                    val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump/$pkg")
                    saveDir.mkdirs()
                    val csFile = File(saveDir, "dump.cs")
                    csFile.writeText(dumpCs.toString())
                    addLine("\n✅ dump.cs saved to ${csFile.absolutePath}")
                    addLine("   (${dumpCs.lines().size} lines)")
                } else {
                    addLine("❌ libil2cpp.so not found in arm64 split!")
                    addLine("   Available .so files:")
                    allSoEntries.forEach { addLine("   → ${it.name}") }
                }

                // Check for global-metadata.dat
                if (metadataEntry != null) {
                    addLine("\n📦 Found global-metadata.dat (${metadataEntry.size} bytes)")
                    if (metadataEntry.size > 0) {
                        val metaFile = File(cacheDir, "global-metadata.dat")
                        splitZip.getInputStream(metadataEntry).use { input ->
                            FileOutputStream(metaFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val metaBytes = metaFile.readBytes()
                        if (metaBytes.size >= 4) {
                            val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            addLine("   Magic: 0x${"%08X".format(magic)}")
                            if (magic == -83918337) { // 0xFAB11BAF
                                addLine("   ✅ Valid global-metadata.dat!")
                                setProgress(0.6f)
                                parseMetadataFull(metaBytes, addLine, setDumpCs)
                            } else {
                                addLine("   ⚠️ Invalid magic (metadata may be encrypted)")
                            }
                        }
                    } else {
                        addLine("   ⚠️ Empty metadata (encrypted/downloaded at runtime)")
                    }
                } else {
                    addLine("\n⚠️ global-metadata.dat not found in APK")
                    addLine("   → Metadata is likely downloaded at runtime (encrypted)")
                    addLine("   → Use Root mode with game running for memory dump")
                }

                splitZip.close()
            } else {
                addLine("❌ No arm64 split found in APK bundle")
            }
            zipFile.close()
        } else {
            // Regular .apk
            addLine("📦 Regular APK (${entries.size} entries)")
            val soEntries = entries.filter { it.name.endsWith(".so") }
            val il2cppEntry = entries.find { it.name.contains("libil2cpp.so") }
            val metaEntry = entries.find { it.name.contains("global-metadata") }

            addLine("   .so files: ${soEntries.size}")
            soEntries.forEach { addLine("   → ${it.name.substringAfterLast("/")} (${it.size / 1024}KB)") }

            if (il2cppEntry != null) {
                addLine("\n🎯 Found libil2cpp.so (${il2cppEntry.size / 1024}KB)")
                val il2cppFile = File(cacheDir, "libil2cpp.so")
                zipFile.getInputStream(il2cppEntry).use { input ->
                    FileOutputStream(il2cppFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val il2cppBytes = il2cppFile.readBytes()

                val dumpCs = StringBuilder()
                dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6 (APK Mode)\n")
                dumpCs.append("// Package: $pkg\n")
                dumpCs.append("// Date: $ts\n\n")

                val elfResult = parseElfHeaderFull(il2cppBytes, addLine)
                dumpCs.append(elfResult.first)

                val allStrings = extractStringsFromBinary(il2cppBytes, 4)
                addLine("\n📝 Total strings: ${allStrings.size}")

                val categories = categorizeStrings(allStrings)
                dumpCs.append(buildDumpCsContent(categories, allStrings.size))

                setDumpCs(dumpCs.toString())

                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump/$pkg")
                saveDir.mkdirs()
                val csFile = File(saveDir, "dump.cs")
                csFile.writeText(dumpCs.toString())
                addLine("✅ dump.cs saved: ${csFile.absolutePath}")
            }

            if (metaEntry != null && metaEntry.size > 0) {
                addLine("📦 Found global-metadata.dat (${metaEntry.size} bytes)")
                val metaFile = File(cacheDir, "global-metadata.dat")
                zipFile.getInputStream(metaEntry).use { input ->
                    FileOutputStream(metaFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val metaBytes = metaFile.readBytes()
                if (metaBytes.size >= 4) {
                    val magic = ByteBuffer.wrap(metaBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (magic == -83918337) {
                        addLine("   ✅ Valid metadata!")
                        parseMetadataFull(metaBytes, addLine, setDumpCs)
                    } else {
                        addLine("   ⚠️ Invalid magic (encrypted)")
                    }
                }
            } else {
                addLine("⚠️ global-metadata.dat not found or empty (encrypted)")
            }

            zipFile.close()
        }
    } catch (e: Exception) {
        addLine("❌ Error: ${e.message}")
        e.printStackTrace()
    }

    // Cleanup
    cacheFile.delete()
    File(cacheDir, "arm64_split.apk").delete()
    File(cacheDir, "libil2cpp.so").delete()
    File(cacheDir, "global-metadata.dat").delete()

    setProgress(1.0f)
    addLine("\n🎉 Dump complete!")
    setStatus("Done")
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

    addLine("🚀 Auto Dump v6 - Root Memory Dump")
    addLine("Package: $pkg")
    addLine("Time: $ts")
    addLine("")

    // 1. Check root
    addLine("🔍 Checking root...")
    val rootOk = suShell("id").contains("uid=0")
    if (!rootOk) {
        addLine("❌ No root! Run with root/shizuku.")
        return@withContext
    }
    addLine("✅ Root confirmed")
    setProgress(0.05f)

    // 2. Find PID
    addLine("\n🔍 Finding PID...")
    val pidOut = suShell("pidof $pkg")
    val pid = pidOut.trim().split("\\s+".toRegex()).firstOrNull { it.all { c -> c.isDigit() } }
    if (pid == null) {
        addLine("❌ Process not found. Launch $pkg first!")
        return@withContext
    }
    addLine("✅ PID: $pid")
    setProgress(0.1f)

    // 3. Parse memory maps
    addLine("\n📋 Parsing memory maps...")
    val mapsRaw = suShell("cat /proc/$pid/maps")
    val maps = mapsRaw.lines().filter { it.isNotBlank() }

    val allReadable = maps.filter { line ->
        val perms = line.substringAfter(" ").substringBefore(" ")
        perms[0] == 'r'
    }
    val codeRegions = maps.filter { line ->
        val perms = line.substringAfter(" ").substringBefore(" ")
        perms.contains("x") && (line.contains(".so") || line.contains(".oat"))
    }

    addLine("   Total: ${maps.size} | Readable: ${allReadable.size} | Code: ${codeRegions.size}")
    setProgress(0.15f)

    // 4. Find libil2cpp.so
    addLine("\n🎯 Searching for libil2cpp.so...")
    val il2cppLine = maps.find { it.contains("libil2cpp.so") && it.contains("r-xp") }
        ?: maps.find { it.contains("libil2cpp.so") }
    if (il2cppLine == null) {
        addLine("❌ libil2cpp.so not found!")
        addLine("   Available .so files:")
        codeRegions.take(10).forEach { addLine("     ${it.substringAfterLast(" ").trim()}") }
        setStatus("Failed: libil2cpp.so not found")
        return@withContext
    }

    val il2cppRange = il2cppLine.substringBefore(" ")
    val il2cppStart = il2cppRange.substringBefore("-").toLong(16)
    val il2cppEnd = il2cppRange.substringAfter("-").toLong(16)
    val il2cppSize = il2cppEnd - il2cppStart
    addLine("✅ Found libil2cpp.so @ 0x${"%X".format(il2cppStart)} (${il2cppSize / 1024}KB)")
    setProgress(0.2f)

    // Find libcsharp.so
    val csharpLine = maps.find { it.contains("libcsharp.so") && it.contains("r-xp") }
    var csharpStart = 0L
    var csharpEnd = 0L
    if (csharpLine != null) {
        val csharpRange = csharpLine.substringBefore(" ")
        csharpStart = csharpRange.substringBefore("-").toLong(16)
        csharpEnd = csharpRange.substringAfter("-").toLong(16)
        addLine("✅ Found libcsharp.so @ 0x${"%X".format(csharpStart)} (${(csharpEnd - csharpStart) / 1024}KB)")
    }

    // 5. Search for global-metadata.dat
    addLine("\n📦 Searching for global-metadata.dat...")
    val magic = intArrayOf(0xAF, 0x1B, 0xF1, 0xFA)

    var metaOffset = 0L
    var metaFound = false

    // Strategy 1: Near libil2cpp.so
    addLine("   Strategy 1: Near libil2cpp.so regions...")
    val il2cppRegions = maps.filter { it.contains("libil2cpp.so") }
    for (region in il2cppRegions) {
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
            addLine("   ✅ Found metadata @ 0x${"%X".format(metaOffset)} (near libil2cpp)")
            break
        }
    }

    // Strategy 2: All readable regions
    if (!metaFound) {
        addLine("   Strategy 2: All readable regions...")
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
                addLine("   ✅ Found metadata @ 0x${"%X".format(metaOffset)}")
                break
            }
            scanned++
            if (scanned % 100 == 0) {
                addLine("   ... scanned $scanned regions ...")
                delay(1)
            }
        }
    }

    if (!metaFound) {
        addLine("⚠️ global-metadata.dat NOT found in memory")
        addLine("")
        addLine("   🔒 Metadata is likely ENCRYPTED at runtime")
        addLine("   This is common in games like MLBB, Free Fire, PUBG")
        addLine("")
        addLine("   💡 Suggestions:")
        addLine("   → Enter a match/lobby first, then dump")
        addLine("   → Try APK mode instead (extract from APK)")
        addLine("   → Use Il2CppDumper on PC with dumped libil2cpp.so")
    }
    setProgress(0.4f)

    // 6. Parse metadata
    val dumpCs = StringBuilder()
    dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v6 (Root Mode)\n")
    dumpCs.append("// Package: $pkg\n")
    dumpCs.append("// PID: $pid\n")
    dumpCs.append("// libil2cpp: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)} (${il2cppSize / 1024}KB)\n")
    if (csharpStart > 0) {
        dumpCs.append("// libcsharp: 0x${"%X".format(csharpStart)} - 0x${"%X".format(csharpEnd)} (${(csharpEnd - csharpStart) / 1024}KB)\n")
    }
    dumpCs.append("// Metadata: ${if (metaFound) "0x${"%X".format(metaOffset)}" else "ENCRYPTED (not in memory)"}\n")
    dumpCs.append("// Date: $ts\n\n")

    // Read ELF header
    addLine("\n📖 Reading ELF headers...")
    val elfData = readMemChunked(pid, il2cppStart, 4096.coerceAtMost(il2cppSize.toInt()))
    var is64bit = true
    if (elfData != null && elfData.size >= 20 && elfData[0] == 0x7F.toByte() && elfData[1] == 'E'.code.toByte()) {
        is64bit = elfData[4] == 2.toByte()
        dumpCs.append("// ELF: ${if (is64bit) "64-bit" else "32-bit"}\n")
        addLine("   libil2cpp.so: ${if (is64bit) "ELF64" else "ELF32"}")
    }

    // Parse metadata ONLY if found AND valid
    if (metaFound) {
        addLine("\n📖 Validating metadata header...")
        val metaData = readMemChunked(pid, metaOffset, 256)
        if (metaData != null && metaData.size >= 24) {
            val bb = ByteBuffer.wrap(metaData).order(ByteOrder.LITTLE_ENDIAN)
            val magicFound = bb.getInt(0)
            val version = bb.getInt(4)

            if (magicFound == -83918337 && version in 24..29) {
                addLine("   ✅ Header valid: magic=0x${"%08X".format(magicFound)}, version=$version")
                parseMetadataFull(metaData, addLine, setDumpCs)
            } else {
                addLine("   ❌ Invalid metadata header!")
                addLine("   → Magic: 0x${"%08X".format(magicFound)} (expected 0xFAB11BAF)")
                addLine("   → Version: $version (expected 24-29)")
            }
        }
    }
    setProgress(0.6f)

    // 7. Extract strings from libil2cpp.so
    addLine("\n🔍 Extracting strings from libil2cpp.so...")
    val allStrings = mutableSetOf<String>()
    var bytesRead = 0L
    val chunkSize = 1048576

    while (bytesRead < il2cppSize) {
        val toRead = chunkSize.coerceAtMost((il2cppSize - bytesRead).toInt())
        val data = readMemChunked(pid, il2cppStart + bytesRead, toRead)
        if (data == null || data.isEmpty()) break
        allStrings.addAll(extractStringsFromBinary(data, 5))
        bytesRead += toRead
    }

    if (csharpStart > 0) {
        addLine("🔍 Extracting strings from libcsharp.so...")
        bytesRead = 0L
        val csharpSize = csharpEnd - csharpStart
        while (bytesRead < csharpSize) {
            val toRead = chunkSize.coerceAtMost((csharpSize - bytesRead).toInt())
            val data = readMemChunked(pid, csharpStart + bytesRead, toRead)
            if (data == null || data.isEmpty()) break
            allStrings.addAll(extractStringsFromBinary(data, 5))
            bytesRead += toRead
        }
    }

    addLine("   ✅ Total strings: ${allStrings.size}")
    val categories = categorizeStrings(allStrings)
    dumpCs.append(buildDumpCsContent(categories, allStrings.size))

    setProgress(0.8f)

    // 8. Dump raw memory regions
    addLine("\n💾 Dumping IL2CPP regions...")
    val saveDir = File(context.getExternalFilesDir(null), "dump/$pkg")
    saveDir.mkdirs()
    var dumpCount = 0

    for (region in il2cppRegions) {
        val range = region.substringBefore(" ")
        val perms = region.substringAfter(" ").substringBefore(" ")
        val start = range.substringBefore("-").toLong(16)
        val end = range.substringAfter("-").toLong(16)
        val size = (end - start).toInt().coerceAtMost(524288)

        if (size <= 0) continue
        val data = readMemChunked(pid, start, size)
        if (data != null && data.isNotEmpty()) {
            val tag = if (perms.contains("x")) "code" else if (perms.contains("w")) "data" else "ro"
            val outFile = File(saveDir, "il2cpp_${tag}_0x${"%X".format(start)}.bin")
            outFile.writeBytes(data)
            dumpCount++
        }
        delay(5)
    }

    addLine("   ✅ Dumped $dumpCount IL2CPP regions")

    // 9. Save dump.cs
    setDumpCs(dumpCs.toString())
    val csFile = File(saveDir, "dump.cs")
    csFile.writeText(dumpCs.toString())
    addLine("\n✅ dump.cs saved to ${csFile.absolutePath}")

    setProgress(1.0f)
    addLine("\n🎉 Dump complete! ${allStrings.size} strings, $dumpCount regions, dump.cs generated.")
    setStatus("Done: ${allStrings.size} strings")
}

// ========== ELF PARSER ==========
private fun parseElfHeaderFull(data: ByteArray, addLine: (String) -> Unit): Pair<String, Any> {
    if (data.size < 16 || data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte()) {
        return Pair("// Invalid ELF file\n", false)
    }

    val is64 = data[4] == 2.toByte()
    val isLE = data[5] == 1.toByte()
    val bb = ByteBuffer.wrap(data).order(if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

    val sb = StringBuilder()
    sb.append("// ELF Header\n")
    sb.append("// Class: ${if (is64) "ELF64" else "ELF32"}\n")
    sb.append("// Endian: ${if (isLE) "Little" else "Big"}\n")

    if (is64) {
        val entry = bb.getLong(24)
        val phoff = bb.getLong(32)
        val shoff = bb.getLong(40)
        val phnum = bb.getShort(56).toInt() and 0xFFFF
        val shnum = bb.getShort(60).toInt() and 0xFFFF
        val shstrndx = bb.getShort(62).toInt() and 0xFFFF
        sb.append("// Entry: 0x${"%016X".format(entry)}\n")
        sb.append("// PH offset: 0x${"%X".format(phoff)}, count: $phnum\n")
        sb.append("// SH offset: 0x${"%X".format(shoff)}, count: $shnum\n")
        sb.append("// SH strndx: $shstrndx\n\n")
        addLine("   ELF64: Entry=0x${"%X".format(entry)}, PH=$phnum, SH=$shnum")
    } else {
        val entry = bb.getInt(24).toLong() and 0xFFFFFFFF
        val phoff = bb.getInt(28).toLong() and 0xFFFFFFFF
        val shoff = bb.getInt(32).toLong() and 0xFFFFFFFF
        val phnum = bb.getShort(44).toInt() and 0xFFFF
        val shnum = bb.getShort(48).toInt() and 0xFFFF
        val shstrndx = bb.getShort(50).toInt() and 0xFFFF
        sb.append("// Entry: 0x${"%08X".format(entry)}\n")
        sb.append("// PH offset: 0x${"%X".format(phoff)}, count: $phnum\n")
        sb.append("// SH offset: 0x${"%X".format(shoff)}, count: $shnum\n")
        sb.append("// SH strndx: $shstrndx\n\n")
        addLine("   ELF32: Entry=0x${"%X".format(entry)}, PH=$phnum, SH=$shnum")
    }

    return Pair(sb.toString(), is64)
}

// ========== METADATA PARSER ==========
private fun parseMetadataFull(
    metaData: ByteArray,
    addLine: (String) -> Unit,
    setDumpCs: (String) -> Unit
) {
    if (metaData.size < 128) return

    val bb = ByteBuffer.wrap(metaData).order(ByteOrder.LITTLE_ENDIAN)
    val magic = bb.getInt(0)
    val version = bb.getInt(4)

    addLine("   📊 Metadata v$version")

    // Parse IL2CPP metadata header based on version
    if (version in 24..29 && metaData.size >= 128) {
        valstringLiteralOffset = bb.getInt(8)
        val stringLiteralCount = bb.getInt(12)

        // Version-dependent offsets
        val stringLiteralDataOffset = if (version >= 29) bb.getInt(28).toLong() and 0xFFFFFFFF else 0L
        val typeDefOffset = bb.getInt(if (version >= 29) 44 else 24)
        val typeDefCount = bb.getInt(if (version >= 29) 48 else 28)
        val methodDefOffset = bb.getInt(if (version >= 29) 52 else 32)
        val methodDefCount = bb.getInt(if (version >= 29) 56 else 36)
        val fieldDefOffset = bb.getInt(if (version >= 29) 60 else 40)
        val fieldDefCount = bb.getInt(if (version >= 29) 64 else 44)
        val parameterOffset = bb.getInt(if (version >= 29) 68 else 48)
        val parameterCount = bb.getInt(if (version >= 29) 72 else 52)
        val stringOffset = bb.getInt(if (version >= 29) 76 else 56)
        val stringCount = bb.getInt(if (version >= 29) 80 else 60)
        val genericContainerOffset = bb.getInt(if (version >= 29) 84 else 104)
        val genericContainerCount = bb.getInt(if (version >= 29) 88 else 108)

        addLine("   TypeDef: $typeDefCount | MethodDef: $methodDefCount | FieldDef: $fieldDefCount")
        addLine("   Parameter: $parameterCount | String: $stringCount | Generic: $genericContainerCount")
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
    val gameKeywords = listOf("Player", "Weapon", "Damage", "Health", "Score", "Enemy", "Bullet",
        "Aim", "Shoot", "Kill", "ESP", "Aimbot", "BattleManager", "ShowEntity", "ShowPlayer",
        "Monster", "Retribution", "Lord", "Turtle", "Buff", "Hero", "Skill", "Camp", "Die",
        "Death", "Gun", "Knife", "Grenade", "Bomb", "Team", "Rank", "League", "Match")
    val unityKeywords = listOf("UnityEngine", "Mono.", "System.", "Unity.", "MonoBehaviour",
        "GameObject", "Transform", "Rigidbody", "Collider", "Animator", "Camera",
        "Light", "AudioSource", "Renderer", "Canvas", "UI", "EventSystem")
    val networkKeywords = listOf("http", "api", "token", "auth", "login", "session", "key",
        "secret", "supabase", "firebase", "cloudflare", "workers.dev", "vercel",
        "netlify", "render.com", "migoreng", "convex", "telegram", "t.me")
    val il2cppKeywords = listOf("il2cpp_", "mono_", "il2cpp_class", "il2cpp_method",
        "il2cpp_field", "il2cpp_string", "il2cpp_array", "il2cpp_object")

    val game = allStrings.filter { s -> gameKeywords.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val types = allStrings.filter { it.startsWith("L") && it.contains("/") && it.endsWith(";") }.sorted()
    val methods = allStrings.filter { it.contains("(") && (it.contains("V") || it.contains("I") || it.contains("Z")) && it.contains("->") }.sorted()
    val namespaces = allStrings.filter { it.contains("::") && !it.contains("(") }.sorted()
    val unity = allStrings.filter { s -> unityKeywords.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val network = allStrings.filter { s -> networkKeywords.any { k -> s.contains(k, ignoreCase = true) } }.sorted()
    val il2cppApi = allStrings.filter { s -> il2cppKeywords.any { k -> s.startsWith(k) } }.sorted()
    val used = game + types + methods + namespaces + unity + network + il2cppApi
    val other = allStrings.filter { it !in used }.sorted()

    return StringCategories(game, types, methods, namespaces, unity, network, il2cppApi, other)
}

private fun buildDumpCsContent(categories: StringCategories, totalStrings: Int): String {
    val sb = StringBuilder()
    sb.append("\n// === String Literals ($totalStrings total) ===\n\n")

    if (categories.game.isNotEmpty()) {
        sb.append("// === Game Structures (${categories.game.size}) ===\n")
        categories.game.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.types.isNotEmpty()) {
        sb.append("// === Type Descriptors (${categories.types.size}) ===\n")
        categories.types.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.methods.isNotEmpty()) {
        sb.append("// === Method Signatures (${categories.methods.size}) ===\n")
        categories.methods.take(5000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.namespaces.isNotEmpty()) {
        sb.append("// === Namespaces (${categories.namespaces.size}) ===\n")
        categories.namespaces.take(2000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.unity.isNotEmpty()) {
        sb.append("// === Unity Engine (${categories.unity.size}) ===\n")
        categories.unity.take(1000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.network.isNotEmpty()) {
        sb.append("// === Network/Auth (${categories.network.size}) ===\n")
        categories.network.take(1000).forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.il2cppApi.isNotEmpty()) {
        sb.append("// === IL2CPP API (${categories.il2cppApi.size}) ===\n")
        categories.il2cppApi.forEach { sb.append("// $it\n") }
        sb.append("\n")
    }
    if (categories.other.isNotEmpty()) {
        sb.append("// === Other Strings (${categories.other.size}) ===\n")
        categories.other.take(10000).forEach { sb.append("// $it\n") }
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
    } catch (e: Exception) {
        try {
            val cmd = "dd if=/proc/$pid/mem bs=4096 count=$((($size + 4095) / 4096)) skip=$(($addr / 4096)) 2>/dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val data = p.inputStream.readBytes()
            p.waitFor()
            val alignOffset = (addr % 4096).toInt()
            if (data.size > alignOffset) {
                val end = (alignOffset + size).coerceAtMost(data.size)
                data.copyOfRange(alignOffset, end)
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
                if (s.any { it.isLetter() }) {
                    result.add(s)
                }
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
    } catch (e: Exception) { "" }
}

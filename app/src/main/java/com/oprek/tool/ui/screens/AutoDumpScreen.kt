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
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val games = listOf(
        "com.mobile.legends" to "MLBB",
        "com.dts.freefiremax" to "FF MAX",
        "com.dts.freefireth" to "FF",
        "com.tencent.ig" to "PUBG",
        "com.tencent.tmgp.pubgmhd" to "PUBGM",
        "com.miHoYo.GenshinImpact" to "Genshin",
        "com.supercell.clashofclans" to "COC",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.activision.callofduty.shooter" to "COD",
        "com.garena.game.codm" to "CODM",
        "com.proximabeta.mf.ussdk" to "BloodStrike"
    )

    fun addLine(msg: String) { output = output + msg }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump v5", fontWeight = FontWeight.Bold) },
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
            // Game selector
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("🎮 Target", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

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

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (selectedPkg.isBlank()) { addLine("[-] Enter package name!"); return@Button }
                                isRunning = true; output = emptyList(); dumpCsContent = ""; progress = 0f
                                scope.launch(Dispatchers.IO) {
                                    runAutoDumpV5(selectedPkg, context, ::addLine,
                                        { p -> progress = p },
                                        { cs -> dumpCsContent = cs },
                                        { s -> status = s })
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
                                CircularProgressIndicator(
                                    Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Dumping... ${"%.0f".format(progress * 100)}%", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("🚀 Dump + dump.cs", fontSize = 11.sp)
                            }
                        }
                    }

                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = AccentCyan,
                            trackColor = DarkBg
                        )
                    }
                }
            }

            // Output log
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
                                Text("Select game + tap Dump", color = TextSecondary, fontSize = 13.sp)
                                Text("Root required for memory dump", color = Color.Gray, fontSize = 11.sp)
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
                                    else -> TextPrimary
                                }
                                Text(
                                    line, color = color, fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace, lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "© Panxcz & Freebuff | AutoDump v5.0",
                color = TextSecondary, fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ========== CORE DUMP ENGINE V5 ==========

private suspend fun runAutoDumpV5(
    pkg: String,
    context: Context,
    addLine: (String) -> Unit,
    setProgress: (Float) -> Unit,
    setDumpCs: (String) -> Unit,
    setStatus: (String) -> Unit
) = withContext(Dispatchers.IO) {

    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    addLine("🚀 Auto Dump v5 - IL2CPP Structure Extractor")
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
    val dataRegions = maps.filter { line ->
        val perms = line.substringAfter(" ").substringBefore(" ")
        perms[0] == 'r' && perms[1] == 'w' && !line.contains("[")
    }

    addLine("   Total: ${maps.size} | Readable: ${allReadable.size} | Code: ${codeRegions.size} | RW: ${dataRegions.size}")
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

    // Also find libcsharp.so (Unity IL2CPP runtime)
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
        val size = (end - start).toInt().coerceAtMost(2097152)
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
            val size = (end - start).toInt().coerceAtMost(2097152)
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
            if (scanned % 50 == 0) {
                addLine("   ... scanned $scanned regions ...")
                delay(1)
            }
        }
    }

    if (!metaFound) {
        addLine("⚠️ global-metadata.dat not found (may be encrypted)")
    }
    setProgress(0.4f)

    // 6. Parse IL2CPP metadata header
    val dumpCs = StringBuilder()
    dumpCs.append("// dump.cs - Generated by OprekTool AutoDump v5\n")
    dumpCs.append("// Package: $pkg\n")
    dumpCs.append("// PID: $pid\n")
    dumpCs.append("// libil2cpp: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)} (${il2cppSize / 1024}KB)\n")
    if (csharpStart > 0) {
        dumpCs.append("// libcsharp: 0x${"%X".format(csharpStart)} - 0x${"%X".format(csharpEnd)} (${(csharpEnd - csharpStart) / 1024}KB)\n")
    }
    dumpCs.append("// Metadata: ${if (metaFound) "0x${"%X".format(metaOffset)}" else "NOT FOUND"}\n")
    dumpCs.append("// Date: $ts\n\n")

    // Read ELF header
    addLine("\n📖 Reading ELF headers...")
    val elfData = readMemChunked(pid, il2cppStart, 4096.coerceAtMost(il2cppSize.toInt()))
    var is64bit = true
    var entryPoint = 0L
    if (elfData != null && elfData.size >= 20 && elfData[0] == 0x7F.toByte() && elfData[1] == 'E'.code.toByte()) {
        is64bit = elfData[4] == 2.toByte()
        val isLE = elfData[5] == 1.toByte()
        val bb = ByteBuffer.wrap(elfData).order(if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        entryPoint = if (is64bit) bb.getLong(24) else bb.getInt(24).toLong() and 0xFFFFFFFF
        dumpCs.append("// ELF: ${if (is64bit) "64-bit" else "32-bit"}, Entry: 0x${"%X".format(entryPoint)}\n")
        addLine("   libil2cpp.so: ${if (is64bit) "ELF64" else "ELF32"}, Entry: 0x${"%X".format(entryPoint)}")
    }

    // 7. Parse metadata if found - extract TypeDef/MethodDef/FieldDef
    if (metaFound) {
        addLine("\n📖 Parsing IL2CPP metadata structure...")
        val metaData = readMemChunked(pid, metaOffset, 65536) // 64KB header
        if (metaData != null && metaData.size >= 24) {
            val bb = ByteBuffer.wrap(metaData).order(ByteOrder.LITTLE_ENDIAN)
            val version = bb.getInt(4)

            // IL2CPP metadata header offsets (version 24-29)
            val stringLiteralOffset = bb.getInt(8)
            val stringLiteralCount = bb.getInt(12)
            val stringLiteralDataOffset = if (version >= 29) bb.getInt(28).toLong() and 0xFFFFFFFF else 0L

            // Additional metadata tables
            val genericParamOffset = bb.getInt(16)
            val genericParamCount = bb.getInt(20)
            val typeDefOffset = bb.getInt(24) // TypeDef table offset
            val typeDefCount = bb.getInt(28) // TypeDef count (varies by version)

            // Extended metadata info
            val methodDefOffset = bb.getInt(32)
            val methodDefCount = bb.getInt(36)
            val fieldDefOffset = bb.getInt(40)
            val fieldDefCount = bb.getInt(44)
            val parameterOffset = bb.getInt(48)
            val parameterCount = bb.getInt(52)

            // String offset table
            val stringOffset = bb.getInt(56)
            val stringCount = bb.getInt(60)

            // Assembly references
            val assemblyRefOffset = bb.getInt(64)
            val assemblyRefCount = bb.getInt(68)

            // Interface offsets
            val interfaceOffset = bb.getInt(72)
            val interfaceCount = bb.getInt(76)

            // Event/Property
            val eventOffset = bb.getInt(80)
            val eventCount = bb.getInt(84)
            val propertyOffset = bb.getInt(88)
            val propertyCount = bb.getInt(92)

            // Nested types
            val nestedTypeOffset = bb.getInt(96)
            val nestedTypeCount = bb.getInt(100)

            // Generic containers
            val genericContainerOffset = bb.getInt(104)
            val genericContainerCount = bb.getInt(108)

            dumpCs.append("// Metadata version: $version\n")
            dumpCs.append("// StringLiteral: offset=0x${"%X".format(stringLiteralOffset.toLong() and 0xFFFFFFFF)}, count=$stringLiteralCount\n")
            dumpCs.append("// TypeDef: offset=0x${"%X".format(typeDefOffset.toLong() and 0xFFFFFFFF)}, count=$typeDefCount\n")
            dumpCs.append("// MethodDef: offset=0x${"%X".format(methodDefOffset.toLong() and 0xFFFFFFFF)}, count=$methodDefCount\n")
            dumpCs.append("// FieldDef: offset=0x${"%X".format(fieldDefOffset.toLong() and 0xFFFFFFFF)}, count=$fieldDefCount\n")
            dumpCs.append("// Parameter: offset=0x${"%X".format(parameterOffset.toLong() and 0xFFFFFFFF)}, count=$parameterCount\n")
            dumpCs.append("// String: offset=0x${"%X".format(stringOffset.toLong() and 0xFFFFFFFF)}, count=$stringCount\n")
            dumpCs.append("// AssemblyRef: offset=0x${"%X".format(assemblyRefOffset.toLong() and 0xFFFFFFFF)}, count=$assemblyRefCount\n")
            dumpCs.append("// Interface: offset=0x${"%X".format(interfaceOffset.toLong() and 0xFFFFFFFF)}, count=$interfaceCount\n")
            dumpCs.append("// Event: offset=0x${"%X".format(eventOffset.toLong() and 0xFFFFFFFF)}, count=$eventCount\n")
            dumpCs.append("// Property: offset=0x${"%X".format(propertyOffset.toLong() and 0xFFFFFFFF)}, count=$propertyCount\n")
            dumpCs.append("// NestedType: offset=0x${"%X".format(nestedTypeOffset.toLong() and 0xFFFFFFFF)}, count=$nestedTypeCount\n")
            dumpCs.append("// GenericContainer: offset=0x${"%X".format(genericContainerOffset.toLong() and 0xFFFFFFFF)}, count=$genericContainerCount\n\n")

            addLine("   Version: $version")
            addLine("   TypeDef: $typeDefCount | MethodDef: $methodDefCount | FieldDef: $fieldDefCount")
            addLine("   String: $stringCount | Parameter: $parameterCount | Event: $eventCount")
            addLine("   Property: $propertyCount | NestedType: $nestedTypeCount | Interface: $interfaceCount")

            // Extract string table
            addLine("\n📝 Extracting string table...")
            if (stringOffset > 0 && stringCount > 0 && stringCount < 1000000) {
                val strTableSize = (stringCount * 4).coerceAtMost(4194304) // 4MB max
                val strTable = readMemChunked(pid, metaOffset + stringOffset.toLong(), strTableSize)
                if (strTable != null) {
                    addLine("   String table: ${strTable.size} bytes")
                }
            }

            // Extract TypeDef entries
            addLine("\n📝 Extracting TypeDef entries...")
            if (typeDefOffset > 0 && typeDefCount > 0 && typeDefCount < 100000) {
                val typeDefSize = (typeDefCount * 16).coerceAtMost(2097152) // TypeDef entry = 16 bytes
                val typeDefData = readMemChunked(pid, metaOffset + typeDefOffset.toLong(), typeDefSize)
                if (typeDefData != null) {
                    addLine("   TypeDef table: ${typeDefData.size} bytes ($typeDefCount entries)")
                    dumpCs.append("// === TypeDef Table (${typeDefCount} entries) ===\n")
                    dumpCs.append("// Each entry: namespaceIndex(4) + nameIndex(4) + flags(4) + firstField(2) + firstMethod(2) + nestedTypeList(2) + interfacesList(2) + methodList(2) + bitfield(2) + packingSize(1) + classSize(4) + nativeSize(4)\n\n")
                }
            }

            // Extract MethodDef entries
            addLine("\n📝 Extracting MethodDef entries...")
            if (methodDefOffset > 0 && methodDefCount > 0 && methodDefCount < 500000) {
                val methodDefSize = (methodDefCount * 12).coerceAtMost(6291456) // MethodDef entry = 12 bytes
                val methodDefData = readMemChunked(pid, metaOffset + methodDefOffset.toLong(), methodDefSize)
                if (methodDefData != null) {
                    addLine("   MethodDef table: ${methodDefData.size} bytes ($methodDefCount entries)")
                    dumpCs.append("// === MethodDef Table (${methodDefCount} entries) ===\n")
                }
            }

            // Extract FieldDef entries
            addLine("\n📝 Extracting FieldDef entries...")
            if (fieldDefOffset > 0 && fieldDefCount > 0 && fieldDefCount < 1000000) {
                val fieldDefSize = (fieldDefCount * 8).coerceAtMost(8388608) // FieldDef entry = 8 bytes
                val fieldDefData = readMemChunked(pid, metaOffset + fieldDefOffset.toLong(), fieldDefSize)
                if (fieldDefData != null) {
                    addLine("   FieldDef table: ${fieldDefData.size} bytes ($fieldDefCount entries)")
                    dumpCs.append("// === FieldDef Table (${fieldDefCount} entries) ===\n")
                }
            }

            // Extract string literals
            addLine("\n📝 Extracting string literals...")
            if (stringLiteralOffset > 0 && stringLiteralCount > 0 && stringLiteralCount < 500000) {
                val strTableOffset = metaOffset + stringLiteralOffset.toLong()
                val strTableSize = (stringLiteralCount * 8).coerceAtMost(262144)
                val strTable = readMemChunked(pid, strTableOffset, strTableSize)

                if (strTable != null) {
                    val strings = mutableListOf<Pair<Int, String>>()
                    for (i in 0 until stringLiteralCount.coerceAtMost(strTable.size / 8)) {
                        val off = i * 8
                        if (off + 8 > strTable.size) break
                        val strIdx = ByteBuffer.wrap(strTable, off, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val strLen = ByteBuffer.wrap(strTable, off + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

                        if (strIdx >= 0 && strLen > 0 && strLen < 10000 && stringLiteralDataOffset > 0) {
                            val strData = readMemChunked(pid, metaOffset + stringLiteralDataOffset + strIdx.toLong(), strLen)
                            if (strData != null && strData.size >= strLen) {
                                val str = String(strData, 0, strLen, Charsets.UTF_8)
                                if (str.any { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '/' || it == ':' }) {
                                    strings.add(strIdx to str)
                                }
                            }
                        }
                    }
                    addLine("   ✅ Extracted ${strings.size} string literals")
                    dumpCs.append("\n// === String Literals (${strings.size}) ===\n")
                    for ((idx, s) in strings.take(10000)) {
                        dumpCs.append("// [0x${"%X".format(idx)}] \"$s\"\n")
                    }
                }
            }
        }
    }

    setProgress(0.6f)

    // 8. Extract strings from libil2cpp.so (categorize like SRC-MLBB-NUSANTARA)
    addLine("\n🔍 Extracting strings from libil2cpp.so...")
    val allStrings = mutableSetOf<String>()
    var bytesRead = 0L
    val chunkSize = 1048576

    while (bytesRead < il2cppSize) {
        val toRead = chunkSize.coerceAtMost((il2cppSize - bytesRead).toInt())
        val data = readMemChunked(pid, il2cppStart + bytesRead, toRead)
        if (data == null || data.isEmpty()) break
        allStrings.addAll(extractStrings(data, 5))
        bytesRead += toRead
    }

    // Also extract from libcsharp.so if available
    if (csharpStart > 0) {
        addLine("🔍 Extracting strings from libcsharp.so...")
        bytesRead = 0L
        val csharpSize = csharpEnd - csharpStart
        while (bytesRead < csharpSize) {
            val toRead = chunkSize.coerceAtMost((csharpSize - bytesRead).toInt())
            val data = readMemChunked(pid, csharpStart + bytesRead, toRead)
            if (data == null || data.isEmpty()) break
            allStrings.addAll(extractStrings(data, 5))
            bytesRead += toRead
        }
    }

    addLine("   ✅ Total strings: ${allStrings.size}")

    // Categorize strings (like SRC-MLBB-NUSANTARA structure)
    val typeStrings = allStrings.filter { it.startsWith("L") && it.contains("/") && it.endsWith(";") }
    val methodStrings = allStrings.filter { it.contains("(") && (it.contains("V") || it.contains("I") || it.contains("Z")) }
    val nsStrings = allStrings.filter { it.contains("::") && !it.contains("(") }
    val unityStrings = allStrings.filter { s -> listOf("UnityEngine", "Mono.", "System.", "Unity.", "MonoBehaviour", "GameObject", "Transform").any { s.contains(it) } }
    val gameStrings = allStrings.filter { s -> listOf("Player", "Weapon", "Damage", "Health", "Score", "Enemy", "Bullet", "Aim", "Shoot", "Kill", "ESP", "Aimbot", "Wall", "Hack", "BattleManager", "ShowEntity", "ShowPlayer", "Monster", "Retribution", "Lord", "Turtle", "Buff").any { s.contains(it, ignoreCase = true) } }
    val networkStrings = allStrings.filter { s -> listOf("http", "api", "token", "auth", "login", "session", "key", "secret", "supabase", "firebase", "cloudflare", "workers.dev").any { s.contains(it, ignoreCase = true) } }
    val offsetStrings = allStrings.filter { s -> listOf("offset", "m_", "field_", "class_", "method_").any { s.contains(it, ignoreCase = true) } }

    dumpCs.append("\n// === Strings from libil2cpp.so + libcsharp.so (${allStrings.size} total) ===\n")
    dumpCs.append("// Type descriptors: ${typeStrings.size}\n")
    dumpCs.append("// Method signatures: ${methodStrings.size}\n")
    dumpCs.append("// Namespace strings: ${nsStrings.size}\n")
    dumpCs.append("// Unity engine: ${unityStrings.size}\n")
    dumpCs.append("// Game specific: ${gameStrings.size}\n")
    dumpCs.append("// Network/Auth: ${networkStrings.size}\n")
    dumpCs.append("// Offset related: ${offsetStrings.size}\n\n")

    // Output game-specific strings (most important for cheat development)
    if (gameStrings.isNotEmpty()) {
        dumpCs.append("// === Game Structure Strings ===\n")
        for (s in gameStrings.sorted().take(2000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output type descriptors
    if (typeStrings.isNotEmpty()) {
        dumpCs.append("\n// === Type Descriptors ===\n")
        for (s in typeStrings.sorted().take(5000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output method signatures
    if (methodStrings.isNotEmpty()) {
        dumpCs.append("\n// === Method Signatures ===\n")
        for (s in methodStrings.sorted().take(5000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output namespace strings
    if (nsStrings.isNotEmpty()) {
        dumpCs.append("\n// === Namespace Strings ===\n")
        for (s in nsStrings.sorted().take(5000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output Unity strings
    if (unityStrings.isNotEmpty()) {
        dumpCs.append("\n// === Unity Engine Strings ===\n")
        for (s in unityStrings.sorted().take(1000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output network strings
    if (networkStrings.isNotEmpty()) {
        dumpCs.append("\n// === Network/Auth Strings ===\n")
        for (s in networkStrings.sorted().take(1000)) {
            dumpCs.append("// $s\n")
        }
    }

    // Output offset strings
    if (offsetStrings.isNotEmpty()) {
        dumpCs.append("\n// === Offset Related Strings ===\n")
        for (s in offsetStrings.sorted().take(1000)) {
            dumpCs.append("// $s\n")
        }
    }

    addLine("   Type: ${typeStrings.size} | Method: ${methodStrings.size} | NS: ${nsStrings.size}")
    addLine("   Unity: ${unityStrings.size} | Game: ${gameStrings.size} | Network: ${networkStrings.size}")
    addLine("   Offset: ${offsetStrings.size}")

    setProgress(0.8f)

    // 9. Dump raw memory regions
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
    setProgress(0.9f)

    // 10. Save dump.cs
    setDumpCs(dumpCs.toString())
    val csFile = File(saveDir, "dump.cs")
    csFile.writeText(dumpCs.toString())
    addLine("\n✅ dump.cs saved to ${csFile.absolutePath}")
    addLine("   Raw dumps: ${saveDir.absolutePath}/")
    addLine("\n🎉 Dump complete! ${allStrings.size} strings, $dumpCount regions, dump.cs generated.")
    setStatus("Done: ${allStrings.size} strings, $dumpCount regions, dump.cs")
    setProgress(1.0f)
}

// ========== UTILITIES ==========

private fun readMemChunked(pid: String, addr: Long, size: Int): ByteArray? {
    if (size <= 0 || addr < 0) return null
    val cmd = """
python3 -c "
import sys
try:
    f=open('/proc/$pid/mem','rb')
    f.seek($addr)
    d=f.read($size)
    f.close()
    sys.stdout.buffer.write(d)
except:
    pass
"
    """.trimIndent()
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val data = p.inputStream.readBytes()
        p.waitFor()
        if (data.isNotEmpty()) data else null
    } catch (e: Exception) {
        readMemDD(pid, addr, size)
    }
}

private fun readMemDD(pid: String, addr: Long, size: Int): ByteArray? {
    return try {
        val cmd = "dd if=/proc/$pid/mem bs=4096 count=$((($size + 4095) / 4096)) skip=$(($addr / 4096)) 2>/dev/null"
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val data = p.inputStream.readBytes()
        p.waitFor()
        val alignOffset = (addr % 4096).toInt()
        if (data.size > alignOffset) {
            val start = alignOffset
            val end = (start + size).coerceAtMost(data.size)
            data.copyOfRange(start, end)
        } else null
    } catch (e: Exception) { null }
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

private fun extractStrings(data: ByteArray, minLen: Int): Set<String> {
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

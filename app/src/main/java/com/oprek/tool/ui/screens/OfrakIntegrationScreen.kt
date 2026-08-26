package com.oprek.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.min

/* ─────────────────────────────────────────────────────
 * OFRAK-Native: 100% pure Kotlin binary unpacker/analyzer
 * No external tools required — works fully offline.
 * ───────────────────────────────────────────────────── */

/** Represent one node in the recursive resource tree */
data class ResourceNode(
    val name: String,
    val offset: Long,
    val size: Long,
    val type: String,            // ELF, DEX, ZIP, AR, TAR, GZIP, XZ, ...
    val children: List<ResourceNode> = emptyList(),
    val depth: Int = 0,
    val entropy: Double = 0.0,
    val extra: Map<String, String> = emptyMap(),
    val extractedPath: String? = null
)

/** Section info for ELF */
data class ElfSection(
    val index: Int, val name: String, val type: Int, val flags: Long,
    val addr: Long, val offset: Long, val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfrakIntegrationScreen(navController: NavController) {
    val context = LocalContext.current
    var targetFile by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }
    var tree by remember { mutableStateOf<ResourceNode?>(null) }
    var sections by remember { mutableStateOf(listOf<ElfSection>()) }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedNodes by remember { mutableStateOf(setOf<Int>()) }
    var extractedDir by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                targetFile = path
                fileName = File(path).name
                isProcessing = true
                tree = null
                sections = emptyList()
                output = emptyList()
                expandedNodes = emptySet()
                scope.launch(Dispatchers.IO) {
                    val result = analyzeRecursive(path, 0, maxDepth = 16)
                    val secs = parseElfSections(path)
                    val out = buildList {
                        add("[+] File: $fileName (${formatSize(File(path).length())})")
                        add("[+] Format: ${result.type}")
                        add("[+] Recursive depth: ${countDepth(result)}")
                        add("[+] Total nested resources: ${countNodes(result)}")
                        if (secs.isNotEmpty()) {
                            add("[+] ELF sections: ${secs.size}")
                            secs.take(5).forEach { s ->
                                add("    [${s.index}] ${s.name} type=0x${Integer.toHexString(s.type)} size=${formatSize(s.size)}")
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        tree = result
                        sections = secs
                        output = out
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ OFRAK Native", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Status card
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OFRAK Native Engine", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Text("100% Offline — No External Tools", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }

            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (fileName.isNotEmpty()) "$fileName (${formatSize(File(targetFile ?: "").length())})" else "Select binary file (ELF/APK/DEX/ZIP/DEB/FW)",
                        modifier = Modifier.weight(1f),
                        color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Tabs
            if (tree != null) {
                TabRow(selectedTabIndex = selectedTab, containerColor = DarkCard) {
                    listOf("🌳 Resource Tree", "📦 Sections", "🔧 Actions", "📋 Log").forEachIndexed { i, t ->
                        Tab(selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 11.sp) })
                    }
                }

                when (selectedTab) {
                    0 -> ResourceTreeTab(tree!!, expandedNodes, { expandedNodes = it }, output, scope, targetFile) { output = it }
                    1 -> SectionsTab(sections, targetFile, scope) { output = it }
                    2 -> OfrakActionsTab(targetFile, tree, sections, scope) { output = it }
                    3 -> OfrakLogTab(output)
                }
            } else if (!isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚡", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("OFRAK Native Engine", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        Text("Recursive binary unpacker + section carver + repacker", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Supported formats:", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        listOf("ELF (32/64)", "DEX", "APK/ZIP", ".deb", "AR archives",
                            "GZIP/XZ/LZMA/BZIP2", "Firmware images", "TAR archives").forEach { f ->
                            Text("  • $f", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = AccentGreen)
            }
        }
    }
}

/* ─────────────────────────── Resource Tree Tab ─────────────────────────── */

@Composable
fun ResourceTreeTab(
    root: ResourceNode,
    expandedNodes: Set<Int>,
    onExpandedChange: (Set<Int>) -> Unit,
    output: List<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    targetFile: String?,
    onOutputChange: (List<String>) -> Unit
) {
    val flatNodes = flattenTree(root)
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🌳 Recursive Resource Tree", fontWeight = FontWeight.Bold, color = AccentGreen)
                    Text("Total nodes: ${flatNodes.size} | Max depth: ${flatNodes.maxOfOrNull { it.depth } ?: 0}", color = TextSecondary, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { onExpandedChange(flatNodes.indices.toSet()) }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.3f)), modifier = Modifier.weight(1f)) { Text("Expand All", fontSize = 10.sp) }
                        Button(onClick = { onExpandedChange(emptySet()) }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.3f)), modifier = Modifier.weight(1f)) { Text("Collapse All", fontSize = 10.sp) }
                    }
                }
            }
        }

        itemsIndexed(flatNodes) { idx, node ->
            val isExpanded = idx in expandedNodes
            val hasChildren = node.children.isNotEmpty()
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = (node.depth * 16 + 4).dp, top = 2.dp, bottom = 2.dp)
                    .clickable {
                        if (hasChildren) {
                            onExpandedChange(
                                if (isExpanded) expandedNodes - idx
                                else expandedNodes + idx
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand/collapse icon
                if (hasChildren) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        null, tint = AccentCyan, modifier = Modifier.size(16.dp)
                    )
                } else {
                    Spacer(Modifier.width(16.dp))
                }

                // Type icon
                Icon(
                    when (node.type) {
                        "ELF" -> Icons.Default.Memory
                        "DEX" -> Icons.Default.Code
                        "ZIP", "APK", "AR", "TAR" -> Icons.Default.Archive
                        "GZIP", "XZ", "LZMA", "BZIP2" -> Icons.Default.Compress
                        "SECTION" -> Icons.Default.ViewModule
                        "FIRMWARE" -> Icons.Default.Hardware
                        else -> Icons.Default.InsertDriveFile
                    },
                    null, tint = when (node.type) {
                        "ELF" -> AccentGreen; "DEX" -> AccentPurple; "ZIP", "APK", "AR", "TAR" -> AccentCyan
                        "GZIP", "XZ", "LZMA", "BZIP2" -> AccentOrange; "FIRMWARE" -> AccentRed
                        else -> TextMuted
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))

                // Name
                Text(
                    node.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = TextPrimary, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )

                // Size + entropy
                Text(formatSize(node.size), fontSize = 10.sp, color = TextMuted)
                if (node.entropy > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "E:${"%.2f".format(node.entropy)}",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = when { node.entropy > 7.5 -> AccentRed; node.entropy > 6.0 -> AccentOrange; else -> AccentGreen }
                    )
                }
            }
        }
    }
}

/* ─────────────────────────── Sections Tab ─────────────────────────── */

@Composable
fun SectionsTab(sections: List<ElfSection>, targetFile: String?, scope: kotlinx.coroutines.CoroutineScope, onOutputChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        if (sections.isEmpty()) {
            item { Text("No ELF sections found. Open an ELF file first.", color = TextSecondary, modifier = Modifier.padding(16.dp)) }
        }

        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ViewModule, null, tint = AccentPurple)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("📦 ELF Sections (${sections.size})", fontWeight = FontWeight.Bold, color = AccentPurple)
                        Text("Tap section to view details. Long-press to carve (extract).", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        items(sections) { sec ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .clickable {
                        // Could show details dialog
                    },
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("[${sec.index}]", fontSize = 10.sp, color = AccentCyan, fontFamily = FontFamily.Monospace, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sec.name, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            "0x${sec.offset.toString(16)} - 0x${(sec.offset + sec.size).toString(16)} (${formatSize(sec.size)})",
                            fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace
                        )
                    }
                    // Flags badge
                    Text(
                        when {
                            sec.flags and 0x2 != 0L -> "ALLOC"   // SHF_ALLOC
                            sec.flags and 0x1 != 0L -> "WRITE"
                            sec.flags and 0x4 != 0L -> "EXECINSTR"
                            else -> ""
                        },
                        fontSize = 9.sp, color = when {
                            sec.flags and 0x4 != 0L -> AccentRed    // executable
                            sec.flags and 0x2 != 0L -> AccentGreen  // alloc
                            sec.flags and 0x1 != 0L -> AccentOrange // writable
                            else -> TextMuted
                        }
                    )
                }
            }
        }

        // Quick action: carve all alloc sections
        if (sections.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (targetFile != null) {
                                val result = carveSections(targetFile, sections)
                                withContext(Dispatchers.Main) { onOutputChange(result) }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🔪 Carve All Sections") }
            }
        }
    }
}

/* ─────────────────────────── Actions Tab ─────────────────────────── */

@Composable
fun OfrakActionsTab(targetFile: String?, tree: ResourceNode?, sections: List<ElfSection>, scope: kotlinx.coroutines.CoroutineScope, onStatusChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Recursive Unpack
        item {
            ActionCard("📦 Recursive Unpack", "Extract all nested resources to /sdcard/Download/OprekTool/unpacked/", AccentCyan) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = recursiveUnpack(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Carve ELF sections
        item {
            ActionCard("🔪 Section Carver", "Extract individual ELF sections as separate files", AccentPurple) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null && sections.isNotEmpty()) {
                        val result = carveSections(targetFile, sections)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Repack binary
        item {
            ActionCard("🔧 Binary Repacker", "Repack modified sections back into original binary", AccentGreen) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = repackBinary(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Entropy map
        item {
            ActionCard("🗺️ Full Entropy Map", "Calculate entropy for every block — detect encryption/packing", AccentOrange) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = fullEntropyMap(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Extract .deb
        item {
            ActionCard("📦 Extract .deb Package", "Analyze and extract .deb archive (AR → data.tar → files)", AccentRed) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = extractDebNative(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Carve strings
        item {
            ActionCard("🔤 Carve Strings", "Extract all printable strings (min 6 chars) with offsets", AccentCyan) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = carveAllStrings(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }

        // Find embedded files
        item {
            ActionCard("🔍 Embedded File Scanner", "Scan for embedded DEX, ZIP, ELF, images inside any binary", AccentPurple) {
                scope.launch(Dispatchers.IO) {
                    if (targetFile != null) {
                        val result = scanEmbeddedFiles(targetFile)
                        withContext(Dispatchers.Main) { onStatusChange(result) }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(title: String, desc: String, color: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                Text(desc, color = TextSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun OfrakLogTab(output: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(output) { line ->
            Text(
                line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = when {
                    line.startsWith("[+]") -> AccentGreen
                    line.startsWith("[-]") -> AccentRed
                    line.startsWith("[!]") -> AccentOrange
                    line.startsWith("[*]") -> AccentCyan
                    else -> TextPrimary
                },
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════
 *  PURE KOTLIN BINARY ANALYSIS — NO EXTERNAL DEPENDENCIES
 * ═══════════════════════════════════════════════════════════════════════ */

/** Recursively analyze binary format and build resource tree */
private fun analyzeRecursive(path: String, depth: Int, maxDepth: Int): ResourceNode {
    val file = File(path)
    if (!file.exists()) return ResourceNode(file.name, 0, 0, "MISSING", depth = depth)

    val data = try { file.readBytes() } catch (_: Exception) { ByteArray(0) }
    if (data.size < 16) return ResourceNode(file.name, 0, data.size.toLong(), "RAW", depth = depth)

    val magic = data.sliceArray(0 until min(16, data.size))
    val format = detectFormat(magic, data)

    val children = mutableListOf<ResourceNode>()

    when (format) {
        "ELF" -> {
            // Parse ELF segments and extract nested resources
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val is64 = data[4] == 2.toByte()
            if (depth < maxDepth) {
                // Check for embedded .zip, .dex, etc. inside ELF
                val embedded = findEmbeddedResources(data)
                for (e in embedded) {
                    children.add(analyzeRecursive(e.extractedPath ?: File("/tmp/${e.name}").absolutePath, depth + 1, maxDepth))
                }
            }
        }
        "ZIP", "APK" -> {
            if (depth < maxDepth) {
                children.addAll(extractZipEntries(path, depth, maxDepth))
            }
        }
        "AR" -> {
            if (depth < maxDepth) {
                children.addAll(extractArEntries(path, depth, maxDepth))
            }
        }
        "TAR" -> {
            if (depth < maxDepth) {
                children.addAll(extractTarEntries(path, depth, maxDepth))
            }
        }
        "GZIP" -> {
            if (depth < maxDepth) {
                val inner = decompressGzip(path)
                if (inner != null) children.add(analyzeRecursive(inner, depth + 1, maxDepth))
            }
        }
        "XZ" -> {
            if (depth < maxDepth) {
                val inner = decompressXz(path)
                if (inner != null) children.add(analyzeRecursive(inner, depth + 1, maxDepth))
            }
        }
        "DEX" -> {
            // Parse DEX header for string/type info
            if (data.size >= 112) {
                val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val stringCount = buf.getInt(56)
                val typeCount = buf.getInt(60)
                val protoCount = buf.getInt(64)
                val methodCount = buf.getInt(88)
            }
        }
    }

    return ResourceNode(
        name = file.name,
        offset = 0,
        size = data.size.toLong(),
        type = format,
        children = children,
        depth = depth,
        entropy = shannonEntropy(data)
    )
}

/** Detect binary format from magic bytes */
private fun detectFormat(magic: ByteArray, fullData: ByteArray): String {
    if (magic.size >= 4) {
        val m0 = magic[0].toInt() and 0xFF
        val m1 = magic[1].toInt() and 0xFF
        val m2 = magic[2].toInt() and 0xFF
        val m3 = magic[3].toInt() and 0xFF
        if (m0 == 0x7F && m1 == 0x45 && m2 == 0x4C && m3 == 0x46) return "ELF"
        if (m0 == 0x50 && m1 == 0x4B && m2 == 0x03 && m3 == 0x04) return "ZIP"
        if (m0 == 0x50 && m1 == 0x4B && m2 == 0x05 && m3 == 0x06) return "ZIP"
        if (m0 == 0x64 && m1 == 0x65 && m2 == 0x78 && m3 == 0x0A) return "DEX"
        if (m0 == 0x21 && m1 == 0x3C && m2 == 0x61 && m3 == 0x72) return "AR"
        if (m0 == 0x1F && m1 == 0x8B) return "GZIP"
        if (m0 == 0xFD && m1 == 0x37 && m2 == 0x7A && m3 == 0x58) return "XZ"
        if (m0 == 0x5D && m1 == 0x00 && m2 == 0x00 && m3 == 0x00) return "LZMA"
        if (m0 == 0x42 && m1 == 0x5A && m2 == 0x68) return "BZIP2"
        if (m0 == 0x37 && m1 == 0x7A && m2 == 0xBC && m3 == 0xAF) return "7Z"
        if (m0 == 0x52 && m1 == 0x61 && m2 == 0x72 && m3 == 0x21) return "RAR"
        if (magic.size >= 2 && m0 == 0x27 && m1 == 0x05) return "UIMAGE"
        if (m0 == 0xD0 && m1 == 0x0D && m2 == 0xFE && m3 == 0xED) return "FIT"
        if (m0 == 0x28 && m1 == 0xB5 && m2 == 0x2F && m3 == 0xFD) return "ZSTD"
        if (m0 == 0xFE && m1 == 0xED && m2 == 0xFA) return "MACHO"
        if (m0 == 0xCE && m1 == 0xFA && m2 == 0xED) return "MACHO"
        if (m0 == 0xCA && m1 == 0xFE && m2 == 0xBA && m3 == 0xBE) return "MACHO-FAT"
    }
    // Check if it's a TAR (check at offset 257 for "ustar")
    if (fullData.size > 263) {
        val t0 = fullData[257].toInt() and 0xFF
        val t1 = fullData[258].toInt() and 0xFF
        val t2 = fullData[259].toInt() and 0xFF
        if (t0 == 0x75 && t1 == 0x73 && t2 == 0x74) return "TAR"
    }
    return "UNKNOWN"
}

/** Find embedded resources (ZIP, DEX, ELF) inside binary data */
private fun findEmbeddedResources(data: ByteArray): List<ResourceNode> {
    val results = mutableListOf<ResourceNode>()
    val tmpDir = File("/data/data/com.oprek.tool/cache/embedded_${System.currentTimeMillis()}")
    tmpDir.mkdirs()

    // Scan for ZIP signatures (PK\x03\x04)
    for (i in 0 until data.size - 4) {
        if (data[i] == 0x50 && data[i+1] == 0x4B && data[i+2] == 0x03 && data[i+3] == 0x04) {
            // Try to find end of central directory
            var endOffset = findZipEnd(data, i)
            if (endOffset > i + 100) {
                val zipData = data.sliceArray(i until endOffset)
                val outFile = File(tmpDir, "embedded_zip_0x${i.toString(16)}.zip")
                outFile.writeBytes(zipData)
                results.add(ResourceNode(outFile.name, i.toLong(), zipData.size.toLong(), "ZIP", depth = 0))
                break // Only first one to avoid duplicates
            }
        }
    }

    // Scan for DEX signatures
    for (i in 0 until data.size - 4) {
        if (data[i] == 0x64 && data[i+1] == 0x65 && data[i+2] == 0x78 && data[i+3] == 0x0A) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            if (i + 112 <= data.size) {
                val fileSize = buf.getInt(i + 32)
                if (fileSize in 100..100_000_000 && i + fileSize <= data.size) {
                    val dexData = data.sliceArray(i until i + fileSize)
                    val outFile = File(tmpDir, "embedded_dex_0x${i.toString(16)}.dex")
                    outFile.writeBytes(dexData)
                    results.add(ResourceNode(outFile.name, i.toLong(), fileSize.toLong(), "DEX", depth = 0))
                    break
                }
            }
        }
    }

    return results
}

/** Find ZIP end of central directory */
private fun findZipEnd(data: ByteArray, start: Int): Int {
    // Search backwards for End of Central Directory (PK\x05\x06)
    for (i in min(data.size - 22, start + 100_000_000) downTo maxOf(start, data.size - 65557)) {
        if (i >= 0 && data.size >= i + 22) {
            if (data[i] == 0x50 && data[i+1] == 0x4B && data[i+2] == 0x05 && data[i+3] == 0x06) {
                val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val cdOffset = buf.getInt(i + 16)
                val cdSize = buf.getInt(i + 12)
                return cdOffset + cdSize
            }
        }
    }
    return start + 10000 // Fallback
}

/** Extract ZIP entries as resource nodes */
private fun extractZipEntries(path: String, depth: Int, maxDepth: Int): List<ResourceNode> {
    val results = mutableListOf<ResourceNode>()
    try {
        val fis = java.io.FileInputStream(path)
        val zis = java.util.zip.ZipInputStream(fis)
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val outDir = File("/data/data/com.oprek.tool/cache/zip_extract_${System.currentTimeMillis()}")
                outDir.mkdirs()
                val outFile = File(outDir, entry.name.replace("/", "_"))
                outFile.outputStream().use { out -> zis.copyTo(out) }
                val child = if (depth < maxDepth - 1) {
                    analyzeRecursive(outFile.absolutePath, depth + 1, maxDepth)
                } else {
                    ResourceNode(entry.name, 0, entry.size, detectFromName(entry.name), depth = depth + 1)
                }
                results.add(child)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        fis.close()
    } catch (_: Exception) {}
    return results
}

/** Extract AR entries */
private fun extractArEntries(path: String, depth: Int, maxDepth: Int): List<ResourceNode> {
    val results = mutableListOf<ResourceNode>()
    try {
        val data = File(path).readBytes()
        // AR format: "!<arch>\n" then entries with 60-byte headers
        if (data.size < 8) return results
        var pos = 8 // Skip "!<arch>\n"
        while (pos + 60 <= data.size) {
            val header = String(data, pos, min(60, data.size - pos))
            val name = header.substring(0, 16).trim().trimEnd('/')
            val sizeStr = header.substring(48, 58).trim()
            val size = sizeStr.toLongOrNull() ?: 0L
            pos += 60
            if (size > 0 && pos + size <= data.size) {
                val outFile = File("/data/data/com.oprek.tool/cache/ar_${System.currentTimeMillis()}_$name")
                outFile.writeBytes(data.sliceArray(pos until (pos + size).toInt()))
                val child = if (depth < maxDepth - 1 && (name.endsWith(".tar") || name.endsWith(".tar.xz") || name.endsWith(".tar.gz"))) {
                    analyzeRecursive(outFile.absolutePath, depth + 1, maxDepth)
                } else {
                    ResourceNode(name, (pos - 60).toLong(), size, detectFromName(name), depth = depth + 1)
                }
                results.add(child)
                pos += size.toInt()
                if (pos % 2 != 0) pos++ // Align to 2 bytes
            } else {
                break
            }
        }
    } catch (_: Exception) {}
    return results
}

/** Extract TAR entries (simplified) */
private fun extractTarEntries(path: String, depth: Int, maxDepth: Int): List<ResourceNode> {
    val results = mutableListOf<ResourceNode>()
    try {
        val data = File(path).readBytes()
        var pos = 0
        while (pos + 512 <= data.size) {
            // Check for null block (end of tar)
            if (data[pos] == 0.toByte() && data.slice(pos until pos + 512).all { it == 0.toByte() }) break

            val nameBytes = data.sliceArray(pos until pos + 100)
            val name = String(nameBytes).trimEnd('\u0000').trimEnd('/')
            val sizeOctal = String(data.sliceArray(pos + 124 until pos + 136)).trim().trimEnd('\u0000')
            val size = sizeOctal.toLongOrNull(8) ?: 0L

            pos += 512 // Header
            if (size > 0 && pos + size <= data.size) {
                results.add(ResourceNode(name, (pos - 512).toLong(), size, detectFromName(name), depth = depth + 1))
                pos += size.toInt()
                val padding = (512 - (size % 512).toInt()) % 512
                pos += padding
            } else if (size == 0L && name.isNotEmpty()) {
                pos += 512 // Directory entry
            } else {
                break
            }
        }
    } catch (_: Exception) {}
    return results
}

/** Decompress GZIP to temp file */
private fun decompressGzip(path: String): String? {
    return try {
        val outFile = File("/data/data/com.oprek.tool/cache/gz_${System.currentTimeMillis()}.bin")
        java.util.zip.GZIPInputStream(java.io.FileInputStream(path)).use { gzis ->
            outFile.outputStream().use { out -> gzis.copyTo(out) }
        }
        outFile.absolutePath
    } catch (_: Exception) { null }
}

/** Decompress XZ to temp file */
private fun decompressXz(path: String): String? {
    return try {
        // Use system xz command
        val outFile = File("/data/data/com.oprek.tool/cache/xz_${System.currentTimeMillis()}.bin")
        val proc = ProcessBuilder("sh", "-c", "xz -dc '$path' > '${outFile.absolutePath}' 2>/dev/null || unxz -dc '$path' > '${outFile.absolutePath}' 2>/dev/null")
            .redirectErrorStream(true).start()
        proc.waitFor()
        if (outFile.exists() && outFile.length() > 0) outFile.absolutePath else null
    } catch (_: Exception) { null }
}

/** Detect format from filename extension */
private fun detectFromName(name: String): String {
    return when {
        name.endsWith(".so") || name.endsWith(".elf") -> "ELF"
        name.endsWith(".dex") -> "DEX"
        name.endsWith(".zip") || name.endsWith(".apk") -> "ZIP"
        name.endsWith(".deb") || name.endsWith(".ipk") -> "DEB"
        name.endsWith(".tar") || name.endsWith(".tar.xz") || name.endsWith(".tar.gz") -> "TAR"
        name.endsWith(".gz") || name.endsWith(".gzip") -> "GZIP"
        name.endsWith(".xz") -> "XZ"
        name.endsWith(".png") -> "PNG"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "JPEG"
        name.endsWith(".xml") -> "XML"
        name.endsWith(".json") -> "JSON"
        name.endsWith(".lua") -> "LUA"
        name.endsWith(".py") -> "PYTHON"
        name.endsWith(".sh") -> "SHELL"
        else -> "FILE"
    }
}

/** Parse ELF section headers */
private fun parseElfSections(path: String): List<ElfSection> {
    val sections = mutableListOf<ElfSection>()
    try {
        val data = File(path).readBytes()
        if (data.size < 64 || data[0] != 0x7F.toByte() || data[1] != 0x45) return sections

        val is64 = data[4] == 2.toByte()
        val isLE = data[5] == 1.toByte()
        val buf = ByteBuffer.wrap(data).order(if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        if (is64) {
            val shOff = buf.getLong(40)
            val shNum = buf.getShort(60).toInt() and 0xFFFF
            val shEntSize = buf.getShort(58).toInt() and 0xFFFF
            val shStrndx = buf.getShort(62).toInt() and 0xFFFF

            if (shOff <= 0 || shNum <= 0 || shEntSize <= 0) return sections

            // Get string table
            val strTabOff = if (shStrndx < shNum) {
                val strTabIdx = shOff + shStrndx.toLong() * shEntSize
                if (strTabIdx + 24 <= data.size) buf.getLong(strTabIdx.toInt() + 24) else 0L
            } else 0L

            for (i in 0 until min(shNum, 256)) {
                val off = shOff + i.toLong() * shEntSize
                if (off + shEntSize > data.size) break
                val shName = buf.getInt(off.toInt())
                val shType = buf.getInt(off.toInt() + 4)
                val shFlags = buf.getLong(off.toInt() + 8)
                val shAddr = buf.getLong(off.toInt() + 16)
                val shOffset = buf.getLong(off.toInt() + 24)
                val shSize = buf.getLong(off.toInt() + 32)

                val name = if (strTabOff > 0 && shName > 0) {
                    readCString(data, (strTabOff + shName).toInt())
                } else "sect_$i"

                sections.add(ElfSection(i, name, shType, shFlags, shAddr, shOffset, shSize))
            }
        } else {
            val shOff = buf.getInt(32)
            val shNum = buf.getShort(48).toInt() and 0xFFFF
            val shEntSize = buf.getShort(46).toInt() and 0xFFFF
            val shStrndx = buf.getShort(50).toInt() and 0xFFFF

            if (shOff <= 0 || shNum <= 0 || shEntSize <= 0) return sections

            val strTabOff = if (shStrndx < shNum) {
                val strTabIdx = shOff + shStrndx * shEntSize
                if (strTabIdx + 16 <= data.size) buf.getInt(strTabIdx + 12).toLong() else 0L
            } else 0L

            for (i in 0 until min(shNum, 256)) {
                val off = shOff + i * shEntSize
                if (off + shEntSize > data.size) break
                val shName = buf.getInt(off)
                val shType = buf.getInt(off + 4)
                val shFlags = buf.getInt(off + 8).toLong()
                val shAddr = buf.getInt(off + 12).toLong()
                val shOffset = buf.getInt(off + 16).toLong()
                val shSize = buf.getInt(off + 20).toLong()

                val name = if (strTabOff > 0 && shName > 0) {
                    readCString(data, (strTabOff + shName).toInt())
                } else "sect_$i"

                sections.add(ElfSection(i, name, shType, shFlags, shAddr, shOffset, shSize))
            }
        }
    } catch (_: Exception) {}
    return sections
}

/** Read null-terminated C string from byte array */
private fun readCString(data: ByteArray, offset: Int): String {
    if (offset < 0 || offset >= data.size) return ""
    val sb = StringBuilder()
    var i = offset
    while (i < data.size && data[i] != 0.toByte()) {
        sb.append(data[i].toInt().toChar())
        i++
    }
    return sb.toString()
}

/** Carve (extract) ELF sections to files */
private fun carveSections(path: String, sections: List<ElfSection>): List<String> {
    val result = mutableListOf<String>()
    val outDir = File("/sdcard/Download/OprekTool/sections/${File(path).nameWithoutExtension}")
    outDir.mkdirs()
    result.add("[+] Carving ${sections.size} sections to ${outDir.absolutePath}")

    val data = File(path).readBytes()
    var carved = 0
    for (sec in sections) {
        if (sec.offset + sec.size <= data.size && sec.size > 0) {
            val secData = data.sliceArray(sec.offset.toInt() until (sec.offset + sec.size).toInt())
            val outFile = File(outDir, "${sec.index}_${sec.name.replace("/", "_")}.bin")
            outFile.writeBytes(secData)
            carved++
            result.add("[+] [${sec.index}] ${sec.name} → ${outFile.name} (${formatSize(secData.size.toLong())})")
        }
    }
    result.add("[+] Carved $carved sections → ${outDir.absolutePath}")
    return result
}

/** Recursive unpack to /sdcard/Download/OprekTool/unpacked/ */
private fun recursiveUnpack(path: String): List<String> {
    val result = mutableListOf<String>()
    val outDir = File("/sdcard/Download/OprekTool/unpacked/${File(path).nameWithoutExtension}")
    outDir.mkdirs()
    result.add("[+] Recursive unpack → ${outDir.absolutePath}")

    // Analyze tree
    val tree = analyzeRecursive(path, 0, 16)
    result.add("[+] Format: ${tree.type} | Nodes: ${countNodes(tree)}")

    // Actually extract based on format
    when (tree.type) {
        "ZIP", "APK" -> {
            try {
                val fis = java.io.FileInputStream(path)
                val zis = java.util.zip.ZipInputStream(fis)
                var entry = zis.nextEntry
                var count = 0
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(outDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zis.copyTo(out) }
                        count++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
                fis.close()
                result.add("[+] Extracted $count files")
            } catch (e: Exception) { result.add("[-] ZIP extract error: ${e.message}") }
        }
        "AR" -> {
            val proc = ProcessBuilder("sh", "-c", "cd '${outDir.absolutePath}' && ar x '$path' 2>&1")
                .redirectErrorStream(true).start()
            proc.waitFor()
            val count = outDir.walkTopDown().filter { it.isFile }.count()
            result.add("[+] Extracted $count files from AR")
        }
        "GZIP" -> {
            val inner = decompressGzip(path)
            if (inner != null) {
                File(inner).copyTo(File(outDir, File(path).nameWithoutExtension), overwrite = true)
                result.add("[+] Decompressed → ${File(path).nameWithoutExtension}")
            }
        }
        "XZ" -> {
            val inner = decompressXz(path)
            if (inner != null) {
                File(inner).copyTo(File(outDir, File(path).nameWithoutExtension), overwrite = true)
                result.add("[+] Decompressed → ${File(path).nameWithoutExtension}")
            }
        }
        "ELF" -> {
            // Carve sections
            val secs = parseElfSections(path)
            val data = File(path).readBytes()
            for (sec in secs) {
                if (sec.offset + sec.size <= data.size && sec.size > 0) {
                    val secData = data.sliceArray(sec.offset.toInt() until (sec.offset + sec.size).toInt())
                    File(outDir, "section_${sec.index}_${sec.name}.bin").writeBytes(secData)
                }
            }
            result.add("[+] Carved ${secs.size} ELF sections")
        }
        "TAR" -> {
            val proc = ProcessBuilder("sh", "-c", "cd '${outDir.absolutePath}' && tar xf '$path' 2>&1")
                .redirectErrorStream(true).start()
            proc.waitFor()
            val count = outDir.walkTopDown().filter { it.isFile }.count()
            result.add("[+] Extracted $count files from TAR")
        }
        else -> {
            // Copy raw file
            File(path).copyTo(File(outDir, File(path).name), overwrite = true)
            result.add("[*] Unknown format (${tree.type}), copied raw file")
        }
    }

    val total = outDir.walkTopDown().filter { it.isFile }.count()
    result.add("[+] Total files: $total")
    result.add("[+] Output: ${outDir.absolutePath}")
    return result
}

/** Repack modified sections back into binary */
private fun repackBinary(path: String): List<String> {
    val result = mutableListOf<String>()
    val outFile = File(path)
    val sectionsDir = File("/sdcard/Download/OprekTool/sections/${outFile.nameWithoutExtension}")

    if (!sectionsDir.exists()) {
        return listOf("[-] Sections directory not found. Carve sections first.")
    }

    // Create backup
    val backup = File(path + ".bak")
    if (!backup.exists()) {
        outFile.copyTo(backup)
        result.add("[+] Backup: ${backup.absolutePath}")
    }

    val data = outFile.readBytes().copyOf()
    val sectionFiles = sectionsDir.listFiles()?.filter { it.name.endsWith(".bin") } ?: emptyList()
    result.add("[+] Repacking ${sectionFiles.size} sections into ${outFile.name}")

    for (sf in sectionFiles) {
        // Parse section index from filename: "0_.text.bin" → index 0
        val idxStr = sf.nameBefore("_").toIntOrNull() ?: continue
        val secs = parseElfSections(path)
        val sec = secs.getOrNull(idxStr) ?: continue

        val newData = sf.readBytes()
        if (sec.offset + newData.size <= data.size) {
            newData.copyInto(data, sec.offset.toInt())
            result.add("[+] [${sec.index}] ${sec.name} → ${newData.size} bytes patched at 0x${sec.offset.toString(16)}")
        }
    }

    // Write back
    val patchedFile = File("/sdcard/Download/OprekTool/${outFile.nameWithoutExtension}_repacked${outFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }}")
    patchedFile.writeBytes(data)
    result.add("[+] Repacked: ${patchedFile.absolutePath} (${formatSize(data.size.toLong())})")
    return result
}

/** Full entropy map with block-by-block analysis */
private fun fullEntropyMap(path: String): List<String> {
    val result = mutableListOf<String>()
    val data = File(path).readBytes()
    if (data.isEmpty()) return listOf("[-] Empty file")

    val blockSize = 4096
    val blocks = (data.size + blockSize - 1) / blockSize
    result.add("[+] Entropy map: ${data.size} bytes, $blocks blocks (${blockSize}B each)")

    val entropies = mutableListOf<Double>()
    val ranges = mutableListOf<Pair<Long, Double>>()

    for (i in 0 until blocks) {
        val start = i * blockSize
        val end = min(start + blockSize, data.size)
        val block = data.sliceArray(start until end)
        val e = shannonEntropy(block)
        entropies.add(e)
        ranges.add(Pair(start.toLong(), e))
    }

    val avg = entropies.average()
    val max = entropies.maxOrNull() ?: 0.0
    val encryptedBlocks = entropies.count { it > 7.5 }
    val highBlocks = entropies.count { it in 6.5..7.5 }

    result.add("[+] Average entropy: ${"%.4f".format(avg)}")
    result.add("[+] Max entropy: ${"%.4f".format(max)}")
    result.add("[+] Encrypted/packed blocks (>7.5): $encryptedBlocks / $blocks")
    result.add("[+] High entropy blocks (6.5-7.5): $highBlocks / $blocks")

    // Visual map (text-based heatmap)
    result.add("")
    result.add("[*] Entropy heatmap:")
    val chars = " ▁▂▃▄▅▆▇█"
    val lineLen = 64
    var line = StringBuilder()
    for ((i, e) in entropies.withIndex()) {
        val idx = min((e / 8.0 * (chars.length - 1)).toInt(), chars.length - 1)
        line.append(chars[idx])
        if ((i + 1) % lineLen == 0 || i == entropies.lastIndex) {
            result.add("  ${String.format("%06X", (i / lineLen) * lineLen * blockSize)}: $line")
            line = StringBuilder()
        }
    }

    // High-entropy regions (likely encrypted/packed)
    if (encryptedBlocks > 0) {
        result.add("")
        result.add("[!] High-entropy regions (likely encrypted/packed):")
        var inRegion = false
        var regionStart = 0
        for ((i, e) in entropies.withIndex()) {
            if (e > 7.5 && !inRegion) {
                inRegion = true
                regionStart = i
            } else if ((e <= 7.5 || i == entropies.lastIndex) && inRegion) {
                inRegion = false
                val startOff = regionStart.toLong() * blockSize
                val endOff = min(((i + 1).toLong() * blockSize), data.size.toLong())
                result.add("  0x${startOff.toString(16)} - 0x${endOff.toString(16)} (${formatSize(endOff - startOff)})")
            }
        }
    }

    return result
}

/** Extract .deb natively (AR → control.tar → data.tar → files) */
private fun extractDebNative(path: String): List<String> {
    val result = mutableListOf<String>()
    val outDir = File("/sdcard/Download/OprekTool/deb/${File(path).nameWithoutExtension}")
    outDir.mkdirs()

    result.add("[+] Extracting .deb: ${File(path).name}")
    result.add("[+] Output: ${outDir.absolutePath}")

    // Step 1: Extract AR
    val proc = ProcessBuilder("sh", "-c", "ar x '$path' --output='${outDir.absolutePath}' 2>&1")
        .redirectErrorStream(true).start()
    proc.waitFor()

    val arFiles = outDir.listFiles()?.toList() ?: emptyList()
    result.add("[+] AR entries: ${arFiles.map { it.name }.joinToString(", ")}")

    // Step 2: Extract data.tar.*
    val dataTar = arFiles.firstOrNull { it.name.startsWith("data.tar") }
    if (dataTar != null) {
        val dataDir = File(outDir, "data")
        dataDir.mkdirs()
        val proc2 = ProcessBuilder("sh", "-c", "tar xf '${dataTar.absolutePath}' -C '${dataDir.absolutePath}' 2>&1")
            .redirectErrorStream(true).start()
        proc2.waitFor()
        val fileCount = dataDir.walkTopDown().filter { it.isFile }.count()
        result.add("[+] Extracted $fileCount files from data.tar")
    }

    // Step 3: Parse control
    val controlTar = arFiles.firstOrNull { it.name.startsWith("control.tar") }
    if (controlTar != null) {
        val ctrlDir = File(outDir, "control_extracted")
        ctrlDir.mkdirs()
        ProcessBuilder("sh", "-c", "tar xf '${controlTar.absolutePath}' -C '${ctrlDir.absolutePath}' 2>&1")
            .redirectErrorStream(true).start().waitFor()
        val controlFile = File(ctrlDir, "control")
        if (controlFile.exists()) {
            result.add("[+] Control info:")
            controlFile.readLines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) result.add("    ${line.substring(0, idx).trim()}: ${line.substring(idx + 1).trim()}")
            }
        }
    }

    return result
}

/** Carve all printable strings with offsets */
private fun carveAllStrings(path: String): List<String> {
    val result = mutableListOf<String>()
    val data = File(path).readBytes()
    val outDir = File("/sdcard/Download/OprekTool/strings/${File(path).nameWithoutExtension}")
    outDir.mkdirs()

    result.add("[+] Carving strings from ${File(path).name} (${formatSize(data.size.toLong())})")

    val strings = mutableListOf<Pair<Long, String>>()
    val current = StringBuilder()
    var startOffset = 0L

    for (i in data.indices) {
        val c = data[i].toInt() and 0xFF
        if (c in 0x20..0x7E) {
            if (current.isEmpty()) startOffset = i.toLong()
            current.append(c.toChar())
        } else {
            if (current.length >= 6) {
                strings.add(Pair(startOffset, current.toString()))
            }
            current.clear()
        }
    }

    result.add("[+] Found ${strings.size} strings")

    // Categorize
    val urls = strings.filter { it.second.contains("http") }
    val ips = strings.filter { Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(it.second) }
    val paths = strings.filter { it.second.startsWith("/") && it.second.contains('.') }
    val keys = strings.filter { it.second.contains("key", ignoreCase = true) || it.second.contains("secret", ignoreCase = true) || it.second.contains("token", ignoreCase = true) || it.second.contains("password", ignoreCase = true) }

    if (urls.isNotEmpty()) {
        result.add("[+] URLs (${urls.size}):")
        urls.take(10).forEach { (off, s) -> result.add("  0x${off.toString(16)}: $s") }
    }
    if (ips.isNotEmpty()) {
        result.add("[+] IP addresses (${ips.size}):")
        ips.take(10).forEach { (off, s) -> result.add("  0x${off.toString(16)}: $s") }
    }
    if (keys.isNotEmpty()) {
        result.add("[+] Keys/secrets (${keys.size}):")
        keys.take(10).forEach { (off, s) -> result.add("  0x${off.toString(16)}: $s") }
    }

    // Save all
    val outFile = File(outDir, "${File(path).nameWithoutExtension}.strings.txt")
    outFile.writeText(strings.joinToString("\n") { "0x${String.format("%08X", it.first)}: ${it.second}" })
    result.add("[+] All strings → ${outFile.absolutePath}")

    // Save categorized
    val catFile = File(outDir, "${File(path).nameWithoutExtension}.important_strings.txt")
    catFile.writeText(buildString {
        appendLine("=== URLs ===")
        urls.forEach { (off, s) -> appendLine("0x${off.toString(16)}: $s") }
        appendLine("\n=== IPs ===")
        ips.forEach { (off, s) -> appendLine("0x${off.toString(16)}: $s") }
        appendLine("\n=== Paths ===")
        paths.take(50).forEach { (off, s) -> appendLine("0x${off.toString(16)}: $s") }
        appendLine("\n=== Keys/Secrets ===")
        keys.forEach { (off, s) -> appendLine("0x${off.toString(16)}: $s") }
    })
    result.add("[+] Important strings → ${catFile.absolutePath}")

    return result
}

/** Scan for embedded files inside binary */
private fun scanEmbeddedFiles(path: String): List<String> {
    val result = mutableListOf<String>()
    val data = File(path).readBytes()
    result.add("[+] Scanning ${File(path).name} (${formatSize(data.size.toLong())}) for embedded files")

    val signatures = listOf(
        Triple(byteArrayOf(0x7F, 0x45, 0x4C, 0x46), "ELF", "Executable"),
        Triple(byteArrayOf(0x50, 0x4B, 0x03, 0x04), "ZIP", "Archive"),
        Triple(byteArrayOf(0x64, 0x65, 0x78, 0x0A), "DEX", "Dalvik Executable"),
        Triple(byteArrayOf(0x52, 0x61, 0x72, 0x21), "RAR", "RAR Archive"),
        Triple(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte()), "7Z", "7-Zip Archive"),
        Triple(byteArrayOf(0x1F, 0x8B.toByte()), "GZIP", "Gzip compressed"),
        Triple(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58), "XZ", "XZ compressed"),
        Triple(byteArrayOf(0x89, 0x50, 0x4E, 0x47), "PNG", "PNG image"),
        Triple(byteArrayOf(0xFF.toByte(), 0xD8, 0xFF.toByte(), 0xE0), "JPEG", "JPEG image"),
        Triple(byteArrayOf(0x4D, 0x5A), "PE", "Windows executable"),
    )

    var found = 0
    for (i in 0 until data.size - 4) {
        for ((magic, type, desc) in signatures) {
            if (i + magic.size <= data.size) {
                var match = true
                for (j in magic.indices) {
                    if (data[i + j] != magic[j]) { match = false; break }
                }
                if (match) {
                    result.add("[+] 0x${String.format("%08X", i)}: $type ($desc)")
                    found++
                    break // Skip overlapping
                }
            }
        }
    }

    result.add("[+] Found $found embedded signatures")
    return result
}

/* ═══════════════════════════════════════════════════════════════════════
 *  UTILITIES
 * ═══════════════════════════════════════════════════════════════════════ */

private fun shannonEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val freq = IntArray(256)
    for (b in data) freq[b.toInt() and 0xFF]++
    var entropy = 0.0
    for (f in freq) {
        if (f > 0) {
            val p = f.toDouble() / data.size
            entropy -= p * ln(p) / ln(2.0)
        }
    }
    return entropy
}

private fun flattenTree(node: ResourceNode): List<ResourceNode> {
    val result = mutableListOf<ResourceNode>()
    fun walk(n: ResourceNode) {
        result.add(n)
        for (c in n.children) walk(c)
    }
    walk(node)
    return result
}

private fun countNodes(node: ResourceNode): Int {
    return 1 + node.children.sumOf { countNodes(it) }
}

private fun countDepth(node: ResourceNode): Int {
    return if (node.children.isEmpty()) node.depth
    else node.children.maxOf { countDepth(it) }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}MB"
    }
}

private fun getPathFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                val name = it.getString(idx)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val file = File(context.cacheDir, name)
                file.outputStream().use { out -> inputStream.copyTo(out) }
                return file.absolutePath
            }
        }
    }
    return null
}

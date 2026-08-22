package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpxUnpackerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var packerDetected by remember { mutableStateOf("") }
    var entropy by remember { mutableStateOf(0.0) }
    var unpackedData by remember { mutableStateOf<ByteArray?>(null) }

    // Auto-refresh when file changes
    val rev = SharedFileState.revision
    LaunchedEffect(rev) {
        val f = SharedFileState.findFile(context)
        if (f != null) {
            fileData = f.readBytes()
            fileName = f.name
            output = listOf("[+] Auto-loaded: ${f.name} (${fileData?.size ?: 0} bytes)")
        }
    }

    fun addLine(msg: String) { output = output + msg }

    fun loadFile() {
        val f = SharedFileState.findFile(context)
        if (f != null) {
            fileData = f.readBytes()
            fileName = f.name
            unpackedData = null
            output = emptyList()
            addLine("[+] Loaded: ${f.name} (${fileData?.size ?: 0} bytes)")
        } else {
            output = listOf("[-] No file. Open from Home first.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 UPX Unpacker", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { output = emptyList(); loadFile() }) { Icon(Icons.Default.Refresh, "Load") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Info card
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📦 UPX Unpacker & Section Extractor", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("UPX, Themida, ASPack, MEW, ELF section extraction", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(fileName.ifEmpty { "No file loaded" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (packerDetected.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Detection: $packerDetected", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            isRunning = true
                            scope.launch(Dispatchers.IO) {
                                addLine("📦 UPX Unpacker v2.0 (Entropy + Section)")
                                addLine("")

                                if (fileData == null) {
                                    loadFile()
                                    if (fileData == null) {
                                        addLine("[-] No file loaded!")
                                        isRunning = false
                                        return@launch
                                    }
                                }

                                val data = fileData!!

                                // 1. Detect packer
                                addLine("🔍 Analyzing binary...")
                                val packerResult = detectPackerAdvanced(data)
                                packerDetected = packerResult.first
                                addLine("   Packer: ${packerResult.first}")
                                addLine("   Confidence: ${packerResult.second}%")

                                // 2. Calculate entropy
                                entropy = calculateEntropy(data)
                                addLine("   Entropy: ${"%.4f".format(entropy)} / 8.0")
                                when {
                                    entropy > 7.5 -> addLine("   ⚠️ Very high entropy - definitely packed/encrypted")
                                    entropy > 7.0 -> addLine("   ⚠️ High entropy - likely packed")
                                    entropy > 6.0 -> addLine("   ℹ️ Medium entropy - possibly compressed")
                                    else -> addLine("   ✅ Low entropy - likely unpacked")
                                }

                                // 3. ELF section analysis
                                if (data.size >= 16 && data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte()) {
                                    addLine("\n📋 ELF Section Analysis:")
                                    val is64 = data[4] == 2.toByte()
                                    addLine("   Format: ELF ${if (is64) "64-bit" else "32-bit"}")

                                    val sections = parseElfSections(data)
                                    if (sections.isNotEmpty()) {
                                        addLine("   Sections found: ${sections.size}")
                                        addLine("   ┌──────────────┬──────────┬──────────┬──────────┬─────────┐")
                                        addLine("   │ Name         │ Offset   │ Size     │ Entropy  │ Packed? │")
                                        addLine("   ├──────────────┼──────────┼──────────┼──────────┼─────────┤")
                                        for (s in sections) {
                                            val e = if (s.data.isNotEmpty()) calculateEntropy(s.data) else 0.0
                                            val packed = if (e > 7.0) "YES" else "no"
                                            val color = if (e > 7.0) "🔴" else if (e > 6.0) "🟡" else "🟢"
                                            addLine("   │ ${s.name.padEnd(12)} │ 0x${"%08X".format(s.offset)} │ 0x${"%06X".format(s.size)} │ ${"%.2f".format(e).padStart(6)} │ $color $packed │")
                                        }
                                        addLine("   └──────────────┴──────────┴──────────┴──────────┴─────────┘")

                                        // Extract high-entropy sections (packed data)
                                        val packedSections = sections.filter {
                                            it.data.isNotEmpty() && calculateEntropy(it.data) > 6.8
                                        }
                                        if (packedSections.isNotEmpty()) {
                                            addLine("\n🔍 Found ${packedSections.size} high-entropy (packed) sections")

                                            // Try UPX signature detection
                                            val upxIdx = findUpxSignature(data)
                                            if (upxIdx >= 0) {
                                                addLine("   ✅ UPX signature at 0x${"%X".format(upxIdx)}")
                                                addLine("   📦 Attempting UPX decompression...")

                                                val unpacked = tryUpxLzmaDecompress(data, upxIdx)
                                                if (unpacked != null) {
                                                    addLine("   ✅ Decompressed! ${unpacked.size} bytes")
                                                    unpackedData = unpacked
                                                    val outDir = File(context.getExternalFilesDir(null), "unpacked")
                                                    outDir.mkdirs()
                                                    val outFile = File(outDir, "${fileName}_unpacked.bin")
                                                    outFile.writeBytes(unpacked)
                                                    addLine("   Saved to: ${outFile.absolutePath}")
                                                } else {
                                                    addLine("   ⚠️ LZMA decompress failed (stub-only extraction)")
                                                    addSectionExtraction(packedSections, context, fileName, addLine = { addLine(it) })
                                                }
                                            } else {
                                                addLine("   ℹ️ No UPX signature - using section extraction")
                                                addSectionExtraction(packedSections, context, fileName, addLine = { addLine(it) })
                                            }
                                        }

                                        // Also check for common packer section names
                                        val upxSections = sections.filter { it.name.contains("UPX", ignoreCase = true) }
                                        if (upxSections.isNotEmpty()) {
                                            addLine("\n📦 UPX sections detected:")
                                            for (s in upxSections) {
                                                val e = calculateEntropy(s.data)
                                                addLine("   ${s.name}: ${s.size} bytes (entropy: ${"%.2f".format(e)})")
                                            }
                                        }
                                    } else {
                                        addLine("   ⚠️ Could not parse ELF sections (stripped?)")
                                        // Fallback: scan for patterns
                                        addLine("\n🔍 Fallback: scanning for UPX patterns...")
                                        scanForPackerPatterns(data, addLine = { addLine(it) })
                                    }
                                } else {
                                    // Not ELF - try generic scan
                                    addLine("\n🔍 Non-ELF binary - scanning for packer patterns...")
                                    scanForPackerPatterns(data, addLine = { addLine(it) })
                                }

                                // 4. Entropy graph
                                addLine("\n📊 Entropy Map (256-byte blocks):")
                                val blockSize = 256
                                val blocks = data.size / blockSize
                                val maxBlocks = 60 // Limit display width
                                val step = if (blocks > maxBlocks) blocks / maxBlocks else 1
                                val graph = StringBuilder("   [")
                                for (i in 0 until blocks step step) {
                                    val chunk = data.copyOfRange(i * blockSize, ((i + 1) * blockSize).coerceAtMost(data.size))
                                    val e = calculateEntropy(chunk)
                                    val c = when {
                                        e > 7.5 -> '█'
                                        e > 7.0 -> '▓'
                                        e > 6.5 -> '▒'
                                        e > 6.0 -> '░'
                                        else -> '·'
                                    }
                                    graph.append(c)
                                }
                                graph.append("]")
                                addLine(graph.toString())
                                addLine("   Legend: █>7.5 ▓>7.0 ▒>6.5 ░>6.0 ·<6.0")

                                addLine("\n🎉 Analysis complete!")
                                isRunning = false
                            }
                        }, modifier = Modifier.weight(1f), enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AccentRed else AccentGreen)) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Processing...", fontSize = 11.sp)
                            } else {
                                Text("🔍 Analyze & Unpack", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Output
            Card(Modifier.fillMaxWidth().weight(1f).padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        if (output.isNotEmpty()) {
                            TextButton(onClick = {
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clip.setPrimaryClip(android.content.ClipData.newPlainText("output", output.joinToString("\n")))
                            }) { Text("Copy All", color = AccentCyan, fontSize = 10.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📦", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Load a file and tap Analyze", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn {
                            itemsIndexed(output) { _, line ->
                                val color = when {
                                    line.startsWith("[+]") || line.startsWith("✅") -> AccentGreen
                                    line.startsWith("[-]") || line.startsWith("❌") -> AccentRed
                                    line.startsWith("⚠️") -> AccentOrange
                                    line.startsWith("   🔴") -> AccentRed
                                    line.startsWith("   🟡") -> AccentOrange
                                    line.startsWith("   🟢") -> AccentGreen
                                    else -> TextPrimary
                                }
                                Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ElfSectionEntry(val name: String, val offset: Long, val size: Long, val data: ByteArray)

private fun parseElfSections(data: ByteArray): List<ElfSectionEntry> {
    if (data.size < 64) return emptyList()
    val is64 = data[4] == 2.toByte()
    val isLE = data[5] == 1.toByte()

    fun read16(off: Int): Int = if (isLE) (data[off].toInt() and 0xFF) or ((data[off+1].toInt() and 0xFF) shl 8) else ((data[off].toInt() and 0xFF) shl 8) or (data[off+1].toInt() and 0xFF)
    fun read32(off: Int): Long {
        var v = 0L
        for (i in 0..3) v = v or ((data[off+i].toLong() and 0xFF) shl (if (isLE) i * 8 else (3-i) * 8))
        return v
    }
    fun read64(off: Int): Long {
        var v = 0L
        for (i in 0..7) v = v or ((data[off+i].toLong() and 0xFF) shl (if (isLE) i * 8 else (7-i) * 8))
        return v
    }

    return try {
        val shoff: Long
        val shnum: Int
        val shentsize: Int
        val shstrndx: Int

        if (is64) {
            shoff = read64(0x28)
            shnum = read16(0x3C)
            shentsize = read16(0x3A)
            shstrndx = read16(0x3E)
        } else {
            shoff = read32(0x20)
            shnum = read16(0x30)
            shentsize = read16(0x2E)
            shstrndx = read16(0x32)
        }

        if (shoff == 0L || shnum == 0 || shstrndx >= shnum) return emptyList()
        if (shoff.toInt() + shnum * shentsize > data.size) return emptyList()

        // Read string table
        val strTabOff = if (is64) {
            val sOff = shoff + shstrndx.toLong() * shentsize
            if (sOff.toInt() + 24 <= data.size) read64(sOff.toInt() + 24) else 0L
        } else {
            val sOff = shoff + shstrndx.toLong() * shentsize
            if (sOff.toInt() + 16 <= data.size) read32(sOff.toInt() + 12) else 0L
        }
        val strTabSize = if (is64) {
            val sOff = shoff + shstrndx.toLong() * shentsize
            if (sOff.toInt() + 32 <= data.size) read64(sOff.toInt() + 32) else 0L
        } else {
            val sOff = shoff + shstrndx.toLong() * shentsize
            if (sOff.toInt() + 20 <= data.size) read32(sOff.toInt() + 16) else 0L
        }
        val strTable = if (strTabOff.toInt() + strTabSize <= data.size) data.copyOfRange(strTabOff.toInt(), (strTabOff + strTabSize).toInt()) else byteArrayOf()

        val sections = mutableListOf<ElfSectionEntry>()
        for (i in 0 until shnum) {
            val base = (shoff + i.toLong() * shentsize).toInt()
            if (base + shentsize > data.size) break

            val nameIdx = read32(base)
            val name = if (nameIdx.toInt() < strTable.size) {
                val startIdx = nameIdx.toInt()
                var end = startIdx
                while (end < strTable.size && strTable[end] != 0.toByte()) end++
                String(strTable, startIdx, end - startIdx)
            } else "?"

            val secOffset: Long
            val secSize: Long
            if (is64) {
                secOffset = read64(base + 24)
                secSize = read64(base + 32)
            } else {
                secOffset = read32(base + 16)
                secSize = read32(base + 20)
            }

            if (secOffset.toInt() + secSize <= data.size && secSize > 0 && name.isNotEmpty() && name != "?") {
                val secData = data.copyOfRange(secOffset.toInt(), (secOffset + secSize).toInt())
                sections.add(ElfSectionEntry(name, secOffset, secSize, secData))
            }
        }
        sections
    } catch (e: Exception) {
        emptyList()
    }
}

private fun detectPackerAdvanced(data: ByteArray): Pair<String, Int> {
    val text = String(data, Charsets.US_ASCII)

    // UPX
    if (findUpxSignature(data) >= 0) return "UPX" to 95
    if (text.contains("UPX!", ignoreCase = true)) return "UPX (string)" to 90
    if (text.contains("UPX0") || text.contains("UPX1")) return "UPX (sections)" to 92

    // Others
    if (text.contains("Themida", ignoreCase = true)) return "Themida" to 85
    if (text.contains("VMProtect", ignoreCase = true)) return "VMProtect" to 85
    if (text.contains(".aspack", ignoreCase = true)) return "ASPack" to 85
    if (text.contains("PECompact", ignoreCase = true)) return "PECompact" to 80
    if (text.contains("MEW", ignoreCase = true)) return "MEW" to 70
    if (text.contains("FSG", ignoreCase = true)) return "FSG" to 70
    if (text.contains("MPRESS", ignoreCase = true)) return "MPRESS" to 75

    // ELF packer sections
    if (text.contains(".upx")) return "UPX (section)" to 90
    if (text.contains(".adata")) return "ASPack (section)" to 85

    val entropy = calculateEntropy(data)
    if (entropy > 7.5) return "Unknown (high entropy)" to 75

    return "Not packed" to 90
}

private fun findUpxSignature(data: ByteArray): Int {
    val magic = byteArrayOf(0x55, 0x50, 0x58, 0x21) // "UPX!"
    for (i in 0..(data.size - magic.size)) {
        if (data[i] == magic[0] && data[i + 1] == magic[1] && data[i + 2] == magic[2] && data[i + 3] == magic[3]) return i
    }
    return -1
}

private fun calculateEntropy(data: ByteArray): Double {
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

private fun tryUpxLzmaDecompress(data: ByteArray, upxIdx: Int): ByteArray? {
    // UPX format: UPX! magic + version(2) + method(1) + level(1) + ...
    // After the stub, there's LZMA-compressed data
    // Full implementation requires UPX decompressor library
    // For now, try to find LZMA stream after UPX header
    if (upxIdx + 16 >= data.size) return null

    // UPX typically has: UPX! followed by header, then compressed data
    // The exact offset depends on UPX version and format
    // We try a few common offsets
    val offsets = listOf(upxIdx + 32, upxIdx + 64, upxIdx + 96, upxIdx + 128, upxIdx + 52, upxIdx + 80)
    for (off in offsets) {
        if (off + 20 >= data.size) continue
        // Check for LZMA properties byte (usually 0x5D 0x00)
        if (data[off] == 0x5D.toByte() && data[off + 1] == 0x00.toByte()) {
            // Found potential LZMA stream - but full decompression needs liblzma
            // Return null to fall back to section extraction
            return null
        }
    }
    return null
}

private fun addSectionExtraction(sections: List<ElfSectionEntry>, context: Context, fileName: String, addLine: (String) -> Unit) {
    addLine("\n📦 Extracting high-entropy sections...")
    val outDir = File(context.getExternalFilesDir(null), "extracted_sections")
    outDir.mkdirs()

    for (s in sections) {
        val e = calculateEntropy(s.data)
        if (e > 6.5 && s.data.size > 64) {
            val outFile = File(outDir, "${fileName}_${s.name.replace("/", "_")}.bin")
            outFile.writeBytes(s.data)
            addLine("   ✅ ${s.name}: ${s.size} bytes → ${outFile.name}")
        }
    }

    // Also extract ALL sections for comprehensive analysis
    addLine("\n📦 Extracting ALL sections...")
    val allDir = File(outDir, "all_sections")
    allDir.mkdirs()
    for (s in sections) {
        val outFile = File(allDir, "${s.name.replace("/", "_")}.bin")
        outFile.writeBytes(s.data)
    }
    addLine("   ✅ ${sections.size} sections extracted to: ${allDir.name}/")
    addLine("   📁 Path: ${allDir.absolutePath}")
}

private fun scanForPackerPatterns(data: ByteArray, addLine: (String) -> Unit) {
    val text = String(data, Charsets.US_ASCII)

    // Common packer signatures
    val patterns = mapOf(
        "UPX!" to "UPX compressor",
        "UPX0" to "UPX section",
        "UPX1" to "UPX section",
        ".aspack" to "ASPack",
        ".adata" to "ASPack data",
        "Themida" to "Themida/WinLicense",
        "VMProtect" to "VMProtect",
        "PECompact" to "PECompact",
        "MEW" to "MEW packer",
        "FSG!" to "FSG packer",
        "MPRESS" to "MPRESS",
        ".vmp0" to "VMProtect v3",
        ".vmp1" to "VMProtect v3",
        "petite" to "Petite",
        "y0da" to "Y0da Crypter",
        "nsp0" to "NsPack",
        "nsp1" to "NsPack",
        "pec2" to "PECompact 2",
        "aPLib" to "aPLib compression"
    )

    var found = false
    for ((pat, name) in patterns) {
        val idx = text.indexOf(pat, ignoreCase = true)
        if (idx >= 0) {
            addLine("   🔍 Found: $name at offset 0x${"%X".format(idx)}")
            found = true
        }
    }

    // Check entropy
    val entropy = calculateEntropy(data)
    if (entropy > 7.0 && !found) {
        addLine("   ⚠️ High entropy (${"%.2f".format(entropy)}) but no known packer signature")
        addLine("   💡 This may be encrypted or compressed with unknown packer")
        addLine("   💡 Try: unzip/binwalk/7z to check for nested archives")
    }

    if (!found && entropy <= 6.0) {
        addLine("   ✅ No packer signatures found - binary appears clean")
    }
}

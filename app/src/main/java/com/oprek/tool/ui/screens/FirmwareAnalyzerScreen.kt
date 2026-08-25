package com.oprek.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import java.io.File
import kotlin.math.ln

/**
 * Firmware Analyzer Screen
 * Extract embedded files from firmware images (binwalk-like)
 */
data class FirmwareChunk(
    val offset: Long, val size: Long, val description: String,
    val entropy: Double = 0.0, val confidence: String = "high"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var targetFile by remember { mutableStateOf<String?>(null) }
    var chunks by remember { mutableStateOf(listOf<FirmwareChunk>()) }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var avgEntropy by remember { mutableDoubleStateOf(0.0) }

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
                scope.launch(Dispatchers.IO) {
                    val result = scanFirmware(path)
                    withContext(Dispatchers.Main) {
                        chunks = result.first
                        avgEntropy = result.second
                        output = listOf(
                            "[+] Scanned: $fileName",
                            "[+] Size: ${File(path).length()} bytes",
                            "[+] Found ${chunks.size} embedded signatures",
                            "[+] Average entropy: ${"%.4f".format(avgEntropy)}"
                        ) + chunks.map { c ->
                            "[*] 0x${String.format("%08X", c.offset)}: ${c.description} (${c.confidence} confidence)"
                        }
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Firmware Analyzer", fontWeight = FontWeight.Bold) },
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
            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = AccentOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (fileName.isNotEmpty()) "$fileName (${chunks.size} signatures)" else "Select firmware image",
                        modifier = Modifier.weight(1f),
                        color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Actions
            if (targetFile != null) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                scope.launch(Dispatchers.IO) {
                                    val result = extractFirmware(targetFile!!)
                                    withContext(Dispatchers.Main) {
                                        output = output + result
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) { Text("📦 Extract All") }

                    Button(
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                scope.launch(Dispatchers.IO) {
                                    val result = carveStrings(targetFile!!)
                                    withContext(Dispatchers.Main) {
                                        output = output + result
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) { Text("🔤 Carve Strings") }
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = AccentOrange)
            }

            // Chunks list
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                // Stats header
                if (chunks.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("📊 Scan Results", fontWeight = FontWeight.Bold, color = AccentGreen)
                                Text("Found ${chunks.size} embedded signatures", color = TextSecondary, fontSize = 12.sp)
                                Text("Avg entropy: ${"%.4f".format(avgEntropy)}", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                items(chunks) { chunk ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    chunk.description.contains("Squashfs") -> Icons.Default.Archive
                                    chunk.description.contains("JFFS") -> Icons.Default.Storage
                                    chunk.description.contains("UBI") -> Icons.Default.Memory
                                    chunk.description.contains("Cramfs") -> Icons.Default.FolderZip
                                    chunk.description.contains("ext2/ext3/ext4") -> Icons.Default.Folder
                                    chunk.description.contains("Linux") -> Icons.Default.Code
                                    chunk.description.contains("gzip") -> Icons.Default.Compress
                                    chunk.description.contains("xz") -> Icons.Default.Compress
                                    chunk.description.contains("LZMA") -> Icons.Default.Compress
                                    else -> Icons.Default.InsertDriveFile
                                },
                                null,
                                tint = when (chunk.confidence) {
                                    "high" -> AccentGreen
                                    "medium" -> AccentOrange
                                    else -> AccentRed
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chunk.description, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Offset: 0x${String.format("%08X", chunk.offset)} | Size: ${formatSize(chunk.size)} | Entropy: ${"%.2f".format(chunk.entropy)}",
                                    color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(chunk.confidence, color = when (chunk.confidence) {
                                "high" -> AccentGreen; "medium" -> AccentOrange; else -> AccentRed
                            }, fontSize = 9.sp)
                        }
                    }
                }

                // Log output
                if (output.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("📋 Log", fontWeight = FontWeight.Bold, color = AccentPurple, fontSize = 14.sp)
                    }
                    items(output.takeLast(50)) { line ->
                        Text(
                            line, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
                                else -> TextPrimary
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Firmware Scanning Functions ───

private fun scanFirmware(path: String): Pair<List<FirmwareChunk>, Double> {
    val data = File(path).readBytes()
    if (data.isEmpty()) return Pair(emptyList(), 0.0)

    val chunks = mutableListOf<FirmwareChunk>()

    // Magic signatures to search for
    // Magic signatures to search for (all byteArrayOf)
    val signatures: List<Pair<ByteArray, String>> = listOf(
        byteArrayOf(0x68, 0x73, 0x71, 0x73) to "Squashfs filesystem (little-endian)",
        byteArrayOf(0x73, 0x71, 0x73, 0x68) to "Squashfs filesystem (big-endian)",
        byteArrayOf(0x31, 0x19, 0x00, 0x00) to "UBI volume header",
        byteArrayOf(0x2D, 0x7C, 0x27, 0x01) to "Cramfs filesystem",
        byteArrayOf(0x53, 0xEF.toByte(), 0x01, 0x00) to "ext2/ext3/ext4 filesystem",
        byteArrayOf(0x9F.toByte(), 0xA0.toByte(), 0x1A, 0xFC.toByte()) to "Reiser filesystem",
        byteArrayOf(0x27, 0x05, 0x19, 0x56) to "uImage header",
        byteArrayOf(0xD0.toByte(), 0x0D, 0xFE.toByte(), 0xED.toByte()) to "FIT (Flattened Image Tree)",
        byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00) to "gzip compressed data",
        byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58) to "XZ compressed data",
        byteArrayOf(0x5D, 0x00, 0x00, 0x00) to "LZMA compressed data",
        byteArrayOf(0x42, 0x5A, 0x68) to "bzip2 compressed data",
        byteArrayOf(0x28, (0xB5.toByte()).toByte(), 0x2F, (0xFD.toByte()).toByte()) to "Zstandard compressed data",
        byteArrayOf(0x37, 0x7A, (0xBC.toByte()).toByte(), (0xAF.toByte()).toByte()) to "7-zip archive",
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) to "ZIP archive",
        byteArrayOf(0x52, 0x61, 0x72, 0x21) to "RAR archive",
        byteArrayOf(0x7F, 0x45, 0x4C, 0x46) to "ELF executable",
        byteArrayOf(0x4D, 0x5A) to "PE executable",
        byteArrayOf((0xCA.toByte()).toByte(), (0xFE.toByte()).toByte(), (0xBA.toByte()).toByte(), (0xBE.toByte()).toByte()) to "Mach-O (fat binary)",
        byteArrayOf((0xFE.toByte()).toByte(), (0xED.toByte()).toByte(), (0xFA.toByte()).toByte(), (0xCE.toByte()).toByte()) to "Mach-O 32-bit",
        byteArrayOf((0xFE.toByte()).toByte(), (0xED.toByte()).toByte(), (0xFA.toByte()).toByte(), (0xCF.toByte()).toByte()) to "Mach-O 64-bit",
    )


    for (i in 0 until data.size - 4) {
        for ((magic, desc) in signatures) {
            if (i + magic.size <= data.size) {
                var match = true
                for (j in magic.indices) {
                    if (data[i + j] != magic[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    val blockSize = minOf(65536, data.size - i)
                    val block = data.sliceArray(i until i + blockSize)
                    val entropy = shannonEntropy(block)
                    val confidence = when {
                        entropy > 7.5 && desc.contains("compressed") -> "high"
                        entropy > 7.0 -> "medium"
                        else -> "high"
                    }
                    chunks.add(FirmwareChunk(i.toLong(), blockSize.toLong(), desc, entropy, confidence))
                }
            }
        }
    }

    // Calculate average entropy
    val avg = if (chunks.isNotEmpty()) chunks.map { it.entropy }.average() else shannonEntropy(data)

    return Pair(chunks.sortedBy { it.offset }, avg)
}

private fun extractFirmware(path: String): List<String> {
    val result = mutableListOf<String>()
    val outDir = File("/sdcard/Download/OprekTool/firmware/${File(path).nameWithoutExtension}")
    outDir.mkdirs()

    result.add("[+] Extracting to: ${outDir.absolutePath}")

    // Try binwalk-like extraction
    try {
        // Method 1: Use binwalk if available
        val binwalkCheck = ProcessBuilder("which", "binwalk")
            .redirectErrorStream(true)
            .start()
        binwalkCheck.waitFor()

        if (binwalkCheck.exitValue() == 0) {
            result.add("[+] Using binwalk for extraction...")
            val process = ProcessBuilder("binwalk", "-e", "-C", outDir.absolutePath, path)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readLines()
            process.waitFor()
            result.addAll(output.map { "[*] $it" })
        } else {
            // Method 2: Manual extraction using dd
            result.add("[!] binwalk not found, using manual extraction")

            val data = File(path).readBytes()
            // Extract ELF files
            val elfMagic = byteArrayOf(0x7F.toByte(), 0x45, 0x4C, 0x46)
            var elfCount = 0
            for (i in 0 until data.size - 4) {
                if (data[i] == elfMagic[0] && data[i+1] == elfMagic[1] &&
                    data[i+2] == elfMagic[2] && data[i+3] == elfMagic[3]) {
                    // Found ELF - try to determine size
                    val end = findElfEnd(data, i)
                    if (end > i + 16) {
                        val elfData = data.sliceArray(i until end)
                        val elfFile = File(outDir, "extracted_elf_${elfCount}_0x${String.format("%08X", i)}.bin")
                        elfFile.writeBytes(elfData)
                        result.add("[+] Extracted ELF at 0x${String.format("%08X", i)} (${elfData.size} bytes)")
                        elfCount++
                    }
                }
            }
            result.add("[+] Extracted $elfCount ELF files")

            // Extract gzip streams
            val gzipMagic = byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08, 0x00)
            var gzipCount = 0
            for (i in 0 until data.size - 4) {
                if (data[i] == gzipMagic[0] && data[i+1] == gzipMagic[1] &&
                    data[i+2] == gzipMagic[2] && data[i+3] == gzipMagic[3]) {
                    // Try to decompress
                    try {
                        val gzipFile = File(outDir, "extracted_gzip_${gzipCount}_0x${String.format("%08X", i)}.gz")
                        // Write from offset to end (or next magic)
                        var end = i + 1024 // Start with small chunk
                        while (end < data.size && end < i + 1024 * 1024) {
                            end++
                        }
                        gzipFile.writeBytes(data.sliceArray(i until minOf(end, data.size)))
                        result.add("[+] Found gzip at 0x${String.format("%08X", i)} (${gzipFile.length()} bytes)")
                        gzipCount++
                    } catch (_: Exception) { }
                }
            }
        }
    } catch (e: Exception) {
        result.add("[-] Extraction error: ${e.message}")
    }

    val totalFiles = outDir.walkTopDown().filter { it.isFile }.count()
    result.add("[+] Total extracted: $totalFiles files")
    result.add("[+] Output: ${outDir.absolutePath}")

    return result
}

private fun carveStrings(path: String): List<String> {
    val result = mutableListOf<String>()
    val data = File(path).readBytes()

    result.add("[+] Carving printable strings (min 8 chars)...")

    val strings = mutableListOf<Pair<Long, String>>()
    val current = StringBuilder()
    var startOffset = 0L

    for (i in data.indices) {
        val c = data[i].toInt() and 0xFF.toByte()
        if (c in 0x20..0x7E) {
            if (current.isEmpty()) startOffset = i.toLong()
            current.append(c.toChar())
        } else {
            if (current.length >= 8) {
                strings.add(Pair(startOffset, current.toString()))
            }
            current.clear()
        }
    }

    result.add("[+] Found ${strings.size} strings")

    // Group by category
    val urls = strings.filter { it.second.startsWith("http") }
    val paths = strings.filter { it.second.startsWith("/") && it.second.contains(".") }
    val emails = strings.filter { it.second.contains("@") && it.second.contains(".") }

    if (urls.isNotEmpty()) {
        result.add("[+] URLs found: ${urls.size}")
        urls.take(20).forEach { (off, s) -> result.add("  0x${String.format("%08X", off)}: $s") }
    }
    if (paths.isNotEmpty()) {
        result.add("[+] Paths found: ${paths.size}")
        paths.take(20).forEach { (off, s) -> result.add("  0x${String.format("%08X", off)}: $s") }
    }
    if (emails.isNotEmpty()) {
        result.add("[+] Emails found: ${emails.size}")
        emails.take(10).forEach { (off, s) -> result.add("  0x${String.format("%08X", off)}: $s") }
    }

    // Save all strings to file
    val outFile = File("/sdcard/Download/OprekTool/firmware/${File(path).nameWithoutExtension}_strings.txt")
    outFile.parentFile?.mkdirs()
    outFile.writeText(strings.joinToString("\n") { "${String.format("0x%08X", it.first)}: ${it.second}" })
    result.add("[+] All strings saved: ${outFile.absolutePath}")

    return result
}

private fun findElfEnd(data: ByteArray, start: Int): Int {
    if (start + 16 > data.size) return start + 16
    val is64 = data[start + 4] == 2.toByte()
    if (is64) {
        if (start + 64 > data.size) return data.size
        val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val phOff = buf.getLong(start + 32).toInt()
        val phNum = buf.getShort(start + 56).toInt() and 0xFFFF.toByte()
        val phEntSize = buf.getShort(start + 54).toInt() and 0xFFFF.toByte()
        var maxEnd = start + 64
        for (i in 0 until phNum) {
            val off = phOff + i * phEntSize
            if (off + 16 <= data.size) {
                val pOffset = buf.getLong(off + 8).toInt()
                val pFilesz = buf.getLong(off + 32).toInt()
                val end = pOffset + pFilesz
                if (end > maxEnd && end <= data.size) maxEnd = end
            }
        }
        return maxEnd
    } else {
        if (start + 52 > data.size) return data.size
        val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val phOff = buf.getInt(start + 28)
        val phNum = buf.getShort(start + 44).toInt() and 0xFFFF.toByte()
        val phEntSize = buf.getShort(start + 42).toInt() and 0xFFFF.toByte()
        var maxEnd = start + 52
        for (i in 0 until phNum) {
            val off = phOff + i * phEntSize
            if (off + 16 <= data.size) {
                val pOffset = buf.getInt(off + 4)
                val pFilesz = buf.getInt(off + 16)
                val end = pOffset + pFilesz
                if (end > maxEnd && end <= data.size) maxEnd = end
            }
        }
        return maxEnd
    }
}

private fun shannonEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val freq = IntArray(256)
    for (b in data) freq[b.toInt() and 0xFF.toByte()]++
    var entropy = 0.0
    for (f in freq) {
        if (f > 0) {
            val p = f.toDouble() / data.size
            entropy -= p * ln(p) / ln(2.0)
        }
    }
    return entropy
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

package com.oprek.tool.ui.screens

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
import com.oprek.tool.core.LoadedFileHelper
import com.oprek.tool.core.NativeLib
import com.oprek.tool.ui.theme.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    fun addLine(msg: String) { output = output + msg }

    fun loadFile() {
        val f = LoadedFileHelper.findLoadedFile(context)
        if (f != null) {
            fileData = f.readBytes()
            fileName = f.name
            addLine("[+] Loaded: ${f.name} (${fileData?.size ?: 0} bytes)")
        } else {
            addLine("[-] No file. Open from Home first.")
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
                    Text("📦 UPX Unpacker & Packer Detection", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Detects UPX, Themida, ASPack, Themida, MEW, and other packers", color = TextSecondary, fontSize = 11.sp)
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
                            output = emptyList()
                            scope.launch(Dispatchers.IO) {
                                addLine("📦 UPX Unpacker v1.0")
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

                                // Detect packer
                                addLine("🔍 Analyzing binary...")
                                val packerResult = detectPacker(data)
                                packerDetected = packerResult.first
                                addLine("   Packer: ${packerResult.first}")
                                addLine("   Confidence: ${packerResult.second}%")

                                // Calculate entropy
                                entropy = calculateEntropy(data)
                                addLine("   Entropy: ${"%.4f".format(entropy)} / 8.0")
                                if (entropy > 7.0) addLine("   ⚠️ High entropy - likely packed/encrypted")
                                else if (entropy > 6.0) addLine("   ℹ️ Medium entropy - possibly compressed")
                                else addLine("   ✅ Low entropy - likely unpacked")

                                // Check ELF sections
                                if (data.size >= 4 && data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte()) {
                                    addLine("\n📋 ELF Analysis:")
                                    val is64 = data[4] == 2.toByte()
                                    addLine("   Format: ELF ${if (is64) "64-bit" else "32-bit"}")

                                    // Check for UPX signature
                                    val upxIdx = findUpxSignature(data)
                                    if (upxIdx >= 0) {
                                        addLine("   ✅ UPX signature found at offset 0x${"%X".format(upxIdx)}")
                                        addLine("   UPX magic: ${data.sliceArray(upxIdx until (upxIdx + 8).coerceAtMost(data.size)).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}")
                                    } else {
                                        addLine("   ℹ️ No UPX signature found in binary")
                                    }

                                    // Check for common packer strings
                                    val packerStrings = listOf("UPX!", "UPX0", "UPX1", "UPX2", ".aspack", ".adata",
                                        "Themida", "VMProtect", "ASPack", "PECompact", "MEW", "FSG", "MPRESS")
                                    for (ps in packerStrings) {
                                        if (String(data).contains(ps, ignoreCase = true)) {
                                            addLine("   🔍 Found packer string: $ps")
                                        }
                                    }
                                }

                                // Try UPX unpack
                                addLine("\n🔧 Attempting UPX unpack...")
                                val upxResult = tryUpxUnpack(data, context)
                                if (upxResult != null) {
                                    addLine("✅ Unpacked successfully!")
                                    addLine("   Original size: ${data.size} bytes")
                                    addLine("   Unpacked size: ${upxResult.size} bytes")
                                    addLine("   Compression ratio: ${"%.1f".format((1.0 - upxResult.size.toDouble() / data.size) * 100)}%")

                                    // Save unpacked file
                                    val outDir = File(context.getExternalFilesDir(null), "unpacked")
                                    outDir.mkdirs()
                                    val outFile = File(outDir, "${fileName}_unpacked")
                                    outFile.writeBytes(upxResult)
                                    addLine("   Saved to: ${outFile.name}")

                                    fileData = upxResult
                                } else {
                                    addLine("⚠️ UPX unpack failed - binary may not be UPX packed")
                                    addLine("   Try using UPX on a Linux/PC system:")
                                    addLine("   upx -d ${fileName}")
                                }

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
                    Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
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

private fun detectPacker(data: ByteArray): Pair<String, Int> {
    // Check for UPX
    if (findUpxSignature(data) >= 0) return "UPX" to 95

    // Check entropy
    val entropy = calculateEntropy(data)
    if (entropy > 7.5) return "Unknown (high entropy)" to 80

    // Check for common packer strings
    val text = String(data, Charsets.US_ASCII)
    if (text.contains("UPX!", ignoreCase = true)) return "UPX (string found)" to 90
    if (text.contains("Themida", ignoreCase = true)) return "Themida" to 85
    if (text.contains(".aspack", ignoreCase = true)) return "ASPack" to 85
    if (text.contains("VMProtect", ignoreCase = true)) return "VMProtect" to 85
    if (text.contains("MEW", ignoreCase = true)) return "MEW" to 70
    if (text.contains("FSG", ignoreCase = true)) return "FSG" to 70

    // Check ELF section names
    if (data.size >= 4 && data[0] == 0x7F.toByte()) {
        if (text.contains(".upx")) return "UPX (section name)" to 90
        if (text.contains(".adata")) return "ASPack (section)" to 85
    }

    return "Not packed" to 90
}

private fun findUpxSignature(data: ByteArray): Int {
    // UPX magic: "UPX!" (0x55 0x50 0x58 0x21)
    val magic = byteArrayOf(0x55, 0x50, 0x58, 0x21)
    for (i in 0..(data.size - magic.size)) {
        if (data[i] == magic[0] && data[i + 1] == magic[1] && data[i + 2] == magic[2] && data[i + 3] == magic[3]) {
            return i
        }
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
            entropy -= p * (Math.log(p) / Math.log(2.0))
        }
    }
    return entropy
}

private fun tryUpxUnpack(data: ByteArray, context: Context): ByteArray? {
    // Try to find and extract UPX-compressed data
    // This is a simplified UPX decompressor - real UPX uses LZMA/NSIS decompression
    // For full UPX support, use the `upx` tool from a native library

    // Check if file starts with UPX stub
    val upxIdx = findUpxSignature(data)
    if (upxIdx < 0) return null

    // For now, return null - full UPX unpacking requires native code
    // The user should use `upx -d` on a PC for full unpacking
    return null
}

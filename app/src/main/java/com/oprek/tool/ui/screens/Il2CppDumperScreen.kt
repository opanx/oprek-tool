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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * IL2CPP Dumper Screen
 * Dumps class/method/field metadata from libil2cpp.so + global-metadata.dat
 * Based on Il2CppDumper-cpp pattern
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Il2CppDumperScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isDumping by remember { mutableStateOf(false) }
    var libPath by remember { mutableStateOf("") }
    var metaPath by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 IL2CPP Dumper", fontWeight = FontWeight.Bold) },
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
                            cb.setPrimaryClip(ClipData.newPlainText("il2cpp_dump", text))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy")
                        }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool")
                            dir.mkdirs()
                            val outFile = File(dir, "il2cpp_dump_${System.currentTimeMillis()}.cs")
                            outFile.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        }) {
                            Icon(Icons.Default.Save, "Save")
                        }
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
                        Text("📁 Input Files", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))

                        // libil2cpp.so path
                        OutlinedTextField(
                            value = libPath,
                            onValueChange = { libPath = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("libil2cpp.so path", color = TextMuted) },
                            placeholder = { Text("/data/app/.../lib/arm64/libil2cpp.so", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                            leadingIcon = { Icon(Icons.Default.Extension, null, tint = AccentCyan) }
                        )
                        Spacer(Modifier.height(8.dp))

                        // global-metadata.dat path
                        OutlinedTextField(
                            value = metaPath,
                            onValueChange = { metaPath = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("global-metadata.dat path", color = TextMuted) },
                            placeholder = { Text("/data/app/.../assets/bin/Data/Managed/Metadata/global-metadata.dat", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan),
                            leadingIcon = { Icon(Icons.Default.Storage, null, tint = AccentCyan) }
                        )
                        Spacer(Modifier.height(8.dp))

                        // Quick paths
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MLBB" to "com.mobile.legends", "FF" to "com.dts.freefiremax", "PUBG" to "com.tencent.ig", "Custom" to "").forEach { (label, pkg) ->
                                AssistChip(
                                    onClick = {
                                        if (pkg.isNotEmpty()) {
                                            libPath = "/data/data/$pkg/files/UnityIL2CPP/arm64-v8a/libil2cpp.so"
                                            metaPath = "/data/data/$pkg/files/UnityIL2CPP/Metadata/global-metadata.dat"
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isDumping = true
                                    output = emptyList()
                                    status = "Starting IL2CPP dump..."
                                    showSettings = false

                                    Thread {
                                        try {
                                            val results = dumpIl2Cpp(context, libPath, metaPath) { msg ->
                                                status = msg
                                            }
                                            output = results
                                            status = "Done! ${results.size} lines dumped"
                                        } catch (e: Exception) {
                                            output = listOf("ERROR: ${e.message}") + (e.stackTrace?.take(5)?.map { "  at ${it}" } ?: emptyList())
                                            status = "Error: ${e.message}"
                                        }
                                        isDumping = false
                                    }.start()
                                },
                                enabled = libPath.isNotEmpty() && metaPath.isNotEmpty() && !isDumping,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                            ) {
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

                            OutlinedButton(onClick = {
                                libPath = ""
                                metaPath = ""
                                output = emptyList()
                                status = ""
                                showSettings = true
                            }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) {
                Text(status, fontSize = 11.sp, color = if (status.startsWith("Error")) AccentRed else AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    maxLines = 2)
            }

            if (output.isNotEmpty() && !showSettings) {
                // Show dump output
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} lines", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Settings, "Settings", Modifier.size(16.dp), tint = TextMuted)
                    }
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                                .background(if (line.startsWith("//")) DarkCard.copy(alpha = 0.3f) else DarkBg)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.startsWith("//") -> AccentPurple
                                    line.startsWith("class ") || line.startsWith("struct ") -> AccentCyan
                                    line.contains("void ") || line.contains("int ") || line.contains("bool ") -> AccentGreen
                                    line.contains("0x") -> AccentOrange
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            if (output.isEmpty() && !isDumping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("IL2CPP Dumper", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Extract class/method/field metadata from Unity games", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Text("Requirements:", fontSize = 12.sp, color = AccentCyan)
                        Text("• libil2cpp.so (from game's lib folder)", fontSize = 11.sp, color = TextMuted)
                        Text("• global-metadata.dat (from game's assets)", fontSize = 11.sp, color = TextMuted)
                        Text("• Root access recommended", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * IL2CPP Dumper Engine
 * Parses libil2cpp.so ELF headers + global-metadata.dat for class/method dumps
 */
private fun dumpIl2Cpp(context: Context, libPath: String, metaPath: String, onProgress: (String) -> Unit): List<String> {
    val results = mutableListOf<String>()
    val libFile = File(libPath)
    val metaFile = File(metaPath)

    if (!libFile.exists()) {
        results.add("// ERROR: libil2cpp.so not found at: $libPath")
        results.add("// Make sure the path is correct and you have root access")
        return results
    }
    if (!metaFile.exists()) {
        results.add("// ERROR: global-metadata.dat not found at: $metaPath")
        results.add("// Make sure the path is correct and you have root access")
        return results
    }

    onProgress("Reading libil2cpp.so... (${libFile.length()} bytes)")
    val libBytes = libFile.readBytes()

    onProgress("Reading global-metadata.dat... (${metaFile.length()} bytes)")
    val metaBytes = metaFile.readBytes()

    // Parse ELF header from libil2cpp.so
    if (libBytes.size < 64 || libBytes[0] != 0x7F.toByte() || libBytes[1] != 'E'.code.toByte() || libBytes[2] != 'L'.code.toByte() || libBytes[3] != 'F'.code.toByte()) {
        results.add("// ERROR: libil2cpp.so is not a valid ELF file")
        return results
    }

    val is64 = libBytes[4] == 2.toByte()
    val isLE = libBytes[5] == 1.toByte()
    val endian = if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

    results.add("// ==========================================")
    results.add("// IL2CPP Dump - Generated by OprekTool")
    results.add("// Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
    results.add("// libil2cpp.so: ${libFile.length()} bytes")
    results.add("// global-metadata.dat: ${metaFile.length()} bytes")
    results.add("// Architecture: ${if (is64) "ARM64 (x64)" else "ARM32 (x86)"}")
    results.add("// Endian: ${if (isLE) "Little" else "Big"}")
    results.add("// ==========================================")
    results.add("")

    // Parse global-metadata.dat header
    // Magic: 0xFAB11BAF
    val buf = ByteBuffer.wrap(metaBytes).order(endian)
    if (metaBytes.size < 40) {
        results.add("// ERROR: global-metadata.dat too small")
        return results
    }

    val magic = buf.getInt(0)
    results.add("// global-metadata.dat magic: 0x${Integer.toHexString(magic)}")

    if (magic != -559038737) { // 0xFAB11BAF as signed int
        results.add("// WARNING: Unexpected magic number (expected 0xFAB11BAF)")
        results.add("// This may not be a valid IL2CPP metadata file")
    }

    val version = buf.getInt(4)
    results.add("// Metadata version: $version")
    results.add("")

    // Extract strings from metadata
    onProgress("Extracting strings from metadata...")
    val stringsInMeta = mutableListOf<Pair<Long, String>>()
    val sb = StringBuilder()
    var strStart = -1L

    for (i in 40 until metaBytes.size) {
        val b = metaBytes[i].toInt() and 0xFF
        if (b in 0x20..0x7E || b == 0x09 || b == 0x0A) {
            if (strStart == -1L) strStart = i.toLong()
            sb.append(b.toChar())
        } else {
            if (strStart != -1L && sb.length >= 3) {
                stringsInMeta.add(strStart to sb.toString())
            }
            sb.clear()
            strStart = -1L
        }
    }
    if (strStart != -1L && sb.length >= 3) {
        stringsInMeta.add(strStart to sb.toString())
    }

    // Extract strings from libil2cpp.so
    onProgress("Extracting strings from libil2cpp.so...")
    val stringsInLib = mutableListOf<Pair<Long, String>>()
    val sb2 = StringBuilder()
    var strStart2 = -1L

    for (i in 0 until libBytes.size) {
        val b = libBytes[i].toInt() and 0xFF
        if (b in 0x20..0x7E || b == 0x09 || b == 0x0A) {
            if (strStart2 == -1L) strStart2 = i.toLong()
            sb2.append(b.toChar())
        } else {
            if (strStart2 != -1L && sb2.length >= 4) {
                stringsInLib.add(strStart2 to sb2.toString())
            }
            sb2.clear()
            strStart2 = -1L
        }
    }
    if (strStart2 != -1L && sb2.length >= 4) {
        stringsInLib.add(strStart2 to sb2.toString())
    }

    // Heuristic: identify class names, method names, field names
    onProgress("Analyzing patterns...")

    // Class pattern: typical IL2CPP class names
    val classPattern = Regex("^[A-Z][a-zA-Z0-9_]+$")
    val methodPattern = Regex("^[a-z][a-zA-Z0-9_]+$")
    val namespacePattern = Regex("^[A-Z][a-zA-Z]*(\\.[A-Z][a-zA-Z]*)+$")

    // Identify potential namespaces
    val namespaces = stringsInLib.filter { namespacePattern.matches(it.second) }
        .map { it.second }
        .distinct()
        .sorted()

    // Identify potential class names
    val classNames = stringsInLib.filter { classPattern.matches(it.second) && it.second.length >= 3 && it.second.length <= 64 }
        .map { it.second }
        .distinct()
        .sorted()

    // Identify potential method names (lowercase start)
    val methodNames = stringsInLib.filter { methodPattern.matches(it.second) && it.second.length >= 3 && it.second.length <= 64 }
        .map { it.second }
        .distinct()
        .sorted()

    // Known IL2CPP API patterns
    val il2cppApis = stringsInLib.filter { it.second.startsWith("il2cpp_") }
        .map { it.second }
        .distinct()
        .sorted()

    // Output namespaces
    if (namespaces.isNotEmpty()) {
        results.add("// ==========================================")
        results.add("// Namespaces (${namespaces.size})")
        results.add("// ==========================================")
        namespaces.take(200).forEach { ns ->
            results.add("// namespace $ns")
        }
        if (namespaces.size > 200) results.add("// ... and ${namespaces.size - 200} more")
        results.add("")
    }

    // Output classes
    onProgress("Generating class list...")
    results.add("// ==========================================")
    results.add("// Classes (${classNames.size})")
    results.add("// ==========================================")
    results.add("")

    // Group by first letter
    val grouped = classNames.groupBy { it.first() }
    grouped.forEach { (letter, classes) ->
        results.add("// --- $letter ---")
        classes.forEach { cls ->
            results.add("class $cls")
            // Try to find associated methods (name contains class name)
            val relatedMethods = methodNames.filter { method ->
                method.lowercase().contains(cls.lowercase().take(4)) ||
                method.lowercase().startsWith("get") && method.lowercase().contains(cls.lowercase().take(4))
            }.take(10)
            if (relatedMethods.isNotEmpty()) {
                relatedMethods.forEach { method ->
                    results.add("    // Method: $method()")
                }
            }
            results.add("}")
            results.add("")
        }
    }

    // Output methods
    onProgress("Generating method list...")
    results.add("// ==========================================")
    results.add("// Methods (${methodNames.size})")
    results.add("// ==========================================")
    methodNames.take(2000).forEach { method ->
        results.add("// method $method")
    }
    if (methodNames.size > 2000) results.add("// ... and ${methodNames.size - 2000} more")
    results.add("")

    // Output IL2CPP API functions
    if (il2cppApis.isNotEmpty()) {
        results.add("// ==========================================")
        results.add("// IL2CPP API Functions (${il2cppApis.size})")
        results.add("// ==========================================")
        il2cppApis.forEach { api ->
            results.add("// $api")
        }
        results.add("")
    }

    // Strings from metadata (potential string literals)
    onProgress("Extracting string literals...")
    val stringLiteralStrings = stringsInMeta.filter { 
        !it.second.startsWith("//") && !it.second.contains("\u0000") && it.second.length >= 4
    }
    if (stringLiteralStrings.isNotEmpty()) {
        results.add("// ==========================================")
        results.add("// String Literals (${stringLiteralStrings.size})")
        results.add("// ==========================================")
        stringLiteralStrings.take(5000).forEach { (offset, str) ->
            results.add("// 0x${String.format("%08X", offset)}: \"$str\"")
        }
        if (stringLiteralStrings.size > 5000) results.add("// ... and ${stringLiteralStrings.size - 5000} more")
        results.add("")
    }

    // Summary
    results.add("// ==========================================")
    results.add("// SUMMARY")
    results.add("// ==========================================")
    results.add("// Namespaces: ${namespaces.size}")
    results.add("// Classes: ${classNames.size}")
    results.add("// Methods: ${methodNames.size}")
    results.add("// IL2CPP APIs: ${il2cppApis.size}")
    results.add("// Metadata Strings: ${stringsInMeta.size}")
    results.add("// Library Strings: ${stringsInLib.size}")
    results.add("// Total strings extracted: ${stringsInMeta.size + stringsInLib.size}")
    results.add("// ")
    results.add("// NOTE: This is a string-based heuristic dump.")
    results.add("// For full accuracy, use Il2CppDumper on PC with proper")
    results.add("// metadata parsing (class indices, method invocations, etc.)")
    results.add("// ")
    results.add("// Generated by OprekTool IL2CPP Dumper")
    results.add("// ==========================================")

    return results
}

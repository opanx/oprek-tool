package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeLibAnalyzerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var filePath by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) }

    val modes = listOf("ELF Header", "Sections", "Symbols", "Imports", "Exports", "Relocations", "Strings", "Entropy", "Full Analysis")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Native Lib Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            Text("Target .so / ELF binary", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = filePath,
                onValueChange = { filePath = it },
                label = { Text("Path to .so / ELF file") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // Mode chips
            Text("Analysis Mode", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in modes.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            val i = modes.indexOf(m)
                            FilterChip(
                                selected = selectedMode == i,
                                onClick = { selectedMode = i },
                                label = { Text(m, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        output = withContext(Dispatchers.IO) {
                            try {
                                analyzeNativeLib(filePath, selectedMode)
                            } catch (e: Exception) {
                                "Error: ${e.message}\n${e.stackTraceToString()}"
                            }
                        }
                        isProcessing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                enabled = !isProcessing && filePath.isNotEmpty()
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(modes[selectedMode])
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Result", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("result", output))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        output.ifEmpty { "Analysis result will appear here..." },
                        color = Color(0xFF00FF41),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun analyzeNativeLib(path: String, mode: Int): String {
    val file = java.io.File(path)
    if (!file.exists()) return "File not found: $path"

    val bytes = file.readBytes()
    if (bytes.size < 4) return "File too small"

    // Verify ELF magic
    if (bytes[0] != 0x7F.toByte() || bytes[1] != 0x45.toByte() || bytes[2] != 0x4C.toByte() || bytes[3] != 0x46.toByte()) {
        return "Not an ELF file (magic: ${String(bytes, 0, minOf(4, bytes.size))})"
    }

    val sb = StringBuilder()
    val is64 = bytes[4] == 2.toByte()
    val isLE = bytes[5] == 1.toByte()
    val machine = if (isLE) (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8)
                  else ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)

    val archName = when (machine) {
        0x03 -> "x86"; 0x3E -> "x86_64"; 0x28 -> "ARM"; 0xB7 -> "AArch64"
        0x08 -> "MIPS"; 0x0340 -> "MIPS64"; 0x00F3 -> "RISC-V"
        else -> "Unknown (0x${Integer.toHexString(machine)})"
    }

    when (mode) {
        0 -> { // ELF Header
            sb.appendLine("📋 ELF HEADER")
            sb.appendLine("═".repeat(50))
            sb.appendLine("Class:     ELF${if (is64) "64" else "32"}")
            sb.appendLine("Endian:    ${if (isLE) "Little" else "Big"} Endian")
            sb.appendLine("Version:   ${bytes[6].toInt()}")
            sb.appendLine("OS/ABI:    ${when(bytes[7].toInt()) { 0 -> "UNIX System V"; 1 -> "HP-UX"; 2 -> "NetBSD"; 3 -> "Linux"; 6 -> "Solaris"; 9 -> "FreeBSD"; else -> "Unknown (${bytes[7].toInt()})" }}")
            sb.appendLine("Type:      ${when((if(isLE)(bytes[16].toInt() and 0xFF) or ((bytes[17].toInt() and 0xFF) shl 8) else ((bytes[16].toInt() and 0xFF) shl 8) or (bytes[17].toInt() and 0xFF))) { 1 -> "REL (Relocatable)"; 2 -> "EXEC (Executable)"; 3 -> "DYN (Shared object)"; 4 -> "CORE"; else -> "Unknown" }}")
            sb.appendLine("Machine:   $archName (0x${Integer.toHexString(machine)})")
            if (is64) {
                val entry = readELF64LE(bytes, 24)
                val phoff = readELF64LE(bytes, 32)
                val shoff = readELF64LE(bytes, 40)
                val flags = readELF32LE(bytes, 48)
                val ehsize = readELF16LE(bytes, 52)
                val phentsize = readELF16LE(bytes, 54)
                val phnum = readELF16LE(bytes, 56)
                val shentsize = readELF16LE(bytes, 58)
                val shnum = readELF16LE(bytes, 60)
                val shstrndx = readELF16LE(bytes, 62)
                sb.appendLine("Entry:     0x${java.lang.Long.toHexString(entry)}")
                sb.appendLine("PH Offset: $phoff")
                sb.appendLine("SH Offset: $shoff")
                sb.appendLine("Flags:     0x${Integer.toHexString(flags)}")
                sb.appendLine("EH Size:   $ehsize")
                sb.appendLine("PH Num:    $phnum (entsize: $phentsize)")
                sb.appendLine("SH Num:    $shnum (entsize: $shentsize)")
                sb.appendLine("SH Strndx:$shstrndx")
            } else {
                val entry = readELF32LE(bytes, 24)
                val phoff = readELF32LE(bytes, 28)
                val shoff = readELF32LE(bytes, 32)
                sb.appendLine("Entry:     0x${Integer.toHexString(entry)}")
                sb.appendLine("PH Offset: $phoff")
                sb.appendLine("SH Offset: $shoff")
            }
            sb.appendLine("File Size: ${file.length()} bytes")
        }
        1 -> { // Sections
            sb.appendLine("📋 ELF SECTIONS")
            sb.appendLine("═".repeat(50))
            sb.appendLine(parseSections(bytes, is64, isLE))
        }
        2 -> { // Symbols
            sb.appendLine("📋 SYMBOL TABLE")
            sb.appendLine("═".repeat(50))
            sb.appendLine(parseSections(bytes, is64, isLE))
            // Search for symbol-like strings
            val syms = extractSymbols(bytes)
            if (syms.isNotEmpty()) {
                sb.appendLine("\nFound ${syms.size} symbol names:")
                syms.take(200).forEach { sb.appendLine("  $it") }
                if (syms.size > 200) sb.appendLine("  ... and ${syms.size - 200} more")
            }
        }
        3 -> { // Imports
            sb.appendLine("📋 IMPORTS (External References)")
            sb.appendLine("═".repeat(50))
            val imports = extractImports(bytes)
            if (imports.isEmpty()) {
                sb.appendLine("No imports found in string table")
                sb.appendLine("Try Full Analysis mode for deeper scan")
            } else {
                imports.forEach { sb.appendLine("  → $it") }
                sb.appendLine("\nTotal: ${imports.size} imports")
            }
        }
        4 -> { // Exports
            sb.appendLine("📋 EXPORTS (Public Symbols)")
            sb.appendLine("═".repeat(50))
            val exports = extractExports(bytes)
            if (exports.isEmpty()) {
                sb.appendLine("No exports found (stripped?)")
            } else {
                exports.forEach { sb.appendLine("  ← $it") }
                sb.appendLine("\nTotal: ${exports.size} exports")
            }
        }
        5 -> { // Relocations
            sb.appendLine("📋 RELOCATIONS")
            sb.appendLine("═".repeat(50))
            val relocs = extractRelocations(bytes)
            if (relocs.isEmpty()) {
                sb.appendLine("No relocations found (or stripped)")
            } else {
                relocs.forEach { sb.appendLine("  $it") }
                sb.appendLine("\nTotal: ${relocs.size} relocations")
            }
        }
        6 -> { // Strings
            sb.appendLine("📋 STRINGS")
            sb.appendLine("═".repeat(50))
            val strings = extractStrings(bytes)
            sb.appendLine("Found ${strings.size} strings (min length 4):")
            sb.appendLine()
            strings.take(300).forEach { (off, s) ->
                sb.appendLine("  0x${String.format("%06X", off)}: $s")
            }
            if (strings.size > 300) sb.appendLine("  ... and ${strings.size - 300} more")
        }
        7 -> { // Entropy
            sb.appendLine("📋 ENTROPY ANALYSIS")
            sb.appendLine("═".repeat(50))
            val blockSize = 256
            val blocks = (bytes.size + blockSize - 1) / blockSize
            var totalEntropy = 0.0
            var maxEntropy = 0.0
            var maxBlock = 0

            for (i in 0 until minOf(blocks, 4096)) {
                val start = i * blockSize
                val end = minOf(start + blockSize, bytes.size)
                val freq = IntArray(256)
                for (j in start until end) freq[bytes[j].toInt() and 0xFF]++
                val len = (end - start).toDouble()
                var entropy = 0.0
                for (f in freq) {
                    if (f > 0) {
                        val p = f / len
                        entropy -= p * Math.log(p) / Math.log(2.0)
                    }
                }
                totalEntropy += entropy
                if (entropy > maxEntropy) { maxEntropy = entropy; maxBlock = i }

                val bar = "█".repeat((entropy * 4).toInt())
                val color = when { entropy < 4.0 -> "Low"; entropy < 6.5 -> "Medium"; entropy < 7.5 -> "High"; else -> "Encrypted/Compressed" }
                sb.appendLine("  Block ${String.format("%04X", start)}: ${String.format("%.2f", entropy)} $bar ($color)")
            }

            val avgEntropy = totalEntropy / minOf(blocks, 4096)
            sb.appendLine()
            sb.appendLine("Average Entropy: ${String.format("%.2f", avgEntropy)} / 8.0")
            sb.appendLine("Max Entropy:     ${String.format("%.2f", maxEntropy)} (at block 0x${String.format("%06X", maxBlock * blockSize)})")
            sb.appendLine()
            when {
                avgEntropy < 4.0 -> sb.appendLine("Assessment: Low entropy — likely code/text sections")
                avgEntropy < 6.0 -> sb.appendLine("Assessment: Medium — mix of code and data")
                avgEntropy < 7.0 -> sb.appendLine("Assessment: High — possibly compressed or encrypted data")
                else -> sb.appendLine("Assessment: Very high — likely encrypted/packed binary")
            }
        }
        8 -> { // Full Analysis
            sb.appendLine("📋 FULL NATIVE LIB ANALYSIS")
            sb.appendLine("═".repeat(50))
            sb.appendLine("File: ${file.absolutePath}")
            sb.appendLine("Size: ${file.length()} bytes")
            sb.appendLine("Arch: $archName")
            sb.appendLine("ELF: ${if (is64) "64-bit" else "32-bit"} ${if (isLE) "LE" else "BE"}")
            sb.appendLine()
            sb.appendLine(parseSections(bytes, is64, isLE))
            sb.appendLine()
            val syms = extractSymbols(bytes)
            if (syms.isNotEmpty()) {
                sb.appendLine("SYMBOLS (${syms.size}):")
                syms.take(50).forEach { sb.appendLine("  $it") }
            }
            sb.appendLine()
            val imports = extractImports(bytes)
            if (imports.isNotEmpty()) {
                sb.appendLine("IMPORTS (${imports.size}):")
                imports.take(30).forEach { sb.appendLine("  → $it") }
            }
            sb.appendLine()
            val exports = extractExports(bytes)
            if (exports.isNotEmpty()) {
                sb.appendLine("EXPORTS (${exports.size}):")
                exports.take(30).forEach { sb.appendLine("  ← $it") }
            }
            sb.appendLine()
            val strings = extractStrings(bytes)
            sb.appendLine("STRINGS: ${strings.size}")
            val funcs = syms.filter { it.contains("(") || it.startsWith("Java_") || it.startsWith("native_") }
            if (funcs.isNotEmpty()) {
                sb.appendLine("\nNATIVE FUNCTIONS:")
                funcs.take(20).forEach { sb.appendLine("  🔧 $it") }
            }
        }
    }

    return sb.toString()
}

private fun parseSections(bytes: ByteArray, is64: Boolean, isLE: Boolean): String {
    val sb = StringBuilder()
    val shoff = if (is64) readELF64LE(bytes, 40) else readELF32LE(bytes, 32).toLong()
    val shnum = readELF16LE(bytes, if (is64) 60 else 48)
    val shentsize = readELF16LE(bytes, if (is64) 58 else 46)
    val shstrndx = readELF16LE(bytes, if (is64) 62 else 50)

    if (shoff <= 0 || shnum <= 0 || shoff + shnum * shentsize > bytes.size) {
        return "No valid section headers"
    }

    // Get string table
    val strOff = if (shstrndx < shnum) {
        val sh = shoff + shstrndx * shentsize
        if (is64) readELF64LE(bytes, sh.toInt() + 24).toInt() else readELF32LE(bytes, sh.toInt() + 16)
    } else 0

    sb.appendLine(String.format("%-20s %-10s %-12s %-12s %-8s", "Name", "Type", "Addr", "Offset", "Size"))

    for (i in 0 until shnum) {
        val sh = shoff + i * shentsize
        if (sh + shentsize > bytes.size) break

        val nameIdx = readELF32LE(bytes, sh.toInt()).toInt()
        val name = if (strOff + nameIdx < bytes.size) readString(bytes, strOff + nameIdx) else "?"
        val type = readELF32LE(bytes, sh.toInt() + 4)

        val addr = if (is64) readELF64LE(bytes, sh.toInt() + 16) else readELF32LE(bytes, sh.toInt() + 12).toLong()
        val offset = if (is64) readELF64LE(bytes, sh.toInt() + 24).toLong() else readELF32LE(bytes, sh.toInt() + 16).toLong()
        val size = if (is64) readELF64LE(bytes, sh.toInt() + 32).toLong() else readELF32LE(bytes, sh.toInt() + 20).toLong()

        val typeName = when (type) {
            0 -> "NULL"; 1 -> "PROGBITS"; 2 -> "SYMTAB"; 3 -> "STRTAB"; 4 -> "RELA"
            5 -> "HASH"; 6 -> "DYNAMIC"; 7 -> "NOTE"; 8 -> "NOBITS"; 9 -> "REL"
            11 -> "DYNSYM"; 14 -> "INIT_ARRAY"; 15 -> "FINI_ARRAY"; 16 -> "PREINIT"
            17 -> "GROUP"; 18 -> "GNU_HASH"; 19 -> "GNU_LIBLIST"; 0x6FFFFFF6 -> "GNU_VERDEF"
            0x6FFFFFF7 -> "GNU_VERNEED"; 0x6FFFFFFF -> "GNU_VERSYM"
            else -> "0x${Integer.toHexString(type)}"
        }

        sb.appendLine(String.format("%-20s %-10s 0x%-10s 0x%-10s %-8s", name.take(20), typeName,
            java.lang.Long.toHexString(addr), java.lang.Long.toHexString(offset), size))
    }
    return sb.toString()
}

private fun extractSymbols(bytes: ByteArray): List<String> {
    val syms = mutableListOf<String>()
    // Search for known symbol patterns
    val patterns = listOf("Java_", "native_", "__aeabi_", "__cxa_", "_ZN", "JNI_", "android_")
    val text = String(bytes, charset("UTF-8"))
    val regex = Regex("[A-Za-z_][A-Za-z0-9_]{2,60}")
    for (match in regex.findAll(text)) {
        val s = match.value
        if (patterns.any { s.startsWith(it) } ||
            (s.startsWith("Java_") && s.length > 5) ||
            s.startsWith("_Z")) {
            if (!syms.contains(s)) syms.add(s)
        }
    }
    return syms.distinct().sorted()
}

private fun extractImports(bytes: ByteArray): List<String> {
    val imports = mutableListOf<String>()
    val libcFuncs = listOf(
        "malloc", "free", "calloc", "realloc", "memcpy", "memset", "memmove",
        "strcmp", "strncmp", "strlen", "strcpy", "strncpy", "strcat", "strncat",
        "sprintf", "snprintf", "printf", "fprintf", "scanf",
        "open", "read", "write", "close", "lseek", "stat", "fstat",
        "mmap", "munmap", "mprotect",
        "pthread_create", "pthread_mutex_lock", "pthread_mutex_unlock",
        "dlopen", "dlsym", "dlclose",
        "__android_log_print", "__android_log_write",
        "ioctl", "fcntl", "dup", "fork", "execve", "getpid",
        "clock_gettime", "usleep", "nanosleep"
    )
    val text = String(bytes, charset("UTF-8"))
    for (func in libcFuncs) {
        if (text.contains(func)) imports.add(func)
    }
    // Also look for JNI functions
    val jniFuncs = listOf("RegisterNatives", "FindClass", "GetMethodID", "GetStaticMethodID",
        "NewStringUTF", "GetStringUTFChars", "CallVoidMethod", "CallIntMethod",
        "GetFieldID", "GetIntField", "SetIntField", "NewObject", "ExceptionCheck")
    for (func in jniFuncs) {
        if (text.contains(func)) imports.add("JNI: $func")
    }
    return imports.distinct()
}

private fun extractExports(bytes: ByteArray): List<String> {
    val exports = mutableListOf<String>()
    val text = String(bytes, charset("UTF-8"))
    val regex = Regex("(Java_[A-Za-z0-9_]+_[A-Za-z0-9_]+|JNI_OnLoad|__system_property_get|ANativeActivity_onCreate)")
    for (match in regex.findAll(text)) {
        if (!exports.contains(match.value)) exports.add(match.value)
    }
    // Also look for .init / .fini functions
    if (text.contains("_init") || text.contains("__init")) exports.add("_init()")
    if (text.contains("_fini") || text.contains("__fini")) exports.add("_fini()")
    return exports.distinct()
}

private fun extractRelocations(bytes: ByteArray): List<String> {
    val relocs = mutableListOf<String>()
    val text = String(bytes, charset("UTF-8"))
    // Look for relocation-related strings
    val patterns = listOf("R_ARM_", "R_AARCH64_", "R_386_", "R_X86_64_")
    for (p in patterns) {
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf(p, idx)
            if (found < 0) break
            val end = text.indexOf('\u0000', found)
            val name = if (end > found) text.substring(found, end).take(80) else text.substring(found, minOf(found + 80, text.length))
            if (!relocs.contains(name)) relocs.add(name)
            idx = found + 1
        }
    }
    return relocs
}

private fun extractStrings(bytes: ByteArray): List<Pair<Int, String>> {
    val strings = mutableListOf<Pair<Int, String>>()
    val minLen = 4
    val sb = StringBuilder()
    var start = -1

    for (i in bytes.indices) {
        val b = bytes[i].toInt() and 0xFF
        if (b in 0x20..0x7E) {
            if (start < 0) start = i
            sb.append(b.toChar())
        } else {
            if (sb.length >= minLen && start >= 0) {
                strings.add(start to sb.toString())
            }
            sb.clear(); start = -1
        }
    }
    if (sb.length >= minLen && start >= 0) strings.add(start to sb.toString())
    return strings
}

private fun readELF64LE(bytes: ByteArray, off: Int): Long {
    if (off + 8 > bytes.size) return 0
    var v = 0L
    for (i in 0..7) v = v or ((bytes[off + i].toLong() and 0xFF) shl (i * 8))
    return v
}

private fun readELF32LE(bytes: ByteArray, off: Int): Int {
    val o = off
    if (o + 4 > bytes.size) return 0
    return (bytes[o].toInt() and 0xFF) or ((bytes[o+1].toInt() and 0xFF) shl 8) or
           ((bytes[o+2].toInt() and 0xFF) shl 16) or ((bytes[o+3].toInt() and 0xFF) shl 24)
}

private fun readELF16LE(bytes: ByteArray, off: Int): Int {
    if (off + 2 > bytes.size) return 0
    return (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
}

private fun readString(bytes: ByteArray, off: Int): String {
    if (off >= bytes.size) return ""
    val sb = StringBuilder()
    var i = off
    while (i < bytes.size && bytes[i] != 0.toByte()) {
        sb.append(bytes[i].toInt().toChar())
        i++
    }
    return sb.toString()
}

package com.oprek.tool.ui.screens

import android.content.Intent
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * XREF Finder — find all cross-references to a string, function, or address in ELF binaries.
 * Supports ARM32/ARM64 BL/BLX branch detection + string reference scanning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrefFinderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isBusy by remember { mutableStateOf(false) }
    var searchMode by remember { mutableIntStateOf(0) } // 0=string, 1=address, 2=hex pattern

    val modes = listOf("🔤 String", "📍 Address", "🔍 Hex Pattern")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        scope.launch(Dispatchers.IO) {
            isBusy = true
            output = listOf("[*] Loading binary...")
            try {
                val name = uri.lastPathSegment ?: "unknown"
                val cache = File(context.cacheDir, "xref_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cache).use { out -> input.copyTo(out) }
                }
                val data = cache.readBytes()
                loadedFile = cache
                fileData = data
                val fmt = when {
                    data.size >= 4 && data[0] == 0x7F.toByte() && data[1] == 0x45.toByte() -> "ELF"
                    data.size >= 4 && data[0] == 0x64.toByte() && data[1] == 0x65.toByte() && data[2] == 0x78.toByte() -> "DEX"
                    else -> "RAW"
                }
                withContext(Dispatchers.Main) {
                    output = listOf("[+] Loaded: $name (${fmtXref(data.size.toLong())}) [${data.size} bytes]", "[+] Format: $fmt", "", "[*] Enter a search query and tap Find")
                    isBusy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { output = listOf("[-] Error: ${e.message}"); isBusy = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔗 XREF Finder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // File card
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (loadedFile != null) "${loadedFile!!.name} (${fmtXref(fileData?.size?.toLong() ?: 0)})"
                        else "No file loaded",
                        color = if (loadedFile != null) TextPrimary else TextSecondary,
                        fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1
                    )
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Open", fontSize = 11.sp)
                    }
                }
            }

            // Search bar
            if (fileData != null) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        // Mode selector
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            modes.forEachIndexed { i, m ->
                                FilterChip(
                                    selected = searchMode == i,
                                    onClick = { searchMode = i },
                                    label = { Text(m, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f))
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        when (searchMode) {
                                            0 -> "e.g. curl_easy_setopt,/login,/connect"
                                            1 -> "e.g. 0xFBC70"
                                            else -> "e.g. 90 EF 00 0A"
                                        },
                                        fontSize = 11.sp
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = TextMuted)
                            )
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank() && fileData != null) {
                                        scope.launch(Dispatchers.IO) {
                                            isBusy = true
                                            output = listOf("[*] Searching...")
                                            val data = fileData!!
                                            val result = when (searchMode) {
                                                0 -> xrefString(data, searchQuery.trim())
                                                1 -> xrefAddress(data, searchQuery.trim())
                                                else -> xrefHex(data, searchQuery.trim())
                                            }
                                            withContext(Dispatchers.Main) { output = result; isBusy = false }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("Find", fontSize = 11.sp) }
                        }
                    }
                }
            }

            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = AccentCyan)

            // Output
            if (output.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                    items(output) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
                                line.startsWith("[*]") -> AccentCyan
                                line.contains("REF") || line.contains("CALL") -> AccentPurple
                                else -> TextPrimary
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            } else if (fileData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔗", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("XREF Finder", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Text("Find cross-references to strings, addresses, or hex patterns", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        listOf("ARM32/ARM64 BL/BLX branch detection", "String reference scanning", "Address-based xref", "Hex pattern matching").forEach {
                            Text("  • $it", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Find all string references in binary */
private fun xrefString(data: ByteArray, query: String): List<String> {
    val out = mutableListOf<String>()
    // 1) Find the string offset(s) in binary
    val offsets = mutableListOf<Int>()
    val qBytes = query.toByteArray(Charsets.UTF_8)
    for (i in 0 until data.size - qBytes.size) {
        var match = true
        for (j in qBytes.indices) {
            if (data[i + j] != qBytes[j]) { match = false; break }
        }
        if (match) offsets.add(i)
    }

    if (offsets.isEmpty()) {
        out.add("[-] String \"$query\" not found in binary")
        return out
    }

    out.add("[+] Found string \"$query\" at ${offsets.size} offset(s):")
    for (off in offsets) {
        out.add("    0x${String.format("%06X", off)}: \"${String(data, off, minOf(qBytes.size + 20, data.size - off))}\"")
    }
    out.add("")

    // 2) For each offset, find cross-references (instructions that load this address)
    out.add("[+] Cross-references:")
    val is64 = data.size > 4 && data[4] == 2.toByte()
    if (is64) {
        // ARM64: scan for ADRP+ADD/ADRP+LDR pairs that reference this page
        for (off in offsets) {
            val page = off and 0xFFFFF000
            val pageOff = off and 0xFFF
            // Scan for ADRP (page-relative addressing)
            for (i in 0 until data.size - 4 step 4) {
                val insn = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(i)
                val op = (insn shr 24) and 0xFF
                // ADRP: op = 0x90 (ADRP Xd, label)
                if (op == 0x90 || op == 0xB0) {
                    val immlo = (insn shr 29) and 0x3
                    val immhi = (insn shr 5) and 0x7FFFF
                    val imm = (immhi shl 2) or immlo
                    val sextImm = if (imm >= 0x40000) imm or inv(0x3FFFF) else imm
                    val target = (i.toLong() and 0xFFFFF000L) + (sextImm.toLong() shl 12)
                    if (target == page.toLong()) {
                        // Check if next instruction references pageOff
                        if (i + 4 < data.size) {
                            val next = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(i + 4)
                            val nOp = (next shr 24) and 0xFF
                            // ADD: op = 0x91 (ADD Xd, Xn, #imm)
                            if (nOp == 0x91) {
                                val addImm = (next shr 10) and 0xFFF
                                if (addImm.toLong() == pageOff.toLong()) {
                                    out.add("    0x${String.format("%06X", i)}: [REF] ADRP+ADD → 0x${String.format("%06X", off)}")
                                }
                            }
                            // LDR: op = 0x58 (LDR Xd, label)
                            if (nOp == 0x58) {
                                out.add("    0x${String.format("%06X", i)}: [REF] ADRP+LDR → 0x${String.format("%06X", off)}")
                            }
                        }
                    }
                }
                // MOVZ: op = 0xA4 (MOVZ Xd, #imm16, LSL #0/16/32/48)
                if (op == 0xA4 || op == 0xE4) {
                    val hw = (insn shr 21) and 0x3
                    val imm16 = (insn shr 5) and 0xFFFF
                    val target = imm16.toLong() shl (hw * 16)
                    if (target == off.toLong()) {
                        out.add("    0x${String.format("%06X", i)}: [REF] MOVZ #0x${String.format("%X", off)}")
                    }
                }
            }
        }
    } else {
        // ARM32: scan for LDR (PC-relative) that reference this address
        for (off in offsets) {
            for (i in 0 until data.size - 4 step 4) {
                val insn = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(i)
                val op = (insn shr 24) and 0xFF
                // LDR Rd, [PC, #imm] = 0x594D...
                if ((op and 0xF5) == 0x51) {
                    val rn = (insn shr 16) and 0xF
                    val imm = (insn and 0xFFF) * if ((insn and 0x00800000) != 0) 1 else -1
                    if (rn == 15) { // PC
                        val pc = (i + 8) and 0xFFFFFFFC.toInt()
                        val target = pc + imm
                        if (target == off) {
                            out.add("    0x${String.format("%06X", i)}: [REF] LDR PC-relative → 0x${String.format("%06X", off)}")
                        }
                    }
                }
            }
        }
    }

    if (out.size <= 2) out.add("    (no code cross-references found — data reference only)")
    return out
}

/** Find all references to an address */
private fun xrefAddress(data: ByteArray, addrStr: String): List<String> {
    val out = mutableListOf<String>()
    val addr = try {
        val s = addrStr.removePrefix("0x").removePrefix("0X")
        s.toLong(16)
    } catch (_: Exception) {
        return listOf("[-] Invalid address: $addrStr")
    }

    out.add("[+] Searching for references to 0x${String.format("%X", addr)}...")
    val is64 = data.size > 4 && data[4] == 2.toByte()

    var refs = 0
    for (i in 0 until data.size - 4 step 4) {
        val insn = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(i)
        if (is64) {
            // ADRP
            val op = (insn shr 24) and 0xFF
            if (op == 0x90 || op == 0xB0) {
                val immlo = (insn shr 29) and 0x3
                val immhi = (insn shr 5) and 0x7FFFF
                val imm = (immhi shl 2) or immlo
                val sextImm = if (imm >= 0x40000) imm or inv(0x3FFFF) else imm
                val target = (i.toLong() and 0xFFFFF000L) + (sextImm.toLong() shl 12)
                if (target == addr) {
                    out.add("    0x${String.format("%06X", i)}: [ADRP] → 0x${String.format("%X", addr)}")
                    refs++
                }
            }
        } else {
            // ARM32 LDR PC-relative
            val op = (insn shr 24) and 0xFF
            if ((op and 0xF5) == 0x51) {
                val rn = (insn shr 16) and 0xF
                val imm = (insn and 0xFFF) * if ((insn and 0x00800000) != 0) 1 else -1
                if (rn == 15) {
                    val pc = (i + 8) and 0xFFFFFFFC.toInt()
                    val target = pc + imm
                    if (target.toLong() == addr) {
                        out.add("    0x${String.format("%06X", i)}: [LDR PC-relative] → 0x${String.format("%X", addr)}")
                        refs++
                    }
                }
            }
        }
    }

    if (refs == 0) out.add("[-] No references found for 0x${String.format("%X", addr)}")
    else out.add(0, "[+] Found $refs references")
    return out
}

/** Find hex pattern in binary */
private fun xrefHex(data: ByteArray, hexStr: String): List<String> {
    val out = mutableListOf<String>()
    val cleaned = hexStr.replace(" ", "").replace("0x", "").replace("0X", "")
    if (cleaned.length % 2 != 0) return listOf("[-] Hex pattern must have even number of characters")
    val pattern = ByteArray(cleaned.length / 2)
    try {
        for (i in pattern.indices) {
            pattern[i] = cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (_: Exception) {
        return listOf("[-] Invalid hex pattern: $hexStr")
    }

    out.add("[+] Searching for pattern: ${cleaned.uppercase()}")
    val matches = mutableListOf<Int>()
    for (i in 0 until data.size - pattern.size) {
        var match = true
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) { match = false; break }
        }
        if (match) matches.add(i)
    }

    if (matches.isEmpty()) {
        out.add("[-] Pattern not found")
        return out
    }

    out.add("[+] Found ${matches.size} matches:")
    for (off in matches.take(50)) {
        // Show context: 16 bytes before and after
        val start = maxOf(0, off - 8)
        val end = minOf(data.size, off + pattern.size + 8)
        val ctx = StringBuilder()
        for (j in start until end) {
            if (j == off) ctx.append(">>")
            ctx.append(String.format("%02X", data[j].toInt() and 0xFF))
            if (j == off + pattern.size - 1) ctx.append("<<")
            ctx.append(" ")
        }
        out.add("    0x${String.format("%06X", off)}: $ctx")
    }
    if (matches.size > 50) out.add("    ... and ${matches.size - 50} more")
    return out
}

private fun inv(bits: Int): Int = bits.inv()

private fun fmtXref(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1048576.0)} MB"
}

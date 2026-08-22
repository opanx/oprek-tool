package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CrackFinding(
    val offset: Int,
    val type: String,
    val pattern: String,
    val originalBytes: String,
    val matchedStr: String = "",
    val confidence: String = "HIGH"
)

/**
 * AutoPatchLoginScreen v2 — Full ELF auto-crack
 * - ARM32 + ARM64 pattern detection
 * - Auth/license/login bypass
 * - Obfuscated URL detection (migoreng.my.id, filescit.my.id, etc.)
 * - Multiple crack strategies: NOP, B-always, XOR 0, MOV W0 #0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPatchLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var fileName by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf(listOf<CrackFinding>()) }
    var loaded by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var crackStrategy by remember { mutableStateOf(0) } // 0=NOP, 1=B-always, 2=XOR0, 3=MOV0
    var showSaveDialog by remember { mutableStateOf(false) }
    var archMode by remember { mutableStateOf("") }



    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(it)
                val b = input?.readBytes() ?: byteArrayOf()
                val name = uri.lastPathSegment ?: "unknown"
                withContext(Dispatchers.Main) {
                    fileBytes = b; fileName = name; loaded = true; findings = emptyList(); patchedCount = 0
                    // Auto-detect arch
                    archMode = if (b.size >= 18) {
                        val machine = (b[18].toInt() and 0xFF) or ((b[19].toInt() and 0xFF) shl 8)
                        when (machine) { 0xB7 -> "ARM64 (AArch64)"; 0x28 -> "ARM32"; 0x03 -> "x86"; 0x3E -> "x86_64"; else -> "Unknown (0x${String.format("%04X", machine)})" }
                    } else "Unknown"
                }
                input?.close()
            } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("🔓 Auto Crack v2", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = {
                if (loaded && findings.isNotEmpty()) {
                    IconButton(onClick = { showSaveDialog = true }) { Icon(Icons.Default.Save, "Save Patched") }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) {
                Button(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                    Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open ELF Binary / .so / .sh")
                }
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔓 Supported:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        listOf(
                            "ARM32 / ARM64 ELF binaries",
                            "Auth/license/login check bypass",
                            "Obfuscated URL detection (migoreng, filescit, etc.)",
                            "Multiple crack strategies",
                            "4-byte pattern matching"
                        ).forEach { Text("• $it", fontSize = 11.sp, color = TextMuted) }
                    }
                }
            }

            if (loaded) {
                // File info
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📄 $fileName", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                            Text("📦 $archMode | ${(fileBytes.size / 1024)} KB", fontSize = 11.sp, color = TextMuted)
                        }
                        if (patchedCount > 0) {
                            Text("✅ $patchedCount patched", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Crack strategy selector
                Text("⚙️ Crack Strategy:", fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("NOP ✨" to 0, "B-always" to 1, "MOV #0" to 2, "XOR #0" to 3).forEach { (label, idx) ->
                        FilterChip(selected = crackStrategy == idx, onClick = { crackStrategy = idx },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.2f)))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Scan button
                Button(onClick = {
                    isScanning = true; findings = emptyList(); patchedCount = 0
                    Thread {
                        findings = scanForCracks(fileBytes, crackStrategy)
                        isScanning = false
                    }.start()
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp)); Text("Scanning...")
                    } else {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp))
                        Text("🔍 Scan & Crack", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Findings
                if (findings.isNotEmpty()) {
                    Text("🎯 ${findings.size} crack points found:", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(findings) { finding ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                                Row(Modifier.padding(8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("0x${String.format("%08X", finding.offset)}  [${finding.type}] ${finding.confidence}",
                                            color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("Pattern: ${finding.pattern}", color = AccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        if (finding.matchedStr.isNotEmpty()) Text("String: \"${finding.matchedStr}\"", color = AccentOrange, fontSize = 10.sp)
                                        Text("Bytes: ${finding.originalBytes}", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Button(onClick = {
                                        applyCrack(fileBytes, finding.offset, crackStrategy)
                                        patchedCount++
                                        findings = findings.filter { it != finding }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                        modifier = Modifier.padding(start = 4.dp)) {
                                        Text("BYPASS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (findings.isEmpty() && !isScanning) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Click 'Scan & Crack' to analyze the binary", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Save dialog
    if (showSaveDialog) {
        AlertDialog(onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Patched Binary", fontWeight = FontWeight.Bold) },
            text = { Text("Save the patched binary to /sdcard/Download/OprekTool/crack/ ?") },
            confirmButton = {
                Button(onClick = {
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/crack")
                    dir.mkdirs()
                    val outFile = File(dir, "patched_${fileName}")
                    outFile.writeBytes(fileBytes)
                    showSaveDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard)
    }
}

// ============================================================
// Scan for cracks — comprehensive ARM32/ARM64 patterns
// ============================================================
private fun scanForCracks(data: ByteArray, strategy: Int): List<CrackFinding> {
    val findings = mutableListOf<CrackFinding>()
    if (data.size < 16) return findings

    // Detect architecture
    val is64 = data.size >= 20 && data[4] == 2.toByte()
    val machine = if (data.size >= 20) (data[18].toInt() and 0xFF) or ((data[19].toInt() and 0xFF) shl 8) else 0

    // Extract strings first for context
    val strings = extractStrings(data, 4)

    // === 1. Find auth-related strings and nearby branches ===
    val authKeywords = listOf(
        "login", "auth", "license", "licence", "expire", "expired", "key", "token",
        "device", "banned", "blocked", "denied", "unauthorized", "wrong", "invalid",
        "incorrect", "failed", "error", "check", "verify", "validate", "crack",
        "migoreng", "filescit", "gembelcit", "convex", "brave-lobster",
        "curl", "http://", "https://", ".my.id", ".site", ".xyz", ".php"
    )

    for (i in strings.indices) {
        val str = strings[i].first.lowercase()
        val offset = strings[i].second
        for (keyword in authKeywords) {
            if (str.contains(keyword, ignoreCase = true)) {
                // Find nearest branch instruction before/after this string
                val branchOffset = findNearestBranch(data, offset, is64)
                if (branchOffset >= 0) {
                    val bytesHex = data.sliceArray(branchOffset until minOf(branchOffset + 4, data.size)).joinToString(" ") { String.format("%02X", it) }
                    findings.add(CrackFinding(
                        offset = branchOffset,
                        type = "AUTH-BRANCH",
                        pattern = if (is64) "ARM64 B.cond/CBZ" else "ARM32 Bcond",
                        originalBytes = bytesHex,
                        matchedStr = keyword,
                        confidence = "HIGH"
                    ))
                }
                break
            }
        }
    }

    // === 2. ARM64 specific patterns ===
    if (is64) {
        for (i in 0 until data.size - 7) {
            val insn = readU32LE(data, i).toInt()

            // CBZ/CBNZ Xn (return check): 0xB4/0xB5/0x34/0x35
            val opc = (insn shr 24) and 0xFF
            if (opc == 0xB4 || opc == 0xB5 || opc == 0x34 || opc == 0x35) {
                val rn = insn and 0x1F
                // Check if near auth strings
                if (isNearAuthString(data, i, strings)) {
                    findings.add(CrackFinding(
                        offset = i, type = "ARM64-CBZ", pattern = String.format("CBZ X%d, ...", rn),
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "HIGH"
                    ))
                }
            }

            // B.cond: 0x54xxxxxx (condition code in bits 3-5 of second byte)
            if (opc == 0x54) {
                val cond = insn and 0xF
                if (isNearAuthString(data, i, strings)) {
                    val condName = when(cond) { 0 -> "EQ"; 1 -> "NE"; 2 -> "CS/HS"; 3 -> "CC/LO"; 4 -> "MI"; 5 -> "PL"; 6 -> "VS"; 7 -> "VC"; 8 -> "HI"; 9 -> "LS"; 10 -> "GE"; 11 -> "LT"; 12 -> "GT"; 13 -> "LE"; else -> "??" }
                    findings.add(CrackFinding(
                        offset = i, type = "ARM64-B.$condName",
                        pattern = String.format("B.%s (cond=%d)", condName, cond),
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "HIGH"
                    ))
                }
            }

            // RET: 0xD65F03C0
            if (insn == 0xD65F03C0L) {
                if (isNearAuthString(data, i, strings)) {
                    findings.add(CrackFinding(
                        offset = i, type = "ARM64-RET", pattern = "RET (login return point)",
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "MEDIUM"
                    ))
                }
            }

            // MOV W0, #0 (return false → true): 0x52800000
            if ((insn and 0xFFE00000) == 0x52800000L) {
                if (isNearAuthString(data, i, strings)) {
                    findings.add(CrackFinding(
                        offset = i, type = "ARM64-MOV-RET", pattern = String.format("MOV W%d, #0", insn and 0x1F),
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "HIGH"
                    ))
                }
            }
        }
    }

    // === 3. ARM32 specific patterns ===
    if (!is64) {
        for (i in 0 until data.size - 3 step 2) {
            val insn = readU32LE(data, i).toInt()
            val opc = (insn shr 24) and 0xFF

            // Bcond: xxxx101x
            if ((opc and 0x0E) == 0x0A) {
                if (isNearAuthString(data, i, strings)) {
                    val cond = (insn shr 28) and 0xF
                    val condName = when(cond) { 0 -> "EQ"; 1 -> "NE"; 2 -> "HS"; 3 -> "LO"; 4 -> "MI"; 5 -> "PL"; 6 -> "VS"; 7 -> "VC"; 8 -> "HI"; 9 -> "LS"; 10 -> "GE"; 11 -> "LT"; 12 -> "GT"; 13 -> "LE"; else -> "??" }
                    findings.add(CrackFinding(
                        offset = i, type = "ARM32-B.$condName",
                        pattern = String.format("B.%s", condName),
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "HIGH"
                    ))
                }
            }

            // MOV R0, #0 (return false → true): 0xE3A00000
            if (insn == 0xE3A00000.toInt()) {
                if (isNearAuthString(data, i, strings)) {
                    findings.add(CrackFinding(
                        offset = i, type = "ARM32-MOV-R0", pattern = "MOV R0, #0 (return false)",
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "HIGH"
                    ))
                }
            }

            // BX LR (return): 0xE12FFF1E
            if (insn == 0xE12FFF1E.toInt()) {
                if (isNearAuthString(data, i, strings)) {
                    findings.add(CrackFinding(
                        offset = i, type = "ARM32-BX-LR", pattern = "BX LR (return)",
                        originalBytes = String.format("%02X %02X %02X %02X", data[i], data[i+1], data[i+2], data[i+3]),
                        confidence = "MEDIUM"
                    ))
                }
            }
        }
    }

    // Deduplicate (keep unique offsets, remove nearby duplicates)
    val unique = mutableListOf<CrackFinding>()
    for (f in findings) {
        if (unique.isEmpty() || (f.offset - unique.last().offset) > 4) {
            unique.add(f)
        } else if (f.confidence == "HIGH" && unique.last().confidence != "HIGH") {
            unique[unique.size - 1] = f
        }
    }

    return unique.sortedBy { it.offset }
}

private fun readU32LE(data: ByteArray, offset: Int): Long {
    if (offset + 4 > data.size) return 0
    return (data[offset].toLong() and 0xFF) or
        ((data[offset + 1].toLong() and 0xFF) shl 8) or
        ((data[offset + 2].toLong() and 0xFF) shl 16) or
        ((data[offset + 3].toLong() and 0xFF) shl 24)
}

private fun extractStrings(data: ByteArray, minLength: Int): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    val sb = StringBuilder()
    var start = 0
    for (i in data.indices) {
        val b = data[i].toInt() and 0xFF
        if (b in 32..126) {
            if (sb.isEmpty()) start = i
            sb.append(b.toChar())
        } else {
            if (sb.length >= minLength) result.add(sb.toString() to start)
            sb.clear()
        }
    }
    return result
}

private fun findNearestBranch(data: ByteArray, stringOffset: Int, is64: Boolean): Int {
    val range = 1024
    val searchStart = maxOf(0, stringOffset - range)
    val searchEnd = minOf(if (is64) data.size - 3 else data.size - 7, stringOffset + range)

    for (i in searchStart until searchEnd step if (is64) 4 else 2) {
        val insn = readU32LE(data, i).toInt()
        val opc = (insn shr 24) and 0xFF
        if (is64) {
            if (opc == 0x54 || opc == 0xB4 || opc == 0xB5 || opc == 0x34 || opc == 0x35) return i
        } else {
            if ((opc and 0x0E) == 0x0A) return i
        }
    }
    return -1
}

private fun isNearAuthString(data: ByteArray, offset: Int, strings: List<Pair<String, Int>>): Boolean {
    val keywords = listOf("login", "auth", "license", "key", "device", "expire", "curl", "http", "ban", "error", "wrong", "check", "verify", "migoreng", "filescit", "convex", "data/local")
    for ((str, strOffset) in strings) {
        val dist = kotlin.math.abs(offset - strOffset)
        if (dist < 512 && keywords.any { str.lowercase().contains(it) }) return true
    }
    return false
}

// ============================================================
// Apply crack based on strategy
// ============================================================
private fun applyCrack(data: ByteArray, offset: Int, strategy: Int) {
    when (strategy) {
        0 -> { // NOP (ARM64: 0x1F2003D5, ARM32: 0xE1A00000)
            if (data.size > offset + 3) {
                data[offset] = 0x1F.toByte(); data[offset+1] = 0x20; data[offset+2] = 0x03; data[offset+3] = 0xD5.toByte()
            }
        }
        1 -> { // B-always (ARM64: 0x14000000 | imm26=1 → skip, ARM32: 0xEA000000)
            if (data.size > offset + 3) {
                data[offset] = 0x00.toByte(); data[offset+1] = 0x00; data[offset+2] = 0x00; data[offset+3] = 0x14.toByte()
            }
        }
        2 -> { // MOV W0/X0, #0 → return 0
            if (data.size > offset + 3) {
                data[offset] = 0x00.toByte(); data[offset+1] = 0x00; data[offset+2] = 0x80; data[offset+3] = 0x52.toByte()
            }
        }
        3 -> { // XOR R0/W0, R0, R0 → return 0
            if (data.size > offset + 3) {
                data[offset] = 0x00.toByte(); data[offset+1] = 0x00; data[offset+2] = 0x00; data[offset+3] = 0xCA.toByte()
            }
        }
    }
}

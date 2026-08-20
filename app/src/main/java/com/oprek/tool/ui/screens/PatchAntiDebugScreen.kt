package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlin.math.ln
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchAntiDebugScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var findings by remember { mutableStateOf(listOf<Triple<Long, String, String>>()) }
    var loaded by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Patch Anti-Debug", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open ELF") } }
            if (loaded) {
                Button(onClick = {
                    val found = mutableListOf<Triple<Long, String, String>>()
                    val strs = mutableListOf<Pair<Long, String>>()
                    val sb = StringBuilder(); var start = 0L
                    for (i in fileBytes.indices) {
                        val b = fileBytes[i].toInt() and 0xFF
                        if (b in 0x20..0x7E) { if (sb.isEmpty()) start = i.toLong(); sb.append(b.toChar()) }
                        else { if (sb.length >= 4) strs.add(start to sb.toString()); sb.clear() }
                    }
                    val antiDbg = listOf("tracerpid", "ptrace", "frida", "gdb", "debug", "/proc/self/status", "/proc/self/fd")
                    for ((off, s) in strs) {
                        val lower = s.lowercase()
                        for (kw in antiDbg) {
                            if (lower.contains(kw)) {
                                val branchOff = findBranchNear(fileBytes, off.toInt(), s.length)
                                if (branchOff >= 0) found.add(Triple(branchOff.toLong(), kw, s))
                                break
                            }
                        }
                    }
                    findings = found
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("🔍 Scan Anti-Debug") }
                Spacer(Modifier.height(8.dp))

                Text("${findings.size} anti-debug checks found", color = AccentCyan, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(findings) { _, (addr, kw, str) ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("0x${"%08X".format(addr)}", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("\"$kw\" → \"$str\"", color = TextSecondary, fontSize = 10.sp)
                                }
                                Button(onClick = {
                                    val nop = byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte())
                                    for (j in 0..3) fileBytes[addr.toInt()+j] = nop[j]
                                    patchedCount++; findings = findings.filter { it.first != addr }
                                }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) { Text("NOP", fontSize = 9.sp) }
                            }
                        }
                    }
                }
                if (patchedCount > 0) Text("✓ $patchedCount anti-debug checks NOP'd", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "$patchedCount anti-debug checks NOP'd" },
                filename = "antidebug_bypass.txt",
                subfolder = "patches"
            )

    }
}

private fun findBranchNear(data: ByteArray, start: Int, len: Int): Int {
    val range = 300; val s = maxOf(0, start - range); val e = minOf(data.size - 3, start + len + range)
    for (i in s until e step 4) {
        if (i + 4 > data.size) return -1
        val insn = data[i].toInt() and 0xFF or ((data[i+1].toInt() and 0xFF) shl 8) or ((data[i+2].toInt() and 0xFF) shl 16) or ((data[i+3].toInt() and 0xFF) shl 24)
        val opc8 = (insn shr 24) and 0xFF; if (opc8 == 0x54) return i
    }
    return -1
}

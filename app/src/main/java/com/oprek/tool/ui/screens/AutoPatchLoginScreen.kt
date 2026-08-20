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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPatchLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var findings by remember { mutableStateOf(listOf<Triple<Long, String, String>>()) }
    var loaded by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableStateOf(0) }

    val loginStrs = listOf("wrong", "invalid", "login failed", "unauthorized", "denied", "expired",
        "incorrect", "failed", "not found", "error", "blocked", "banned")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Auto Patch Login", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open ELF Binary") } }
            if (loaded) {
                Button(onClick = {
                    val found = mutableListOf<Triple<Long, String, String>>()
                    // Extract strings and find login-related
                    val sb = StringBuilder(); var start = 0L
                    for (i in fileBytes.indices) {
                        val b = fileBytes[i].toInt() and 0xFF
                        if (b in 0x20..0x7E) { if (sb.isEmpty()) start = i.toLong(); sb.append(b.toChar()) }
                        else {
                            if (sb.length >= 4) {
                                val str = sb.toString().lowercase()
                                for (login in loginStrs) {
                                    if (str.contains(login)) {
                                        // Find nearby branch (scan backwards/forwards)
                                        val branchOff = findNearestBranch(fileBytes, start.toInt(), sb.length)
                                        if (branchOff >= 0) {
                                            found.add(Triple(branchOff.toLong(), login, sb.toString()))
                                        }
                                        break
                                    }
                                }
                            }
                            sb.clear()
                        }
                    }
                    findings = found
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("🔍 Scan Login Checks") }
                Spacer(Modifier.height(8.dp))

                Text("${findings.size} login checks found", color = AccentCyan, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    items(findings) { (addr, keyword, str) ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
                                Column(Modifier.weight(1f)) {
                                    Text("0x${"%08X".format(addr)}", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("\"$keyword\" near \"$str\"", color = TextSecondary, fontSize = 10.sp)
                                }
                                Button(onClick = {
                                    val nop = byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte())
                                    for (j in 0..3) fileBytes[addr.toInt()+j] = nop[j]
                                    patchedCount++
                                    findings = findings.filter { it.first != addr }
                                }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) { Text("BYPASS", fontSize = 9.sp) }
                            }
                        }
                    }
                }
                if (patchedCount > 0) Text("✓ $patchedCount login checks bypassed!", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

        }

    }
}

private fun findNearestBranch(data: ByteArray, start: Int, strLen: Int): Int {
    val range = 300
    val searchStart = maxOf(0, start - range)
    val searchEnd = minOf(data.size - 3, start + strLen + range)
    for (i in searchStart until searchEnd step 4) {
        if (i + 4 > data.size) return -1
        val insn = data[i].toInt() and 0xFF or ((data[i+1].toInt() and 0xFF) shl 8) or
            ((data[i+2].toInt() and 0xFF) shl 16) or ((data[i+3].toInt() and 0xFF) shl 24)
        val opc8 = (insn shr 24) and 0xFF
        if (opc8 == 0x54 || ((insn shr 26) and 0x3F) == 0x05) return i
    }
    return -1
}

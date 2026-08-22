package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.*
import java.io.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggerScreen() {
    val context = LocalContext.current
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Breakpoints", "Memory", "Disasm", "Log")
    val log = remember { mutableStateListOf<String>() }

    // Breakpoints
    var bpAddress by remember { mutableStateOf("") }
    val breakpoints = remember { mutableStateOf(emptyList<Long>()) }

    // Memory
    var memAddr by remember { mutableStateOf("0x0") }
    var memSize by remember { mutableStateOf("128") }
    var memContent by remember { mutableStateOf("") }
    var memWrite by remember { mutableStateOf("") }

    // Disasm
    var disasmStart by remember { mutableStateOf("0x0") }
    var disasmCount by remember { mutableStateOf("30") }
    var disasmOut by remember { mutableStateOf("") }

    fun addLog(msg: String) { log.add(msg); if (log.size > 500) log.removeRange(0, 100) }

    fun loadFile() {
        val f = context.cacheDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        if (f != null) { loadedFile = f; addLog("[+] Loaded: ${f.name} (${f.length()} bytes)") }
        else addLog("[-] No file in cache")
    }

    LaunchedEffect(Unit) { loadFile() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A))) {
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Debugger", color = AccentPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Button(onClick = { loadFile() }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(32.dp)) { Text("Load", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { log.clear() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)), modifier = Modifier.height(32.dp)) { Text("Clear", fontSize = 12.sp) }
                }
                loadedFile?.let { Text("${it.name} | ${it.length()} bytes", color = AccentGreen, fontSize = 10.sp) }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF16213E)) {
            tabs.forEachIndexed { i, n -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(n, fontSize = 11.sp, color = if (selectedTab == i) AccentPurple else Color.Gray) }) }
        }

        when (selectedTab) {
            0 -> { // Breakpoints
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Set Breakpoint", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = bpAddress, onValueChange = { bpAddress = it }, label = { Text("Address (hex)", fontSize = 11.sp) }, modifier = Modifier.weight(1f).height(48.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val a = try { bpAddress.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                            if (a > 0 && a !in breakpoints.value) {
                                breakpoints.value = (breakpoints.value + a).sorted()
                                addLog("[+] BP set at 0x${a.toString(16)}")
                                loadedFile?.let { f ->
                                    try {
                                        val data = f.readBytes()
                                        if (a.toInt() + 3 < data.size) {
                                            data[a.toInt()] = 0x00; data[(a+1).toInt()] = 0x00
                                            data[(a+2).toInt()] = 0x20.toByte(); data[(a+3).toInt()] = 0xD4.toByte()
                                            f.writeBytes(data); addLog("[+] BRK #0 written")
                                        }
                                    } catch (e: Exception) { addLog("[!] ${e.message}") }
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.height(48.dp)) { Text("Set BP", color = Color.Black, fontSize = 12.sp) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Active: ${breakpoints.value.size}", color = AccentCyan, fontSize = 12.sp)
                    breakpoints.value.forEachIndexed { idx, addr ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${idx+1}. 0x${addr.toString(16).uppercase()}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                breakpoints.value = breakpoints.value.toMutableList().also { it.removeAt(idx) }
                                addLog("[-] BP removed")
                            }) { Text("X", color = Color.Red, fontSize = 11.sp) }
                        }
                    }
                }
            }
            1 -> { // Memory
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Memory Viewer", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(value = memAddr, onValueChange = { memAddr = it }, label = { Text("Address", fontSize = 11.sp) }, modifier = Modifier.weight(1f).height(48.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = memSize, onValueChange = { memSize = it }, label = { Text("Size", fontSize = 11.sp) }, modifier = Modifier.width(80.dp).height(48.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            val a = try { memAddr.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                            val s = try { memSize.toInt() } catch (e: Exception) { 64 }
                            val f = loadedFile ?: return@Button
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val data = f.readBytes()
                                    if (a.toInt() + s > data.size) { addLog("[-] OOB"); return@launch }
                                    val sb = StringBuilder()
                                    for (i in a.toInt() until (a.toInt() + s).coerceAtMost(data.size)) {
                                        sb.append(String.format("%02X ", data[i].toInt() and 0xFF))
                                        if ((i - a.toInt()) % 16 == 15) sb.appendLine()
                                    }
                                    withContext(Dispatchers.Main) { memContent = sb.toString(); addLog("[+] Read $s bytes from 0x${a.toString(16)}") }
                                } catch (e: Exception) { addLog("[!] ${e.message}") }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(36.dp)) { Text("Read", fontSize = 12.sp) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val a = try { memAddr.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                            val vals = memWrite.trim().split(" ").mapNotNull { try { it.toInt(16).toByte() } catch (e: Exception) { null } }
                            val f = loadedFile ?: return@Button
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val data = f.readBytes()
                                    if (a.toInt() + vals.size > data.size) { addLog("[-] OOB"); return@launch }
                                    for (i in vals.indices) data[(a.toInt() + i)] = vals[i]
                                    f.writeBytes(data); addLog("[+] Wrote ${vals.size} bytes")
                                } catch (e: Exception) { addLog("[!] ${e.message}") }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.height(36.dp)) { Text("Write", color = Color.Black, fontSize = 12.sp) }
                    }
                    OutlinedTextField(value = memWrite, onValueChange = { memWrite = it }, label = { Text("Hex: 48 65 6C 6C 6F", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace), maxLines = 2)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color(0xFF0A0A1A), modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).border(1.dp, Color(0xFF333333))) {
                        Text(memContent.ifEmpty { "Click Read" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            2 -> { // Disasm
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Disassemble", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(value = disasmStart, onValueChange = { disasmStart = it }, label = { Text("Start", fontSize = 11.sp) }, modifier = Modifier.weight(1f).height(48.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = disasmCount, onValueChange = { disasmCount = it }, label = { Text("Count", fontSize = 11.sp) }, modifier = Modifier.width(70.dp).height(48.dp), textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val f = loadedFile ?: return@Button
                        val start = try { disasmStart.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { 0L }
                        val count = try { disasmCount.toInt() } catch (e: Exception) { 20 }
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val data = f.readBytes()
                                val sb = StringBuilder()
                                val opcodes = mapOf(
                                    0xD4200000L to "SVC #0", 0xD503201FL to "NOP", 0xD65F03C0L to "RET",
                                    0xD63F0000L to "BLR", 0xD61F0000L to "BR", 0xD69F03E0L to "ERET"
                                )
                                var off = start
                                repeat(count) {
                                    if (off.toInt() + 4 > data.size) return@repeat
                                    val insn = (data[off.toInt()].toLong() and 0xFF) or
                                            ((data[(off+1).toInt()].toLong() and 0xFF) shl 8) or
                                            ((data[(off+2).toInt()].toLong() and 0xFF) shl 16) or
                                            ((data[(off+3).toInt()].toLong() and 0xFF) shl 24)
                                    val name = opcodes[insn and 0xFFFFFFFFL] ?: when {
                                        (insn and 0xFF000000L) == 0x14000000L -> "B ${String.format("0x%X", (insn and 0x3FFFFFFL) * 4)}"
                                        (insn and 0xFF000000L) == 0x94000000L -> "BL ${String.format("0x%X", (insn and 0x3FFFFFFL) * 4)}"
                                        (insn and 0xFFC003FFL) == 0xD10003FFL -> "SUB SP, SP, #${(insn shr 10) and 0xFFF}"
                                        (insn and 0xFF800000L) == 0xD2800000L -> "MOVZ X${insn and 0x1F}, #${((insn shr 5) and 0xFFFF)}"
                                        else -> "DCD ${String.format("0x%08X", insn)}"
                                    }
                                    sb.appendLine(String.format("%08X:  %08X  %s", off.toInt(), insn.toInt(), name))
                                    off += 4
                                }
                                withContext(Dispatchers.Main) { disasmOut = sb.toString(); addLog("[+] Disassembled ${count} insns") }
                            } catch (e: Exception) { addLog("[!] ${e.message}") }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(36.dp)) { Text("Disassemble", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color(0xFF0A0A1A), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).border(1.dp, Color(0xFF333333))) {
                        Text(disasmOut.ifEmpty { "Click Disassemble" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            3 -> { // Log
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(log) { line ->
                        Text(line, color = when {
                            line.contains("[+]") -> AccentGreen
                            line.contains("[-]") -> Color(0xFF888888)
                            line.contains("[!]") -> Color.Red
                            else -> AccentCyan
                        }, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Surface(color = Color(0xFF0F3460), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(modifier = Modifier.padding(8.dp)) {
                Text("BP: ${breakpoints.value.size}", color = AccentPurple, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("File: ${loadedFile?.name ?: "none"}", color = AccentGreen, fontSize = 10.sp)
            }
        }
    }
}

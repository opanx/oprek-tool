package com.oprek.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.LoadedFileHelper
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("📋 Breakpoints", "💾 Memory", "🔍 Disasm", "📊 Info", "📝 Log")
    val log = remember { mutableStateListOf<String>() }

    // Breakpoints
    var bpInput by remember { mutableStateOf("") }
    val breakpoints = remember { mutableStateOf(listOf<Long>()) }

    // Memory
    var memAddr by remember { mutableStateOf("0x0") }
    var memSize by remember { mutableStateOf("256") }
    var memResult by remember { mutableStateOf("") }

    // Disasm
    var disasmOffset by remember { mutableStateOf("0x0") }
    var disasmCount by remember { mutableStateOf("50") }
    var disasmOut by remember { mutableStateOf("") }
    var archMode by remember { mutableStateOf(1) } // ARM64 default

    fun addLog(msg: String) {
        log.add(msg)
        if (log.size > 1000) log.removeRange(0, 200)
    }

    fun loadFile() {
        val f = LoadedFileHelper.findLoadedFile(context)
        if (f != null) {
            loadedFile = f
            fileData = f.readBytes()
            addLog("[+] Loaded: ${f.name} (${fileData?.size ?: 0} bytes)")

            // Detect arch from ELF header
            val data = fileData ?: return
            if (data.size >= 20) {
                val isElf = data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte() && data[2] == 'L'.code.toByte() && data[3] == 'F'.code.toByte()
                if (isElf) {
                    val is64 = data[4] == 2.toByte()
                    val machine = if (is64) {
                        (data[18].toInt() and 0xFF) or ((data[19].toInt() and 0xFF) shl 8)
                    } else {
                        (data[18].toInt() and 0xFF) or ((data[19].toInt() and 0xFF) shl 8)
                    }
                    val (arch, mode) = NativeLib.detectArchFromElf(machine)
                    archMode = mode
                    addLog("[+] ELF detected: ${if (is64) "64-bit" else "32-bit"} machine=0x${"%04X".format(machine)} arch=$arch mode=$mode")

                    // Entry point
                    val entry = if (is64) {
                        var v = 0L
                        for (i in 0..7) v = v or ((data[0x18 + i].toLong() and 0xFF) shl (i * 8))
                        v
                    } else {
                        var v = 0L
                        for (i in 0..3) v = v or ((data[0x18 + i].toLong() and 0xFF) shl (i * 8))
                        v
                    }
                    disasmOffset = "0x${"%X".format(entry)}"
                    addLog("[+] Entry point: 0x${"%X".format(entry)}")
                } else {
                    addLog("[+] Not ELF - raw binary mode")
                }
            }
        } else {
            addLog("[-] No file loaded. Open a file first from Home screen.")
        }
    }

    LaunchedEffect(Unit) { loadFile() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A))) {
        // Header
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🔧 Debugger", color = AccentPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { loadFile() }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reload", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { log.clear() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.height(32.dp)) {
                        Text("Clear Log", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    loadedFile?.let {
                        Text("${it.name} | ${it.length()} bytes", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } ?: Text("No file loaded", color = AccentRed, fontSize = 10.sp)
                }
            }
        }

        // Tabs
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF16213E), edgePadding = 0.dp) {
            tabs.forEachIndexed { i, n ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(n, fontSize = 11.sp, color = if (selectedTab == i) AccentPurple else Color.Gray) })
            }
        }

        when (selectedTab) {
            0 -> { // Breakpoints
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Set Breakpoint", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = bpInput, onValueChange = { bpInput = it },
                            label = { Text("Address (hex)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val a = try { bpInput.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                            if (a >= 0 && a !in breakpoints.value) {
                                breakpoints.value = (breakpoints.value + a).sorted()
                                addLog("[+] BP set at 0x${"%X".format(a)}")
                                bpInput = ""
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("+ Add", fontSize = 11.sp) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Active Breakpoints (${breakpoints.value.size})", color = AccentCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    breakpoints.value.forEach { addr ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔴 0x${"%08X".format(addr)}", color = AccentRed, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { breakpoints.value = breakpoints.value - addr; addLog("[-] BP removed 0x${"%X".format(addr)}") }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = AccentRed)
                            }
                        }
                    }
                    if (breakpoints.value.isEmpty()) {
                        Text("No breakpoints set", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Quick Breakpoints", color = AccentCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("main", ".init", ".fini", "exit").forEach { name ->
                            OutlinedButton(onClick = {
                                val addr = when(name) { "main" -> 0x1000L; ".init" -> 0x200L; ".fini" -> 0x300L; else -> 0x400L }
                                if (addr !in breakpoints.value) { breakpoints.value = (breakpoints.value + addr).sorted(); addLog("[+] Quick BP: $name @ 0x${"%X".format(addr)}") }
                            }) { Text(name, fontSize = 10.sp) }
                        }
                    }
                }
            }
            1 -> { // Memory View
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Memory Viewer", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = memAddr, onValueChange = { memAddr = it }, label = { Text("Address", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = memSize, onValueChange = { memSize = it }, label = { Text("Size", fontSize = 11.sp) },
                            modifier = Modifier.width(80.dp), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val data = fileData ?: run { addLog("[-] No file loaded"); return@launch }
                            val addr = try { memAddr.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { 0L }
                            val sz = try { memSize.trim().toInt() } catch (e: Exception) { 256 }
                            val start = addr.toInt().coerceIn(0, data.size - 1)
                            val end = (start + sz).coerceAtMost(data.size)
                            if (start >= data.size || start >= end) { addLog("[-] Invalid address"); return@launch }
                            val chunk = data.sliceArray(start until end)
                            // Hex dump
                            val sb = StringBuilder()
                            for (i in chunk.indices step 16) {
                                sb.append("%08X: ".format(start + i))
                                val row = chunk.sliceArray(i until (i + 16).coerceAtMost(chunk.size))
                                for (b in row) sb.append("%02X ".format(b.toInt() and 0xFF))
                                sb.append(" ".repeat((16 - row.size) * 3))
                                sb.append(" |")
                                for (b in row) {
                                    val c = b.toInt() and 0xFF
                                    sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
                                }
                                sb.append("|\n")
                            }
                            memResult = sb.toString()
                            addLog("[+] Read ${chunk.size} bytes from 0x${"%X".format(start)}")
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) { Text("Read Memory", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (memResult.isNotEmpty()) {
                        Text(memResult, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(8.dp)).padding(8.dp))
                    }
                }
            }
            2 -> { // Disassembler
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Disassembler", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = disasmOffset, onValueChange = { disasmOffset = it }, label = { Text("Offset", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = disasmCount, onValueChange = { disasmCount = it }, label = { Text("Count", fontSize = 11.sp) },
                            modifier = Modifier.width(80.dp), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ARM64" to 2, "ARM" to 0, "THUMB" to 1, "X86" to 4).forEach { (label, mode) ->
                            FilterChip(selected = archMode == mode, onClick = { archMode = mode },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.2f)))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val data = fileData ?: run { addLog("[-] No file"); return@launch }
                            val off = try { disasmOffset.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { 0L }
                            val cnt = try { disasmCount.trim().toInt() } catch (e: Exception) { 50 }
                            val start = off.toInt().coerceIn(0, data.size - 4)
                            val end = (start + cnt * 4).coerceAtMost(data.size)
                            val code = data.sliceArray(start until end)
                            try {
                                val result = NativeLib.disassemble(code, off, 1, archMode, cnt)
                                disasmOut = result.ifEmpty { "[-] Disassembly failed" }
                                addLog("[+] Disassembled ${cnt} instructions from 0x${"%X".format(off)}")
                            } catch (e: Exception) {
                                disasmOut = "[-] Error: ${e.message}"
                                addLog("[-] Disasm error: ${e.message}")
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Disassemble", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (disasmOut.isNotEmpty()) {
                        Text(disasmOut, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(8.dp)).padding(8.dp))
                    }
                }
            }
            3 -> { // Info
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("File Info", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val data = fileData
                    if (data != null) {
                        val info = StringBuilder()
                        info.append("Name: ${loadedFile?.name}\n")
                        info.append("Size: ${data.size} bytes\n")
                        info.append("MD5: ${java.security.MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }}\n")
                        info.append("SHA256: ${java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }}\n\n")

                        if (data.size >= 4 && data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte()) {
                            val is64 = data[4] == 2.toByte()
                            info.append("Format: ELF ${if (is64) "64-bit" else "32-bit"}\n")
                            info.append("Endian: ${if (data[5] == 1.toByte()) "Little" else "Big"}\n")
                            val machine = (data[18].toInt() and 0xFF) or ((data[19].toInt() and 0xFF) shl 8)
                            info.append("Machine: 0x${"%04X".format(machine)}\n")
                            info.append("Arch: ${when(machine) { 0x28 -> "ARM"; 0xB7 -> "ARM64"; 0x03 -> "x86"; 0x3E -> "x86_64"; else -> "Unknown" }}\n")
                            val entry = if (is64) { var v=0L; for(i in 0..7) v = v or ((data[0x18+i].toLong() and 0xFF) shl (i*8)); v }
                            else { var v=0L; for(i in 0..3) v = v or ((data[0x18+i].toLong() and 0xFF) shl (i*8)); v }
                            info.append("Entry: 0x${"%X".format(entry)}\n")
                        } else {
                            info.append("Format: Raw binary\n")
                            info.append("First bytes: ${data.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}\n")
                        }

                        // Strings count
                        val strCount = String(data).split(Regex("[\\x00-\\x1F]{4,}")).size - 1
                        info.append("Strings (4+): ~$strCount\n")

                        Text(info.toString(), color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(8.dp)).padding(8.dp))
                    } else {
                        Text("No file loaded", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            4 -> { // Log
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(log) { line ->
                        val color = when {
                            line.startsWith("[+]") -> AccentGreen
                            line.startsWith("[-]") -> AccentRed
                            else -> AccentCyan
                        }
                        Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

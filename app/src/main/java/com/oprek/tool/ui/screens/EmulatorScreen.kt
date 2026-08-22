package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
// import com.oprek.tool.core.SharedFileState // replaced by SharedFileState
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("🖥️ CPU", "🔍 Disasm", "💾 Memory", "📋 Syscalls", "⚡ Hooks", "📝 Log")
    val log = remember { mutableStateListOf<String>() }

    // CPU Registers (ARM64: X0-X30, SP, PC, PSTATE)
    val regs = remember { mutableStateOf(LongArray(33) { 0L }) }
    var pc by remember { mutableStateOf(0L) }
    var sp by remember { mutableStateOf(0x7FFF0000L) }
    var isRunning by remember { mutableStateOf(false) }
    var stepCount by remember { mutableStateOf(0) }

    // Disasm
    var disasmOut by remember { mutableStateOf("") }
    var archMode by remember { mutableStateOf(2) }

    // Memory map
    var memMaps by remember { mutableStateOf(listOf<String>()) }

    // Syscalls
    var syscallLog by remember { mutableStateOf(listOf<String>()) }

    // Hooks
    var hookAddr by remember { mutableStateOf("") }
    var hookAction by remember { mutableStateOf("NOP") }
    val hooks = remember { mutableStateOf(mutableMapOf<Long, String>()) }

    fun addLog(msg: String) {
        log.add(msg)
        if (log.size > 2000) log.removeRange(0, 500)
    }

    fun loadFile() {
        val f = SharedFileState.findFile(context)
        if (f != null) {
            val data = f.readBytes()
            fileData = data
            fileName = f.name
            addLog("[+] Loaded: ${f.name} (${data.size} bytes)")

            // Parse ELF entry + create memory map
            if (data.size >= 20 && data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte()) {
                val is64 = data[4] == 2.toByte()
                val entry = if (is64) {
                    var v = 0L; for (i in 0..7) v = v or ((data[0x18 + i].toLong() and 0xFF) shl (i * 8)); v
                } else {
                    var v = 0L; for (i in 0..3) v = v or ((data[0x18 + i].toLong() and 0xFF) shl (i * 8)); v
                }
                pc = entry
                sp = 0x7FFF0000L
                regs.value[29] = sp // FP
                regs.value[30] = 0L // LR

                val machine = (data[18].toInt() and 0xFF) or ((data[19].toInt() and 0xFF) shl 8)
                val (_, mode) = NativeLib.detectArchFromElf(machine)
                archMode = mode

                // Create fake memory maps
                memMaps = listOf(
                    "00000000-00000000 r-xp 00000000 00:00 0  ${f.name} (code)",
                    "7f000000-7f${"%06X".format(data.size.coerceAtMost(0xFFFFFF))} r--p 00000000 00:00 0  ${f.name} (rodata)",
                    "7fff0000-7fffffff rw-p 00000000 00:00 0  [stack]",
                    "ffff0000-ffffffff r-xp 00000000 00:00 0  [vdso]"
                )
                addLog("[+] Entry: 0x${"%X".format(entry)} | Arch: ${when(machine) { 0xB7->"ARM64"; 0x28->"ARM"; 0x3E->"x86_64"; else->"?" }}")
                addLog("[+] Memory maps: ${memMaps.size} regions")
            } else {
                pc = 0
                addLog("[+] Raw binary - no ELF structure")
            }
        } else {
            addLog("[-] No file in cache. Open a file first.")
        }
    }

    // Auto-refresh when file changes
    val rev = SharedFileState.revision
    LaunchedEffect(rev) { loadFile() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A))) {
        // Header
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡ Emulator", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (isRunning) {
                        Text("RUNNING", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Button(onClick = { loadFile() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Load", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (!isRunning && fileData != null) {
                            isRunning = true
                            scope.launch(Dispatchers.IO) {
                                addLog("[+] Starting emulation from 0x${"%X".format(pc)}")
                                // Simulate: disassemble and step through
                                val data = fileData ?: return@launch
                                val start = pc.toInt().coerceIn(0, (data.size - 4).coerceAtLeast(0))
                                val end = (start + 4096).coerceAtMost(data.size)
                                val code = data.sliceArray(start until end)
                                try {
                                    val result = NativeLib.disassemble(code, pc, 1, archMode, 100)
                                    val lines = result.lines().filter { it.isNotBlank() }
                                    for ((i, line) in lines.withIndex()) {
                                        if (!isRunning) break
                                        // Check hooks
                                        val addrStr = line.trim().substringBefore(" ").trim()
                                        val addr = try { addrStr.removePrefix("0x").toLong(16) } catch (e: Exception) { 0L }
                                        if (addr in hooks.value) {
                                            val action = hooks.value[addr]!!
                                            addLog("⚡ HOOK @ $addr: $action")
                                            when (action) {
                                                "NOP" -> { /* skip instruction */ }
                                                "LOG" -> addLog("📍 HIT: ${line.trim()}")
                                                "RET" -> { addLog("↩️ Return at $addr"); isRunning = false }
                                            }
                                        } else {
                                            addLog("  ${line.trim()}")
                                        }
                                        pc += 4
                                        stepCount++
                                        kotlinx.coroutines.delay(50) // visual delay
                                    }
                                    addLog("[+] Emulation complete: $stepCount instructions")
                                } catch (e: Exception) {
                                    addLog("[-] Error: ${e.message}")
                                }
                                isRunning = false
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AccentRed else AccentGreen), modifier = Modifier.height(32.dp)) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRunning) "Stop" else "Run", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        // Single step
                        if (fileData != null) {
                            val data = fileData!!
                            val start = pc.toInt().coerceIn(0, (data.size - 4).coerceAtLeast(0))
                            val code = data.sliceArray(start until (start + 4).coerceAtMost(data.size))
                            try {
                                val result = NativeLib.disassemble(code, pc, 1, archMode, 1)
                                addLog("STEP: ${result.trim()}")
                                pc += 4
                                stepCount++
                            } catch (e: Exception) { addLog("[-] Step error: ${e.message}") }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(32.dp)) {
                        Text("Step", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$fileName | Step: $stepCount", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Tabs
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF16213E)) {
            tabs.forEachIndexed { i, n ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(n, fontSize = 11.sp, color = if (selectedTab == i) AccentCyan else Color.Gray) })
            }
        }

        when (selectedTab) {
            0 -> { // CPU State
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("CPU Registers", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Key registers
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("PC = 0x${"%016X".format(pc)}", color = AccentGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text("SP = 0x${"%016X".format(sp)}", color = AccentCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text("FP = 0x${"%016X".format(regs.value[29])}", color = AccentPurple, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text("LR = 0x${"%016X".format(regs.value[30])}", color = AccentOrange, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text("Steps: $stepCount", color = Color.Gray, fontSize = 11.sp)
                                    Text("Hooks: ${hooks.value.size}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("General Purpose (X0-X30)", color = AccentCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    // Register grid
                    for (row in 0..10) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0..2) {
                                val idx = row * 3 + col
                                if (idx <= 30) {
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(4.dp)) {
                                        Column(Modifier.padding(4.dp)) {
                                            Text("X$idx", fontSize = 8.sp, color = Color.Gray)
                                            Text("0x${"%016X".format(regs.value[idx])}", fontSize = 9.sp, color = AccentGreen, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
            1 -> { // Disasm
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Disassembly @ PC", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (fileData != null) {
                        val data = fileData!!
                        val start = pc.toInt().coerceIn(0, (data.size - 4).coerceAtLeast(0))
                        val end = (start + 2048).coerceAtMost(data.size)
                        val code = data.sliceArray(start until end)
                        val disasmResult = try {
                            NativeLib.disassemble(code, pc, 1, archMode, 100)
                        } catch (e: Exception) { "[-] Error: ${e.message}" }
                        Text(disasmResult, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(8.dp)).padding(8.dp))
                    } else {
                        Text("No file loaded", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            2 -> { // Memory Map
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Memory Map", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    memMaps.forEach { map ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(4.dp)) {
                            Text(map, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (memMaps.isEmpty()) {
                        Text("No memory maps - load an ELF first", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            3 -> { // Syscalls
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("System Calls", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (syscallLog.isEmpty()) {
                        Text("No syscalls recorded yet. Start emulation to capture.", color = Color.Gray, fontSize = 12.sp)
                    }
                    syscallLog.forEach { line ->
                        Text(line, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            4 -> { // Hooks
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Function Hooks", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = hookAddr, onValueChange = { hookAddr = it }, label = { Text("Address", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                        Spacer(Modifier.width(8.dp))
                        DropdownMenuSample(hookAction, listOf("NOP", "LOG", "RET")) { hookAction = it }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val a = try { hookAddr.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                            if (a >= 0) { hooks.value[a] = hookAction; addLog("[+] Hook @ 0x${"%X".format(a)} → $hookAction"); hookAddr = "" }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) { Text("+", fontSize = 14.sp) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    hooks.value.forEach { (addr, action) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡ 0x${"%08X".format(addr)} → $action", color = AccentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { hooks.value.remove(addr) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = AccentRed)
                            }
                        }
                    }
                    if (hooks.value.isEmpty()) Text("No hooks set", color = Color.Gray, fontSize = 12.sp)
                }
            }
            5 -> { // Log
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(log) { line ->
                        val color = when {
                            line.startsWith("[+]") -> AccentGreen
                            line.startsWith("[-]") -> AccentRed
                            line.startsWith("⚡") -> AccentOrange
                            else -> AccentCyan
                        }
                        Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownMenuSample(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected, fontSize = 10.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

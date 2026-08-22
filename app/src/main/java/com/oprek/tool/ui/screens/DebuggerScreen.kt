package com.oprek.tool.ui/screens

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
import com.oprek.tool.engine.NativeLib
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggerScreen() {
    val context = LocalContext.current
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Breakpoints", "Memory", "Registers", "Watch", "Step", "Log")
    val output = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(false) }
    var useRoot by remember { mutableStateOf(false) }
    var targetPid by remember { mutableStateOf("") }
    var targetProcess by remember { mutableStateOf("") }

    // Breakpoints state
    var bpAddress by remember { mutableStateOf("") }
    val breakpoints = remember { mutableStateListOf<Long>() }
    val bpHits = remember { mutableStateOf(mapOf<Long, Int>()) }

    // Memory state
    var memAddress by remember { mutableStateOf("0x00000000") }
    var memSize by remember { mutableStateOf("256") }
    var memWriteVal by remember { mutableStateOf("") }

    // Register state
    var registers = remember { mutableStateOf(mapOf<String, Long>()) }

    // Watch state
    var watchAddress by remember { mutableStateOf("") }
    var watchSize by remember { mutableStateOf("4") }
    var watchType by remember { mutableStateOf("int32") }
    val watchValues = remember { mutableStateOf(listOf<Triple<Long, String, String>>()) }

    // Step state
    var stepPC by remember { mutableStateOf(0L) }
    var disasmLines = remember { mutableStateOf(listOf<String>()) }
    var stepCount by remember { mutableIntStateOf(10) }

    fun addLog(msg: String) {
        output.add("[${System.currentTimeMillis() % 100000}] $msg")
        if (output.size > 500) output.removeRange(0, 100)
    }

    // Find root
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val suPaths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/su/bin/su")
            for (p in suPaths) {
                if (File(p).exists()) {
                    val r = Runtime.getRuntime().exec(arrayOf(p, "-c", "id")).inputStream.bufferedReader().readText()
                    if (r.contains("uid=0")) {
                        useRoot = true
                        addLog("[+] Root found: $p")
                        return@withContext
                    }
                }
            }
            addLog("[-] No root found. File-only mode.")
        }
    }

    fun loadFile() {
        val f = context.cacheDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        if (f != null) { loadedFile = f; addLog("[+] Loaded: ${f.name} (${f.length()} bytes)") }
        else addLog("[-] No file in cache. Open a binary first.")
    }

    fun findProcesses() {
        if (!useRoot) { addLog("[-] Root required for process attach"); return }
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val su = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "ps -A"))
                val procs = su.inputStream.bufferedReader().readLines()
                addLog("[+] Running processes:")
                procs.take(30).forEach { addLog("  $it") }
                addLog("[+] Total: ${procs.size} processes")
            } catch (e: Exception) { addLog("[!] Error: ${e.message}") }
            isLoading = false
        }
    }

    // Load file on start
    LaunchedEffect(Unit) { loadFile() }

    Column(modifier = Modifier.fillMaxSize().background(AccentDark)) {
        // Top bar
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Debugger", color = AccentPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { loadFile() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier.height(32.dp)) {
                        Text("Load File", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { findProcesses() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.height(32.dp)) {
                        Text("Find PIDs", fontSize = 12.sp, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { output.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                        modifier = Modifier.height(32.dp)) {
                        Text("Clear", fontSize = 12.sp)
                    }
                }
                if (loadedFile != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("File: ${loadedFile!!.name} | Root: ${if (useRoot) "YES" else "NO"}",
                        color = AccentGreen, fontSize = 11.sp)
                }
            }
        }

        // Tabs
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF16213E)) {
            tabs.forEachIndexed { i, name ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(name, fontSize = 11.sp, color = if (selectedTab == i) AccentPurple else Color.Gray) })
            }
        }

        // Content
        when (selectedTab) {
            0 -> BreakpointsTab(breakpoints, bpAddress, { bpAddress = it }, { addr ->
                val a = try { addr.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                if (a > 0 && a !in breakpoints) {
                    breakpoints.add(a)
                    breakpoints.sort()
                    addLog("[+] BP set at 0x${a.toString(16)}")
                    // Write SWI/BKPT to binary if we have one
                    loadedFile?.let { f ->
                        try {
                            val data = f.readBytes()
                            if (a < data.size) {
                                if (data[a].toInt() and 0xFF == 0x00) {
                                    // ARM64: BRK #0
                                    data[a] = 0x00; data[a+1] = 0x00; data[a+2] = 0x20; data[a+3] = 0xD4
                                    f.writeBytes(data)
                                    addLog("[+] BRK #0 written at 0x${a.toString(16)}")
                                }
                            } else addLog("[-] Address out of range")
                        } catch (e: Exception) { addLog("[!] Patch error: ${e.message}") }
                    }
                } else if (a in breakpoints) addLog("[-] BP already exists")
                else addLog("[-] Invalid address: $addr")
            }, { idx ->
                if (idx in breakpoints.indices) {
                    val a = breakpoints[idx]
                    // Restore original bytes
                    loadedFile?.let { f ->
                        try {
                            val data = f.readBytes()
                            if (a + 3 < data.size) {
                                data[a] = 0x00; data[a+1] = 0x00; data[a+2] = 0x00; data[a+3] = 0x00
                                f.writeBytes(data)
                            }
                        } catch (_: Exception) {}
                    }
                    breakpoints.removeAt(idx)
                    addLog("[-] BP removed at 0x${a.toString(16)}")
                }
            })
            1 -> MemoryTab(memAddress, { memAddress = it }, memSize, { memSize = it },
                memWriteVal, { memWriteVal = it }, useRoot, targetPid, loadedFile, { addLog(it) })
            2 -> RegisterTab(registers, useRoot, targetPid, { registers = it }, { addLog(it) })
            3 -> WatchTab(watchAddress, { watchAddress = it }, watchSize, { watchSize = it },
                watchType, { watchType = it }, watchValues, loadedFile, useRoot, { addLog(it) })
            4 -> StepTab(stepPC, { stepPC = it }, stepCount, { stepCount = it },
                disasmLines, { disasmLines = it }, loadedFile, { addLog(it) })
            5 -> LogTab(output)
        }

        // Status bar
        Surface(color = Color(0xFF0F3460), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BP: ${breakpoints.size}", color = AccentPurple, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Root: ${if (useRoot) "YES" else "NO"}", color = if (useRoot) AccentGreen else Color.Red, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Watch: ${watchValues.size}", color = AccentCyan, fontSize = 10.sp)
                Spacer(modifier = Modifier.weight(1f))
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentPurple)
            }
        }
    }
}

@Composable
private fun BreakpointsTab(
    breakpoints: MutableList<Long>, bpAddress: String, onAddressChange: (String) -> Unit,
    onAdd: (String) -> Unit, onRemove: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Set Breakpoint", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = bpAddress, onValueChange = onAddressChange,
                label = { Text("Address (hex)", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onAdd(bpAddress) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                modifier = Modifier.height(48.dp)) { Text("Set BP", color = Color.Black, fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Active Breakpoints (${breakpoints.size})", color = AccentCyan, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(breakpoints) { idx, addr ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${idx+1}.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                    Text("0x${addr.toString(16).uppercase()}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(24.dp)) {
                        Text("X", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
        if (breakpoints.isEmpty()) {
            Text("No breakpoints set. Enter a hex address above.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MemoryTab(
    address: String, onAddressChange: (String) -> Unit,
    size: String, onSizeChange: (String) -> Unit,
    writeVal: String, onWriteValChange: (String) -> Unit,
    useRoot: Boolean, pid: String, file: File?, addLog: (String) -> Unit
) {
    var memContent by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Memory Viewer/Editor", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row {
            OutlinedTextField(value = address, onValueChange = onAddressChange,
                label = { Text("Address", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = size, onValueChange = onSizeChange,
                label = { Text("Size", fontSize = 11.sp) },
                modifier = Modifier.width(80.dp).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp))
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Button(onClick = {
                val a = try { address.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                val s = try { size.toInt() } catch (e: Exception) { 64 }
                if (a < 0 || file == null) { addLog("[-] Invalid address or no file"); return@Button }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val data = file.readBytes()
                        if (a + s > data.size) { addLog("[-] Out of range"); return@launch }
                        val hex = StringBuilder()
                        val asc = StringBuilder()
                        for (i in a until (a + s).coerceAtMost(data.size.toLong())) {
                            val b = data[i.toInt()].toInt() and 0xFF
                            hex.append(String.format("%02X ", b))
                            asc.append(if (b in 0x20..0x7E) b.toChar() else '.')
                            if ((i - a) % 16 == 15L) {
                                hex.append("  "); hex.appendLine(asc.toString())
                                asc.clear()
                            }
                        }
                        if (asc.isNotEmpty()) hex.append("  "); hex.append(asc)
                        memContent = hex.toString()
                        addLog("[+] Read $s bytes from 0x${a.toString(16)}")
                    } catch (e: Exception) { addLog("[!] Error: ${e.message}") }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(36.dp)) {
                Text("Read", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val a = try { address.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
                val vals = writeVal.trim().split(" ").mapNotNull { try { it.toInt(16).toByte() } catch (e: Exception) { null } }
                if (a < 0 || file == null || vals.isEmpty()) { addLog("[-] Invalid input"); return@Button }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val data = file.readBytes()
                        if (a + vals.size > data.size) { addLog("[-] Out of range"); return@launch }
                        for (i in vals.indices) data[(a + i).toInt()] = vals[i]
                        file.writeBytes(data)
                        addLog("[+] Wrote ${vals.size} bytes at 0x${a.toString(16)}")
                    } catch (e: Exception) { addLog("[!] Write error: ${e.message}") }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.height(36.dp)) {
                Text("Write", color = Color.Black, fontSize = 12.sp)
            }
        }

        OutlinedTextField(value = writeVal, onValueChange = onWriteValChange,
            label = { Text("Write values (hex: 48 65 6C 6C 6F)", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2)

        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = Color(0xFF0A0A1A), modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            Text(memContent.ifEmpty { "Click 'Read' to view memory contents" },
                color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun RegisterTab(
    registers: Map<String, Long>, useRoot: Boolean, pid: String,
    onRegisters: (Map<String, Long>) -> Unit, addLog: (String) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("ARM64 Registers", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            if (!useRoot) { addLog("[-] Root required"); return@Button }
            addLog("[i] Attach to process not implemented (requires ptrace)")
            // Show current register layout from ELF
            onRegisters(mapOf(
                "X0" to 0L, "X1" to 0L, "X2" to 0L, "X3" to 0L,
                "X4" to 0L, "X5" to 0L, "X6" to 0L, "X7" to 0L,
                "X8" to 0L, "X9" to 0L, "X10" to 0L, "X11" to 0L,
                "X12" to 0L, "X13" to 0L, "X14" to 0L, "X15" to 0L,
                "X16" to 0L, "X17" to 0L, "X18" to 0L, "X19" to 0L,
                "X20" to 0L, "X21" to 0L, "X22" to 0L, "X23" to 0L,
                "X24" to 0L, "X25" to 0L, "X26" to 0L, "X27" to 0L,
                "X28" to 0L, "X29" to 0L, "X30" to 0L, "SP" to 0L, "PC" to 0L
            ))
            addLog("[+] Register view initialized (attach to live process for real values)")
        }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), modifier = Modifier.height(36.dp)) {
            Text("Init Registers", color = Color.Black, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Register display
        if (registers.isNotEmpty()) {
            val regs = registers.entries.toList()
            for (chunk in regs.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for ((name, value) in chunk) {
                        Surface(color = Color(0xFF16213E), modifier = Modifier.weight(1f).padding(2.dp)) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text(name, color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(String.format("0x%016X", value), color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        } else {
            Text("No register data. Click 'Init Registers' first.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WatchTab(
    watchAddress: String, onAddressChange: (String) -> Unit,
    watchSize: String, onSizeChange: (String) -> Unit,
    watchType: String, onTypeChange: (String) -> Unit,
    watchValues: List<Triple<Long, String, String>>,
    file: File?, useRoot: Boolean, addLog: (String) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Memory Watch", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row {
            OutlinedTextField(value = watchAddress, onValueChange = onAddressChange,
                label = { Text("Address", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = watchSize, onValueChange = onSizeChange,
                label = { Text("Size", fontSize = 11.sp) },
                modifier = Modifier.width(60.dp).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp))
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Type chips
        Row {
            listOf("int8", "int16", "int32", "int64", "float", "hex").forEach { type ->
                FilterChip(selected = watchType == type, onClick = { onTypeChange(type) },
                    label = { Text(type, fontSize = 10.sp) },
                    modifier = Modifier.padding(end = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPurple,
                        selectedLabelColor = Color.White))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val a = try { watchAddress.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
            val s = try { watchSize.toInt() } catch (e: Exception) { 4 }
            if (a < 0 || file == null) { addLog("[-] Invalid input"); return@Button }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val data = file.readBytes()
                    if (a + s > data.size) { addLog("[-] Out of range"); return@launch }
                    val raw = data.slice(a.toInt() until (a + s).toInt())
                    val hex = raw.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
                    val value = when (watchType) {
                        "int8" -> "${raw[0].toInt() and 0xFF}"
                        "int16" -> "${(raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)}"
                        "int32" -> "${raw.take(4).fold(0L) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF).toLong() }}"
                        "int64" -> "${raw.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF).toLong() }}"
                        "float" -> Float.fromBits(raw.take(4).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }).toString()
                        else -> hex
                    }
                    withContext(Dispatchers.Main) {
                        addLog("[+] Watch: 0x${a.toString(16)} = $value ($hex)")
                    }
                } catch (e: Exception) { addLog("[!] Watch error: ${e.message}") }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.height(36.dp)) {
            Text("Add Watch", color = Color.Black, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Watched Addresses", color = AccentCyan, fontSize = 12.sp)
        if (watchValues.isEmpty()) {
            Text("No watches set.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StepTab(
    pc: Long, onPCChange: (Long) -> Unit,
    stepCount: Int, onStepCountChange: (Int) -> Unit,
    disasmLines: List<String>, onDisasmChange: (List<String>) -> Unit,
    file: File?, addLog: (String) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Step Through / Disassemble", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row {
            OutlinedTextField(value = String.format("0x%X", pc), onValueChange = {
                val v = try { it.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { 0L }
                onPCChange(v)
            }, label = { Text("PC Address", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = "$stepCount", onValueChange = {
                onStepCountChange(it.toIntOrNull() ?: 10)
            }, label = { Text("Lines", fontSize = 11.sp) },
                modifier = Modifier.width(60.dp).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = {
                if (file == null) { addLog("[-] No file loaded"); return@Button }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val data = file.readBytes()
                        if (pc + stepCount * 4 > data.size) { addLog("[-] Address out of range"); return@launch }
                        val result = mutableListOf<String>()
                        val insnNames = mapOf(
                            0xD4400000.toInt() to "BRK #0",
                            0xD61F0000.toInt() to "BR Xn",
                            0xD63F0000.toInt() to "BLR Xn",
                            0xD65F03C0.toInt() to "RET",
                            0xA9007BFD.toInt() to "STP X29, X30",
                            0xA9407BFD.toInt() to "LDP X29, X30",
                            0xD10003FF.toInt() to "SUB SP, SP",
                            0xF9400000.toInt() to "LDR Xn",
                            0xF9000000.toInt() to "STR Xn",
                            0x54000000.toInt() to "B.cond",
                            0x14000000.toInt() to "B",
                            0x94000000.toInt() to "BL",
                            0xB4000000.toInt() to "CBZ Xn",
                            0xB5000000.toInt() to "CBNZ Xn",
                            0xAA0003E0.toInt() to "MOV Xd, Xn",
                            0xD2800000.toInt() to "MOV Xd, #imm",
                            0xEB00001F.toInt() to "CMP Xn, Xm",
                            0x7100001F.toInt() to "CMP Wn, #imm",
                            0x54000000.toInt() to "B.cond",
                            0x35000000.toInt() to "CBNZ Wn",
                            0x34000000.toInt() to "CBZ Wn",
                        )
                        var offset = pc
                        repeat(stepCount) {
                            if (offset + 4 > data.size) return@repeat
                            val insn = (data[offset.toInt()].toInt() and 0xFF) or
                                    ((data[offset.toInt()+1].toInt() and 0xFF) shl 8) or
                                    ((data[offset.toInt()+2].toInt() and 0xFF) shl 16) or
                                    ((data[offset.toInt()+3].toInt() and 0xFF) shl 24)
                            val name = insnNames[insn and 0xFC000000.toInt()] ?: "DCD 0x${insn.toUInt().toString(16)}"
                            result.add(String.format("%08X:  %08X  %s", offset, insn, name))
                            offset += 4
                        }
                        withContext(Dispatchers.Main) {
                            onDisasmChange(result)
                            onPCChange(offset)
                            addLog("[+] Disassembled ${result.size} instructions from 0x${pc.toString(16)}")
                        }
                    } catch (e: Exception) { addLog("[!] Disasm error: ${e.message}") }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), modifier = Modifier.height(36.dp)) {
                Text("Disassemble", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                // Step next = PC + 4
                onPCChange(pc + 4)
            }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), modifier = Modifier.height(36.dp)) {
                Text("Step →", color = Color.Black, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(color = Color(0xFF0A0A1A), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(disasmLines) { line ->
                    Text(line, color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun LogTab(output: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(output) { line ->
            Text(line, color = when {
                line.contains("[+]") -> AccentGreen
                line.contains("[-]") -> Color(0xFF888888)
                line.contains("[!]") -> Color.Red
                else -> AccentCyan
            }, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

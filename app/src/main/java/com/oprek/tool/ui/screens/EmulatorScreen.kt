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
import java.util.concurrent.atomic.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen() {
    val context = LocalContext.current
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("CPU State", "Disasm", "Memory Map", "Syscalls", "Hooks", "Log")
    val output = remember { mutableStateListOf<String>() }

    // CPU State
    val regs = remember { mutableStateOf(LongArray(32) { 0L }) }
    var pc by remember { mutableLongStateOf(0L) }
    var sp by remember { mutableLongStateOf(0L) }
    var fp by remember { mutableLongStateOf(0L) }
    var lr by remember { mutableLongStateOf(0L) }
    var cpsr by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var maxSteps by remember { mutableIntStateOf(1000) }

    // Disasm
    var disasmLines = remember { mutableStateOf(listOf<String>()) }
    var disasmStart by remember { mutableStateOf("0x0") }
    var disasmCount by remember { mutableIntStateOf(20) }

    // Memory maps
    var memMaps = remember { mutableStateOf(listOf<String>()) }

    // Syscalls
    var syscallLog = remember { mutableStateOf(listOf<String>()) }
    var hookSyscalls by remember { mutableStateOf(true) }

    // Hooks
    var hookAddress by remember { mutableStateOf("") }
    var hookAction by remember { mutableStateOf("log") }
    val hooks = remember { mutableStateOf(mapOf<Long, String>()) }

    fun addLog(msg: String) {
        output.add("[${output.size}] $msg")
        if (output.size > 1000) output.removeRange(0, 200)
    }

    fun loadFile() {
        val f = context.cacheDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        if (f != null) {
            loadedFile = f
            val data = f.readBytes()
            // Parse ELF entry point
            if (data.size >= 0x20) {
                val is64 = data[4] == 2.toByte()
                if (is64 && data.size >= 0x20) {
                    pc = (data[0x18].toLong() and 0xFF) or ((data[0x19].toLong() and 0xFF) shl 8) or
                            ((data[0x1A].toLong() and 0xFF) shl 16) or ((data[0x1B].toLong() and 0xFF) shl 24) or
                            ((data[0x1C].toLong() and 0xFF) shl 32) or ((data[0x1D].toLong() and 0xFF) shl 40) or
                            ((data[0x1E].toLong() and 0xFF) shl 48) or ((data[0x1F].toLong() and 0xFF) shl 56)
                } else if (!is64 && data.size >= 0x18) {
                    pc = (data[0x18].toLong() and 0xFF) or ((data[0x19].toLong() and 0xFF) shl 8) or
                            ((data[0x1A].toLong() and 0xFF) shl 16) or ((data[0x1B].toLong() and 0xFF) shl 24)
                }
            }
            sp = 0xBFFF0000L
            fp = sp
            regs.value[30] = 0L // LR
            addLog("[+] Loaded: ${f.name} (${data.size} bytes)")
            addLog("[+] Entry point: 0x${pc.toString(16)}")
            addLog("[+] SP: 0x${sp.toString(16)}")
        } else addLog("[-] No file in cache")
    }

    // ARM64 instruction decoder
    fun decodeArm64(insn: Int): String {
        val opc = (insn shr 24) and 0xFF
        val op0 = (insn shr 25) and 0x07

        return when {
            insn == 0xD4200000 -> "SVC #0"
            insn == 0xD503201F -> "NOP"
            insn == 0xD65F03C0 -> "RET"
            insn == 0xD63F0000 -> "BLR X${(insn shr 5) and 0x1F}"
            insn == 0xD61F0000 -> "BR X${(insn shr 5) and 0x1F}"
            insn and 0xFFE003E0 == 0xA90003E0 -> "STP X${(insn shr 16) and 0x1F}, X${(insn shr 10) and 0x1F}, [X${insn and 0x1F}]"
            insn and 0xFFE003E0 == 0xA94003E0 -> "LDP X${(insn shr 16) and 0x1F}, X${(insn shr 10) and 0x1F}, [X${insn and 0x1F}]"
            insn and 0xFFC003FF == 0xD10003FF -> "SUB SP, SP, #${(insn shr 10) and 0xFFF}"
            insn and 0xFFC003FF == 0x910003FF -> "ADD SP, SP, #${(insn shr 10) and 0xFFF}"
            insn and 0xFF000000.toInt() == 0x14000000 -> "B ${String.format("0x%X", (insn and 0x3FFFFFF) * 4)}"
            insn and 0xFF000000.toInt() == 0x94000000 -> "BL ${String.format("0x%X", (insn and 0x3FFFFFF) * 4)}"
            insn and 0xFF000000.toInt() == 0xB4000000 -> "CBZ X${insn and 0x1F}, ..."
            insn and 0xFF000000.toInt() == 0xB5000000 -> "CBNZ X${insn and 0x1F}, ..."
            insn and 0xFF000000.toInt() == 0x34000000 -> "CBZ W${insn and 0x1F}, ..."
            insn and 0xFF000000.toInt() == 0x35000000 -> "CBNZ W${insn and 0x1F}, ..."
            insn and 0xFFE0001F == 0xAA0003E0 -> "MOV X${(insn shr 0) and 0x1F}, X${(insn shr 16) and 0x1F}"
            insn and 0xFF800000.toInt() == 0xD2800000 -> "MOVZ X${insn and 0x1F}, #${((insn shr 5) and 0xFFFF) shl ((insn shr 21) and 3) * 16}"
            insn and 0xFFE00000.toInt() == 0xF9400000 -> "LDR X${insn and 0x1F}, [X${(insn shr 5) and 0x1F}]"
            insn and 0xFFE00000.toInt() == 0xF9000000 -> "STR X${insn and 0x1F}, [X${(insn shr 5) and 0x1F}]"
            insn and 0xFFE00000.toInt() == 0xB9400000 -> "LDR W${insn and 0x1F}, [X${(insn shr 5) and 0x1F}]"
            insn and 0xFFE00000.toInt() == 0xB9000000 -> "STR W${insn and 0x1F}, [X${(insn shr 5) and 0x1F}]"
            insn and 0xFFC003E0.toInt() == 0xF81F0000 -> "STR X${insn and 0x1F}, [X${(insn shr 5) and 0x1F}, #-${(insn shr 10) and 0xFFF}]!"
            insn and 0xFFC003E0.toInt() == 0xF84003E0 -> "LDR X${insn and 0x1F}, [X${(insn shr 5) and 0x1F}], #${(insn shr 10) and 0xFFF}"
            insn == 0xD69F03E0 -> "ERET"
            insn and 0xFF000000.toInt() == 0xD4000000 -> "BRK #${(insn shr 5) and 0xFFF}"
            insn and 0xFFC00000.toInt() == 0xF8400000 -> "LDR X${insn and 0x1F}, [X${(insn shr 5) and 0x1F}, #${(insn shr 10) and 0xFFF}]"
            else -> "UND ${String.format("0x%08X", insn)}"
        }
    }

    fun readInsn(data: ByteArray, addr: Long): Int {
        if (addr < 0 || addr + 4 > data.size) return 0
        return (data[addr.toInt()].toInt() and 0xFF) or
                ((data[addr.toInt()+1].toInt() and 0xFF) shl 8) or
                ((data[addr.toInt()+2].toInt() and 0xFF) shl 16) or
                ((data[addr.toInt()+3].toInt() and 0xFF) shl 24)
    }

    fun stepEmulation() {
        val f = loadedFile ?: return
        val data = f.readBytes()
        if (pc + 4 > data.size) { addLog("[!] PC out of range"); return }

        val insn = readInsn(data, pc)
        val decoded = decodeArm64(insn)
        val regsCopy = regs.value.copyOf()

        // Simulate execution
        when {
            decoded.startsWith("NOP") -> { pc += 4 }
            decoded.startsWith("MOV X") -> {
                val parts = decoded.replace("MOV X", "").split(", X")
                if (parts.size == 2) {
                    val rd = parts[0].trim().toIntOrNull() ?: 0
                    val rn = parts[1].trim().toIntOrNull() ?: 0
                    regs.value[rd] = regsCopy[rn]
                }
                pc += 4
            }
            decoded.startsWith("MOVZ X") -> {
                val parts = decoded.replace("MOVZ X", "").split(", #")
                if (parts.size >= 2) {
                    val rd = parts[0].trim().toIntOrNull() ?: 0
                    val imm = parts[1].trim().split(" ")[0].toLongOrNull() ?: 0
                    regs.value[rd] = imm
                }
                pc += 4
            }
            decoded.startsWith("ADD SP") -> {
                val imm = Regex("#(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                sp += imm; pc += 4
            }
            decoded.startsWith("SUB SP") -> {
                val imm = Regex("#(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                sp -= imm; pc += 4
            }
            decoded.startsWith("STR X") -> {
                val parts = decoded.replace("STR X", "").split(", [X")
                if (parts.size >= 2) {
                    val rt = parts[0].trim().toIntOrNull() ?: 0
                    val rn = parts[1].trim().removeSuffix("]").toIntOrNull() ?: 0
                    val addr = regsCopy[rn]
                    addLog("[MEM] STR X$rt -> [X$rn] = 0x${addr.toString(16)}")
                }
                pc += 4
            }
            decoded.startsWith("LDR X") -> {
                val parts = decoded.replace("LDR X", "").split(", [X")
                if (parts.size >= 2) {
                    val rt = parts[0].trim().toIntOrNull() ?: 0
                    val rn = parts[1].trim().removeSuffix("]").trim().split(",")[0].toIntOrNull() ?: 0
                    if (rn < 32) regs.value[rt] = regsCopy[rn]
                    addLog("[MEM] LDR X$rt <- [X$rn]")
                }
                pc += 4
            }
            decoded.startsWith("STP") -> { pc += 4 }
            decoded.startsWith("LDP") -> { pc += 4 }
            decoded.startsWith("RET") -> {
                if (lr != 0L) { pc = lr; lr = 0L }
                else { addLog("[!] RET with LR=0, stopping"); isRunning = false }
            }
            decoded.startsWith("BL ") -> {
                val target = Regex("0x([\\dA-F]+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull(16) ?: 0
                lr = pc + 4
                pc = target
                addLog("[CALL] BL 0x${target.toString(16)} (LR=0x${lr.toString(16)})")
            }
            decoded.startsWith("BR X") -> {
                val rn = Regex("BR X(\\d+)").find(decoded)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                pc = regsCopy[rn]
                addLog("[JMP] BR X$rn -> 0x${pc.toString(16)}")
            }
            decoded.startsWith("BLR X") -> {
                val rn = Regex("BLR X(\\d+)").find(decoded)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                lr = pc + 4
                pc = regsCopy[rn]
                addLog("[CALL] BLR X$rn -> 0x${pc.toString(16)}")
            }
            decoded.startsWith("CBZ") -> {
                val parts = Regex("CBNZ? X(\\d+)").find(decoded)
                val rt = parts?.groupValues?.get(1)?.toIntOrNull() ?: 0
                pc += 4
                addLog("[CBZ] X$rt = ${regsCopy[rt]}")
            }
            decoded.startsWith("CBNZ") -> {
                val rt = Regex("CBNZ X(\\d+)").find(decoded)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (regsCopy[rt] != 0L) {
                    addLog("[CBNZ] X$rt != 0, would branch")
                }
                pc += 4
            }
            decoded.startsWith("SVC") -> {
                val imm = Regex("#(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val syscallNum = regsCopy[8] // X8 = syscall number on ARM64
                addLog("[SYSCALL] SVC #$imm (X8=$syscallNum, X0=${regsCopy[0]})")
                when (syscallNum) {
                    63L -> { // read
                        addLog("  -> read(${regsCopy[0]}, buf, ${regsCopy[2]})")
                        regs.value[0] = regsCopy[2] // return bytes read
                    }
                    64L -> { // write
                        addLog("  -> write(${regsCopy[0]}, buf, ${regsCopy[2]})")
                        regs.value[0] = regsCopy[2]
                    }
                    57L -> { // fork
                        addLog("  -> fork() = 0 (child)")
                        regs.value[0] = 0
                    }
                    56L -> { // exit
                        addLog("  -> exit(${regsCopy[0]})")
                        isRunning = false
                    }
                    221L -> { // execve
                        addLog("  -> execve()")
                        regs.value[0] = -2 // ENOENT
                    }
                    else -> {
                        addLog("  -> syscall #$syscallNum")
                        regs.value[0] = 0
                    }
                }
                pc += 4
            }
            decoded.startsWith("BRK") -> {
                addLog("[TRAP] BRK at 0x${pc.toString(16)}")
                isRunning = false
                pc += 4
            }
            decoded.startsWith("B ") -> {
                val target = Regex("0x([\\dA-F]+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull(16) ?: 0
                pc = target
                addLog("[JMP] B 0x${target.toString(16)}")
            }
            decoded.startsWith("B.cond") || decoded.startsWith("B.EQ") || decoded.startsWith("B.NE") -> {
                addLog("[BCC] Conditional branch at 0x${pc.toString(16)}")
                pc += 4
            }
            decoded.startsWith("ERET") -> {
                addLog("[!] ERET - cannot emulate in userspace")
                isRunning = false
            }
            else -> {
                pc += 4
            }
        }
    }

    fun runEmulation() {
        if (loadedFile == null) { addLog("[-] No file loaded"); return }
        isRunning = true
        CoroutineScope(Dispatchers.Default).launch {
            val steps = mutableListOf<String>()
            var count = 0
            while (isRunning && count < maxSteps) {
                val data = loadedFile!!.readBytes()
                if (pc + 4 > data.size) { addLog("[!] PC out of bounds"); break }
                val insn = readInsn(data, pc)
                val decoded = decodeArm64(insn)
                steps.add(String.format("  %08X:  %08X  %s  X0=%X SP=%X", pc, insn, decoded, regs.value[0], sp))
                stepEmulation()
                count++
                if (count % 100 == 0) {
                    withContext(Dispatchers.Main) {
                        addLog("[...] Step $count, PC=0x${pc.toString(16)}")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                disasmLines = steps
                addLog("[+] Emulation done: $count steps, PC=0x${pc.toString(16)}")
                isRunning = false
            }
        }
    }

    // Load file on start
    LaunchedEffect(Unit) { loadFile() }

    Column(modifier = Modifier.fillMaxSize().background(AccentDark)) {
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ARM64 Emulator", color = AccentPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { loadFile() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier.height(32.dp)) { Text("Load", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { runEmulation() },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier.height(32.dp)) { Text(if (isRunning) "Running..." else "▶ Run", color = Color.Black, fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (loadedFile != null) stepEmulation() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.height(32.dp)) { Text("Step", color = Color.Black, fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { isRunning = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.height(32.dp)) { Text("Stop", fontSize = 12.sp) }
                }
                if (loadedFile != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${loadedFile!!.name} | PC: 0x${pc.toString(16)} | Steps: ${disasmLines.size}",
                        color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF16213E)) {
            tabs.forEachIndexed { i, name ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(name, fontSize = 10.sp, color = if (selectedTab == i) AccentPurple else Color.Gray) })
            }
        }

        when (selectedTab) {
            0 -> CpuStateTab(regs.value, pc, sp, fp, lr, cpsr)
            1 -> EmuDisasmTab(disasmLines.value)
            2 -> MemoryMapTab(memMaps.value)
            3 -> SyscallTab(syscallLog.value, hookSyscalls, { hookSyscalls = it })
            4 -> EmuHookTab(hookAddress, { hookAddress = it }, hookAction, { hookAction = it },
                hooks.value, { a, action -> })
            5 -> EmuLogTab(output)
        }

        Surface(color = Color(0xFF0F3460), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(modifier = Modifier.padding(8.dp)) {
                Text("PC: 0x${pc.toString(16)}", color = AccentPurple, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(12.dp))
                Text("SP: 0x${sp.toString(16)}", color = AccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(12.dp))
                Text("X0: 0x${regs.value[0].toString(16)}", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Steps: ${disasmLines.size}", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CpuStateTab(regs: LongArray, pc: Long, sp: Long, fp: Long, lr: Long, cpsr: Long) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Text("General Purpose Registers (ARM64)", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(regs.indices.toList().chunked(2)) { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (idx in pair) {
                    Surface(color = Color(0xFF16213E), modifier = Modifier.weight(1f).padding(2.dp)) {
                        Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("X$idx", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Text(String.format("%016X", regs[idx]), color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Special Registers", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("PC" to pc, "SP" to sp, "FP" to fp, "LR" to lr, "CPSR" to cpsr).forEach { (name, value) ->
                    Surface(color = Color(0xFF1A1A3E), modifier = Modifier.weight(1f).padding(2.dp)) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(name, color = AccentPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(String.format("%016X", value), color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmuDisasmTab(lines: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(lines) { line ->
            Text(line, color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (lines.isEmpty()) {
            item { Text("Click '▶ Run' to emulate or 'Step' to single-step.", color = Color.Gray, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun MemoryMapTab(maps: List<String>) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Memory Maps", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (maps.isEmpty()) {
            Text("No memory maps loaded. Emulate first.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SyscallTab(log: List<String>, hookSyscalls: Boolean, onToggle: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Syscall Log", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = hookSyscalls, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentPurple))
            Text("Hook", color = Color.Gray, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(log) { line ->
                Text(line, color = AccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun EmuHookTab(
    address: String, onAddressChange: (String) -> Unit,
    action: String, onActionChange: (String) -> Unit,
    hooks: Map<Long, String>, onAdd: (Long, String) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("Function Hooks", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            OutlinedTextField(value = address, onValueChange = onAddressChange,
                label = { Text("Address", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = action, onValueChange = onActionChange,
                label = { Text("Action", fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val a = try { address.trim().removePrefix("0x").toLong(16) } catch (e: Exception) { -1L }
            if (a > 0) onAdd(a, action)
        }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.height(36.dp)) {
            Text("Add Hook", color = Color.Black, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (hooks.isEmpty()) {
            Text("No hooks set. Use this to intercept function calls.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmuLogTab(output: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(output) { line ->
            Text(line, color = when {
                line.contains("[+]") -> AccentGreen
                line.contains("[-]") -> Color(0xFF666666)
                line.contains("[!]") -> Color.Red
                line.contains("[SYSCALL]") -> Color.Yellow
                line.contains("[MEM]") -> AccentCyan
                line.contains("[CALL]") -> AccentPurple
                line.contains("[JMP]") -> AccentOrange
                else -> Color.White
            }, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

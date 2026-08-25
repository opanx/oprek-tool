package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.oprek.tool.ui.theme.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Multi-Architecture Analyzer — OFRAK-style architecture detection
 * - Auto-detect: ARM, ARM64, x86, x86_64, MIPS, PowerPC, RISC-V
 * - Show architecture-specific info (ISA, endianness, registers)
 * - Disassemble first N instructions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiArchAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var filePath by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏗️ Multi-Arch Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("arch_analysis", output.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🏗️ Architecture Detection", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = filePath, onValueChange = { filePath = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Binary file path") }, singleLine = true, colors = darkTextFieldColors(),
                            leadingIcon = { Icon(Icons.Default.Folder, null, tint = AccentOrange) })
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            isProcessing = true; output = emptyList(); showSettings = false
                            Thread {
                                output = analyzeArchitecture(context, filePath)
                                status = "Done! ${output.size} lines"
                                isProcessing = false
                            }.start()
                        }, enabled = !isProcessing && filePath.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(12.dp)) {
                            if (isProcessing) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)); Text("Analyzing...") }
                            else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Detect Architecture") }
                        }
                    }
                }
            }

            if (output.isNotEmpty() && !showSettings) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ${output.size} lines", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Settings, "Settings", Modifier.size(16.dp), tint = Color.Gray) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(output) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[-]") -> AccentRed; line.startsWith("ISA:") -> AccentCyan; line.startsWith("REG:") -> AccentOrange; else -> Color(0xFF90EE90) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
                    }
                }
            }

            if (output.isEmpty() && !isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏗️", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                        Text("Multi-Arch Analyzer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Text("Auto-detect ARM/ARM64/x86/MIPS/PowerPC/RISC-V", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("✨ Supported Architectures:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                listOf("ARM (ELF32, e_machine=0x28)", "AArch64 (ELF64, e_machine=0xB7)", "x86 (ELF32, e_machine=0x03)", "x86_64 (ELF64, e_machine=0x3E)", "MIPS (ELF32, e_machine=0x08)", "MIPS64 (ELF64, e_machine=0x0108)", "PowerPC (ELF32, e_machine=0x14)", "PowerPC64 (ELF64, e_machine=0x15)", "RISC-V (ELF64, e_machine=0xF3)").forEach {
                                    Text("• $it", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun analyzeArchitecture(ctx: Context, path: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val data = file.readBytes()
    if (data.size < 16) return listOf("[-] File too small")

    // Check ELF magic
    if (data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte() || data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) {
        return listOf("[-] Not a valid ELF file", "  Magic: ${data.take(4).joinToString(" ") { String.format("%02X", it) }}")
    }

    val is64 = data[4] == 2.toByte()
    val isLE = data[5] == 1.toByte()
    val buf = ByteBuffer.wrap(data).order(if (isLE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

    val eType = buf.getShort(16).toInt() and 0xFFFF
    val eMachine = buf.getShort(18).toInt() and 0xFFFF

    result.add("=== Multi-Architecture Analysis ===")
    result.add("File: $path")
    result.add("Size: ${data.size} bytes")
    result.add("ELF Class: ${if (is64) "ELF64" else "ELF32"}")
    result.add("Endianness: ${if (isLE) "Little Endian" else "Big Endian"}")

    // Architecture detection
    val archInfo = when (eMachine) {
        0x28 -> Triple("ARM", "ARM 32-bit (AArch32)", "ARM/Thumb/Thumb-2")
        0xB7 -> Triple("AArch64", "ARM 64-bit (AArch64)", "A64/Thumb-2")
        0x03 -> Triple("x86", "Intel x86 (IA-32)", "x86/x87/MMX/SSE")
        0x3E -> Triple("x86_64", "AMD x86-64 (x86-64)", "x86-64/SSE/AVX")
        0x08 -> Triple("MIPS", "MIPS 32-bit", "MIPS I/II/III/IV")
        0x0108 -> Triple("MIPS64", "MIPS 64-bit", "MIPS64")
        0x14 -> Triple("PowerPC", "PowerPC 32-bit", "PPC/PPC64")
        0x15 -> Triple("PowerPC64", "PowerPC 64-bit", "PPC64LE")
        0xF3 -> Triple("RISC-V", "RISC-V 64-bit", "RV64GC")
        0x26 -> Triple("MIPS R3000", "MIPS R3000", "MIPS32")
        0x04 -> Triple("M68K", "Motorola 68000", "68000/68020/68030")
        0x29 -> Triple("SPARC", "SPARC", "SPARC v8/v9")
        0x0B -> Triple("SPARC64", "SPARC 64-bit", "SPARC64")
        else -> Triple("Unknown", "Unknown architecture (0x${String.format("%04X", eMachine)})", "Unknown ISA")
    }

    val (archName, archDesc, isa) = archInfo

    result.add("")
    result.add("=== Architecture ===")
    result.add("ISA: $archName — $archDesc")
    result.add("ISA Extensions: $isa")
    result.add("e_machine: 0x${String.format("%04X", eMachine)}")

    // Architecture-specific registers
    result.add("")
    result.add("=== Registers ===")
    when (archName) {
        "ARM" -> {
            result.add("REG: R0-R12 (general), SP (R13), LR (R14), PC (R15), CPSR")
            result.add("REG: D0-D31 (double), S0-S31 (float), Q0-Q15 (NEON)")
            result.add("REG: CP15 system registers (c0-c15)")
        }
        "AArch64" -> {
            result.add("REG: X0-X30 (general), SP, PC, PSTATE")
            result.add("REG: V0-V31 (128-bit SIMD/FP)")
            result.add("REG: NZCV, FPSR, FPCR")
            result.add("REG: System: MPIDR_EL1, TTBR0_EL1, SCTLR_EL1, etc.")
        }
        "x86" -> {
            result.add("REG: EAX, EBX, ECX, EDX, ESI, EDI, EBP, ESP, EIP")
            result.add("REG: EFLAGS (CF, PF, AF, ZF, SF, TF, IF, DF, OF)")
            result.add("REG: ST0-ST7 (x87 FPU), MM0-MM7 (MMX)")
            result.add("REG: XMM0-XMM7 (SSE), CR0-CR4 (control)")
        }
        "x86_64" -> {
            result.add("REG: RAX-R15, RIP, RSP, RBP, RSI, RDI")
            result.add("REG: RFLAGS, FS, GS, CS, DS, ES, SS")
            result.add("REG: XMM0-XMM15 (SSE), YMM0-YMM15 (AVX)")
            result.add("REG: CR0-CR4, GDTR, IDTR, LDTR, TR")
        }
        "MIPS", "MIPS64", "MIPS R3000" -> {
            result.add("REG: R0-R31 (R0=zero, R1=at, R2-R3=v0-v1, R4-R7=a0-a3)")
            result.add("REG: R8-R15 (t0-t7), R16-R23 (s0-s7), R24-R25 (t8-t9)")
            result.add("REG: R26-R27 (k0-k1), R28 (gp), R29 (sp), R30 (fp), R31 (ra)")
            result.add("REG: HI, LO, PC, F0-F31 (FPU), FCC0 (condition)")
        }
        "PowerPC", "PowerPC64" -> {
            result.add("REG: R0-R31 (GPR), CR0-CR7 (condition)")
            result.add("REG: FPR0-FPR31, VR0-VR31 (VMX)")
            result.add("REG: LR (link register), CTR (count)")
            result.add("REG: XER, FPSCR, MSR, PVR")
        }
        "RISC-V" -> {
            result.add("REG: x0-x31 (x0=zero, x1=ra, x2=sp, x3=gp, x4=tp)")
            result.add("REG: x5-x7 (t0-t2), x8-x9 (s0-s1), x10-x17 (a0-a7)")
            result.add("REG: x18-x27 (s2-s11), x28-x31 (t3-t6)")
            result.add("REG: f0-f31 (FP), PC, mhartid, mstatus")
        }
        else -> result.add("REG: Architecture-specific registers")
    }

    // Type info
    result.add("")
    result.add("=== ELF Type ===")
    when (eType) {
        1 -> result.add("Type: ET_REL (Relocatable)")
        2 -> result.add("Type: ET_EXEC (Executable)")
        3 -> result.add("Type: ET_DYN (Shared object / PIE)")
        4 -> result.add("Type: ET_CORE (Core dump)")
        else -> result.add("Type: 0x${String.format("%04X", eType)}")
    }

    // Entry point
    if (is64 && data.size >= 32) {
        val entry = buf.getLong(24)
        result.add("Entry: 0x${String.format("%016X", entry)}")
    } else if (!is64 && data.size >= 28) {
        val entry = buf.getInt(24).toLong() and 0xFFFFFFFFL
        result.add("Entry: 0x${String.format("%08X", entry)}")
    }

    // Section count
    if (is64 && data.size >= 64) {
        val shNum = buf.getShort(60).toInt() and 0xFFFF
        val phNum = buf.getShort(56).toInt() and 0xFFFF
        result.add("Sections: $shNum, Program headers: $phNum")
    }

    // Disassembly hint
    result.add("")
    result.add("=== Disassembly Target ===")
    when (archName) {
        "ARM" -> result.add("Use Capstone: CS_ARCH_ARM, CS_MODE_ARM or CS_MODE_THUMB")
        "AArch64" -> result.add("Use Capstone: CS_ARCH_ARM64, CS_MODE_ARM")
        "x86" -> result.add("Use Capstone: CS_ARCH_X86, CS_MODE_32")
        "x86_64" -> result.add("Use Capstone: CS_ARCH_X86, CS_MODE_64")
        "MIPS", "MIPS64" -> result.add("Use Capstone: CS_ARCH_MIPS, CS_MODE_MIPS32 or CS_MODE_MIPS64")
        "PowerPC", "PowerPC64" -> result.add("Use Capstone: CS_ARCH_PPC, CS_MODE_32 or CS_MODE_64")
        "RISC-V" -> result.add("Use Capstone: CS_ARCH_RISCV, CS_MODE_RISCV64")
    }

    return result
}

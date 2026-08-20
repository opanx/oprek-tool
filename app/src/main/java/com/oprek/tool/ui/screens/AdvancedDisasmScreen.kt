package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDisasmScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var offset by remember { mutableStateOf("0") }
    var arch by remember { mutableStateOf(1) } // 0=ARM32 1=ARM64 2=X86
    var instructions by remember { mutableStateOf(listOf<String>()) }
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf()
                    withContext(Dispatchers.Main) { fileBytes = bytes; loaded = true }
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Disassembler", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Binary") }
            }
            if (loaded) {
                Row {
                    OutlinedTextField(value = offset, onValueChange = { offset = it }, label = { Text("Offset (hex)") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
                    Row { listOf("ARM32", "ARM64", "X86").forEachIndexed { i, name ->
                        FilterChip(selected = arch == i, onClick = { arch = i }, label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(0.2f)))
                    }}
                }
                Button(onClick = {
                    error = ""
                    instructions = listOf()
                    try {
                        val off = offset.toLong(16)
                        val count = 50
                        val end = minOf((off + count * 4).toInt(), fileBytes.size)
                        if (off >= fileBytes.size) { error = "Offset out of range"; return@Button }
                        val chunk = fileBytes.copyOfRange(off.toInt(), end)
                        // Display as hex disassembly (real capstone not bundled)
                        val lines = mutableListOf<String>()
                        for (i in chunk.indices step 4) {
                            if (i + 4 > chunk.size) break
                            val insn = chunk[i].toInt() and 0xFF or ((chunk[i+1].toInt() and 0xFF) shl 8) or
                                ((chunk[i+2].toInt() and 0xFF) shl 16) or ((chunk[i+3].toInt() and 0xFF) shl 24)
                            val addr = off + i
                            val hex = "%02X %02X %02X %02X".format(chunk[i], chunk[i+1], chunk[i+2], chunk[i+3])
                            val mnemonic = decodeInsnARM64(insn)
                            lines.add("0x${"%08X".format(addr)}:  $hex  $mnemonic")
                        }
                        instructions = lines
                    } catch (e: Exception) { error = e.message ?: "Error" }
                }, Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Disassemble") }
                if (error.isNotEmpty()) Text(error, color = AccentRed, fontSize = 11.sp)
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {

                    itemsIndexed(instructions) { _, line ->
                        Text(line, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp).horizontalScroll(rememberScrollState()))
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { instructions.joinToString("\n") },
                filename = "adv_disasm.txt",
                subfolder = "disasm"
            )

        }

    }
}

private fun decodeInsnARM64(insn: Int): String {
    val opc = (insn shr 26) and 0x3F
    when (opc) {
        0x25 -> return "BL #0x${"%08X".format(((insn and 0x3FFFFFF).toLong() shl 2))}"
        0x05 -> return "B #0x${"%08X".format(((insn and 0x3FFFFFF).toLong() shl 2))}"
        0x54 -> {
            val cond = insn and 0xF
            val condStr = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE","AL","NV")[cond]
            return "B.$condStr #0x${"%X".format(((insn shr 5) and 0x7FFFF).toLong() shl 2)}"
        }
        0xD4 -> return "SVC #0x${"%X".format((insn shr 5) and 0xFFFF)}"
    }
    // Data processing immediate
    val op0 = (insn shr 23) and 0x3FF
    if ((op0 and 0x1FC) == 0x090) return "MOV W0, #0x${"%X".format(insn and 0xFFFF)}"
    if ((op0 and 0x1FC) == 0x110) return "MOV X0, #0x${"%X".format(insn and 0xFFFF)}"
    // ADRP
    val opHi = (insn shr 24) and 0xFF
    if (opHi == 0x90) return "ADRP X${(insn shr 0) and 0x1F}, #imm"
    // Load/Store
    val ldr = (insn shr 22) and 0x3FF
    if (ldr == 0x1E1) return "LDR X${(insn and 0x1F)}, [X${(insn shr 5) and 0x1F}]"
    if (ldr == 0x1E5) return "LDR W${(insn and 0x1F)}, [X${(insn shr 5) and 0x1F}]"
    if (ldr == 0x1E0) return "STR X${(insn and 0x1F)}, [X${(insn shr 5) and 0x1F}]"
    // RET
    if (insn == 0xD65F03C0.toInt()) return "RET"
    // NOP
    if (insn == 0xD503201F.toInt()) return "NOP"
    // STP
    val stp = (insn shr 22) and 0x1FF
    if (stp == 0x1A5) return "STP X29, X30, [SP, #-0x10]!"
    if (stp == 0x2A5) return "STP X29, X30, [SP, #0x10]"
    // SUB
    val sub = (insn shr 23) and 0x1FF
    if (sub == 0x235) return "SUB SP, SP, #imm"
    if (sub == 0x225) return "SUB X${(insn shr 0) and 0x1F}, X${(insn shr 5) and 0x1F}, #imm"
    return ".word 0x${"%08X".format(insn)}"
}

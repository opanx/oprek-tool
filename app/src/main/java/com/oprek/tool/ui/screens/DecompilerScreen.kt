package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.NavController
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecompilerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var funcName by remember { mutableStateOf("") }
    var hasNative by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { NativeLib.elfValidate(byteArrayOf(0x7F, 0x45, 0x4C, 0x46)); hasNative = true } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Pseudo-C Decompiler", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Function Name (optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = funcName, onValueChange = { funcName = it },
                        label = { Text("e.g. sub_12345 or main") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                }
            }

            Button(onClick = {
                isLoading = true
                scope.launch(Dispatchers.Default) {
                    try {
                        val file = context.cacheDir.listFiles()?.firstOrNull()
                        if (file == null) { result = "No file loaded"; isLoading = false; return@launch }
                        val data = StreamingIO.readRange(file, 0, minOf(file.length(), 200000L).toInt())
                        // Basic pseudo-C decompilation from disassembly
                        val disasm = withContext(Dispatchers.IO) {
                            NativeLib.disassemble(data, 0, 1, 2, 500) // ARM64
                        }
                        result = decompileToPseudoC(disasm, funcName.ifEmpty { "main" })
                    } catch (e: Exception) { result = "Error: ${e.message}" }
                    isLoading = false
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(12.dp), enabled = !isLoading && hasNative) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Decompile", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))
            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Pseudo-C Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        Text(result, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutputButton(content = { result }, filename = "decompile.c", subfolder = "decompile")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun decompileToPseudoC(disasm: String, funcName: String): String {
    val sb = StringBuilder()
    sb.appendLine("// Pseudo-C decompilation of $funcName")
    sb.appendLine("// Generated by OprekTool Decompiler")
    sb.appendLine()
    sb.appendLine("void $funcName() {")

    val lines = disasm.lines().filter { it.contains("0x") }
    val locals = mutableSetOf<String>()
    val params = mutableSetOf<String>()

    for (line in lines) {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 3) continue
        val mnemonic = parts.getOrElse(2) { "" }
        val operands = parts.drop(3).joinToString(" ")

        when {
            mnemonic.startsWith("stp") || mnemonic.startsWith("str") -> {
                if (operands.contains("x29") || operands.contains("fp")) {
                    sb.appendLine("    // function prologue")
                }
            }
            mnemonic == "ret" -> {
                sb.appendLine("    return;")
            }
            mnemonic.startsWith("bl") && !mnemonic.startsWith("blr") -> {
                val target = operands.split(",").firstOrNull()?.trim() ?: ""
                sb.appendLine("    ${target.removePrefix("#")}();")
            }
            mnemonic.startsWith("b.") -> {
                val cond = mnemonic.removePrefix("b.")
                sb.appendLine("    if ($cond) {")
                sb.appendLine("        // conditional branch")
                sb.appendLine("    }")
            }
            mnemonic == "b" || mnemonic == "br" -> {
                val target = operands.split(",").firstOrNull()?.trim() ?: ""
                sb.appendLine("    goto ${target.removePrefix("#")};")
            }
            mnemonic.startsWith("mov") -> {
                val dst = operands.split(",").firstOrNull()?.trim() ?: ""
                val src = operands.split(",").getOrNull(1)?.trim() ?: ""
                if (dst.startsWith("x") && src.startsWith("#")) {
                    val reg = "v_" + dst.removePrefix("x")
                    if (locals.add(reg)) sb.appendLine("    long $reg = ${src.removePrefix("#")};")
                    else sb.appendLine("    $reg = ${src.removePrefix("#")};")
                }
            }
            mnemonic.startsWith("ldr") -> {
                sb.appendLine("    // load from memory")
            }
            mnemonic.startsWith("cmp") -> {
                sb.appendLine("    // compare")
            }
        }
    }
    sb.appendLine("}")
    return sb.toString()
}

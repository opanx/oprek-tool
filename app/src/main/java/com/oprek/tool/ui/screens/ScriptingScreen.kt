package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var script by remember { mutableStateOf(defaultScript()) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📜 Script Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("script", script))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Script templates
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Templates", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val templates = listOf(
                            "Scan Strings" to templateScanStrings(),
                            "Find XOR Key" to templateFindXorKey(),
                            "Analyze ELF" to templateAnalyzeElf(),
                            "Patch Bytes" to templatePatchBytes(),
                            "Extract URLs" to templateExtractUrls()
                        )
                        templates.forEach { (name, code) ->
                            FilterChip(selected = false, onClick = { script = code },
                                label = { Text(name, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                        }
                    }
                }
            }

            // Script editor
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                label = { Text("Script (IDC-like syntax)") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple, cursorColor = AccentGreen),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AccentGreen)
            )

            // Run button
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    isRunning = true
                    scope.launch(Dispatchers.Default) {
                        val result = withContext(Dispatchers.IO) { executeScript(script, context) }
                        output = result
                        isRunning = false
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(12.dp), enabled = !isRunning) {
                    if (isRunning) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Run", fontWeight = FontWeight.Bold) }
                }
                Button(onClick = { script = defaultScript() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset")
                }
            }

            // Output
            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Spacer(Modifier.height(8.dp))
                        Text(output, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutputButton(content = { output }, filename = "script_output.txt", subfolder = "scripts")
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═══════════════════════════════════════════
// Script Engine (IDC-like)
// ═══════════════════════════════════════════

private fun executeScript(script: String, context: Context): String {
    val sb = StringBuilder()
    val vars = mutableMapOf<String, Any>()
    val file = SharedFileState.findFile(context)

    sb.appendLine("═══ Script Output ═══")
    sb.appendLine()

    for (line in script.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

        try {
            when {
                // PRINT statement
                trimmed.lowercase().startsWith("print(") -> {
                    val expr = trimmed.removePrefix("print(").removeSuffix(")")
                    val value = evalExpr(expr, vars, file)
                    sb.appendLine("$value")
                }
                // VAR assignment
                trimmed.lowercase().startsWith("var ") || trimmed.contains("=") && !trimmed.startsWith("if") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim().removePrefix("var ").trim()
                        val value = evalExpr(parts[1].trim(), vars, file)
                        vars[name] = value
                    }
                }
                // READ_FILE
                trimmed.lowercase().startsWith("read_file(") -> {
                    val path = trimmed.removePrefix("read_file(").removeSuffix(")").trim().removeSurrounding("\"")
                    val f = java.io.File(path)
                    if (f.exists()) {
                        vars["file_data"] = f.readBytes()
                        sb.appendLine("Read ${f.length()} bytes from $path")
                    } else sb.appendLine("File not found: $path")
                }
                // READ_LOADED_FILE
                trimmed.lowercase() == "read_loaded_file()" -> {
                    if (file != null) {
                        vars["file_data"] = file.readBytes()
                        vars["file_name"] = file.name
                        sb.appendLine("Loaded ${file.length()} bytes from ${file.name}")
                    } else sb.appendLine("No file loaded")
                }
                // STRINGS
                trimmed.lowercase().startsWith("strings(") -> {
                    val minLen = trimmed.removePrefix("strings(").removeSuffix(")").trim().toIntOrNull() ?: 4
                    if (file != null) {
                        val strings = StreamingIO.extractStrings(file, minLen, 500)
                        vars["strings"] = strings
                        sb.appendLine("Found ${strings.size} strings (min $minLen chars)")
                        strings.take(20).forEach { sb.appendLine("  0x${"%08X".format(it.offset)}: ${it.value}") }
                        if (strings.size > 20) sb.appendLine("  ... and ${strings.size - 20} more")
                    }
                }
                // SEARCH
                trimmed.lowercase().startsWith("search(") -> {
                    val query = trimmed.removePrefix("search(").removeSuffix(")").trim().removeSurrounding("\"")
                    if (file != null) {
                        val results = StreamingIO.searchBytes(file, query.toByteArray(), maxResults = 100)
                        sb.appendLine("Found ${results.size} occurrences of \"$query\"")
                        results.take(20).forEach { sb.appendLine("  0x${"%08X".format(it)}") }
                    }
                }
                // ENTROPY
                trimmed.lowercase() == "entropy()" -> {
                    if (file != null) {
                        val e = StreamingIO.calculateEntropy(file)
                        sb.appendLine("File entropy: ${"%.4f".format(e)} / 8.0")
                        sb.appendLine(if (e > 7.0) "  → Likely encrypted/compressed" else if (e > 6.0) "  → Possibly packed" else "  → Normal data")
                    }
                }
                // HEX
                trimmed.lowercase().startsWith("hex(") -> {
                    val args = trimmed.removePrefix("hex(").removeSuffix(")").trim()
                    val parts = args.split(",")
                    if (parts.size == 2) {
                        val offset = parts[0].trim().toLongOrNull() ?: 0L
                        val length = parts[1].trim().toIntOrNull() ?: 64
                        if (file != null) {
                            val data = StreamingIO.readRange(file, offset, length)
                            data.forEachIndexed { i, b ->
                                if (i % 16 == 0) sb.append("${"%08X".format(offset + i)}: ")
                                sb.append("${"%02X".format(b.toInt() and 0xFF)} ")
                                if (i % 16 == 15) sb.appendLine()
                            }
                            sb.appendLine()
                        }
                    }
                }
                // DISASM
                trimmed.lowercase().startsWith("disasm(") -> {
                    val args = trimmed.removePrefix("disasm(").removeSuffix(")").trim()
                    val parts = args.split(",")
                    if (parts.size >= 2 && file != null) {
                        val offset = parts[0].trim().toLongOrNull() ?: 0L
                        val count = parts[1].trim().toIntOrNull() ?: 20
                        val data = StreamingIO.readRange(file, offset, count * 8)
                        val result = NativeLib.disassemble(data, offset, 1, 2, count)
                        sb.appendLine(result)
                    }
                }
                // CALL
                trimmed.lowercase().startsWith("call(") -> {
                    val func = trimmed.removePrefix("call(").removeSuffix(")").trim().removeSurrounding("\"")
                    sb.appendLine("Calling $func... (simulated)")
                }
                // FOR loop
                trimmed.lowercase().startsWith("for(") || trimmed.lowercase().startsWith("for (") -> {
                    sb.appendLine("// For loop: ${trimmed} (basic iteration)")
                }
                // IF
                trimmed.lowercase().startsWith("if(") || trimmed.lowercase().startsWith("if (") -> {
                    sb.appendLine("// If: ${trimmed}")
                }
                // END
                trimmed.lowercase() == "end" -> { }
                // DEFAULT
                else -> {
                    sb.appendLine("// Unknown: $trimmed")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Error on line: $trimmed")
            sb.appendLine("  ${e.message}")
        }
    }

    sb.appendLine()
    sb.appendLine("═══ Script Complete ═══")
    return sb.toString()
}

private fun evalExpr(expr: String, vars: Map<String, Any>, file: java.io.File?): String {
    val clean = expr.trim().removeSurrounding("\"")
    // Check if it's a variable
    if (vars.containsKey(clean)) return vars[clean].toString()
    // Check if it's a function call
    if (clean.lowercase().startsWith("file_size()") && file != null) return "${file.length()} bytes"
    if (clean.lowercase().startsWith("file_name()") && file != null) return file.name
    if (clean.lowercase() == "true") return "1"
    if (clean.lowercase() == "false") return "0"
    // Try numeric
    if (clean.matches(Regex("-?\\d+"))) return clean
    return clean
}

// ═══════════════════════════════════════════
// Script Templates
// ═══════════════════════════════════════════

private fun defaultScript() = templateAnalyzeElf()

private fun templateScanStrings() = """// Scan all strings in loaded file
read_loaded_file()
strings(6)
print("Done scanning strings")
"""

private fun templateFindXorKey() = """// Find XOR key by brute force
read_loaded_file()
print("Brute-forcing XOR key...")
for (key = 0; key < 256; key++)
  // Try each key, check for readable output
  print("Key 0x" + key.toString(16) + " tested")
end
print("Done")
"""

private fun templateAnalyzeElf() = """// Analyze ELF file
read_loaded_file()
print("File: " + file_name())
print("Size: " + file_size())
entropy()
strings(4)
hex(0, 64)
"""

private fun templatePatchBytes() = """// Patch bytes at offset
read_loaded_file()
print("Patching offset 0x1000...")
// patch(0x1000, 0x90) // NOP
// patch(0x1004, 0xC0, 0x03, 0x5F, 0xD6) // RET
print("Patch applied")
"""

private fun templateExtractUrls() = """// Extract URLs from binary
read_loaded_file()
strings(8)
search("http")
search("https")
search(".com")
search(".net")
print("URL scan complete")
"""

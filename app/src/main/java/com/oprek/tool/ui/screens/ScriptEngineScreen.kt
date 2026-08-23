package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEngineScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var script by remember { mutableStateOf(defaultScriptEngine()) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Script Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("output", output))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen) }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            // Templates
            Text("Templates", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))

            val templates = listOf(
                "Scan Strings" to "scan_strings()",
                "Find XOR Keys" to "find_xor()",
                "Analyze ELF" to "analyze_elf()",
                "Extract URLs" to "extract_urls()",
                "XOR Decrypt" to "xor_decrypt()"
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { (name, code) ->
                    OutlinedButton(
                        onClick = { script = code },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text(name, fontSize = 10.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Script editor
            Text("Script", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF00FF41)
                ),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
            )

            Spacer(Modifier.height(12.dp))

            // Run button
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        output = withContext(Dispatchers.IO) {
                            try {
                                runScriptEngine(script, context)
                            } catch (e: Exception) {
                                "Error: ${e.message}"
                            }
                        }
                        isRunning = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                enabled = !isRunning
            ) {
                if (isRunning) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Run Script")
            }

            Spacer(Modifier.height(12.dp))

            // Output
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Output", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        output.ifEmpty { "Output will appear here..." },
                        color = Color(0xFF00FF41),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Built-in functions reference
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Built-in Functions", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    val funcs = listOf(
                        "print(msg) — Print output",
                        "read_file(path) — Read file as string",
                        "read_bytes(path) — Read file as hex",
                        "strings(path) — Extract strings",
                        "search(path, pattern) — Search for pattern",
                        "hex_encode(str) — Encode to hex",
                        "hex_decode(hex) — Decode from hex",
                        "b64_encode(str) — Base64 encode",
                        "b64_decode(str) — Base64 decode",
                        "xor(str, key) — XOR encrypt/decrypt",
                        "rot13(str) — ROT13 cipher",
                        "entropy(data) — Calculate entropy",
                        "scan_strings(path) — Scan file strings",
                        "find_xor(path) — Brute-force XOR keys",
                        "analyze_elf(path) — Quick ELF analysis",
                        "extract_urls(data) — Find URLs",
                        "xor_decrypt(path) — Auto XOR decrypt"
                    )
                    funcs.forEach { Text("• $it", color = Color(0xFFAAAAAA), fontSize = 11.sp) }
                }
            }
        }
    }
}

private fun defaultScriptEngine(): String = """// OprekTool Script Engine v1.0
// Available: print(), read_file(), strings(), search(), hex_encode(), etc.
// Run a template or write your own script!

print("Hello from OprekTool Script Engine!")
print("Type scan_strings() to scan a file")
"""

private fun runScriptEngine(script: String, context: Context): String {
    val sb = StringBuilder()
    val outputLines = mutableListOf<String>()

    // Simple script interpreter
    val lines = script.lines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

        // print("...")
        if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
            val arg = trimmed.removePrefix("print(").removeSuffix(")").trim()
            outputLines.add(evalString(arg))
        }
        // Built-in function calls
        else if (trimmed.endsWith("()")) {
            val funcName = trimmed.removeSuffix("()")
            val result = callBuiltinFunc(funcName, context)
            outputLines.add(result)
        }
        // Variable assignment: var = func()
        else if (trimmed.contains("=")) {
            val parts = trimmed.split("=", limit = 2)
            val varName = parts[0].trim()
            val expr = parts[1].trim()
            if (expr.endsWith("()")) {
                val result = callBuiltinFunc(expr.removeSuffix("()"), context)
                outputLines.add("$varName = $result")
            }
        }
    }

    return outputLines.joinToString("\n").ifEmpty { "Script executed (no output)" }
}

private fun evalString(arg: String): String {
    // Handle string literals
    if ((arg.startsWith("\"") && arg.endsWith("\"")) || (arg.startsWith("'") && arg.endsWith("'"))) {
        return arg.substring(1, arg.length - 1)
    }
    return arg
}

private fun callBuiltinFunc(name: String, context: Context): String {
    return when (name) {
        "scan_strings" -> {
            val file = getLastOpenedFile(context)
            if (file != null) {
                val bytes = file.readBytes()
                val strings = mutableListOf<String>()
                val sb = StringBuilder()
                for (b in bytes) {
                    val c = b.toInt() and 0xFF
                    if (c in 0x20..0x7E) sb.append(c.toChar())
                    else {
                        if (sb.length >= 4) strings.add(sb.toString())
                        sb.clear()
                    }
                }
                "Found ${strings.size} strings:\n${strings.take(50).joinToString("\n")}"
            } else "No file loaded. Open a file first."
        }
        "find_xor" -> "XOR brute-force: Open a file and use find_xor() in Terminal"
        "analyze_elf" -> {
            val file = getLastOpenedFile(context)
            if (file != null) {
                val bytes = file.readBytes()
                if (bytes.size >= 16 && bytes[0] == 0x7F.toByte() && bytes[1] == 0x45.toByte()) {
                    val is64 = bytes[4] == 2.toByte()
                    "ELF ${if (is64) "64" else "32"}-bit binary\nSize: ${file.length()} bytes"
                } else "Not an ELF file"
            } else "No file loaded"
        }
        "extract_urls" -> {
            val file = getLastOpenedFile(context)
            if (file != null) {
                val text = String(file.readBytes())
                val urls = Regex("https?://[^\\s\"']+").findAll(text).map { it.value }.distinct().toList()
                "Found ${urls.size} URLs:\n${urls.joinToString("\n")}"
            } else "No file loaded"
        }
        "xor_decrypt" -> "Use the Encrypt/Decrypt screen for XOR auto-decrypt"
        else -> "Unknown function: $name"
    }
}

private fun getLastOpenedFile(context: Context): java.io.File? {
    // Check sdcard for any file
    val outputDir = java.io.File("/sdcard/OprekTool")
    if (outputDir.exists()) {
        val files = outputDir.listFiles()
        if (files != null && files.isNotEmpty()) return files.last()
    }
    return null
}

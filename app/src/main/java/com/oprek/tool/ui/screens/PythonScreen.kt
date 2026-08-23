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
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PythonScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var script by remember { mutableStateOf(pythonTemplate()) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐍 Python Script", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
            // Templates
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Python Templates", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val templates = listOf(
                            "Deobfuscate" to pyDeobfuscate(),
                            "XOR Decrypt" to pyXorDecrypt(),
                            "AES Decrypt" to pyAesDecrypt(),
                            "Base64 Decode" to pyBase64(),
                            "ROT13" to pyRot13(),
                            "Scan Binary" to pyScanBinary(),
                            "Extract Strings" to pyExtractStrings(),
                            "Patch Bytes" to pyPatchBytes()
                        )
                        templates.forEach { (name, code) ->
                            FilterChip(selected = false, onClick = { script = code },
                                label = { Text(name, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                        }
                    }
                }
            }

            // Script editor
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                label = { Text("Python Script") },
                colors = darkTextFieldColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AccentGreen)
            )

            // Run button
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    isRunning = true
                    scope.launch(Dispatchers.Default) {
                        val result = withContext(Dispatchers.IO) { executePython(script, context) }
                        output = result
                        isRunning = false
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp), enabled = !isRunning) {
                    if (isRunning) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Run", fontWeight = FontWeight.Bold) }
                }
                Button(onClick = { script = pythonTemplate() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset")
                }
            }

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
            OutputButton(content = { output }, filename = "python_output.txt", subfolder = "python")
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═══════════════════════════════════════════
// Python Interpreter (simplified)
// ═══════════════════════════════════════════

private fun executePython(script: String, context: Context): String {
    val sb = StringBuilder()
    val vars = mutableMapOf<String, String>()
    val file = SharedFileState.findFile(context)

    sb.appendLine("═══ Python Output ═══")
    sb.appendLine()

    for (line in script.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

        try {
            when {
                // print()
                trimmed.startsWith("print(") -> {
                    val expr = trimmed.removePrefix("print(").removeSuffix(")")
                    sb.appendLine(evalPyExpr(expr, vars, file))
                }
                // Variable assignment
                trimmed.contains(" = ") && !trimmed.startsWith("if") && !trimmed.startsWith("for") -> {
                    val parts = trimmed.split(" = ", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim().removePrefix("var ").trim()
                        vars[name] = evalPyExpr(parts[1].trim(), vars, file)
                    }
                }
                // def function
                trimmed.startsWith("def ") -> {
                    sb.appendLine("// Function: ${trimmed.removePrefix("def ").removeSuffix(":")}")
                }
                // if/elif/else
                trimmed.startsWith("if ") || trimmed.startsWith("elif ") || trimmed == "else:" -> {
                    sb.appendLine("// ${trimmed}")
                }
                // for loop
                trimmed.startsWith("for ") -> {
                    sb.appendLine("// Loop: ${trimmed}")
                }
                // import
                trimmed.startsWith("import ") -> {
                    sb.appendLine("// Import: ${trimmed.removePrefix("import ")}")
                }
                // with open
                trimmed.startsWith("with open(") -> {
                    sb.appendLine("// File operation: ${trimmed}")
                }
                // return
                trimmed.startsWith("return ") -> {
                    val expr = trimmed.removePrefix("return ")
                    sb.appendLine("return: ${evalPyExpr(expr, vars, file)}")
                }
                // pass/break/continue
                trimmed == "pass" || trimmed == "break" || trimmed == "continue" -> { }
                // End of block
                trimmed == "end" -> { }
                // Default
                else -> {
                    sb.appendLine("// ${trimmed}")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
        }
    }

    sb.appendLine()
    sb.appendLine("═══ Done ═══")
    return sb.toString()
}

private fun evalPyExpr(expr: String, vars: Map<String, String>, file: java.io.File?): String {
    val clean = expr.trim().removeSurrounding("\"").removeSurrounding("'")

    // Built-in functions
    return when {
        clean == "file_size()" && file != null -> "${file.length()}"
        clean == "file_name()" && file != null -> file.name
        clean.startsWith("len(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("len(").removeSuffix(")")
            val v = vars[inner]
            if (v != null) "${v.length}" else "0"
        }
        clean.startsWith("ord(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("ord(").removeSuffix(")").trim().removeSurrounding("\"")
            "${inner.firstOrNull()?.code ?: 0}"
        }
        clean.startsWith("chr(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("chr(").removeSuffix(")").trim().toIntOrNull() ?: 0
            "${inner.toChar()}"
        }
        clean.startsWith("hex(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("hex(").removeSuffix(")").trim().toIntOrNull() ?: 0
            "0x${inner.toString(16)}"
        }
        clean.startsWith("int(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("int(").removeSuffix(")").trim()
            vars[inner] ?: inner
        }
        clean == "True" -> "1"
        clean == "False" -> "0"
        clean == "None" -> "null"
        // Variable lookup
        vars.containsKey(clean) -> vars[clean]!!
        // Numeric
        clean.matches(Regex("-?\\d+")) -> clean
        // String
        else -> clean
    }
}

// ═══════════════════════════════════════════
// Python Templates
// ═══════════════════════════════════════════

private fun pythonTemplate() = pyDeobfuscate()

private fun pyDeobfuscate() = """# Deobfuscate encrypted strings
# Usage: Provide hex-encoded or base64-encoded strings

encrypted = "48656c6c6f"  # hex encoded
decoded = ""
for i in range(0, len(encrypted), 2):
    byte = int(encrypted[i:i+2], 16)
    decoded = decoded + chr(byte)
print("Decoded: " + decoded)

# Base64 example
import base64
b64 = "SGVsbG8gV29ybGQ="
result = base64.b64decode(b64)
print("Base64: " + result)
"""

private fun pyXorDecrypt() = """# XOR decrypt with key
data = "4a5b6c7d8e"  # hex encoded ciphertext
key = 0x42

result = ""
for i in range(0, len(data), 2):
    byte = int(data[i:i+2], 16)
    decrypted = byte ^ key
    result = result + chr(decrypted)
print("XOR decrypted: " + result)
print("Key: 0x" + hex(key))
"""

private fun pyAesDecrypt() = """# AES decrypt example
# Note: This is a simplified version
# For real AES, use PyCryptodome

encrypted = "U2FsdGVkX1+vupppZksvRf5pq5g5XjFRIipRkwB0K1Y="
key = "0123456789abcdef"
print("AES encrypted: " + encrypted)
print("Key: " + key)
print("Note: Install pycryptodome for real AES decryption")
"""

private fun pyBase64() = """# Base64 encode/decode
import base64

# Decode
encoded = "SGVsbG8gV29ybGQh"
decoded = base64.b64decode(encoded)
print("Decoded: " + decoded)

# Encode
text = "Hello World!"
encoded = base64.b64encode(text.encode())
print("Encoded: " + encoded)
"""

private fun pyRot13() = """# ROT13 cipher
def rot13(text):
    result = ""
    for c in text:
        if 'a' <= c <= 'z':
            result += chr((ord(c) - ord('a') + 13) % 26 + ord('a'))
        elif 'A' <= c <= 'Z':
            result += chr((ord(c) - ord('A') + 13) % 26 + ord('A'))
        else:
            result += c
    return result

encrypted = "Uryyb Jbeyq!"
decrypted = rot13(encrypted)
print("Encrypted: " + encrypted)
print("Decrypted: " + decrypted)
"""

private fun pyScanBinary() = """# Scan binary file for patterns
import os

# Load file
filename = "target.bin"
if os.path.exists(filename):
    with open(filename, "rb") as f:
        data = f.read()
    print("File size: " + str(len(data)) + " bytes")
    
    # Search for ELF header
    if data[:4] == b'\x7fELF':
        print("Type: ELF binary")
    elif data[:2] == b'MZ':
        print("Type: PE/EXE")
    elif data[:4] == b'PK\x03\x04':
        print("Type: ZIP/APK")
    
    # Find strings
    strings = []
    current = ""
    for b in data:
        if 32 <= b < 127:
            current += chr(b)
        else:
            if len(current) >= 4:
                strings.append(current)
            current = ""
    print("Found " + str(len(strings)) + " strings")
else:
    print("File not found: " + filename)
"""

private fun pyExtractStrings() = """# Extract printable strings from binary
import os

filename = "target.bin"
min_length = 6

if os.path.exists(filename):
    with open(filename, "rb") as f:
        data = f.read()
    
    strings = []
    current = ""
    offset = 0
    start = 0
    
    for i, b in enumerate(data):
        if 32 <= b < 127:
            if not current:
                start = i
            current += chr(b)
        else:
            if len(current) >= min_length:
                strings.append((start, current))
            current = ""
    
    print("Found " + str(len(strings)) + " strings (min " + str(min_length) + " chars)")
    for offset, s in strings[:50]:
        print("  0x{:08x}: {}".format(offset, s))
else:
    print("File not found")
"""

private fun pyPatchBytes() = """# Patch bytes in binary file
import os

filename = "target.bin"
offset = 0x1000
new_bytes = bytes([0x90, 0x90, 0x90, 0x90])  # NOP sled

if os.path.exists(filename):
    with open(filename, "rb") as f:
        data = bytearray(f.read())
    
    print("Original: " + " ".join("{:02x}".format(b) for b in data[offset:offset+4]))
    
    # Patch
    for i, b in enumerate(new_bytes):
        data[offset + i] = b
    
    print("Patched:  " + " ".join("{:02x}".format(b) for b in data[offset:offset+4]))
    
    # Save
    output = filename + ".patched"
    with open(output, "wb") as f:
        f.write(data)
    print("Saved to: " + output)
else:
    print("File not found")
"""

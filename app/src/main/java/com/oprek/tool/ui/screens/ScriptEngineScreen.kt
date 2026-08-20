package com.oprek.tool.ui.screens

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
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEngineScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var script by remember { mutableStateOf(defaultScript()) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Script Engine", fontWeight = FontWeight.Bold) },
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
            // Templates
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚡ Script Templates", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val templates = listOf(
                            "Deobfuscate Base64" to tDeob64(),
                            "XOR Decrypt" to tXorDec(),
                            "ROT13" to tRot13(),
                            "Scan Strings" to tScanStr(),
                            "Find XOR Key" to tFindXor(),
                            "Analyze ELF" to tAnalyze(),
                            "Extract URLs" to tUrls(),
                            "Patch Bytes" to tPatch(),
                            "Hash Calculator" to tHash(),
                            "Entropy Check" to tEntropy(),
                            "AES Decrypt" to tAes(),
                            "Vigenère" to tVigenere()
                        )
                        templates.forEach { (name, code) ->
                            FilterChip(selected = false, onClick = { script = code },
                                label = { Text(name, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                        }
                    }
                }
            }

            // Editor
            OutlinedTextField(
                value = script, onValueChange = { script = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                label = { Text("JavaScript / Analysis Script") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, cursorColor = AccentGreen),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AccentGreen)
            )

            // Run
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    isRunning = true
                    scope.launch(Dispatchers.Default) {
                        val result = withContext(Dispatchers.IO) { executeJS(script, context) }
                        output = result
                        isRunning = false
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp), enabled = !isRunning) {
                    if (isRunning) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Run Script", fontWeight = FontWeight.Bold) }
                }
                Button(onClick = { script = defaultScript() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)) { Text("Reset") }
            }

            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Spacer(Modifier.height(8.dp))
                        Text(output, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutputButton(content = { output }, filename = "script_output.txt", subfolder = "scripts")
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═══════════════════════════════════════════
// Real JavaScript Execution Engine
// ═══════════════════════════════════════════

private fun executeJS(script: String, context: Context): String {
    val sb = StringBuilder()
    sb.appendLine("═══ Script Output ═══")
    sb.appendLine()

    try {
        val mgr = ScriptEngineManager()
        val engine = mgr.getEngineByName("rhino") ?: mgr.getEngineByName("nashorn") ?: mgr.getEngineByName("js")

        if (engine == null) {
            // Fallback: manual execution
            return executeManual(script, context)
        }

        val bindings = SimpleBindings()
        val output = StringBuilder()

        // Inject helper functions
        engine.eval("""
            var output = [];
            function print(msg) { output.push(String(msg)); }
            function hex(n) { return "0x" + (n >>> 0).toString(16); }
            function ord(c) { return c.charCodeAt(0); }
            function chr(n) { return String.fromCharCode(n); }
            function btoa(s) { return java.util.Base64.getEncoder().encodeToString(s.getBytes()); }
            function atob(s) { return new String(java.util.Base64.getDecoder().decode(s)); }
            function strlen(s) { return s.length; }
            function substr(s, i, n) { return s.substring(i, i + n); }
            function indexOf(s, sub) { return s.indexOf(sub); }
            function toHex(s) { var h = ""; for (var i = 0; i < s.length; i++) h += ("0" + s.charCodeAt(i).toString(16)).slice(-2); return h; }
            function fromHex(h) { var s = ""; for (var i = 0; i < h.length; i += 2) s += String.fromCharCode(parseInt(h.substr(i, 2), 16)); return s; }
            function xorDec(data, key) { var r = ""; for (var i = 0; i < data.length; i += 2) { var b = parseInt(data.substr(i, 2), 16); r += String.fromCharCode(b ^ key); } return r; }
            function rot13(s) { return s.replace(/[a-zA-Z]/g, function(c) { return String.fromCharCode((c <= 'Z' ? 90 : 122) >= (c = c.charCodeAt(0) + 13) ? c : c - 26); }); }
            function rot47(s) { return s.replace(/[!-~]/g, function(c) { return String.fromCharCode(((c.charCodeAt(0) - 33 + 47) % 94) + 33); }); }
            function vigenereDec(s, key) { var r = ""; var ki = 0; for (var i = 0; i < s.length; i++) { var c = s.charCodeAt(i); if (c >= 65 && c <= 90) { r += String.fromCharCode(((c - 65 - (key.charCodeAt(ki % key.length) - 65) + 26) % 26) + 65); ki++; } else if (c >= 97 && c <= 122) { r += String.fromCharCode(((c - 97 - (key.charCodeAt(ki % key.length).toLowerCase().charCodeAt(0) - 97) + 26) % 26) + 97); ki++; } else { r += s[i]; } } return r; }
        """)

        engine.put("output", output)
        engine.eval(script)

        val result = output.toString()
        if (result.isNotEmpty()) sb.appendLine(result)
        else sb.appendLine("// Script executed (no output)")

    } catch (e: Exception) {
        sb.appendLine("Error: ${e.message}")
        sb.appendLine("// Falling back to manual execution...")
        sb.appendLine(executeManual(script, context))
    }

    sb.appendLine()
    sb.appendLine("═══ Done ═══")
    return sb.toString()
}

private fun executeManual(script: String, context: Context): String {
    val sb = StringBuilder()
    val vars = mutableMapOf<String, String>()
    val file = context.cacheDir.listFiles()?.firstOrNull()

    for (line in script.lines()) {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*")) continue

        try {
            when {
                t.startsWith("print(") -> {
                    val expr = t.removePrefix("print(").removeSuffix(")")
                    sb.appendLine(evalManual(expr, vars, file))
                }
                t.contains(" = ") && !t.startsWith("if") && !t.startsWith("for") && !t.startsWith("function") -> {
                    val parts = t.split(" = ", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim().removePrefix("var ").removePrefix("let ").removePrefix("const ").trim()
                        vars[name] = evalManual(parts[1].trim(), vars, file)
                    }
                }
                t.startsWith("function ") -> { }
                t.startsWith("if ") || t.startsWith("} else") -> { }
                t.startsWith("for ") -> { }
                t == "}" || t == "{" -> { }
                else -> sb.appendLine("// $t")
            }
        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
        }
    }
    return sb.toString()
}

private fun evalManual(expr: String, vars: Map<String, String>, file: java.io.File?): String {
    val clean = expr.trim().removeSurrounding("\"").removeSurrounding("'")

    return when {
        clean == "file_size()" && file != null -> "${file.length()}"
        clean == "file_name()" && file != null -> file.name
        clean.startsWith("hex(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("hex(").removeSuffix(")").trim().toIntOrNull() ?: 0
            "0x${inner.toString(16)}"
        }
        clean.startsWith("ord(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("ord(").removeSuffix(")").trim().removeSurrounding("\"")
            "${inner.firstOrNull()?.code ?: 0}"
        }
        clean.startsWith("chr(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("chr(").removeSuffix(")").trim().toIntOrNull() ?: 0
            "${inner.toChar()}"
        }
        clean.startsWith("strlen(") && clean.endsWith(")") -> {
            val inner = clean.removePrefix("strlen(").removeSuffix(")").trim()
            val v = vars[inner] ?: inner.removeSurrounding("\"")
            "${v.length}"
        }
        clean == "true" -> "1"; clean == "false" -> "0"; clean == "null" -> "null"
        vars.containsKey(clean) -> vars[clean]!!
        clean.matches(Regex("-?\\d+")) -> clean
        else -> clean
    }
}

// ═══════════════════════════════════════════
// Templates
// ═══════════════════════════════════════════

private fun defaultScript() = tAnalyze()

private fun tDeob64() = """// Deobfuscate Base64 encoded strings
var encoded = "SGVsbG8gV29ybGQhIFRoaXMgaXMgYSB0ZXN0IHN0cmluZw==";
var decoded = atob(encoded);
print("Encoded: " + encoded);
print("Decoded: " + decoded);
print("Length: " + strlen(decoded));
"""

private fun tXorDec() = """// XOR decrypt with brute force
var data = "4a5b6c7d8e9f";
print("XOR brute force on: " + data);
for (var key = 0; key < 256; key++) {
    var result = xorDec(data, key);
    // Check if result is printable
    var printable = true;
    for (var i = 0; i < result.length; i++) {
        var c = result.charCodeAt(i);
        if (c < 32 || c > 126) { printable = false; break; }
    }
    if (printable && result.length > 2) {
        print("Key 0x" + hex(key) + ": " + result);
    }
}
"""

private fun tRot13() = """// ROT13 / ROT47 decoder
var text = "Uryyb Jbeyq! Guvf vf n grfg";
print("Original: " + text);
print("ROT13: " + rot13(text));
print("ROT47: " + rot47(text));

// Also try reverse
var reversed = text.split("").reverse().join("");
print("Reversed: " + reversed);
"""

private fun tScanStr() = """// Scan for interesting strings
var patterns = ["password", "secret", "key", "token", "license", 
                "admin", "root", "debug", "http", "https",
                "base64", "encrypt", "decrypt", "cipher"];
print("Scanning for sensitive patterns...");
for (var i = 0; i < patterns.length; i++) {
    print("Pattern: " + patterns[i]);
}
print("Use strings() in terminal for full scan");
"""

private fun tFindXor() = """// Find XOR key by frequency analysis
var data = "1a2b3c4d5e6f7a8b9c0d";
print("Analyzing XOR-encrypted data...");
print("Data length: " + (data.length / 2) + " bytes");

// Simple frequency analysis
var freq = {};
for (var i = 0; i < data.length; i += 2) {
    var byte = parseInt(data.substr(i, 2), 16);
    freq[byte] = (freq[byte] || 0) + 1;
}
print("Byte frequency:");
for (var k in freq) {
    print("  0x" + hex(parseInt(k)) + ": " + freq[k] + " times");
}
"""

private fun tAnalyze() = """// Analyze binary file
print("=== Binary Analysis ===");
print("File: " + file_name());
print("Size: " + file_size() + " bytes");
print("");

// Check file type
var name = file_name();
if (name.endsWith(".so") || name.endsWith(".elf")) {
    print("Type: ELF binary (shared object)");
} else if (name.endsWith(".apk")) {
    print("Type: Android APK");
} else if (name.endsWith(".dex")) {
    print("Type: DEX (Dalvik Executable)");
} else if (name.endsWith(".sh")) {
    print("Type: Shell script");
} else if (name.endsWith(".py")) {
    print("Type: Python script");
} else {
    print("Type: Unknown");
}
"""

private fun tUrls() = """// Extract URLs and domains from strings
var urls = ["http://", "https://", "ftp://", "file://"];
var domains = [".com", ".net", ".org", ".io", ".dev", ".xyz"];
print("URL patterns to search:");
for (var i = 0; i < urls.length; i++) print("  " + urls[i]);
print("Domain patterns:");
for (var i = 0; i < domains.length; i++) print("  " + domains[i]);
print("Run: strings() in terminal for full extraction");
"""

private fun tPatch() = """// Binary patching helper
var offset = 0x1000;
var original = [0x90, 0x90, 0x90, 0x90];
var patched = [0x00, 0x00, 0x00, 0x00];
print("Patch plan:");
print("Offset: 0x" + hex(offset));
print("Original: " + original.map(function(b) { return hex(b); }).join(" "));
print("Patched:  " + patched.map(function(b) { return hex(b); }).join(" "));
print("Use patch_bytes() function to apply");
"""

private fun tHash() = """// Hash calculation
var text = "Hello World";
print("Text: " + text);
print("Length: " + text.length + " bytes");
print("Hex: " + toHex(text));
print("Base64: " + btoa(text));
// Simple hash (not crypto-grade)
var hash = 0;
for (var i = 0; i < text.length; i++) {
    hash = ((hash << 5) - hash) + text.charCodeAt(i);
    hash = hash & hash;
}
print("Simple hash: " + hex(hash));
"""

private fun tEntropy() = """// Entropy calculation
var text = "Hello World! This is a test string with some entropy.";
print("Text: " + text);
print("Length: " + text.length);

// Calculate Shannon entropy
var freq = {};
for (var i = 0; i < text.length; i++) {
    var c = text.charAt(i);
    freq[c] = (freq[c] || 0) + 1;
}
var entropy = 0;
for (var c in freq) {
    var p = freq[c] / text.length;
    entropy -= p * Math.log(p) / Math.log(2);
}
print("Entropy: " + entropy.toFixed(4) + " bits/char");
print("Max possible: " + Math.log(text.length) / Math.log(2)).toFixed(4) + " bits/char");
"""

private fun tAes() = """// AES encryption/decryption helper
print("=== AES Encryption ===");
print("Note: This uses Java's built-in AES");
print("For real AES, use the Encrypt/Decrypt tools");
print("");
print("Common AES modes:");
print("  AES/ECB/PKCS5Padding");
print("  AES/CBC/PKCS5Padding");
print("  AES/GCM/NoPadding");
print("");
print("Use the Encrypt Tool for actual AES operations");
"""

private fun tVigenere() = """// Vigenère cipher decoder
var key = "SECRET";
var encrypted = "ZINCSMFNCI";
print("Vigenère Decryption");
print("Key: " + key);
print("Encrypted: " + encrypted);
print("Decrypted: " + vigenereDec(encrypted, key));
print("");
// Try common keys
var commonKeys = ["KEY", "SECRET", "PASSWORD", "ADMIN", "TEST"];
print("Trying common keys:");
for (var i = 0; i < commonKeys.length; i++) {
    print("  " + commonKeys[i] + ": " + vigenereDec(encrypted, commonKeys[i]));
}
"""

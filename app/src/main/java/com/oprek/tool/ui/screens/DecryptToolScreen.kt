package com.oprek.tool.ui.screens

import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecryptToolScreen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableIntStateOf(-1) } // -1 = auto-detect
    var key by remember { mutableStateOf("") }
    var caesarShift by remember { mutableStateOf("3") }
    var autoDetectResult by remember { mutableStateOf("") }

    val methods = listOf(
        "Auto-Detect", "XOR Single-Key", "XOR Multi-Key", "AES-128/256", "DES",
        "Base64", "ROT13", "ROT47", "Vigenère", "RC4", "Caesar"
    )
    val methodIcons = listOf("🔍", "🔑", "🔐", "🛡️", "🔒", "📝", "🔄", "🔃", "🗝️", "🎲", "🔢")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔓 Decrypt Tool", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // Method selector
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Decryption Method", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentRed)
                    Spacer(Modifier.height(8.dp))
                    for (row in 0 until 6) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 2) {
                                val idx = row * 2 + col
                                if (idx < methods.size) {
                                    FilterChip(
                                        selected = selectedMethod == idx,
                                        onClick = { selectedMethod = idx },
                                        label = { Text("${methodIcons[idx]} ${methods[idx]}", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Key input
            if (selectedMethod in listOf(1, 2, 3, 4, 7, 8, 9)) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Key / Parameter", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                        Spacer(Modifier.height(8.dp))
                        when (selectedMethod) {
                            2 -> {
                                OutlinedTextField(value = key, onValueChange = { key = it },
                                    label = { Text("Multi-byte key (hex: 4A6F686E)") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = darkTextFieldColors())
                            }
                            10 -> {
                                OutlinedTextField(value = caesarShift, onValueChange = { caesarShift = it },
                                    label = { Text("Shift (1-25)") }, modifier = Modifier.width(120.dp), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = darkTextFieldColors())
                            }
                            else -> {
                                OutlinedTextField(value = key, onValueChange = { key = it },
                                    label = { Text("Decryption key") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = darkTextFieldColors())
                                if (selectedMethod == 3) Text("AES: 16 or 32 bytes key", fontSize = 10.sp, color = TextMuted)
                                if (selectedMethod == 4) Text("DES: 8 bytes key", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // Input
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Ciphertext Input", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        label = { Text("Enter text/hex to decrypt...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = darkTextFieldColors())
                }
            }

            // Decrypt button
            Button(onClick = {
                if (selectedMethod == 0) {
                    // Auto-detect
                    autoDetectResult = autoDetectAndDecrypt(input)
                    output = autoDetectResult
                } else {
                    output = try {
                        decryptData(input, selectedMethod - 1, key, caesarShift.toIntOrNull() ?: 3)
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp), enabled = input.isNotEmpty()) {
                Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selectedMethod == 0) "Auto-Detect & Decrypt" else "Decrypt", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Auto-detect results
            if (selectedMethod == 0 && autoDetectResult.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔍 Auto-Detect Results", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                        Spacer(Modifier.height(8.dp))
                        Text(autoDetectResult, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            // Output
            if (output.isNotEmpty() && selectedMethod != 0) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Decrypted Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("decrypted", output))
                            }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(output, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutputButton(content = { output }, filename = "decrypted.txt", subfolder = "decrypt")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun decryptData(input: String, method: Int, key: String, shift: Int): String {
    if (input.isEmpty()) return ""
    return when (method) {
        0 -> { // XOR Single-Key
            val k = if (key.isNotEmpty()) key.first().code.toByte() else 0x42
            val bytes = hexToBytes(input)
            String(bytes.map { (it.toInt() xor k.toInt()) and 0xFF }.map { it.toByte() }.toByteArray())
        }
        1 -> { // XOR Multi-Key
            val keyBytes = hexToBytes(key.ifEmpty { "4A6F686E" })
            val bytes = hexToBytes(input)
            String(bytes.mapIndexed { i, b -> (b.toInt() xor keyBytes[i % keyBytes.size].toInt()) and 0xFF }.map { it.toByte() }.toByteArray())
        }
        2 -> { // AES
            val keyBytes = padKey(key.ifEmpty { "0000000000000000" }, 16)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            String(cipher.doFinal(Base64.decode(input, Base64.NO_WRAP)))
        }
        3 -> { // DES
            val keyBytes = padKey(key.ifEmpty { "00000000" }, 8)
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
            String(cipher.doFinal(Base64.decode(input, Base64.NO_WRAP)))
        }
        4 -> String(Base64.decode(input, Base64.NO_WRAP)) // Base64
        5 -> input.map { c -> // ROT13
            when {
                c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                else -> c
            }
        }.joinToString("")
        6 -> input.map { c -> // ROT47
            if (c in '!'..'~') (((c.code - 33 + 47) % 94) + 33).toChar() else c
        }.joinToString("")
        7 -> { // Vigenère (decrypt = reverse shift)
            val k = key.ifEmpty { "KEY" }.uppercase()
            input.mapIndexed { i, c ->
                val shift = k[i % k.length].code - 'A'.code
                when {
                    c in 'A'..'Z' -> 'A' + (c - 'A' - shift + 26) % 26
                    c in 'a'..'z' -> 'a' + (c - 'a' - shift + 26) % 26
                    else -> c
                }
            }.joinToString("")
        }
        8 -> { // RC4
            val keyBytes = if (key.isNotEmpty()) key.toByteArray() else "key".toByteArray()
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) { j = (j + s[i] + (keyBytes[i % keyBytes.size].toInt() and 0xFF)) and 0xFF; val t = s[i]; s[i] = s[j]; s[j] = t }
            var x = 0; var y = 0
            val bytes = hexToBytes(input)
            val result = mutableListOf<Int>()
            for (b in bytes) { x = (x + 1) and 0xFF; y = (y + s[x]) and 0xFF; val t = s[x]; s[x] = s[y]; s[y] = t; result.add((b.toInt() xor s[(s[x] + s[y]) and 0xFF]) and 0xFF) }
            String(result.map { it.toByte() }.toByteArray())
        }
        9 -> { // Caesar
            val s = shift.coerceIn(1, 25)
            input.map { c ->
                when {
                    c in 'A'..'Z' -> 'A' + (c - 'A' - s + 26) % 26
                    c in 'a'..'z' -> 'a' + (c - 'a' - s + 26) % 26
                    else -> c
                }
            }.joinToString("")
        }
        else -> input
    }
}

private fun autoDetectAndDecrypt(input: String): String {
    val results = mutableListOf<String>()
    results.add("═══════════════════════════════════════")
    results.add("  🔍 AUTO-DETECT DECRYPTION RESULTS")
    results.add("═══════════════════════════════════════")
    results.add("")

    // 1. Try Base64
    try {
        val decoded = Base64.decode(input, Base64.NO_WRAP)
        val text = String(decoded)
        if (text.all { it.code in 0x20..0x7E || it.code in 9..13 } && text.isNotEmpty()) {
            results.add("✅ Base64 DECODED:")
            results.add("   $text")
            results.add("")
        }
    } catch (_: Exception) {}

    // 2. Try ROT13
    val rot13 = input.map { c ->
        when {
            c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
            c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
            else -> c
        }
    }.joinToString("")
    if (rot13 != input) {
        val score = rot13.count { it.isLetter() }.toFloat() / rot13.length.coerceAtLeast(1)
        if (score > 0.5f) {
            results.add("✅ ROT13:")
            results.add("   $rot13")
            results.add("")
        }
    }

    // 3. Try ROT47
    val rot47 = input.map { c ->
        if (c in '!'..'~') (((c.code - 33 + 47) % 94) + 33).toChar() else c
    }.joinToString("")
    if (rot47 != input) {
        results.add("✅ ROT47:")
        results.add("   $rot47")
        results.add("")
    }

    // 4. Try Caesar shifts 1-25
    var bestCaesar = ""
    var bestCaesarShift = 0
    var bestScore = 0f
    for (s in 1..25) {
        val shifted = input.map { c ->
            when {
                c in 'A'..'Z' -> 'A' + (c - 'A' - s + 26) % 26
                c in 'a'..'z' -> 'a' + (c - 'a' - s + 26) % 26
                else -> c
            }
        }.joinToString("")
        val score = shifted.count { it.isLetter() }.toFloat() / shifted.length.coerceAtLeast(1)
        if (score > bestScore) { bestScore = score; bestCaesar = shifted; bestCaesarShift = s }
    }
    if (bestScore > 0.5f) {
        results.add("✅ Caesar (shift=$bestCaesarShift):")
        results.add("   $bestCaesar")
        results.add("")
    }

    // 5. Try XOR brute force (0x00-0xFF)
    val bytes = try { hexToBytes(input) } catch (_: Exception) { input.toByteArray() }
    var bestXorKey = 0
    var bestXorScore = 0f
    var bestXorResult = ""
    for (k in 0..255) {
        val decoded = String(bytes.map { (it.toInt() xor k) and 0xFF }.map { it.toByte() }.toByteArray())
        val score = decoded.count { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }.toFloat() / decoded.length.coerceAtLeast(1)
        if (score > bestXorScore) { bestXorScore = score; bestXorKey = k; bestXorResult = decoded }
    }
    if (bestXorScore > 0.4f) {
        results.add("✅ XOR (key=0x${"%02X".format(bestXorKey)}):")
        results.add("   $bestXorResult")
        results.add("")
    }

    // 6. Try Vigenère with common keys
    val commonKeys = listOf("KEY", "SECRET", "PASSWORD", "ADMIN", "TEST", "HELLO", "WORLD", "PASS")
    for (vk in commonKeys) {
        val decoded = input.mapIndexed { i, c ->
            val shift = vk[i % vk.length].code - 'A'.code
            when {
                c in 'A'..'Z' -> 'A' + (c - 'A' - shift + 26) % 26
                c in 'a'..'z' -> 'a' + (c - 'a' - shift + 26) % 26
                else -> c
            }
        }.joinToString("")
        val score = decoded.count { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }.toFloat() / decoded.length.coerceAtLeast(1)
        if (score > 0.6f) {
            results.add("✅ Vigenère (key=$vk):")
            results.add("   $decoded")
            results.add("")
            break
        }
    }

    // 7. Try RC4 with common keys
    for (rk in listOf("key", "admin", "secret", "password")) {
        try {
            val keyBytes = rk.toByteArray()
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) { j = (j + s[i] + keyBytes[i % keyBytes.size].toInt()) and 0xFF; val t = s[i]; s[i] = s[j]; s[j] = t }
            var x = 0; var y = 0
            val result = mutableListOf<Int>()
            for (b in bytes) { x = (x + 1) and 0xFF; y = (y + s[x]) and 0xFF; val t = s[x]; s[x] = s[y]; s[y] = t; result.add((b.toInt() xor s[(s[x] + s[y]) and 0xFF]) and 0xFF) }
            val decoded = String(result.map { it.toByte() }.toByteArray())
            val score = decoded.count { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }.toFloat() / decoded.length.coerceAtLeast(1)
            if (score > 0.6f) {
                results.add("✅ RC4 (key=$rk):")
                results.add("   $decoded")
                results.add("")
                break
            }
        } catch (_: Exception) {}
    }

    // 8. Try Hex decode
    try {
        if (input.matches(Regex("^[0-9A-Fa-f]+$")) && input.length % 2 == 0) {
            val decoded = String(hexToBytes(input))
            if (decoded.any { it.isLetter() }) {
                results.add("✅ Hex Decode:")
                results.add("   $decoded")
                results.add("")
            }
        }
    } catch (_: Exception) {}

    if (results.size <= 3) {
        results.add("❌ No decryption method matched with confidence.")
        results.add("   Try manual method selection above.")
    }

    results.add("═══════════════════════════════════════")
    return results.joinToString("\n")
}

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace(" ", "")
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun padKey(key: String, size: Int): ByteArray {
    val bytes = key.toByteArray()
    return if (bytes.size >= size) bytes.copyOf(size) else bytes + ByteArray(size - bytes.size)
}

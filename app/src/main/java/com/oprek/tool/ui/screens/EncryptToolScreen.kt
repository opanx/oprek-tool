package com.oprek.tool.ui.screens

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
import com.oprek.tool.core.OutputManager
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptToolScreen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf("") }
    var caesarShift by remember { mutableStateOf("3") }

    val methods = listOf(
        "XOR Single-Key", "XOR Multi-Key", "AES-128/256", "DES",
        "Base64", "ROT13", "ROT47", "Vigenère", "RC4", "Caesar"
    )
    val methodIcons = listOf("🔑", "🔐", "🛡️", "🔒", "📝", "🔄", "🔃", "🗝️", "🎲", "🔢")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔒 Encrypt Tool", fontWeight = FontWeight.Bold) },
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
                    Text("Encryption Method", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    // 2x5 grid
                    for (row in 0 until 5) {
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
                                            selectedContainerColor = AccentGreen.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Key input (for methods that need it)
            if (selectedMethod in listOf(0, 1, 2, 3, 7, 8, 9)) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Key / Parameter", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                        Spacer(Modifier.height(8.dp))
                        when (selectedMethod) {
                            1 -> {
                                OutlinedTextField(value = key, onValueChange = { key = it },
                                    label = { Text("Multi-byte key (hex: 4A6F686E)") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                                Text("Enter hex-encoded key bytes", fontSize = 10.sp, color = TextMuted)
                            }
                            9 -> {
                                OutlinedTextField(value = caesarShift, onValueChange = { caesarShift = it },
                                    label = { Text("Shift (1-25)") }, modifier = Modifier.width(120.dp), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                            }
                            else -> {
                                OutlinedTextField(value = key, onValueChange = { key = it },
                                    label = { Text("Encryption key") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                                if (selectedMethod == 2) Text("AES: 16 bytes (128-bit) or 32 bytes (256-bit)", fontSize = 10.sp, color = TextMuted)
                                if (selectedMethod == 3) Text("DES: 8 bytes key", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // Input
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Plaintext Input", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        label = { Text("Enter text to encrypt...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                }
            }

            // Encrypt button
            Button(onClick = {
                output = try {
                    encryptData(input, selectedMethod, key, caesarShift.toIntOrNull() ?: 3)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp), enabled = input.isNotEmpty()) {
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Encrypt", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Output
            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Encrypted Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("encrypted", output))
                            }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(output, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutputButton(content = { output }, filename = "encrypted.txt", subfolder = "encrypt")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun encryptData(input: String, method: Int, key: String, shift: Int): String {
    if (input.isEmpty()) return ""
    return when (method) {
        0 -> { // XOR Single-Key
            val k = if (key.isNotEmpty()) key.first().code.toByte() else 0x42
            input.toByteArray().joinToString("") { "%02X".format((it.toInt() xor k.toInt()) and 0xFF) }
        }
        1 -> { // XOR Multi-Key
            val keyBytes = hexToBytes(key.ifEmpty { "4A6F686E" })
            input.toByteArray().mapIndexed { i, b -> "%02X".format((b.toInt() xor keyBytes[i % keyBytes.size].toInt()) and 0xFF) }.joinToString("")
        }
        2 -> { // AES
            val keyBytes = padKey(key.ifEmpty { "0000000000000000" }, 16)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            Base64.encodeToString(cipher.doFinal(input.toByteArray()), Base64.NO_WRAP)
        }
        3 -> { // DES
            val keyBytes = padKey(key.ifEmpty { "00000000" }, 8)
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
            Base64.encodeToString(cipher.doFinal(input.toByteArray()), Base64.NO_WRAP)
        }
        4 -> Base64.encodeToString(input.toByteArray(), Base64.NO_WRAP) // Base64
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
        7 -> { // Vigenère
            val k = key.ifEmpty { "KEY" }.uppercase()
            input.mapIndexed { i, c ->
                val shift = k[i % k.length].code - 'A'.code
                when {
                    c in 'A'..'Z' -> 'A' + (c - 'A' + shift) % 26
                    c in 'a'..'z' -> 'a' + (c - 'a' + shift) % 26
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
            val result = mutableListOf<Int>()
            for (b in input.toByteArray()) { x = (x + 1) and 0xFF; y = (y + s[x]) and 0xFF; val t = s[x]; s[x] = s[y]; s[y] = t; result.add((b.toInt() xor s[(s[x] + s[y]) and 0xFF]) and 0xFF) }
            result.joinToString("") { "%02X".format(it) }
        }
        9 -> { // Caesar
            val s = shift.coerceIn(1, 25)
            input.map { c ->
                when {
                    c in 'A'..'Z' -> 'A' + (c - 'A' + s) % 26
                    c in 'a'..'z' -> 'a' + (c - 'a' + s) % 26
                    else -> c
                }
            }.joinToString("")
        }
        else -> input
    }
}

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace(" ", "")
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun padKey(key: String, size: Int): ByteArray {
    val bytes = key.toByteArray()
    return if (bytes.size >= size) bytes.copyOf(size) else bytes + ByteArray(size - bytes.size)
}

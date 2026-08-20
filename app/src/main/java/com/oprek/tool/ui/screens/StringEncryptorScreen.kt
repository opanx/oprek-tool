package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringEncryptorScreen(navController: NavController) {
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("XOR") }
    var result by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("String Encryptor", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Input string") }, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Key (hex or text)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, cursorColor = AccentOrange))
            Row(Modifier.padding(vertical = 8.dp)) {
                listOf("XOR", "AES", "ROT13", "Base64+XOR").forEach { m ->
                    FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m, fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(0.2f)))
                }
            }
            Button(onClick = {
                val keyBytes = key.ifEmpty { "default_key" }.toByteArray()
                result = when (method) {
                    "XOR" -> input.toByteArray().mapIndexed { i, b -> (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }.joinToString(" ") { "%02X".format(it) }
                    "ROT13" -> input.map { c -> when { c in 'a'..'m' || c in 'A'..'M' -> (c.code + 13).toChar(); c in 'n'..'z' || c in 'N'..'Z' -> (c.code - 13).toChar(); else -> c } }.joinToString("")
                    "Base64+XOR" -> {
                        val xored = input.toByteArray().mapIndexed { i, b -> (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }.toByteArray()
                        android.util.Base64.encodeToString(xored, android.util.Base64.NO_WRAP)
                    }
                    "AES" -> try {
                        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes.copyOf(16).also { if (it.size < 16) it[it.size-1] = 0 }, "AES"))
                        android.util.Base64.encodeToString(cipher.doFinal(input.toByteArray()), android.util.Base64.NO_WRAP)
                    } catch (e: Exception) { "Error: ${e.message}" }
                    else -> ""
                }
            }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Encrypt") }
            Spacer(Modifier.height(12.dp))
            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Text(result, modifier = Modifier.padding(12.dp), color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Encryption complete" },
                filename = "encrypted.txt",
                subfolder = "encode"
            )

        }
    }
}

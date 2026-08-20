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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfuscateScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("hex") }
    var xorKey by remember { mutableStateOf("FF") }
    var isProcessing by remember { mutableStateOf(false) }

    val modes = listOf(
        "hex" to "To Hex",
        "base64" to "To Base64",
        "url" to "To URL Encode",
        "unicode" to "To Unicode",
        "xor" to "XOR Encrypt",
        "reverse" to "Reverse",
        "escape" to "Shell Escape",
        "rot13" to "ROT13",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔒 Obfuscate", fontWeight = FontWeight.Bold) },
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Mode selector
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.take(4).forEach { (key, label) ->
                            FilterChip(selected = selectedMode == key, onClick = { selectedMode = key }, label = { Text(label, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(0.3f)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.drop(4).forEach { (key, label) ->
                            FilterChip(selected = selectedMode == key, onClick = { selectedMode = key }, label = { Text(label, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(0.3f)))
                        }
                    }

                    // XOR key input
                    if (selectedMode == "xor") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = xorKey,
                            onValueChange = { xorKey = it },
                            label = { Text("XOR Key (hex, e.g. FF)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                        )
                    }
                }
            }

            // Input
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Input", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Enter text to obfuscate...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                    )
                }
            }

            // Process button
            Button(
                onClick = {
                    isProcessing = true
                    scope.launch(Dispatchers.Default) {
                        val result = withContext(Dispatchers.Default) {
                            processObfuscate(inputText, selectedMode, xorKey)
                        }
                        outputText = result
                        isProcessing = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp),
                enabled = inputText.isNotEmpty() && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Obfuscate", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Output
            if (outputText.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val clipboard = navController.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("output", outputText))
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentOrange)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            outputText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

        }
    }
}

private fun processObfuscate(input: String, mode: String, xorKey: String): String {
    if (input.isBlank()) return ""
    return try {
        when (mode) {
            "hex" -> input.toByteArray().joinToString(" ") { "%02X".format(it) }
            "base64" -> android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
            "url" -> URLEncoder.encode(input, "UTF-8")
            "unicode" -> input.map { "\\u${"%04x".format(it.code)}" }.joinToString("")
            "xor" -> {
                val key = xorKey.removePrefix("0x").removePrefix("0X").toInt(16).toByte()
                input.toByteArray().map { (it.toInt() xor key.toInt()).toByte() }.joinToString(" ") { "%02X".format(it) }
            }
            "reverse" -> input.reversed()
            "escape" -> input.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
            "rot13" -> input.map { c ->
                when {
                    c in 'a'..'m' || c in 'A'..'M' -> (c.code + 13).toChar()
                    c in 'n'..'z' || c in 'N'..'Z' -> (c.code - 13).toChar()
                    else -> c
                }
            }.joinToString("")
            else -> input
        }
    } catch (e: Exception) {
        "Error: \${e.message}"
    }
}

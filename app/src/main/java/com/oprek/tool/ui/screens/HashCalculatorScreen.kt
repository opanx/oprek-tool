package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Color
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashCalculatorScreen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val file = java.io.File(context.cacheDir, "oprek").listFiles()?.firstOrNull()
        if (file != null) {
            val bytes = file.readBytes()
            input = file.name + " (" + bytes.size + " bytes)"
            results = mapOf(
                "MD5" to md5(bytes), "SHA-1" to sha(bytes, "SHA-1"),
                "SHA-256" to sha(bytes, "SHA-256"), "SHA-512" to sha(bytes, "SHA-512"),
                "CRC32" to crc32(bytes), "Length" to bytes.size.toString() + " bytes",
                "Hex" to bytes.take(32).joinToString(" ") { "%02X".format(it) } + "..."
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 Hash Calculator", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Input Text", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { results },
                filename = "hashes.txt",
                subfolder = "hash"
            )

                    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Enter text or paste content...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val bytes = input.toByteArray()
                results = mapOf(
                    "MD5" to md5(bytes),
                    "SHA-1" to sha(bytes, "SHA-1"),
                    "SHA-256" to sha(bytes, "SHA-256"),
                    "SHA-512" to sha(bytes, "SHA-512"),
                    "CRC32" to crc32(bytes),
                    "Length" to "${bytes.size} bytes",
                    "Hex" to bytes.joinToString(" ") { "%02X".format(it) }
                )
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                Text("Calculate", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            results.forEach { (name, value) ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$name: ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentPurple, modifier = Modifier.width(90.dp))
                        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText(name, value))
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }
private fun sha(b: ByteArray, algo: String) = MessageDigest.getInstance(algo).digest(b).joinToString("") { "%02x".format(it) }
private fun crc32(b: ByteArray): String { val c = CRC32(); c.update(b); return "%08X".format(c.value) }

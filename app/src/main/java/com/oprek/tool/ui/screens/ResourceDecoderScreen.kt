package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.io.File
import java.util.jar.JarFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDecoderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isDecoding by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("auto") }

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isDecoding = true
            output = listOf("[*] Decoding resources...")
            scope.launch(Dispatchers.IO) {
                val result = decodeResources(context, it, selectedMode)
                withContext(Dispatchers.Main) { output = result; isDecoding = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 Resource Decoder", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }, enabled = output.isNotEmpty()) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Decode Android binary resources to readable format", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("auto" to "Auto Detect", "arsc" to "resources.arsc", "xml" to "Binary XML", "list" to "List Files").forEach { (mode, label) ->
                            FilterChip(selected = selectedMode == mode, onClick = { selectedMode = mode }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isDecoding,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), shape = RoundedCornerShape(8.dp)) {
                        Text("Select APK/Resource File", fontSize = 11.sp)
                    }
                }
            }
            if (isDecoding) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentOrange)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Decoded Resources (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(output) { line ->
                            val color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("<") -> AccentCyan  // XML
                                line.contains("res/") -> AccentOrange
                                else -> TextPrimary
                            }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun decodeResources(context: Context, uri: android.net.Uri, mode: String): List<String> {
    val result = mutableListOf<String>()
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open")
        val data = stream.readBytes()
        stream.close()

        // Check if it's an APK
        if (data.size > 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
            // It's a ZIP/APK
            val tempFile = File(context.cacheDir, "res_decode.apk")
            tempFile.writeBytes(data)
            val jar = JarFile(tempFile)

            val entries = jar.entries()
            val resources = mutableListOf<String>()
            val xmls = mutableListOf<String>()
            val drawables = mutableListOf<String>()
            val others = mutableListOf<String>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                when {
                    entry.name.startsWith("res/") && entry.name.endsWith(".xml") -> xmls.add(entry.name)
                    entry.name.startsWith("res/") && (entry.name.endsWith(".png") || entry.name.endsWith(".webp") || entry.name.endsWith(".jpg")) -> drawables.add(entry.name)
                    entry.name == "resources.arsc" -> resources.add(entry.name)
                    entry.name.startsWith("res/") -> others.add(entry.name)
                }
            }

            result.add("[+] APK Resource Analysis")
            result.add("[+] resources.arsc: ${resources.size} files")
            result.add("[+] Binary XML: ${xmls.size} files")
            result.add("[+] Drawables: ${drawables.size} files")
            result.add("[+] Other resources: ${others.size} files")
            result.add("")

            if (mode == "arsc" || mode == "auto") {
                result.add("[*] === resources.arsc ===")
                try {
                    val resEntry = jar.getEntry("resources.arsc")
                    if (resEntry != null) {
                        val resBytes = jar.getInputStream(resEntry).readBytes()
                        result.add("[+] Size: ${resBytes.size} bytes")
                        // Parse res table header
                        if (resBytes.size >= 12) {
                            val pkgCount = (resBytes[8].toInt() and 0xFF) or
                                ((resBytes[9].toInt() and 0xFF) shl 8) or
                                ((resBytes[10].toInt() and 0xFF) shl 16) or
                                ((resBytes[11].toInt() and 0xFF) shl 24)
                            result.add("[+] Package count: $pkgCount")
                        }
                        // Extract string pool from arsc
                        val text = String(resBytes, Charsets.US_ASCII)
                        val regex = Regex("""[a-z][a-z0-9_.]+/[a-z_]+""")
                        val resStrings = regex.findAll(text).map { it.value }.distinct().take(50).toList()
                        result.add("[+] Resource references: ${resStrings.size}")
                        resStrings.forEach { result.add("    $it") }
                    }
                } catch (e: Exception) {
                    result.add("[-] Cannot parse arsc: ${e.message}")
                }
            }

            if (mode == "xml" || mode == "auto") {
                result.add("")
                result.add("[*] === Binary XML Files ===")
                xmls.take(20).forEach { name ->
                    result.add("[+] $name")
                    try {
                        val xmlEntry = jar.getEntry(name)
                        if (xmlEntry != null) {
                            val xmlBytes = jar.getInputStream(xmlEntry).readBytes()
                            // Extract readable strings from binary XML
                            val strings = extractReadableStrings(xmlBytes)
                            strings.take(10).forEach { s -> result.add("      $s") }
                        }
                    } catch (e: Exception) {
                        result.add("      [-] Parse error")
                    }
                }
            }

            if (mode == "list" || mode == "auto") {
                result.add("")
                result.add("[*] === All Resource Files ===")
                (xmls + drawables + others).sorted().forEach { result.add("    $it") }
            }

            tempFile.delete()
            jar.close()
        } else {
            // Not an APK - try to decode raw binary XML
            result.add("[*] Not an APK, attempting raw binary decode...")
            if (data.size > 4 && data[0] == 0x03.toByte()) {
                result.add("[+] Detected Android Binary XML format")
                val strings = extractReadableStrings(data)
                result.add("[+] Extracted ${strings.size} strings:")
                strings.forEach { result.add("    $it") }
            } else if (data.size > 4 && data[0] == 0x02.toByte()) {
                result.add("[+] Detected resources.arsc format")
                val strings = extractReadableStrings(data)
                strings.take(50).forEach { result.add("    $it") }
            } else {
                result.add("[-] Unknown resource format")
                result.add("[*] Magic: ${data.take(4).joinToString(" ") { "%02X".format(it) }}")
            }
        }
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

private fun extractReadableStrings(data: ByteArray): List<String> {
    val strings = mutableListOf<String>()
    val current = StringBuilder()
    for (b in data) {
        if (b in 0x20..0x7E) {
            current.append(b.toInt().toChar())
        } else {
            if (current.length >= 4) {
                val s = current.toString().trim()
                if (s.isNotEmpty() && !strings.contains(s)) strings.add(s)
            }
            current.clear()
        }
    }
    if (current.length >= 4) {
        val s = current.toString().trim()
        if (s.isNotEmpty() && !strings.contains(s)) strings.add(s)
    }
    return strings
}

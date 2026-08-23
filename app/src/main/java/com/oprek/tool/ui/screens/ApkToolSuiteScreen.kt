@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkToolSuiteScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf("decode") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isRunning = true
            output = emptyList()
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(it) ?: return@launch
                    val bytes = stream.readBytes()
                    stream.close()

                    // Get filename
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    val fileName = cursor?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && idx >= 0) c.getString(idx) ?: "app.apk" else "app.apk"
                    } ?: "app.apk"

                    // Save to cache
                    val cacheFile = File(context.cacheDir, fileName)
                    cacheFile.writeBytes(bytes)

                    withContext(Dispatchers.Main) { output = output + "[+] Loaded: $fileName (${bytes.size} bytes)" }
                    setProgress(0.1f)

                    if (!fileName.endsWith(".apk", true) && !fileName.endsWith(".apks", true)) {
                        withContext(Dispatchers.Main) { output = output + "[-] Not an APK file"; isRunning = false }
                        return@launch
                    }

                    // Open as ZIP
                    val zip = ZipFile(cacheFile)
                    val entries = zip.entries().toList()

                    withContext(Dispatchers.Main) {
                        output = output + "[+] ZIP entries: ${entries.size}"
                        output = output + ""
                    }
                    setProgress(0.2f)

                    // Mode: Decode
                    if (mode == "decode") {
                        // 1. Extract AndroidManifest.xml (binary)
                        withContext(Dispatchers.Main) { output = output + "═══ AndroidManifest.xml ═══" }
                        val manifestEntry = entries.find { it.name == "AndroidManifest.xml" }
                        if (manifestEntry != null) {
                            val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
                            // Extract strings from binary XML
                            val strings = extractBinaryXmlStrings(manifestBytes)
                            withContext(Dispatchers.Main) {
                                output = output + "[+] Binary XML: ${manifestBytes.size} bytes"
                                output = output + "[+] Extracted ${strings.size} strings:"
                                strings.take(100).forEach { s -> output = output + "    $s" }
                                if (strings.size > 100) output = output + "    ... and ${strings.size - 100} more"
                            }
                        } else {
                            withContext(Dispatchers.Main) { output = output + "[-] AndroidManifest.xml not found" }
                        }
                        setProgress(0.3f)

                        // 2. List resources.arsc
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ resources.arsc ═══" }
                        val arscEntry = entries.find { it.name == "resources.arsc" }
                        if (arscEntry != null) {
                            val arscBytes = zip.getInputStream(arscEntry).readBytes()
                            withContext(Dispatchers.Main) {
                                output = output + "[+] Size: ${arscBytes.size} bytes"
                                // Extract resource type strings
                                val resStrings = extractReadableStrings(arscBytes).take(50)
                                resStrings.forEach { s -> output = output + "    $s" }
                            }
                        }
                        setProgress(0.4f)

                        // 3. List DEX files
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ DEX Files ═══" }
                        val dexEntries = entries.filter { it.name.endsWith(".dex") }
                        withContext(Dispatchers.Main) {
                            output = output + "[+] Found ${dexEntries.size} DEX files"
                            dexEntries.forEach { e -> output = output + "    ${e.name} (${e.size / 1024}KB)" }
                        }
                        setProgress(0.5f)

                        // 4. List native libraries
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ Native Libraries ═══" }
                        val soEntries = entries.filter { it.name.endsWith(".so") }
                        withContext(Dispatchers.Main) {
                            output = output + "[+] Found ${soEntries.size} .so files"
                            soEntries.take(30).forEach { e -> output = output + "    ${e.name} (${e.size / 1024}KB)" }
                            if (soEntries.size > 30) output = output + "    ... and ${soEntries.size - 30} more"
                        }
                        setProgress(0.6f)

                        // 5. List all resources
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ Resource Files ═══" }
                        val resEntries = entries.filter { it.name.startsWith("res/") }
                        val resTypes = resEntries.groupBy { it.name.substringAfter("res/").substringBefore("/") }
                        withContext(Dispatchers.Main) {
                            output = output + "[+] ${resEntries.size} resource files in ${resTypes.size} types:"
                            resTypes.forEach { (type, files) ->
                                output = output + "    $type/: ${files.size} files"
                            }
                        }
                        setProgress(0.7f)

                        // 6. List other important files
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ Other Files ═══" }
                        val otherEntries = entries.filter {
                            !it.name.startsWith("res/") && !it.name.endsWith(".dex") &&
                            !it.name.endsWith(".so") && it.name != "AndroidManifest.xml" &&
                            it.name != "resources.arsc"
                        }
                        withContext(Dispatchers.Main) {
                            output = output + "[+] ${otherEntries.size} other files:"
                            otherEntries.take(20).forEach { e -> output = output + "    ${e.name} (${e.size} bytes)" }
                        }

                        // 7. Extract to /sdcard
                        withContext(Dispatchers.Main) { output = output + ""; output = output + "═══ Extracting APK ═══" }
                        val outDir = File("/sdcard/Download/OprekTool/apktool/${fileName.replace(".apk", "")}")
                        outDir.mkdirs()

                        var extracted = 0
                        for (entry in entries) {
                            if (entry.isDirectory) continue
                            val outFile = File(outDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output2 ->
                                    input.copyTo(output2)
                                }
                            }
                            extracted++
                            if (extracted % 50 == 0) {
                                setProgress(0.7f + (extracted.toFloat() / entries.size) * 0.25f)
                            }
                        }
                        zip.close()

                        withContext(Dispatchers.Main) {
                            output = output + "[+] Extracted $extracted files to:"
                            output = output + "    ${outDir.absolutePath}"
                            output = output + ""
                            output = output + "✅ APK decoded successfully!"
                            output = output + "📋 Next steps:"
                            output = output + "  1. Edit files in ${outDir.absolutePath}"
                            output = output + "  2. Use APK Signer to re-sign modified APK"
                            output = output + "  3. Or use 'rebuild' mode (coming soon)"
                        }
                        setProgress(1.0f)
                    }

                    // Clean up cache
                    cacheFile.delete()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { output = output + "[-] Error: ${e.message}" }
                }
                withContext(Dispatchers.Main) { isRunning = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 APKTool Suite", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📦 APK Decode & Analyze", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 13.sp)
                    Text("Extract resources, manifest, DEX, native libs from APK", fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))

                    // Mode selector
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("decode" to "📥 Decode APK", "rebuild" to "🔨 Rebuild (PC)").forEach { (m, label) ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = !isRunning, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp)) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isRunning) "Processing..." else "Select APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = AccentOrange)
                    }
                }
            }

            // Output
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(output) { line ->
                            val color = when {
                                line.startsWith("✅") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("═") -> AccentCyan
                                line.startsWith("[+]") -> AccentGreen
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

private fun extractBinaryXmlStrings(data: ByteArray): List<String> {
    val strings = mutableListOf<String>()
    var cur = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) cur.append(c.toChar())
        else {
            if (cur.length >= 3) strings.add(cur.toString())
            cur.clear()
        }
    }
    if (cur.length >= 3) strings.add(cur.toString())

    // Also extract UTF-16LE strings
    val text = String(data, Charsets.UTF_16LE)
    val regex = Regex("""[a-zA-Z][a-zA-Z0-9_.]{2,}""")
    regex.findAll(text).forEach { match ->
        val s = match.value
        if (s !in strings && s.length >= 3) strings.add(s)
    }
    return strings.distinct()
}

private fun extractReadableStrings(data: ByteArray): List<String> {
    val strings = mutableListOf<String>()
    var cur = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) cur.append(c.toChar())
        else {
            if (cur.length >= 4) strings.add(cur.toString())
            cur.clear()
        }
    }
    if (cur.length >= 4) strings.add(cur.toString())
    return strings.distinct()
}

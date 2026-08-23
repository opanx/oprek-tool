package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Environment
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkMergerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("info") }

    val singlePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isRunning = true
            scope.launch(Dispatchers.IO) {
                val result = when (mode) {
                    "info" -> analyzeApk(context, it)
                    "extract" -> extractApk(context, it)
                    "decompile" -> listApkContents(context, it)
                    else -> listOf("[-] Unknown mode")
                }
                withContext(Dispatchers.Main) { output = result; isRunning = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 APK Tools", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
                    Text("APK analysis, extraction, and manipulation tools", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("info" to "📋 Info", "extract" to "📂 Extract", "decompile" to "🔍 List").forEach { (m, label) ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { singlePicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                        modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), shape = RoundedCornerShape(8.dp)) {
                        Text("Select APK", fontSize = 11.sp)
                    }
                }
            }
            if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentOrange)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(output) { line ->
                            val color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
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

private fun analyzeApk(context: Context, uri: android.net.Uri): List<String> {
    val result = mutableListOf<String>()
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open")
        val data = stream.readBytes()
        stream.close()

        result.add("[+] APK Analysis")
        result.add("[+] File size: ${data.size} bytes (${data.size / 1024}KB)")
        result.add("[+] Magic: ${data.take(4).joinToString("") { "%02X".format(it) }}")

        val tempFile = File(context.cacheDir, "apk_info.apk")
        tempFile.writeBytes(data)
        val jar = ZipInputStream(tempFile.inputStream())
        var entry = jar.nextEntry

        var manifest = false; var dex = 0; var libs = 0; var res = 0; var assets = 0; var meta = 0
        val soFiles = mutableListOf<String>()
        val allFiles = mutableListOf<String>()

        while (entry != null) {
            allFiles.add(entry.name)
            when {
                entry.name == "AndroidManifest.xml" -> manifest = true
                entry.name.endsWith(".dex") -> dex++
                entry.name.startsWith("lib/") -> { libs++; if (entry.name.endsWith(".so")) soFiles.add(entry.name) }
                entry.name.startsWith("res/") -> res++
                entry.name.startsWith("assets/") -> assets++
                entry.name.startsWith("META-INF/") -> meta++
            }
            entry = jar.nextEntry
        }
        jar.close()
        tempFile.delete()

        result.add("")
        result.add("[+] Contents:")
        result.add("[+] AndroidManifest.xml: ${if (manifest) "✅" else "❌"}")
        result.add("[+] DEX files: $dex")
        result.add("[+] Native libraries (.so): $libs")
        result.add("[+] Resources: $res")
        result.add("[+] Assets: $assets")
        result.add("[+] META-INF: $meta")
        result.add("[+] Total entries: ${allFiles.size}")

        if (soFiles.isNotEmpty()) {
            result.add("")
            result.add("[+] Native libraries:")
            soFiles.forEach { result.add("    $it") }
        }
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

private fun extractApk(context: Context, uri: android.net.Uri): List<String> {
    val result = mutableListOf<String>()
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open")
        val data = stream.readBytes()
        stream.close()

        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/extracted")
        outDir.mkdirs()

        val tempFile = File(context.cacheDir, "extract.apk")
        tempFile.writeBytes(data)
        val zis = ZipInputStream(tempFile.inputStream())
        var entry = zis.nextEntry
        var count = 0

        while (entry != null) {
            val outFile = File(outDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                count++
            }
            entry = zis.nextEntry
        }
        zis.close()
        tempFile.delete()

        result.add("[+] Extracted $count files")
        result.add("[+] Output: ${outDir.absolutePath}")
        result.add("")
        // List extracted files
        outDir.walkTopDown().take(50).forEach { f ->
            if (f.isFile) result.add("    ${f.relativeTo(outDir)}")
        }
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

private fun listApkContents(context: Context, uri: android.net.Uri): List<String> {
    val result = mutableListOf<String>()
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open")
        val data = stream.readBytes()
        stream.close()

        val tempFile = File(context.cacheDir, "list.apk")
        tempFile.writeBytes(data)
        val zis = ZipInputStream(tempFile.inputStream())
        var entry = zis.nextEntry

        val entries = mutableListOf<Pair<String, Long>>()
        var totalSize = 0L
        while (entry != null) {
            entries.add(entry.name to (entry.size ?: 0))
            totalSize += (entry.size ?: 0)
            entry = zis.nextEntry
        }
        zis.close()
        tempFile.delete()

        result.add("[+] APK Contents (${entries.size} entries)")
        result.add("[+] Compressed size: ${data.size} bytes")
        result.add("[+] Uncompressed size: $totalSize bytes")
        result.add("[+] Ratio: ${(data.size * 100 / maxOf(totalSize, 1))}%")
        result.add("")

        // Group by directory
        val grouped = entries.groupBy { it.first.substringBeforeLast("/", "root") }
        grouped.toSortedMap().forEach { (dir, files) ->
            result.add("📂 $dir/ (${files.size} files)")
            files.sortedBy { it.first }.forEach { (name, size) ->
                result.add("    ${name.substringAfterLast("/")}  ($size bytes)")
            }
        }
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

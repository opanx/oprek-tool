package com.oprek.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import java.util.zip.ZipInputStream

/**
 * .deb Package Analyzer
 * Extract, analyze, modify, and repack .deb packages
 * Supports Android system .deb packages from Termux/OFRAK
 */
data class DebEntry(
    val path: String, val size: Long, val isDir: Boolean,
    val type: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebPackageScreen(navController: NavController) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var targetFile by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf(listOf<DebEntry>()) }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var controlInfo by remember { mutableStateOf(mapOf<String, String>()) }

    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                targetFile = path
                fileName = File(path).name
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    val result = analyzeDeb(path)
                    withContext(Dispatchers.Main) {
                        entries = result.first
                        controlInfo = result.second
                        output = listOf(
                            "[+] Analyzed: ${fileName}",
                            "[+] Entries: ${entries.size}",
                            "[+] Control fields: ${controlInfo.size}",
                            "[+] Package: ${controlInfo["Package"] ?: "unknown"}",
                            "[+] Version: ${controlInfo["Version"] ?: "unknown"}",
                            "[+] Architecture: ${controlInfo["Architecture"] ?: "unknown"}"
                        )
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 .deb Package Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, null, tint = AccentOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (fileName.isNotEmpty()) "$fileName (${entries.size} files)" else "Select .deb package",
                        modifier = Modifier.weight(1f),
                        color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Tabs
            if (entries.isNotEmpty()) {
                TabRow(selectedTabIndex = selectedTab, containerColor = DarkCard) {
                    listOf("Files", "Control", "Actions", "Log").forEachIndexed { i, t ->
                        Tab(selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) })
                    }
                }

                when (selectedTab) {
                    0 -> FileTreeTab(entries)
                    1 -> ControlTab(controlInfo)
                    2 -> ActionsTab(targetFile, entries, output, { output = it }, isProcessing, { isProcessing = it }, scope)
                    3 -> LogTab(output)
                }
            } else if (!isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(".deb Package Analyzer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        Text("Extract, analyze, modify .deb packages", color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Supports: .deb, .ipk, .apk (ar archives)", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FileTreeTab(entries: List<DebEntry>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(entries) { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(start = (entry.path.count { it == '/' } * 12).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (entry.isDir) Icons.Default.Folder else Icons.Default.Description,
                    null,
                    tint = if (entry.isDir) AccentOrange else AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    entry.path.substringAfterLast('/'),
                    color = if (entry.isDir) AccentOrange else TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                if (!entry.isDir) {
                    Text(
                        formatSize(entry.size),
                        color = TextMuted, fontSize = 10.sp
                    )
                }
                if (entry.type.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(entry.type, color = AccentPurple, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun ControlTab(controlInfo: Map<String, String>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        if (controlInfo.isEmpty()) {
            item { Text("No control information found", color = TextSecondary) }
        }
        items(controlInfo.entries.toList()) { (key, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("$key: ", color = AccentGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.3f))
                Text(value, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
            }
        }
    }
}

@Composable
fun ActionsTab(
    targetFile: String?, entries: List<DebEntry>,
    output: List<String>, onOutputChange: (List<String>) -> Unit,
    isProcessing: Boolean, onProcessingChange: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Extract
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📦 Extract .deb", color = AccentOrange, fontWeight = FontWeight.Bold)
                    Text("Extract all files to /sdcard/Download/OprekTool/deb/", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (targetFile != null && !isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = extractDeb(targetFile)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        enabled = targetFile != null && !isProcessing
                    ) { Text("Extract") }
                }
            }
        }

        // Extract data.tar
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📂 Extract data.tar.xz", color = AccentCyan, fontWeight = FontWeight.Bold)
                    Text("Extract the main data archive from .deb", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (targetFile != null && !isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = extractDataTar(targetFile)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        enabled = targetFile != null && !isProcessing
                    ) { Text("Extract data.tar") }
                }
            }
        }

        // Repack
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔧 Repack .deb", color = AccentGreen, fontWeight = FontWeight.Bold)
                    Text("Repack modified files back to .deb format", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (targetFile != null && !isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = repackDeb(targetFile)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        enabled = targetFile != null && !isProcessing
                    ) { Text("Repack") }
                }
            }
        }

        // Info
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("ℹ️ .deb Format Info", color = AccentPurple, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Format: ar archive containing:", color = TextPrimary, fontSize = 11.sp)
                    Text("  debian-binary  — format version", color = TextMuted, fontSize = 10.sp)
                    Text("  control.tar.*   — metadata + scripts", color = TextMuted, fontSize = 10.sp)
                    Text("  data.tar.*      — actual files", color = TextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Compression: xz, gz, bz2, zst", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun LogTab(output: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(output) { line ->
            Text(
                line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = when {
                    line.startsWith("[+]") -> AccentGreen
                    line.startsWith("[-]") -> AccentRed
                    line.startsWith("[!]") -> AccentOrange
                    else -> TextPrimary
                },
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

// ─── .deb Analysis Functions ───

private fun analyzeDeb(path: String): Pair<List<DebEntry>, Map<String, String>> {
    val file = File(path)
    if (!file.exists()) return Pair(emptyList(), emptyMap())

    val entries = mutableListOf<DebEntry>()
    val control = mutableMapOf<String, String>()

    try {
        // .deb is an ar archive
        val process = ProcessBuilder("ar", "t", path)
            .redirectErrorStream(true)
            .start()
        val arOutput = process.inputStream.bufferedReader().readLines()
        process.waitFor()

        for (line in arOutput) {
            if (line.isNotBlank() && !line.startsWith("ar")) {
                entries.add(DebEntry(line, 0, line.endsWith("/"), getDebFileType(line)))
            }
        }

        // Try to extract control info
        val tmpDir = File(file.parentFile, "deb_tmp_${System.currentTimeMillis()}")
        tmpDir.mkdirs()
        try {
            ProcessBuilder("sh", "-c", "ar x '$path' --output='$tmpDir'")
                .start().waitFor()

            val controlTar = tmpDir.listFiles()?.firstOrNull { it.name.startsWith("control.tar") }
            if (controlTar != null) {
                val controlProcess = ProcessBuilder("sh", "-c", "tar xf '${controlTar.absolutePath}' -C '$tmpDir' ./control 2>/dev/null || tar xf '${controlTar.absolutePath}' -C '$tmpDir' control 2>/dev/null")
                    .redirectErrorStream(true)
                    .start()
                controlProcess.waitFor()

                val controlFile = File(tmpDir, "control")
                if (controlFile.exists()) {
                    controlFile.readLines().forEach { line ->
                        val colonIdx = line.indexOf(':')
                        if (colonIdx > 0) {
                            control[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim()
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        // Cleanup
        tmpDir.deleteRecursively()
    } catch (e: Exception) {
        entries.add(DebEntry("ERROR: ${e.message}", 0, false, "error"))
    }

    return Pair(entries, control)
}

private fun extractDeb(path: String): List<String> {
    val result = mutableListOf<String>()
    val outFile = File(path)
    val extractDir = File("/sdcard/Download/OprekTool/deb/${outFile.nameWithoutExtension}")
    extractDir.mkdirs()

    try {
        result.add("[+] Extracting: ${outFile.name}")
        result.add("[+] Output: ${extractDir.absolutePath}")

        val process = ProcessBuilder("sh", "-c", "ar x '$path' --output='${extractDir.absolutePath}'")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        result.addAll(output.map { "[*] $it" })

        // Extract data.tar
        val dataTar = extractDir.listFiles()?.firstOrNull { it.name.startsWith("data.tar") }
        if (dataTar != null) {
            result.add("[+] Extracting data.tar...")
            val dataExtract = File(extractDir, "data")
            dataExtract.mkdirs()
            val dataProcess = ProcessBuilder("sh", "-c", "tar xf '${dataTar.absolutePath}' -C '${dataExtract.absolutePath}'")
                .redirectErrorStream(true)
                .start()
            val dataOutput = dataProcess.inputStream.bufferedReader().readLines()
            dataProcess.waitFor()
            result.addAll(dataOutput.map { "[*] $it" })

            // Count files
            val fileCount = dataExtract.walkTopDown().filter { it.isFile }.count()
            result.add("[+] Extracted $fileCount files")
        }

        result.add("[+] Done! Output: ${extractDir.absolutePath}")
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }

    return result
}

private fun extractDataTar(path: String): List<String> {
    val result = mutableListOf<String>()
    val outFile = File(path)
    val extractDir = File("/sdcard/Download/OprekTool/deb/${outFile.nameWithoutExtension}_data")
    extractDir.mkdirs()

    try {
        // Extract .deb first
        val tmpDir = File(extractDir, "tmp")
        tmpDir.mkdirs()
        ProcessBuilder("sh", "-c", "ar x '$path' --output='${tmpDir.absolutePath}'").start().waitFor()

        val dataTar = tmpDir.listFiles()?.firstOrNull { it.name.startsWith("data.tar") }
        if (dataTar != null) {
            result.add("[+] Found: ${dataTar.name} (${dataTar.length()} bytes)")
            result.add("[+] Extracting to: ${extractDir.absolutePath}")

            val dataProcess = ProcessBuilder("sh", "-c", "tar xf '${dataTar.absolutePath}' -C '${extractDir.absolutePath}'")
                .redirectErrorStream(true)
                .start()
            val output = dataProcess.inputStream.bufferedReader().readLines()
            dataProcess.waitFor()
            result.addAll(output.map { "[*] $it" })

            val fileCount = extractDir.walkTopDown().filter { it.isFile }.count()
            result.add("[+] Extracted $fileCount files")
        } else {
            result.add("[-] data.tar.* not found in .deb")
        }

        tmpDir.deleteRecursively()
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }

    return result
}

private fun repackDeb(path: String): List<String> {
    val result = mutableListOf<String>()
    val outFile = File(path)
    val extractDir = File("/sdcard/Download/OprekTool/deb/${outFile.nameWithoutExtension}")

    if (!extractDir.exists()) {
        return listOf("[-] Extract directory not found: ${extractDir.absolutePath}", "[!] Extract first before repacking")
    }

    try {
        result.add("[+] Repacking: ${outFile.name}")

        val controlTar = extractDir.listFiles()?.firstOrNull { it.name.startsWith("control.tar") }
        val dataDir = File(extractDir, "data")
        val debianBinary = File(extractDir, "debian-binary")

        // Rebuild data.tar.xz
        if (dataDir.exists()) {
            result.add("[+] Building data.tar.xz...")
            val dataTarXz = File(extractDir, "data.tar.xz")
            val dataProcess = ProcessBuilder("sh", "-c", "cd '${dataDir.absolutePath}' && tar cJf '${dataTarXz.absolutePath}' .")
                .redirectErrorStream(true)
                .start()
            dataProcess.inputStream.bufferedReader().readLines()
            dataProcess.waitFor()
            result.add("[+] data.tar.xz: ${dataTarXz.length()} bytes")
        }

        // Repack .deb
        val outDeb = File("/sdcard/Download/OprekTool/${outFile.nameWithoutExtension}_repacked.deb")
        val arParts = mutableListOf<String>()
        if (debianBinary.exists()) arParts.add(debianBinary.absolutePath)
        if (controlTar != null) arParts.add(controlTar.absolutePath)
        val dataTarXz = File(extractDir, "data.tar.xz")
        if (dataTarXz.exists()) arParts.add(dataTarXz.absolutePath)

        if (arParts.isNotEmpty()) {
            val arCmd = "ar rcs '${outDeb.absolutePath}' ${arParts.joinToString(" ") { "'$it'" }}"
            val arProcess = ProcessBuilder("sh", "-c", arCmd)
                .redirectErrorStream(true)
                .start()
            val arOutput = arProcess.inputStream.bufferedReader().readLines()
            arProcess.waitFor()
            result.addAll(arOutput.map { "[*] $it" })
            result.add("[+] Repacked: ${outDeb.absolutePath} (${outDeb.length()} bytes)")
        }

        result.add("[+] Done!")
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }

    return result
}

private fun getDebFileType(name: String): String {
    return when {
        name == "debian-binary" -> "version"
        name.startsWith("control.tar") -> "control"
        name.startsWith("data.tar") -> "data"
        name.endsWith("/") -> "dir"
        else -> "file"
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}MB"
    }
}

private fun getPathFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                val name = it.getString(idx)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val file = File(context.cacheDir, name)
                file.outputStream().use { out -> inputStream.copyTo(out) }
                return file.absolutePath
            }
        }
    }
    return null
}

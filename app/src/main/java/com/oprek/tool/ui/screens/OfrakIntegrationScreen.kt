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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OFRAK Integration Screen
 * Run OFRAK commands from oprek-tool (requires Termux or root + ofrak installed)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfrakIntegrationScreen(navController: NavController) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var ofrakPath by remember { mutableStateOf("/data/data/com.termux/files/usr/bin/ofrak") }
    var isInstalled by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var targetFile by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }

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
                output = output + "[+] Target file: $fileName"
            }
        }
    }

    // Check if ofrak is installed
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val result = runCommand("which ofrak 2>/dev/null || ls /data/data/com.termux/files/usr/bin/ofrak 2>/dev/null || echo NOT_FOUND")
            withContext(Dispatchers.Main) {
                isInstalled = !result.contains("NOT_FOUND")
                if (isInstalled) {
                    val pathLine = result.firstOrNull { !it.contains("NOT_FOUND") && it.isNotBlank() }
                    if (pathLine != null) ofrakPath = pathLine.trim()
                }
                output = listOf(
                    if (isInstalled) "[+] OFRAK found at: $ofrakPath" else "[!] OFRAK not found",
                    "[!] Install: curl -sL https://raw.githubusercontent.com/Opanxxc/ofrak/master/scripts/termux-install.sh | bash",
                    "[!] Or: pip install ofrak"
                )
            }
        }
    }

    // Preset commands
    val presets = listOf(
        Triple("🔍 Identify", "identify", "Identify file format and magic bytes"),
        Triple("📦 Unpack All", "unpack", "Recursively unpack all nested formats"),
        Triple("📊 Analyze", "analyze", "Analyze binary structure (ELF/PE/DEX)"),
        Triple("🔧 Patch NOP", "patch-nop", "NOP out a section at given offset"),
        Triple("🏷️ List Symbols", "symbols", "List all symbols in ELF binary"),
        Triple("🗺️ Entropy Map", "entropy", "Calculate entropy per block"),
        Triple("🔄 Repack", "repack", "Repack modified resource back to original format"),
        Triple("📦 Extract .deb", "extract-deb", "Extract .deb package contents"),
        Triple("🔧 Modify Bytes", "patch-bytes", "Replace bytes at offset"),
        Triple("🔍 Search Pattern", "search", "Search for hex pattern in binary"),
        Triple("📋 Resource Tree", "tree", "Show resource tree structure"),
        Triple("🗑️ Strip Debug", "strip", "Strip debug symbols from ELF"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ OFRAK Integration", fontWeight = FontWeight.Bold) },
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
            // Status card
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        tint = if (isInstalled) AccentGreen else AccentRed
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isInstalled) "OFRAK Ready" else "OFRAK Not Installed",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (isInstalled) AccentGreen else AccentRed
                        )
                        Text("Path: $ofrakPath", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (fileName.isNotEmpty()) fileName else "Select target file",
                        modifier = Modifier.weight(1f),
                        color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Preset commands
            Text("  Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(patches.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (label, cmd, desc) ->
                            Card(
                                Modifier.weight(1f).clickable {
                                    selectedPreset = cmd
                                    val cmdStr = when (cmd) {
                                        "identify" -> "ofrak identify $targetFile"
                                        "unpack" -> "ofrak unpack --force $targetFile"
                                        "analyze" -> "ofrak analyze $targetFile"
                                        "symbols" -> "ofrak elf-symbols $targetFile"
                                        "entropy" -> "ofrak entropy $targetFile"
                                        "repack" -> "ofrak repack $targetFile"
                                        "extract-deb" -> "ar x $targetFile --output=deb_extracted/"
                                        "search" -> "ofrak hex $targetFile | grep -i 'pattern'"
                                        "tree" -> "ofrak tree $targetFile"
                                        "strip" -> "ofrak strip $targetFile"
                                        else -> "ofrak $cmd $targetFile"
                                    }
                                    command = cmdStr
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedPreset == cmd) AccentPurple.copy(alpha = 0.2f) else DarkCard
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                                    Text(desc, fontSize = 9.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Command input
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("OFRAK command") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                leadingIcon = { Icon(Icons.Default.Terminal, null, tint = AccentGreen) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentGreen),
                maxLines = 3
            )

            // Run button
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (command.isNotEmpty() && !isRunning) {
                            isRunning = true
                            scope.launch(Dispatchers.IO) {
                                val result = runCommand(command)
                                withContext(Dispatchers.Main) {
                                    output = output + result
                                    isRunning = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    enabled = command.isNotEmpty() && !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRunning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Run")
                }

                OutlinedButton(
                    onClick = { output = emptyList() },
                    colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Clear") }
            }

            // Output
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(output) { line ->
                    Text(
                        line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = when {
                            line.startsWith("[+]") -> AccentGreen
                            line.startsWith("[-]") -> AccentRed
                            line.startsWith("[!]") -> AccentOrange
                            line.startsWith("[*]") -> AccentCyan
                            line.contains("error", ignoreCase = true) -> AccentRed
                            line.contains("warning", ignoreCase = true) -> AccentOrange
                            else -> TextPrimary
                        },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun runCommand(cmd: String): List<String> {
    return try {
        val process = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        output.ifEmpty { listOf("[*] Command completed with no output") }
    } catch (e: Exception) {
        listOf("[-] Error: ${e.message}")
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

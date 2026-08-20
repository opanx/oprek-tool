package com.oprek.tool.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.oprek.tool.core.FileAnalyzer
import com.oprek.tool.core.FileUtils
import com.oprek.tool.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(navController: NavController) {
    val context = LocalContext.current
    var exportContent by remember { mutableStateOf("") }
    var savedPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull()
        if (file != null) {
            val info = FileAnalyzer.getFileInfo(file)
            val strings = FileAnalyzer.extractStrings(file).take(100)
            val hex = FileAnalyzer.getHexDumpFull(file, 256).toHexLines()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

            exportContent = buildString {
                appendLine("=== OprekTool Analysis Report ===")
                appendLine("Generated: $timestamp")
                appendLine()
                appendLine("--- File Info ---")
                appendLine("Name: ${info.name}")
                appendLine("Type: ${info.type}")
                appendLine("Size: ${info.size} bytes")
                appendLine("Magic: ${info.magic}")
                appendLine("MD5: ${info.md5}")
                appendLine("SHA-256: ${info.sha256}")
                appendLine()
                appendLine("--- ELF Info ---")
                try {
                    val elfData = file.readBytes()
                    val elfInfo = com.oprek.tool.core.NativeLib.elfGetInfo(elfData)
                    appendLine(elfInfo)
                } catch (_: Exception) { appendLine("N/A") }
                appendLine()
                appendLine("--- Strings (first 100) ---")
                strings.forEach { appendLine("0x${"%08X".format(it.offset)}: ${it.value}") }
                appendLine()
                appendLine("--- Hex Dump (first 256 bytes) ---")
                hex.forEach { appendLine(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📤 Export Report", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val path = FileUtils.exportToFile(context, exportContent, "report_${System.currentTimeMillis()}.txt")
                    savedPath = path.absolutePath
                    Toast.makeText(context, "Saved to ${path.name}", Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                    Text("Save TXT", fontWeight = FontWeight.Bold)
                }
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, exportContent)
                        putExtra(Intent.EXTRA_SUBJECT, "OprekTool Report")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Report"))
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(12.dp)) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }
            if (savedPath.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Saved: $savedPath", fontSize = 12.sp, color = AccentGreen)
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Text(exportContent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                    modifier = Modifier.padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()))
            }
        }
    }
}

package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElfHeaderScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var headerInfo by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf<List<String>>(emptyList()) }
    var is64 by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        if (file.length() > 100 * 1024 * 1024) {

            // File too large for in-memory processing

        }

        val data = withContext(Dispatchers.IO) { file.readBytes() }
        if (data.size < 4 || data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte()) return@LaunchedEffect
        is64 = data[4] == 2.toByte()
        headerInfo = withContext(Dispatchers.Default) { com.oprek.tool.core.NativeLib.elfGetInfo(data) }
        sections = withContext(Dispatchers.Default) { com.oprek.tool.core.NativeLib.elfGetSections(data).toList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📦 ELF Header", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("elf", headerInfo)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // Header
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📦", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("ELF Header", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentGreen)
                            Text(if (is64) "ELF64 (64-bit)" else "ELF32 (32-bit)", fontSize = 13.sp, color = AccentCyan)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(headerInfo, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sections
            if (sections.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📂 Sections (${sections.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))

                        sections.forEach { sec ->
                            val parts = sec.split("|")
                            val name = parts.getOrElse(0) { "?" }
                            val type = parts.getOrElse(1) { "" }
                            val offset = parts.getOrElse(2) { "" }
                            val size = parts.getOrElse(3) { "" }
                            val typeColor = when {
                                name.contains(".text") -> AccentGreen
                                name.contains(".data") || name.contains(".bss") -> AccentRed
                                name.contains(".rodata") -> AccentBlue
                                name.contains(".symtab") || name.contains(".dynsym") -> AccentPurple
                                name.contains(".plt") || name.contains(".got") -> AccentOrange
                                else -> TextPrimary
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = typeColor, modifier = Modifier.width(100.dp))
                                Text(type, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentCyan, modifier = Modifier.width(80.dp))
                                Text(offset, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentOrange, modifier = Modifier.width(90.dp))
                                Text(size, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            }
                        }
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "ELF header loaded" },
                filename = "elf_header.txt",
                subfolder = "elf"
            )

        }
    }
}

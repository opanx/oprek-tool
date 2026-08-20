package com.oprek.tool.ui.screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var elfFound by remember { mutableStateOf(false) }
    var elfBase by remember { mutableLongStateOf(0L) }
    var strings by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var pointers by remember { mutableStateOf<List<Long>>(emptyList()) }
    var regions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        val data = withContext(kotlinx.coroutines.Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 10_000_000)) }

        // Search for ELF header in dump
        for (i in 0 until data.size - 4) {
            if (data[i] == 0x7F.toByte() && data[i+1] == 'E'.code.toByte() && data[i+2] == 'L'.code.toByte() && data[i+3] == 'F'.code.toByte()) {
                elfFound = true
                elfBase = i.toLong()
                break
            }
        }

        // Extract strings
        val sb = StringBuilder()
        var start = 0L
        val strs = mutableListOf<Pair<Long, String>>()
        for (i in data.indices) {
            val b = data[i].toInt() and 0xFF
            if (b in 0x20..0x7E) { if (sb.isEmpty()) start = i.toLong(); sb.append(b.toChar()) }
            else { if (sb.length >= 4) strs.add(start to sb.toString()); sb.clear() }
            if (strs.size >= 500) break
        }
        strings = strs

        // Extract pointers (8-byte aligned valid addresses)
        val ptrs = mutableListOf<Long>()
        for (i in 0 until data.size - 8 step 8) {
            val v = data[i].toLong() and 0xFF or ((data[i+1].toLong() and 0xFF) shl 8) or
                    ((data[i+2].toLong() and 0xFF) shl 16) or ((data[i+3].toLong() and 0xFF) shl 24) or
                    ((data[i+4].toLong() and 0xFF) shl 32) or ((data[i+5].toLong() and 0xFF) shl 40) or
                    ((data[i+6].toLong() and 0xFF) shl 48) or ((data[i+7].toLong() and 0xFF) shl 56)
            if (v in 0x400000L..0x7FFFFFFFFFL) { // User-space address range
                ptrs.add(v)
                if (ptrs.size >= 100) break
            }
        }
        pointers = ptrs
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🧠 Memory Dump", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // ELF detection
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔍 ELF Detection", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { results.joinToString("\n") },
                filename = "memdump.txt",
                subfolder = "analysis"
            )

                    if (elfFound) {
                        Text("✅ ELF header found at offset 0x${"%08X".format(elfBase)}", fontSize = 12.sp, color = AccentGreen)
                    } else {
                        Text("❌ No ELF header found in dump", fontSize = 12.sp, color = AccentRed)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Strings
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝 Strings (${strings.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("strs", strings.joinToString("\n") { "0x${"%08X".format(it.first)}: ${it.second}" })); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = AccentCyan) }
                    }
                    Spacer(Modifier.height(4.dp))
                    strings.take(50).forEach { (off, str) ->
                        Text("0x${"%08X".format(off)}: $str", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Pointers
            if (pointers.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📍 Pointers (${pointers.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                        Spacer(Modifier.height(4.dp))
                        pointers.take(30).forEach { ptr ->
                            Text("0x${"%016X".format(ptr)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentOrange)
                        }
                    }
                }
            }
        }
    }
}

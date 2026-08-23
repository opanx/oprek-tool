package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

data class PakEntry(val name: String, val offset: Long, val size: Long, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PakArchiveScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var entries by remember { mutableStateOf<List<PakEntry>>(emptyList()) }
    var magic by remember { mutableStateOf("") }
    var archiveType by remember { mutableStateOf("") }
    var totalSize by remember { mutableLongStateOf(0L) }
    var isZip by remember { mutableStateOf(false) }

    val rev = SharedFileState.revision

    LaunchedEffect(rev) {
        val file = SharedFileState.findFile(context) ?: return@LaunchedEffect
        totalSize = file.length()
        val bytes = withContext(Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 64)) }

        // Detect format
        magic = bytes.take(16).joinToString(" ") { "%02X".format(it) }
        isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

        if (isZip) {
            archiveType = "ZIP/PK format (Unity AssetBundle)"
            // Parse ZIP entries
            try {
                val zipEntries = mutableListOf<PakEntry>()
                withContext(Dispatchers.IO) {
                    val fis = file.inputStream()
                    val zis = ZipInputStream(fis)
                    var entry = zis.nextEntry
                    var offset = 0L
                    while (entry != null) {
                        val size = entry.size
                        val ext = entry.name.substringAfterLast('.').lowercase()
                        val type = when(ext) {
                            "unity3d", "assets", "bundle" -> "Unity Asset"
                            "json" -> "JSON"
                            "lua" -> "Lua Script"
                            "png", "jpg", "tga" -> "Texture"
                            "fbx", "obj" -> "Model"
                            "wav", "ogg", "mp3" -> "Audio"
                            "prefab" -> "Prefab"
                            "meta" -> "Meta"
                            else -> ext.uppercase()
                        }
                        zipEntries.add(PakEntry(entry.name, offset, size, type))
                        offset += size
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    zis.close()
                    fis.close()
                }
                entries = zipEntries
            } catch (_: Exception) {}
        } else {
            // Try to detect as custom pak format
            archiveType = when {
                magic.startsWith("41 54 41 43") -> "UnityFS (AssetBundle)"
                magic.startsWith("50 41 4B") -> "Custom PAK format"
                magic.startsWith("52 53 54") -> "RST Archive"
                else -> "Unknown binary format"
            }
            // Scan for embedded file signatures
            val scanEntries = mutableListOf<PakEntry>()
            val fullData: ByteArray = withContext(Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 10_000_000)) }

            // Search for common signatures
            val signatures = listOf(
                "UnityFS" to "Unity AssetBundle",
                "UnityRaw" to "Unity Raw Bundle",
                "PK" to "ZIP entry",
                "\u0089PNG" to "PNG image",
                "\u007FELF" to "ELF binary",
                "BM" to "BMP image",
                "GIF8" to "GIF image",
                "RIFF" to "RIFF/WAV audio",
            )
            for ((sig, desc) in signatures) {
                val sigBytes = sig.toByteArray()
                var pos = 0
                while (pos < fullData.size - sigBytes.size) {
                    var match = true
                    for (j in sigBytes.indices) { if (fullData[pos + j] != sigBytes[j]) { match = false; break } }
                    if (match) {
                        scanEntries.add(PakEntry("$desc @ 0x${"%08X".format(pos)}", pos.toLong(), 0, desc))
                    }
                    pos++
                }
            }
            entries = scanEntries
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📦 Pak Archive", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("pak", entries.joinToString("\n") { "${it.name} [${it.type}] ${it.size}B" })); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Info
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📦", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(archiveType, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                            Text("Size: ${totalSize} bytes • ${entries.size} entries", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Magic: $magic", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                }
            }
            // Entries
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No entries found", color = TextSecondary) }
            } else {
                LazyColumn(Modifier.padding(12.dp)) {

                    itemsIndexed(entries) { idx, entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(if (idx % 2 == 0) DarkBg else DarkSurface).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(entry.type) { "ELF binary" -> "📦"; "PNG image" -> "🖼️"; "Lua Script" -> "🌙"; "JSON" -> "📋"; "Audio" -> "🔊"; else -> "📄" }
                            Text(icon, fontSize = 16.sp, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, maxLines = 1)
                                Text("${entry.type} • ${entry.size}B", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

        }
    }
}

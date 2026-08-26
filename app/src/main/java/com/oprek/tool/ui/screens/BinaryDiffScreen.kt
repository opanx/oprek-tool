package com.oprek.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

data class DiffEntry(
    val offset: Int,
    val original: ByteArray,
    val modified: ByteArray,
    val sectionName: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinaryDiffScreen(navController: NavController) {
    val context = LocalContext.current
    var fileA by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var fileB by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var diffs by remember { mutableStateOf(listOf<DiffEntry>()) }
    var stats by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val pickerA = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                val data = File(path).readBytes()
                fileA = Pair(File(path).name, data)
            }
        }
    }

    val pickerB = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                val data = File(path).readBytes()
                fileB = Pair(File(path).name, data)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔀 Binary Diff", fontWeight = FontWeight.Bold) },
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
            // File A
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("A (Original): ${fileA?.first ?: "Not selected"}", modifier = Modifier.weight(1f), color = if (fileA != null) AccentGreen else TextSecondary, fontSize = 12.sp)
                    TextButton(onClick = { pickerA.launch(arrayOf("*/*")) }) { Text("Select") }
                }
            }

            // File B
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = AccentRed)
                    Spacer(Modifier.width(8.dp))
                    Text("B (Modified): ${fileB?.first ?: "Not selected"}", modifier = Modifier.weight(1f), color = if (fileB != null) AccentRed else TextSecondary, fontSize = 12.sp)
                    TextButton(onClick = { pickerB.launch(arrayOf("*/*")) }) { Text("Select") }
                }
            }

            // Compare button
            Button(
                onClick = {
                    if (fileA != null && fileB != null && !isProcessing) {
                        isProcessing = true
                        scope.launch(Dispatchers.IO) {
                            val result = computeDiff(fileA!!.second, fileB!!.second)
                            withContext(Dispatchers.Main) {
                                diffs = result
                                stats = "[+] Diff complete: ${result.size} byte differences found"
                                isProcessing = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                enabled = fileA != null && fileB != null && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Default.CompareArrows, null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Compare")
            }

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = AccentCyan)
            }

            if (stats.isNotEmpty()) {
                Text(stats, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = AccentGreen, fontSize = 12.sp)
            }

            // Diff results
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                itemsIndexed(diffs) { idx, diff ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                "#${idx + 1} @ 0x${String.format("%08X", diff.offset)}" +
                                        if (diff.sectionName.isNotEmpty()) " [${diff.sectionName}]" else "",
                                color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Row {
                                Text("A: ", color = AccentRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    diff.original.joinToString(" ") { String.format("%02X", it) },
                                    color = AccentRed.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                            Row {
                                Text("B: ", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    diff.modified.joinToString(" ") { String.format("%02X", it) },
                                    color = AccentGreen.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (diffs.isEmpty() && stats.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Files are identical! ✅", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun computeDiff(a: ByteArray, b: ByteArray): List<DiffEntry> {
    val diffs = mutableListOf<DiffEntry>()
    val len = minOf(a.size, b.size)

    var i = 0
    while (i < len) {
        if (a[i] != b[i]) {
            // Collect consecutive differing bytes
            val start = i
            val orig = mutableListOf<Byte>()
            val mod = mutableListOf<Byte>()
            while (i < len && a[i] != b[i]) {
                orig.add(a[i])
                mod.add(b[i])
                i++
                if (orig.size >= 64) break // Limit chunk size
            }
            diffs.add(DiffEntry(start, orig.toByteArray(), mod.toByteArray()))
        } else {
            i++
        }
    }

    return diffs
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

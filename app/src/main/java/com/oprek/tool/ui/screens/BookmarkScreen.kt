package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.FileAnalyzer
import com.oprek.tool.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BookmarkEntry(val offset: Long, val label: String, val timestamp: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(navController: NavController) {
    val context = LocalContext.current
    val bookmarks = remember { mutableStateListOf<BookmarkEntry>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var newOffset by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    // Auto-scan for interesting addresses on load
    val rev = SharedFileState.revision

    LaunchedEffect(rev) {
        val file = SharedFileState.findFile(context) ?: return@LaunchedEffect
        val data = withContext(kotlinx.coroutines.Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 1_000_000)) }
        // Find ELF magic
        for (i in 0 until data.size - 4) {
            if (data[i] == 0x7F.toByte() && data[i+1] == 'E'.code.toByte() && data[i+2] == 'L'.code.toByte() && data[i+3] == 'F'.code.toByte()) {
                bookmarks.add(BookmarkEntry(i.toLong(), "ELF Header", System.currentTimeMillis())); break
            }
        }
        // Find function prologues (STP X29, X30)
        var count = 0
        for (i in 0 until data.size - 4 step 4) {
            if (count >= 5) break
            val insn = data[i].toInt() and 0xFF or ((data[i+1].toInt() and 0xFF) shl 8) or
                    ((data[i+2].toInt() and 0xFF) shl 16) or ((data[i+3].toInt() and 0xFF) shl 24)
            if (insn == 0xA9BF7BFD.toInt() || insn == 0xA9007BFD.toInt()) {
                bookmarks.add(BookmarkEntry(i.toLong(), "Function prologue", System.currentTimeMillis())); count++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📌 Bookmarks", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📌", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No bookmarks yet", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Save important addresses for quick access", fontSize = 13.sp, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(12.dp)) {
                itemsIndexed(bookmarks) { idx, bm ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bookmark, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bm.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("0x${"%08X".format(bm.offset)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(bm.timestamp)), fontSize = 10.sp, color = TextMuted)
                            }
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("offset", "0x${"%08X".format(bm.offset)}"))
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentCyan) }
                            IconButton(onClick = { bookmarks.removeAt(idx) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = AccentRed) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add Bookmark", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = newOffset, onValueChange = { newOffset = it }, label = { Text("Offset (hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val off = newOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                        bookmarks.add(BookmarkEntry(off, newLabel.ifBlank { "Bookmark" }, System.currentTimeMillis()))
                        showAddDialog = false; newOffset = ""; newLabel = ""
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard
        )

    }
}

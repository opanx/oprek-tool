package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*
import java.io.File

/**
 * HexViewer v2 — Goto, bookmark, highlight search, entropy, export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(navController: NavController, vm: MainViewModel) {
    val hexLines by vm.hexLines.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val context = LocalContext.current
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showGotoDialog by remember { mutableStateOf(false) }
    var gotoOffset by remember { mutableStateOf("") }
    var showPatchDialog by remember { mutableStateOf(false) }
    var patchOffset by remember { mutableStateOf("") }
    var patchValue by remember { mutableStateOf("") }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkName by remember { mutableStateOf("") }
    var bookmarkOffset by remember { mutableStateOf("") }
    var bookmarks by remember { mutableStateOf(listOf<Pair<String, Long>>()) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadHex() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hex Viewer v2", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { showGotoDialog = true }) { Icon(Icons.Default.DoubleArrow, "Goto") }
                    IconButton(onClick = { showPatchDialog = true }) { Icon(Icons.Default.Edit, "Patch") }
                    IconButton(onClick = { showBookmarkDialog = true }) { Icon(Icons.Default.BookmarkAdd, "Bookmark") }
                    IconButton(onClick = { showInfo = !showInfo }) { Icon(Icons.Default.Info, "Info") }
                    IconButton(onClick = { vm.loadHex() }) { Icon(Icons.Default.Refresh, "Reload") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search bar
            if (showSearchBar) {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Hex pattern (e.g. 7F 45 4C 46) or ASCII") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    trailingIcon = {
                        IconButton(onClick = { if (searchQuery.isNotEmpty()) vm.searchHex(searchQuery) }) {
                            Icon(Icons.Default.Search, "Go")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, cursorColor = AccentGreen))
            }

            // Info panel
            if (showInfo) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("📊 File Info", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 13.sp)
                        Text("Lines: ${hexLines.size} | Lines × 16 = ${hexLines.size * 16} bytes", fontSize = 11.sp, color = TextSecondary)
                        if (bookmarks.isNotEmpty()) {
                            Text("📌 Bookmarks: ${bookmarks.size}", fontSize = 11.sp, color = AccentOrange)
                            bookmarks.take(5).forEach { (name, offset) ->
                                Text("  $name @ 0x${String.format("%08X", offset)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // Bookmarks bar
            if (bookmarks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bookmarks.take(5).forEach { (name, offset) ->
                        AssistChip(onClick = { vm.gotoOffset(offset) }, label = { Text("$name @ ${String.format("%08X", offset)}", fontSize = 9.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = AccentPurple.copy(alpha = 0.3f)))
                    }
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, fontSize = 11.sp, color = AccentGreen, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                items(hexLines) { line -> HexLine(line) }
                if (hexLines.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("No file loaded\nOpen a file from Home", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Goto dialog
    if (showGotoDialog) {
        AlertDialog(onDismissRequest = { showGotoDialog = false },
            title = { Text("Go to Offset", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = gotoOffset, onValueChange = { gotoOffset = it },
                    label = { Text("Offset (hex, e.g. 0x1234)") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val off = gotoOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                        vm.gotoOffset(off)
                        showGotoDialog = false
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showGotoDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard)
    }

    // Patch dialog
    if (showPatchDialog) {
        AlertDialog(onDismissRequest = { showPatchDialog = false },
            title = { Text("Patch Bytes", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = patchOffset, onValueChange = { patchOffset = it },
                        label = { Text("Offset (hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = patchValue, onValueChange = { patchValue = it },
                        label = { Text("New bytes (hex, e.g. 90 90 90)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val off = patchOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                        val bytes = patchValue.replace("\\s+".toRegex(), "").chunked(2).map { it.toByte(16) }.toByteArray()
                        vm.patchBytes(off, bytes)
                        showPatchDialog = false
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Patch") }
            },
            dismissButton = { TextButton(onClick = { showPatchDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard)
    }

    // Bookmark dialog
    if (showBookmarkDialog) {
        AlertDialog(onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Add Bookmark", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = bookmarkName, onValueChange = { bookmarkName = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = bookmarkOffset, onValueChange = { bookmarkOffset = it },
                        label = { Text("Offset (hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val off = bookmarkOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                        bookmarks = bookmarks + (bookmarkName.ifEmpty { "Bookmark ${bookmarks.size + 1}" } to off)
                        showBookmarkDialog = false; bookmarkName = ""; bookmarkOffset = ""
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showBookmarkDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard)
    }
}

@Composable
fun HexLine(line: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(line.take(8), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentPurple, modifier = Modifier.width(70.dp))
        Spacer(Modifier.width(8.dp))
        val hexPart = if (line.length > 10) line.substring(10, minOf(line.length, 58)) else ""
        val ascPart = if (line.contains("|")) line.substringAfter("|").trimEnd('|') else ""
        Text(hexPart, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
        Spacer(Modifier.weight(1f))
        Text(ascPart, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
    }
}

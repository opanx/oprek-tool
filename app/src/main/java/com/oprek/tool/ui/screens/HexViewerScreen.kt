package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(navController: NavController, vm: MainViewModel) {
    val hexLines by vm.hexLines.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var showPatchDialog by remember { mutableStateOf(false) }
    var patchOffset by remember { mutableStateOf("") }
    var patchValue by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadHex() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hex Viewer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { showPatchDialog = true }) {
                        Icon(Icons.Default.Edit, "Patch")
                    }
                    IconButton(onClick = { vm.loadHex() }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search bar
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search hex pattern (e.g. 7F 45 4C 46)") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    trailingIcon = {
                        IconButton(onClick = { if (searchQuery.isNotEmpty()) vm.searchHex(searchQuery) }) {
                            Icon(Icons.Default.Search, "Go")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        cursorColor = AccentGreen
                    )
                )
            }

            // Status
            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, fontSize = 11.sp, color = AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            // Hex content
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            ) {
                items(hexLines) { line ->
                    HexLine(line)
                }
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

    // Patch dialog
    if (showPatchDialog) {
        AlertDialog(
            onDismissRequest = { showPatchDialog = false },
            title = { Text("Patch Byte", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = patchOffset,
                        onValueChange = { patchOffset = it },
                        label = { Text("Offset (hex, e.g. 0x1234)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = patchValue,
                        onValueChange = { patchValue = it },
                        label = { Text("New byte (hex, e.g. 90)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val off = patchOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                            val byte = patchValue.removePrefix("0x").removePrefix("0X").toByte(16)
                            vm.patchByte(off, byte)
                            showPatchDialog = false
                            patchOffset = ""
                            patchValue = ""
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("Patch") }
            },
            dismissButton = {
                TextButton(onClick = { showPatchDialog = false }) { Text("Cancel") }
            },
            containerColor = DarkCard
        )
    }
}

@Composable
fun HexLine(line: String) {
    val isAddressLine = line.matches(Regex("^\\p{XDigit}{8}  .+"))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Address
        Text(
            line.take(8),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentPurple,
            modifier = Modifier.width(70.dp)
        )
        Spacer(Modifier.width(8.dp))
        // Hex bytes
        val hexPart = if (line.length > 10) line.substring(10, minOf(line.length, 58)) else ""
        val ascPart = if (line.contains("|")) line.substringAfter("|").trimEnd('|') else ""
        Text(
            hexPart,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentGreen
        )
        Spacer(Modifier.weight(1f))
        // ASCII
        Text(
            ascPart,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentCyan
        )
    }
}

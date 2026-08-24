@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.oprek.tool.MainViewModel
import com.oprek.tool.core.StringPair
import com.oprek.tool.ui.theme.*
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * StringExtractor v3 — Hex offset display, filter by type, collapse/expand, batch export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringExtractorScreen(navController: NavController, vm: MainViewModel) {
    val context = LocalContext.current
    val strings by vm.strings.collectAsState()
    var filterQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableIntStateOf(0) } // 0=All, 1=URL, 2=Auth, 3=Path, 4=IP, 5=Email, 6=Base64, 7=JNI, 8=Crypto
    var minLength by remember { mutableIntStateOf(4) }
    var showSettings by remember { mutableStateOf(false) }
    var showOffsets by remember { mutableStateOf(true) } // toggle hex offset display
    var sortByOffset by remember { mutableStateOf(false) }

    val filteredStrings = remember(strings, filterQuery, filterType, minLength) {
        strings.filter { entry ->
            val matchesQuery = filterQuery.isEmpty() || entry.value.contains(filterQuery, ignoreCase = true)
            val matchesType = when (filterType) {
                1 -> entry.value.contains("http", true) || entry.value.contains(".com", true) || entry.value.contains(".net", true) || entry.value.contains(".id", true)
                2 -> entry.value.contains("login", true) || entry.value.contains("auth", true) || entry.value.contains("license", true) || entry.value.contains("key", true) || entry.value.contains("device", true) || entry.value.contains("expire", true)
                3 -> entry.value.startsWith("/") || entry.value.contains("\\") || entry.value.contains(".so") || entry.value.contains(".dex") || entry.value.contains(".dat")
                4 -> Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(entry.value)
                5 -> entry.value.contains("@") && entry.value.contains(".")
                6 -> entry.value.length > 20 && entry.value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
                7 -> entry.value.contains("Landroid/") || entry.value.contains("Ljava/") || entry.value.contains("Lcom/")
                8 -> entry.value.contains("encrypt") || entry.value.contains("decrypt") || entry.value.contains("cipher") || entry.value.contains("AES") || entry.value.contains("RSA") || entry.value.contains("secret")
                else -> entry.value.length >= minLength
            }
            matchesQuery && matchesType
        }.let { list ->
            if (sortByOffset) list.sortedBy { it.offset } else list
        }
    }

    LaunchedEffect(Unit) { vm.extractStrings(minLength) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\uD83D\uDCDD Strings v3", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (filteredStrings.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = if (showOffsets) {
                                filteredStrings.joinToString("\n") { "0x${"%08X".format(it.offset)} ${it.value}" }
                            } else {
                                filteredStrings.joinToString("\n") { it.value }
                            }
                            cb.setPrimaryClip(ClipData.newPlainText("strings", text))
                            Toast.makeText(context, "Copied ${filteredStrings.size} strings!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy All") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/output")
                            dir.mkdirs()
                            val outFile = File(dir, "strings_${System.currentTimeMillis()}.txt")
                            val text = if (showOffsets) {
                                filteredStrings.joinToString("\n") { "0x${"%08X".format(it.offset)} ${it.value}" }
                            } else {
                                filteredStrings.joinToString("\n") { it.value }
                            }
                            outFile.writeText(text)
                            Toast.makeText(context, "Exported ${filteredStrings.size} strings to ${outFile.name}", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Save, "Export") }
                    }
                    IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Tune, "Settings") }
                    IconButton(onClick = { vm.extractStrings(minLength) }) { Icon(Icons.Default.Refresh, "Reload") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("\u2699\uFE0F Min Length: $minLength", fontSize = 12.sp, color = AccentCyan)
                        Slider(value = minLength.toFloat(), onValueChange = { minLength = it.toInt() },
                            valueRange = 2f..20f, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        // Toggle options
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = showOffsets, onClick = { showOffsets = !showOffsets },
                                label = { Text("Hex Offset", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.2f), selectedLabelColor = AccentGreen))
                            FilterChip(selected = sortByOffset, onClick = { sortByOffset = !sortByOffset },
                                label = { Text("Sort by Offset", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f), selectedLabelColor = AccentCyan))
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { vm.extractStrings(minLength) }, Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Extract") }
                    }
                }
            }

            // Search + Filter
            OutlinedTextField(value = filterQuery, onValueChange = { filterQuery = it },
                placeholder = { Text("Search strings...") }, modifier = Modifier.fillMaxWidth().padding(8.dp),
                singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentCyan) },
                colors = TextFieldDefaults.colors(focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface, focusedIndicatorColor = AccentGreen, unfocusedIndicatorColor = TextMuted, cursorColor = AccentGreen))

            // Type filter chips — 2 rows for 9 types
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All" to 0, "URLs" to 1, "Auth" to 2, "Paths" to 3, "IPs" to 4).forEach { (label, idx) ->
                        FilterChip(selected = filterType == idx, onClick = { filterType = idx },
                            label = { Text(label, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Email" to 5, "Base64" to 6, "JNI" to 7, "Crypto" to 8).forEach { (label, idx) ->
                        FilterChip(selected = filterType == idx, onClick = { filterType = idx },
                            label = { Text(label, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                    }
                }
            }

            // Count + hex toggle
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredStrings.size} / ${strings.size} strings", fontSize = 11.sp, color = AccentGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = showOffsets, onClick = { showOffsets = !showOffsets },
                        label = { Text("0x****", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.2f), selectedLabelColor = AccentPurple))
                    FilterChip(selected = sortByOffset, onClick = { sortByOffset = !sortByOffset },
                        label = { Text("\u2195 Offset", fontSize = 8.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.2f), selectedLabelColor = AccentOrange))
                }
            }

            // String list with hex offsets
            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredStrings) { entry ->
                    val displayText = if (showOffsets) {
                        "0x${"%08X".format(entry.offset)}  ${entry.value}"
                    } else {
                        entry.value
                    }
                    val color = when {
                        entry.value.contains("http", true) -> AccentCyan
                        entry.value.contains("login", true) || entry.value.contains("auth", true) || entry.value.contains("license", true) -> AccentRed
                        entry.value.startsWith("/") -> AccentPurple
                        entry.value.contains("@") && entry.value.contains(".") -> AccentOrange
                        Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(entry.value) -> AccentOrange
                        entry.value.contains("Landroid/") || entry.value.contains("Ljava/") -> Color(0xFF50FA7B) // green for JNI
                        entry.value.contains("encrypt", true) || entry.value.contains("decrypt", true) || entry.value.contains("AES", true) -> Color(0xFFFF79C6) // pink for crypto
                        else -> TextSecondary
                    }
                    Text(displayText, fontSize = if (showOffsets) 10.sp else 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (showOffsets) AccentGreen else color,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            .background(DarkBg).padding(horizontal = 4.dp))
                }
                if (filteredStrings.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text("No strings found", color = TextSecondary) } }
                }
            }
        }
    }
}
